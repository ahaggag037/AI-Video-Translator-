package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.PolicyOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SessionCodecTest {
    @Test fun manifestRoundTripPreservesRevisionEpochAndSortedRefs() {
        val manifest = SessionManifest(
            sessionId = "session-1",
            revision = 7,
            epoch = 3,
            activeEntryRefs = linkedMapOf("u2" to "r2", "u1" to "r1"),
        )
        val json = SessionCodec.encodeManifest(manifest)
        assertEquals(manifest, SessionCodec.decodeManifest(json))
        assertTrue(json.indexOf("u1") < json.indexOf("u2"))
    }

    @Test fun entryRoundTripPreservesManualAndMachineHistory() {
        val machine = MachineTranslationRevision("m1", "ترجمة آلية", "sig-1")
        val manual = ManualTranslationRevision("manual-1", "تصحيح يدوي", "source-hash", machine.id)
        val entry = StoredTranslationEntry(
            revisionId = "entry-1",
            record = TranslationRecord(
                unitId = "u1",
                machineRevisions = listOf(machine),
                activeMachineRevisionId = machine.id,
                manualRevision = manual,
                reviewState = TranslationReviewState.APPROVED,
            ),
        )
        val restored = SessionCodec.decodeEntry(SessionCodec.encodeEntry(entry))
        assertEquals(entry, restored)
        assertEquals("تصحيح يدوي", restored.record.effectiveText())
        assertEquals("ترجمة آلية", restored.record.machineRevisions.single().text)
    }

    @Test fun receiptRoundTripPreservesOutcomeAndExactAdoptionFenceWithoutCredentials() {
        val outcome = TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.CANDIDATE,
            policy = PolicyOutcome.ALLOWED,
            contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
            candidateText = "ترجمة محفوظة",
            httpStatus = 200,
            requestId = "provider-123",
            resolvedModel = "nvidia/model",
            finishReason = "stop",
        )
        val receipt = RequestReceipt(
            attemptId = "attempt-1",
            sessionId = "session-1",
            unitId = "u1",
            epoch = 4,
            requestSignature = "sig-1",
            expectedManifestRevision = 9,
            expectedActiveEntryRevisionId = "entry-3",
            phase = RequestReceiptPhase.RECEIVED,
            outcome = outcome,
        )
        val encoded = SessionCodec.encodeReceipt(receipt)
        val restored = SessionCodec.decodeReceipt(encoded)
        assertEquals(receipt, restored)
        assertEquals(9L, restored.adoptionFence().expectedManifestRevision)
        assertEquals("entry-3", restored.adoptionFence().expectedActiveEntryRevisionId)
        listOf("apiKey", "Authorization", "Bearer", "credential").forEach { secretField ->
            assertFalse(encoded.contains(secretField, ignoreCase = true))
        }
    }

    @Test fun receiptRecoveryStatesDoNotBlindlyResubmitSentAttempts() {
        fun receipt(phase: RequestReceiptPhase, outcome: TranslationProviderOutcome? = null) = RequestReceipt(
            attemptId = "attempt-1",
            sessionId = "session-1",
            unitId = "u1",
            epoch = 1,
            requestSignature = "sig",
            expectedManifestRevision = 2,
            expectedActiveEntryRevisionId = null,
            phase = phase,
            outcome = outcome,
        )
        assertEquals(ReceiptRecoveryDisposition.SAFE_TO_PLAN_NEW_ATTEMPT, receipt(RequestReceiptPhase.PREPARED).recoveryDisposition())
        assertEquals(ReceiptRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME, receipt(RequestReceiptPhase.SENT).recoveryDisposition())
        assertEquals(
            ReceiptRecoveryDisposition.RECEIVED_AVAILABLE,
            receipt(
                RequestReceiptPhase.RECEIVED,
                TranslationProviderOutcome(TransportOutcome.RESPONSE_RECEIVED, ProtocolOutcome.EMPTY),
            ).recoveryDisposition(),
        )
    }

    @Test fun unknownSchemaFailsClosed() {
        val json = JSONObject()
            .put("schemaVersion", 99)
            .put("sessionId", "session-1")
            .put("revision", 0)
            .put("epoch", 0)
            .put("activeEntryRefs", JSONObject())
            .toString()
        try {
            SessionCodec.decodeManifest(json)
            fail("Expected unsupported schema failure")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun serializedEntrySchemaContainsNoCredentialFields() {
        val entry = StoredTranslationEntry(
            revisionId = "entry-1",
            record = TranslationRecord(
                unitId = "u1",
                machineRevisions = listOf(MachineTranslationRevision("m1", "نص", "sig-1")),
                activeMachineRevisionId = "m1",
                manualRevision = null,
                reviewState = TranslationReviewState.MACHINE_CANDIDATE,
            ),
        )
        val root = JSONObject(SessionCodec.encodeEntry(entry))
        listOf("apiKey", "authorization", "credential", "token").forEach { key -> assertFalse(root.has(key)) }
    }
}
