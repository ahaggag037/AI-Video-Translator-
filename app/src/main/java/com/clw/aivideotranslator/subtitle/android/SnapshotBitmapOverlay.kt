package com.clw.aivideotranslator.subtitle.android

import android.graphics.Bitmap
import android.graphics.Color
import androidx.annotation.OptIn
import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Immutable render-snapshot cue on the source presentation clock.
 */
data class SnapshotRasterCue(
    val cueId: String,
    val startUs: Long,
    val endUs: Long,
    val request: RasterRequest,
) {
    init {
        require(cueId.isNotBlank())
        require(startUs >= 0L)
        require(endUs > startUs)
    }
}

/**
 * Sorted half-open cue timeline. Lookup is binary and independent from frame cadence.
 */
class SnapshotRasterTimeline(
    cues: List<SnapshotRasterCue>,
) {
    val cues: List<SnapshotRasterCue> = cues.toList()
    private val starts = LongArray(cues.size) { cues[it].startUs }

    init {
        this.cues.forEachIndexed { index, cue ->
            if (index > 0) {
                val previous = this.cues[index - 1]
                require(cue.startUs >= previous.endUs) { "snapshot cues must be sorted and non-overlapping" }
            }
        }
        require(this.cues.map { it.cueId }.toSet().size == this.cues.size) {
            "snapshot cue ids must be unique"
        }
        require(this.cues.map { it.request.requestId }.toSet().size == this.cues.size) {
            "snapshot raster request ids must be unique"
        }
    }

    fun locate(sourceTimeUs: Long): LocatedRasterCue {
        if (cues.isEmpty()) return LocatedRasterCue(active = null, next = null)
        var low = 0
        var high = starts.size
        while (low < high) {
            val mid = (low + high) ushr 1
            if (starts[mid] <= sourceTimeUs) low = mid + 1 else high = mid
        }
        val candidateIndex = low - 1
        val active = candidateIndex.takeIf { index ->
            index >= 0 && sourceTimeUs < cues[index].endUs
        }?.let(cues::get)
        val nextIndex = when {
            active != null -> candidateIndex + 1
            low < cues.size -> low
            else -> -1
        }
        val next = nextIndex.takeIf { it in cues.indices }?.let(cues::get)
        return LocatedRasterCue(active, next)
    }
}

data class LocatedRasterCue(
    val active: SnapshotRasterCue?,
    val next: SnapshotRasterCue?,
)

/**
 * Shadow-only Media3 bitmap adapter for X004.
 *
 * It never performs text layout or rasterization on the GL/frame thread. The coordinator performs
 * that work on its single worker. A non-empty active cue must resolve within the bounded N31 wait or
 * export fails; transparency is returned only when the snapshot timeline contains no active cue.
 *
 * Media3's BitmapOverlay retains the last Bitmap while its texture is active. Therefore the lease
 * for the previously uploaded bitmap is released only after super.getTextureId() has successfully
 * uploaded the newly selected bitmap/transparent frame.
 */
@OptIn(UnstableApi::class)
class SnapshotBitmapOverlay(
    private val timeline: SnapshotRasterTimeline,
    private val expectedGeometry: FrameGeometry,
    private val exportRangeStartUs: Long,
    private val coordinator: RasterCoordinator,
    private val overlaySettings: OverlaySettings = StaticOverlaySettings.Builder().build(),
) : BitmapOverlay() {
    private val stateLock = Any()
    private val transparentBitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).let { mutable ->
        mutable.eraseColor(Color.TRANSPARENT)
        val frozen = requireNotNull(mutable.copy(Bitmap.Config.ARGB_8888, false))
        mutable.recycle()
        frozen
    }

    private var configured = false
    private var released = false
    private var selectedRequestId: String? = null
    private var selectedLease: RasterLease? = null
    private var uploadedLease: RasterLease? = null

    init {
        require(exportRangeStartUs >= 0L)
        timeline.cues.forEach { cue ->
            require(cue.request.geometry == expectedGeometry) {
                "snapshot raster geometry must match configured output geometry"
            }
        }
    }

    override fun configure(videoSize: Size) {
        synchronized(stateLock) {
            check(!released) { "snapshot overlay already released" }
            require(
                videoSize.width == expectedGeometry.uprightWidthPx &&
                    videoSize.height == expectedGeometry.uprightHeightPx
            ) {
                "configured video size does not match immutable render snapshot"
            }
            configured = true
        }
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = synchronized(stateLock) {
        ensureUsable()
        if (presentationTimeUs < 0L) {
            throw VideoFrameProcessingException("negative output presentation timestamp", presentationTimeUs)
        }
        val sourceTimeUs = try {
            Math.addExact(exportRangeStartUs, presentationTimeUs)
        } catch (error: ArithmeticException) {
            throw VideoFrameProcessingException("snapshot presentation clock overflow", error, presentationTimeUs)
        }
        val located = timeline.locate(sourceTimeUs)
        val active = located.active
        if (active == null) {
            located.next?.let { coordinator.prepareWindow(it.request) }
            clearUnuploadedSelectionLocked()
            return@synchronized transparentBitmap
        }

        coordinator.prepareWindow(active.request, located.next?.request)
        if (selectedRequestId == active.request.requestId) {
            val selected = selectedLease
            if (selected != null && !selected.raster.bitmap.isRecycled) {
                return@synchronized selected.raster.bitmap
            }
        }

        clearUnuploadedSelectionLocked()
        when (val prepared = coordinator.awaitPrepared(active.request.requestId)) {
            is RasterAwaitResult.Ready -> {
                selectedRequestId = active.request.requestId
                selectedLease = prepared.lease
                prepared.lease.raster.bitmap
            }
            is RasterAwaitResult.Rejected -> throw VideoFrameProcessingException(
                "active subtitle raster unavailable: ${prepared.reason}",
                presentationTimeUs,
            )
            RasterAwaitResult.TimedOut -> throw VideoFrameProcessingException(
                "active subtitle raster exceeded bounded preparation wait",
                presentationTimeUs,
            )
        }
    }

    override fun getTextureId(presentationTimeUs: Long): Int = synchronized(stateLock) {
        ensureUsable()
        val textureId = super.getTextureId(presentationTimeUs)
        val newlyUploaded = selectedLease
        val previousUploaded = uploadedLease
        if (previousUploaded !== newlyUploaded) {
            previousUploaded?.close()
            uploadedLease = newlyUploaded
        }
        textureId
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = overlaySettings

    override fun release() {
        var thrown: VideoFrameProcessingException? = null
        try {
            super.release()
        } catch (error: VideoFrameProcessingException) {
            thrown = error
        } finally {
            synchronized(stateLock) {
                if (!released) {
                    released = true
                    val selected = selectedLease
                    val uploaded = uploadedLease
                    selectedLease = null
                    uploadedLease = null
                    selectedRequestId = null
                    selected?.close()
                    if (uploaded !== selected) uploaded?.close()
                    if (!transparentBitmap.isRecycled) transparentBitmap.recycle()
                }
            }
        }
        thrown?.let { throw it }
    }

    private fun clearUnuploadedSelectionLocked() {
        val selected = selectedLease
        if (selected != null && selected !== uploadedLease) selected.close()
        selectedLease = null
        selectedRequestId = null
    }

    private fun ensureUsable() {
        check(!released) { "snapshot overlay already released" }
        check(configured) { "snapshot overlay must be configured before frame access" }
    }
}
