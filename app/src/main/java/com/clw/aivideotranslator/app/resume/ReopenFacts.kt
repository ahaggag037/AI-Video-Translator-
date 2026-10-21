package com.clw.aivideotranslator.app.resume

/**
 * Minimal durable facts required to choose the first product surface after launch/reopen.
 * No field here starts work; routing is a pure presentation decision.
 */
internal data class ReopenFacts(
    val hasActiveSession: Boolean,
    val sessionReadable: Boolean = true,
    val hasUnknownProviderOutcome: Boolean = false,
    val hasInterruptedExport: Boolean = false,
    val hasLocalProcessingToResume: Boolean = false,
    val nextStepNeedsProvider: Boolean = false,
    val credentialUsable: Boolean = true,
    val sourceAvailable: Boolean = true,
    val hasValidatedOutputForCurrentRevision: Boolean = false,
    val hasAcceptedText: Boolean = false,
)
