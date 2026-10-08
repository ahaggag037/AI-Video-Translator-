package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceContentProbeInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun withSource(bytes: ByteArray, block: (File, String) -> Unit) {
        val directory = File(context.cacheDir, "p0_subtitles").apply { mkdirs() }
        val file = File(directory, "probe-${UUID.randomUUID()}.mp4")
        try {
            file.writeBytes(bytes)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.subtitles", file)
            block(file, uri.toString())
        } finally {
            file.delete()
        }
    }

    private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun attachment(uri: String, bytes: ByteArray) = SourceAttachment(
        sessionId = "session-1",
        contentUri = uri,
        persistedReadGrantAtCapture = false,
        fingerprint = SourceFingerprint(digest(bytes), bytes.size.toLong()),
        durationUs = 10_000_000,
        selectedRange = PresentationIntervalUs(PresentationTimeUs(0), PresentationTimeUs(5_000_000)),
        audioTrack = SourceAudioTrack(0, "audio/mp4a-latm", "en", 48_000, 2),
    )

    private fun manifest(attachment: SourceAttachment) = SessionManifest(
        sessionId = attachment.sessionId,
        revision = 1,
        epoch = 1,
        activeEntryRefs = emptyMap(),
        sourceBindingState = SourceBindingState.ATTACHMENT_BOUND,
        activeSourceAttachmentRef = attachment.attachmentId,
    )

    @Test fun realContentResolverReadProducesFullFingerprintWithoutInventingPersistedGrant() {
        val bytes = "synthetic-source-bytes".toByteArray()
        withSource(bytes) { _, uri ->
            val attachment = attachment(uri, bytes)
            val manifest = manifest(attachment)
            val observation = SourceContentProbe.probe(context.contentResolver, SourceProbeToken.from(manifest), attachment)
            assertEquals(SourceReadStatus.READABLE, observation.status)
            assertEquals(attachment.fingerprint, observation.fingerprint)
            assertFalse(observation.persistedReadGrantNow)
            val assessment = SourceResumeEvaluator.evaluate(manifest, attachment, observation)
            assertEquals(SourceAvailability.AVAILABLE, assessment.availability)
            assertFalse(assessment.persistedReadGrantNow)
        }
    }

    @Test fun sameUriReplacementAndEmptyReplacementFailClosedAsSourceChanged() {
        val original = "original-video-bytes".toByteArray()
        withSource(original) { file, uri ->
            val attachment = attachment(uri, original)
            val manifest = manifest(attachment)
            file.writeBytes("different-video-bytes".toByteArray())
            var observation = SourceContentProbe.probe(context.contentResolver, SourceProbeToken.from(manifest), attachment)
            assertEquals(SourceReadStatus.READABLE, observation.status)
            assertEquals(SourceAvailability.SOURCE_CHANGED,
                SourceResumeEvaluator.evaluate(manifest, attachment, observation).availability)

            file.writeBytes(ByteArray(0))
            observation = SourceContentProbe.probe(context.contentResolver, SourceProbeToken.from(manifest), attachment)
            assertEquals(SourceReadStatus.EMPTY_SOURCE, observation.status)
            assertEquals(SourceAvailability.SOURCE_CHANGED,
                SourceResumeEvaluator.evaluate(manifest, attachment, observation).availability)
        }
    }

    @Test fun deletedSourceBecomesMissingAndUnsupportedSchemeNeverOpens() {
        val original = "source".toByteArray()
        withSource(original) { file, uri ->
            val attachment = attachment(uri, original)
            val manifest = manifest(attachment)
            file.delete()
            val observation = SourceContentProbe.probe(context.contentResolver, SourceProbeToken.from(manifest), attachment)
            assertEquals(SourceReadStatus.SOURCE_MISSING, observation.status)
            assertEquals(SourceAvailability.SOURCE_MISSING,
                SourceResumeEvaluator.evaluate(manifest, attachment, observation).availability)
        }
        val unsupported = SourceContentProbe.inspect(context.contentResolver, "file:///tmp/not-allowed.mp4")
        assertEquals(SourceReadStatus.UNSUPPORTED, unsupported.status)
        assertNull(unsupported.fingerprint)
    }

    @Test fun probeTokenMustOwnAttachmentBeforeAnyObservationCanBeCreated() {
        val bytes = "source".toByteArray()
        withSource(bytes) { _, uri ->
            val attachment = attachment(uri, bytes)
            val token = SourceProbeToken.from(manifest(attachment)).copy(attachmentId = "other")
            try {
                SourceContentProbe.probe(context.contentResolver, token, attachment)
                fail("mismatched token must fail")
            } catch (_: IllegalArgumentException) { }
        }
    }
}
