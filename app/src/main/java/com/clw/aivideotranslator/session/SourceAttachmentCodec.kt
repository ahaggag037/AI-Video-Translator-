package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.json.JSONObject

/** Independent schema; manifest v2 and entry/receipt v1 are not rewritten by this additive codec. */
object SourceAttachmentCodec {
    const val MAX_BYTES = 65_536

    fun encode(value: SourceAttachment): String = JSONObject()
        .put("schemaVersion", SOURCE_ATTACHMENT_SCHEMA_VERSION)
        .put("attachmentId", value.attachmentId)
        .put("sessionId", value.sessionId)
        .put("contentUri", value.contentUri)
        .put("persistedReadGrantAtCapture", value.persistedReadGrantAtCapture)
        .put("sha256", value.fingerprint.sha256)
        .put("sizeBytes", value.fingerprint.sizeBytes)
        .put("durationUs", value.durationUs)
        .put("rangeStartUs", value.selectedRange.start.value)
        .put("rangeEndUs", value.selectedRange.end.value)
        .put("audioTrack", JSONObject()
            .put("containerIndex", value.audioTrack.containerIndex)
            .put("mime", value.audioTrack.mime)
            .put("language", value.audioTrack.language ?: JSONObject.NULL)
            .put("sampleRateHz", value.audioTrack.sampleRateHz ?: JSONObject.NULL)
            .put("channelCount", value.audioTrack.channelCount ?: JSONObject.NULL))
        .toString().also(::requireBounded)

    fun decode(json: String): SourceAttachment {
        requireBounded(json)
        val root = JSONObject(json)
        root.requireKeys(setOf("schemaVersion", "attachmentId", "sessionId", "contentUri",
            "persistedReadGrantAtCapture", "sha256", "sizeBytes", "durationUs", "rangeStartUs",
            "rangeEndUs", "audioTrack"))
        require(root.strictLong("schemaVersion") == SOURCE_ATTACHMENT_SCHEMA_VERSION.toLong()) {
            "unsupported source attachment schema"
        }
        val track = root.getJSONObject("audioTrack")
        track.requireKeys(setOf("containerIndex", "mime", "language", "sampleRateHz", "channelCount"))
        val value = SourceAttachment(
            sessionId = root.strictString("sessionId"),
            contentUri = root.strictString("contentUri"),
            persistedReadGrantAtCapture = root.get("persistedReadGrantAtCapture").let {
                require(it is Boolean) { "invalid grant observation" }; it
            },
            fingerprint = SourceFingerprint(root.strictString("sha256"), root.strictLong("sizeBytes")),
            durationUs = root.strictLong("durationUs"),
            selectedRange = PresentationIntervalUs(
                PresentationTimeUs(root.strictLong("rangeStartUs")), PresentationTimeUs(root.strictLong("rangeEndUs")),
            ),
            audioTrack = SourceAudioTrack(
                containerIndex = Math.toIntExact(track.strictLong("containerIndex")),
                mime = track.strictString("mime"),
                language = if (track.isNull("language")) null else track.strictString("language"),
                sampleRateHz = if (track.isNull("sampleRateHz")) null else Math.toIntExact(track.strictLong("sampleRateHz")),
                channelCount = if (track.isNull("channelCount")) null else Math.toIntExact(track.strictLong("channelCount")),
            ),
        )
        require(root.strictString("attachmentId") == value.attachmentId) { "source attachment identity mismatch" }
        return value
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "source attachment too large" }
    }

    private fun JSONObject.strictLong(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("source integer field has wrong type: $key")
    }

    private fun JSONObject.strictString(key: String): String {
        val value = get(key)
        require(value is String) { "source text field has wrong type: $key" }
        return value
    }

    private fun JSONObject.requireKeys(expected: Set<String>) {
        require(keys().asSequence().toSet() == expected) { "unexpected source attachment fields" }
    }
}
