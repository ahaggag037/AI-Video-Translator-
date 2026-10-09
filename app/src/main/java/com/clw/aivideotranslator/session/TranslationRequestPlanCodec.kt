package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.ApprovedExample
import com.clw.aivideotranslator.semantic.TranslationPlanner
import com.clw.aivideotranslator.semantic.TranslationProfile
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import org.json.JSONArray
import org.json.JSONObject

internal const val TRANSLATION_REQUEST_PLAN_SCHEMA_VERSION = 1

/**
 * Private durable copy of the exact translation request plan needed for receipt recovery.
 * It deliberately contains no source timing, media locator, credential, Authorization header or
 * provider response body. The request signature is revalidated from the exact wire-affecting fields
 * on decode; acceptanceSignature remains historical acceptance provenance and is re-evaluated by the
 * legacy adoption policy before a recovered candidate can commit.
 */
internal object TranslationRequestPlanCodec {
    const val MAX_BYTES = 32 * 1024

    fun encode(plan: TranslationRequestPlan): String {
        require(isSafeId(plan.unitId)) { "invalid request-plan unit id" }
        require(TranslationPlanner.isRequestPlanSelfConsistent(plan)) { "request plan is not self-consistent" }
        require(isLowerSha256(plan.requestSignature) && isLowerSha256(plan.acceptanceSignature)) {
            "invalid request-plan signature format"
        }
        val profile = plan.profile
        val root = JSONObject()
            .put("schemaVersion", TRANSLATION_REQUEST_PLAN_SCHEMA_VERSION)
            .put("unitId", plan.unitId)
            .put("exactSourceText", plan.exactSourceText)
            .put("requestSignature", plan.requestSignature)
            .put("acceptanceSignature", plan.acceptanceSignature)
            .put("profile", JSONObject()
                .put("id", profile.id)
                .put("providerId", profile.providerId)
                .put("model", profile.model)
                .put("endpoint", profile.endpoint)
                .put("transportContractId", profile.transportContractId)
                .put("systemContent", profile.systemContent)
                .put("sourceLanguage", profile.sourceLanguage)
                .put("targetLanguage", profile.targetLanguage)
                .put("maxTokens", profile.maxTokens)
                .put("temperature", profile.temperature)
                .put("stream", profile.stream)
                .put("protocolVersion", profile.protocolVersion))
            .put("approvedExamples", JSONArray().also { array ->
                plan.approvedExamples.forEach { example ->
                    array.put(JSONObject()
                        .put("id", example.id)
                        .put("source", example.source)
                        .put("target", example.target))
                }
            })
        return root.toString().also(::requireBounded)
    }

    fun decode(json: String): TranslationRequestPlan {
        requireBounded(json)
        val root = JSONObject(json)
        requireExactFields(
            root,
            setOf(
                "schemaVersion", "unitId", "exactSourceText", "requestSignature",
                "acceptanceSignature", "profile", "approvedExamples",
            ),
            "request plan",
        )
        require(root.get("schemaVersion") is Int) { "request-plan schema has wrong type" }
        require(root.getInt("schemaVersion") == TRANSLATION_REQUEST_PLAN_SCHEMA_VERSION) {
            "unsupported request-plan schema"
        }
        val profileObject = root.getJSONObjectStrict("profile")
        requireExactFields(
            profileObject,
            setOf(
                "id", "providerId", "model", "endpoint", "transportContractId", "systemContent",
                "sourceLanguage", "targetLanguage", "maxTokens", "temperature", "stream",
                "protocolVersion",
            ),
            "translation profile",
        )
        require(profileObject.get("maxTokens") is Int) { "maxTokens has wrong type" }
        require(profileObject.get("temperature") is Int) { "temperature has wrong type" }
        require(profileObject.get("stream") is Boolean) { "stream has wrong type" }
        val profile = TranslationProfile(
            id = profileObject.getStringStrict("id"),
            providerId = profileObject.getStringStrict("providerId"),
            model = profileObject.getStringStrict("model"),
            endpoint = profileObject.getStringStrict("endpoint"),
            transportContractId = profileObject.getStringStrict("transportContractId"),
            systemContent = profileObject.getStringStrict("systemContent"),
            sourceLanguage = profileObject.getStringStrict("sourceLanguage"),
            targetLanguage = profileObject.getStringStrict("targetLanguage"),
            maxTokens = profileObject.getInt("maxTokens"),
            temperature = profileObject.getInt("temperature"),
            stream = profileObject.getBoolean("stream"),
            protocolVersion = profileObject.getStringStrict("protocolVersion"),
        )
        val examplesArray = root.getJSONArrayStrict("approvedExamples")
        val examples = buildList {
            for (index in 0 until examplesArray.length()) {
                val item = examplesArray.opt(index)
                require(item is JSONObject) { "approved example has wrong type" }
                requireExactFields(item, setOf("id", "source", "target"), "approved example")
                add(
                    ApprovedExample(
                        id = item.getStringStrict("id"),
                        source = item.getStringStrict("source"),
                        target = item.getStringStrict("target"),
                    )
                )
            }
        }
        val plan = TranslationRequestPlan(
            unitId = root.getStringStrict("unitId"),
            exactSourceText = root.getStringStrict("exactSourceText"),
            profile = profile,
            approvedExamples = examples,
            requestSignature = root.getStringStrict("requestSignature"),
            acceptanceSignature = root.getStringStrict("acceptanceSignature"),
        )
        require(isSafeId(plan.unitId)) { "invalid request-plan unit id" }
        require(isLowerSha256(plan.requestSignature) && isLowerSha256(plan.acceptanceSignature)) {
            "invalid request-plan signature format"
        }
        require(TranslationPlanner.isRequestPlanSelfConsistent(plan)) {
            "persisted request plan signature mismatch"
        }
        return plan
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "request plan exceeds size limit" }
    }

    private fun requireExactFields(value: JSONObject, expected: Set<String>, label: String) {
        require(value.keys().asSequence().toSet() == expected) { "unexpected $label fields" }
    }

    private fun JSONObject.getStringStrict(name: String): String {
        val raw = get(name)
        require(raw is String) { "$name has wrong type" }
        return raw
    }

    private fun JSONObject.getJSONObjectStrict(name: String): JSONObject {
        val raw = get(name)
        require(raw is JSONObject) { "$name has wrong type" }
        return raw
    }

    private fun JSONObject.getJSONArrayStrict(name: String): JSONArray {
        val raw = get(name)
        require(raw is JSONArray) { "$name has wrong type" }
        return raw
    }

    private fun isLowerSha256(value: String): Boolean =
        value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }
}
