package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.NvidiaSttClient
import com.clw.aivideotranslator.NvidiaSttParserContract
import com.clw.aivideotranslator.NvidiaSttWireContract
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** X001 end-to-end UI boundary: null hosted timing becomes a controlled translation failure. */
@RunWith(AndroidJUnit4::class)
class X001NullTimingViewModelInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun liveUntimedSttFailsTranslationBeforeProviderCapablePlanOrTimelineExists() {
        val id = UUID.randomUUID().toString()
        val root = File(context.cacheDir, "x001-null-viewmodel-$id")
        val source = File(File(context.cacheDir, "p0_subtitles").apply { mkdirs() }, "x001-null-vm-$id.m4a")
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
            val registry = ActiveSessionRegistry(root, validateSession = { sessionId -> store.readManifest(sessionId); Unit })
            val planStore = TranslationRequestPlanStore(root) { sessionId -> store.readManifest(sessionId); Unit }
            val untimed = NvidiaSttClient.parseResponseWithoutTimingAuthority(
                """
                {"text":"Hello world.","words":[
                  {"word":"Hello","start":0.10,"end":0.25,"confidence":0.9},
                  {"word":"world.","start":0.30,"end":0.80,"confidence":0.8}
                ]}
                """.trimIndent(),
                200,
            )
            assertTrue(untimed.words.all { it.startMs == null && it.endMs == null })

            val sttRunner = DurableSttRunner { _, sessionStore, sessionId, _ ->
                val manifest = sessionStore.readManifest(sessionId)
                val attachment = requireNotNull(sessionStore.readActiveSourceAttachment(sessionId))
                val snapshot = SourceSnapshot(
                    sessionId = sessionId,
                    sourceAttachmentId = attachment.attachmentId,
                    transcript = untimed.transcript,
                    words = untimed.words.mapIndexed { index, word ->
                        SourceSnapshotWord(index, word.text, word.confidence, audioInterval = null)
                    },
                    pcmSample = SourcePcmSampleIdentity(
                        wavSha256 = "a".repeat(64),
                        wavSizeBytes = 48_044,
                        pcmFrameCount = 24_000,
                        sampleRateHz = 48_000,
                        channelCount = 1,
                        bitsPerSample = 16,
                    ),
                    stt = SourceSttProvenance(
                        providerId = NvidiaSttWireContract.PROFILE.providerId,
                        modelId = NvidiaSttWireContract.PROFILE.modelId,
                        requestProfileId = NvidiaSttWireContract.PROFILE.profileId,
                        parserVersion = NvidiaSttParserContract.ID,
                        rawResponseSha256 = "b".repeat(64),
                        httpStatus = 200,
                    ),
                    clock = SourceClockProvenance(
                        observedPresentationOriginUs = 0L,
                        verificationStatus = ClockVerificationStatus.UNVERIFIED,
                    ),
                )
                val bound = sessionStore.bindInitialSourceSnapshot(sessionId, manifest.revision, snapshot)
                Result.success(
                    SourceSnapshotOperationResult(
                        manifest = bound,
                        delivery = SourceSnapshotDelivery.LIVE_PROVIDER,
                        legacyResult = untimed,
                    )
                )
            }

            val viewModel = TranslationSessionViewModel(
                context = context,
                activeSessionOwner = registry,
                store = store,
                sttRunner = sttRunner,
                planStore = planStore,
            )
            viewModel.selectNewSource(uri)
            val sourceState = runBlocking {
                withTimeout(30_000) {
                    viewModel.durableSource.first {
                        it.phase == DurableSourcePhase.BOUND || it.phase == DurableSourcePhase.FAILED
                    }
                }
            }
            assertEquals(DurableSourcePhase.BOUND, sourceState.phase)

            viewModel.runStt("synthetic-stt-key-not-persisted")
            val sttState = runBlocking {
                withTimeout(30_000) {
                    viewModel.stt.first {
                        it.phase == DurableSttPhase.LIVE_SUCCESS || it.phase == DurableSttPhase.FAILED
                    }
                }
            }
            assertEquals(DurableSttPhase.LIVE_SUCCESS, sttState.phase)
            assertTrue(requireNotNull(sttState.legacyResult).words.all { it.startMs == null && it.endMs == null })

            // The default translation runner plans before any provider call. Untimed words must stop
            // there, and ViewModel must publish a controlled failure rather than crash or mint cues.
            viewModel.runTranslation("synthetic-translation-key-not-persisted")
            val translation = runBlocking {
                withTimeout(30_000) {
                    viewModel.translation.first {
                        it.phase == DurableTranslationPhase.FAILED ||
                            it.phase == DurableTranslationPhase.LIVE_SUCCESS ||
                            it.phase == DurableTranslationPhase.BLOCKED
                    }
                }
            }
            assertEquals(DurableTranslationPhase.FAILED, translation.phase)
            assertEquals(DurableTranslationFailure.PROVIDER_OR_STORAGE, translation.failure)
            assertNull(translation.liveUnits)
            assertTrue(translation.entries.isEmpty())
            val sessionId = requireNotNull(sttState.sessionId)
            assertTrue(store.listReceipts(sessionId).isEmpty())
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }
}
