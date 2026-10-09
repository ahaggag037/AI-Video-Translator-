package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.SttAudioProfile
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceSnapshotOperationResultInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withBoundSource(block: (File, TranslationSessionStore) -> Unit) {
        val id = UUID.randomUUID().toString()
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "single-stt-$id.m4a")
        val root = File(context.cacheDir, "single-stt-sessions-$id")
        try {
            source.writeBytes(
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("source_capture/tone_a.m4a").use { it.readBytes() }
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.subtitles", source).toString()
            val store = TranslationSessionStore(root)
            store.createSession("session-1")
            val attachment = SourceAttachmentBuilder.build(context, "session-1", uri).getOrThrow()
            store.bindInitialSourceAttachment("session-1", 0L, attachment)
            block(root, store)
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }

    private fun observation(profile: SttAudioProfile) = NvidiaSttClient.bindDetailedResponse(
        """{"text":"single response","words":[{"word":"single","start":0.01,"end":0.02}]}""",
        200,
        MessageDigest.getInstance("SHA-256").digest(profile.file.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 0xff) },
    )

    @Test fun liveDetailedResultReturnsExactAcceptedParseFromOneSubmission() = withBoundSource { _, store ->
        var providerCalls = 0
        var accepted: NvidiaSttResult? = null

        val result = SourceSnapshotOperation.runDetailed(context, store, "session-1", transcribe = { profile ->
            providerCalls++
            observation(profile).also { accepted = it.result }
        }).getOrThrow()

        assertEquals(1, providerCalls)
        assertEquals(SourceSnapshotDelivery.LIVE_PROVIDER, result.delivery)
        assertSame(accepted, result.legacyResult)
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, result.manifest.sourceBindingState)
        assertNotNull(store.readActiveSourceSnapshot("session-1"))
    }

    @Test fun durableReceivedRecoveryReturnsNoLegacyTimingAndNeverResubmits() = withBoundSource { root, initialStore ->
        val simulatedDeath = IOException("synthetic death after durable RECEIVED")
        val crashStore = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit
            override fun afterSttAttemptReceivedPersisted(sessionId: String, attemptId: String) {
                throw simulatedDeath
            }
        })
        // Reuse the source binding created by initialStore; both objects point at the same durable root.
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, initialStore.readManifest("session-1").sourceBindingState)
        var providerCalls = 0
        val first = SourceSnapshotOperation.runDetailed(context, crashStore, "session-1", transcribe = { profile ->
            providerCalls++
            observation(profile)
        })
        assertSame(simulatedDeath, first.exceptionOrNull())
        assertEquals(1, providerCalls)

        val reopened = TranslationSessionStore(root)
        val recovered = SourceSnapshotOperation.runDetailed(context, reopened, "session-1", transcribe = {
            providerCalls++
            error("durable RECEIVED must not submit again")
        }).getOrThrow()

        assertEquals(1, providerCalls)
        assertEquals(SourceSnapshotDelivery.LOCAL_RECOVERY, recovered.delivery)
        assertNull(recovered.legacyResult)
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, recovered.manifest.sourceBindingState)
        assertNotNull(reopened.readActiveSourceSnapshot("session-1"))
    }
}
