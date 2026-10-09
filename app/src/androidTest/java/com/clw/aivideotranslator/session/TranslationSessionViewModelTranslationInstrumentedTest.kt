package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SubtitlePipeline
import com.clw.aivideotranslator.provider.ContentValidationOutcome
import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TranslationSessionViewModelTranslationInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Harness(
        val root: File,
        val source: File,
        val uri: String,
        val store: TranslationSessionStore,
        val registry: ActiveSessionRegistry,
        val planStore: TranslationRequestPlanStore,
    )

    private fun withHarness(block: (Harness) -> Unit) {
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "viewmodel-translation-$id")
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "translation-source-$id.m4a")
        try {
            source.writeBytes(
                InstrumentationRegistry.getInstrumentation().context.assets
                    .open("source_capture/tone_a.m4a").use { it.readBytes() }
            )
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.subtitles", source).toString()
            val store = TranslationSessionStore(root)
            val registry = ActiveSessionRegistry(root, validateSession = { store.readManifest(it); Unit })
            val planStore = TranslationRequestPlanStore(root) { sessionId -> store.readManifest(sessionId); Unit }
            block(Harness(root, source, uri, store, registry, planStore))
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }

    private fun liveResult() = NvidiaSttResult(
        transcript = "Hello world.",
        words = listOf(
            NvidiaWord("Hello", 0, 180, 0.95),
            NvidiaWord("world.", 200, 420, 0.95),
        ),
        httpStatus = 200,
    )

    private fun candidate(text: String = "مرحبًا بالعالم.") = TranslationProviderOutcome(
        transport = TransportOutcome.RESPONSE_RECEIVED,
        protocol = ProtocolOutcome.CANDIDATE,
        contentValidation = ContentValidationOutcome.CANDIDATE_UNVALIDATED,
        candidateText = text,
        httpStatus = 200,
    )

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

    private fun liveSttRunner(result: NvidiaSttResult) = DurableSttRunner { _, store, sessionId, _ ->
        val manifest = bindSyntheticSnapshot(store, sessionId, result.transcript)
        Result.success(
            SourceSnapshotOperationResult(
                manifest = manifest,
                delivery = SourceSnapshotDelivery.LIVE_PROVIDER,
                legacyResult = result,
            )
        )
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

    private fun awaitTranslation(viewModel: TranslationSessionViewModel): DurableTranslationUiState = runBlocking {
        withTimeout(30_000) {
            viewModel.translation.first {
                it.phase == DurableTranslationPhase.LIVE_SUCCESS ||
                    it.phase == DurableTranslationPhase.RECOVERED_TEXT_ONLY ||
                    it.phase == DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME ||
                    it.phase == DurableTranslationPhase.BLOCKED ||
                    it.phase == DurableTranslationPhase.FAILED
            }
        }
    }

    private fun establishLiveSession(harness: Harness, result: NvidiaSttResult): Pair<TranslationSessionViewModel, String> {
        val viewModel = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = harness.store,
            sttRunner = liveSttRunner(result),
            planStore = harness.planStore,
        )
        viewModel.selectNewSource(harness.uri)
        assertEquals(DurableSourcePhase.BOUND, awaitSource(viewModel).phase)
        viewModel.runStt("not-persisted-test-key")
        val stt = awaitStt(viewModel)
        assertEquals(DurableSttPhase.LIVE_SUCCESS, stt.phase)
        assertSame(result, stt.legacyResult)
        return viewModel to requireNotNull(stt.sessionId)
    }

    private fun preparedReceipt(
        store: TranslationSessionStore,
        sessionId: String,
        unit: LegacyParityTranslationUnit,
        attemptId: String,
    ): RequestReceipt {
        val manifest = store.readManifest(sessionId)
        return RequestReceipt(
            attemptId = attemptId,
            sessionId = sessionId,
            unitId = unit.requestPlan.unitId,
            epoch = manifest.epoch,
            requestSignature = unit.requestPlan.requestSignature,
            expectedManifestRevision = manifest.revision,
            expectedActiveEntryRevisionId = manifest.activeEntryRefs[unit.requestPlan.unitId],
            phase = RequestReceiptPhase.PREPARED,
        )
    }

    @Test fun liveTranslationPublishesExactP0FUnitsFromOneRunnerInvocation() = withHarness { harness ->
        val live = liveResult()
        var translationCalls = 0
        val runner = DurableTranslationRunner { _, _, _, _, observed ->
            translationCalls++
            assertSame(live, observed)
            val units = LegacyParityTranslationPlanner.plan(observed)
            Result.success(
                DurableTranslationExecution(
                    units = units,
                    batch = DurableTranslationBatchResult(
                        units.mapIndexed { index, unit ->
                            DurableTranslationUnitResult(
                                unitId = unit.requestPlan.unitId,
                                disposition = DurableTranslationUnitDisposition.ADOPTED,
                                effectiveText = "ترجمة ${index + 1}",
                            )
                        }
                    ),
                )
            )
        }
        val viewModel = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = harness.store,
            sttRunner = liveSttRunner(live),
            planStore = harness.planStore,
            translationRunner = runner,
        )
        viewModel.selectNewSource(harness.uri)
        assertEquals(DurableSourcePhase.BOUND, awaitSource(viewModel).phase)
        viewModel.runStt("stt-key-not-persisted")
        assertEquals(DurableSttPhase.LIVE_SUCCESS, awaitStt(viewModel).phase)

        viewModel.runTranslation("translation-key-not-persisted")
        val translated = awaitTranslation(viewModel)

        assertEquals(1, translationCalls)
        assertEquals(DurableTranslationPhase.LIVE_SUCCESS, translated.phase)
        val expectedUnits = SubtitlePipeline.sourceUnits(live.words)
        assertEquals(expectedUnits, translated.liveUnits)
        assertEquals(expectedUnits.map { it.id }, translated.entries.map { it.sourceUnitId })
        assertEquals(listOf("ترجمة 1"), translated.entries.map { it.translatedText })
        assertNull(translated.blocker)
        assertNull(translated.failure)
    }

    @Test fun sentTranslationReopensAsUnknownWithoutProviderInvocation() = withHarness { harness ->
        val live = liveResult()
        val (_, sessionId) = establishLiveSession(harness, live)
        val unit = LegacyParityTranslationPlanner.plan(live).single()
        harness.planStore.publish(sessionId, unit.requestPlan)
        val prepared = preparedReceipt(harness.store, sessionId, unit, "attempt-sent-viewmodel")
        harness.store.writeReceipt(prepared)
        harness.store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        var translationRunnerCalls = 0

        val reopened = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = harness.store,
            planStore = harness.planStore,
            translationRunner = DurableTranslationRunner { _, _, _, _, _ ->
                translationRunnerCalls++
                error("passive reopen must not execute provider translation")
            },
        )
        val translated = awaitTranslation(reopened)

        assertEquals(0, translationRunnerCalls)
        assertEquals(DurableTranslationPhase.UNKNOWN_REMOTE_OUTCOME, translated.phase)
        assertEquals(DurableTranslationUnitDisposition.UNKNOWN_REMOTE_OUTCOME, translated.blocker)
        assertEquals(RequestReceiptPhase.SENT, harness.store.readReceipt(sessionId, "attempt-sent-viewmodel").phase)
        assertNull(harness.store.readActiveEntry(sessionId, unit.requestPlan.unitId))
    }

    @Test fun receivedTranslationReopensAndAdoptsLocallyWithoutProviderInvocation() = withHarness { harness ->
        val live = liveResult()
        val (_, sessionId) = establishLiveSession(harness, live)
        val unit = LegacyParityTranslationPlanner.plan(live).single()
        harness.planStore.publish(sessionId, unit.requestPlan)
        val prepared = preparedReceipt(harness.store, sessionId, unit, "attempt-received-viewmodel")
        harness.store.writeReceipt(prepared)
        val sent = harness.store.markSentIfCurrent(prepared.copy(phase = RequestReceiptPhase.SENT))
        harness.store.writeReceipt(
            sent.copy(
                phase = RequestReceiptPhase.RECEIVED,
                outcome = candidate(),
            )
        )
        var translationRunnerCalls = 0

        val reopened = TranslationSessionViewModel(
            context = context,
            activeSessionOwner = harness.registry,
            store = harness.store,
            planStore = harness.planStore,
            translationRunner = DurableTranslationRunner { _, _, _, _, _ ->
                translationRunnerCalls++
                error("RECEIVED passive recovery must not execute provider translation")
            },
        )
        val translated = awaitTranslation(reopened)

        assertEquals(0, translationRunnerCalls)
        assertEquals(DurableTranslationPhase.RECOVERED_TEXT_ONLY, translated.phase)
        assertNull(translated.liveUnits)
        assertEquals(listOf(unit.requestPlan.unitId), translated.entries.map { it.sourceUnitId })
        assertEquals("مرحبًا بالعالم.", translated.entries.single().translatedText)
        assertEquals(
            "مرحبًا بالعالم.",
            assertNotNull(harness.store.readActiveEntry(sessionId, unit.requestPlan.unitId)).record.effectiveText(),
        )
        assertEquals(
            RequestReceiptPhase.RECEIVED,
            harness.store.readReceipt(sessionId, "attempt-received-viewmodel").phase,
        )
    }
}
