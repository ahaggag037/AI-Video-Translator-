package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import com.clw.aivideotranslator.session.DurableTranslationAttemptExecutor
import com.clw.aivideotranslator.session.RequestReceipt
import com.clw.aivideotranslator.session.TranslationSessionStore

object DurableNvidiaTranslationAttempt {
    suspend fun execute(
        apiKey: String,
        store: TranslationSessionStore,
        prepared: RequestReceipt,
        requestPlan: TranslationRequestPlan,
    ): RequestReceipt {
        require(apiKey.trim().isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        NvidiaTranslationPlanContract.requireSupported(requestPlan)
        return DurableTranslationAttemptExecutor(store).execute(prepared, requestPlan) { plan ->
            NvidiaTranslationClient.translateDetailed(apiKey, plan)
        }
    }
}
