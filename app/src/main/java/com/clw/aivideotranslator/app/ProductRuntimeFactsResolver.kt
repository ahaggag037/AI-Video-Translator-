package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.resume.ReopenFacts
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState
import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableSttUiState
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState
import com.clw.aivideotranslator.ui.home.HomeFacts
import com.clw.aivideotranslator.ui.home.HomeStatus
import com.clw.aivideotranslator.ui.home.HomeStatusResolver

internal data class ProductRuntimeFacts(
    val homeStatus: HomeStatus,
    val reopenFacts: ReopenFacts,
)

/**
 * Pure adapter from durable engine state to product routing/summary facts.
 * It never starts provider work, mutates persistence, or owns credentials.
 */
internal object ProductRuntimeFactsResolver {
    fun resolve(
        source: DurableSourceUiState,
        stt: DurableSttUiState,
        translation: DurableTranslationUiState,
        credentialUsable: Boolean,
        sessionReadable: Boolean = true,
        hasInterruptedExport: Boolean = false,
        hasValidatedOutputForCurrentRevision: Boolean = false,
    ): ProductRuntimeFacts {
        val durableSessionIds = listOfNotNull(
            source.sessionId,
            stt.sessionId,
            translation.sessionId,
        ).toSet()
        val hasActiveSession = durableSessionIds.isNotEmpty()
        val sourceAvailable = source.phase == DurableSourcePhase.BOUND
        val providerUnknown =
            stt.phase == DurableSttPhase.UNKNOWN_REMOTE_OUTCOME ||
                translation.phase == DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME
        val processingActive =
            source.phase == DurableSourcePhase.CAPTURING ||
                stt.phase == DurableSttPhase.RUNNING ||
                translation.phase == DurableTranslationPhase.RUNNING
        val hasAcceptedText = translation.entries.isNotEmpty() &&
            (translation.phase == DurableTranslationPhase.LIVE_SUCCESS ||
                translation.phase == DurableTranslationPhase.RECOVERED_TEXT_ONLY)
        val nextStepNeedsProvider = hasActiveSession && sourceAvailable && !hasAcceptedText &&
            !providerUnknown && !processingActive

        val homeStatus = HomeStatusResolver.resolve(
            HomeFacts(
                hasActiveSession = hasActiveSession,
                providerUnknown = providerUnknown,
                sourceUnavailable = hasActiveSession && !sourceAvailable,
                processingActive = processingActive,
            ),
        )

        return ProductRuntimeFacts(
            homeStatus = homeStatus,
            reopenFacts = ReopenFacts(
                hasActiveSession = hasActiveSession,
                sessionReadable = sessionReadable,
                hasUnknownProviderOutcome = providerUnknown,
                hasInterruptedExport = hasInterruptedExport,
                hasLocalProcessingToResume = processingActive,
                nextStepNeedsProvider = nextStepNeedsProvider,
                credentialUsable = credentialUsable,
                sourceAvailable = sourceAvailable,
                hasValidatedOutputForCurrentRevision = hasValidatedOutputForCurrentRevision,
                hasAcceptedText = hasAcceptedText,
            ),
        )
    }
}
