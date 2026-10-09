package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SttReopenRecoveryInstrumentedTest {
    private fun attachment() = SourceAttachment(
        sessionId = "session-1",
        contentUri = "content://synthetic.documents/video/1",
        persistedReadGrantAtCapture = true,
        fingerprint = SourceFingerprint("a".repeat(64), 123_456),
        durationUs = 42_000_000,
        selectedRange = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(42_000_000)),
        audioTrack = SourceAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun snapshot(source: SourceAttachment) = SourceSnapshot(
        sessionId = "session-1",
        sourceAttachmentId = source.attachmentId,
        transcript = "Hello world.",
        words = listOf(
            SourceSnapshotWord(0, "Hello", 0.9),
            SourceSnapshotWord(1, "world.", null),
        ),
        pcmSample = SourcePcmSampleIdentity("b".repeat(64), 96_044, 48_000, 48_000, 1, 16),
        stt = SourceSttProvenance(
            "nvidia", "parakeet-ctc-1.1b-en-us", "p0-stt-v1", "legacy-parser-v1",
            "c".repeat(64), 200,
        ),
        clock = SourceClockProvenance(500_000, ClockVerificationStatus.UNVERIFIED),
    )

    private fun withStore(block: (File, TranslationSessionStore) -> Unit) {
        val root = File(
            InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "stt-reopen-${UUID.randomUUID()}",
        )
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            block(root, store)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun bindAttachment(store: TranslationSessionStore): SourceAttachment {
        val source = attachment()
        store.bindInitialSourceAttachment("session-1", 0, source)
        return source
    }

    private fun prepared(store: TranslationSessionStore, source: SourceAttachment): SttAttemptReceipt {
        val manifest = store.readManifest("session-1")
        val receipt = SttAttemptReceipt(
            attemptId = SttAttemptIdentity.forInitialSnapshot("session-1", source.attachmentId),
            sessionId = "session-1",
            epoch = manifest.epoch,
            expectedManifestRevision = manifest.revision,
            sourceAttachmentId = source.attachmentId,
            requestProfileId = "p0-stt-v1",
            sampleSha256 = "b".repeat(64),
            phase = SttAttemptPhase.PREPARED,
        )
        return store.persistPreparedSttAttempt(receipt)
    }

    private fun readable(token: SourceProbeToken, source: SourceAttachment) = SourceReadObservation(
        token = token,
        observedContentUri = source.contentUri,
        status = SourceReadStatus.READABLE,
        fingerprint = source.fingerprint,
        persistedReadGrantNow = true,
    )

    @Test fun sentAttemptReopensAsUnknownWithoutMutationOrSubmissionPath() = withStore { root, store ->
        val source = bindAttachment(store)
        val prepared = prepared(store, source)
        store.markSttAttemptSent(prepared.copy(phase = SttAttemptPhase.SENT))
        val before = store.readManifest("session-1")

        val reopened = TranslationSessionStore(root)
        val result = SourceSessionCoordinator.reopenSession(reopened, "session-1", ::readable).getOrThrow()

        assertEquals(SourceAvailability.AVAILABLE, result.source.availability)
        assertEquals(SourceSnapshotAvailability.NOT_BOUND, result.snapshotAvailability)
        assertEquals(SttReopenDisposition.UNKNOWN_REMOTE_OUTCOME, result.sttDisposition)
        assertNull(result.snapshot)
        assertEquals(before, reopened.readManifest("session-1"))
        assertEquals(SttAttemptPhase.SENT, reopened.readSttAttemptOrNull("session-1", prepared.attemptId)!!.phase)
    }

    @Test fun receivedAttemptIsAdoptedLocallyAfterRestart() = withStore { root, store ->
        val source = bindAttachment(store)
        val prepared = prepared(store, source)
        val sent = store.markSttAttemptSent(prepared.copy(phase = SttAttemptPhase.SENT))
        val expected = snapshot(source)
        store.persistReceivedSttAttempt(sent.copy(phase = SttAttemptPhase.RECEIVED, snapshot = expected))

        val reopened = TranslationSessionStore(root)
        val result = SourceSessionCoordinator.reopenSession(reopened, "session-1", ::readable).getOrThrow()

        assertEquals(SourceAvailability.AVAILABLE, result.source.availability)
        assertEquals(SourceSnapshotAvailability.AVAILABLE, result.snapshotAvailability)
        assertEquals(SttReopenDisposition.RECOVERED_RECEIVED, result.sttDisposition)
        assertEquals(expected, result.snapshot)
        val manifest = reopened.readManifest("session-1")
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, manifest.sourceBindingState)
        assertEquals(expected.snapshotId, manifest.activeSourceSnapshotRef)
        assertEquals(SttAttemptPhase.ADOPTED, reopened.readSttAttemptOrNull("session-1", prepared.attemptId)!!.phase)
    }

    @Test fun receivedJournalAfterManifestAdoptionFinalizesLocally() = withStore { root, store ->
        val source = bindAttachment(store)
        val prepared = prepared(store, source)
        val sent = store.markSttAttemptSent(prepared.copy(phase = SttAttemptPhase.SENT))
        val expected = snapshot(source)
        val received = store.persistReceivedSttAttempt(sent.copy(phase = SttAttemptPhase.RECEIVED, snapshot = expected))
        store.bindInitialSourceSnapshot("session-1", 1, expected)

        val reopened = TranslationSessionStore(root)
        val result = SourceSessionCoordinator.reopenSession(reopened, "session-1", ::readable).getOrThrow()

        assertEquals(SourceSnapshotAvailability.AVAILABLE, result.snapshotAvailability)
        assertEquals(SttReopenDisposition.RECOVERED_RECEIVED, result.sttDisposition)
        assertEquals(expected, result.snapshot)
        assertEquals(SttAttemptPhase.ADOPTED, reopened.readSttAttemptOrNull("session-1", received.attemptId)!!.phase)
    }
}
