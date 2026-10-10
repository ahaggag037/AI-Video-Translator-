package com.clw.aivideotranslator.semantic

import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome

enum class X002SemanticRequestMode {
    UNIT_ONLY,
    BOUNDED_SOURCE_CONTEXT,
}

data class X002SourceContextUnit(
    val unitId: String,
    val sourceText: String,
    val sourceTextHash: String = sha256Utf8(sourceText),
) {
    init {
        require(unitId.isNotBlank())
        require(sourceText.isNotBlank())
        require(sourceTextHash == sha256Utf8(sourceText)) { "source context hash mismatch" }
    }
}

data class X002SemanticRequest(
    val targetUnitId: String,
    val targetSourceText: String,
    val targetSourceTextHash: String,
    val mode: X002SemanticRequestMode,
    val beforeContext: List<X002SourceContextUnit>,
    val afterContext: List<X002SourceContextUnit>,
    val profile: TranslationProfile,
    val providerUserContent: String,
    val providerRequestSignature: String,
    val bindingSignature: String,
) {
    init {
        require(targetUnitId.isNotBlank())
        require(targetSourceText.isNotBlank())
        require(targetSourceTextHash == sha256Utf8(targetSourceText)) { "target source hash mismatch" }
        require(providerUserContent.isNotBlank())
        require(providerRequestSignature.isNotBlank())
        require(bindingSignature.isNotBlank())
    }
}

object X002SemanticRequestPlanner {
    const val MAX_CONTEXT_UNITS_PER_SIDE = 1
    const val MAX_CONTEXT_SCALARS_TOTAL = 600
    const val MAX_PROVIDER_USER_CHARS = 1_000

    val SHADOW_PROFILE = TranslationProfile(
        id = "x002-semantic-shadow-v1",
        protocolVersion = "x002-shadow-1",
    )

    fun plan(
        target: X002SourceContextUnit,
        beforeContext: List<X002SourceContextUnit> = emptyList(),
        afterContext: List<X002SourceContextUnit> = emptyList(),
        mode: X002SemanticRequestMode = X002SemanticRequestMode.UNIT_ONLY,
        profile: TranslationProfile = SHADOW_PROFILE,
    ): X002SemanticRequest {
        require(target.sourceText.length <= 1_000) { "source request exceeds semantic shadow cap" }
        require(beforeContext.none { it.unitId == target.unitId } && afterContext.none { it.unitId == target.unitId }) {
            "target unit cannot also be source context"
        }
        require((beforeContext + afterContext).map { it.unitId }.distinct().size == beforeContext.size + afterContext.size) {
            "source context contains duplicate unit ids"
        }
        when (mode) {
            X002SemanticRequestMode.UNIT_ONLY -> require(beforeContext.isEmpty() && afterContext.isEmpty()) {
                "unit-only semantic request cannot contain hidden context"
            }
            X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT -> {
                require(beforeContext.size <= MAX_CONTEXT_UNITS_PER_SIDE && afterContext.size <= MAX_CONTEXT_UNITS_PER_SIDE) {
                    "source context exceeds unit boundary"
                }
                require(beforeContext.isNotEmpty() || afterContext.isNotEmpty()) {
                    "bounded-context request requires explicit source context"
                }
                val contextScalars = (beforeContext + afterContext).sumOf {
                    it.sourceText.codePointCount(0, it.sourceText.length)
                }
                require(contextScalars <= MAX_CONTEXT_SCALARS_TOTAL) { "source context exceeds scalar cap" }
            }
        }

        val userContent = when (mode) {
            X002SemanticRequestMode.UNIT_ONLY -> target.sourceText
            X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT -> renderBoundedContext(target, beforeContext, afterContext)
        }
        require(userContent.length <= MAX_PROVIDER_USER_CHARS) { "semantic shadow provider content exceeds request cap" }
        val providerSignature = TranslationPlanner.requestSignatureFor(profile, userContent)
        val bindingSignature = TranslationPlanner.canonicalSha256(buildList {
            add("x002-binding-v1")
            add(target.unitId)
            add(target.sourceTextHash)
            add(providerSignature)
            beforeContext.forEach { add("before"); add(it.unitId); add(it.sourceTextHash) }
            afterContext.forEach { add("after"); add(it.unitId); add(it.sourceTextHash) }
        })
        return X002SemanticRequest(
            target.unitId, target.sourceText, target.sourceTextHash, mode,
            beforeContext.toList(), afterContext.toList(), profile,
            userContent, providerSignature, bindingSignature,
        )
    }

