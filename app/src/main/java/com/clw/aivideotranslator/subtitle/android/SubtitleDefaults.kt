package com.clw.aivideotranslator.subtitle.android

data class SubtitleLayoutConfig(
    val maxLines: Int = 2,
    val horizontalMarginFraction: Double = 0.06,
    val verticalMarginFraction: Double = 0.06,
    val preferredShortSideFraction: Double = 0.055,
    val floorShortSideFraction: Double = 0.036,
    val minimumPreferredPx: Int = 16,
    val absoluteFloorPx: Int = 14,
    val lineSpacingMultiplier: Float = 1.10f,
    val paddingXEm: Float = 0.35f,
    val paddingYEm: Float = 0.20f,
    val guardPx: Int = 2,
    val candidateCap: Int = 256,
) {
    init {
        require(maxLines == 2) { "V1 authored subtitle contract is two lines" }
        require(horizontalMarginFraction in 0.0..0.25)
        require(verticalMarginFraction in 0.0..0.25)
        require(preferredShortSideFraction > floorShortSideFraction)
        require(minimumPreferredPx >= absoluteFloorPx && absoluteFloorPx > 0)
        require(lineSpacingMultiplier >= 1.0f)
        require(paddingXEm >= 0f && paddingYEm >= 0f)
        require(guardPx >= 0)
        require(candidateCap > 0)
    }
}

object SubtitleDefaults {
    const val VERSION = "subtitle-layout-defaults-v1"

    fun preferredFontPx(
        geometry: FrameGeometry,
        config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
    ): Int {
        val shortSide = minOf(contentWidth(geometry), contentHeight(geometry))
        return maxOf(config.minimumPreferredPx, (shortSide * config.preferredShortSideFraction).toInt())
    }

    fun floorFontPx(
        geometry: FrameGeometry,
        config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
    ): Int {
        val shortSide = minOf(contentWidth(geometry), contentHeight(geometry))
        return maxOf(config.absoluteFloorPx, (shortSide * config.floorShortSideFraction).toInt())
    }

    fun safeRect(
        geometry: FrameGeometry,
        config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
    ): PixelRect {
        val content = geometry.videoContentRect
        val width = content.right - content.left
        val height = content.bottom - content.top
        val horizontal = (width * config.horizontalMarginFraction).toInt()
        val vertical = (height * config.verticalMarginFraction).toInt()
        require(width - horizontal * 2 > 0 && height - vertical * 2 > 0) { "safe area collapsed" }
        return PixelRect(
            left = content.left + horizontal,
            top = content.top + vertical,
            right = content.right - horizontal,
            bottom = content.bottom - vertical,
        )
    }

    private fun contentWidth(geometry: FrameGeometry): Int =
        geometry.videoContentRect.right - geometry.videoContentRect.left

    private fun contentHeight(geometry: FrameGeometry): Int =
        geometry.videoContentRect.bottom - geometry.videoContentRect.top
}
