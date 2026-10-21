package com.clw.aivideotranslator.app.nav

/**
 * Product-level destinations only. Durable session state owns semantic truth; these values merely
 * identify the surface that should present that truth.
 */
internal sealed interface Route {
    data object Home : Route
    data object SourceReview : Route
    data object AiSetup : Route
    data object Processing : Route
    data object Editor : Route
    data object ExportProgress : Route
    data object Result : Route
    data object ProviderRecovery : Route
    data object SourceRecovery : Route
    data object LocalRecovery : Route
}
