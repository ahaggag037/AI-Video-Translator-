package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.SttAudioProfile
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

internal data class FieldTestPreparedSttWindow(
    val window: FieldTestSttWindow,
    val profile: SttAudioProfile,
)

/**
 * Splits one canonical full-source mono PCM16 WAV into deterministic provider windows.
 *
 * The source is decoded once before this layer. Window boundaries are therefore frame-exact and do
 * not depend on MediaExtractor seeking or repeated source reads. This field-test helper never calls
 * the provider and never changes durable session state.
 */
internal object FieldTestPcmWindowSplitter {
    fun split(
        fullProfile: SttAudioProfile,
        outputDir: File,
        windowUs: Long = FieldTestSttWindowPlanner.WINDOW_US,
    ): List<FieldTestPreparedSttWindow> {
        require(fullProfile.file.isFile) { "full STT WAV is missing" }
        require(fullProfile.sampleRateHz > 0) { "invalid full STT sample rate" }
        require(fullProfile.channelCount == 1) { "field-test splitter requires mono PCM" }
        require(fullProfile.bitsPerSample == 16) { "field-test splitter requires PCM16" }
        require(windowUs > 0L && windowUs % 1_000L == 0L) { "invalid STT window duration" }
        require(outputDir.mkdirs() || outputDir.isDirectory) { "cannot create STT window directory" }

        val source = RandomAccessFile(fullProfile.file, "r")
        val created = mutableListOf<File>()
        try {
            val header = ByteArray(WAV_HEADER_BYTES)
            source.readFully(header)
            val wav = inspectCanonicalWav(header)
            require(wav.sampleRateHz == fullProfile.sampleRateHz) { "full STT WAV sample rate mismatch" }
            require(wav.channelCount == 1 && wav.bitsPerSample == 16) { "full STT WAV is not canonical mono PCM16" }

            val dataBytes = source.length() - WAV_HEADER_BYTES
            require(dataBytes > 0L && dataBytes % BYTES_PER_FRAME == 0L) { "invalid full STT PCM byte count" }
            require(wav.dataBytes == dataBytes) { "full STT WAV data size mismatch" }
            val totalFrames = dataBytes / BYTES_PER_FRAME
            val derivedEndUs = Math.addExact(
                fullProfile.sourceStartUs,
                Math.multiplyExact(totalFrames, 1_000_000L) / fullProfile.sampleRateHz,
            )
            require(derivedEndUs == fullProfile.sourceEndUs) { "full STT profile duration does not match WAV frames" }

            val framesPerWindowNumerator = Math.multiplyExact(windowUs, fullProfile.sampleRateHz.toLong())
            require(framesPerWindowNumerator % 1_000_000L == 0L) {
                "STT window is not frame-aligned for this sample rate"
            }
            val maxFrames = framesPerWindowNumerator / 1_000_000L
            require(maxFrames > 0L) { "STT window contains no PCM frames" }

            val prepared = mutableListOf<FieldTestPreparedSttWindow>()
            var frameCursor = 0L
            var index = 0
            while (frameCursor < totalFrames) {
                val frameEnd = minOf(totalFrames, Math.addExact(frameCursor, maxFrames))
                val frameCount = frameEnd - frameCursor
                val windowStartUs = Math.addExact(
                    fullProfile.sourceStartUs,
                    Math.multiplyExact(frameCursor, 1_000_000L) / fullProfile.sampleRateHz,
                )
                val windowEndUs = Math.addExact(
                    fullProfile.sourceStartUs,
                    Math.multiplyExact(frameEnd, 1_000_000L) / fullProfile.sampleRateHz,
                )
                val window = FieldTestSttWindow(index, windowStartUs, windowEndUs)
                val output = File(outputDir, "stt-window-${index.toString().padStart(3, '0')}.wav")
                if (output.exists() && !output.delete()) error("cannot replace previous STT window")
                created += output
                writeWindow(source, output, fullProfile.sampleRateHz, frameCount)

                val durationMs = Math.multiplyExact(frameCount, 1_000L) / fullProfile.sampleRateHz
                prepared += FieldTestPreparedSttWindow(
                    window = window,
                    profile = SttAudioProfile(
                        file = output,
                        sampleRateHz = fullProfile.sampleRateHz,
                        channelCount = 1,
                        bitsPerSample = 16,
                        sourceStartUs = windowStartUs,
                        sourceEndUs = windowEndUs,
                        durationMs = durationMs,
                    ),
                )
                frameCursor = frameEnd
                index += 1
            }

            require(prepared.isNotEmpty()) { "full STT WAV produced no windows" }
            require(prepared.first().window.startUs == fullProfile.sourceStartUs)
            require(prepared.last().window.endUs == fullProfile.sourceEndUs)
            prepared.zipWithNext().forEach { (left, right) ->
                require(left.window.endUs == right.window.startUs) { "PCM windows are not contiguous" }
            }
            return prepared
        } catch (error: Throwable) {
            created.forEach { file -> runCatching { if (file.exists()) file.delete() } }
            throw error
        } finally {
            source.close()
        }
    }

