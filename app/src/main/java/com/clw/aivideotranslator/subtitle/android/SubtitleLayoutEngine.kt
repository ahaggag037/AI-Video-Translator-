package com.clw.aivideotranslator.subtitle.android

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import com.clw.aivideotranslator.semantic.TextBoundaryProvider
import com.clw.aivideotranslator.semantic.TextPolicy
import kotlin.math.abs

class SubtitleLayoutEngine(
    private val boundaryProvider: TextBoundaryProvider = AndroidTextBoundaryProvider(),
    private val inkBoundsProvider: InkBoundsProvider = RasterInkBoundsProvider(),
    private val config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
) {
    fun layout(
        rawText: String,
        geometry: FrameGeometry,
        typeface: Typeface,
        fontProfile: SubtitleFontProfile,
        rendererEnvironment: String,
    ): SubtitleLayoutResult {
        if (rendererEnvironment.isBlank()) return SubtitleLayoutResult.ReviewRequired("MISSING_RENDERER_ENVIRONMENT")
        SubtitleFonts.requirePinned(fontProfile)

        val canonical = runCatching { TextPolicy.canonicalView(rawText) }.getOrElse {
            return SubtitleLayoutResult.ReviewRequired("INVALID_TEXT")
        }
        if (canonical.requiresReview) {
            return SubtitleLayoutResult.ReviewRequired("TEXT_POLICY_REVIEW:${canonical.warnings.sorted().joinToString(",")}")
        }
        val text = canonical.displayCanonical
        if (text.isBlank()) return SubtitleLayoutResult.ReviewRequired("EMPTY_TEXT")

        val safe = runCatching { SubtitleDefaults.safeRect(geometry, config) }.getOrElse {
            return SubtitleLayoutResult.ReviewRequired("INVALID_FRAME_GEOMETRY")
        }
        val safeWidth = safe.right - safe.left
        val preferred = SubtitleDefaults.preferredFontPx(geometry, config)
        val floor = SubtitleDefaults.floorFontPx(geometry, config)
        if (floor > preferred) return SubtitleLayoutResult.ReviewRequired("INVALID_FONT_RANGE")

        val boundaries = boundaryProvider.boundaries(text, canonical.protectedRanges)
        val legalBreaks = boundaries.legalLineBreakOffsets.sorted()
        if (legalBreaks.size > config.candidateCap) {
            return SubtitleLayoutResult.ReviewRequired("LAYOUT_BUDGET_EXCEEDED")
        }

        var incompleteInkProof = false
        var fontPx = preferred
        while (fontPx >= floor) {
            val candidates = buildList<Int?> {
                add(null)
                legalBreaks.forEach { add(it) }
            }
            val fits = mutableListOf<Pair<SubtitleLayoutDescriptor, Float>>()
            candidates.forEach { forcedBreak ->
                val display = forcedBreak?.let { offset ->
                    text.substring(0, offset) + "\n" + text.substring(offset)
                } ?: text
                val native = buildLayout(display, text, safeWidth, fontPx, typeface)
                if (native.lineCount !in 1..config.maxLines) return@forEach
                if (native.getLineEnd(native.lineCount - 1) != display.length) return@forEach
                if (forcedBreak != null && native.lineCount != 2) return@forEach

                val semanticRanges = semanticLineRanges(native, text, forcedBreak)
                    ?: return@forEach
                if (semanticRanges.size != native.lineCount) return@forEach

                val inkMeasurement = inkBoundsProvider.measure(native, config.guardPx)
                val ink = inkMeasurement.bounds
                if (!inkMeasurement.proofComplete || ink == null) {
                    incompleteInkProof = true
                    return@forEach
                }
                val paddingX = maxOf(1, (fontPx * config.paddingXEm).toInt())
                val paddingY = maxOf(1, (fontPx * config.paddingYEm).toInt())

                val placedInk = PixelRect(
                    left = safe.left + ink.left,
                    top = safe.bottom - paddingY - ink.bottom + ink.top,
                    right = safe.left + ink.right,
                    bottom = safe.bottom - paddingY,
                )
                val box = PixelRect(
                    left = placedInk.left - paddingX,
                    top = placedInk.top - paddingY,
                    right = placedInk.right + paddingX,
                    bottom = placedInk.bottom + paddingY,
                )
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

    private fun buildLayout(
        displayText: String,
        semanticText: String,
        width: Int,
        fontPx: Int,
        typeface: Typeface,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.typeface = typeface
            textSize = fontPx.toFloat()
            color = Color.WHITE
        }
        val direction = if (opaqueLtrOnly(semanticText)) TextDirectionHeuristics.LTR else TextDirectionHeuristics.RTL
        return StaticLayout.Builder.obtain(displayText, 0, displayText.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setIncludePad(true)
            .setLineSpacing(0f, config.lineSpacingMultiplier)
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
