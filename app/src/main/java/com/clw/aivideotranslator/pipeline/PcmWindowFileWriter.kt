package com.clw.aivideotranslator.pipeline

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class ProductionSttAudioWindow(
    val index: Int,
    val file: File,
    val sampleRateHz: Int,
    val sampleStartFrame: Long,
    val sampleEndFrame: Long,
    /** Observed first compressed-track PTS only. X001 owns any authoritative presentation mapping. */
    val observedFirstTrackPresentationUs: Long,
) {
    init {
        require(index >= 0) { "negative STT window index" }
        require(file.isFile && file.length() > WAV_HEADER_BYTES) { "STT window WAV is missing" }
        require(sampleRateHz > 0) { "invalid STT window sample rate" }
        require(sampleStartFrame >= 0L && sampleEndFrame > sampleStartFrame) { "invalid STT sample-frame interval" }
        require(observedFirstTrackPresentationUs >= 0L) { "invalid observed audio origin" }
    }

    val frameCount: Long get() = sampleEndFrame - sampleStartFrame
    val durationMs: Long get() = frameCount * 1_000L / sampleRateHz

    companion object {
        const val WAV_HEADER_BYTES = 44L
    }
}

/**
 * Bounded mono-PCM16 WAV window writer.
 *
 * The writer never materializes full-video decoded audio. Incoming PCM is split at exact sample-frame
 * boundaries. As soon as one window is full its WAV header is finalized, the file is closed, and a
 * callback receives an immutable window while later PCM continues into a new file.
 *
 * Ownership rule: once [onWindowFinalized] returns successfully, that file belongs to the consumer
 * and this writer will never delete it. This permits concurrent STT consumption while later decode
 * continues. Files that have not been handed off remain writer-owned and are removed on abort.
 */
