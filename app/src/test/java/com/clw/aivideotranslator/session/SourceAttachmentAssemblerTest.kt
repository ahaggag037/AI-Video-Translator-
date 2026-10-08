package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SourceAttachmentAssemblerTest {
    private fun readableInspection(
        uri: String = "content://synthetic.documents/video/9",
        sha: String = "b".repeat(64),
        size: Long = 654_321,
        grant: Boolean = true,
    ) = SourceContentInspection(
        observedContentUri = uri,
        status = SourceReadStatus.READABLE,
        fingerprint = SourceFingerprint(sha, size),
        persistedReadGrantNow = grant,
    )

    private fun track(
        mime: String = "audio/mp4a-latm",
    ) = SourceAudioTrack(
        containerIndex = 1,
        mime = mime,
        language = "en",
        sampleRateHz = 48_000,
        channelCount = 2,
    )

    private fun assemble(
        inspection: SourceContentInspection = readableInspection(),
        durationMs: Long = 673_000,
        track: SourceAudioTrack = track(),
        range: PresentationIntervalUs? = null,
    ) = SourceAttachmentAssembler.assemble(
        sessionId = "session-9",
        inspection = inspection,
        durationMs = durationMs,
        audioTrack = track,
        requestedRange = range,
    )

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("must fail closed")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test fun readableEvidenceAssemblesImmutableAttachmentWithFullRangeDefault() {
        val attachment = assemble()
        assertEquals("session-9", attachment.sessionId)
        assertEquals("content://synthetic.documents/video/9", attachment.contentUri)
        assertTrue(attachment.persistedReadGrantAtCapture)
        assertEquals(673_000_000L, attachment.durationUs)
        assertEquals(0L, attachment.selectedRange.start.value)
        assertEquals(673_000_000L, attachment.selectedRange.end.value)
        assertEquals("b".repeat(64), attachment.fingerprint.sha256)
        assertEquals(654_321L, attachment.fingerprint.sizeBytes)
        assertEquals(1, attachment.audioTrack.containerIndex)
        assertEquals("audio/mp4a-latm", attachment.audioTrack.mime)
    }

    @Test fun nonReadableEvidenceNeverBecomesAnAttachment() {
        listOf(
            SourceReadStatus.PERMISSION_MISSING,
            SourceReadStatus.SOURCE_MISSING,
            SourceReadStatus.EMPTY_SOURCE,
            SourceReadStatus.IO_FAILURE,
            SourceReadStatus.UNSUPPORTED,
        ).forEach { status ->
            rejected {
                assemble(inspection = SourceContentInspection("content://synthetic.documents/video/9", status))
            }
        }
    }

    @Test fun unknownOrNonPositiveDurationFailsClosed() {
        rejected { assemble(durationMs = 0L) }
        rejected { assemble(durationMs = -5L) }
    }

    @Test fun requestedRangeIsKeptButMustStayInsideObservedDuration() {
        val ranged = assemble(
            range = PresentationIntervalUs(PresentationTimeUs(1_000_000), PresentationTimeUs(2_000_000)),
        )
        assertEquals(1_000_000L, ranged.selectedRange.start.value)
        assertEquals(2_000_000L, ranged.selectedRange.end.value)

        rejected {
            assemble(
                range = PresentationIntervalUs(PresentationTimeUs(1_000_000), PresentationTimeUs(674_000_000)),
            )
        }
    }

    @Test fun attachmentIdentityChangesWithLocatorGrantFingerprintRangeOrTrack() {
        val base = assemble()
        assertNotEquals(
            base.attachmentId,
            assemble(inspection = readableInspection(uri = "content://synthetic.documents/video/10")).attachmentId,
        )
        assertNotEquals(
            base.attachmentId,
            assemble(inspection = readableInspection(grant = false)).attachmentId,
        )
        assertNotEquals(
            base.attachmentId,
            assemble(inspection = readableInspection(sha = "c".repeat(64))).attachmentId,
        )
        assertNotEquals(
            base.attachmentId,
            assemble(inspection = readableInspection(size = 654_322)).attachmentId,
        )
        assertNotEquals(
            base.attachmentId,
            assemble(
                range = PresentationIntervalUs(PresentationTimeUs(0L), PresentationTimeUs(600_000_000)),
            ).attachmentId,
        )
        assertNotEquals(
            base.attachmentId,
            assemble(track = track(mime = "audio/opus")).attachmentId,
        )
    }

    @Test fun capturedGrantIsAnObservationNotAPermissionClaim() {
        val withoutGrant = assemble(inspection = readableInspection(grant = false))
        assertFalse(withoutGrant.persistedReadGrantAtCapture)
        assertEquals("session-9", withoutGrant.sessionId)
    }
}
