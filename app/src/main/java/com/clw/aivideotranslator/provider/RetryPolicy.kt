package com.clw.aivideotranslator.provider

enum class RetryDecision {
    ADOPT,
    WAIT_AND_RETRY,
    RETRY_WITH_REVISED_PLAN,
    ASK_RETRY,
    HOLD_PENDING,
    NEEDS_USER_ACTION,
    REVIEW_OR_PAUSE,
    STOP,
}

data class RetryDirective(
    val decision: RetryDecision,
    val delayMs: Long? = null,
)

data class RetryContext(
    val submissionCount: Int,
    val truncationRescueUsed: Boolean = false,
    val cancelled: Boolean = false,
    val stale: Boolean = false,
)

object RetryPolicy {
    const val MAX_SUBMISSIONS = 3

    fun decide(outcome: TranslationProviderOutcome, context: RetryContext): RetryDirective {
        require(context.submissionCount >= 0)
        if (context.cancelled || context.stale || outcome.transport == TransportOutcome.CANCELLED) {
            return RetryDirective(RetryDecision.STOP)
        }
        if (outcome.protocol == ProtocolOutcome.CANDIDATE) {
            return RetryDirective(RetryDecision.ADOPT)
        }
        if (outcome.protocol == ProtocolOutcome.PENDING) {
            return RetryDirective(RetryDecision.HOLD_PENDING)
        }
        if (outcome.policy in setOf(
                PolicyOutcome.AUTH_FAILURE,
                PolicyOutcome.PERMISSION_DENIED,
                PolicyOutcome.ACCOUNT_BLOCKED,
                PolicyOutcome.CONTENT_FILTER,
                PolicyOutcome.PROVIDER_REFUSAL,
                PolicyOutcome.POLICY_BLOCK,
            )
        ) {
            return RetryDirective(RetryDecision.NEEDS_USER_ACTION)
        }
        if (outcome.transport == TransportOutcome.UNKNOWN_AFTER_SUBMISSION) {
            return RetryDirective(RetryDecision.ASK_RETRY)
        }
        if (outcome.policy == PolicyOutcome.RATE_LIMIT) {
            return if (context.submissionCount < MAX_SUBMISSIONS) {
                RetryDirective(RetryDecision.WAIT_AND_RETRY, outcome.retryAfterMs ?: backoffMs(context.submissionCount))
            } else RetryDirective(RetryDecision.REVIEW_OR_PAUSE)
        }
        if (outcome.transport == TransportOutcome.DEFINITELY_NOT_SUBMITTED) {
            return if (context.submissionCount < MAX_SUBMISSIONS) {
                RetryDirective(RetryDecision.WAIT_AND_RETRY, backoffMs(context.submissionCount))
            } else RetryDirective(RetryDecision.REVIEW_OR_PAUSE)
        }
        if (outcome.protocol == ProtocolOutcome.TRUNCATED) {
            return if (!context.truncationRescueUsed && context.submissionCount < MAX_SUBMISSIONS) {
                RetryDirective(RetryDecision.RETRY_WITH_REVISED_PLAN)
            } else RetryDirective(RetryDecision.REVIEW_OR_PAUSE)
        }
        return when (outcome.protocol) {
            ProtocolOutcome.REQUEST_INVALID,
            ProtocolOutcome.REQUEST_TOO_LARGE -> RetryDirective(RetryDecision.NEEDS_USER_ACTION)
            ProtocolOutcome.MALFORMED,
            ProtocolOutcome.EMPTY,
            ProtocolOutcome.NO_RESPONSE -> RetryDirective(RetryDecision.ASK_RETRY)
            else -> RetryDirective(RetryDecision.REVIEW_OR_PAUSE)
        }
    }

    internal fun backoffMs(submissionCount: Int): Long {
        val exponent = (submissionCount - 1).coerceIn(0, 4)
        return (2_000L shl exponent).coerceAtMost(30_000L)
    }
}
