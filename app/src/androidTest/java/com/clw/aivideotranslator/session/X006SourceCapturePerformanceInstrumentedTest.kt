package com.clw.aivideotranslator.session

import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.LockSupport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X006 performance harness for the production full-source identity path.
 *
 * CI/emulator results are baseline evidence only. X006 remains open until this same harness is run
 * on the target-device matrix. The assertions deliberately test architectural properties rather
 * than a device-specific speed SLA: every source byte must be copied/hashed, while Java heap must
 * not grow by approximately the full source size (the implementation is expected to stream).
 */
@RunWith(AndroidJUnit4::class)
class X006SourceCapturePerformanceInstrumentedTest {
    private val targetContext get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val testContext get() = InstrumentationRegistry.getInstrumentation().context

    @Test
    fun fullSourceCopyHashStreamsThirtyTwoMiBWithoutWholeFileHeapBuffering() {
        val source = File(
            File(targetContext.cacheDir, "p0_subtitles").apply { mkdirs() },
            "x006-${UUID.randomUUID()}.m4a",
        )
        try {
            testContext.assets.open("source_capture/tone_a.m4a").use { input ->
                source.outputStream().use { output -> input.copyTo(output) }
            }
            padWithTrailingZeros(source, TARGET_SOURCE_BYTES)
            assertEquals(TARGET_SOURCE_BYTES, source.length())

            val uri = FileProvider.getUriForFile(
                targetContext,
                "${targetContext.packageName}.subtitles",
                source,
            )

            Runtime.getRuntime().gc()
            System.runFinalization()
            val heapBefore = javaHeapUsedBytes()
            val peakHeap = AtomicLong(heapBefore)
            val sampling = AtomicBoolean(true)
            val sampler = Thread({
                while (sampling.get()) {
                    val observed = javaHeapUsedBytes()
                    peakHeap.accumulateAndGet(observed, ::maxOf)
                    LockSupport.parkNanos(HEAP_SAMPLE_INTERVAL_NS)
                }
            }, "x006-heap-sampler").apply { start() }

            val startedNs = SystemClock.elapsedRealtimeNanos()
            val captured = try {
                SourceAttachmentBuilder.capture(
                    context = targetContext,
                    sessionId = "x006-session",
                    contentUri = uri.toString(),
                ).getOrThrow()
            } finally {
                sampling.set(false)
                sampler.join(SAMPLER_JOIN_TIMEOUT_MS)
            }
            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs

            captured.use { exact ->
                assertEquals(TARGET_SOURCE_BYTES, exact.attachment.fingerprint.sizeBytes)
                assertEquals(TARGET_SOURCE_BYTES, exact.privateCopy.length())
                assertTrue(exact.attachment.fingerprint.sha256.matches(Regex("[0-9a-f]{64}")))
            }

            val elapsedMs = elapsedNs / 1_000_000.0
            val throughputMiBPerSecond =
                (TARGET_SOURCE_BYTES / MIB.toDouble()) / (elapsedNs / 1_000_000_000.0)
            val peakHeapGrowth = (peakHeap.get() - heapBefore).coerceAtLeast(0L)

            assertTrue("capture duration must be measurable", elapsedNs > 0L)
            assertTrue("copy/hash throughput must be positive", throughputMiBPerSecond > 0.0)
            assertTrue(
                "full-source capture must remain streaming; peak Java heap grew by $peakHeapGrowth bytes",
                peakHeapGrowth < MAX_STREAMING_HEAP_GROWTH_BYTES,
            )

            println(
                "X006_METRIC " +
                    "sourceBytes=$TARGET_SOURCE_BYTES " +
                    "elapsedMs=${"%.3f".format(java.util.Locale.ROOT, elapsedMs)} " +
                    "throughputMiBps=${"%.3f".format(java.util.Locale.ROOT, throughputMiBPerSecond)} " +
                    "javaHeapBeforeBytes=$heapBefore " +
                    "javaHeapPeakBytes=${peakHeap.get()} " +
                    "javaHeapGrowthBytes=$peakHeapGrowth",
            )
        } finally {
            source.delete()
        }
    }

    private fun padWithTrailingZeros(file: File, targetBytes: Long) {
        require(targetBytes > file.length())
        FileOutputStream(file, true).use { output ->
            val zeros = ByteArray(PAD_BUFFER_BYTES)
            var remaining = targetBytes - file.length()
            while (remaining > 0L) {
                val count = minOf(zeros.size.toLong(), remaining).toInt()
                output.write(zeros, 0, count)
                remaining -= count
            }
        }
    }

    private fun javaHeapUsedBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private companion object {
        const val MIB = 1024L * 1024L
        const val TARGET_SOURCE_BYTES = 32L * MIB
        const val PAD_BUFFER_BYTES = 64 * 1024
        const val HEAP_SAMPLE_INTERVAL_NS = 5_000_000L
        const val SAMPLER_JOIN_TIMEOUT_MS = 2_000L
        const val MAX_STREAMING_HEAP_GROWTH_BYTES = 24L * MIB
    }
}
