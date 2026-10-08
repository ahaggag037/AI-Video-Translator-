package com.clw.aivideotranslator.semantic

import java.nio.ByteBuffer
import java.security.MessageDigest

data class TranslationProfile(
    val id: String = "nvidia-text-v1",
    val providerId: String = "nvidia",
    val model: String = "nvidia/riva-translate-4b-instruct-v2",
    val endpoint: String = "https://integrate.api.nvidia.com/v1/chat/completions",
    val transportContractId: String = "nvidia-chat-http-v1",
    val systemContent: String = "en-ar",
    val sourceLanguage: String = "en",
    val targetLanguage: String = "ar",
    val maxTokens: Int = 1024,
    val temperature: Int = 0,
    val stream: Boolean = false,
    val protocolVersion: String = "1",
)

data class ApprovedExample(
    val id: String,
    val source: String,
    val target: String,
)

data class TranslationRequestPlan(
    val unitId: String,
    val exactSourceText: String,
    val profile: TranslationProfile,
    val approvedExamples: List<ApprovedExample>,
    val requestSignature: String,
    val acceptanceSignature: String,
)

object TranslationPlanner {
    fun plan(
        unit: SemanticSourceUnit,
        profile: TranslationProfile = TranslationProfile(),
        approvedExamples: List<ApprovedExample> = emptyList(),
        applicableAcceptanceDependencies: List<String> = emptyList(),
    ): TranslationRequestPlan {
        require(unit.sourceText.length <= 1_000) { "source request exceeds nvidia-text-v1 cap" }
        require(approvedExamples.isEmpty() || profile.protocolVersion != "1") {
            "nvidia-text-v1 does not send few-shot examples"
        }
        val examples = approvedExamples.sortedBy { it.id }
        val requestSignature = canonicalSha256(requestFields(profile, unit.sourceText, examples))
        val acceptanceSignature = canonicalSha256(
            listOf(requestSignature) + applicableAcceptanceDependencies.sorted(),
        )
        return TranslationRequestPlan(
            unitId = unit.id,
            exactSourceText = unit.sourceText,
            profile = profile,
            approvedExamples = examples,
            requestSignature = requestSignature,
            acceptanceSignature = acceptanceSignature,
        )
    }

    fun isRequestPlanSelfConsistent(plan: TranslationRequestPlan): Boolean {
        if (plan.unitId.isBlank() || plan.exactSourceText.length > 1_000) return false
        if (plan.approvedExamples.isNotEmpty() && plan.profile.protocolVersion == "1") return false
        val examples = plan.approvedExamples.sortedBy { it.id }
        val expected = canonicalSha256(requestFields(plan.profile, plan.exactSourceText, examples))
        return expected == plan.requestSignature
    }

    private fun requestFields(
        profile: TranslationProfile,
        exactSourceText: String,
        examples: List<ApprovedExample>,
    ): List<String> = buildList {
        add(profile.id)
        add(profile.providerId)
        add(profile.model)
        add(profile.endpoint)
        add(profile.transportContractId)
        add(profile.systemContent)
        add(profile.sourceLanguage)
        add(profile.targetLanguage)
        add(profile.protocolVersion)
        add(profile.maxTokens.toString())
        add(profile.temperature.toString())
        add(profile.stream.toString())
        add(exactSourceText)
        examples.forEach { add(it.id); add(it.source); add(it.target) }
    }

    internal fun canonicalSha256(fields: List<String>): String {
        val digest = MessageDigest.getInstance("SHA-256")
        fields.forEach { field ->
            val bytes = field.toByteArray(Charsets.UTF_8)
            digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(bytes.size).array())
            digest.update(bytes)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
