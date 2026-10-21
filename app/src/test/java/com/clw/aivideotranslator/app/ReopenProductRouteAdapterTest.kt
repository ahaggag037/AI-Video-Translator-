package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.nav.Route
import org.junit.Assert.assertEquals
import org.junit.Test

class ReopenProductRouteAdapterTest {
    @Test fun mapsEveryImplementedReopenDestinationToProductSurface() {
        assertEquals(ProductRoute.HOME, ReopenProductRouteAdapter.toProductRoute(Route.Home))
        assertEquals(ProductRoute.SOURCE_REVIEW, ReopenProductRouteAdapter.toProductRoute(Route.SourceReview))
        assertEquals(ProductRoute.AI_SETUP, ReopenProductRouteAdapter.toProductRoute(Route.AiSetup))
        assertEquals(ProductRoute.PROCESSING, ReopenProductRouteAdapter.toProductRoute(Route.Processing))
        assertEquals(ProductRoute.EDITOR_TEXT, ReopenProductRouteAdapter.toProductRoute(Route.Editor))
        assertEquals(ProductRoute.EXPORT_PROGRESS, ReopenProductRouteAdapter.toProductRoute(Route.ExportProgress))
        assertEquals(ProductRoute.RESULT, ReopenProductRouteAdapter.toProductRoute(Route.Result))
        assertEquals(ProductRoute.PROVIDER_RECOVERY, ReopenProductRouteAdapter.toProductRoute(Route.ProviderRecovery))
    }

    @Test fun unreleasedRecoverySurfacesFailClosedInsteadOfFallingThrough() {
        assertEquals(ProductRoute.SOURCE_RECOVERY, ReopenProductRouteAdapter.toProductRoute(Route.SourceRecovery))
        assertEquals(ProductRoute.LOCAL_RECOVERY, ReopenProductRouteAdapter.toProductRoute(Route.LocalRecovery))
    }
}
