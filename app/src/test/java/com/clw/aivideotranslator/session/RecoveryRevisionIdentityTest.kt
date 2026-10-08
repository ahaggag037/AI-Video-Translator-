package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryRevisionIdentityTest {
    private fun receipt(
        attemptId: String = "attempt-1",
        requestSignature: String = "sig-1",
    ) = RequestReceipt(
        attemptId = attemptId,
        sessionId = "session-1",
        unitId = "u1",
        epoch = 2,
        requestSignature = requestSignature,
        expectedManifestRevision = 4,
        expectedActiveEntryRevisionId = "entry-1",
        phase = RequestReceiptPhase.RECEIVED,
        outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "مرحبًا",
        ),
    )

    @Test fun sameFrozenReceiptIdentityProducesSameRecoveryIds() {
        val first = RecoveryRevisionIdentity.forReceipt(receipt())
        val second = RecoveryRevisionIdentity.forReceipt(receipt())
        assertEquals(first, second)
        assertTrue(first.machineRevisionId.matches(Regex("[A-Za-z0-9._-]+")))
        assertTrue(first.entryRevisionId.matches(Regex("[A-Za-z0-9._-]+")))
    }

    @Test fun differentAttemptOrRequestIdentityProducesDifferentRecoveryIds() {
        val baseline = RecoveryRevisionIdentity.forReceipt(receipt())
        assertNotEquals(baseline, RecoveryRevisionIdentity.forReceipt(receipt(attemptId = "attempt-2")))
        assertNotEquals(baseline, RecoveryRevisionIdentity.forReceipt(receipt(requestSignature = "sig-2")))
    }
}
