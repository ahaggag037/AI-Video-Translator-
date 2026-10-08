package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.text.StaticLayout

data class InkBoundsMeasurement(
    val bounds: Rect?,
    val proofComplete: Boolean,
    val diagnostic: String? = null,
)

interface InkBoundsProvider {
    fun measure(layout: StaticLayout, guardPx: Int): InkBoundsMeasurement
}

class RasterInkBoundsProvider(
    private val maxScanPixels: Long = 8_000_000L,
) : InkBoundsProvider {
    override fun measure(layout: StaticLayout, guardPx: Int): InkBoundsMeasurement {
        require(guardPx >= 0)
        val width = layout.width + guardPx * 2
        val height = layout.height + guardPx * 2
        if (width <= 0 || height <= 0) return InkBoundsMeasurement(null, false, "EMPTY_RASTER")
        if (width.toLong() * height.toLong() > maxScanPixels) {
            return InkBoundsMeasurement(null, false, "INK_SCAN_BUDGET_EXCEEDED")
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            bitmap.eraseColor(Color.TRANSPARENT)
            Canvas(bitmap).apply {
                translate(guardPx.toFloat(), guardPx.toFloat())
                layout.draw(this)
            }
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
            var minX = width
            var minY = height
            var maxX = -1
            var maxY = -1
            for (y in 0 until height) {
                val row = y * width
                for (x in 0 until width) {
                    if ((pixels[row + x] ushr 24) != 0) {
                        minX = minOf(minX, x)
                        minY = minOf(minY, y)
                        maxX = maxOf(maxX, x)
                        maxY = maxOf(maxY, y)
                    }
                }
            }
            if (maxX < 0 || maxY < 0) return InkBoundsMeasurement(null, false, "NO_VISIBLE_INK")
            val touchesOuterEdge = minX == 0 || minY == 0 || maxX == width - 1 || maxY == height - 1
            InkBoundsMeasurement(
                bounds = Rect(
                    minX - guardPx,
                    minY - guardPx,
                    maxX - guardPx + 1,
                    maxY - guardPx + 1,
                ),
                proofComplete = !touchesOuterEdge,
                diagnostic = if (touchesOuterEdge) "INK_TOUCHES_GUARD_EDGE" else null,
            )
        } finally {
            bitmap.recycle()
        }
    }
}
