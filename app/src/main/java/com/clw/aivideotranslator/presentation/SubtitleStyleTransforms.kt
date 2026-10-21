package com.clw.aivideotranslator.presentation

internal object CueStyleOverrideDiff {
    fun between(project: SubtitleStyle, cue: SubtitleStyle): CueStyleOverride = CueStyleOverride(
        fontFamilyId = cue.fontFamilyId.takeIf { it != project.fontFamilyId },
        fontWeight = cue.fontWeight.takeIf { it != project.fontWeight },
        fontSizeSp = cue.fontSizeSp.takeIf { it != project.fontSizeSp },
        lineHeightSp = cue.lineHeightSp.takeIf { it != project.lineHeightSp },
        textColorArgb = cue.textColorArgb.takeIf { it != project.textColorArgb },
        opacity = cue.opacity.takeIf { it != project.opacity },
        alignment = cue.alignment.takeIf { it != project.alignment },
        outlineWidthDp = cue.outlineWidthDp.takeIf { it != project.outlineWidthDp },
        shadowEnabled = cue.shadowEnabled.takeIf { it != project.shadowEnabled },
        backgroundEnabled = cue.backgroundEnabled.takeIf { it != project.backgroundEnabled },
        anchor = cue.anchor.takeIf { it != project.anchor },
        offsetXFraction = cue.offsetXFraction.takeIf { it != project.offsetXFraction },
        offsetYFraction = cue.offsetYFraction.takeIf { it != project.offsetYFraction },
        safeMarginFraction = cue.safeMarginFraction.takeIf { it != project.safeMarginFraction },
        maxWidthFraction = cue.maxWidthFraction.takeIf { it != project.maxWidthFraction },
        maxLines = cue.maxLines.takeIf { it != project.maxLines },
    )
}

internal object BuiltInSubtitleStyleResolver {
    fun apply(preset: BuiltInStylePreset, base: SubtitleStyle): SubtitleStyle = when (preset) {
        BuiltInStylePreset.CLEAR -> base.copy(
            fontSizeSp = 42f,
            lineHeightSp = 52f,
            opacity = 1f,
            outlineWidthDp = 2f,
            shadowEnabled = true,
            backgroundEnabled = false,
            anchor = SubtitleAnchor.BOTTOM_CENTER,
            maxWidthFraction = 0.86f,
            maxLines = 2,
        )
        BuiltInStylePreset.SIMPLE -> base.copy(
            fontSizeSp = 38f,
            lineHeightSp = 48f,
            opacity = 1f,
            outlineWidthDp = 0f,
            shadowEnabled = true,
            backgroundEnabled = false,
            anchor = SubtitleAnchor.BOTTOM_CENTER,
            maxWidthFraction = 0.84f,
            maxLines = 2,
        )
        BuiltInStylePreset.CINEMA -> base.copy(
            fontSizeSp = 40f,
            lineHeightSp = 50f,
            opacity = 0.96f,
            outlineWidthDp = 2f,
            shadowEnabled = true,
            backgroundEnabled = false,
            anchor = SubtitleAnchor.BOTTOM_CENTER,
            offsetYFraction = -0.03f,
            maxWidthFraction = 0.82f,
            maxLines = 2,
        )
        BuiltInStylePreset.HIGH_CONTRAST -> base.copy(
            fontSizeSp = 44f,
            lineHeightSp = 54f,
            opacity = 1f,
            outlineWidthDp = 3f,
            shadowEnabled = true,
            backgroundEnabled = true,
            anchor = SubtitleAnchor.BOTTOM_CENTER,
            maxWidthFraction = 0.86f,
            maxLines = 2,
        )
        BuiltInStylePreset.BOX -> base.copy(
            fontSizeSp = 40f,
            lineHeightSp = 50f,
            opacity = 1f,
            outlineWidthDp = 0f,
            shadowEnabled = false,
            backgroundEnabled = true,
            anchor = SubtitleAnchor.BOTTOM_CENTER,
            maxWidthFraction = 0.86f,
            maxLines = 2,
        )
    }
}
