package com.clw.aivideotranslator.pipeline

/**
 * Redacted production performance measurements. Values describe timings/resources only; they must
 * never contain API keys, source locators, transcripts, translated text, or provider response bodies.
 */
data class ProductionPipelineMetrics(
    val sourceBytes: Long? = null,
    val sourceCaptureElapsedMs: Long? = null,
    val firstAudioWindowElapsedMs: Long? = null,
    val firstSttSentElapsedMs: Long? = null,
    val firstSttReceivedElapsedMs: Long? = null,
    val firstTranslationReceivedElapsedMs: Long? = null,
    val previewReadyElapsedMs: Long? = null,
    val exportStartedElapsedMs: Long? = null,
    val exportCompletedElapsedMs: Long? = null,
    val exportValidatedElapsedMs: Long? = null,
    val peakTemporaryBytes: Long? = null,
    val peakJavaHeapBytes: Long? = null,
    val peakNativeHeapBytes: Long? = null,
) {
    init {
        listOfNotNull(
            sourceBytes,
            sourceCaptureElapsedMs,
            firstAudioWindowElapsedMs,
            firstSttSentElapsedMs,
            firstSttReceivedElapsedMs,
            firstTranslationReceivedElapsedMs,
            previewReadyElapsedMs,
            exportStartedElapsedMs,
            exportCompletedElapsedMs,
            exportValidatedElapsedMs,
            peakTemporaryBytes,
            peakJavaHeapBytes,
            peakNativeHeapBytes,
        ).forEach { require(it >= 0L) { "production pipeline metric cannot be negative" } }

        requireOrdered(
            sourceCaptureElapsedMs,
            firstAudioWindowElapsedMs,
            "first audio window cannot precede completed source capture in retained-source v1",
        )
        requireOrdered(firstAudioWindowElapsedMs, firstSttSentElapsedMs, "first STT send cannot precede its audio window")
        requireOrdered(firstSttSentElapsedMs, firstSttReceivedElapsedMs, "first STT response cannot precede first STT send")
        requireOrdered(exportStartedElapsedMs, exportCompletedElapsedMs, "export cannot complete before it starts")
        requireOrdered(exportCompletedElapsedMs, exportValidatedElapsedMs, "export validation cannot precede export completion")
    }

    val sourceCaptureBytesPerSecond: Double?
        get() = if (sourceBytes != null && sourceCaptureElapsedMs != null && sourceCaptureElapsedMs > 0L) {
            sourceBytes.toDouble() * 1_000.0 / sourceCaptureElapsedMs.toDouble()
        } else null

    val timeFromFirstWindowToFirstSttSentMs: Long?
        get() = delta(firstAudioWindowElapsedMs, firstSttSentElapsedMs)

    val firstSttRoundTripMs: Long?
        get() = delta(firstSttSentElapsedMs, firstSttReceivedElapsedMs)

    val exportValidationElapsedMs: Long?
        get() = delta(exportCompletedElapsedMs, exportValidatedElapsedMs)

    private fun requireOrdered(left: Long?, right: Long?, message: String) {
        if (left != null && right != null) require(right >= left) { message }
    }

    private fun delta(left: Long?, right: Long?): Long? =
        if (left != null && right != null) right - left else null
}

internal class ProductionPipelineMetricsRecorder(
    private val operationStartedMonotonicMs: Long,
) {
    init {
        require(operationStartedMonotonicMs >= 0L)
    }

    private var metrics = ProductionPipelineMetrics()

    @Synchronized fun snapshot(): ProductionPipelineMetrics = metrics

    @Synchronized fun recordSourceCapture(nowMs: Long, bytes: Long) {
        require(bytes > 0L)
        if (metrics.sourceCaptureElapsedMs == null) {
            metrics = metrics.copy(sourceBytes = bytes, sourceCaptureElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordFirstAudioWindow(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(firstAudioWindowElapsedMs = value)
    }

    @Synchronized fun recordFirstSttSent(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(firstSttSentElapsedMs = value)
    }

    @Synchronized fun recordFirstSttReceived(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(firstSttReceivedElapsedMs = value)
    }

    @Synchronized fun recordFirstTranslationReceived(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(firstTranslationReceivedElapsedMs = value)
    }

    @Synchronized fun recordPreviewReady(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(previewReadyElapsedMs = value)
    }

    @Synchronized fun recordExportStarted(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(exportStartedElapsedMs = value)
    }

    @Synchronized fun recordExportCompleted(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(exportCompletedElapsedMs = value)
    }

    @Synchronized fun recordExportValidated(nowMs: Long) = recordOnce(nowMs) { value ->
        metrics = metrics.copy(exportValidatedElapsedMs = value)
    }

    @Synchronized fun observeResourceUsage(
        temporaryBytes: Long? = null,
        javaHeapBytes: Long? = null,
        nativeHeapBytes: Long? = null,
    ) {
        fun max(old: Long?, next: Long?): Long? = when {
            next == null -> old
            old == null -> next.also { require(it >= 0L) }
            else -> maxOf(old, next.also { require(it >= 0L) })
        }
        metrics = metrics.copy(
            peakTemporaryBytes = max(metrics.peakTemporaryBytes, temporaryBytes),
            peakJavaHeapBytes = max(metrics.peakJavaHeapBytes, javaHeapBytes),
            peakNativeHeapBytes = max(metrics.peakNativeHeapBytes, nativeHeapBytes),
        )
    }

    private fun recordOnce(nowMs: Long, update: (Long) -> Unit) {
        update(elapsed(nowMs))
    }

    private fun elapsed(nowMs: Long): Long {
        require(nowMs >= operationStartedMonotonicMs) { "metrics clock moved backwards" }
        return nowMs - operationStartedMonotonicMs
    }
}
