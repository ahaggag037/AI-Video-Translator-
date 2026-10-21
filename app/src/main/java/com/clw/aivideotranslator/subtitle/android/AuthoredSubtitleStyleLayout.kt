package com.clw.aivideotranslator.subtitle.android

import com.clw.aivideotranslator.editor.SubtitleStyleDefaults
import com.clw.aivideotranslator.presentation.SubtitleStyle
import kotlin.math.ceil
import kotlin.math.max

/**
 * Deterministic bridge from authored style units to the existing video-relative layout engine.
 * The production default is the identity transform over the already-qualified legacy config.
 * Device density never participates.
 */
internal object AuthoredSubtitleStyleLayout {
    fun configFor(
        style: SubtitleStyle,
        base: SubtitleLayoutConfig = SubtitleLayoutConfig(),
    ): SubtitleLayoutConfig {
        val baseline = SubtitleStyleDefaults.PROJECT
        val fontScale = style.fontSizeSp / baseline.fontSizeSp
        val baselineLineRatio = baseline.lineHeightSp / baseline.fontSizeSp
        val authoredLineRatio = style.lineHeightSp / style.fontSizeSp
        val lineScale = authoredLineRatio / baselineLineRatio
        val widthMargin = (1.0 - style.maxWidthFraction.toDouble()) / 2.0
        val horizontalMargin = max(style.safeMarginFraction.toDouble(), widthMargin)

        return base.copy(
            maxLines = style.maxLines,
            horizontalMarginFraction = horizontalMargin,
            verticalMarginFraction = style.safeMarginFraction.toDouble(),
            preferredShortSideFraction = base.preferredShortSideFraction * fontScale,
            floorShortSideFraction = base.floorShortSideFraction * fontScale,
            minimumPreferredPx = max(1, ceil(base.minimumPreferredPx * fontScale).toInt()),
            absoluteFloorPx = max(1, ceil(base.absoluteFloorPx * fontScale).toInt()),
            lineSpacingMultiplier = base.lineSpacingMultiplier * lineScale,
        )
    }

    fun outlineWidthPx(style: SubtitleStyle, fontPx: Int): Float =
        fontPx * (style.outlineWidthDp / SubtitleStyleDefaults.PROJECT.fontSizeSp)

    fun shadowRadiusPx(style: SubtitleStyle, fontPx: Int): Float =
        if (style.shadowEnabled) max(1f, fontPx * 0.06f) else 0f

    fun shadowOffsetYPx(style: SubtitleStyle, fontPx: Int): Float =
        if (style.shadowEnabled) max(1f, fontPx * 0.03f) else 0f
}