package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaTranslationClient
import kotlinx.coroutines.delay

/**
 * Round-2 translation-quality path.
 *
 * The durable request/receipt machinery remains unchanged; only source segmentation is switched to
 * [FieldTestSemanticTranslationPlanner]. This keeps the frozen production planner available for
 * canonical recovery/regression while letting the field-test APK evaluate better semantic context.
 */
internal object FieldTestRound2TranslationOperation {
    suspend fun translate(
        store: TranslationSessionStore,
        planStore: TranslationRequestPlanStore,
        sessionId: String,
        apiKey: String,
        liveStt: NvidiaSttResult,
    ): Result<DurableTranslationExecution> = runCatching {
        require(apiKey.trim().isNotEmpty()) { "translation API key is blank" }
        val units = FieldTestSemanticTranslationPlanner.plan(liveStt)
        var providerSubmissions = 0
        val batch = DurableLegacyTranslationOperation(store, planStore)
            .execute(sessionId, units) { plan ->
                if (providerSubmissions > 0) delay(1_500L)
                providerSubmissions += 1
                NvidiaTranslationClient.translateDetailed(apiKey, plan)
            }
            .getOrThrow()
        DurableTranslationExecution(units, batch)
    }
}
