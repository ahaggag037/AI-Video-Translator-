package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceAttachmentTest {
    private fun attachment() = SourceAttachment(
        sessionId = "session-1", contentUri = "content://synthetic.documents/video/1",
        persistedReadGrantAtCapture = true,
        fingerprint = SourceFingerprint("a".repeat(64), 123_456), durationUs = 42_000_000,
        selectedRange = PresentationIntervalUs(PresentationTimeUs(500_000), PresentationTimeUs(12_000_000)),
        audioTrack = SourceAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun rejected(block: () -> Unit) {
        try { block(); fail("must fail closed") } catch (_: IllegalArgumentException) { }
    }

    @Test fun roundTripPreservesSelectedTrackNonzeroRangeAndHistoricalGrant() {
        val value = attachment()
        assertEquals(value, SourceAttachmentCodec.decode(SourceAttachmentCodec.encode(value)))
        assertTrue(isSafeId(value.attachmentId))
        assertEquals(1, SOURCE_ATTACHMENT_SCHEMA_VERSION)
        assertEquals(2, TRANSLATION_MANIFEST_SCHEMA_VERSION)
        assertEquals(1, TRANSLATION_ENTRY_SCHEMA_VERSION)
        assertEquals(1, TRANSLATION_RECEIPT_SCHEMA_VERSION)
    }

    @Test fun everyPersistedFieldParticipatesInContentAddress() {
        val original = attachment()
        val variants = listOf(
            original.copy(sessionId = "session-2"),
            original.copy(contentUri = "content://synthetic.documents/video/2"),
            original.copy(persistedReadGrantAtCapture = false),
            original.copy(fingerprint = original.fingerprint.copy(sha256 = "b".repeat(64))),
            original.copy(fingerprint = original.fingerprint.copy(sizeBytes = 123_457)),
            original.copy(durationUs = 43_000_000),
            original.copy(selectedRange = PresentationIntervalUs(PresentationTimeUs(600_000), original.selectedRange.end)),
            original.copy(selectedRange = PresentationIntervalUs(original.selectedRange.start, PresentationTimeUs(13_000_000))),
            original.copy(audioTrack = original.audioTrack.copy(containerIndex = 3)),
            original.copy(audioTrack = original.audioTrack.copy(mime = "audio/opus")),
            original.copy(audioTrack = original.audioTrack.copy(language = null)),
            original.copy(audioTrack = original.audioTrack.copy(sampleRateHz = null)),
            original.copy(audioTrack = original.audioTrack.copy(channelCount = null)),
        )
        assertEquals(variants.size, variants.map { it.attachmentId }.toSet().size)
        variants.forEach { assertNotEquals(original.attachmentId, it.attachmentId) }
        assertEquals(original.attachmentId, original.copy().attachmentId)
    }

    @Test fun validJsonMutationCannotKeepOldAttachmentIdentity() {
        val json = JSONObject(SourceAttachmentCodec.encode(attachment()))
        listOf("contentUri" to "content://synthetic.documents/video/changed", "sha256" to "b".repeat(64)).forEach { (key, value) ->
            val changed = JSONObject(json.toString()).put(key, value)
            rejected { SourceAttachmentCodec.decode(changed.toString()) }
        }
    }

    @Test fun malformedVersionsFieldsAndCoercedNumbersAreNotRepaired() {
        val json = SourceAttachmentCodec.encode(attachment())
        listOf(
            JSONObject(json).put("schemaVersion", 2),
            JSONObject(json).put("rangeStartUs", "500000"),
            JSONObject(json).put("rangeStartUs", 500000.25),
            JSONObject(json).put("persistedReadGrantAtCapture", "true"),
            JSONObject(json).put("unexpected", "discarding me would hide schema drift"),
            JSONObject(json).apply { remove("durationUs") },
        ).forEach { rejected { SourceAttachmentCodec.decode(it.toString()) } }
    }

    @Test fun longMicrosecondsRoundTripWithoutDoublePrecisionLoss() {
        val exact = 9_007_199_254_740_993L
        val value = attachment().copy(
            durationUs = Long.MAX_VALUE,
            selectedRange = PresentationIntervalUs(PresentationTimeUs(exact), PresentationTimeUs(exact + 1)),
        )
        assertEquals(exact, SourceAttachmentCodec.decode(SourceAttachmentCodec.encode(value)).selectedRange.start.value)
    }

    @Test fun nonContentLocatorsOutOfRangeAndInvalidFingerprintFailClosed() {
        listOf("/storage/video.mp4", "file:///data/video.mp4", "https://example.com/video", "content:opaque", "content:///video", "content://p/video#fragment").forEach {
            rejected { attachment().copy(contentUri = it) }
        }
        rejected { attachment().copy(durationUs = 1) }
        rejected { SourceFingerprint("metadata-only", 12) }
        rejected { SourceFingerprint("A".repeat(64), 12) }
        rejected { SourceFingerprint("a".repeat(64), 0) }
        rejected { attachment().copy(audioTrack = attachment().audioTrack.copy(containerIndex = -1)) }
    }

    @Test fun pathDotSegmentsCannotNameSessionsOrSourceAttachments() {
        for (id in listOf(".", "..", "../outside", "folder/name")) {
            assertFalse(isSafeId(id))
            rejected { attachment().copy(sessionId = id) }
            rejected { SessionManifest(sessionId = id, revision = 0, epoch = 0, activeEntryRefs = emptyMap()) }
        }
        assertTrue(isSafeId("session.v1"))
    }
}
