package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.sha256Utf8

data class SttWindow(
    val index: Int,
    val startUs: Long,
    val endUs: Long,
) {
    init {
        require(index >= 0) { "negative STT window index" }
        require(startUs >= 0L && endUs > startUs) { "invalid STT window bounds" }
    }

    val durationUs: Long get() = endUs - startUs
}

/** Provider-safe full-video decomposition using the already-qualified one-minute request size. */
internal object FullVideoSttWindowPlan {
    const val MAX_WINDOW_US: Long = 60_000_000L

    fun forDuration(durationUs: Long): List<SttWindow> {
        require(durationUs > 0L) { "source duration must be positive" }
        val windows = ArrayList<SttWindow>()
        var index = 0
        var startUs = 0L
        while (startUs < durationUs) {
            val remaining = durationUs - startUs
            val endUs = startUs + minOf(MAX_WINDOW_US, remaining)
            windows += SttWindow(index = index, startUs = startUs, endUs = endUs)
            startUs = endUs
            index = Math.addExact(index, 1)
        }
        return windows
    }

    /**
     * Profile is intentionally excluded: an app/profile upgrade must still discover SENT work for
     * this exact source window rather than minting a new identity and risking a duplicate request.
     */
    fun attemptId(sessionId: String, sourceAttachmentId: String, window: SttWindow): String {
        require(isSafeId(sessionId) && isSafeId(sourceAttachmentId)) { "invalid STT window identity input" }
        val fields = listOf(
            "stt-source-window-v1",
            sessionId,
            sourceAttachmentId,
            window.index.toString(),
            window.startUs.toString(),
            window.endUs.toString(),
        )
        val canonical = fields.joinToString("") { value ->
            "${value.toByteArray(Charsets.UTF_8).size}:$value"
        }
        return "stt-window-${sha256Utf8(canonical)}"
    }
}