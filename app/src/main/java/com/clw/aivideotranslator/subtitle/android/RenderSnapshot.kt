package com.clw.aivideotranslator.subtitle.android

import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.SubtitlePipeline
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * Immutable presentation snapshot shared by preview and burned export.
 *
 * Text layout is resolved exactly once here. Consumers receive the same descriptor-bearing raster
 * requests and may never independently re-layout the cue text. The content-derived [snapshotId]
 * changes when any cue timing/text, geometry, font profile, or renderer environment changes.
 */
data class RenderSnapshot(
    val snapshotId: String,
    val geometry: FrameGeometry,
    val fontProfile: SubtitleFontProfile,
    val rendererEnvironment: String,
    val cues: List<SnapshotRasterCue>,
) {
    val timeline: SnapshotRasterTimeline = SnapshotRasterTimeline(cues)

    init {
        require(snapshotId.matches(Regex("[0-9a-f]{64}"))) { "render snapshot id must be SHA-256" }
        require(rendererEnvironment.isNotBlank())
        require(cues.isNotEmpty()) { "render snapshot must contain at least one cue" }
        cues.forEach { cue ->
            require(cue.request.geometry == geometry) { "render snapshot geometry drift" }
            require(cue.request.font.profile == fontProfile) { "render snapshot font drift" }
            require(cue.request.descriptor.rendererEnvironment == rendererEnvironment) {
                "render snapshot environment drift"
            }
        }
    }
}

sealed interface RenderSnapshotBuildResult {
    data class Ready(val snapshot: RenderSnapshot) : RenderSnapshotBuildResult

    data class Rejected(
        val cueId: String,
        val status: SubtitleLayoutStatus,
        val reason: String,
    ) : RenderSnapshotBuildResult {
        init {
            require(cueId.isNotBlank())
            require(status != SubtitleLayoutStatus.FITS)
            require(reason.isNotBlank())
        }
    }
}

/**
 * Converts already-approved presentation-clock cues into the one immutable raster truth used by
 * preview and export. It does not repair timing, normalize text, or fall back to an alternate font.
 */
class RenderSnapshotFactory(
    private val layoutEngine: SubtitleLayoutEngine = SubtitleLayoutEngine(),
) {
    fun build(
        presentationCues: List<ArabicSubtitleCue>,
        geometry: FrameGeometry,
        font: LoadedSubtitleFont,
        rendererEnvironment: String,
    ): RenderSnapshotBuildResult {
        require(presentationCues.isNotEmpty()) { "no presentation cues for render snapshot" }
        require(rendererEnvironment.isNotBlank() && '\u0000' !in rendererEnvironment) {
            "invalid renderer environment"
        }

        val seenIds = linkedSetOf<String>()
        var previousEndMs = 0L
        presentationCues.forEach { cue ->
            require(cue.sourceUnitId.isNotBlank() && seenIds.add(cue.sourceUnitId)) {
                "render cue ids must be nonblank and unique"
            }
            require(cue.startMs >= previousEndMs && cue.endMs > cue.startMs) {
                "render cues must be sorted, non-overlapping, and positive-duration"
            }
            require(SubtitlePipeline.validateText(cue.text) == cue.text) {
                "render snapshot text must already be canonical"
            }
            previousEndMs = cue.endMs
        }

        val snapshotId = contentIdentity(
            cues = presentationCues,
            geometry = geometry,
            fontProfile = font.profile,
            rendererEnvironment = rendererEnvironment,
        )
        val rasterCues = ArrayList<SnapshotRasterCue>(presentationCues.size)

        presentationCues.forEach { cue ->
            when (val layout = layoutEngine.layout(cue.text, geometry, font, rendererEnvironment)) {
                is SubtitleLayoutResult.Fits -> {
                    require(layout.descriptor.text == cue.text) { "layout changed semantic cue text" }
                    rasterCues += SnapshotRasterCue(
                        cueId = cue.sourceUnitId,
                        startUs = Math.multiplyExact(cue.startMs, 1_000L),
                        endUs = Math.multiplyExact(cue.endMs, 1_000L),
                        request = RasterRequest(
                            requestId = "$snapshotId:${cue.sourceUnitId}",
                            descriptor = layout.descriptor,
                            geometry = geometry,
                            font = font,
                        ),
                    )
                }
                is SubtitleLayoutResult.Overflow -> return RenderSnapshotBuildResult.Rejected(
                    cueId = cue.sourceUnitId,
                    status = layout.status,
                    reason = layout.reason,
                )
                is SubtitleLayoutResult.ReviewRequired -> return RenderSnapshotBuildResult.Rejected(
                    cueId = cue.sourceUnitId,
                    status = layout.status,
                    reason = layout.reason,
                )
            }
        }

        return RenderSnapshotBuildResult.Ready(
            RenderSnapshot(
                snapshotId = snapshotId,
                geometry = geometry,
                fontProfile = font.profile,
                rendererEnvironment = rendererEnvironment,
                cues = rasterCues.toList(),
            )
        )
    }

    private fun contentIdentity(
        cues: List<ArabicSubtitleCue>,
        geometry: FrameGeometry,
        fontProfile: SubtitleFontProfile,
        rendererEnvironment: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fun field(value: String) {
            val bytes = value.toByteArray(StandardCharsets.UTF_8)
            digest.update((bytes.size ushr 24).toByte())
            digest.update((bytes.size ushr 16).toByte())
            digest.update((bytes.size ushr 8).toByte())
            digest.update(bytes.size.toByte())
            digest.update(bytes)
        }

        val rect = geometry.videoContentRect
        field(VERSION)
        field(geometry.uprightWidthPx.toString())
        field(geometry.uprightHeightPx.toString())
        field(rect.left.toString())
        field(rect.top.toString())
        field(rect.right.toString())
        field(rect.bottom.toString())
        field(fontProfile.profileId)
        field(fontProfile.assetSha256)
        field(fontProfile.weight.toString())
        field(rendererEnvironment)
        cues.forEach { cue ->
            field(cue.sourceUnitId)
            field(cue.startMs.toString())
            field(cue.endMs.toString())
            field(cue.text)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val VERSION = "render-snapshot-v1"
    }
}
