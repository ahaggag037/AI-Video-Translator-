package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.text.LineBreaker
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint

/**
 * Shadow-only native raster boundary for X003/X004/X006.
 *
 * Layout ownership remains in [SubtitleLayoutEngine]. This class accepts only a descriptor that can
 * be reproduced exactly for the same geometry/font/renderer environment, then rasterizes it once
 * into an immutable output-frame bitmap. Frame callbacks must consume the bitmap; they must not run
 * Android text layout again.
 */
class SubtitleRasterizer(
    private val layoutEngine: SubtitleLayoutEngine = SubtitleLayoutEngine(),
    private val inkBoundsProvider: InkBoundsProvider = RasterInkBoundsProvider(),
    private val config: SubtitleLayoutConfig = SubtitleLayoutConfig(),
) {
    fun rasterize(
        descriptor: SubtitleLayoutDescriptor,
        geometry: FrameGeometry,
        font: LoadedSubtitleFont,
    ): SubtitleRasterResult {
        if (descriptor.fontAssetHash != font.profile.assetSha256) {
            return SubtitleRasterResult.Rejected("FONT_IDENTITY_MISMATCH")
        }
        val estimatedBytes = geometry.uprightWidthPx.toLong() * geometry.uprightHeightPx.toLong() * 4L
        if (estimatedBytes > MAX_SINGLE_RASTER_BYTES) {
            return SubtitleRasterResult.Rejected("RASTER_MEMORY_BUDGET_EXCEEDED")
        }

        val reproduced = layoutEngine.layout(
            rawText = descriptor.text,
            geometry = geometry,
            font = font,
            rendererEnvironment = descriptor.rendererEnvironment,
        )
        val reproducedDescriptor = (reproduced as? SubtitleLayoutResult.Fits)?.descriptor
            ?: return SubtitleRasterResult.Rejected("LAYOUT_NO_LONGER_ACCEPTED")
        if (reproducedDescriptor != descriptor) {
            return SubtitleRasterResult.Rejected("LAYOUT_DESCRIPTOR_MISMATCH")
        }

        val displayText = displayText(descriptor)
            ?: return SubtitleRasterResult.Rejected("LINE_RANGE_MISMATCH")
        val safeWidth = descriptor.safeRect.right - descriptor.safeRect.left
        val layout = buildLayout(
            displayText = displayText,
            semanticText = descriptor.text,
            width = safeWidth,
            fontPx = descriptor.fontPx,
            font = font,
        )
        if (layout.lineCount != descriptor.lineRanges.size) {
            return SubtitleRasterResult.Rejected("RASTER_LINE_COUNT_MISMATCH")
        }

        val inkMeasurement = inkBoundsProvider.measure(layout, config.guardPx)
        val localInk = inkMeasurement.bounds
        if (!inkMeasurement.proofComplete || localInk == null) {
            return SubtitleRasterResult.Rejected(inkMeasurement.diagnostic ?: "RASTER_INK_UNPROVEN")
        }
        val descriptorInkWidth = descriptor.inkBounds.right - descriptor.inkBounds.left
        val descriptorInkHeight = descriptor.inkBounds.bottom - descriptor.inkBounds.top
        if (localInk.width() != descriptorInkWidth || localInk.height() != descriptorInkHeight) {
            return SubtitleRasterResult.Rejected("RASTER_INK_MISMATCH")
        }

        val mutable = Bitmap.createBitmap(
            geometry.uprightWidthPx,
            geometry.uprightHeightPx,
            Bitmap.Config.ARGB_8888,
        )
        return try {
            mutable.eraseColor(Color.TRANSPARENT)
            val canvas = Canvas(mutable)
            val box = descriptor.boxBounds
            val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(191, 0, 0, 0)
                style = Paint.Style.FILL
            }
            val radius = maxOf(2f, descriptor.fontPx * 0.15f)
            canvas.drawRoundRect(
                RectF(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()),
                radius,
                radius,
                boxPaint,
            )

            val translateX = descriptor.inkBounds.left - localInk.left
            val translateY = descriptor.inkBounds.top - localInk.top
            canvas.save()
            canvas.translate(translateX.toFloat(), translateY.toFloat())
            layout.draw(canvas)
            canvas.restore()

            val immutable = requireNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false)) {
                "failed to freeze subtitle raster"
            }
            SubtitleRasterResult.Ready(
                ImmutableSubtitleRaster(
                    bitmap = immutable,
                    descriptor = descriptor,
                    frameWidthPx = geometry.uprightWidthPx,
                    frameHeightPx = geometry.uprightHeightPx,
                )
            )
        } catch (error: RuntimeException) {
            SubtitleRasterResult.Rejected("RASTERIZATION_FAILED:${error.javaClass.simpleName}")
        } finally {
            mutable.recycle()
        }
    }

    private fun displayText(descriptor: SubtitleLayoutDescriptor): String? {
        if (descriptor.lineRanges.size == 1) return descriptor.text
        val first = descriptor.lineRanges[0]
        val second = descriptor.lineRanges[1]
        val breakOffset = first.last + 1
        if (first.first != 0 || second.first != breakOffset || second.last != descriptor.text.lastIndex) {
            return null
        }
        return descriptor.text.substring(0, breakOffset) + "\n" + descriptor.text.substring(breakOffset)
    }

    private fun buildLayout(
        displayText: String,
        semanticText: String,
        width: Int,
        fontPx: Int,
        font: LoadedSubtitleFont,
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = font.typeface
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

    private fun opaqueLtrOnly(text: String): Boolean {
        val hasArabic = text.any {
            it.code in 0x0600..0x06FF || it.code in 0x0750..0x077F || it.code in 0x08A0..0x08FF
        }
        return !hasArabic && (text.contains("://") || text.contains('@') || text.startsWith('`'))
    }

    companion object {
        const val MAX_SINGLE_RASTER_BYTES: Long = 16L * 1024L * 1024L
    }
}

data class ImmutableSubtitleRaster(
    val bitmap: Bitmap,
    val descriptor: SubtitleLayoutDescriptor,
    val frameWidthPx: Int,
    val frameHeightPx: Int,
) {
    init {
        require(!bitmap.isMutable) { "published subtitle raster must be immutable" }
        require(bitmap.width == frameWidthPx && bitmap.height == frameHeightPx)
        require(bitmap.config == Bitmap.Config.ARGB_8888)
    }

    val byteCount: Long get() = bitmap.allocationByteCount.toLong()
}

sealed interface SubtitleRasterResult {
    data class Ready(val raster: ImmutableSubtitleRaster) : SubtitleRasterResult
    data class Rejected(val reason: String) : SubtitleRasterResult {
        init { require(reason.isNotBlank()) }
    }
}
