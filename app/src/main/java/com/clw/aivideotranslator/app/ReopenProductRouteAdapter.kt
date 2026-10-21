package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.nav.Route

/** Single adapter between durable reopen routing and rendered product surfaces. */
internal object ReopenProductRouteAdapter {
    fun toProductRoute(route: Route): ProductRoute = when (route) {
        Route.Home -> ProductRoute.HOME
        Route.SourceReview -> ProductRoute.SOURCE_REVIEW
        Route.AiSetup -> ProductRoute.AI_SETUP
        Route.Processing -> ProductRoute.PROCESSING
        Route.Editor -> ProductRoute.EDITOR_TEXT
        Route.ExportProgress -> ProductRoute.EXPORT_PROGRESS
        Route.Result -> ProductRoute.RESULT
        Route.ProviderRecovery -> ProductRoute.PROVIDER_RECOVERY
        Route.SourceRecovery -> ProductRoute.SOURCE_RECOVERY
        Route.LocalRecovery -> ProductRoute.LOCAL_RECOVERY
    }
}
