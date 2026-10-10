package com.clw.aivideotranslator

/** Source playback bounds for raster preview. Cue coverage must never shorten the video sample. */
internal object PreviewPlaybackWindow {
    fun endMs(
        sampleStartMs: Long,
        sampleEndMs: Long,
        lastCueEndMs: Long?,
    ): Long {
        require(sampleStartMs >= 0L && sampleEndMs > sampleStartMs)
        require(lastCueEndMs == null || lastCueEndMs >= 0L)
        // Export renders the whole source sample and uses transparent raster in cue gaps. Preview
        // must expose the same trailing gap instead of stopping at the last subtitle boundary.
        return sampleEndMs
    }
}
