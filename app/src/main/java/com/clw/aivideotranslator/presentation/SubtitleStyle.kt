package com.clw.aivideotranslator.presentation

enum class StyleScope {
    PROJECT,
    CURRENT_CUE,
}

enum class BuiltInStylePreset {
    CLEAR,
    SIMPLE,
    CINEMA,
    HIGH_CONTRAST,
    BOX,
}

enum class SubtitleTextAlignment {
    START,
    CENTER,
    END,
}

enum class SubtitleAnchor {
    TOP_START,
    TOP_CENTER,
    TOP_END,
    CENTER_START,
    CENTER,
    CENTER_END,
    BOTTOM_START,
    BOTTOM_CENTER,
    BOTTOM_END,
}

/**
 * Authored style truth. Spatial fractions are relative to video content, never device/Compose pixels.
 * LINE_HEIGHT is the only vertical typography-spacing authority in V1.
 */
data class SubtitleStyle(
    val fontFamilyId: String,
    val fontWeight: Int,
    val fontSizeSp: Float,
    val lineHeightSp: Float,
    val textColorArgb: Long,
    val opacity: Float,
    val alignment: SubtitleTextAlignment,
    val outlineWidthDp: Float,
    val shadowEnabled: Boolean,
    val backgroundEnabled: Boolean,
    val anchor: SubtitleAnchor,
    val offsetXFraction: Float,
    val offsetYFraction: Float,
    val safeMarginFraction: Float,
    val maxWidthFraction: Float,
    val maxLines: Int,
) {
    init {
        require(fontFamilyId.isNotBlank())
        require(fontWeight in 1..1000)
        require(fontSizeSp > 0f)
        require(lineHeightSp >= fontSizeSp)
        require(opacity in 0f..1f)
        require(outlineWidthDp >= 0f)
        require(offsetXFraction in -1f..1f)
        require(offsetYFraction in -1f..1f)
        require(safeMarginFraction in 0f..0.5f)
        require(maxWidthFraction > 0f && maxWidthFraction <= 1f)
        require(maxLines in 1..2) { "V1 authored subtitle contract supports one or two lines" }
    }
}

/** Sparse field-level delta over ProjectStyle; null means inherit. */
data class CueStyleOverride(
    val fontFamilyId: String? = null,
    val fontWeight: Int? = null,
    val fontSizeSp: Float? = null,
    val lineHeightSp: Float? = null,
    val textColorArgb: Long? = null,
    val opacity: Float? = null,
    val alignment: SubtitleTextAlignment? = null,
    val outlineWidthDp: Float? = null,
    val shadowEnabled: Boolean? = null,
    val backgroundEnabled: Boolean? = null,
    val anchor: SubtitleAnchor? = null,
    val offsetXFraction: Float? = null,
    val offsetYFraction: Float? = null,
    val safeMarginFraction: Float? = null,
    val maxWidthFraction: Float? = null,
    val maxLines: Int? = null,
)

object EffectiveSubtitleStyleResolver {
    fun resolve(project: SubtitleStyle, cueOverride: CueStyleOverride?): SubtitleStyle {
        val override = cueOverride ?: CueStyleOverride()
        return SubtitleStyle(
            fontFamilyId = override.fontFamilyId ?: project.fontFamilyId,
            fontWeight = override.fontWeight ?: project.fontWeight,
            fontSizeSp = override.fontSizeSp ?: project.fontSizeSp,
            lineHeightSp = override.lineHeightSp ?: project.lineHeightSp,
            textColorArgb = override.textColorArgb ?: project.textColorArgb,
            opacity = override.opacity ?: project.opacity,
            alignment = override.alignment ?: project.alignment,
            outlineWidthDp = override.outlineWidthDp ?: project.outlineWidthDp,
            shadowEnabled = override.shadowEnabled ?: project.shadowEnabled,
            backgroundEnabled = override.backgroundEnabled ?: project.backgroundEnabled,
            anchor = override.anchor ?: project.anchor,
            offsetXFraction = override.offsetXFraction ?: project.offsetXFraction,
            offsetYFraction = override.offsetYFraction ?: project.offsetYFraction,
            safeMarginFraction = override.safeMarginFraction ?: project.safeMarginFraction,
            maxWidthFraction = override.maxWidthFraction ?: project.maxWidthFraction,
            maxLines = override.maxLines ?: project.maxLines,
        )
    }
}