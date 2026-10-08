package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SemanticSourceUnit
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.sha256Utf8
import org.junit.Assert.fail
import org.junit.Test

class NvidiaTranslationPlanContractTest {
    private fun plan() = TranslationPlanner.plan(
        SemanticSourceUnit(
            id = "u1",
            orderedWordIds = listOf("w1"),
            sourceText = "Hello world.",
            sourceTextHash = sha256Utf8("Hello world."),
            sourceInterval = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(1_000_000)),
            segmentationVersion = "test",
        ),
    )

    @Test fun defaultV1PlanMatchesFixedNvidiaRequestBodyContract() {
        NvidiaTranslationPlanContract.requireSupported(plan())
    }

    @Test fun profileDriftIsRejectedBeforeDurableSendBoundary() {
        val changed = plan().let { it.copy(profile = it.profile.copy(maxTokens = 2048)) }
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("profile drift must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun tamperedExactSourceIsRejectedBeforeDurableSendBoundary() {
        val changed = plan().copy(exactSourceText = "Different source")
        try {
            NvidiaTranslationPlanContract.requireSupported(changed)
            fail("tampered request plan must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
