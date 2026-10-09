package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionReopenAssessmentInstrumentedTest {
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
            "session-reopen-${UUID.randomUUID()}",
        )
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            block(root, store)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun bindSnapshot(store: TranslationSessionStore): Pair<SessionManifest, SourceSnapshot> {
        val source = attachment()
        store.bindInitialSourceAttachment("session-1", 0, source)
        val expected = snapshot(source)
        val manifest = store.bindInitialSourceSnapshot("session-1", 1, expected)
        return manifest to expected
    }

    private fun readable(token: SourceProbeToken, source: SourceAttachment) = SourceReadObservation(
        token = token,
        observedContentUri = source.contentUri,
        status = SourceReadStatus.READABLE,
        fingerprint = source.fingerprint,
        persistedReadGrantNow = true,
    )

    @Test fun validSnapshotBoundSessionReopensWithoutChangingClockTruth() = withStore { root, store ->
        val (before, expected) = bindSnapshot(store)
        val reopened = TranslationSessionStore(root)
        var probeCalls = 0
        val result = SourceSessionCoordinator.assessSessionReopen(reopened, "session-1") { token, source ->
            probeCalls++
            readable(token, source)
        }.getOrThrow()

        assertEquals(1, probeCalls)
        assertEquals(SourceAvailability.AVAILABLE, result.source.availability)
        assertEquals(SourceSnapshotAvailability.AVAILABLE, result.snapshotAvailability)
        assertEquals(expected, result.snapshot)
        assertEquals(ClockVerificationStatus.UNVERIFIED, result.snapshot!!.clock.verificationStatus)
        assertTrue(result.snapshot!!.words.all { it.audioInterval == null })
        assertEquals(before, reopened.readManifest("session-1"))
    }

    private fun corruptSnapshot(mutate: (File, String) -> Unit) = withStore { root, store ->
        val (before, expected) = bindSnapshot(store)
        val file = File(root, "session-1/snapshots/${expected.snapshotId}.json")
        val original = file.readText()
        mutate(file, original)
        val reopened = TranslationSessionStore(root)
        var probeCalls = 0
        val result = SourceSessionCoordinator.assessSessionReopen(reopened, "session-1") { _, _ ->
            probeCalls++
            error("corrupt snapshot must block before source/provider I/O")
        }.getOrThrow()

        assertEquals(0, probeCalls)
        assertEquals(SourceSnapshotAvailability.CORRUPT_BINDING, result.snapshotAvailability)
        assertNull(result.snapshot)
        assertEquals(before, reopened.readManifest("session-1"))
    }

    @Test fun missingSnapshotBlocksReopenLocally() = corruptSnapshot { file, _ ->
        assertTrue(file.delete())
    }

    @Test fun malformedSnapshotBlocksReopenLocally() = corruptSnapshot { file, _ ->
        file.writeText("{")
    }

    @Test fun oversizedSnapshotBlocksReopenLocally() = corruptSnapshot { file, _ ->
        file.writeText("x".repeat(SourceSnapshotCodec.MAX_BYTES + 1))
    }

    @Test fun identityMismatchedSnapshotBlocksReopenLocally() = corruptSnapshot { file, json ->
        file.writeText(JSONObject(json).put("sourceAttachmentId", "source-tampered").toString())
    }

    @Test fun snapshotMutationDuringSourceProbeFailsClosed() = withStore { root, store ->
        val (before, expected) = bindSnapshot(store)
        val snapshotFile = File(root, "session-1/snapshots/${expected.snapshotId}.json")
        val reopened = TranslationSessionStore(root)
        var probeCalls = 0
        val result = SourceSessionCoordinator.assessSessionReopen(reopened, "session-1") { token, source ->
            probeCalls++
            snapshotFile.writeText("{")
            readable(token, source)
        }.getOrThrow()

        assertEquals(1, probeCalls)
        assertEquals(SourceAvailability.AVAILABLE, result.source.availability)
        assertEquals(SourceSnapshotAvailability.CORRUPT_BINDING, result.snapshotAvailability)
        assertNull(result.snapshot)
        assertEquals(before, reopened.readManifest("session-1"))
    }
}
