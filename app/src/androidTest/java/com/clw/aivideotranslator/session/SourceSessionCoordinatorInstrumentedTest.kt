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
class SourceSessionCoordinatorInstrumentedTest {
    private fun attachment() = SourceAttachment(
        "session-1", "content://synthetic.documents/video/1", true,
        SourceFingerprint("a".repeat(64), 123_456), 42_000_000,
        PresentationIntervalUs(PresentationTimeUs(500_000), PresentationTimeUs(12_000_000)),
        SourceAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun snapshot(source: SourceAttachment) = SourceSnapshot(
        sessionId = "session-1", sourceAttachmentId = source.attachmentId,
        transcript = "Hello world.",
        words = listOf(SourceSnapshotWord(0, "Hello", 0.9), SourceSnapshotWord(1, "world.", null)),
        pcmSample = SourcePcmSampleIdentity("b".repeat(64), 96_044, 48_000, 48_000, 1, 16),
        stt = SourceSttProvenance("nvidia", "parakeet-ctc-1.1b-en-us", "p0-stt-v1", "legacy-parser-v1", "c".repeat(64), 200),
        clock = SourceClockProvenance(500_000, ClockVerificationStatus.UNVERIFIED),
    )

    private fun withStore(block: (File, TranslationSessionStore) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,
            "source-coordinator-${UUID.randomUUID()}")
        try {
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            block(root, store)
        } finally { root.deleteRecursively() }
    }

    @Test fun unchangedLeaseBindsAndSurvivesReopen() = withStore { root, store ->
        val source = attachment()
        SourceSessionCoordinator.captureAndBindInitialSource(store, "session-1") { source }.getOrThrow()
        val expected = snapshot(source)
        val bound = SourceSessionCoordinator.snapshotAndBindInitialSource(store, "session-1") {
            assertEquals(source, it)
            expected
        }.getOrThrow()
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, bound.sourceBindingState)
        assertEquals(2L, bound.epoch)
        assertEquals(expected, TranslationSessionStore(root).readActiveSourceSnapshot("session-1"))
    }

    @Test fun captureCannotCrossEpochAndIsNeverAutomaticallyRetried() = withStore { root, store ->
        var calls = 0
        val result = SourceSessionCoordinator.captureAndBindInitialSource(store, "session-1") {
            calls++
            store.bumpEpoch("session-1", 0)
            attachment()
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(1, calls)
        assertEquals(1L, store.readManifest("session-1").epoch)
        assertEquals(SourceBindingState.UNBOUND, store.readManifest("session-1").sourceBindingState)
        assertFalse(File(root, "session-1/sources").exists())
    }

    @Test fun snapshotCannotCrossEpochOrPublishAnyStaleObject() = withStore { root, store ->
        store.bindInitialSourceAttachment("session-1", 0, attachment())
        var calls = 0
        val result = SourceSessionCoordinator.snapshotAndBindInitialSource(store, "session-1") {
            calls++
            store.bumpEpoch("session-1", 1)
            snapshot(it)
        }
        assertTrue(result.exceptionOrNull() is IllegalStateException)
        assertEquals(1, calls)
        val current = store.readManifest("session-1")
        assertEquals(2L, current.epoch)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, current.sourceBindingState)
        assertNull(current.activeSourceSnapshotRef)
        assertFalse(File(root, "session-1/snapshots").exists())
    }

    @Test fun competingSourceBindingWinsWithoutLoserPublication() = withStore { root, store ->
        val winner = attachment().copy(contentUri = "content://synthetic.documents/video/2")
        val result = SourceSessionCoordinator.captureAndBindInitialSource(store, "session-1") {
            store.bindInitialSourceAttachment("session-1", 0, winner)
            attachment()
        }
        assertTrue(result.isFailure)
        assertEquals(winner, store.readActiveSourceAttachment("session-1"))
        assertEquals(1, File(root, "session-1/sources").listFiles()!!.size)
    }

    private fun corruptAttachment(mutate: (File, String) -> Unit) = withStore { root, store ->
        val source = attachment()
        val before = store.bindInitialSourceAttachment("session-1", 0, source)
        val file = File(root, "session-1/sources/${source.attachmentId}.json")
        mutate(file, file.readText())
        var probeCalls = 0
        val result = SourceSessionCoordinator.assessSourceResume(store, "session-1") { _, _ ->
            probeCalls++
            error("corrupt source must not reach URI I/O")
        }.getOrThrow()
        assertEquals(SourceAvailability.CORRUPT_BINDING, result.availability)
        assertEquals(0, probeCalls)
        assertEquals(before, store.readManifest("session-1"))
    }

    @Test fun missingAttachmentIsTypedCorruption() = corruptAttachment { file, _ -> assertTrue(file.delete()) }
    @Test fun malformedAttachmentIsTypedCorruption() = corruptAttachment { file, _ -> file.writeText("{") }
    @Test fun wrongAttachmentIdentityIsTypedCorruption() = corruptAttachment { file, json ->
        file.writeText(JSONObject(json).put("contentUri", "content://changed/video/2").toString())
    }
    @Test fun oversizedAttachmentIsTypedCorruption() = corruptAttachment { file, _ ->
        file.writeText("x".repeat(SourceAttachmentCodec.MAX_BYTES + 1))
    }
    @Test fun unreadableAttachmentIsTypedCorruption() = corruptAttachment { file, _ ->
        assertTrue(file.delete())
        assertTrue(file.mkdir()) // Deterministic EISDIR, independent of emulator/root permissions.
    }
    @Test fun outOfRangeCodecIntegerIsTypedCorruption() = corruptAttachment { file, json ->
        val value = JSONObject(json)
        value.getJSONObject("audioTrack").put("containerIndex", Long.MAX_VALUE)
        file.writeText(value.toString())
    }

    @Test fun manifestAndSessionErrorsRemainFailures() = withStore { root, store ->
        val probe: (SourceProbeToken, SourceAttachment) -> SourceReadObservation = { _, _ -> error("no probe") }
        assertTrue(SourceSessionCoordinator.assessSourceResume(store, "..", probe).isFailure)
        assertTrue(SourceSessionCoordinator.assessSourceResume(store, "missing-session", probe).isFailure)
        File(root, "session-1/manifest.json").writeText("{")
        assertTrue(SourceSessionCoordinator.assessSourceResume(store, "session-1", probe).isFailure)
    }

    @Test fun probeFailureIsNotMisclassifiedAsDurableCorruption() = withStore { _, store ->
        store.bindInitialSourceAttachment("session-1", 0, attachment())
        val defect = IllegalArgumentException("probe programming defect")
        val result = SourceSessionCoordinator.assessSourceResume(store, "session-1") { _, _ -> throw defect }
        assertSame(defect, result.exceptionOrNull())
    }

    @Test fun resumeProbeStillRejectsEpochDriftAndAcceptsUnchangedIdentity() = withStore { _, store ->
        store.bindInitialSourceAttachment("session-1", 0, attachment())
        fun observation(token: SourceProbeToken, source: SourceAttachment) = SourceReadObservation(
            token, source.contentUri, SourceReadStatus.READABLE, source.fingerprint, true)
        val good = SourceSessionCoordinator.assessSourceResume(store, "session-1", ::observation).getOrThrow()
        assertEquals(SourceAvailability.AVAILABLE, good.availability)
        val stale = SourceSessionCoordinator.assessSourceResume(store, "session-1") { token, source ->
            store.bumpEpoch("session-1", 1)
            observation(token, source)
        }.getOrThrow()
        assertEquals(SourceAvailability.STALE_OBSERVATION, stale.availability)
    }
}
