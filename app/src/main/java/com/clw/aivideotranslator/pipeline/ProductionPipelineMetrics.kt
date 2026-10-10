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

    @Synchronized fun recordFirstAudioWindow(nowMs: Long) {
        if (metrics.firstAudioWindowElapsedMs == null) {
            metrics = metrics.copy(firstAudioWindowElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordFirstSttSent(nowMs: Long) {
        if (metrics.firstSttSentElapsedMs == null) {
            metrics = metrics.copy(firstSttSentElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordFirstSttReceived(nowMs: Long) {
        if (metrics.firstSttReceivedElapsedMs == null) {
            metrics = metrics.copy(firstSttReceivedElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordFirstTranslationReceived(nowMs: Long) {
        if (metrics.firstTranslationReceivedElapsedMs == null) {
            metrics = metrics.copy(firstTranslationReceivedElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordPreviewReady(nowMs: Long) {
        if (metrics.previewReadyElapsedMs == null) {
            metrics = metrics.copy(previewReadyElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordExportStarted(nowMs: Long) {
        if (metrics.exportStartedElapsedMs == null) {
            metrics = metrics.copy(exportStartedElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordExportCompleted(nowMs: Long) {
        if (metrics.exportCompletedElapsedMs == null) {
            metrics = metrics.copy(exportCompletedElapsedMs = elapsed(nowMs))
        }
    }

    @Synchronized fun recordExportValidated(nowMs: Long) {
        if (metrics.exportValidatedElapsedMs == null) {
            metrics = metrics.copy(exportValidatedElapsedMs = elapsed(nowMs))
        }
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

    private fun elapsed(nowMs: Long): Long {
        require(nowMs >= operationStartedMonotonicMs) { "metrics clock moved backwards" }
        return nowMs - operationStartedMonotonicMs
    }
}