    private fun renderBoundedContext(
        target: X002SourceContextUnit,
        beforeContext: List<X002SourceContextUnit>,
        afterContext: List<X002SourceContextUnit>,
    ): String = buildString {
        append("Translate TARGET only into Arabic. Use SOURCE_CONTEXT only to resolve meaning. Return only the TARGET translation.\n")
        beforeContext.forEachIndexed { index, context -> appendFramed("SOURCE_CONTEXT_BEFORE_${index + 1}", context.sourceText) }
        appendFramed("TARGET", target.sourceText)
        afterContext.forEachIndexed { index, context -> appendFramed("SOURCE_CONTEXT_AFTER_${index + 1}", context.sourceText) }
    }

    private fun StringBuilder.appendFramed(label: String, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        append(label).append("_UTF8_BYTES=").append(bytes.size).append('\n')
        append(value).append('\n')
    }
}

data class X002SemanticResultEnvelope(
    val unitId: String,
    val bindingSignature: String,
    val providerOutcome: TranslationProviderOutcome,
)

enum class X002SemanticResultState { ACCEPTABLE, REVIEW_REQUIRED, REJECTED, PENDING, NO_CANDIDATE, STALE_RESULT }

data class X002SemanticResult(
    val unitId: String,
    val state: X002SemanticResultState,
    val canonicalTarget: String? = null,
    val validation: TranslationValidationResult? = null,
)

object X002SemanticResponseValidator {
    fun validate(request: X002SemanticRequest, envelope: X002SemanticResultEnvelope): X002SemanticResult {
        if (envelope.unitId != request.targetUnitId || envelope.bindingSignature != request.bindingSignature) {
            return X002SemanticResult(request.targetUnitId, X002SemanticResultState.STALE_RESULT)
        }
        val outcome = envelope.providerOutcome
        if (outcome.transport != TransportOutcome.RESPONSE_RECEIVED) {
            return X002SemanticResult(request.targetUnitId, X002SemanticResultState.NO_CANDIDATE)
        }
        return when (outcome.protocol) {
            ProtocolOutcome.CANDIDATE -> {
                val validation = TranslationValidator.validate(request.targetSourceText, requireNotNull(outcome.candidateText))
                val state = when (validation.state) {
                    TranslationValidationState.PASS, TranslationValidationState.PASS_WITH_WARNING -> X002SemanticResultState.ACCEPTABLE
                    TranslationValidationState.REVIEW_REQUIRED -> X002SemanticResultState.REVIEW_REQUIRED
                    TranslationValidationState.NON_RETRYABLE_FAILURE -> X002SemanticResultState.REJECTED
                }
                X002SemanticResult(request.targetUnitId, state, validation.canonicalTarget, validation)
            }
            ProtocolOutcome.PENDING -> X002SemanticResult(request.targetUnitId, X002SemanticResultState.PENDING)
            else -> X002SemanticResult(request.targetUnitId, X002SemanticResultState.NO_CANDIDATE)
        }
    }
}

data class X002SemanticBatchMapping(
    val orderedResults: List<X002SemanticResult>,
    val errors: Set<String>,
) {
    val complete: Boolean get() = errors.isEmpty() && orderedResults.none { it.state == X002SemanticResultState.STALE_RESULT }
}

object X002SemanticBatchMapper {
    fun map(requests: List<X002SemanticRequest>, envelopes: List<X002SemanticResultEnvelope>): X002SemanticBatchMapping {
        val errors = linkedSetOf<String>()
        val duplicateRequestIds = requests.groupingBy { it.targetUnitId }.eachCount().filterValues { it > 1 }.keys
        duplicateRequestIds.sorted().forEach { errors += "DUPLICATE_REQUEST_UNIT:$it" }
        val groupedResults = envelopes.groupBy { it.unitId }
        groupedResults.filterValues { it.size > 1 }.keys.sorted().forEach { errors += "DUPLICATE_RESULT_UNIT:$it" }
        val requestIds = requests.map { it.targetUnitId }.toSet()
        val resultIds = envelopes.map { it.unitId }.toSet()
        (requestIds - resultIds).sorted().forEach { errors += "MISSING_RESULT:$it" }
        (resultIds - requestIds).sorted().forEach { errors += "EXTRA_RESULT:$it" }
        val ordered = if (duplicateRequestIds.isEmpty()) {
            requests.mapNotNull { request ->
                val candidates = groupedResults[request.targetUnitId].orEmpty()
                if (candidates.size == 1) X002SemanticResponseValidator.validate(request, candidates.single()) else null
            }
        } else emptyList()
        return X002SemanticBatchMapping(ordered, errors)
    }
}
