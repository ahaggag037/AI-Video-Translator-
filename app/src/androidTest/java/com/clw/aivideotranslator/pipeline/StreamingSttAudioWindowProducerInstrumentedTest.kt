package com.clw.aivideotranslator.pipeline

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.session.RetainedSessionSource
import com.clw.aivideotranslator.session.SourceAttachment
import com.clw.aivideotranslator.session.SourceAudioTrack
import com.clw.aivideotranslator.session.SourceFingerprint
import java.io.File
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StreamingSttAudioWindowProducerInstrumentedTest {
    @Test fun syntheticAacDecodesIntoEarlyBoundedWindowsWithoutFullVideoWav() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val root = File(context.cacheDir, "x006-streaming-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
        try {
            val retainedFile = File(root, "retained-source.m4a")
            instrumentation.context.assets.open("source_capture/tone_a.m4a").use { input ->
                retainedFile.outputStream().use { output -> input.copyTo(output) }
            }
            assertEquals(9_307L, retainedFile.length())

            val attachment = SourceAttachment(
                sessionId = "x006-streaming-test",
                contentUri = "content://fixture.example/source/tone-a",
                persistedReadGrantAtCapture = false,
                fingerprint = SourceFingerprint(
                    sha256 = "08780889ebde4b79cef3c6fe217f5b7de65386ca502abbaa88a7008f6095f6c7",
                    sizeBytes = retainedFile.length(),
                ),
                durationUs = 1_021_333L,
                selectedRange = PresentationIntervalUs(
                    PresentationTimeUs(0L),
                    PresentationTimeUs(1_021_333L),
                ),
                audioTrack = SourceAudioTrack(
                    containerIndex = 0,
                    mime = "audio/mp4a-latm",
                    language = null,
                    sampleRateHz = 48_000,
                    channelCount = 1,
                ),
            )
            val retained = RetainedSessionSource(attachment, retainedFile)
            val outputDir = File(root, "windows")
            val windows = mutableListOf<ProductionSttAudioWindow>()
            var firstWindowCallbackAtMs: Long? = null
            val startedAtMs = android.os.SystemClock.elapsedRealtime()

            val summary = StreamingSttAudioWindowProducer.produce(
                retainedSource = retained,
                outputDir = outputDir,
                windowDurationUs = 250_000L,
                onWindowFinalized = { window ->
                    if (firstWindowCallbackAtMs == null) {
                        firstWindowCallbackAtMs = android.os.SystemClock.elapsedRealtime()
                    }
                    windows += window
                },
            ).getOrThrow()
            val finishedAtMs = android.os.SystemClock.elapsedRealtime()

            assertTrue(windows.size >= 4)
            assertEquals(windows.indices.toList(), windows.map { it.index })
            assertEquals(0L, windows.first().sampleStartFrame)
            windows.zipWithNext().forEach { (left, right) ->
                assertEquals(left.sampleEndFrame, right.sampleStartFrame)
            }
            assertEquals(summary.decodedFrames, windows.sumOf { it.frameCount })
            assertTrue(summary.decodedFrames > windows.first().sampleEndFrame)
            assertTrue(firstWindowCallbackAtMs != null)
            assertTrue(firstWindowCallbackAtMs!! <= finishedAtMs)
            assertTrue(outputDir.listFiles().orEmpty().none { it.name.contains("full", ignoreCase = true) })
            assertTrue(windows.all { it.file.length() <= 48_000L * 2L / 4L + 44L })

            println(
                "X006_STREAMING_STT first_window_ms=${firstWindowCallbackAtMs!! - startedAtMs} " +
                    "total_decode_ms=${finishedAtMs - startedAtMs} windows=${windows.size} " +
                    "frames=${summary.decodedFrames} sample_rate=${summary.outputSampleRateHz}",
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