internal class PcmWindowFileWriter(
    private val outputDir: File,
    private val sampleRateHz: Int,
    windowDurationUs: Long,
    private val observedFirstTrackPresentationUs: Long,
    private val onWindowFinalized: (ProductionSttAudioWindow) -> Unit,
) : Closeable {
    private val framesPerWindow: Long
    private var currentFile: File? = null
    private var currentRaf: RandomAccessFile? = null
    private var currentFrames = 0L
    private var totalFrames = 0L
    private var nextIndex = 0
    private var closed = false
    private var finished = false
    private val writerOwnedFiles = mutableListOf<File>()

    init {
        require(sampleRateHz > 0) { "sample rate must be positive" }
        require(windowDurationUs > 0L) { "window duration must be positive" }
        require(observedFirstTrackPresentationUs >= 0L) { "observed audio origin must be non-negative" }
        require(outputDir.mkdirs() || outputDir.isDirectory) { "cannot create STT PCM window directory" }
        val numerator = Math.multiplyExact(windowDurationUs, sampleRateHz.toLong())
        require(numerator % 1_000_000L == 0L) {
            "STT window duration is not sample-frame aligned"
        }
        framesPerWindow = numerator / 1_000_000L
        require(framesPerWindow > 0L) { "STT window contains no sample frames" }
    }

    /** Appends canonical mono PCM16 little-endian bytes. The callback may apply backpressure. */
    @Synchronized fun append(bytes: ByteArray, offset: Int = 0, length: Int = bytes.size - offset) {
        check(!closed && !finished) { "PCM window writer is not active" }
        require(offset >= 0 && length >= 0 && offset + length <= bytes.size) { "invalid PCM append range" }
        require(length % BYTES_PER_FRAME == 0) { "mono PCM16 append is not frame aligned" }
        var cursor = offset
        var remainingFrames = length / BYTES_PER_FRAME
        while (remainingFrames > 0) {
            ensureOpenWindow()
            val writableFrames = minOf(remainingFrames.toLong(), framesPerWindow - currentFrames).toInt()
            val writableBytes = Math.multiplyExact(writableFrames, BYTES_PER_FRAME)
            require(writableBytes > 0) { "PCM writer made no progress" }
            currentRaf!!.write(bytes, cursor, writableBytes)
            cursor += writableBytes
            remainingFrames -= writableFrames
            currentFrames += writableFrames.toLong()
            totalFrames += writableFrames.toLong()
            if (currentFrames == framesPerWindow) finalizeCurrentWindow()
        }
    }

    /** Finalizes the last partial window, if any. No empty tail window is created. */
    @Synchronized fun finish() {
        check(!closed) { "PCM window writer is closed" }
        if (finished) return
        finished = true
        if (currentFrames > 0L) finalizeCurrentWindow() else closeCurrentHandle()
    }

    @Synchronized fun totalFramesWritten(): Long = totalFrames

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        if (!finished) {
            closeCurrentHandle()
            writerOwnedFiles.forEach { file ->
                if (file.exists() && !file.delete()) file.deleteOnExit()
            }
        } else {
            closeCurrentHandle()
        }
        writerOwnedFiles.clear()
    }

    private fun ensureOpenWindow() {
        if (currentRaf != null) return
        val file = File(outputDir, "stt-window-${nextIndex.toString().padStart(4, '0')}.wav")
        require(!file.exists()) { "STT window file already exists" }
        val raf = RandomAccessFile(file, "rw")
        try {
            raf.setLength(0L)
            raf.write(ByteArray(ProductionSttAudioWindow.WAV_HEADER_BYTES.toInt()))
        } catch (error: Throwable) {
            raf.close()
            file.delete()
            throw error
        }
        currentFile = file
        currentRaf = raf
        currentFrames = 0L
        writerOwnedFiles += file
    }

    private fun finalizeCurrentWindow() {
        val file = requireNotNull(currentFile) { "no STT window file to finalize" }
        val raf = requireNotNull(currentRaf) { "no STT window handle to finalize" }
        require(currentFrames > 0L) { "cannot finalize empty STT window" }
        val dataBytes = Math.multiplyExact(currentFrames, BYTES_PER_FRAME.toLong())
        require(dataBytes <= MAX_RIFF_DATA_BYTES) { "STT window exceeds RIFF32 bounds" }
        val startFrame = totalFrames - currentFrames
        val endFrame = totalFrames
        try {
            raf.seek(0L)
            raf.write(buildWavHeader(sampleRateHz, dataBytes))
            raf.fd.sync()
        } finally {
            raf.close()
            currentRaf = null
            currentFile = null
        }
        require(file.length() == ProductionSttAudioWindow.WAV_HEADER_BYTES + dataBytes) {
            "finalized STT window size mismatch"
        }
        val window = ProductionSttAudioWindow(
            index = nextIndex,
            file = file,
            sampleRateHz = sampleRateHz,
            sampleStartFrame = startFrame,
            sampleEndFrame = endFrame,
            observedFirstTrackPresentationUs = observedFirstTrackPresentationUs,
        )
        nextIndex += 1
        currentFrames = 0L
        onWindowFinalized(window)
        // Consumer now owns this immutable file. Later producer failure must not delete it.
        writerOwnedFiles.remove(file)
    }

    private fun closeCurrentHandle() {
        runCatching { currentRaf?.close() }
        currentRaf = null
        currentFile = null
        currentFrames = 0L
    }

    private fun buildWavHeader(sampleRateHz: Int, dataBytes: Long): ByteArray {
        val byteRate = Math.multiplyExact(sampleRateHz, BYTES_PER_FRAME)
        return ByteBuffer.allocate(ProductionSttAudioWindow.WAV_HEADER_BYTES.toInt())
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                put("RIFF".toByteArray(Charsets.US_ASCII))
                putInt((dataBytes + 36L).toInt())
                put("WAVE".toByteArray(Charsets.US_ASCII))
                put("fmt ".toByteArray(Charsets.US_ASCII))
                putInt(16)
                putShort(1.toShort())
                putShort(1.toShort())
                putInt(sampleRateHz)
                putInt(byteRate)
                putShort(BYTES_PER_FRAME.toShort())
                putShort(16.toShort())
                put("data".toByteArray(Charsets.US_ASCII))
                putInt(dataBytes.toInt())
            }
            .array()
    }

    private companion object {
        const val BYTES_PER_FRAME = 2
        const val MAX_RIFF_DATA_BYTES = 0xffff_ffffL - 36L
    }
}
