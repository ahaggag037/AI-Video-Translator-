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

data class VideoPointPx(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) { "video point must be finite" } }
}

data class PreviewPointPx(val x: Double, val y: Double) {
    init { require(x.isFinite() && y.isFinite()) { "preview point must be finite" } }
}

/**
 * Continuous transform between the upright VIDEO coordinate space and the fit-center preview rect.
 * It deliberately performs no integer/pixel rounding; actual VideoView/device raster rounding remains
 * an X004 measurement. UI bars are outside the transform domain and cannot map back to video truth.
 */
data class VideoPreviewTransform(
    val frame: CanonicalVideoFrame,
    val preview: PreviewFitCenterGeometry,
) {
    fun videoToPreview(point: VideoPointPx): PreviewPointPx {
        require(point.x in 0.0..frame.uprightWidthPx.toDouble() &&
            point.y in 0.0..frame.uprightHeightPx.toDouble()) { "video point outside upright frame" }
        val rect = preview.contentRect
        return PreviewPointPx(
            x = rect.leftPx + point.x * rect.widthPx / frame.uprightWidthPx,
            y = rect.topPx + point.y * rect.heightPx / frame.uprightHeightPx,
        )
    }

    fun previewToVideo(point: PreviewPointPx): VideoPointPx {
        val rect = preview.contentRect
        require(point.x in rect.leftPx.toDouble()..rect.rightPx.toDouble() &&
            point.y in rect.topPx.toDouble()..rect.bottomPx.toDouble()) {
            "preview point is outside video content rect"
        }
        return VideoPointPx(
            x = (point.x - rect.leftPx) * frame.uprightWidthPx / rect.widthPx,
            y = (point.y - rect.topPx) * frame.uprightHeightPx / rect.heightPx,
        )
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

    fun transform(frame: CanonicalVideoFrame, preview: PreviewFitCenterGeometry): VideoPreviewTransform =
        VideoPreviewTransform(frame, preview)
}
