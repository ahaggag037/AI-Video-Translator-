package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.resume.ReopenResolver
import com.clw.aivideotranslator.session.DurableSourceFailure
import com.clw.aivideotranslator.session.DurableSourcePhase
import com.clw.aivideotranslator.session.DurableSourceUiState
import com.clw.aivideotranslator.session.DurableSttPhase
import com.clw.aivideotranslator.session.DurableSttUiState
import com.clw.aivideotranslator.session.DurableTranslationPhase
import com.clw.aivideotranslator.session.DurableTranslationUiState
import com.clw.aivideotranslator.ui.home.HomeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductRuntimeFactsResolverTest {
    private val boundSource = DurableSourceUiState(
        phase = DurableSourcePhase.BOUND,
        sessionId = "s1",
        contentUri = "content://video/1",
    )

    @Test fun noDurableSessionProjectsToEmptyHomeAndHomeRoute() {
        val facts = ProductRuntimeFactsResolver.resolve(
            source = DurableSourceUiState.none(),
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState.idle(),
            credentialUsable = false,
        )

        assertEquals(HomeStatus.Empty, facts.homeStatus)
        assertEquals(ProductRoute.HOME, ReopenProductRouteAdapter.toProductRoute(ReopenResolver.resolve(facts.reopenFacts)))
    }

    @Test fun unknownSttOutcomeOutranksOrdinaryProcessing() {
        val facts = ProductRuntimeFactsResolver.resolve(
            source = boundSource,
            stt = DurableSttUiState(
                phase = DurableSttPhase.UNKNOWN_REMOTE_OUTCOME,
                sessionId = "s1",
            ),
            translation = DurableTranslationUiState.idle(),
            credentialUsable = true,
        )

        assertEquals(HomeStatus.ActionUnknownProvider, facts.homeStatus)
        assertTrue(facts.reopenFacts.hasUnknownProviderOutcome)
        assertEquals(ProductRoute.PROVIDER_RECOVERY, ReopenProductRouteAdapter.toProductRoute(ReopenResolver.resolve(facts.reopenFacts)))
    }

    @Test fun missingSourceWithRecoveredTextRoutesSourceRecoveryAndKeepsAcceptedTextFact() {
        val facts = ProductRuntimeFactsResolver.resolve(
            source = DurableSourceUiState(
                phase = DurableSourcePhase.FAILED,
                sessionId = "s1",
                contentUri = "content://video/1",
                failure = DurableSourceFailure.SOURCE_MISSING,
            ),
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState(
                phase = DurableTranslationPhase.RECOVERED_TEXT_ONLY,
                sessionId = "s1",
                entries = listOf(TestTranslationEntryFactory.entry()),
            ),
            credentialUsable = true,
        )

        assertEquals(HomeStatus.ActionSourceMissing, facts.homeStatus)
        assertFalse(facts.reopenFacts.sourceAvailable)
        assertTrue(facts.reopenFacts.hasAcceptedText)
        assertEquals(ProductRoute.SOURCE_RECOVERY, ReopenProductRouteAdapter.toProductRoute(ReopenResolver.resolve(facts.reopenFacts)))
    }

    @Test fun boundSourceWithoutAcceptedTextNeedsProviderWhenCredentialMissing() {
        val facts = ProductRuntimeFactsResolver.resolve(
            source = boundSource,
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState.idle(),
            credentialUsable = false,
        )

        assertTrue(facts.reopenFacts.nextStepNeedsProvider)
        assertEquals(ProductRoute.AI_SETUP, ReopenProductRouteAdapter.toProductRoute(ReopenResolver.resolve(facts.reopenFacts)))
    }

    @Test fun activeLocalCaptureProjectsProcessingWithoutProviderSubmissionClaim() {
        val facts = ProductRuntimeFactsResolver.resolve(
            source = DurableSourceUiState(
                phase = DurableSourcePhase.CAPTURING,
                contentUri = "content://video/candidate",
            ),
            stt = DurableSttUiState.idle(),
            translation = DurableTranslationUiState.idle(),
            credentialUsable = true,
        )

        assertEquals(HomeStatus.ActiveProcessing, facts.homeStatus)
        assertTrue(facts.reopenFacts.hasLocalProcessingToResume)
        assertFalse(facts.reopenFacts.hasUnknownProviderOutcome)
    }
}
