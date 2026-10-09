package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionViewModelInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Harness(
        val root: File,
        val source: File,
        val uri: String,
        val store: TranslationSessionStore,
        val registry: ActiveSessionRegistry,
    )

    private fun withHarness(block: (Harness) -> Unit) {
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "viewmodel-session-$id")
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "viewmodel-source-$id.m4a")
        try {
            source.writeBytes(
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("source_capture/tone_a.m4a").use { it.readBytes() }
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.subtitles", source).toString()
            val store = TranslationSessionStore(root)
            val registry = ActiveSessionRegistry(root, validateSession = { store.readManifest(it); Unit })
            block(Harness(root, source, uri, store, registry))
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }

    private fun awaitSource(viewModel: TranslationSessionViewModel): DurableSourceUiState = runBlocking {
        withTimeout(30_000) {
            viewModel.durableSource.first {
                it.phase == DurableSourcePhase.BOUND || it.phase == DurableSourcePhase.FAILED
            }
        }
    }

    private fun awaitStt(viewModel: TranslationSessionViewModel): DurableSttUiState = runBlocking {
        withTimeout(30_000) {
            viewModel.stt.first {
                it.phase == DurableSttPhase.LIVE_SUCCESS ||
                    it.phase == DurableSttPhase.RECOVERED ||
                    it.phase == DurableSttPhase.UNKNOWN_REMOTE_OUTCOME ||
                    it.phase == DurableSttPhase.FAILED
            }
        }
    }

    private fun bindSyntheticSnapshot(
        store: TranslationSessionStore,
        sessionId: String,
        transcript: String,
    ): SessionManifest {
        val manifest = store.readManifest(sessionId)
        val attachment = store.readActiveSourceAttachment(sessionId)!!
        val snapshot = SourceSnapshot(
            sessionId = sessionId,
            sourceAttachmentId = attachment.attachmentId,
            transcript = transcript,
            words = listOf(SourceSnapshotWord(0, transcript, 0.9, audioInterval = null)),
            pcmSample = SourcePcmSampleIdentity(
                wavSha256 = "a".repeat(64),
                wavSizeBytes = 48_044,
                pcmFrameCount = 24_000,
                sampleRateHz = 48_000,
                channelCount = 1,
                bitsPerSample = 16,
            ),
            stt = SourceSttProvenance(
                providerId = "nvidia",
                modelId = "parakeet-ctc-1.1b-en-us",
                requestProfileId = "test-profile",
                parserVersion = "test-parser",
                rawResponseSha256 = "b".repeat(64),
                httpStatus = 200,
            ),
            clock = SourceClockProvenance(
                observedPresentationOriginUs = 0,
                verificationStatus = ClockVerificationStatus.UNVERIFIED,
            ),
        )
        return store.bindInitialSourceSnapshot(sessionId, manifest.revision, snapshot)
    }

    @Test fun selectedSourceBecomesExactDurableActiveSession() = withHarness { harness ->
        val viewModel = TranslationSessionViewModel(context, harness.registry, harness.store)
        viewModel.selectNewSource(harness.uri)
        val state = awaitSource(viewModel)

        assertEquals(DurableSourcePhase.BOUND, state.phase)
        assertNull(state.failure)
        assertEquals(harness.uri, state.contentUri)
        val sessionId = requireNotNull(state.sessionId)
        assertEquals(sessionId, harness.registry.readActiveSessionId())
        val manifest = harness.store.readManifest(sessionId)
        assertEquals(SourceBindingState.ATTACHMENT_BOUND, manifest.sourceBindingState)
        assertNull(manifest.activeSourceSnapshotRef)
        val attachment = harness.store.readActiveSourceAttachment(sessionId)!!
        assertEquals(harness.uri, attachment.contentUri)
        assertEquals(manifest.activeSourceAttachmentRef, attachment.attachmentId)
    }

    @Test fun liveSttResultIsPublishedFromOneRunnerInvocationAndSameDurableSnapshot() = withHarness { harness ->
        var calls = 0
        val accepted = NvidiaSttResult(
            transcript = "one hosted response",
            words = listOf(NvidiaWord("one", 10, 20, 0.9)),
            httpStatus = 200,
        )
        val runner = DurableSttRunner { _, store, sessionId, _ ->
            calls++
            val manifest = bindSyntheticSnapshot(store, sessionId, accepted.transcript)
            Result.success(
                SourceSnapshotOperationResult(
                    manifest = manifest,
                    delivery = SourceSnapshotDelivery.LIVE_PROVIDER,
                    legacyResult = accepted,
                )
            )
        }
        val viewModel = TranslationSessionViewModel(context, harness.registry, harness.store, runner)
        viewModel.selectNewSource(harness.uri)
        assertEquals(DurableSourcePhase.BOUND, awaitSource(viewModel).phase)

        viewModel.runStt("not-persisted-test-key")
        val stt = awaitStt(viewModel)

        assertEquals(1, calls)
        assertEquals(DurableSttPhase.LIVE_SUCCESS, stt.phase)
        assertSame(accepted, stt.legacyResult)
        assertEquals(accepted.transcript, stt.transcript)
        val sessionId = requireNotNull(stt.sessionId)
        assertEquals(accepted.transcript, harness.store.readActiveSourceSnapshot(sessionId)!!.transcript)
        assertEquals(SourceBindingState.SNAPSHOT_BOUND, harness.store.readManifest(sessionId).sourceBindingState)
    }

    @Test fun localRecoveryPublishesTranscriptWithoutInventingLegacyTiming() = withHarness { harness ->
        var calls = 0
        val runner = DurableSttRunner { _, store, sessionId, _ ->
            calls++
            val manifest = bindSyntheticSnapshot(store, sessionId, "recovered durable transcript")
            Result.success(
                SourceSnapshotOperationResult(
                    manifest = manifest,
                    delivery = SourceSnapshotDelivery.LOCAL_RECOVERY,
                )
            )
        }
        val viewModel = TranslationSessionViewModel(context, harness.registry, harness.store, runner)
        viewModel.selectNewSource(harness.uri)
        assertEquals(DurableSourcePhase.BOUND, awaitSource(viewModel).phase)

        viewModel.runStt("unused-test-key")
        val stt = awaitStt(viewModel)

        assertEquals(1, calls)
        assertEquals(DurableSttPhase.RECOVERED, stt.phase)
        assertEquals("recovered durable transcript", stt.transcript)
        assertNull(stt.legacyResult)
    }
}
