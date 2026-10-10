package com.clw.aivideotranslator.session

import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SourceCaptureProgressInstrumentedTest {
    @Test fun captureProgressIsMonotonicAndFinishesAtFingerprintSize() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val source = File(context.cacheDir, "x006-progress-${System.nanoTime()}.m4a")
        try {
            instrumentation.context.assets.open("source_capture/tone_a.m4a").use { input ->
                FileOutputStream(source).use { output -> input.copyTo(output) }
            }
            FileOutputStream(source, true).use { output ->
                val block = ByteArray(64 * 1024)
                var remaining = TARGET_BYTES - source.length()
                while (remaining > 0L) {
                    val count = minOf(block.size.toLong(), remaining).toInt()
                    output.write(block, 0, count)
                    remaining -= count
                }
            }
            assertEquals(TARGET_BYTES, source.length())
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.subtitles",
                source,
            )
            val observed = mutableListOf<SourceCaptureProgress>()

            val attachment = SourceAttachmentBuilder.build(
                context = context,
                sessionId = "x006-source-progress",
                contentUri = uri.toString(),
                onProgress = observed::add,
            ).getOrThrow()

            assertTrue("capture must publish at least a final progress event", observed.isNotEmpty())
            observed.zipWithNext().forEach { (left, right) ->
                assertTrue("copied bytes must be monotonic", right.copiedBytes >= left.copiedBytes)
                assertTrue("elapsed time must be monotonic", right.elapsedMs >= left.elapsedMs)
                assertTrue("throughput must remain non-negative", right.bytesPerSecond >= 0.0)
            }
            val final = observed.last()
            assertEquals(attachment.fingerprint.sizeBytes, final.copiedBytes)
            assertEquals(TARGET_BYTES, final.copiedBytes)
            println(
                "X006_SOURCE_PROGRESS callbacks=${observed.size} finalBytes=${final.copiedBytes} " +
                    "elapsedMs=${final.elapsedMs} bytesPerSecond=${final.bytesPerSecond}",
            )
        } finally {
            source.delete()
        }
    }

    companion object {
        private const val TARGET_BYTES = 8L * 1024L * 1024L
    }
}
