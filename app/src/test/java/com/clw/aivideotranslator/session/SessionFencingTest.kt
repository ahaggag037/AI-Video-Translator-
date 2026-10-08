package com.clw.aivideotranslator.session

import org.junit.Assert.assertEquals
import org.junit.Test

class SessionFencingTest {
    private val manifest = SessionManifest(
        sessionId = "session-1",
        revision = 7,
        epoch = 3,
        activeEntryRefs = mapOf("u1" to "entry-1"),
    )
    private val fence = RequestAdoptionFence(
        sessionId = "session-1",
        epoch = 3,
        requestSignature = "sig-1",
        expectedManifestRevision = 7,
        unitId = "u1",
        expectedActiveEntryRevisionId = "entry-1",
    )

    @Test fun matchingFenceCanAdopt() {
        assertEquals(AdoptionFenceResult.CURRENT, SessionFencing.check(fence, manifest, "sig-1"))
    }

    @Test fun cancelEpochMakesLateResponseStale() {
        assertEquals(
            AdoptionFenceResult.STALE_EPOCH,
            SessionFencing.check(fence, manifest.copy(revision = 8, epoch = 4), "sig-1"),
        )
    }

    @Test fun sameSourceUriOrUnitCannotBypassManifestRevisionFence() {
        assertEquals(
            AdoptionFenceResult.STALE_MANIFEST_REVISION,
            SessionFencing.check(fence, manifest.copy(revision = 8), "sig-1"),
        )
    }

    @Test fun changedEntryOrRequestSignatureCannotAdopt() {
        assertEquals(
            AdoptionFenceResult.STALE_ENTRY_REVISION,
            SessionFencing.check(fence, manifest.copy(activeEntryRefs = mapOf("u1" to "entry-2")), "sig-1"),
        )
        assertEquals(
            AdoptionFenceResult.SIGNATURE_MISMATCH,
            SessionFencing.check(fence, manifest, "sig-2"),
        )
    }
}
