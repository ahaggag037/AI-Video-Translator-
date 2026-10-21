package com.clw.aivideotranslator.ui.processing

import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableSttUiState
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState

internal sealed interface ProcessingResolution {
    data class Show(val presentation: ProcessingPresentation) : ProcessingResolution
    data object ProviderRecovery : ProcessingResolution
}

/**
 * Pure projection from durable engine truth to processing UX. The current ViewModel exposes RUNNING
 * but not the exact PREPARED/SENT journal boundary while a request is in flight, so RUNNING is
 * intentionally rendered with providerSubmission=UNKNOWN and never exposes Safe Stop.
 */
internal object ProcessingPresentationResolver {
    fun resolve(
        stt: DurableSttUiState,
        translation: DurableTranslationUiState,
    ): ProcessingResolution {
        if (
            stt.phase == DurableSttPhase.UNKNOWN_REMOTE_OUTCOME ||
            translation.phase == DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME
        ) {
            return ProcessingResolution.ProviderRecovery
        }

        if (translation.phase == DurableTranslationPhase.RUNNING) {
            return ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.TRANSLATION,
                    providerSubmission = ProviderSubmissionState.UNKNOWN,
                    safeStopAvailable = false,
                )
            )
        }

        if (
            translation.phase == DurableTranslationPhase.LIVE_SUCCESS ||
            translation.phase == DurableTranslationPhase.RECOVERED_TEXT_ONLY
        ) {
            return ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.REVIEW_PREPARATION,
                    providerSubmission = ProviderSubmissionState.NOT_SENT,
                    safeStopAvailable = false,
                )
            )
        }

        if (translation.phase == DurableTranslationPhase.BLOCKED) {
            return ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.REVIEW_PREPARATION,
                    providerSubmission = ProviderSubmissionState.NOT_SENT,
                    safeStopAvailable = false,
                    detail = "تحتاج نتيجة الترجمة إلى مراجعة قبل المتابعة.",
                )
            )
        }

        if (translation.phase == DurableTranslationPhase.FAILED) {
            return ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.TRANSLATION,
                    providerSubmission = ProviderSubmissionState.UNKNOWN,
                    safeStopAvailable = false,
                    detail = "تعذر إكمال الترجمة. لم تُعتبر العملية نجاحًا.",
                )
            )
        }

        return when (stt.phase) {
            DurableSttPhase.RUNNING -> ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.STT,
                    providerSubmission = ProviderSubmissionState.UNKNOWN,
                    safeStopAvailable = false,
                )
            )

            DurableSttPhase.LIVE_SUCCESS,
            DurableSttPhase.RECOVERED,
            -> ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.TEXT_PREPARATION,
                    providerSubmission = ProviderSubmissionState.NOT_SENT,
                    safeStopAvailable = false,
                )
            )

            DurableSttPhase.FAILED -> ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.STT,
                    providerSubmission = ProviderSubmissionState.UNKNOWN,
                    safeStopAvailable = false,
                    detail = "تعذر إكمال تحويل الكلام إلى نص. لم تُعتبر العملية نجاحًا.",
                )
            )

            DurableSttPhase.IDLE -> ProcessingResolution.Show(
                ProcessingPresentation(
                    stage = ProcessingStage.AUDIO_PREPARATION,
                    providerSubmission = ProviderSubmissionState.NOT_SENT,
                    safeStopAvailable = false,
                )
            )

            DurableSttPhase.UNKNOWN_REMOTE_OUTCOME -> ProcessingResolution.ProviderRecovery
        }
    }
}
