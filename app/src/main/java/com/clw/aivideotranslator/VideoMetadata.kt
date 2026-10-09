package com.clw.aivideotranslator

import com.clw.aivideotranslator.media.CanonicalVideoFrame
import com.clw.aivideotranslator.media.FrameGeometryResolver

data class VideoMetadata(
    val displayName: String,
    val durationMs: Long?,
    val width: Int?,
    val height: Int?,
    val sizeBytes: Long?,
    val rotationDegrees: Int = 0,
) {
    init {
        require(rotationDegrees in setOf(0, 90, 180, 270)) { "unsupported video rotation" }
        require((width == null) == (height == null)) { "video dimensions must be both known or both absent" }
        require(width == null || (width > 0 && height!! > 0)) { "video dimensions must be positive" }
    }

    val durationLabel: String
        get() = durationMs?.let(::formatDuration) ?: "غير معروف"

    val resolutionLabel: String
        get() = if (width != null && height != null) "${width}×${height}" else "غير معروف"

    val sizeLabel: String
        get() = sizeBytes?.let(::formatBytes) ?: "غير معروف"

    /** Encoded geometry plus metadata rotation; no preview/view scaling is inferred here. */
    fun canonicalFrameOrNull(): CanonicalVideoFrame? {
        val encodedWidth = width ?: return null
        val encodedHeight = height ?: return null
        return FrameGeometryResolver.canonical(
            encodedWidthPx = encodedWidth,
            encodedHeightPx = encodedHeight,
            rotationDegrees = rotationDegrees,
        )
    }
}

internal fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1000L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

internal fun formatBytes(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L).toDouble()
    val mb = safe / (1024.0 * 1024.0)
    val gb = mb / 1024.0
    return if (gb >= 1.0) "%.2f GB".format(gb) else "%.1f MB".format(mb)
}
