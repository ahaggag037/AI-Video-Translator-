package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

    @Test fun selectedSourceBecomesExactDurableActiveSession() {
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
            val viewModel = TranslationSessionViewModel(context, registry, store)

            viewModel.selectNewSource(uri)
            val state = runBlocking {
                withTimeout(30_000) {
                    viewModel.durableSource.first {
                        it.phase == DurableSourcePhase.BOUND || it.phase == DurableSourcePhase.FAILED
                    }
                }
            }

            assertEquals(DurableSourcePhase.BOUND, state.phase)
            assertNull(state.failure)
            assertEquals(uri, state.contentUri)
            val sessionId = requireNotNull(state.sessionId)
            assertEquals(sessionId, registry.readActiveSessionId())
            val manifest = store.readManifest(sessionId)
            assertEquals(SourceBindingState.ATTACHMENT_BOUND, manifest.sourceBindingState)
            assertNull(manifest.activeSourceSnapshotRef)
            val attachment = store.readActiveSourceAttachment(sessionId)!!
            assertEquals(uri, attachment.contentUri)
            assertEquals(manifest.activeSourceAttachmentRef, attachment.attachmentId)
        } finally {
            source.delete()
            root.deleteRecursively()
        }
    }
}
