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

    @Test fun processingReadyMovesToEditorWithoutDuplicatingSessionState() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.PROCESSING)
        navigator.onEvent(ProductNavigationEvent.ProcessingReadyForEditor)
        assertEquals(ProductRoute.EDITOR_TEXT, navigator.route)
    }

    @Test fun editorModesSwitchInsideEditorWorkspace() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.EDITOR_TEXT)
        navigator.onEvent(ProductNavigationEvent.OpenTimingEditor)
        assertEquals(ProductRoute.EDITOR_TIMING, navigator.route)
        navigator.onEvent(ProductNavigationEvent.OpenStyleStudio)
        assertEquals(ProductRoute.STYLE_STUDIO, navigator.route)
        navigator.onEvent(ProductNavigationEvent.OpenTextEditor)
        assertEquals(ProductRoute.EDITOR_TEXT, navigator.route)
    }

    @Test fun previewReturnsToEditorWithoutOwningPresentationTruth() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.EDITOR_TEXT)
        navigator.onEvent(ProductNavigationEvent.OpenFullscreenPreview)
        assertEquals(ProductRoute.FULLSCREEN_PREVIEW, navigator.route)
        navigator.onEvent(ProductNavigationEvent.BackFromFullscreenPreview)
        assertEquals(ProductRoute.EDITOR_TEXT, navigator.route)
    }

    @Test fun exportFlowMovesCenterToProgressToResult() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.EDITOR_TEXT)
        navigator.onEvent(ProductNavigationEvent.OpenExportCenter)
        assertEquals(ProductRoute.EXPORT_CENTER, navigator.route)
        navigator.onEvent(ProductNavigationEvent.ExportStarted)
        assertEquals(ProductRoute.EXPORT_PROGRESS, navigator.route)
        navigator.onEvent(ProductNavigationEvent.ExportSettled)
        assertEquals(ProductRoute.RESULT, navigator.route)
        navigator.onEvent(ProductNavigationEvent.BackHomeFromResult)
        assertEquals(ProductRoute.HOME, navigator.route)
    }

    @Test fun backFromEditorReturnsHomeWhileDurableSessionRemainsExternal() {
        val navigator = ProductNavigator(initialRoute = ProductRoute.EDITOR_TEXT)
        navigator.onEvent(ProductNavigationEvent.BackFromEditor)
        assertEquals(ProductRoute.HOME, navigator.route)
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
