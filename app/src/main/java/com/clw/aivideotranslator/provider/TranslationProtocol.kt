package com.clw.aivideotranslator.provider

enum class TransportOutcome {
    RESPONSE_RECEIVED,
    DEFINITELY_NOT_SUBMITTED,
    UNKNOWN_AFTER_SUBMISSION,
    CANCELLED,
}

enum class ProtocolOutcome {
    CANDIDATE,
    PENDING,
    TRUNCATED,
    MALFORMED,
    REQUEST_INVALID,
    REQUEST_TOO_LARGE,
    EMPTY,
    NO_RESPONSE,
}

enum class PolicyOutcome {
    ALLOWED,
    AUTH_FAILURE,
    PERMISSION_DENIED,
    RATE_LIMIT,
    ACCOUNT_BLOCKED,
    CONTENT_FILTER,
    PROVIDER_REFUSAL,
    POLICY_BLOCK,
}

enum class ContentValidationOutcome {
    NOT_EVALUATED,
    CANDIDATE_UNVALIDATED,
    STRUCTURAL_FAILURE,
}

data class TranslationProviderOutcome(
    val transport: TransportOutcome,
    val protocol: ProtocolOutcome,
    val policy: PolicyOutcome = PolicyOutcome.ALLOWED,
    val contentValidation: ContentValidationOutcome = ContentValidationOutcome.NOT_EVALUATED,
    val candidateText: String? = null,
    val httpStatus: Int? = null,
    val requestId: String? = null,
    val resolvedModel: String? = null,
    val finishReason: String? = null,
    val retryAfterMs: Long? = null,
    val diagnosticCode: String? = null,
) {
    init {
        require(retryAfterMs == null || retryAfterMs >= 0L)
        if (protocol == ProtocolOutcome.CANDIDATE) require(!candidateText.isNullOrBlank())
        if (candidateText != null) require(protocol == ProtocolOutcome.CANDIDATE || protocol == ProtocolOutcome.TRUNCATED)
    }
}
