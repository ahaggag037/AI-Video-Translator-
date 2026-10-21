package com.clw.aivideotranslator.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleTest {
    private val project = SubtitleStyle(
        fontFamilyId = "noto-sans-arabic",
        fontWeight = 600,
        fontSizeSp = 42f,
        lineHeightSp = 52f,
        textColorArgb = 0xFFFFFFFF,
        opacity = 1f,
        alignment = SubtitleTextAlignment.CENTER,
        outlineWidthDp = 2f,
        shadowEnabled = true,
        backgroundEnabled = false,
        anchor = SubtitleAnchor.BOTTOM_CENTER,
        offsetXFraction = 0f,
        offsetYFraction = 0f,
        safeMarginFraction = 0.05f,
        maxWidthFraction = 0.86f,
        maxLines = 2,
    )

    @Test fun styleScopeHasExactlyProjectAndCurrentCue() {
        assertEquals(
            listOf(StyleScope.PROJECT, StyleScope.CURRENT_CUE),
            StyleScope.entries,
        )
    }

    @Test fun builtInPresetsAreFixedV1Set() {
        assertEquals(
            listOf(
                BuiltInStylePreset.CLEAR,
                BuiltInStylePreset.SIMPLE,
                BuiltInStylePreset.CINEMA,
                BuiltInStylePreset.HIGH_CONTRAST,
                BuiltInStylePreset.BOX,
            ),
            BuiltInStylePreset.entries,
        )
    }

    @Test fun currentCueOverrideIsSparseFieldLevelDelta() {
        val override = CueStyleOverride(
            fontSizeSp = 48f,
            backgroundEnabled = true,
        )

        val effective = EffectiveSubtitleStyleResolver.resolve(project, override)

        assertEquals(48f, effective.fontSizeSp)
        assertTrue(effective.backgroundEnabled)
        assertEquals(project.fontFamilyId, effective.fontFamilyId)
        assertEquals(project.lineHeightSp, effective.lineHeightSp)
        assertEquals(project.maxLines, effective.maxLines)
    }

    @Test fun emptyCueOverrideResolvesExactlyToProjectStyle() {
        assertEquals(project, EffectiveSubtitleStyleResolver.resolve(project, CueStyleOverride()))
    }

    @Test fun modelContainsLineHeightButNoLineSpacingAuthority() {
        val propertyNames = SubtitleStyle::class.java.declaredFields.map { it.name }

        assertTrue(propertyNames.contains("lineHeightSp"))
        assertFalse(propertyNames.any { it.contains("lineSpacing", ignoreCase = true) })
    }
}
