package com.clw.aivideotranslator.session

import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.SttAudioPreparer
import com.clw.aivideotranslator.SttAudioProfile
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSnapshotOperationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets
        .open("source_capture/$name.m4a").use { it.readBytes() }

    private fun withSource(block: (File, String, File, TranslationSessionStore) -> Unit) {
        val id = UUID.randomUUID().toString()
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "capture-$id.m4a")
        val root = File(context.cacheDir, "capture-sessions-$id")
        try {
            source.writeBytes(fixture("tone_a"))
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.subtitles", source).toString()
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            block(source, uri, root, store)
        } finally { source.delete(); root.deleteRecursively() }
    }

    private fun bind(store: TranslationSessionStore, uri: String): SourceAttachment {
        val attachment = SourceAttachmentBuilder.build(context, "session-1", uri).getOrThrow()
        store.bindInitialSourceAttachment("session-1", 0, attachment)
        return attachment
    }

    private fun observation(profile: SttAudioProfile) = NvidiaSttClient.bindDetailedResponse(
        """{"text":"synthetic fixture","words":[{"word":"synthetic","start":0.01,"end":0.02}]}""",
        200,
        MessageDigest.getInstance("SHA-256").digest(profile.file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) },
    )

    private fun rejected(block: () -> Unit) {
        try { block(); throw AssertionError("must reject") } catch (_: Exception) { }
    }

    @Test fun privateDecodeMatchesLegacyAndIgnoresLaterSameUriReplacement() = withSource { source, uri, _, _ ->
        val legacy = SttAudioPreparer.prepareFirstMinuteDetailed(context, Uri.parse(uri)).getOrThrow()
        val expectedBytes = legacy.profile.file.readBytes()
        val captured = SourceAttachmentBuilder.capture(context, "session-1", uri).getOrThrow()
        var ownedWav: File? = null
        try {
            source.writeBytes(fixture("tone_b"))
            val prepared = captured.prepareFirstMinute()
            ownedWav = prepared.profile.file
            assertNotEquals(legacy.profile.file.canonicalPath, prepared.profile.file.canonicalPath)
            assertArrayEquals(expectedBytes, prepared.profile.file.readBytes())
            assertEquals(legacy.provenance, prepared.provenance)
            assertEquals(legacy.profile.sourceStartUs, prepared.profile.sourceStartUs)
            assertEquals(legacy.profile.sourceEndUs, prepared.profile.sourceEndUs)
            val changed = SttAudioPreparer.prepareFirstMinuteDetailed(context, Uri.parse(uri)).getOrThrow()
            assertFalse("fixture must distinguish the two decoded sources", expectedBytes.contentEquals(changed.profile.file.readBytes()))
        } finally { captured.close() }
        assertFalse(ownedWav!!.exists())
        assertTrue(source.exists())
        rejected { captured.prepareFirstMinute() }
    }

    @Test fun capturesOwnDistinctWavsAndClosingOneCannotDestroyTheOther() = withSource { _, uri, _, _ ->
        val a = SourceAttachmentBuilder.capture(context, "session-1", uri).getOrThrow()
        val b = SourceAttachmentBuilder.capture(context, "session-1", uri).getOrThrow()
        try {
            val wavA = a.prepareFirstMinute().profile.file
            val wavB = b.prepareFirstMinute().profile.file
            assertNotEquals(wavA.canonicalPath, wavB.canonicalPath)
            assertArrayEquals(wavA.readBytes(), wavB.readBytes())
            a.close()
            assertFalse(wavA.exists())
            assertTrue(wavB.exists())
            rejected { b.prepareFirstMinute() }
            b.close()
            assertFalse(wavB.exists())
        } finally { a.close(); b.close() }
    }

    @Test fun sameUriEqualSizeAndMetadataReplacementIsRejectedBeforeSubmission() = withSource { source, uri, _, store ->
        val old = bind(store, uri)
        source.writeBytes(fixture("tone_b"))
        val replacement = SourceAttachmentBuilder.build(context, "session-1", uri).getOrThrow()
        assertEquals(old.fingerprint.sizeBytes, replacement.fingerprint.sizeBytes)
        assertEquals(old.durationUs, replacement.durationUs)
        assertEquals(old.audioTrack, replacement.audioTrack)
        assertNotEquals(old.fingerprint.sha256, replacement.fingerprint.sha256)
        var requests = 0
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; observation(it)
        })
        assertTrue(result.isFailure)
        assertEquals(0, requests)
        assertEquals(1L, store.readManifest("session-1").revision)
        assertNull(store.readActiveSourceSnapshot("session-1"))
    }

    @Test fun deletionAfterCaptureCannotChangeDecodeAndSnapshotStaysUnverified() = withSource { source, uri, _, store ->
        val old = bind(store, uri)
        var requests = 0
        var wav: File? = null
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; wav = it.file; observation(it)
        }, capture = {
            SourceAttachmentBuilder.capture(context, "session-1", it.contentUri, it.selectedRange).getOrThrow()
                .also { assertTrue(source.delete()) }
        }).getOrThrow()
        assertEquals(1, requests)
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, result.sourceBindingState)
        val snapshot = store.readActiveSourceSnapshot("session-1")!!
        assertEquals(old.attachmentId, snapshot.sourceAttachmentId)
        assertEquals(ClockVerificationStatus.UNVERIFIED, snapshot.clock.verificationStatus)
        assertTrue(snapshot.words.all { it.audioInterval == null })
        assertFalse(wav!!.exists())
    }

    @Test fun epochDriftDuringCaptureCannotReachPreparationOrSubmission() = withSource { _, uri, _, store ->
        bind(store, uri)
        var requests = 0
        var owned: CapturedSource? = null
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; observation(it)
        }, capture = {
            SourceAttachmentBuilder.capture(context, "session-1", it.contentUri, it.selectedRange).getOrThrow()
                .also { capture -> owned = capture; store.bumpEpoch("session-1", 1) }
        })
        assertTrue(result.isFailure)
        assertEquals(0, requests)
        assertEquals(2L, store.readManifest("session-1").epoch)
        assertNull(store.readActiveSourceSnapshot("session-1"))
        rejected { owned!!.prepareFirstMinute() } // use closed even on a pre-decode failure.
    }

    @Test fun epochDriftDuringSubmissionRejectsResponseAndNeverRetries() = withSource { _, uri, root, store ->
        bind(store, uri)
        var requests = 0
        var wav: File? = null
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; wav = it.file
            store.bumpEpoch("session-1", 1)
            observation(it)
        })
        assertTrue(result.isFailure)
        assertEquals(1, requests)
        assertEquals(2L, store.readManifest("session-1").epoch)
        assertNull(store.readActiveSourceSnapshot("session-1"))
        assertFalse(File(root, "session-1/snapshots").exists())
        assertFalse(wav!!.exists())
    }

    @Test fun transportFailurePreservesAttachmentAndCleansOnlyOperationFiles() = withSource { source, uri, _, store ->
        bind(store, uri)
        var requests = 0
        var wav: File? = null
        val failure = IOException("synthetic response loss; outcome unknown")
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; wav = it.file; throw failure
        })
        assertSame(failure, result.exceptionOrNull())
        assertEquals(1, requests)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, store.readManifest("session-1").sourceBindingState)
        assertFalse(wav!!.exists())
        assertTrue(source.exists())
    }

    @Test fun unsupportedSelectionCannotSilentlyDecodeTheWrongRange() = withSource { _, uri, _, store ->
        val source = SourceAttachmentBuilder.build(context, "session-1", uri).getOrThrow()
        val selected = source.copy(selectedRange = PresentationIntervalUs(PresentationTimeUs(1_000), PresentationTimeUs(source.durationUs)))
        store.bindInitialSourceAttachment("session-1", 0, selected)
        var captures = 0
        var requests = 0
        val result = SourceSnapshotOperation.run(context, store, "session-1", transcribe = {
            requests++; observation(it)
        }, capture = { captures++; error("must fail before opening source") })
        assertTrue(result.isFailure)
        assertEquals(0, captures)
        assertEquals(0, requests)
        assertEquals(1L, store.readManifest("session-1").revision)
    }

    @Test fun grantObservationDriftDoesNotPretendTheSourceBytesChanged() = withSource { _, uri, _, _ ->
        SourceAttachmentBuilder.capture(context, "session-1", uri).getOrThrow().use { captured ->
            captured.requireMatches(captured.attachment.copy(
                persistedReadGrantAtCapture = !captured.attachment.persistedReadGrantAtCapture))
        }
    }
}
