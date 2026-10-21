package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Base64
import java.util.Properties

data class FullVideoSttAggregate(
    val sessionId: String,
    val sourceAttachmentId: String,
    val sourceDurationUs: Long,
    val attemptIds: List<String>,
    val result: NvidiaSttResult,
) {
    init {
        require(isSafeId(sessionId) && isSafeId(sourceAttachmentId)) { "invalid full-video STT aggregate identity" }
        require(sourceDurationUs > 0L) { "invalid full-video source duration" }
        require(attemptIds.isNotEmpty() && attemptIds.distinct().size == attemptIds.size && attemptIds.all(::isSafeId)) {
            "invalid full-video STT attempt identities"
        }
        require(result.httpStatus in 200..299 && result.transcript.isNotBlank() && result.words.isNotEmpty()) {
            "full-video STT aggregate requires accepted timed speech"
        }
        var previousEndMs = 0L
        val maxTimelineMs = (sourceDurationUs + 999L) / 1_000L
        result.words.forEach { word ->
            val start = requireNotNull(word.startMs) { "aggregate word missing start" }
            val end = requireNotNull(word.endMs) { "aggregate word missing end" }
            require(word.text.isNotBlank() && start >= previousEndMs && end > start && end <= maxTimelineMs) {
                "invalid full-video aggregate word timing"
            }
            previousEndMs = end
        }
    }
}

internal class FullVideoSttAggregateStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    fun readOrNull(sessionId: String): FullVideoSttAggregate? = synchronized(lock) {
        validateSession(sessionId)
        require(isSafeId(sessionId)) { "invalid full-video STT session id" }
        val file = aggregateFile(sessionId)
        if (!file.isFile) null else decode(file).also { aggregate ->
            require(aggregate.sessionId == sessionId) { "full-video STT aggregate session mismatch" }
        }
    }

    fun persist(aggregate: FullVideoSttAggregate) = synchronized(lock) {
        validateSession(aggregate.sessionId)
        val target = aggregateFile(aggregate.sessionId)
        val existing = if (target.isFile) decode(target) else null
        if (existing != null) {
            check(existing == aggregate) { "full-video STT aggregate is immutable for this session" }
            return@synchronized
        }
        writeAtomic(target, aggregate)
    }

    private fun aggregateFile(sessionId: String): File = File(File(sessionsRoot, sessionId), FILE_NAME)

    private fun writeAtomic(target: File, aggregate: FullVideoSttAggregate) {
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$FILE_NAME.tmp")
        val p = Properties().apply {
            setProperty("schema", SCHEMA)
            setProperty("sessionId", aggregate.sessionId)
            setProperty("sourceAttachmentId", aggregate.sourceAttachmentId)
            setProperty("sourceDurationUs", aggregate.sourceDurationUs.toString())
            setProperty("attemptCount", aggregate.attemptIds.size.toString())
            aggregate.attemptIds.forEachIndexed { index, id -> setProperty("attempt.$index", id) }
            setProperty("httpStatus", aggregate.result.httpStatus.toString())
            setProperty("transcript", encode(aggregate.result.transcript))
            setProperty("wordCount", aggregate.result.words.size.toString())
            aggregate.result.words.forEachIndexed { index, word ->
                val prefix = "word.$index."
                setProperty(prefix + "text", encode(word.text))
                setProperty(prefix + "startMs", requireNotNull(word.startMs).toString())
                setProperty(prefix + "endMs", requireNotNull(word.endMs).toString())
                word.confidence?.let { setProperty(prefix + "confidence", it.toString()) }
            }
        }
        temp.outputStream().use { out ->
            p.store(out, "AI Video Translator full-video STT aggregate")
            out.flush()
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun decode(file: File): FullVideoSttAggregate {
        val p = Properties().also { props -> file.inputStream().use(props::load) }
        require(p.getProperty("schema") == SCHEMA) { "unsupported full-video STT aggregate schema" }
        val attemptCount = p.required("attemptCount").toInt()
        val wordCount = p.required("wordCount").toInt()
        require(attemptCount > 0 && wordCount > 0) { "empty full-video STT aggregate" }
        return FullVideoSttAggregate(
            sessionId = p.required("sessionId"),
            sourceAttachmentId = p.required("sourceAttachmentId"),
            sourceDurationUs = p.required("sourceDurationUs").toLong(),
            attemptIds = (0 until attemptCount).map { p.required("attempt.$it") },
            result = NvidiaSttResult(
                transcript = decodeText(p.required("transcript")),
                words = (0 until wordCount).map { index ->
                    val prefix = "word.$index."
                    NvidiaWord(
                        text = decodeText(p.required(prefix + "text")),
                        startMs = p.required(prefix + "startMs").toLong(),
                        endMs = p.required(prefix + "endMs").toLong(),
                        confidence = p.getProperty(prefix + "confidence")?.toDouble(),
                    )
                },
                httpStatus = p.required("httpStatus").toInt(),
            ),
        )
    }

    private fun Properties.required(key: String): String = requireNotNull(getProperty(key)) {
        "missing full-video STT aggregate field: $key"
    }

    private fun encode(value: String): String = Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))
    private fun decodeText(value: String): String = String(Base64.getDecoder().decode(value), Charsets.UTF_8)

    private companion object {
        val lock = Any()
        const val FILE_NAME = "full_video_stt.properties"
        const val SCHEMA = "full-video-stt-aggregate-v1"
    }
}
