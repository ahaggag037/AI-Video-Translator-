package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.junit.Assert.*
import org.junit.Test

class SourceResumeEvaluatorTest {
    private val attachment = SourceAttachment(
        "s1", "content://synthetic.documents/video/1", true, SourceFingerprint("a".repeat(64), 100),
        10_000_000, PresentationIntervalUs(PresentationTimeUs(1_000_000), PresentationTimeUs(4_000_000)),
        SourceAudioTrack(1, "audio/aac", null, null, null),
    )
    private val manifest = SessionManifest(
        sessionId = "s1", revision = 3, epoch = 1, activeEntryRefs = emptyMap(),
        sourceBindingState = SourceBindingState.ATTACHMENT_BOUND, activeSourceAttachmentRef = attachment.attachmentId,
    )
    private fun observation(status: SourceReadStatus = SourceReadStatus.READABLE) = SourceReadObservation(
        SourceProbeToken.from(manifest), attachment.contentUri, status,
        if (status == SourceReadStatus.READABLE) attachment.fingerprint else null,
        persistedReadGrantNow = false,
    )

    @Test fun persistedGrantAloneNeverProvesAvailableAndRevocationIsVisible() {
        assertEquals(SourceAvailability.CHECK_REQUIRED, SourceResumeEvaluator.evaluate(manifest, attachment, null).availability)
        assertEquals(SourceAvailability.PERMISSION_MISSING,
            SourceResumeEvaluator.evaluate(manifest, attachment, observation(SourceReadStatus.PERMISSION_MISSING)).availability)
    }

    @Test fun sameUriAndSizeButDifferentFullDigestMeansSourceChanged() {
        val changed = observation().copy(fingerprint = SourceFingerprint("b".repeat(64), 100))
        assertEquals(SourceAvailability.SOURCE_CHANGED, SourceResumeEvaluator.evaluate(manifest, attachment, changed).availability)
        val changedSize = observation().copy(fingerprint = SourceFingerprint("a".repeat(64), 101))
        assertEquals(SourceAvailability.SOURCE_CHANGED, SourceResumeEvaluator.evaluate(manifest, attachment, changedSize).availability)
    }

    @Test fun freshCompleteReadCanBeAvailableWithoutPromiseOfPersistentPermission() {
        val result = SourceResumeEvaluator.evaluate(manifest, attachment, observation())
        assertEquals(SourceAvailability.AVAILABLE, result.availability)
        assertFalse(result.persistedReadGrantNow)
        // Availability alone does not bind a source snapshot or authorize new production behavior.
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, manifest.sourceBindingState)
        assertNull(manifest.activeSourceSnapshotRef)
    }

    @Test fun epochRevisionSessionAttachmentAndLocatorChangesFenceLateProbe() {
        val probe = observation()
        listOf(
            probe.copy(token = probe.token.copy(epoch = 0)),
            probe.copy(token = probe.token.copy(manifestRevision = 2)),
            probe.copy(token = probe.token.copy(sessionId = "other")),
            probe.copy(token = probe.token.copy(attachmentId = "other")),
            probe.copy(observedContentUri = "content://synthetic.documents/video/2"),
        ).forEach {
            assertEquals(SourceAvailability.STALE_OBSERVATION, SourceResumeEvaluator.evaluate(manifest, attachment, it).availability)
        }
    }

    @Test fun missingEmptyIoAndUnsupportedRemainExplicit() {
        listOf(
            SourceReadStatus.SOURCE_MISSING to SourceAvailability.SOURCE_MISSING,
            SourceReadStatus.EMPTY_SOURCE to SourceAvailability.SOURCE_CHANGED,
            SourceReadStatus.IO_FAILURE to SourceAvailability.IO_FAILURE,
            SourceReadStatus.UNSUPPORTED to SourceAvailability.UNSUPPORTED,
        ).forEach { (status, expected) ->
            assertEquals(expected, SourceResumeEvaluator.evaluate(manifest, attachment, observation(status)).availability)
        }
    }

    @Test fun danglingAndCrossSessionReferencesFailClosedWithoutDeletingHistory() {
        assertEquals(SourceAvailability.CORRUPT_BINDING, SourceResumeEvaluator.evaluate(manifest, null, observation()).availability)
        assertEquals(SourceAvailability.CORRUPT_BINDING,
            SourceResumeEvaluator.evaluate(manifest, attachment.copy(sessionId = "other"), observation()).availability)
        val legacy = SessionManifest(sessionId = "s1", revision = 0, epoch = 0,
            activeEntryRefs = mapOf("u1" to "manual-history"), sourceBindingState = SourceBindingState.LEGACY_UNBOUND)
        assertEquals(SourceAvailability.LEGACY_UNBOUND, SourceResumeEvaluator.evaluate(legacy, attachment, observation()).availability)
        assertEquals("manual-history", legacy.activeEntryRefs["u1"])
    }
}
