package com.clw.aivideotranslator.pipeline

import android.os.SystemClock
import com.clw.aivideotranslator.NvidiaTranslationClient
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.session.DurableLegacyTranslationOperation
import com.clw.aivideotranslator.session.DurableTranslationBatchResult
import com.clw.aivideotranslator.session.LegacyParityTranslationPlanner
import com.clw.aivideotranslator.session.LegacyParityTranslationUnit
import com.clw.aivideotranslator.session.TranslationRequestPlanStore
import com.clw.aivideotranslator.session.TranslationSessionStore

internal data class ProductionTranslationExecution(
    val units: List<LegacyParityTranslationUnit>,
    val batch: DurableTranslationBatchResult,
)

/**
 * Production translation runner with no arbitrary inter-request sleep.
 *
 * Provider calls remain sequential because current X005 adoption fences include the global manifest
 * revision. Parallel remote sends would make otherwise-independent receipts stale after the first
 * adopted commit. Removing the fixed delay is safe: PREPARED/SENT/RECEIVED/recovery semantics stay
 * inside [DurableLegacyTranslationOperation], while the actual network round-trip naturally paces
 * sequential calls. True concurrency requires a separately-proven dependency-scoped fence.
 */
internal object ProductionTranslationRunner {
    suspend fun run(
        store: TranslationSessionStore,
        planStore: TranslationRequestPlanStore,
        sessionId: String,
        apiKey: String,
        liveStt: NvidiaSttResult,
        onProgress: (ProductionPipelineProgress) -> Unit = {},
    ): Result<ProductionTranslationExecution> = runCatching {
        require(apiKey.trim().isNotEmpty()) { "translation API key is blank" }
        val operationStartedMs = SystemClock.elapsedRealtime()
        var stageStartedMs = operationStartedMs
        var lastProgressMs = operationStartedMs
        val units = LegacyParityTranslationPlanner.plan(liveStt)
        require(units.isNotEmpty()) { "translation planner produced no units" }
        var providerSubmissions = 0
        var providerResponses = 0

        fun emit(
            stage: ProductionPipelineStage,
            currentOneBased: Int? = null,
            providerWaitStartedMs: Long? = null,
            diagnosticCode: String? = null,
        ) {
            val now = SystemClock.elapsedRealtime()
            if (stage != ProductionPipelineStage.TRANSLATION_WAITING_PROVIDER) {
                stageStartedMs = now
            }
            lastProgressMs = now
            onProgress(
                ProductionPipelineProgress(
                    stage = stage,
                    operationElapsedMs = now - operationStartedMs,
                    stageElapsedMs = now - stageStartedMs,
                    lastProgressMonotonicMs = lastProgressMs,
                    ordinal = PipelineOrdinalProgress(
                        completed = providerResponses.coerceAtMost(units.size),
                        total = units.size,
                        currentOneBased = currentOneBased,
                    ),
                    providerWaitElapsedMs = providerWaitStartedMs?.let { now - it },
                    inFlightRequests = if (stage.isProviderWait || stage == ProductionPipelineStage.TRANSLATION_SENDING) 1 else 0,
                    diagnosticCode = diagnosticCode,
                ),
            )
        }

        emit(ProductionPipelineStage.TRANSLATION_PREPARING)
        val batch = DurableLegacyTranslationOperation(store, planStore)
            .execute(sessionId, units) { plan ->
                val current = providerSubmissions + 1
                providerSubmissions += 1
                emit(
                    stage = ProductionPipelineStage.TRANSLATION_SENDING,
                    currentOneBased = current,
                    diagnosticCode = "TRANSLATION_SEND",
                )
                val waitStarted = SystemClock.elapsedRealtime()
                emit(
                    stage = ProductionPipelineStage.TRANSLATION_WAITING_PROVIDER,
                    currentOneBased = current,
                    providerWaitStartedMs = waitStarted,
                    diagnosticCode = "TRANSLATION_PROVIDER_WAIT",
                )
                val outcome = NvidiaTranslationClient.translateDetailed(apiKey, plan)
                providerResponses += 1
                emit(
                    stage = ProductionPipelineStage.TRANSLATION_RECEIVED,
                    currentOneBased = current,
                    diagnosticCode = "TRANSLATION_RESPONSE_RECEIVED",
                )
                outcome
            }
            .getOrThrow()

        // Reused durable entries can make completed units exceed provider response count. Publish the
        // durable batch truth at the end instead of pretending every unit required a provider call.
        val now = SystemClock.elapsedRealtime()
        onProgress(
            ProductionPipelineProgress(
                stage = if (batch.completed) ProductionPipelineStage.PRESENTATION_BUILDING else ProductionPipelineStage.FAILED,
                operationElapsedMs = now - operationStartedMs,
                stageElapsedMs = 0L,
                lastProgressMonotonicMs = now,
                ordinal = PipelineOrdinalProgress(
                    completed = batch.units.count {
                        it.disposition == com.clw.aivideotranslator.session.DurableTranslationUnitDisposition.ADOPTED ||
                            it.disposition == com.clw.aivideotranslator.session.DurableTranslationUnitDisposition.REUSED_ENTRY
                    },
                    total = units.size,
                ),
                reusedItems = batch.units.count {
                    it.disposition == com.clw.aivideotranslator.session.DurableTranslationUnitDisposition.REUSED_ENTRY
                },
                inFlightRequests = 0,
                diagnosticCode = if (batch.completed) "TRANSLATION_BATCH_COMPLETE" else "TRANSLATION_BATCH_BLOCKED",
            ),
        )
        ProductionTranslationExecution(units, batch)
    }
}
