package com.clw.aivideotranslator.session

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class SourceBindingTokenTest {
    @Test fun everySourceFenceComponentParticipatesInIdentity() {
        val manifest = SessionManifest(
            sessionId = "session-1", revision = 4, epoch = 2, activeEntryRefs = emptyMap(),
            sourceBindingState = SourceBindingState.ATTACHMENT_BOUND,
            activeSourceAttachmentRef = "source-a",
        )
        val token = SourceBindingToken.from(manifest)
        assertEquals(token, SourceBindingToken.from(manifest.copy()))
        listOf(
            manifest.copy(sessionId = "session-2"),
            manifest.copy(revision = 5), // Conservative: even revision-only drift rejects adoption.
            manifest.copy(epoch = 3),
            manifest.copy(activeSourceAttachmentRef = "source-b"),
            manifest.copy(sourceBindingState = SourceBindingState.SNAPSHOT_BOUND,
                activeSourceSnapshotRef = "snapshot-a"),
        ).forEach { assertNotEquals(token, SourceBindingToken.from(it)) }
        val bound = manifest.copy(sourceBindingState = SourceBindingState.SNAPSHOT_BOUND,
            activeSourceSnapshotRef = "snapshot-a")
        assertNotEquals(SourceBindingToken.from(bound),
            SourceBindingToken.from(bound.copy(activeSourceSnapshotRef = "snapshot-b")))
    }
}
