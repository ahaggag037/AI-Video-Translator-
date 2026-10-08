package com.clw.aivideotranslator.tv1

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SourceUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class TimeAndLegacyBridgeTest {
    private fun rejects(block: () -> Unit) {
        try {
            block()
            fail("Expected validation failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test
    fun sampleClockMapPreservesExplicitNonZeroOrigin() {
        val map = SampleClockMap(TimeUs(2_000_000L), sampleDurationUs = 60_000_000L)
        assertEquals(2_080_000L, map.toPresentation(TimeUs(80_000L)).value)
        assertEquals(80_000L, map.toSampleRelative(TimeUs(2_080_000L)).value)
        rejects { map.toPresentation(TimeUs(60_000_001L)) }
        rejects { map.toSampleRelative(TimeUs(1_999_999L)) }
    }

    @Test
    fun legacyWordsCrossBoundaryWithoutRetiming() {
        val words = listOf(
            NvidiaWord("Hello", 80L, 400L, 0.9),
            NvidiaWord("world", 500L, 900L, 0.8),
        )
        val bridged = LegacySubtitleBridge.sourceWords(words)
        assertEquals("w000001", bridged[0].id)
        assertEquals(80_000L, bridged[0].sampleStartUs.value)
        assertEquals(900_000L, bridged[1].sampleEndUs.value)
    }

    @Test
    fun legacySemanticUnitsUsePresentationClockMap() {
        val units = listOf(SourceUnit("u0001", 80L, 800L, "Hello world."))
        val map = SampleClockMap(TimeUs(120_000_000L), sampleDurationUs = 60_000_000L)
        val bridged = LegacySubtitleBridge.semanticUnits(units, map)
        assertEquals(120_080_000L, bridged.single().presentationStartUs.value)
        assertEquals(120_800_000L, bridged.single().presentationEndUs.value)
        assertEquals(null, bridged.single().sourceWordIds)
    }

    @Test
    fun legacyCueRoundTripIsLosslessOnlyAtMillisecondPrecision() {
        val legacy = ArabicSubtitleCue("u1", 80L, 1000L, "مرحبا")
        val semantic = LegacySubtitleBridge.semanticCue(legacy, translationRevisionId = "r1")
        assertEquals(legacy, LegacySubtitleBridge.legacyCue(semantic))

        rejects {
            LegacySubtitleBridge.legacyCue(
                semantic.copy(speechStartUs = TimeUs(80_001L))
            )
        }
    }
}
