package com.clw.aivideotranslator.app.resume

import com.clw.aivideotranslator.app.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class ReopenResolverTest {
    @Test fun unreadableSessionWinsOverEveryOtherFact() {
        val facts = ReopenFacts(
            hasActiveSession = true,
            sessionReadable = false,
            hasUnknownProviderOutcome = true,
            hasInterruptedExport = true,
            hasLocalProcessingToResume = true,
            nextStepNeedsProvider = true,
            credentialUsable = false,
            sourceAvailable = false,
            hasValidatedOutputForCurrentRevision = true,
            hasAcceptedText = true,
        )

        assertEquals(Route.LocalRecovery, ReopenResolver.resolve(facts))
    }

    @Test fun noActiveSessionRoutesHome() {
        assertEquals(
            Route.Home,
            ReopenResolver.resolve(ReopenFacts(hasActiveSession = false)),
        )
    }

    @Test fun unknownProviderOutcomeWinsAfterReadableActiveSession() {
        val facts = activeFacts(
            hasUnknownProviderOutcome = true,
            hasInterruptedExport = true,
            sourceAvailable = false,
            hasAcceptedText = true,
        )

        assertEquals(Route.ProviderRecovery, ReopenResolver.resolve(facts))
    }

    @Test fun interruptedExportWinsOverLocalProcessingAndEditing() {
        val facts = activeFacts(
            hasInterruptedExport = true,
            hasLocalProcessingToResume = true,
            hasAcceptedText = true,
        )

        assertEquals(Route.ExportProgress, ReopenResolver.resolve(facts))
    }

    @Test fun resumableLocalProcessingWinsOverCredentialAndSourceConvenienceRouting() {
        val facts = activeFacts(
            hasLocalProcessingToResume = true,
            nextStepNeedsProvider = true,
            credentialUsable = false,
            sourceAvailable = false,
        )

        assertEquals(Route.Processing, ReopenResolver.resolve(facts))
    }

    @Test fun missingCredentialRoutesAiSetupOnlyWhenNextStepNeedsProvider() {
        assertEquals(
            Route.AiSetup,
            ReopenResolver.resolve(
                activeFacts(
                    nextStepNeedsProvider = true,
                    credentialUsable = false,
                )
            ),
        )

        assertEquals(
            Route.Editor,
            ReopenResolver.resolve(
                activeFacts(
                    nextStepNeedsProvider = false,
                    credentialUsable = false,
                    hasAcceptedText = true,
                )
            ),
        )
    }

    @Test fun missingSourceRoutesRecoveryBeforeResultOrEditor() {
        val facts = activeFacts(
            sourceAvailable = false,
            hasValidatedOutputForCurrentRevision = true,
            hasAcceptedText = true,
        )

        assertEquals(Route.SourceRecovery, ReopenResolver.resolve(facts))
    }

    @Test fun validatedOutputForCurrentRevisionRoutesResult() {
        val facts = activeFacts(
            hasValidatedOutputForCurrentRevision = true,
            hasAcceptedText = true,
        )

        assertEquals(Route.Result, ReopenResolver.resolve(facts))
    }

    @Test fun acceptedTextRoutesEditor() {
        assertEquals(
            Route.Editor,
            ReopenResolver.resolve(activeFacts(hasAcceptedText = true)),
        )
    }

    @Test fun activeSourceWithoutAcceptedTextRoutesSourceReview() {
        assertEquals(
            Route.SourceReview,
            ReopenResolver.resolve(activeFacts()),
        )
    }

    private fun activeFacts(
        sessionReadable: Boolean = true,
        hasUnknownProviderOutcome: Boolean = false,
        hasInterruptedExport: Boolean = false,
        hasLocalProcessingToResume: Boolean = false,
        nextStepNeedsProvider: Boolean = false,
        credentialUsable: Boolean = true,
        sourceAvailable: Boolean = true,
        hasValidatedOutputForCurrentRevision: Boolean = false,
        hasAcceptedText: Boolean = false,
    ) = ReopenFacts(
        hasActiveSession = true,
        sessionReadable = sessionReadable,
        hasUnknownProviderOutcome = hasUnknownProviderOutcome,
        hasInterruptedExport = hasInterruptedExport,
        hasLocalProcessingToResume = hasLocalProcessingToResume,
        nextStepNeedsProvider = nextStepNeedsProvider,
        credentialUsable = credentialUsable,
        sourceAvailable = sourceAvailable,
        hasValidatedOutputForCurrentRevision = hasValidatedOutputForCurrentRevision,
        hasAcceptedText = hasAcceptedText,
    )
}
