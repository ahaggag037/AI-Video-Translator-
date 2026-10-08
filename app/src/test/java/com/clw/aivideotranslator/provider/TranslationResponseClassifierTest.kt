package com.clw.aivideotranslator.provider

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TranslationResponseClassifierTest {
    @Test fun acceptsBaselineCompatibleEnvelopeWithoutFinishReasonOrRole() {
        val outcome = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"message":{"content":"مرحبًا بالعالم."}}]}""",
        )
        assertEquals(ProtocolOutcome.CANDIDATE, outcome.protocol)
        assertEquals("مرحبًا بالعالم.", outcome.candidateText)
        assertNull(outcome.finishReason)
    }

    @Test fun acceptsEmptyToolCallsAndTextPartsButRejectsUnknownParts() {
        val accepted = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","tool_calls":[],"content":[{"type":"text","text":"مرحبًا "},{"type":"output_text","text":"بالعالم."}]}}]}""",
        )
        assertEquals(ProtocolOutcome.CANDIDATE, accepted.protocol)
        assertEquals("مرحبًا بالعالم.", accepted.candidateText)

        val rejected = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"message":{"content":[{"type":"image","url":"x"}]}}]}""",
        )
        assertEquals(ProtocolOutcome.MALFORMED, rejected.protocol)
        assertEquals("UNSUPPORTED_CONTENT_PART", rejected.diagnosticCode)
    }

    @Test fun refusalFilterTruncationAndPendingStayDistinct() {
        val refusal = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"message":{"role":"assistant","refusal":"blocked","content":""}}]}""",
        )
        assertEquals(PolicyOutcome.PROVIDER_REFUSAL, refusal.policy)

        val filtered = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"finish_reason":"content_filter","message":{"content":""}}]}""",
        )
        assertEquals(PolicyOutcome.CONTENT_FILTER, filtered.policy)

        val truncated = TranslationResponseClassifier.classify(
            200,
            """{"choices":[{"finish_reason":"length","message":{"content":"مرحبًا"}}]}""",
        )
        assertEquals(ProtocolOutcome.TRUNCATED, truncated.protocol)
        assertEquals("مرحبًا", truncated.candidateText)

        val pending = TranslationResponseClassifier.classify(202, """{"requestId":"req-1"}""")
        assertEquals(ProtocolOutcome.PENDING, pending.protocol)
        assertEquals("req-1", pending.requestId)
    }

    @Test fun uncertainServerOutcomeIsNotEquivalentToPreSubmissionFailure() {
        val server = TranslationResponseClassifier.classify(503, "{}")
        assertEquals(TransportOutcome.UNKNOWN_AFTER_SUBMISSION, server.transport)
        assertEquals(RetryDecision.ASK_RETRY, RetryPolicy.decide(server, RetryContext(1)).decision)

        val beforeSubmit = TranslationResponseClassifier.transportFailure(mayHaveBeenSubmitted = false)
        val retry = RetryPolicy.decide(beforeSubmit, RetryContext(1))
        assertEquals(RetryDecision.WAIT_AND_RETRY, retry.decision)
        assertEquals(2_000L, retry.delayMs)
    }
}
