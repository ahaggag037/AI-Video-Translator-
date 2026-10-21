package com.clw.aivideotranslator.app

/**
 * Product-surface navigation only. Durable session/provider state must never live here.
 *
 * This first slice deliberately contains only the Home <-> Source Review transition. Additional
 * destinations are added when their product slices become executable and tested.
 */
internal enum class ProductRoute {
    HOME,
    SOURCE_REVIEW,
}

internal sealed interface ProductNavigationEvent {
    data object ChooseVideo : ProductNavigationEvent
    data object BackFromSourceReview : ProductNavigationEvent
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
        }
    }
}
