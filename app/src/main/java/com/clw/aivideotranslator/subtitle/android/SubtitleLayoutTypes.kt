package com.clw.aivideotranslator.subtitle.android

data class PixelRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    init { require(right > left && bottom > top) }
    fun contains(other: PixelRect): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

data class FrameGeometry(
    val uprightWidthPx: Int,
    val uprightHeightPx: Int,
    val videoContentRect: PixelRect = PixelRect(0, 0, uprightWidthPx, uprightHeightPx),
) {
    init {
        require(uprightWidthPx > 0 && uprightHeightPx > 0)
        require(videoContentRect.left >= 0 && videoContentRect.top >= 0)
        require(videoContentRect.right <= uprightWidthPx && videoContentRect.bottom <= uprightHeightPx)
    }
}

enum class SubtitleLayoutStatus { FITS, OVERFLOW, REVIEW_REQUIRED }

data class SubtitleLayoutDescriptor(
    val text: String,
    val fontPx: Int,
    val lineRanges: List<IntRange>,
    val inkBounds: PixelRect,
    val boxBounds: PixelRect,
    val safeRect: PixelRect,
    val fontAssetHash: String,
    val rendererEnvironment: String,
) {
    init {
        require(text.isNotBlank())
        require(fontPx > 0)
        require(lineRanges.size in 1..2)
        require(fontAssetHash.isNotBlank())
        require(rendererEnvironment.isNotBlank())
        require(safeRect.contains(inkBounds) && safeRect.contains(boxBounds)) {
            "accepted layout must contain glyph ink and box inside safe area"
        }
    }
}

sealed interface SubtitleLayoutResult {
    val status: SubtitleLayoutStatus
    data class Fits(val descriptor: SubtitleLayoutDescriptor) : SubtitleLayoutResult {
        override val status = SubtitleLayoutStatus.FITS
    }
    data class Overflow(val reason: String) : SubtitleLayoutResult {
        override val status = SubtitleLayoutStatus.OVERFLOW
    }
    data class ReviewRequired(val reason: String) : SubtitleLayoutResult {
        override val status = SubtitleLayoutStatus.REVIEW_REQUIRED
    }
}
