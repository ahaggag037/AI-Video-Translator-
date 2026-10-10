package com.clw.aivideotranslator.pipeline

enum class ProductionPipelineStage {
    SOURCE_CAPTURING,
    SOURCE_READY,
    AUDIO_DECODING,
    STT_PREPARING,
    STT_SENDING,
    STT_WAITING_PROVIDER,
    STT_RECEIVED,
    STT_ASSEMBLING,
    TRANSLATION_PREPARING,
    TRANSLATION_SENDING,
    TRANSLATION_WAITING_PROVIDER,
    TRANSLATION_RECEIVED,
    PRESENTATION_BUILDING,
    PREVIEW_READY,
    EXPORTING_VIDEO,
    EXPORT_VALIDATING,
    COMPLETE,
    BLOCKED_UNKNOWN_REMOTE_OUTCOME,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this == COMPLETE ||
            this == BLOCKED_UNKNOWN_REMOTE_OUTCOME ||
            this == FAILED ||
            this == CANCELLED

    val isProviderWait: Boolean
        get() = this == STT_WAITING_PROVIDER || this == TRANSLATION_WAITING_PROVIDER
}

data class PipelineOrdinalProgress(
    val completed: Int,
    val total: Int,
    val currentOneBased: Int? = null,
) {
    init {
        require(total > 0) { "pipeline ordinal total must be positive" }
        require(completed in 0..total) { "pipeline ordinal completed is out of range" }
        if (currentOneBased != null) {
            require(currentOneBased in 1..total) { "pipeline current ordinal is out of range" }
        }
    }

    val fraction: Double get() = completed.toDouble() / total.toDouble()
}

data class PipelineByteProgress(
    val processed: Long,
    val total: Long? = null,
    val bytesPerSecond: Double? = null,
) {
    init {
        require(processed >= 0L) { "processed bytes cannot be negative" }
        if (total != null) {
            require(total > 0L) { "total bytes must be positive" }
            require(processed <= total) { "processed bytes exceed total bytes" }
        }
        if (bytesPerSecond != null) {
            require(bytesPerSecond >= 0.0 && bytesPerSecond.isFinite()) {
                "byte throughput must be finite and non-negative"
            }
        }
    }

    val fractionOrNull: Double?
        get() = total?.let { processed.toDouble() / it.toDouble() }
}

/**
 * Product-wide progress snapshot for long-running media/provider work.
 *
 * This object intentionally contains no source locator, API key, transcript, translated text, raw
 * provider body, or other private content. It is safe to feed to UI and redacted performance
 * evidence. Callers own the monotonic clock and publish a new immutable snapshot when meaningful
 * progress occurs.
 */
data class ProductionPipelineProgress(
    val stage: ProductionPipelineStage,
    val operationElapsedMs: Long,
    val stageElapsedMs: Long,
    val lastProgressMonotonicMs: Long,
    val ordinal: PipelineOrdinalProgress? = null,
    val bytes: PipelineByteProgress? = null,
    val providerWaitElapsedMs: Long? = null,
    val reusedItems: Int = 0,
    val queueDepth: Int? = null,
    val inFlightRequests: Int? = null,
    val diagnosticCode: String? = null,
) {
    init {
        require(operationElapsedMs >= 0L) { "operation elapsed time cannot be negative" }
        require(stageElapsedMs >= 0L) { "stage elapsed time cannot be negative" }
        require(lastProgressMonotonicMs >= 0L) { "last-progress time cannot be negative" }
        require(reusedItems >= 0) { "reused item count cannot be negative" }
        if (queueDepth != null) require(queueDepth >= 0) { "queue depth cannot be negative" }
        if (inFlightRequests != null) require(inFlightRequests >= 0) { "in-flight request count cannot be negative" }
        if (providerWaitElapsedMs != null) {
            require(stage.isProviderWait) { "provider wait elapsed time requires a provider-wait stage" }
            require(providerWaitElapsedMs >= 0L) { "provider wait elapsed time cannot be negative" }
        }
        if (stage.isProviderWait) {
            require(providerWaitElapsedMs != null) { "provider-wait stage requires provider wait elapsed time" }
        }
        if (diagnosticCode != null) {
            require(diagnosticCode.isNotBlank() && diagnosticCode.length <= 96) {
                "pipeline diagnostic code is invalid"
            }
            require(diagnosticCode.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }) {
                "pipeline diagnostic code contains unsafe characters"
            }
        }
    }

    val isTerminal: Boolean get() = stage.isTerminal

    val fractionOrNull: Double?
        get() = ordinal?.fraction ?: bytes?.fractionOrNull
}
