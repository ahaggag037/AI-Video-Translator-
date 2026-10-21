package com.clw.aivideotranslator.ui.processing

import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableSttUiState
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState
import com.clw.aivideotranslator.session.DurableTranslationUnitDisposition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessingPresentationResolverTest {
    @Test fun runningSttWithoutJournalPhaseMakesNoSentOrPreSentClaim() {
        val resolution = ProcessingPresentationResolver.resolve(
            stt = DurableSttUiState(
                phase = DurableSttPhase.RUNNING,
                sessionId = "session-a",
            ),
            translation = DurableTranslationUiState.idle(),
        )

        val show = resolution as ProcessingResolution.Show
        assertEquals(ProcessingStage.STT, show.presentation.stage)
        assertEquals(ProviderSubmissionState.UNKNOWN, show.presentation.providerSubmission)
        assertFalse(show.presentation.safeStopAvailable)
    }

    @Test fun sttUnknownRemoteOutcomeRoutesToProviderRecoveryInsteadOfRetryUi() {
        val resolution = ProcessingPresentationResolver.resolve(
            stt = DurableSttUiState(
                phase = DurableSttPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = "session-a",
            ),
            translation = DurableTranslationUiState.idle(),
        )

        assertTrue(resolution is ProcessingResolution.ProviderRecovery)
    }

    @Test fun runningTranslationOwnsTranslationStageWithoutInventedProgress() {
        val resolution = ProcessingPresentationResolver.resolve(
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState(
                phase = DurableTranslationPhase.RUNNING,
                sessionId = "session-a",
            ),
        )

        val show = resolution as ProcessingResolution.Show
        assertEquals(ProcessingStage.TRANSLATION, show.presentation.stage)
        assertEquals(ProviderSubmissionState.UNKNOWN, show.presentation.providerSubmission)
        assertFalse(show.presentation.safeStopAvailable)
    }

    @Test fun translationUnknownRemoteOutcomeRoutesToProviderRecovery() {
        val resolution = ProcessingPresentationResolver.resolve(
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState(
                phase = DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = "session-a",
                blocker = DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME,
            ),
        )

        assertTrue(resolution is ProcessingResolution.ProviderRecovery)
    }

    @Test fun idleProviderStateStartsAtLocalAudioPreparationWithoutSubmissionClaim() {
        val resolution = ProcessingPresentationResolver.resolve(
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState.idle(),
        )

        val show = resolution as ProcessingResolution.Show
        assertEquals(ProcessingStage.AUDIO_PREPARATION, show.presentation.stage)
        assertEquals(ProviderSubmissionState.NOT_SENT, show.presentation.providerSubmission)
        assertFalse(show.presentation.safeStopAvailable)
    }
}
