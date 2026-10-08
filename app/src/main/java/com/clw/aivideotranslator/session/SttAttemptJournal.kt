package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.sha256Utf8
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

internal const val STT_ATTEMPT_SCHEMA_VERSION = 1

internal enum class SttAttemptPhase {
    PREPARED,
    SENT,
    RECEIVED,
    ADOPTED,
}

internal enum class SttAttemptRecoveryDisposition {
    SAFE_TO_SUBMIT,
    UNKNOWN_REMOTE_OUTCOME,
    RECEIVED_AVAILABLE,
    ADOPTED,
}

/**
 * Durable ownership record for the one initial STT/source-snapshot attempt.
 *
 * PREPARED proves no transport has been allowed to run yet. SENT is written before transport can
 * run and therefore means the remote outcome is unknown after a crash. RECEIVED embeds the complete
 * redacted SourceSnapshot so a process death before manifest adoption can recover without another
 * paid request. ADOPTED is only written after the manifest points at that exact snapshot.
 */
internal data class SttAttemptReceipt(
    val schemaVersion: Int = STT_ATTEMPT_SCHEMA_VERSION,
    val attemptId: String,
    val sessionId: String,
    val epoch: Long,
    val expectedManifestRevision: Long,
    val sourceAttachmentId: String,
    val requestProfileId: String,
    val sampleSha256: String,
    val phase: SttAttemptPhase,
    val snapshot: SourceSnapshot? = null,
) {
    init {
        require(schemaVersion == STT_ATTEMPT_SCHEMA_VERSION) { "unsupported STT attempt schema" }
        require(isSafeId(attemptId) && isSafeId(sessionId) && isSafeId(sourceAttachmentId)) {
            "invalid STT attempt identity"
        }
        require(epoch >= 0L && expectedManifestRevision >= 0L) { "negative STT attempt fence" }
        require(requestProfileId.isNotBlank() && requestProfileId.length <= 512 &&
            requestProfileId.none(Char::isISOControl)) { "invalid STT request profile identity" }
        require(isLowerSha256(sampleSha256)) { "invalid STT sample SHA-256" }
        when (phase) {
            SttAttemptPhase.PREPARED,
            SttAttemptPhase.SENT,
            -> require(snapshot == null) { "pre-response STT attempt cannot contain a snapshot" }

            SttAttemptPhase.RECEIVED,
            SttAttemptPhase.ADOPTED,
            -> {
                val accepted = requireNotNull(snapshot) { "received STT attempt requires snapshot" }
                require(accepted.sessionId == sessionId) { "STT snapshot session mismatch" }
                require(accepted.sourceAttachmentId == sourceAttachmentId) { "STT snapshot source mismatch" }
                require(accepted.stt.requestProfileId == requestProfileId) { "STT snapshot profile mismatch" }
                require(accepted.pcmSample.wavSha256 == sampleSha256) { "STT snapshot sample mismatch" }
            }
        }
    }

    fun recoveryDisposition(): SttAttemptRecoveryDisposition = when (phase) {
        SttAttemptPhase.PREPARED -> SttAttemptRecoveryDisposition.SAFE_TO_SUBMIT
        SttAttemptPhase.SENT -> SttAttemptRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME
        SttAttemptPhase.RECEIVED -> SttAttemptRecoveryDisposition.RECEIVED_AVAILABLE
        SttAttemptPhase.ADOPTED -> SttAttemptRecoveryDisposition.ADOPTED
    }
}

internal object SttAttemptIdentity {
    fun forInitialSnapshot(sessionId: String, sourceAttachmentId: String, requestProfileId: String): String {
        require(isSafeId(sessionId) && isSafeId(sourceAttachmentId)) { "invalid STT attempt identity input" }
        require(requestProfileId.isNotBlank()) { "blank STT request profile identity" }
        val fields = listOf("stt-initial-attempt-v1", sessionId, sourceAttachmentId, requestProfileId)
        val canonical = fields.joinToString("") { value ->
            "${value.toByteArray(Charsets.UTF_8).size}:$value"
        }
        return "stt-attempt-${sha256Utf8(canonical)}"
    }
}

internal object SttAttemptSampleDigest {
    fun sha256(file: File): String {
        require(file.isFile) { "prepared STT WAV is missing" }
        val expectedSize = file.length()
        require(expectedSize > 44L) { "prepared STT WAV is invalid" }
        val digest = MessageDigest.getInstance("SHA-256")
        var observedSize = 0L
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                digest.update(buffer, 0, count)
                observedSize = Math.addExact(observedSize, count.toLong())
            }
        }
        require(observedSize == expectedSize && file.length() == expectedSize) {
            "prepared STT WAV changed while being fingerprinted"
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }
}

internal object SttAttemptCodec {
    const val MAX_BYTES = SourceSnapshotCodec.MAX_BYTES + 65_536

    fun encode(value: SttAttemptReceipt): String {
        val root = JSONObject()
            .put("schemaVersion", value.schemaVersion)
            .put("attemptId", value.attemptId)
            .put("sessionId", value.sessionId)
            .put("epoch", value.epoch)
            .put("expectedManifestRevision", value.expectedManifestRevision)
            .put("sourceAttachmentId", value.sourceAttachmentId)
            .put("requestProfileId", value.requestProfileId)
            .put("sampleSha256", value.sampleSha256)
            .put("phase", value.phase.name)
            .put("snapshot", value.snapshot?.let { JSONObject(SourceSnapshotCodec.encode(it)) } ?: JSONObject.NULL)
        return root.toString().also(::requireBounded)
    }

    fun decode(json: String): SttAttemptReceipt {
        requireBounded(json)
        val root = JSONObject(json)
        root.requireKeys(setOf(
            "schemaVersion", "attemptId", "sessionId", "epoch", "expectedManifestRevision",
            "sourceAttachmentId", "requestProfileId", "sampleSha256", "phase", "snapshot",
        ))
        val snapshot = if (root.isNull("snapshot")) null else
            SourceSnapshotCodec.decode(root.getJSONObject("snapshot").toString())
        return SttAttemptReceipt(
            schemaVersion = root.getInt("schemaVersion"),
            attemptId = root.getString("attemptId"),
            sessionId = root.getString("sessionId"),
            epoch = root.getLong("epoch"),
            expectedManifestRevision = root.getLong("expectedManifestRevision"),
            sourceAttachmentId = root.getString("sourceAttachmentId"),
            requestProfileId = root.getString("requestProfileId"),
            sampleSha256 = root.getString("sampleSha256"),
            phase = SttAttemptPhase.valueOf(root.getString("phase")),
            snapshot = snapshot,
        )
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "STT attempt exceeds size limit" }
    }

    private fun JSONObject.requireKeys(expected: Set<String>) {
        require(keys().asSequence().toSet() == expected) { "unexpected STT attempt fields" }
    }
}

internal class UnknownSttRemoteOutcomeException(val attemptId: String) : IllegalStateException(
    "STT request may already have been submitted; remote outcome is unknown and automatic repost is forbidden: $attemptId"
)

private fun isLowerSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
