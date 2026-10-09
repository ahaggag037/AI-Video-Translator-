package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.MachineTranslationRevision
import com.clw.aivideotranslator.semantic.TranslationRecord
import com.clw.aivideotranslator.semantic.TranslationReviewState
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionTranslationRecoveryInstrumentedTest {
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
        val root = File(context.cacheDir, "translation-reopen-$id")
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "translation-reopen-$id.m4a")
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
                    it.phase == DurableSttPhase.FAILED
            }
        }
    }

    private fun awaitTranslation(viewModel: TranslationSessionViewModel): DurableTranslationUiState = runBlocking {
        withTimeout(30_000) {
            viewModel.translation.first { it.phase != DurableTranslationPhase.IDLE && it.phase != DurableTranslationPhase.RUNNING }
        }
    }

    private fun bindSnapshot(
        store: TranslationSessionStore,
        sessionId: String,
        transcript: String,
    ): SessionManifest {
        val manifest = store.readManifest(sessionId)
        val attachment = store.readActiveSourceAttachment(sessionId)!!
        return store.bindInitialSourceSnapshot(
            sessionId,
            manifest.revision,
            SourceSnapshot(
                sessionId = sessionId,
                sourceAttachmentId = attachment.attachmentId,
                transcript = transcript,
                words = listOf(SourceSnapshotWord(0, transcript, 0.9, audioInterval = null)),
                pcmSample = SourcePcmSampleIdentity(
                    wavSha256 = "c".repeat(64),
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
                    rawResponseSha256 = "d".repeat(64),
                    httpStatus = 200,
                ),
                clock = SourceClockProvenance(
                    observedPresentationOriginUs = 0,
                    verificationStatus = ClockVerificationStatus.UNVERIFIED,
                ),
            ),
        )
    }

    private fun liveResult() = NvidiaSttResult(
        transcript = "Hello world.",
        words = listOf(
            NvidiaWord("Hello", 10, 180, 0.9),
            NvidiaWord("world.", 200, 400, 0.9),
        ),
        httpStatus = 200,
    )

    private fun openLiveSession(harness: Harness, accepted: NvidiaSttResult): String {
        val runner = DurableSttRunner { _, store, sessionId, _ ->
            val manifest = bindSnapshot(store, sessionId, accepted.transcript)
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
        viewModel.runStt("test-key-not-persisted")
        val stt = awaitStt(viewModel)
        assertEquals(DurableSttPhase.LIVE_SUCCESS, stt.phase)
        return requireNotNull(stt.sessionId)
    }

    @Test fun receivedCandidateIsAdoptedOnRestartWithoutProviderCall() = withHarness { harness ->
        val accepted = liveResult()
        val sessionId = openLiveSession(harness, accepted)
        val plan = LegacyParityTranslationPlanner.plan(accepted).single().requestPlan
        val planStore = TranslationRequestPlanStore(harness.root) { harness.store.readManifest(it); Unit }
        planStore.publish(sessionId, plan)
        val manifest = harness.store.readManifest(sessionId)
        val prepared = harness.store.writeReceipt(
            RequestReceipt(
                attemptId = "attempt-received-ui-reopen",
                sessionId = sessionId,
                unitId = plan.unitId,
                epoch = manifest.epoch,
                requestSignature = plan.requestSignature,
                expectedManifestRevision = manifest.revision,
                expectedActiveEntryRevisionId = null,
                phase = RequestReceiptPhase.PREPARED,
            )
        )
        val sent = harness.store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        harness.store.writeReceipt(
            sent.copy(
                phase = RequestReceiptPhase.RECEIVED,
                outcome = TranslationProviderOutcome(
                    transport = TransportOutcome.RESPONSE_RECEIVED,
                    protocol = ProtocolOutcome.CANDIDATE,
                    contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
                    candidateText = "مرحبًا بالعالم.",
                ),
            )
        )
        assertEquals(null, harness.store.readActiveEntry(sessionId, plan.unitId))

        val reopenedStore = TranslationSessionStore(harness.root)
        val reopenedPlanStore = TranslationRequestPlanStore(harness.root) { reopenedStore.readManifest(it); Unit }
        var providerCalls = 0
        val reopened = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = reopenedStore,
            planStore = reopenedPlanStore,
            translationSubmitterFactory = {
                TranslationPlanSubmitter {
                    providerCalls++
                    error("RECEIVED reopen must not invoke transport")
                }
            },
        )
        val recovered = awaitTranslation(reopened)

        assertEquals(DurableTranslationPhase.COMPLETE, recovered.phase)
        assertEquals(0, providerCalls)
        assertEquals("مرحبًا بالعالم.", recovered.texts.single().text)
        assertEquals(
            "مرحبًا بالعالم.",
            reopenedStore.readActiveEntry(sessionId, plan.unitId)!!.record.effectiveText(),
        )
    }

    @Test fun durablePrefixIsNotReportedAsCompleteAfterRestart() = withHarness { harness ->
        val accepted = NvidiaSttResult(
            transcript = "One. Two.",
            words = listOf(
                NvidiaWord("One.", 10, 180, 0.9),
                NvidiaWord("Two.", 200, 400, 0.9),
            ),
            httpStatus = 200,
        )
        val sessionId = openLiveSession(harness, accepted)
        val units = LegacyParityTranslationPlanner.plan(accepted)
        assertEquals(2, units.size)
        val planStore = TranslationRequestPlanStore(harness.root) { harness.store.readManifest(it); Unit }
        units.forEach { planStore.publish(sessionId, it.requestPlan) }

        val firstPlan = units.first().requestPlan
        val firstEntry = StoredTranslationEntry(
            revisionId = "entry-prefix-u0001",
            record = TranslationRecord(
                unitId = firstPlan.unitId,
                machineRevisions = listOf(
                    MachineTranslationRevision(
                        id = "machine-prefix-u0001",
                        text = "واحد.",
                        requestSignature = firstPlan.requestSignature,
                    )
                ),
                activeMachineRevisionId = "machine-prefix-u0001",
                manualRevision = null,
                reviewState = TranslationReviewState.MACHINE_CANDIDATE,
            ),
        )
        val manifest = harness.store.readManifest(sessionId)
        harness.store.commitEntry(sessionId, manifest.revision, firstEntry)

        val reopenedStore = TranslationSessionStore(harness.root)
        val reopenedPlanStore = TranslationRequestPlanStore(harness.root) { reopenedStore.readManifest(it); Unit }
        val reopened = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = reopenedStore,
            planStore = reopenedPlanStore,
        )
        val recovered = awaitTranslation(reopened)

        assertEquals(DurableTranslationPhase.FAILED, recovered.phase)
        assertEquals(DurableTranslationFailure.RESTORE, recovered.failure)
        assertEquals("u0002", recovered.blockingUnitId)
        assertEquals(listOf("u0001"), recovered.texts.map { it.unitId })
        assertNotNull(reopenedStore.readActiveEntry(sessionId, "u0001"))
        assertTrue(reopenedStore.listReceipts(sessionId).isEmpty())
    }
}
