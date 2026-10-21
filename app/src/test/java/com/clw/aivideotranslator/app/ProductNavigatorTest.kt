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

    @Test fun activatedSourceWithMissingCredentialRoutesToAiSetup() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.SOURCE_REVIEW)

        navigator.onEvent(ProductNavigationEvent.SourceActivated(needsAiSetup = true))

        assertEquals(ProductRoute.AI_SETUP, navigator.route)
    }

    @Test fun activatedSourceWithUsableCredentialRoutesToProcessing() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.SOURCE_REVIEW)

        navigator.onEvent(ProductNavigationEvent.SourceActivated(needsAiSetup = false))

        assertEquals(ProductRoute.PROCESSING, navigator.route)
    }

    @Test fun backFromAiSetupReturnsToSourceReviewWithoutProviderSideEffect() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.AI_SETUP)

        navigator.onEvent(ProductNavigationEvent.BackFromAiSetup)

        assertEquals(ProductRoute.SOURCE_REVIEW, navigator.route)
    }

    @Test fun acceptedAiCredentialMovesToProcessing() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.AI_SETUP)

        navigator.onEvent(ProductNavigationEvent.AiCredentialAccepted)

        assertEquals(ProductRoute.PROCESSING, navigator.route)
    }

    @Test fun unknownProviderOutcomeLeavesProcessingForDedicatedRecovery() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.PROCESSING)

        navigator.onEvent(ProductNavigationEvent.ProviderOutcomeUnknown)

        assertEquals(ProductRoute.PROVIDER_RECOVERY, navigator.route)
    }

    @Test fun keepUnknownProviderStateAndReturnGoesHomeWithoutFreshAttempt() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.PROVIDER_RECOVERY)

        navigator.onEvent(ProductNavigationEvent.KeepProviderStateAndReturn)

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
