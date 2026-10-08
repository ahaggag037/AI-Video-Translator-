package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationRequestPlan

internal object NvidiaTranslationPlanContract {
    fun requireSupported(plan: TranslationRequestPlan) {
        require(TranslationPlanner.isRequestPlanSelfConsistent(plan)) { "translation request plan is not self-consistent" }
        require(plan.approvedExamples.isEmpty()) { "nvidia-text-v1 does not send few-shot examples" }
        val profile = plan.profile
        require(profile.id == "nvidia-text-v1") { "unsupported translation profile id" }
        require(profile.providerId == "nvidia") { "unsupported translation provider" }
        require(profile.model == NvidiaTranslationClient.MODEL_ID) { "translation model does not match NVIDIA request body" }
        require(profile.endpoint == NvidiaTranslationClient.ENDPOINT) { "translation endpoint does not match NVIDIA transport contract" }
        require(profile.systemContent == "en-ar") { "translation system contract does not match NVIDIA request body" }
        require(profile.sourceLanguage == "en" && profile.targetLanguage == "ar") { "unsupported language pair" }
        require(profile.maxTokens == 1024) { "max token contract does not match NVIDIA request body" }
        require(profile.temperature == 0) { "temperature contract does not match NVIDIA request body" }
        require(!profile.stream) { "streaming is not part of nvidia-text-v1" }
        require(profile.protocolVersion == "1") { "unsupported NVIDIA translation protocol" }
    }
}
