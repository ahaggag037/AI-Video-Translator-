package com.clw.aivideotranslator.tv1.provider

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderOutcomeTest {
    @Test
    fun unknownRemoteOutcomeNeverBlindlyReposts() {
        val outcome = ProviderOutcome(
            submission = SubmissionState.OUTCOME_UNKNOWN,
            transport = TransportOutcome.TIMEOUT,
            protocol = ProtocolOutcome.NOT_EVALUATED,
            policy = PolicyOutcome.NOT_EVALUATED,
            content = ContentValidationOutcome.NOT_EVALUATED,
        )
        assertEquals(RecoveryAction.DO_NOT_REPOST, ProviderRecoveryPolicy.decide(outcome))
    }

    @Test
    fun successfulTransportCanStillRepresentProviderRefusal() {
        val outcome = ProviderOutcome(
            submission = SubmissionState.RESPONSE_RECEIVED,
            transport = TransportOutcome.SUCCESS,
            protocol = ProtocolOutcome.VALID,
            policy = PolicyOutcome.PROVIDER_REFUSAL,
            content = ContentValidationOutcome.NOT_EVALUATED,
            httpStatus = 200,
        )
        assertEquals(RecoveryAction.REQUIRE_USER_ACTION, ProviderRecoveryPolicy.decide(outcome))
    }

    @Test
    fun rateLimitAndPendingHaveDifferentRecovery() {
        val rateLimited = ProviderOutcome(
            submission = SubmissionState.RESPONSE_RECEIVED,
            transport = TransportOutcome.SUCCESS,
            protocol = ProtocolOutcome.RATE_LIMITED,
            policy = PolicyOutcome.NOT_EVALUATED,
            content = ContentValidationOutcome.NOT_EVALUATED,
            httpStatus = 429,
            retryAfterMs = 5_000L,
        )
        assertEquals(RecoveryAction.RETRY_AFTER_DELAY, ProviderRecoveryPolicy.decide(rateLimited))

        val pending = rateLimited.copy(
            protocol = ProtocolOutcome.REMOTE_PENDING,
            httpStatus = 202,
            retryAfterMs = null,
        )
        assertEquals(RecoveryAction.WAIT_OR_POLL, ProviderRecoveryPolicy.decide(pending))
    }

    @Test
    fun validKnownResponseCanBeAccepted() {
        val outcome = ProviderOutcome(
            submission = SubmissionState.RESPONSE_RECEIVED,
            transport = TransportOutcome.SUCCESS,
            protocol = ProtocolOutcome.VALID,
            policy = PolicyOutcome.ALLOWED,
            content = ContentValidationOutcome.VALID,
            httpStatus = 200,
        )
        assertEquals(RecoveryAction.ACCEPT, ProviderRecoveryPolicy.decide(outcome))
    }
}
