package com.clw.aivideotranslator.media

/** Encoded/container geometry plus the rotation required to obtain the upright presentation frame. */
data class CanonicalVideoFrame(
    val encodedWidthPx: Int,
    val encodedHeightPx: Int,
    val rotationDegrees: Int,
    val uprightWidthPx: Int,
    val uprightHeightPx: Int,
) {
    init {
        require(encodedWidthPx > 0 && encodedHeightPx > 0) { "encoded frame must be positive" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "unsupported video rotation" }
        require(uprightWidthPx > 0 && uprightHeightPx > 0) { "upright frame must be positive" }
        val swapsAxes = rotationDegrees == 90 || rotationDegrees == 270
        require(uprightWidthPx == if (swapsAxes) encodedHeightPx else encodedWidthPx)
        require(uprightHeightPx == if (swapsAxes) encodedWidthPx else encodedHeightPx)
    }
}

/** Integer viewport bounds for the video pixels only; UI chrome/letterbox outside this rect is excluded. */
data class PreviewContentRect(
    val leftPx: Int,
    val topPx: Int,
    val rightPx: Int,
    val bottomPx: Int,
) {
    init { require(leftPx >= 0 && topPx >= 0 && rightPx > leftPx && bottomPx > topPx) }
    val widthPx: Int get() = rightPx - leftPx
    val heightPx: Int get() = bottomPx - topPx
}

data class PreviewFitCenterGeometry(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val contentRect: PreviewContentRect,
) {
    init {
        require(viewportWidthPx > 0 && viewportHeightPx > 0) { "preview viewport must be positive" }
        require(contentRect.rightPx <= viewportWidthPx && contentRect.bottomPx <= viewportHeightPx) {
            "video content rect escapes preview viewport"
        }
    }
}

/**
 * Shadow-only X004 geometry model.
 *
 * Subtitle layout truth stays in the upright VIDEO frame. Preview fit-center is a separate transform
 * into a UI viewport and must never redefine canonical subtitle coordinates. Integer fit-center uses
 * an inward floor so the model never includes UI letterbox pixels as video. Actual VideoView/export
 * transforms still require device comparison in X004; this class does not claim parity by itself.
 */
object FrameGeometryResolver {
    fun canonical(
        encodedWidthPx: Int,
        encodedHeightPx: Int,
        rotationDegrees: Int,
    ): CanonicalVideoFrame {
        require(encodedWidthPx > 0 && encodedHeightPx > 0) { "encoded frame must be positive" }
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "unsupported video rotation" }
        val swapsAxes = rotationDegrees == 90 || rotationDegrees == 270
        return CanonicalVideoFrame(
            encodedWidthPx = encodedWidthPx,
            encodedHeightPx = encodedHeightPx,
            rotationDegrees = rotationDegrees,
            uprightWidthPx = if (swapsAxes) encodedHeightPx else encodedWidthPx,
            uprightHeightPx = if (swapsAxes) encodedWidthPx else encodedHeightPx,
        )
    }

    fun fitCenter(
        frame: CanonicalVideoFrame,
        viewportWidthPx: Int,
        viewportHeightPx: Int,
    ): PreviewFitCenterGeometry {
        require(viewportWidthPx > 0 && viewportHeightPx > 0) { "preview viewport must be positive" }
        val videoW = frame.uprightWidthPx.toLong()
        val videoH = frame.uprightHeightPx.toLong()
        val viewportW = viewportWidthPx.toLong()
        val viewportH = viewportHeightPx.toLong()

        // Compare aspect ratios without floating point: videoW/videoH >= viewportW/viewportH.
        val widthLimited = Math.multiplyExact(videoW, viewportH) >= Math.multiplyExact(viewportW, videoH)
        val contentWidth: Int
        val contentHeight: Int
        if (widthLimited) {
            contentWidth = viewportWidthPx
            contentHeight = ((viewportW * videoH) / videoW).toInt().coerceAtLeast(1)
        } else {
            contentHeight = viewportHeightPx
            contentWidth = ((viewportH * videoW) / videoH).toInt().coerceAtLeast(1)
        }

        val left = (viewportWidthPx - contentWidth) / 2
        val top = (viewportHeightPx - contentHeight) / 2
        return PreviewFitCenterGeometry(
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx,
            contentRect = PreviewContentRect(
                leftPx = left,
                topPx = top,
                rightPx = left + contentWidth,
                bottomPx = top + contentHeight,
            ),
        )
    }
}
