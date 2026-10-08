package com.clw.aivideotranslator.tv1.provider

/** Whether a request is known not to have run, known to have returned, or is uncertain remotely. */
enum class SubmissionState {
    NOT_SUBMITTED,
    RESPONSE_RECEIVED,
    OUTCOME_UNKNOWN,
}

enum class TransportOutcome {
    NOT_EVALUATED,
    SUCCESS,
    NETWORK_FAILURE,
    TIMEOUT,
    CANCELLED,
}

enum class ProtocolOutcome {
    NOT_EVALUATED,
    VALID,
    AUTH_FAILURE,
    RATE_LIMITED,
    REQUEST_INVALID,
    REMOTE_PENDING,
    SERVER_FAILURE,
    MALFORMED_RESPONSE,
}

enum class PolicyOutcome {
    NOT_EVALUATED,
    ALLOWED,
    PROVIDER_REFUSAL,
    CONTENT_FILTERED,
}

enum class ContentValidationOutcome {
    NOT_EVALUATED,
    VALID,
    EMPTY,
    TRUNCATED,
    INVALID_TEXT,
    WRONG_LANGUAGE_RISK,
    INTEGRITY_RISK,
}

/**
 * Orthogonal provider result dimensions. A successful transport can coexist with a valid
 * protocol envelope and a policy refusal; no information is flattened into one enum.
 */
data class ProviderOutcome(
    val submission: SubmissionState,
    val transport: TransportOutcome,
    val protocol: ProtocolOutcome,
    val policy: PolicyOutcome,
    val content: ContentValidationOutcome,
    val httpStatus: Int? = null,
    val retryAfterMs: Long? = null,
) {
    init {
        require(httpStatus == null || httpStatus in 100..599) { "invalid HTTP status" }
        require(retryAfterMs == null || retryAfterMs >= 0L) { "retry delay must be non-negative" }
        if (submission == SubmissionState.RESPONSE_RECEIVED) {
            require(transport == TransportOutcome.SUCCESS) {
                "a received response requires successful transport"
            }
        }
        if (submission == SubmissionState.OUTCOME_UNKNOWN) {
            require(transport in setOf(TransportOutcome.NETWORK_FAILURE, TransportOutcome.TIMEOUT, TransportOutcome.CANCELLED)) {
                "unknown remote outcome requires interrupted transport"
            }
        }
    }
}

enum class RecoveryAction {
    ACCEPT,
    RETRY_SAFE,
    RETRY_AFTER_DELAY,
    RETRY_WITH_CONFIRMATION,
    WAIT_OR_POLL,
    REQUIRE_USER_ACTION,
    REVIEW_CONTENT,
    DO_NOT_REPOST,
}

/** Deterministic recovery guidance. It never performs the retry itself. */
object ProviderRecoveryPolicy {
    fun decide(outcome: ProviderOutcome): RecoveryAction {
        if (outcome.submission == SubmissionState.OUTCOME_UNKNOWN) {
            // Critical TV1 invariant: never blindly repost after uncertain remote submission.
            return RecoveryAction.DO_NOT_REPOST
        }

        if (outcome.submission == SubmissionState.NOT_SUBMITTED) {
            return when (outcome.transport) {
                TransportOutcome.NETWORK_FAILURE,
                TransportOutcome.TIMEOUT,
                -> RecoveryAction.RETRY_SAFE
                TransportOutcome.CANCELLED -> RecoveryAction.REQUIRE_USER_ACTION
                TransportOutcome.NOT_EVALUATED,
                TransportOutcome.SUCCESS,
                -> RecoveryAction.REQUIRE_USER_ACTION
            }
        }

        return when {
            outcome.protocol == ProtocolOutcome.AUTH_FAILURE -> RecoveryAction.REQUIRE_USER_ACTION
            outcome.protocol == ProtocolOutcome.REQUEST_INVALID -> RecoveryAction.REQUIRE_USER_ACTION
            outcome.protocol == ProtocolOutcome.RATE_LIMITED -> RecoveryAction.RETRY_AFTER_DELAY
            outcome.protocol == ProtocolOutcome.REMOTE_PENDING -> RecoveryAction.WAIT_OR_POLL
            outcome.protocol == ProtocolOutcome.SERVER_FAILURE -> RecoveryAction.RETRY_WITH_CONFIRMATION
            outcome.protocol == ProtocolOutcome.MALFORMED_RESPONSE -> RecoveryAction.RETRY_WITH_CONFIRMATION
            outcome.policy == PolicyOutcome.PROVIDER_REFUSAL -> RecoveryAction.REQUIRE_USER_ACTION
            outcome.policy == PolicyOutcome.CONTENT_FILTERED -> RecoveryAction.REQUIRE_USER_ACTION
            outcome.content in setOf(
                ContentValidationOutcome.EMPTY,
                ContentValidationOutcome.TRUNCATED,
                ContentValidationOutcome.INVALID_TEXT,
            ) -> RecoveryAction.RETRY_WITH_CONFIRMATION
            outcome.content in setOf(
                ContentValidationOutcome.WRONG_LANGUAGE_RISK,
                ContentValidationOutcome.INTEGRITY_RISK,
            ) -> RecoveryAction.REVIEW_CONTENT
            outcome.protocol == ProtocolOutcome.VALID &&
                outcome.policy == PolicyOutcome.ALLOWED &&
                outcome.content == ContentValidationOutcome.VALID -> RecoveryAction.ACCEPT
            else -> RecoveryAction.REQUIRE_USER_ACTION
        }
    }
}
