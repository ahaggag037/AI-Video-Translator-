package com.clw.aivideotranslator.subtitle.android

import android.content.Context
import android.icu.util.VersionInfo
import android.net.Uri
import android.os.Build
import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.VideoProbe
import com.clw.aivideotranslator.media.FrameGeometryResolver

/**
 * One immutable presentation-layout truth shared by preview and burned export.
 *
 * Timing/text still come from the live P0-F presentation cues. This snapshot only freezes native
 * layout/font/geometry identity so preview and export cannot independently lay out the same text.
 */
data class LivePresentationRasterSnapshot(
    val geometry: FrameGeometry,
    val timeline: SnapshotRasterTimeline,
    val sourceHasAudio: Boolean,
)

object LivePresentationRasterSnapshotFactory {
    fun build(
        context: Context,
        sourceUri: Uri,
        cues: List<ArabicSubtitleCue>,
    ): Result<LivePresentationRasterSnapshot> = runCatching {
        val metadata = VideoProbe.read(context.applicationContext, sourceUri).getOrThrow()
        buildFromMetadata(context, metadata, cues)
    }

    fun build(
        context: Context,
        metadata: VideoMetadata,
        cues: List<ArabicSubtitleCue>,
    ): Result<LivePresentationRasterSnapshot> = runCatching {
        buildFromMetadata(context, metadata, cues)
    }

    private fun buildFromMetadata(
        context: Context,
        metadata: VideoMetadata,
        cues: List<ArabicSubtitleCue>,
    ): LivePresentationRasterSnapshot {
        require(cues.isNotEmpty()) { "no presentation cues for raster snapshot" }
        val encodedWidth = requireNotNull(metadata.width) { "video width is unavailable" }
        val encodedHeight = requireNotNull(metadata.height) { "video height is unavailable" }
        val canonical = FrameGeometryResolver.canonical(
            encodedWidthPx = encodedWidth,
            encodedHeightPx = encodedHeight,
            rotationDegrees = metadata.rotationDegrees,
        )
        val geometry = FrameGeometry(
            uprightWidthPx = canonical.uprightWidthPx,
            uprightHeightPx = canonical.uprightHeightPx,
        )
        val font = SubtitleFonts.loadExperimentCandidate(context.applicationContext)
        val rendererEnvironment = buildString {
            append("api=")
            append(Build.VERSION.SDK_INT)
            append(";icu=")
            append(VersionInfo.ICU_VERSION)
            append(";font=")
            append(font.profile.profileId)
        }
        val layoutEngine = SubtitleLayoutEngine()

        val rasterCues = cues.mapIndexed { index, cue ->
            val layout = layoutEngine.layout(
                rawText = cue.text,
                geometry = geometry,
                font = font,
                rendererEnvironment = rendererEnvironment,
            )
            val descriptor = (layout as? SubtitleLayoutResult.Fits)?.descriptor
                ?: error("subtitle layout is not safely renderable: ${layout.status}")
            val requestId = "live-${index}-${cue.sourceUnitId}-${cue.startMs}-${cue.endMs}"
            SnapshotRasterCue(
                cueId = requestId,
                startUs = Math.multiplyExact(cue.startMs, 1_000L),
                endUs = Math.multiplyExact(cue.endMs, 1_000L),
                request = RasterRequest(
                    requestId = requestId,
                    descriptor = descriptor,
                    geometry = geometry,
                    font = font,
                ),
            )
        }

        return LivePresentationRasterSnapshot(
            geometry = geometry,
            timeline = SnapshotRasterTimeline(rasterCues),
            sourceHasAudio = metadata.hasAudio,
        )
    }
}