    private fun writeWindow(
        source: RandomAccessFile,
        output: File,
        sampleRateHz: Int,
        frameCount: Long,
    ) {
        val dataBytes = Math.multiplyExact(frameCount, BYTES_PER_FRAME)
        require(dataBytes in 1..MAX_RIFF_DATA_BYTES) { "STT window exceeds RIFF32 bounds" }
        RandomAccessFile(output, "rw").use { target ->
            target.setLength(0L)
            target.write(buildHeader(sampleRateHz, dataBytes))
            var remaining = dataBytes
            val buffer = ByteArray(COPY_BUFFER_BYTES)
            while (remaining > 0L) {
                val count = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                require(count > 0) { "full STT WAV ended before planned window" }
                target.write(buffer, 0, count)
                remaining -= count.toLong()
            }
        }
        require(output.length() == WAV_HEADER_BYTES + dataBytes) { "written STT window size mismatch" }
    }

    private data class CanonicalWav(
        val sampleRateHz: Int,
        val channelCount: Int,
        val bitsPerSample: Int,
        val dataBytes: Long,
    )

    private fun inspectCanonicalWav(header: ByteArray): CanonicalWav {
        require(header.size == WAV_HEADER_BYTES.toInt())
        fun ascii(offset: Int, count: Int) = String(header, offset, count, Charsets.US_ASCII)
        val bytes = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(offset: Int) = bytes.getShort(offset).toInt() and 0xffff
        fun u32(offset: Int) = bytes.getInt(offset).toLong() and 0xffff_ffffL

        require(ascii(0, 4) == "RIFF" && ascii(8, 4) == "WAVE") { "not a RIFF/WAVE file" }
        require(ascii(12, 4) == "fmt " && u32(16) == 16L && u16(20) == 1) { "unsupported WAV format" }
        require(ascii(36, 4) == "data") { "canonical WAV data chunk missing" }
        val sampleRateHz = u32(24).toInt()
        val channels = u16(22)
        val bits = u16(34)
        val dataBytes = u32(40)
        require(u32(4) == dataBytes + 36L) { "RIFF size mismatch" }
        require(u16(32) == channels * (bits / 8)) { "WAV block alignment mismatch" }
        return CanonicalWav(sampleRateHz, channels, bits, dataBytes)
    }

    private fun buildHeader(sampleRateHz: Int, dataBytes: Long): ByteArray {
        val byteRate = Math.multiplyExact(sampleRateHz, BYTES_PER_FRAME.toInt())
        return ByteBuffer.allocate(WAV_HEADER_BYTES.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
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
        }.array()
    }

    private const val WAV_HEADER_BYTES = 44L
    private const val BYTES_PER_FRAME = 2L
    private const val COPY_BUFFER_BYTES = 64 * 1024
    private const val MAX_RIFF_DATA_BYTES = 0xffff_ffffL - 36L
}
