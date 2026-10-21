package com.clw.aivideotranslator.ui.source

import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.source.CandidateProbeTicket
import com.clw.aivideotranslator.source.CandidateRejectReason
import com.clw.aivideotranslator.source.CandidateSourceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceReviewPresentationTest {
    private val ticket = CandidateProbeTicket(1L, "content://video/candidate")

    @Test fun probingKeepsChangeAvailableButNeverAllowsAdoption() {
        val model = SourceReviewPresentation.from(
            state = CandidateSourceState.Probing(ticket),
            metadata = null,
        )

        assertEquals(SourceReviewPhase.PROBING, model.phase)
        assertTrue(model.isBusy)
        assertTrue(model.canChangeVideo)
        assertFalse(model.canAdoptVideo)
        assertEquals("جارٍ فحص الفيديو والصوت قبل اعتماد المصدر.", model.primaryMessage)
        assertEquals("لن يتم استبدال المصدر المحفوظ قبل اكتمال الفحص واعتماد الفيديو.", model.supportingMessage)
    }

    @Test fun readyShowsTrustedMetadataAndEnablesExplicitAdoption() {
        val metadata = VideoMetadata(
            displayName = "رحلة القاهرة.mp4",
            durationMs = 62_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 10_485_760L,
            hasAudio = true,
        )
        val model = SourceReviewPresentation.from(
            state = CandidateSourceState.Ready(ticket, metadata.displayName),
            metadata = metadata,
        )

        assertEquals(SourceReviewPhase.READY, model.phase)
        assertFalse(model.isBusy)
        assertTrue(model.canChangeVideo)
        assertTrue(model.canAdoptVideo)
        assertEquals("رحلة القاهرة.mp4", model.displayName)
        assertEquals(metadata.durationLabel, model.durationLabel)
        assertEquals(metadata.resolutionLabel, model.resolutionLabel)
        assertEquals(metadata.sizeLabel, model.sizeLabel)
        assertEquals("الصوت جاهز", model.audioLabel)
        assertNull(model.errorMessage)
    }

    @Test fun rejectedNoAudioIsTypedAndCannotBeAdopted() {
        val model = SourceReviewPresentation.from(
            state = CandidateSourceState.Rejected(ticket, CandidateRejectReason.NO_AUDIO),
            metadata = null,
        )

        assertEquals(SourceReviewPhase.REJECTED, model.phase)
        assertTrue(model.canChangeVideo)
        assertFalse(model.canAdoptVideo)
        assertEquals("لا يحتوي الفيديو على مسار صوت صالح للترجمة.", model.errorMessage)
    }

    @Test fun rejectedAccessLimitedDoesNotLeakPrivateUri() {
        val model = SourceReviewPresentation.from(
            state = CandidateSourceState.Rejected(ticket, CandidateRejectReason.ACCESS_LIMITED),
            metadata = null,
        )

        assertEquals("تعذر الوصول إلى الفيديو. اختره مرة أخرى واسمح للتطبيق بالوصول إليه.", model.errorMessage)
        assertFalse(model.errorMessage.orEmpty().contains("content://"))
    }

    @Test fun activatingLocksCandidateUntilDurableCommitSettles() {
        val model = SourceReviewPresentation.from(
            state = CandidateSourceState.Activating(ticket),
            metadata = null,
        )

        assertEquals(SourceReviewPhase.ACTIVATING, model.phase)
        assertTrue(model.isBusy)
        assertFalse(model.canChangeVideo)
        assertFalse(model.canAdoptVideo)
        assertEquals("جارٍ اعتماد المصدر بأمان…", model.primaryMessage)
    }

    @Test fun activationFailureReturnsReadyAndRemainsRetryable() {
        val state = CandidateSourceState.Ready(
            ticket = ticket,
            displayName = "clip.mp4",
            lastActivationError = "تعذر اعتماد المصدر. بقي المشروع السابق كما هو.",
        )
        val model = SourceReviewPresentation.from(state, metadata = null)

        assertEquals(SourceReviewPhase.READY, model.phase)
        assertTrue(model.canChangeVideo)
        assertTrue(model.canAdoptVideo)
        assertEquals("تعذر اعتماد المصدر. بقي المشروع السابق كما هو.", model.errorMessage)
    }
}
