package com.clw.aivideotranslator.session

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.ManualTranslationRevision
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import java.io.File
import java.io.IOException
import java.util.UUID
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceAttachmentStoreInstrumentedTest {
    private fun source() = SourceAttachment(
        "session-1", "content://synthetic.documents/video/1", true,
        SourceFingerprint("a".repeat(64), 123_456), 42_000_000,
        PresentationIntervalUs(PresentationTimeUs(500_000), PresentationTimeUs(12_000_000)),
        SourceAudioTrack(2, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun withRoot(block: (File) -> Unit) {
        val root = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "source-test-${UUID.randomUUID()}")
        try { block(root) } finally { root.deleteRecursively() }
    }

    private fun rejected(block: () -> Unit) {
        var failed = false
        try { block() } catch (_: Exception) { failed = true }
        assertTrue("must fail closed", failed)
    }

    @Test fun freshBindingSurvivesRestartWithExplicitTrackRangeAndEpoch() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val source = source()
        val bound = store.bindInitialSourceAttachment("session-1", 0, source)
        val restart = TranslationSessionStore(root)
        assertEquals(1L, bound.revision)
        assertEquals(1L, bound.epoch)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, bound.sourceBindingState)
        assertNull(bound.activeSourceSnapshotRef)
        assertEquals(source, restart.readActiveSourceAttachment("session-1"))
        assertEquals(bound, restart.readManifest("session-1"))
        assertEquals(SourceAvailability.CHECK_REQUIRED,
            SourceResumeEvaluator.evaluate(bound, restart.readActiveSourceAttachment("session-1"), null).availability)
    }

    @Test fun crashAfterImmutablePublicationDoesNotBindAndRetryReusesExactObject() = withRoot { root ->
        val crashing = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit
            override fun afterSourceAttachmentPublished(sessionId: String, attachmentId: String) {
                throw IOException("simulated crash window")
            }
        })
        crashing.createSession("session-1")
        rejected { crashing.bindInitialSourceAttachment("session-1", 0, source()) }
        val restart = TranslationSessionStore(root)
        assertEquals(0L, restart.readManifest("session-1").revision)
        assertNull(restart.readActiveSourceAttachment("session-1"))
        val files = File(root, "session-1/sources").listFiles()!!.filter { it.extension == "json" }
        assertEquals(1, files.size)
        val bytes = files.single().readBytes()
        restart.bindInitialSourceAttachment("session-1", 0, source())
        assertArrayEquals(bytes, files.single().readBytes())
        assertEquals(source(), restart.readActiveSourceAttachment("session-1"))
    }

    @Test fun enospcDuringManifestCommitLeavesUnboundStateAndReusableSource() = withRoot { root ->
        var failManifest = false
        val store = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit
            override fun afterAtomicPayloadWritten(file: File) {
                if (failManifest && file.name == "manifest.json") throw IOException("ENOSPC")
            }
        })
        store.createSession("session-1")
        failManifest = true
        rejected { store.bindInitialSourceAttachment("session-1", 0, source()) }
        val restart = TranslationSessionStore(root)
        assertNull(restart.readActiveSourceAttachment("session-1"))
        assertEquals(0L, restart.readManifest("session-1").revision)
        restart.bindInitialSourceAttachment("session-1", 0, source())
        assertEquals(source(), restart.readActiveSourceAttachment("session-1"))
    }

    @Test fun bindingFencesPreparedSendAndStaleCasCannotPublishAnotherAttachment() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val prepared = RequestReceipt(attemptId = "a1", sessionId = "session-1", unitId = "u1", epoch = 0,
            requestSignature = "synthetic", expectedManifestRevision = 0, expectedActiveEntryRevisionId = null,
            phase = RequestReceiptPhase.PREPARED)
        store.writeReceipt(prepared)
        val bound = store.bindInitialSourceAttachment("session-1", 0, source())
        rejected { store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT)) }
        rejected { store.bindInitialSourceAttachment("session-1", 0, source().copy(contentUri = "content://other/video/2")) }
        assertEquals(bound, store.readManifest("session-1"))
        assertEquals(RequestReceiptPhase.PREPARED, store.readReceipt("session-1", "a1").phase)
        assertEquals(1, File(root, "session-1/sources").listFiles()!!.count { it.extension == "json" })
    }

    @Test fun missingCorruptAndOversizedActiveObjectDoNotGetRepairedOrReset() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val bound = store.bindInitialSourceAttachment("session-1", 0, source())
        val file = File(root, "session-1/sources/${source().attachmentId}.json")
        val original = file.readText()
        file.delete()
        rejected { store.readActiveSourceAttachment("session-1") }
        file.writeText(JSONObject(original).put("rangeStartUs", 600_000).toString())
        rejected { store.readActiveSourceAttachment("session-1") }
        file.writeText("x".repeat(SourceAttachmentCodec.MAX_BYTES + 1))
        rejected { store.readActiveSourceAttachment("session-1") }
        assertEquals(bound, store.readManifest("session-1"))
    }

    @Test fun legacyAndManualHistoryCannotBeSilentlyAttachedToNewSource() = withRoot { root ->
        val store = TranslationSessionStore(root)
        store.createSession("session-1")
        val entry = StoredTranslationEntry("entry-1", TranslationRecord("u1",
            listOf(MachineTranslationRevision("m1", "آلة", "sig")), "m1",
            ManualTranslationRevision("manual-1", "تصحيح يدوي", "old-source", "m1"), TranslationReviewState.APPROVED))
        store.commitEntry("session-1", 0, entry)
        rejected { store.bindInitialSourceAttachment("session-1", 1, source()) }
        assertEquals("تصحيح يدوي", store.readActiveEntry("session-1", "u1")?.record?.effectiveText())
        val manifestFile = File(root, "session-1/manifest.json")
        manifestFile.writeText(JSONObject().put("schemaVersion", 1).put("sessionId", "session-1")
            .put("revision", 1).put("epoch", 0).put("activeEntryRefs", JSONObject().put("u1", "entry-1")).toString())
        rejected { store.bindInitialSourceAttachment("session-1", 1, source()) }
        assertEquals(SourceBindingState.LEGACY_UNBOUND, store.readManifest("session-1").sourceBindingState)
        assertEquals("تصحيح يدوي", store.readActiveEntry("session-1", "u1")?.record?.effectiveText())
    }

    @Test fun directoryDotSessionIdsCannotEscapeStoreRoot() = withRoot { root ->
        val store = TranslationSessionStore(root)
        rejected { store.createSession("..") }
        rejected { store.createSession(".") }
        assertFalse(File(root.parentFile, "manifest.json").exists())
        assertFalse(File(root, "manifest.json").exists())
    }
}
