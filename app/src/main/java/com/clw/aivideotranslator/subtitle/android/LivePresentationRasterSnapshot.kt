package com.clw.aivideotranslator.subtitle.android

import android.content.Context
import android.icu.util.VersionInfo
import android.net.Uri
import android.os.Build
import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.VideoProbe
import com.clw.aivideotranslator.media.FrameGeometryResolver
import com.clw.aivideotranslator.presentation.SubtitleStyle

/**
 * One immutable presentation-layout truth shared by preview and burned export.
 *
 * Timing/text/style come from committed presentation truth. The snapshot freezes native
 * layout/font/geometry/style identity so preview and export cannot independently lay out the same
 * subtitle.
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
        buildFromMetadata(context, metadata, cues, null)
    }

    fun build(
        context: Context,
        metadata: VideoMetadata,
        cues: List<ArabicSubtitleCue>,
    ): Result<LivePresentationRasterSnapshot> = runCatching {
        buildFromMetadata(context, metadata, cues, null)
    }

    internal fun buildStyled(
        context: Context,
        sourceUri: Uri,
        cues: List<ArabicSubtitleCue>,
        styleForCue: (String) -> SubtitleStyle,
    ): Result<LivePresentationRasterSnapshot> = runCatching {
        val metadata = VideoProbe.read(context.applicationContext, sourceUri).getOrThrow()
        buildFromMetadata(context, metadata, cues, styleForCue)
    }

    internal fun buildStyled(
        context: Context,
        metadata: VideoMetadata,
        cues: List<ArabicSubtitleCue>,
        styleForCue: (String) -> SubtitleStyle,
    ): Result<LivePresentationRasterSnapshot> = runCatching {
        buildFromMetadata(context, metadata, cues, styleForCue)
    }

    private fun buildFromMetadata(
        context: Context,
        metadata: VideoMetadata,
        cues: List<ArabicSubtitleCue>,
        styleForCue: ((String) -> SubtitleStyle)?,
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
            val authoredStyle = styleForCue?.invoke(cue.sourceUnitId)
            val layout = layoutEngine.layout(
                rawText = cue.text,
                geometry = geometry,
                font = font,
                rendererEnvironment = rendererEnvironment,
                style = authoredStyle,
            )
            val descriptor = (layout as? SubtitleLayoutResult.Fits)?.descriptor
                ?: error("subtitle layout is not safely renderable: ${layout.status}")
            val styleIdentity = authoredStyle?.hashCode()?.toUInt()?.toString(16) ?: "legacy"
            val requestId = "live-${index}-${cue.sourceUnitId}-${cue.startMs}-${cue.endMs}-$styleIdentity"
            SnapshotRasterCue(
                cueId = requestId,
                startUs = Math.multiplyExact(cue.startMs, 1_000L),
                endUs = Math.multiplyExact(cue.endMs, 1_000L),
                request = RasterRequest(
                    requestId = requestId,
                    descriptor = descriptor,
                    geometry = geometry,
                    font = font,
                    style = authoredStyle,
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