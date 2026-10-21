package com.clw.aivideotranslator.subtitle.android

import com.clw.aivideotranslator.editor.SubtitleStyleDefaults
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthoredSubtitleStyleLayoutTest {
    @Test fun productionDefaultPreservesQualifiedLegacyLayoutConfig() {
        val legacy = SubtitleLayoutConfig()
        val authored = AuthoredSubtitleStyleLayout.configFor(SubtitleStyleDefaults.PROJECT, legacy)

        assertEquals(legacy.maxLines, authored.maxLines)
        assertEquals(legacy.horizontalMarginFraction, authored.horizontalMarginFraction, 0.000001)
        assertEquals(legacy.verticalMarginFraction, authored.verticalMarginFraction, 0.000001)
        assertEquals(legacy.preferredShortSideFraction, authored.preferredShortSideFraction, 0.000001)
        assertEquals(legacy.floorShortSideFraction, authored.floorShortSideFraction, 0.000001)
        assertEquals(legacy.lineSpacingMultiplier, authored.lineSpacingMultiplier, 0.000001f)
    }

    @Test fun largerAuthoredFontScalesAgainstVideoGeometryNotDeviceDensity() {
        val base = SubtitleStyleDefaults.PROJECT
        val larger = base.copy(fontSizeSp = base.fontSizeSp * 1.5f, lineHeightSp = base.lineHeightSp * 1.5f)
        val legacy = SubtitleLayoutConfig()
        val authored = AuthoredSubtitleStyleLayout.configFor(larger, legacy)

        assertEquals(legacy.preferredShortSideFraction * 1.5, authored.preferredShortSideFraction, 0.000001)
        assertEquals(legacy.floorShortSideFraction * 1.5, authored.floorShortSideFraction, 0.000001)
        assertEquals(legacy.lineSpacingMultiplier, authored.lineSpacingMultiplier, 0.000001f)
    }

    @Test fun maxWidthAndSafeMarginBothConstrainHorizontalSafeArea() {
        val base = SubtitleStyleDefaults.PROJECT
        val narrowed = base.copy(safeMarginFraction = 0.02f, maxWidthFraction = 0.70f)
        val config = AuthoredSubtitleStyleLayout.configFor(narrowed)

        assertEquals(0.15, config.horizontalMarginFraction, 0.000001)
        assertEquals(0.02, config.verticalMarginFraction, 0.000001)
    }

    @Test fun lineHeightChangesOnlyLineSpacingRatioAtConstantFontSize() {
        val base = SubtitleStyleDefaults.PROJECT
        val taller = base.copy(lineHeightSp = base.lineHeightSp * 1.25f)
        val config = AuthoredSubtitleStyleLayout.configFor(taller)

        assertTrue(config.lineSpacingMultiplier > SubtitleLayoutConfig().lineSpacingMultiplier)
        assertEquals(
            SubtitleLayoutConfig().lineSpacingMultiplier * 1.25f,
            config.lineSpacingMultiplier,
            0.0001f,
        )
    }

    @Test fun oneLineAuthoredLimitRemainsInsideV1Contract() {
        val config = AuthoredSubtitleStyleLayout.configFor(
            SubtitleStyleDefaults.PROJECT.copy(maxLines = 1),
        )
        assertEquals(1, config.maxLines)
    }
}