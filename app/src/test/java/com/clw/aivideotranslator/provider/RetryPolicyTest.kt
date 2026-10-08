package com.clw.aivideotranslator.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryPolicyTest {
    @Test fun rateLimitRespectsProviderDelayAndSubmissionBudget() {
        val rateLimited = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.NO_RESPONSE,
            policy = PolicyOutcome.RATE_LIMIT,
            httpStatus = 429,
            retryAfterMs = 12_000,
        )
        assertEquals(
            RetryDirective(RetryDecision.WAIT_AND_RETRY, 12_000),
            RetryPolicy.decide(rateLimited, RetryContext(submissionCount = 1)),
        )
        assertEquals(
            RetryDecision.REVIEW_OR_PAUSE,
            RetryPolicy.decide(rateLimited, RetryContext(submissionCount = 3)).decision,
        )
    }

    @Test fun authRefusalAndUnknownOutcomeNeverBlindlyRetry() {
        val auth = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.NO_RESPONSE,
            policy = PolicyOutcome.AUTH_FAILURE,
            httpStatus = 401,
        )
        assertEquals(RetryDecision.NEEDS_USER_ACTION, RetryPolicy.decide(auth, RetryContext(1)).decision)

        val unknown = TranslationResponseClassifier.transportFailure(mayHaveBeenSubmitted = true)
        assertEquals(RetryDecision.ASK_RETRY, RetryPolicy.decide(unknown, RetryContext(1)).decision)
    }

    @Test fun truncationAllowsAtMostOneExplicitRescueWithinTotalBudget() {
        val truncated = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.TRUNCATED,
            contentValidation = ContentValidationOutcome.STRUCTURAL_FAILURE,
            candidateText = "partial",
            httpStatus = 200,
            finishReason = "length",
        )
        assertEquals(
            RetryDecision.RETRY_WITH_REVISED_PLAN,
            RetryPolicy.decide(truncated, RetryContext(1, truncationRescueUsed = false)).decision,
        )
        assertEquals(
            RetryDecision.REVIEW_OR_PAUSE,
            RetryPolicy.decide(truncated, RetryContext(2, truncationRescueUsed = true)).decision,
        )
    }
}
