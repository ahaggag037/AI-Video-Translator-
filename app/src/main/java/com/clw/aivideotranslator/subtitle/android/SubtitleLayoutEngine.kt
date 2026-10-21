package com.clw.aivideotranslator.subtitle.android

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.clw.aivideotranslator.presentation.SubtitleAnchor
import com.clw.aivideotranslator.presentation.SubtitleStyle
import com.clw.aivideotranslator.presentation.SubtitleTextAlignment
import com.clw.aivideotranslator.semantic.TextBoundaryProvider
import com.clw.aivideotranslator.semantic.TextPolicy
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

class SubtitleLayoutEngine(
    private val boundaryProvider: TextBoundaryProvider = AndroidTextBoundaryProvider(),
    private val inkBoundsProvider: InkBoundsProvider = RasterInkBoundsProvider(),
    private val config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
) {
    fun layout(
        rawText: String,
        geometry: FrameGeometry,
        font: LoadedSubtitleFont,
        rendererEnvironment: String,
        style: SubtitleStyle? = null,
    ): SubtitleLayoutResult {
        if (rendererEnvironment.isBlank()) return SubtitleLayoutResult.ReviewRequired("MISSING_RENDERER_ENVIRONMENT")
        val fontProfile = font.profile
        val typeface = font.typeface
        if (style != null && (style.fontFamilyId != fontProfile.profileId || style.fontWeight != fontProfile.weight)) {
            return SubtitleLayoutResult.ReviewRequired("STYLE_FONT_PROFILE_MISMATCH")
        }
        val effectiveConfig = runCatching {
            style?.let { AuthoredSubtitleStyleLayout.configFor(it, config) } ?: config
        }.getOrElse {
            return SubtitleLayoutResult.ReviewRequired("INVALID_AUTHORED_STYLE")
        }

        val canonical = runCatching { TextPolicy.canonicalView(rawText) }.getOrElse {
            return SubtitleLayoutResult.ReviewRequired("INVALID_TEXT")
        }
        if (canonical.requiresReview) {
            return SubtitleLayoutResult.ReviewRequired("TEXT_POLICY_REVIEW:${canonical.warnings.sorted().joinToString(",")}")
        }
        val text = canonical.displayCanonical
        if (text.isBlank()) return SubtitleLayoutResult.ReviewRequired("EMPTY_TEXT")
        if (!font.supports(text)) return SubtitleLayoutResult.ReviewRequired("BUNDLED_FONT_GLYPH_UNSUPPORTED")

        val safe = runCatching { SubtitleDefaults.safeRect(geometry, effectiveConfig) }.getOrElse {
            return SubtitleLayoutResult.ReviewRequired("INVALID_FRAME_GEOMETRY")
        }
        val safeWidth = safe.right - safe.left
        val preferred = SubtitleDefaults.preferredFontPx(geometry, effectiveConfig)
        val floor = SubtitleDefaults.floorFontPx(geometry, effectiveConfig)
        if (floor > preferred) return SubtitleLayoutResult.ReviewRequired("INVALID_FONT_RANGE")

        val boundaries = boundaryProvider.boundaries(text, canonical.protectedRanges)
        val legalBreaks = boundaries.legalLineBreakOffsets.sorted()
        if (legalBreaks.size > effectiveConfig.candidateCap) {
            return SubtitleLayoutResult.ReviewRequired("LAYOUT_BUDGET_EXCEEDED")
        }

        var incompleteInkProof = false
        var fontPx = preferred
        while (fontPx >= floor) {
            val candidates = buildList<Int?> {
                add(null)
                if (effectiveConfig.maxLines > 1) legalBreaks.forEach { add(it) }
            }
            val fits = mutableListOf<Pair<SubtitleLayoutDescriptor, Float>>()
            candidates.forEach { forcedBreak ->
                val display = forcedBreak?.let { offset ->
                    text.substring(0, offset) + "\n" + text.substring(offset)
                } ?: text
                val native = buildLayout(
                    displayText = display,
                    semanticText = text,
                    width = safeWidth,
                    fontPx = fontPx,
                    typeface = typeface,
                    effectiveConfig = effectiveConfig,
                    style = style,
                )
                if (native.lineCount !in 1..effectiveConfig.maxLines) return@forEach
                if (native.getLineEnd(native.lineCount - 1) != display.length) return@forEach
                if (forcedBreak != null && native.lineCount != 2) return@forEach

                val semanticRanges = semanticLineRanges(native, text, forcedBreak)
                    ?: return@forEach
                if (semanticRanges.size != native.lineCount) return@forEach
                if (!SubtitleLineCoverage.usesLegalBreaks(text.length, semanticRanges, boundaries)) return@forEach

                val effectGuard = style?.let {
                    ceil(
                        AuthoredSubtitleStyleLayout.outlineWidthPx(it, fontPx) +
                            AuthoredSubtitleStyleLayout.shadowRadiusPx(it, fontPx) +
                            AuthoredSubtitleStyleLayout.shadowOffsetYPx(it, fontPx),
                    ).toInt() + 2
                } ?: effectiveConfig.guardPx
                val inkMeasurement = inkBoundsProvider.measure(native, maxOf(effectiveConfig.guardPx, effectGuard))
                val ink = inkMeasurement.bounds
                if (!inkMeasurement.proofComplete || ink == null) {
                    incompleteInkProof = true
                    return@forEach
                }
                val paddingX = maxOf(1, (fontPx * effectiveConfig.paddingXEm).toInt())
                val paddingY = maxOf(1, (fontPx * effectiveConfig.paddingYEm).toInt())
                val inkWidth = ink.right - ink.left
                val inkHeight = ink.bottom - ink.top
                val boxWidth = inkWidth + paddingX * 2
                val boxHeight = inkHeight + paddingY * 2
                if (boxWidth <= 0 || boxHeight <= 0) return@forEach
                if (boxWidth > safe.right - safe.left || boxHeight > safe.bottom - safe.top) return@forEach

                val content = geometry.videoContentRect
                val offsetX = style?.let {
                    ((content.right - content.left) * it.offsetXFraction).roundToInt()
                } ?: 0
                val offsetY = style?.let {
                    ((content.bottom - content.top) * it.offsetYFraction).roundToInt()
                } ?: 0
                val anchor = style?.anchor ?: SubtitleAnchor.BOTTOM_CENTER
                val boxLeft = when (anchor) {
                    SubtitleAnchor.TOP_START,
                    SubtitleAnchor.CENTER_START,
                    SubtitleAnchor.BOTTOM_START,
                    -> safe.left
                    SubtitleAnchor.TOP_CENTER,
                    SubtitleAnchor.CENTER,
                    SubtitleAnchor.BOTTOM_CENTER,
                    -> safe.left + ((safe.right - safe.left) - boxWidth) / 2
                    SubtitleAnchor.TOP_END,
                    SubtitleAnchor.CENTER_END,
                    SubtitleAnchor.BOTTOM_END,
                    -> safe.right - boxWidth
                } + offsetX
                val boxTop = when (anchor) {
                    SubtitleAnchor.TOP_START,
                    SubtitleAnchor.TOP_CENTER,
                    SubtitleAnchor.TOP_END,
                    -> safe.top
                    SubtitleAnchor.CENTER_START,
                    SubtitleAnchor.CENTER,
                    SubtitleAnchor.CENTER_END,
                    -> safe.top + ((safe.bottom - safe.top) - boxHeight) / 2
                    SubtitleAnchor.BOTTOM_START,
                    SubtitleAnchor.BOTTOM_CENTER,
                    SubtitleAnchor.BOTTOM_END,
                    -> safe.bottom - boxHeight
                } + offsetY

                val box = runCatching {
                    PixelRect(boxLeft, boxTop, boxLeft + boxWidth, boxTop + boxHeight)
                }.getOrNull() ?: return@forEach
                val placedInk = runCatching {
                    PixelRect(
                        left = box.left + paddingX,
                        top = box.top + paddingY,
                        right = box.left + paddingX + inkWidth,
                        bottom = box.top + paddingY + inkHeight,
                    )
                }.getOrNull() ?: return@forEach
                if (!safe.contains(placedInk) || !safe.contains(box)) return@forEach

                val descriptor = SubtitleLayoutDescriptor(
                    text = text,
                    fontPx = fontPx,
                    lineRanges = semanticRanges,
                    inkBounds = placedInk,
                    boxBounds = box,
                    safeRect = safe,
                    fontAssetHash = fontProfile.assetSha256,
                    rendererEnvironment = rendererEnvironment,
                )
                fits += descriptor to imbalance(native)
            }
            if (fits.isNotEmpty()) return SubtitleLayoutResult.Fits(fits.minBy { it.second }.first)
            fontPx -= 1
        }

        if (incompleteInkProof) return SubtitleLayoutResult.ReviewRequired("INK_CONTAINMENT_UNPROVEN")
        return SubtitleLayoutResult.Overflow("NO_LEGAL_LAYOUT_WITHIN_FONT_FLOOR")
    }

    internal fun buildLayout(
        displayText: String,
        semanticText: String,
        width: Int,
        fontPx: Int,
        typeface: Typeface,
        effectiveConfig: SubtitleLayoutConfig = config,
        style: SubtitleStyle? = null,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontPx.toFloat()
            color = Color.WHITE
            if (style != null) {
                val outline = AuthoredSubtitleStyleLayout.outlineWidthPx(style, fontPx)
                if (outline > 0f) {
                    this.style = Paint.Style.FILL_AND_STROKE
                    strokeWidth = outline * 2f
                }
                if (style.shadowEnabled) {
                    setShadowLayer(
                        AuthoredSubtitleStyleLayout.shadowRadiusPx(style, fontPx),
                        0f,
                        AuthoredSubtitleStyleLayout.shadowOffsetYPx(style, fontPx),
                        Color.BLACK,
                    )
                }
            }
        }
        val direction = if (opaqueLtrOnly(semanticText)) TextDirectionHeuristics.LTR else TextDirectionHeuristics.RTL
        val alignment = when (style?.alignment ?: SubtitleTextAlignment.CENTER) {
            SubtitleTextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            SubtitleTextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            SubtitleTextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        return StaticLayout.Builder.obtain(displayText, 0, displayText.length, paint, width)
            .setAlignment(alignment)
            .setIncludePad(true)
            .setLineSpacing(0f, effectiveConfig.lineSpacingMultiplier)
            .setTextDirection(direction)
            .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE)
            .setBreakStrategy(LineBreaker.BREAK_STRATEGY_SIMPLE)
            .build()
    }

    private fun semanticLineRanges(
        layout: StaticLayout,
        semanticText: String,
        forcedBreak: Int?,
    ): List<IntRange>? {
        if (forcedBreak == null) {
            return (0 until layout.lineCount).map { line ->
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line)
                if (end <= start) return null
                start until end
            }.takeIf { ranges -> ranges.lastOrNull()?.last == semanticText.lastIndex }
        }

        val ranges = mutableListOf<IntRange>()
        for (line in 0 until layout.lineCount) {
            val displayStart = layout.getLineStart(line)
            val displayEnd = layout.getLineEnd(line)
            val semanticStart = when {
                displayStart <= forcedBreak -> displayStart
                else -> displayStart - 1
            }
            val semanticEndExclusive = when {
                displayEnd <= forcedBreak -> displayEnd
                displayEnd == forcedBreak + 1 -> forcedBreak
                else -> displayEnd - 1
            }
            if (semanticEndExclusive <= semanticStart) return null
            ranges += semanticStart until semanticEndExclusive
        }
        val flattenedLength = ranges.sumOf { it.last - it.first + 1 }
        return ranges.takeIf { flattenedLength == semanticText.length }
    }

    private fun imbalance(layout: StaticLayout): Float {
        if (layout.lineCount <= 1) return 0f
        val first = layout.getLineRight(0) - layout.getLineLeft(0)
        val second = layout.getLineRight(1) - layout.getLineLeft(1)
        return abs(first - second)
    }

    private fun opaqueLtrOnly(text: String): Boolean {
        val hasArabic = text.any { it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0x08A0..0x08FF }
        return !hasArabic && (text.contains("://") || text.contains('@') || text.startsWith('`'))
    }
}
