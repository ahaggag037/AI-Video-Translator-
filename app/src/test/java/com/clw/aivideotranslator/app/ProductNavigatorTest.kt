package com.clw.aivideotranslator.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductNavigatorTest {
    @Test fun chooseVideoMovesHomeToSourceReviewOnly() {
        val navigator = ProductNavigator()

        navigator.onEvent(ProductNavigationEvent.ChooseVideo)

        assertEquals(ProductRoute.SOURCE_REVIEW, navigator.route)
    }

    @Test fun backFromSourceReviewReturnsHome() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.SOURCE_REVIEW)

        navigator.onEvent(ProductNavigationEvent.BackFromSourceReview)

        assertEquals(ProductRoute.HOME, navigator.route)
    }

    @Test fun backOnHomeIsStable() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.HOME)

        navigator.onEvent(ProductNavigationEvent.BackFromSourceReview)

        assertEquals(ProductRoute.HOME, navigator.route)
    }

    @Test fun routeStateContainsNoDurableSessionOrProviderPayload() {
        val fields = ProductNavigator::class.java.declaredFields.map { it.type.name }

        fields.forEach { typeName ->
            check(!typeName.contains("TranslationSession"))
            check(!typeName.contains("Provider"))
            check(!typeName.contains("Nvidia"))
        }
    }
}
