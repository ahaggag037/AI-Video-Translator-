package com.clw.aivideotranslator.app

import android.content.Context
import android.net.Uri
import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.editor.DurableDisplayTimingState
import com.clw.aivideotranslator.editor.DurableEditorTextState
import com.clw.aivideotranslator.editor.DurableSubtitleStyleState
import com.clw.aivideotranslator.presentation.CommittedPresentationAssembler
import com.clw.aivideotranslator.presentation.LayoutStatus
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshot
import com.clw.aivideotranslator.subtitle.android.LivePresentationRasterSnapshotFactory

internal data class ProductPresentationBundle(
    val sourceUri: Uri,
    val durationMs: Long,
    val cues: List<ArabicSubtitleCue>,
    val rasterSnapshot: LivePresentationRasterSnapshot,
    val layoutStatus: LayoutStatus,
) {
    init {
        require(durationMs > 0L) { "presentation video duration is unavailable" }
        require(cues.isNotEmpty()) { "presentation requires committed cues" }
        require(cues.last().endMs <= durationMs) { "presentation cues exceed video duration" }
        require(layoutStatus == LayoutStatus.FITS) { "frozen production presentation must fit" }
    }
}

/** Builds preview/SRT/MP4 truth exactly once from committed text, timing and style sidecars. */
internal object ProductPresentationBundleFactory {
    fun build(
        context: Context,
        sourceUri: Uri,
        metadata: VideoMetadata,
        units: List<SourceUnit>,
        entries: List<TranslationEntry>,
        textState: DurableEditorTextState,
        timingState: DurableDisplayTimingState,
        styleState: DurableSubtitleStyleState,
    ): Result<ProductPresentationBundle> = runCatching {
        val durationMs = requireNotNull(metadata.durationMs) { "video duration is unavailable" }
        require(durationMs > 0L) { "video duration is invalid" }
        val cues = CommittedPresentationAssembler.cues(
            units = units,
            entries = entries,
            textState = textState,
            timingState = timingState,
        )
        require(cues.last().endMs <= durationMs) { "committed subtitle timing exceeds video duration" }
        val snapshot = LivePresentationRasterSnapshotFactory.buildStyled(
            context = context.applicationContext,
            metadata = metadata,
            cues = cues,
            styleForCue = styleState::effective,
        ).getOrThrow()
        ProductPresentationBundle(
            sourceUri = sourceUri,
            durationMs = durationMs,
            cues = cues,
            rasterSnapshot = snapshot,
            layoutStatus = LayoutStatus.FITS,
        )
    }
}
