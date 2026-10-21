package com.clw.aivideotranslator.ui.home

/** Durable facts projected into the Home product surface. */
internal data class HomeFacts(
    val hasActiveSession: Boolean,
    val providerUnknown: Boolean = false,
    val sourceUnavailable: Boolean = false,
    val processingActive: Boolean = false,
)

internal enum class HomeStatus {
    Empty,
    ActiveReady,
    ActiveProcessing,
    ActionUnknownProvider,
    ActionSourceMissing,
}

/**
 * Pure precedence for the Home summary. Recovery-required states outrank ordinary progress so Home
 * never advertises a session as merely "processing" when explicit user action is actually required.
 */
internal object HomeStatusResolver {
    fun resolve(facts: HomeFacts): HomeStatus {
        if (!facts.hasActiveSession) return HomeStatus.Empty
        if (facts.providerUnknown) return HomeStatus.ActionUnknownProvider
        if (facts.sourceUnavailable) return HomeStatus.ActionSourceMissing
        if (facts.processingActive) return HomeStatus.ActiveProcessing
        return HomeStatus.ActiveReady
    }
}
