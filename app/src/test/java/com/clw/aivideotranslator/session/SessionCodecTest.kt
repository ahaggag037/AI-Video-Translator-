package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        listOf("apiKey", "authorization", "credential", "token").forEach { key ->
            assertFalse(root.has(key))
        }
    }
}
