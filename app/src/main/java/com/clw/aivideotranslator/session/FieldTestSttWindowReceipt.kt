package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.semantic.sha256Utf8
import org.json.JSONArray
import org.json.JSONObject

internal const val FIELD_TEST_STT_WINDOW_RECEIPT_SCHEMA = 1

internal enum class FieldTestSttWindowAttemptPhase {
    PREPARED,
    SENT,
    RECEIVED,
}

internal enum class FieldTestSttWindowRecoveryDisposition {
    SAFE_TO_SUBMIT,
    UNKNOWN_REMOTE_OUTCOME,
    RECEIVED_AVAILABLE,
}

/**
 * Field-test-only durable receipt for one exact full-video STT window.
 *
 * The operation identity deliberately excludes requestProfileId. If an app/profile upgrade happens
 * after SENT, the same source/window still resolves to the old receipt and blocks blind repost.
 */
internal data class FieldTestSttWindowReceipt(
    val schemaVersion: Int = FIELD_TEST_STT_WINDOW_RECEIPT_SCHEMA,
    val attemptId: String,
    val sessionId: String,
    val sourceAttachmentId: String,
    val windowIndex: Int,
    val startUs: Long,
    val endUs: Long,
    val requestProfileId: String,
    val sampleSha256: String,
    val phase: FieldTestSttWindowAttemptPhase,
    val result: NvidiaSttResult? = null,
) {
    init {
        require(schemaVersion == FIELD_TEST_STT_WINDOW_RECEIPT_SCHEMA)
        require(isSafeId(attemptId) && isSafeId(sessionId) && isSafeId(sourceAttachmentId)) {
            "invalid field-test STT receipt identity"
        }
        require(windowIndex >= 0 && startUs >= 0L && endUs > startUs) { "invalid field-test STT window" }
        require(requestProfileId.isNotBlank() && requestProfileId.length <= 512 &&
            requestProfileId.none(Char::isISOControl)) { "invalid field-test STT request profile"
        }
        require(isLowerSha256(sampleSha256)) { "invalid field-test STT sample digest" }
        when (phase) {
            FieldTestSttWindowAttemptPhase.PREPARED,
            FieldTestSttWindowAttemptPhase.SENT,
            -> require(result == null) { "pre-response field-test receipt cannot contain a result" }

            FieldTestSttWindowAttemptPhase.RECEIVED -> {
                val accepted = requireNotNull(result) { "RECEIVED field-test receipt requires a result" }
                require(accepted.httpStatus in 200..299) { "RECEIVED field-test STT result is not successful" }
                val maxRelativeMs = (endUs - startUs + 999L) / 1_000L
                accepted.words.forEach { word ->
                    val wordStart = requireNotNull(word.startMs) { "RECEIVED field-test STT word is untimed" }
                    val wordEnd = requireNotNull(word.endMs) { "RECEIVED field-test STT word is untimed" }
                    require(wordStart >= 0L && wordEnd > wordStart && wordEnd <= maxRelativeMs) {
                        "RECEIVED field-test STT word lies outside its window"
                    }
                }
            }
        }
    }

    val window: FieldTestSttWindow get() = FieldTestSttWindow(windowIndex, startUs, endUs)

    fun recoveryDisposition(): FieldTestSttWindowRecoveryDisposition = when (phase) {
        FieldTestSttWindowAttemptPhase.PREPARED -> FieldTestSttWindowRecoveryDisposition.SAFE_TO_SUBMIT
        FieldTestSttWindowAttemptPhase.SENT -> FieldTestSttWindowRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME
        FieldTestSttWindowAttemptPhase.RECEIVED -> FieldTestSttWindowRecoveryDisposition.RECEIVED_AVAILABLE
    }
}

