package com.clw.aivideotranslator.export

import com.clw.aivideotranslator.presentation.LayoutStatus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportEligibilityTest {
    private val baseFacts = ExportFacts(
        hasCommittedText = true,
        timingValid = true,
        sourceAvailable = true,
        layoutStatus = LayoutStatus.FITS,
    )

    @Test fun srtFastPathIgnoresVisualOverflow() {
        val eligibility = ExportEligibilityResolver.resolve(
            baseFacts.copy(layoutStatus = LayoutStatus.OVERFLOW),
        )

        assertTrue(eligibility.srtEligible)
        assertFalse(eligibility.mp4Eligible)
        assertFalse(OutputKind.SRT.requiresVideoPipeline)
    }

    @Test fun srtFastPathIgnoresVisualReviewRequired() {
        val eligibility = ExportEligibilityResolver.resolve(
            baseFacts.copy(layoutStatus = LayoutStatus.REVIEW_REQUIRED),
        )

        assertTrue(eligibility.srtEligible)
        assertFalse(eligibility.mp4Eligible)
    }

    @Test fun mp4RequiresSourceAndFitLayout() {
        assertTrue(ExportEligibilityResolver.resolve(baseFacts).mp4Eligible)
        assertFalse(
            ExportEligibilityResolver.resolve(baseFacts.copy(sourceAvailable = false)).mp4Eligible,
        )
        assertTrue(OutputKind.MP4.requiresVideoPipeline)
    }

    @Test fun invalidTimingBlocksBothOutputs() {
        val eligibility = ExportEligibilityResolver.resolve(baseFacts.copy(timingValid = false))

        assertFalse(eligibility.srtEligible)
        assertFalse(eligibility.mp4Eligible)
    }

    @Test fun missingCommittedTextBlocksBothOutputs() {
        val eligibility = ExportEligibilityResolver.resolve(baseFacts.copy(hasCommittedText = false))

        assertFalse(eligibility.srtEligible)
        assertFalse(eligibility.mp4Eligible)
    }
}
