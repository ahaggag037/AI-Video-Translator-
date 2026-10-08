package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.sha256Utf8
import java.net.URI

const val SOURCE_ATTACHMENT_SCHEMA_VERSION = 1

/** A complete byte-stream digest, never a URI/mtime/prefix surrogate. No media bytes are stored. */
data class SourceFingerprint(val sha256: String, val sizeBytes: Long) {
    init {
        require(sha256.length == 64 && sha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "invalid source SHA-256"
        }
        require(sizeBytes > 0L) { "empty source" }
    }
}

data class SourceAudioTrack(
    val containerIndex: Int,
    val mime: String,
    val language: String?,
    val sampleRateHz: Int?,
    val channelCount: Int?,
) {
    init {
        require(containerIndex >= 0) { "negative audio track index" }
        require(mime.startsWith("audio/") && mime.length in 7..128 && mime.none(Char::isWhitespace)) {
            "invalid audio track MIME"
        }
        require(language == null || language.isNotBlank() && language.length <= 128) { "invalid track language" }
        require(sampleRateHz == null || sampleRateHz > 0) { "invalid sample rate" }
        require(channelCount == null || channelCount > 0) { "invalid channel count" }
    }
}

/**
 * Immutable source selection, separate from the accepted STT snapshot/clock mapping (still gated).
 * persistedReadGrantAtCapture records an observation, NOT permission to reopen later.
 * attachmentId binds every field; changing locator, selected range or track creates a new identity.
 * Private app storage only. Never put locators or fingerprints in ordinary logs/relay messages.
 */
data class SourceAttachment(
    val sessionId: String,
    val contentUri: String,
    val persistedReadGrantAtCapture: Boolean,
    val fingerprint: SourceFingerprint,
    val durationUs: Long,
    val selectedRange: PresentationIntervalUs,
    val audioTrack: SourceAudioTrack,
) {
    init {
        require(isSafeId(sessionId)) { "invalid attachment session id" }
        require(contentUri.length in 1..8_192 && contentUri.none(Char::isISOControl)) { "invalid source locator" }
        val uri = try { URI(contentUri) } catch (_: Exception) {
            throw IllegalArgumentException("invalid source locator")
        }
        require(uri.scheme == "content" && !uri.isOpaque && !uri.rawAuthority.isNullOrBlank() &&
            uri.rawUserInfo == null && uri.rawFragment == null && !uri.rawPath.isNullOrBlank()) {
            "source locator must be a content URI"
        }
        require(durationUs > 0L && selectedRange.end.value <= durationUs) { "range outside source duration" }
    }

    val attachmentId: String
        get() {
            val fields = listOf(
                "source-attachment-v1", sessionId, contentUri, persistedReadGrantAtCapture.toString(),
                fingerprint.sha256, fingerprint.sizeBytes.toString(), durationUs.toString(),
                selectedRange.start.value.toString(), selectedRange.end.value.toString(),
                audioTrack.containerIndex.toString(), audioTrack.mime, audioTrack.language,
                audioTrack.sampleRateHz?.toString(), audioTrack.channelCount?.toString(),
            )
            val canonical = fields.joinToString("") { value ->
                if (value == null) "-1:" else "${value.toByteArray(Charsets.UTF_8).size}:$value"
            }
            return "source-${sha256Utf8(canonical)}"
        }
}
