package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

enum class FullVideoSttWindowPhase {
    SENT,
    RECEIVED,
}

data class FullVideoSttWindowReceipt(
    val phase: FullVideoSttWindowPhase,
    val attemptId: String,
    val sessionId: String,
    val sourceAttachmentId: String,
    val window: SttWindow,
    val requestProfileId: String,
    val parserVersion: String,
    val sampleSha256: String,
    val sampleRateHz: Int,
    val channelCount: Int,
    val bitsPerSample: Int,
    val sourceStartUs: Long,
    val sourceEndUs: Long,
    val rawResponseSha256: String? = null,
    val result: NvidiaSttResult? = null,
) {
    init {
        require(isSafeId(attemptId) && isSafeId(sessionId) && isSafeId(sourceAttachmentId)) { "invalid STT window receipt identity" }
        require(requestProfileId.isNotBlank() && parserVersion.isNotBlank()) { "missing STT profile/parser identity" }
        require(sampleSha256.isLowerSha256()) { "invalid STT sample digest" }
        require(sampleRateHz > 0 && channelCount == 1 && bitsPerSample == 16) { "unsupported STT sample format" }
        require(sourceStartUs >= window.startUs && sourceEndUs > sourceStartUs && sourceEndUs <= window.endUs + 100_000L) {
            "prepared STT media bounds escape requested window"
        }
        when (phase) {
            FullVideoSttWindowPhase.SENT -> {
                require(rawResponseSha256 == null && result == null) { "SENT receipt cannot contain response" }
            }
            FullVideoSttWindowPhase.RECEIVED -> {
                require(rawResponseSha256?.isLowerSha256() == true) { "invalid received response digest" }
                require(result != null && result.httpStatus in 200..299) { "RECEIVED receipt needs accepted result" }
            }
        }
    }

    fun sameSendIdentity(other: FullVideoSttWindowReceipt): Boolean =
        attemptId == other.attemptId &&
            sessionId == other.sessionId &&
            sourceAttachmentId == other.sourceAttachmentId &&
            window == other.window &&
            requestProfileId == other.requestProfileId &&
            parserVersion == other.parserVersion &&
            sampleSha256 == other.sampleSha256 &&
            sampleRateHz == other.sampleRateHz &&
            channelCount == other.channelCount &&
            bitsPerSample == other.bitsPerSample &&
            sourceStartUs == other.sourceStartUs &&
            sourceEndUs == other.sourceEndUs

    companion object {
        fun sent(
            attemptId: String,
            sessionId: String,
            sourceAttachmentId: String,
            window: SttWindow,
            requestProfileId: String,
            parserVersion: String,
            sampleSha256: String,
            sampleRateHz: Int,
            channelCount: Int,
            bitsPerSample: Int,
            sourceStartUs: Long,
            sourceEndUs: Long,
        ) = FullVideoSttWindowReceipt(
            phase = FullVideoSttWindowPhase.SENT,
            attemptId = attemptId,
            sessionId = sessionId,
            sourceAttachmentId = sourceAttachmentId,
            window = window,
            requestProfileId = requestProfileId,
            parserVersion = parserVersion,
            sampleSha256 = sampleSha256,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            bitsPerSample = bitsPerSample,
            sourceStartUs = sourceStartUs,
            sourceEndUs = sourceEndUs,
        )
    }
}

internal class FullVideoSttWindowJournalStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    fun readOrNull(sessionId: String, attemptId: String): FullVideoSttWindowReceipt? = synchronized(lock) {
        validateSession(sessionId)
        require(isSafeId(attemptId)) { "invalid STT window attempt id" }
        val file = receiptFile(sessionId, attemptId)
        if (!file.exists()) null else decode(file).also { receipt ->
            require(receipt.sessionId == sessionId && receipt.attemptId == attemptId) { "STT window receipt identity mismatch" }
        }
    }

    fun findSentOrNull(sessionId: String): FullVideoSttWindowReceipt? = synchronized(lock) {
        validateSession(sessionId)
        require(isSafeId(sessionId)) { "invalid STT window session id" }
        val directory = File(File(sessionsRoot, sessionId), DIRECTORY)
        if (!directory.isDirectory) return@synchronized null
        directory.listFiles()
            .orEmpty()
            .asSequence()
            .filter { it.isFile && it.extension == "properties" }
            .map(::decode)
            .firstOrNull { it.phase == FullVideoSttWindowPhase.SENT }
    }

    fun persistSent(receipt: FullVideoSttWindowReceipt) = synchronized(lock) {
        require(receipt.phase == FullVideoSttWindowPhase.SENT) { "not a SENT receipt" }
        validateSession(receipt.sessionId)
        val file = receiptFile(receipt.sessionId, receipt.attemptId)
        check(!file.exists()) { "STT window attempt already has durable state" }
        writeAtomic(file, receipt)
    }

    fun persistReceived(
        sent: FullVideoSttWindowReceipt,
        rawResponseSha256: String,
        result: NvidiaSttResult,
    ): FullVideoSttWindowReceipt = synchronized(lock) {
        require(sent.phase == FullVideoSttWindowPhase.SENT) { "received transition requires SENT input" }
        validateSession(sent.sessionId)
        val file = receiptFile(sent.sessionId, sent.attemptId)
        check(file.exists()) { "missing durable SENT fence" }
        val durable = decode(file)
        check(durable.phase == FullVideoSttWindowPhase.SENT && durable.sameSendIdentity(sent)) {
            "durable SENT receipt does not match received response"
        }
        val received = sent.copy(
            phase = FullVideoSttWindowPhase.RECEIVED,
            rawResponseSha256 = rawResponseSha256,
            result = result,
        )
        writeAtomic(file, received)
        received
    }

    private fun receiptFile(sessionId: String, attemptId: String): File =
        File(File(File(sessionsRoot, sessionId), DIRECTORY), "$attemptId.properties")

    private fun writeAtomic(target: File, receipt: FullVideoSttWindowReceipt) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "${target.name}.tmp")
        val p = Properties().apply {
            setProperty("schema", SCHEMA)
            setProperty("phase", receipt.phase.name)
            setProperty("attemptId", receipt.attemptId)
            setProperty("sessionId", receipt.sessionId)
            setProperty("sourceAttachmentId", receipt.sourceAttachmentId)
            setProperty("windowIndex", receipt.window.index.toString())
            setProperty("windowStartUs", receipt.window.startUs.toString())
            setProperty("windowEndUs", receipt.window.endUs.toString())
            setProperty("requestProfileId", receipt.requestProfileId)
            setProperty("parserVersion", receipt.parserVersion)
            setProperty("sampleSha256", receipt.sampleSha256)
            setProperty("sampleRateHz", receipt.sampleRateHz.toString())
            setProperty("channelCount", receipt.channelCount.toString())
            setProperty("bitsPerSample", receipt.bitsPerSample.toString())
            setProperty("sourceStartUs", receipt.sourceStartUs.toString())
            setProperty("sourceEndUs", receipt.sourceEndUs.toString())
            receipt.rawResponseSha256?.let { setProperty("rawResponseSha256", it) }
            receipt.result?.let { result ->
                setProperty("httpStatus", result.httpStatus.toString())
                setProperty("transcript", encode(result.transcript))
                setProperty("wordCount", result.words.size.toString())
                result.words.forEachIndexed { index, word ->
                    val prefix = "word.$index."
                    setProperty(prefix + "text", encode(word.text))
                    word.startMs?.let { setProperty(prefix + "startMs", it.toString()) }
                    word.endMs?.let { setProperty(prefix + "endMs", it.toString()) }
                    word.confidence?.let { setProperty(prefix + "confidence", it.toString()) }
                }
            }
        }
        temp.outputStream().use { out ->
            p.store(out, "AI Video Translator full-video STT window receipt")
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun decode(file: File): FullVideoSttWindowReceipt {
        val p = Properties().also { props -> file.inputStream().use(props::load) }
        require(p.getProperty("schema") == SCHEMA) { "unsupported STT window journal schema" }
        val phase = FullVideoSttWindowPhase.valueOf(p.required("phase"))
        val result = if (phase == FullVideoSttWindowPhase.RECEIVED) {
            val count = p.required("wordCount").toInt()
            require(count > 0) { "empty received STT word list" }
            NvidiaSttResult(
                transcript = decodeText(p.required("transcript")),
                words = (0 until count).map { index ->
                    val prefix = "word.$index."
                    NvidiaWord(
                        text = decodeText(p.required(prefix + "text")),
                        startMs = p.getProperty(prefix + "startMs")?.toLong(),
                        endMs = p.getProperty(prefix + "endMs")?.toLong(),
                        confidence = p.getProperty(prefix + "confidence")?.toDouble(),
                    )
                },
                httpStatus = p.required("httpStatus").toInt(),
            )
        } else null
        return FullVideoSttWindowReceipt(
            phase = phase,
            attemptId = p.required("attemptId"),
            sessionId = p.required("sessionId"),
            sourceAttachmentId = p.required("sourceAttachmentId"),
            window = SttWindow(
                index = p.required("windowIndex").toInt(),
                startUs = p.required("windowStartUs").toLong(),
                endUs = p.required("windowEndUs").toLong(),
            ),
            requestProfileId = p.required("requestProfileId"),
            parserVersion = p.required("parserVersion"),
            sampleSha256 = p.required("sampleSha256"),
            sampleRateHz = p.required("sampleRateHz").toInt(),
            channelCount = p.required("channelCount").toInt(),
            bitsPerSample = p.required("bitsPerSample").toInt(),
            sourceStartUs = p.required("sourceStartUs").toLong(),
            sourceEndUs = p.required("sourceEndUs").toLong(),
            rawResponseSha256 = p.getProperty("rawResponseSha256"),
            result = result,
        )
    }

    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) { "missing STT window journal field: $key" }
    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeText(value: String): String = String(Base64.getDecoder().decode(value), Charsets.UTF_8)

    private companion object {
        val lock = Any()
        const val DIRECTORY = "stt_windows"
        const val SCHEMA = "full-video-stt-window-v1"
    }
}

private fun String.isLowerSha256(): Boolean =
    length == 64 && all { it in '0'..'9' || it in 'a'..'f' }
