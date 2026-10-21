package com.clw.aivideotranslator.app.resume

import com.clw.aivideotranslator.app.nav.Route

/**
 * Pure reopen precedence. Reopening must never submit provider work or mutate durable session truth.
 */
internal object ReopenResolver {
    fun resolve(facts: ReopenFacts): Route {
        if (facts.hasActiveSession && !facts.sessionReadable) return Route.LocalRecovery
        if (!facts.hasActiveSession) return Route.Home
        if (facts.hasUnknownProviderOutcome) return Route.ProviderRecovery
        if (facts.hasInterruptedExport) return Route.ExportProgress
        if (facts.hasLocalProcessingToResume) return Route.Processing
        if (facts.nextStepNeedsProvider && !facts.credentialUsable) return Route.AiSetup
        if (!facts.sourceAvailable) return Route.SourceRecovery
        if (facts.hasValidatedOutputForCurrentRevision) return Route.Result
        if (facts.hasAcceptedText) return Route.Editor
        return Route.SourceReview
    }
}
