package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.SttAudioProfile
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X001 regression for the consequential fail-closed transport change: durable source adoption and
 * RECEIVED recovery must remain valid with accepted text/confidence but no interpreted word timing.
 */
@RunWith(AndroidJUnit4::class)
class X001NullTimingDurabilityInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withStore(block: (String, File, TranslationSessionStore) -> Unit) {
        val id = UUID.randomUUID().toString()
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "x001-null-$id.m4a")
        val root = File(context.cacheDir, "x001-null-session-$id")
        try {
            source.writeBytes(
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("source_capture/tone_a.m4a").use { it.readBytes() }
            )
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.subtitles",
                source,
            ).toString()
            val store = TranslationSessionStore(root)
            store.createSession(SESSION_ID)
            block(uri, root, store)
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }

    private fun bind(store: TranslationSessionStore, uri: String): SourceAttachment {
        val attachment = SourceAttachmentBuilder.build(context, SESSION_ID, uri).getOrThrow()
        store.bindInitialSourceAttachment(SESSION_ID, 0L, attachment)
        return attachment
    }

    private fun observation(profile: SttAudioProfile) = NvidiaSttClient.bindDetailedResponse(
        body = """
            {"text":"synthetic fixture","words":[
              {"word":"synthetic","start":0.01,"end":0.02,"confidence":0.9}
            ]}
        """.trimIndent(),
        httpStatus = 200,
        sampleSha256 = sha256(profile.file),
    )

    private fun sha256(file: File): String = MessageDigest.getInstance("SHA-256")
        .digest(file.readBytes())
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun attemptId(attachment: SourceAttachment): String = SttAttemptIdentity.forInitialSnapshot(
        sessionId = SESSION_ID,
        sourceAttachmentId = attachment.attachmentId,
    )

    @Test fun liveDurableAdoptionAcceptsTextButPersistsNoUnverifiedTiming() = withStore { uri, _, store ->
        val attachment = bind(store, uri)
        var providerCalls = 0

        val outcome = SourceSnapshotOperation.runDetailed(
            context = context,
            store = store,
            sessionId = SESSION_ID,
            transcribe = { profile ->
                providerCalls += 1
                observation(profile)
            },
        ).getOrThrow()

        assertEquals(1, providerCalls)
        assertEquals(SourceSnapshotDelivery.LIVE_PROVIDER, outcome.delivery)
        val live = requireNotNull(outcome.legacyResult)
        assertEquals("synthetic fixture", live.transcript)
        assertTrue(live.words.isNotEmpty())
        assertTrue(live.words.all { it.startMs == null && it.endMs == null })

        val snapshot = requireNotNull(store.readActiveSourceSnapshot(SESSION_ID))
        assertEquals(attachment.attachmentId, snapshot.sourceAttachmentId)
        assertEquals(ClockVerificationStatus.UNVERIFIED, snapshot.clock.verificationStatus)
        assertTrue(snapshot.words.all { it.audioInterval == null })

        val receipt = requireNotNull(store.readSttAttemptOrNull(SESSION_ID, attemptId(attachment)))
        assertEquals(SttAttemptPhase.ADOPTED, receipt.phase)
        assertEquals(snapshot.snapshotId, requireNotNull(receipt.snapshot).snapshotId)
    }

    @Test fun receivedRecoveryAdoptsLocallyWithoutProviderOrInventedTiming() = withStore { uri, root, _ ->
        val simulatedDeath = IOException("synthetic process death after RECEIVED")
        val crashStore = TranslationSessionStore(root, object : SessionStoreFaultInjector {
            override fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String) = Unit

            override fun afterSttAttemptReceivedPersisted(sessionId: String, attemptId: String) {
                throw simulatedDeath
            }
        })
        val attachment = bind(crashStore, uri)
        var providerCalls = 0

        val first = SourceSnapshotOperation.runDetailed(
            context = context,
            store = crashStore,
            sessionId = SESSION_ID,
            transcribe = { profile ->
                providerCalls += 1
                observation(profile)
            },
        )
        assertTrue(first.isFailure)
        assertEquals(1, providerCalls)

        val id = attemptId(attachment)
        val reopened = TranslationSessionStore(root)
        val received = requireNotNull(reopened.readSttAttemptOrNull(SESSION_ID, id))
        assertEquals(SttAttemptPhase.RECEIVED, received.phase)
        val receivedSnapshot = requireNotNull(received.snapshot)
        assertEquals(ClockVerificationStatus.UNVERIFIED, receivedSnapshot.clock.verificationStatus)
        assertTrue(receivedSnapshot.words.all { it.audioInterval == null })

        val resumed = SourceSnapshotOperation.runDetailed(
            context = context,
            store = reopened,
            sessionId = SESSION_ID,
            transcribe = {
                providerCalls += 1
                error("RECEIVED recovery must not call the provider")
            },
        ).getOrThrow()

        assertEquals(1, providerCalls)
        assertEquals(SourceSnapshotDelivery.LOCAL_RECOVERY, resumed.delivery)
        assertNull(resumed.legacyResult)
        val active = requireNotNull(reopened.readActiveSourceSnapshot(SESSION_ID))
        assertEquals(receivedSnapshot.snapshotId, active.snapshotId)
        assertEquals(ClockVerificationStatus.UNVERIFIED, active.clock.verificationStatus)
        assertTrue(active.words.all { it.audioInterval == null })
        assertEquals(SttAttemptPhase.ADOPTED, requireNotNull(reopened.readSttAttemptOrNull(SESSION_ID, id)).phase)
    }

    private companion object {
        const val SESSION_ID = "session-1"
    }
}