internal object FieldTestSttWindowAttemptIdentity {
    fun forWindow(
        sessionId: String,
        sourceAttachmentId: String,
        window: FieldTestSttWindow,
    ): String {
        require(isSafeId(sessionId) && isSafeId(sourceAttachmentId)) { "invalid field-test STT identity input" }
        val fields = listOf(
            "field-test-stt-window-v1",
            sessionId,
            sourceAttachmentId,
            window.index.toString(),
            window.startUs.toString(),
            window.endUs.toString(),
        )
        val canonical = fields.joinToString("") { value ->
            "${value.toByteArray(Charsets.UTF_8).size}:$value"
        }
        return "field-stt-${sha256Utf8(canonical)}"
    }
}

internal object FieldTestSttWindowReceiptCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun encode(receipt: FieldTestSttWindowReceipt): String {
        val root = JSONObject()
            .put("schemaVersion", receipt.schemaVersion)
            .put("attemptId", receipt.attemptId)
            .put("sessionId", receipt.sessionId)
            .put("sourceAttachmentId", receipt.sourceAttachmentId)
            .put("windowIndex", receipt.windowIndex)
            .put("startUs", receipt.startUs)
            .put("endUs", receipt.endUs)
            .put("requestProfileId", receipt.requestProfileId)
            .put("sampleSha256", receipt.sampleSha256)
            .put("phase", receipt.phase.name)
            .put("result", receipt.result?.let(::encodeResult) ?: JSONObject.NULL)
        return root.toString().also(::requireBounded)
    }

    fun decode(json: String): FieldTestSttWindowReceipt {
        requireBounded(json)
        val root = JSONObject(json)
        require(root.keys().asSequence().toSet() == setOf(
            "schemaVersion", "attemptId", "sessionId", "sourceAttachmentId", "windowIndex",
            "startUs", "endUs", "requestProfileId", "sampleSha256", "phase", "result",
        )) { "unexpected field-test STT receipt fields" }
        return FieldTestSttWindowReceipt(
            schemaVersion = root.getInt("schemaVersion"),
            attemptId = root.getString("attemptId"),
            sessionId = root.getString("sessionId"),
            sourceAttachmentId = root.getString("sourceAttachmentId"),
            windowIndex = root.getInt("windowIndex"),
            startUs = root.getLong("startUs"),
            endUs = root.getLong("endUs"),
            requestProfileId = root.getString("requestProfileId"),
            sampleSha256 = root.getString("sampleSha256"),
            phase = FieldTestSttWindowAttemptPhase.valueOf(root.getString("phase")),
            result = if (root.isNull("result")) null else decodeResult(root.getJSONObject("result")),
        )
    }

    private fun encodeResult(result: NvidiaSttResult): JSONObject = JSONObject()
        .put("transcript", result.transcript)
        .put("httpStatus", result.httpStatus)
        .put("words", JSONArray().apply {
            result.words.forEach { word ->
                put(JSONObject()
                    .put("text", word.text)
                    .put("startMs", word.startMs ?: JSONObject.NULL)
                    .put("endMs", word.endMs ?: JSONObject.NULL)
                    .put("confidence", word.confidence ?: JSONObject.NULL))
            }
        })

    private fun decodeResult(root: JSONObject): NvidiaSttResult {
        require(root.keys().asSequence().toSet() == setOf("transcript", "httpStatus", "words")) {
            "unexpected field-test STT result fields"
        }
        val words = root.getJSONArray("words")
        return NvidiaSttResult(
            transcript = root.getString("transcript"),
            words = List(words.length()) { index ->
                val word = words.getJSONObject(index)
                require(word.keys().asSequence().toSet() == setOf("text", "startMs", "endMs", "confidence")) {
                    "unexpected field-test STT word fields"
                }
                NvidiaWord(
                    text = word.getString("text"),
                    startMs = if (word.isNull("startMs")) null else word.getLong("startMs"),
                    endMs = if (word.isNull("endMs")) null else word.getLong("endMs"),
                    confidence = if (word.isNull("confidence")) null else word.getDouble("confidence"),
                )
            },
            httpStatus = root.getInt("httpStatus"),
        )
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "field-test STT receipt exceeds size limit" }
    }
}

private fun isLowerSha256(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
