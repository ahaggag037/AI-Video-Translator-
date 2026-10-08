package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSnapshotStoreInstrumentedTest {
    private fun attachment() = SourceAttachment(
        "session-1", "content://synthetic.documents/video/1", true,
        SourceFingerprint("a".repeat(64), 123_456), 42_000_000,
        PresentationIntervalUs(PresentationTimeUs(500_000), PresentationTimeUs(12_000_000)),
        SourceAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun snapshot(source: SourceAttachment = attachment()) = SourceSnapshot(
        sessionId = "session-1", sourceAttachmentId = source.attachmentId,
        transcript = "Hello world.",
        words = listOf(SourceSnapshotWord(0, "Hello", 0.9), SourceSnapshotWord(1, "world.", null)),
        pcmSample = SourcePcmSampleIdentity("b".repeat(64), 96_044, 48_000, 48_000, 1, 16),
        stt = SourceSttProvenance("nvidia", "parakeet-ctc-1.1b-en-us", "p0-stt-v1", "legacy-parser-v1", "c".repeat(64), 200),
        clock = SourceClockProvenance(500_000, ClockVerificationStatus.UNVERIFIED),
    )

    private fun withRoot(block: (File) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "snapshot-test-${UUID.randomUUID()}")
        try { block(root) } finally { root.deleteRecursively() }
    }

    private fun rejected(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue("must fail closed", failed)
    }

    @Test fun snapshotBindingSurvivesRestartAndAdvancesSourceStateAndEpoch() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val attachment = attachment()
        store.bindInitialSourceAttachment("session-1", 0, attachment)
        val snapshot = snapshot(attachment)
        val bound = store.bindInitialSourceSnapshot("session-1", 1, snapshot)
        val restart = TranslationSessionStore(root)
        assertEquals(2L, bound.revision)
        assertEquals(2L, bound.epoch)
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, bound.sourceBindingState)
        assertEquals(snapshot.snapshotId, bound.activeSourceSnapshotRef)
        assertEquals(snapshot, restart.readActiveSourceSnapshot("session-1"))
        assertEquals(attachment, restart.readActiveSourceAttachment("session-1"))
    }

    @Test fun crashAfterImmutableSnapshotPublicationLeavesAttachmentBoundAndRetryReusesObject() = withRoot { root ->
        val crashing = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit
            override fun afterSourceSnapshotPublished(sessionId: String, snapshotId: String) {
                throw IOException("simulated snapshot crash window")
            }
        })
        crashing.createSession("session-1")
        val attachment = attachment()
        crashing.bindInitialSourceAttachment("session-1", 0, attachment)
        val snapshot = snapshot(attachment)
        rejected { crashing.bindInitialSourceSnapshot("session-1", 1, snapshot) }
        val restart = TranslationSessionStore(root)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, restart.readManifest("session-1").sourceBindingState)
        assertNull(restart.readActiveSourceSnapshot("session-1"))
        val files = File(root, "session-1/snapshots").listFiles()!!.filter { it.extension == "json" }
        assertEquals(1, files.size)
        val bytes = files.single().readBytes()
        restart.bindInitialSourceSnapshot("session-1", 1, snapshot)
        assertArrayEquals(bytes, files.single().readBytes())
        assertEquals(snapshot, restart.readActiveSourceSnapshot("session-1"))
    }

    @Test fun enospcDuringSnapshotManifestCommitRollsBackBindingAndAllowsRetry() = withRoot { root ->
        var failManifest = false
        val store = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit
            override fun afterAtomicPayloadWritten(file: File) {
                if (failManifest && file.name == "manifest.json") throw IOException("ENOSPC")
            }
        })
        store.createSession("session-1")
        val attachment = attachment()
        store.bindInitialSourceAttachment("session-1", 0, attachment)
        failManifest = true
        rejected { store.bindInitialSourceSnapshot("session-1", 1, snapshot(attachment)) }
        val restart = TranslationSessionStore(root)
        assertEquals(1L, restart.readManifest("session-1").revision)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, restart.readManifest("session-1").sourceBindingState)
        restart.bindInitialSourceSnapshot("session-1", 1, snapshot(attachment))
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, restart.readManifest("session-1").sourceBindingState)
    }

    @Test fun snapshotBindingFencesPreparedSendAndRejectsWrongAttachmentOrStaleCas() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val attachment = attachment()
        val attached = store.bindInitialSourceAttachment("session-1", 0, attachment)
        val prepared = RequestReceipt("a1", "session-1", "u1", attached.epoch, "synthetic", attached.revision, null,
            RequestReceiptPhase.PREPARED)
        store.writeReceipt(prepared)
        val bound = store.bindInitialSourceSnapshot("session-1", attached.revision, snapshot(attachment))
        rejected { store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT)) }
        rejected { store.bindInitialSourceSnapshot("session-1", attached.revision, snapshot(attachment)) }
        val wrong = snapshot(attachment).copy(sourceAttachmentId = "source-${"d".repeat(64)}")
        rejected { TranslationSessionStore(root).bindInitialSourceSnapshot("session-1", bound.revision, wrong) }
        assertEquals(bound, store.readManifest("session-1"))
    }

    @Test fun missingCorruptAndOversizedSnapshotFailClosedWithoutChangingManifest() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val attachment = attachment()
        store.bindInitialSourceAttachment("session-1", 0, attachment)
        val snapshot = snapshot(attachment)
        val bound = store.bindInitialSourceSnapshot("session-1", 1, snapshot)
        val file = File(root, "session-1/snapshots/${snapshot.snapshotId}.json")
        val original = file.readText()
        file.delete()
        rejected { store.readActiveSourceSnapshot("session-1") }
        file.writeText(JSONObject(original).put("transcript", "tampered").toString())
        rejected { store.readActiveSourceSnapshot("session-1") }
        file.writeText("x".repeat(SourceSnapshotCodec.MAX_BYTES + 1))
        rejected { store.readActiveSourceSnapshot("session-1") }
        assertEquals(bound, store.readManifest("session-1"))
    }
}
