package com.clw.aivideotranslator.pipeline

internal data class ProductionPipelineLiveProjection(
    val stage: ProductionPipelineStage,
    val fraction: Double?,
    val operationElapsedMs: Long,
    val stageElapsedMs: Long,
    val providerWaitElapsedMs: Long?,
    val sinceLastProgressMs: Long,
    val completed: Int?,
    val total: Int?,
    val currentOneBased: Int?,
    val bytesProcessed: Long?,
    val bytesTotal: Long?,
    val bytesPerSecond: Double?,
    val reusedItems: Int,
    val queueDepth: Int?,
    val inFlightRequests: Int?,
    val diagnosticCode: String?,
) {
    init {
        require(fraction == null || fraction in 0.0..1.0)
        require(operationElapsedMs >= 0L)
        require(stageElapsedMs >= 0L)
        require(providerWaitElapsedMs == null || providerWaitElapsedMs >= 0L)
        require(sinceLastProgressMs >= 0L)
    }
}

/**
 * Projects one event snapshot onto a later monotonic clock value for a live UI.
 *
 * Long local/provider operations often emit only when something meaningful changes. The UI can tick
 * once per second and derive live elapsed/wait/stall values without generating fake backend progress
 * or writing timer noise into durable state.
 */
internal object ProductionPipelineLiveProjector {
    fun project(
        snapshot: ProductionPipelineProgress,
        nowMonotonicMs: Long,
    ): ProductionPipelineLiveProjection {
        require(nowMonotonicMs >= snapshot.lastProgressMonotonicMs) {
            "live projection clock moved backwards"
        }
        val sinceLast = nowMonotonicMs - snapshot.lastProgressMonotonicMs
        val activeDelta = if (snapshot.isTerminal) 0L else sinceLast
        val liveProviderWait = snapshot.providerWaitElapsedMs?.let { base ->
            Math.addExact(base, if (snapshot.stage.isProviderWait) activeDelta else 0L)
        }
        return ProductionPipelineLiveProjection(
            stage = snapshot.stage,
            fraction = snapshot.fractionOrNull,
            operationElapsedMs = Math.addExact(snapshot.operationElapsedMs, activeDelta),
            stageElapsedMs = Math.addExact(snapshot.stageElapsedMs, activeDelta),
            providerWaitElapsedMs = liveProviderWait,
            sinceLastProgressMs = sinceLast,
            completed = snapshot.ordinal?.completed,
            total = snapshot.ordinal?.total,
            currentOneBased = snapshot.ordinal?.currentOneBased,
            bytesProcessed = snapshot.bytes?.processed,
            bytesTotal = snapshot.bytes?.total,
            bytesPerSecond = snapshot.bytes?.bytesPerSecond,
            reusedItems = snapshot.reusedItems,
            queueDepth = snapshot.queueDepth,
            inFlightRequests = snapshot.inFlightRequests,
            diagnosticCode = snapshot.diagnosticCode,
        )
    }
}
