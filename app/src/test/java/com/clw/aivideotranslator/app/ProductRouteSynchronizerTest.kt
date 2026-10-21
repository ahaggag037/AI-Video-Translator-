package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.app.resume.ReopenFacts
import org.junit.Assert.assertEquals
import org.junit.Test

class ProductRouteSynchronizerTest {
    private val readyFacts = ReopenFacts(
        hasActiveSession = true,
        sessionReadable = true,
        hasUnknownProviderOutcome = false,
        hasInterruptedExport = false,
        hasLocalProcessingToResume = false,
        nextStepNeedsProvider = false,
        credentialUsable = true,
        sourceAvailable = true,
        hasValidatedOutputForCurrentRevision = false,
        hasAcceptedText = true,
    )

    @Test fun homeMayResumeIntoDurableDestination() {
        assertEquals(ProductRoute.EDITOR_TEXT, ProductRouteSynchronizer.resolve(ProductRoute.HOME, readyFacts))
    }

    @Test fun processingAdvancesToEditorWhenAcceptedTextAppears() {
        assertEquals(ProductRoute.EDITOR_TEXT, ProductRouteSynchronizer.resolve(ProductRoute.PROCESSING, readyFacts))
    }

    @Test fun ordinaryDurableReadyStateDoesNotPullUserOutOfExportCenter() {
        assertEquals(ProductRoute.EXPORT_CENTER, ProductRouteSynchronizer.resolve(ProductRoute.EXPORT_CENTER, readyFacts))
    }

    @Test fun unknownProviderOutcomePreemptsAnyOrdinaryProductSurface() {
        val unknown = readyFacts.copy(hasUnknownProviderOutcome = true, hasAcceptedText = false)
        assertEquals(ProductRoute.PROVIDER_RECOVERY, ProductRouteSynchronizer.resolve(ProductRoute.EDITOR_TEXT, unknown))
    }

    @Test fun unreadableSessionAlwaysFailsClosedToLocalRecovery() {
        val unreadable = readyFacts.copy(sessionReadable = false)
        assertEquals(ProductRoute.LOCAL_RECOVERY, ProductRouteSynchronizer.resolve(ProductRoute.EXPORT_CENTER, unreadable))
    }

    @Test fun missingSourcePreemptsMediaDependentEditorSurface() {
        val missingSource = readyFacts.copy(sourceAvailable = false)
        assertEquals(ProductRoute.SOURCE_RECOVERY, ProductRouteSynchronizer.resolve(ProductRoute.EDITOR_TIMING, missingSource))
    }

    @Test fun missingSourceDoesNotPreemptTextOnlyEditor() {
        val missingSource = readyFacts.copy(sourceAvailable = false)
        assertEquals(ProductRoute.EDITOR_TEXT, ProductRouteSynchronizer.resolve(ProductRoute.EDITOR_TEXT, missingSource))
    }

    @Test fun missingSourceDoesNotPreemptValidatedTextOnlyResult() {
        val missingSource = readyFacts.copy(sourceAvailable = false)
        assertEquals(ProductRoute.RESULT, ProductRouteSynchronizer.resolve(ProductRoute.RESULT, missingSource))
    }

    @Test fun missingSourceAllowsExplicitReplacementSourceReview() {
        val missingSource = readyFacts.copy(sourceAvailable = false, hasAcceptedText = false)
        assertEquals(ProductRoute.SOURCE_REVIEW, ProductRouteSynchronizer.resolve(ProductRoute.SOURCE_REVIEW, missingSource))
    }
}
