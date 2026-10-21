package com.clw.aivideotranslator.app

/**
 * Product-surface navigation only. Durable session/provider state must never live here.
 *
 * Routes are added only when the corresponding product surface exists and has a truthful state
 * contract. Durable truth remains owned by the session/provider layers outside this navigator.
 */
internal enum class ProductRoute {
    HOME,
    SOURCE_REVIEW,
    AI_SETUP,
    PROCESSING,
    PROVIDER_RECOVERY,
    EDITOR_TEXT,
    EDITOR_TIMING,
    STYLE_STUDIO,
    FULLSCREEN_PREVIEW,
    EXPORT_CENTER,
    EXPORT_PROGRESS,
    RESULT,
}

internal sealed interface ProductNavigationEvent {
    data object ChooseVideo : ProductNavigationEvent
    data object BackFromSourceReview : ProductNavigationEvent
    data class SourceActivated(val needsAiSetup: Boolean) : ProductNavigationEvent
    data object BackFromAiSetup : ProductNavigationEvent
    data object AiCredentialAccepted : ProductNavigationEvent
    data object ProcessingReadyForEditor : ProductNavigationEvent
    data object BackFromEditor : ProductNavigationEvent
    data object OpenTextEditor : ProductNavigationEvent
    data object OpenTimingEditor : ProductNavigationEvent
    data object OpenStyleStudio : ProductNavigationEvent
    data object OpenFullscreenPreview : ProductNavigationEvent
    data object BackFromFullscreenPreview : ProductNavigationEvent
    data object OpenExportCenter : ProductNavigationEvent
    data object ExportStarted : ProductNavigationEvent
    data object ExportSettled : ProductNavigationEvent
    data object BackHomeFromResult : ProductNavigationEvent
    data object ProviderOutcomeUnknown : ProductNavigationEvent
    data object KeepProviderStateAndReturn : ProductNavigationEvent
}

internal class ProductNavigator(
    initialRoute: ProductRoute = ProductRoute.HOME,
) {
    var route: ProductRoute = initialRoute
        private set

    fun onEvent(event: ProductNavigationEvent) {
        route = when (event) {
            ProductNavigationEvent.ChooseVideo -> ProductRoute.SOURCE_REVIEW
            ProductNavigationEvent.BackFromSourceReview -> ProductRoute.HOME
            is ProductNavigationEvent.SourceActivated -> if (event.needsAiSetup) {
                ProductRoute.AI_SETUP
            } else {
                ProductRoute.PROCESSING
            }
            ProductNavigationEvent.BackFromAiSetup -> ProductRoute.SOURCE_REVIEW
            ProductNavigationEvent.AiCredentialAccepted -> ProductRoute.PROCESSING
            ProductNavigationEvent.ProcessingReadyForEditor -> ProductRoute.EDITOR_TEXT
            ProductNavigationEvent.BackFromEditor -> ProductRoute.HOME
            ProductNavigationEvent.OpenTextEditor -> ProductRoute.EDITOR_TEXT
            ProductNavigationEvent.OpenTimingEditor -> ProductRoute.EDITOR_TIMING
            ProductNavigationEvent.OpenStyleStudio -> ProductRoute.STYLE_STUDIO
            ProductNavigationEvent.OpenFullscreenPreview -> ProductRoute.FULLSCREEN_PREVIEW
            ProductNavigationEvent.BackFromFullscreenPreview -> ProductRoute.EDITOR_TEXT
            ProductNavigationEvent.OpenExportCenter -> ProductRoute.EXPORT_CENTER
            ProductNavigationEvent.ExportStarted -> ProductRoute.EXPORT_PROGRESS
            ProductNavigationEvent.ExportSettled -> ProductRoute.RESULT
            ProductNavigationEvent.BackHomeFromResult -> ProductRoute.HOME
            ProductNavigationEvent.ProviderOutcomeUnknown -> ProductRoute.PROVIDER_RECOVERY
            ProductNavigationEvent.KeepProviderStateAndReturn -> ProductRoute.HOME
        }
    }
}
