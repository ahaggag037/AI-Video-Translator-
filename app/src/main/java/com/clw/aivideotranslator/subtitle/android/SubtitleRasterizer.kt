package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.clw.aivideotranslator.presentation.SubtitleStyle
import kotlin.math.roundToInt

/**
 * Native authoritative raster boundary for preview/export.
 *
 * Layout ownership remains in [SubtitleLayoutEngine]. This class accepts only a descriptor that can
 * be reproduced exactly for the same geometry/font/style/renderer environment, then rasterizes it
 * once into an immutable output-frame bitmap. Frame callbacks consume the bitmap and never run
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
        style: SubtitleStyle? = null,
    ): SubtitleRasterResult {
        if (descriptor.fontAssetHash != font.profile.assetSha256) {
            return SubtitleRasterResult.Rejected("FONT_IDENTITY_MISMATCH")
        }
        val estimatedBytes = geometry.uprightWidthPx.toLong() * geometry.uprightHeightPx.toLong() * 4L
        if (estimatedBytes > MAX_SINGLE_RASTER_BYTES) {
            return SubtitleRasterResult.Rejected("RASTER_MEMORY_BUDGET_EXCEEDED")
        }

        val effectiveConfig = runCatching {
            style?.let { AuthoredSubtitleStyleLayout.configFor(it, config) } ?: config
        }.getOrElse {
            return SubtitleRasterResult.Rejected("INVALID_AUTHORED_STYLE")
        }
        val reproduced = layoutEngine.layout(
            rawText = descriptor.text,
            geometry = geometry,
            font = font,
            rendererEnvironment = descriptor.rendererEnvironment,
            style = style,
        )
        val reproducedDescriptor = (reproduced as? SubtitleLayoutResult.Fits)?.descriptor
            ?: return SubtitleRasterResult.Rejected("LAYOUT_NO_LONGER_ACCEPTED")
        if (reproducedDescriptor != descriptor) {
            return SubtitleRasterResult.Rejected("LAYOUT_DESCRIPTOR_MISMATCH")
        }

        val displayText = SubtitleDisplayText.fromSemanticLines(descriptor.text, descriptor.lineRanges)
            ?: return SubtitleRasterResult.Rejected("LINE_RANGE_MISMATCH")
        val safeWidth = descriptor.safeRect.right - descriptor.safeRect.left
        val layout = layoutEngine.buildLayout(
            displayText = displayText,
            semanticText = descriptor.text,
            width = safeWidth,
            fontPx = descriptor.fontPx,
            typeface = font.typeface,
            effectiveConfig = effectiveConfig,
            style = style,
        )
        if (layout.lineCount != descriptor.lineRanges.size) {
            return SubtitleRasterResult.Rejected("RASTER_LINE_COUNT_MISMATCH")
        }

        val effectGuard = style?.let {
            kotlin.math.ceil(
                AuthoredSubtitleStyleLayout.outlineWidthPx(it, descriptor.fontPx) +
                    AuthoredSubtitleStyleLayout.shadowRadiusPx(it, descriptor.fontPx) +
                    AuthoredSubtitleStyleLayout.shadowOffsetYPx(it, descriptor.fontPx),
            ).toInt() + 2
        } ?: config.guardPx
        val inkMeasurement = inkBoundsProvider.measure(layout, maxOf(config.guardPx, effectGuard))
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
            if (style?.backgroundEnabled != false) {
                val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = Color.argb(191, 0, 0, 0)
                    this.style = Paint.Style.FILL
                }
                val radius = maxOf(2f, descriptor.fontPx * 0.15f)
                canvas.drawRoundRect(
                    RectF(box.left.toFloat(), box.top.toFloat(), box.right.toFloat(), box.bottom.toFloat()),
                    radius,
                    radius,
                    boxPaint,
                )
            }

            val translateX = descriptor.inkBounds.left - localInk.left
            val translateY = descriptor.inkBounds.top - localInk.top
            val authoredOpacity = style?.opacity ?: 1f
            val authoredColor = style?.textColorArgb?.toInt() ?: Color.WHITE
            val alpha = (Color.alpha(authoredColor) * authoredOpacity)
                .roundToInt()
                .coerceIn(0, 255)
            val fillColor = Color.argb(
                alpha,
                Color.red(authoredColor),
                Color.green(authoredColor),
                Color.blue(authoredColor),
            )
            val outlinePx = style?.let {
                AuthoredSubtitleStyleLayout.outlineWidthPx(it, descriptor.fontPx)
            } ?: 0f

            canvas.save()
            canvas.translate(translateX.toFloat(), translateY.toFloat())
            if (outlinePx > 0f) {
                layout.paint.apply {
                    this.style = Paint.Style.STROKE
                    strokeWidth = outlinePx * 2f
                    color = Color.argb(alpha, 0, 0, 0)
                    clearShadowLayer()
                }
                layout.draw(canvas)
            }
            layout.paint.apply {
                this.style = Paint.Style.FILL
                strokeWidth = 0f
                color = fillColor
                if (style?.shadowEnabled == true) {
                    setShadowLayer(
                        AuthoredSubtitleStyleLayout.shadowRadiusPx(style, descriptor.fontPx),
                        0f,
                        AuthoredSubtitleStyleLayout.shadowOffsetYPx(style, descriptor.fontPx),
                        Color.argb(alpha, 0, 0, 0),
                    )
                } else {
                    clearShadowLayer()
                }
            }
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