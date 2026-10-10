package com.clw.aivideotranslator.session

import android.os.Build
import android.os.SystemClock
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * X006 full-source copy/hash qualification.
 *
 * The production capture boundary must read the complete provider stream once, copy and SHA-256 it
 * in the same streaming pass, and retain only the private operation-owned file rather than the full
 * source in Java heap. This test deliberately uses 32 MiB, much larger than the production 64 KiB
 * copy buffer. The heap discriminator is half the source size: it is not a device speed SLA; it is a
 * falsifier for accidentally retaining a source-sized Java byte array.
 */
@RunWith(AndroidJUnit4::class)
class X006SourceCapturePerformanceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val captureDir get() = File(context.cacheDir, "p0_source_capture")

    @Test
    fun fullSourceCopyHashStreamsThirtyTwoMiBDeterministicallyAndReleasesPrivateCopy() {
        val source = makePaddedMediaSource("x006-source-${System.nanoTime()}.m4a", TARGET_SOURCE_BYTES)
        val baselineFiles = captureFileNames()
        try {
            val expectedSha = sha256Streaming(source)
            Runtime.getRuntime().gc()
            val heapBefore = usedJavaHeapBytes()
            val peakHeap = AtomicLong(heapBefore)
            val sampling = AtomicBoolean(true)
            val sampler = Thread({
                while (sampling.get()) {
                    val used = usedJavaHeapBytes()
                    peakHeap.updateAndGet { previous -> maxOf(previous, used) }
                    SystemClock.sleep(2L)
                }
            }, "x006-heap-sampler").apply {
                isDaemon = true
                start()
            }

            val startedNs = SystemClock.elapsedRealtimeNanos()
            val captured = try {
                SourceAttachmentBuilder.capture(
                    context = context,
                    sessionId = "x006-source-capture-a",
                    contentUri = sourceUri(source),
                ).getOrThrow()
            } finally {
                sampling.set(false)
                sampler.join(2_000L)
            }
            val elapsedNs = SystemClock.elapsedRealtimeNanos() - startedNs

            try {
                assertEquals(TARGET_SOURCE_BYTES, captured.attachment.fingerprint.sizeBytes)
                assertEquals(expectedSha, captured.attachment.fingerprint.sha256)
                val liveFiles = captureFileNames() - baselineFiles
                assertEquals("one operation-owned private source copy must be live", 1, liveFiles.size)

                val heapPeak = peakHeap.get()
                val heapGrowth = (heapPeak - heapBefore).coerceAtLeast(0L)
                assertTrue(
                    "Java heap growth $heapGrowth suggests source-sized buffering for $TARGET_SOURCE_BYTES bytes",
                    heapGrowth < TARGET_SOURCE_BYTES / 2L,
                )

                val elapsedMs = elapsedNs / 1_000_000.0
                val throughputMiBps = (TARGET_SOURCE_BYTES.toDouble() / MIB) / (elapsedNs / 1_000_000_000.0)
                println(
                    "X006_METRIC kind=source_capture " +
                        "manufacturer=${metricToken(Build.MANUFACTURER)} model=${metricToken(Build.MODEL)} " +
                        "api=${Build.VERSION.SDK_INT} sourceBytes=$TARGET_SOURCE_BYTES " +
                        "elapsedMs=$elapsedMs throughputMiBps=$throughputMiBps " +
                        "javaHeapBeforeBytes=$heapBefore javaHeapPeakBytes=$heapPeak " +
                        "javaHeapGrowthBytes=$heapGrowth",
                )
            } finally {
                captured.close()
            }
            assertEquals("closing capture must release its private source copy", baselineFiles, captureFileNames())

            // build() owns and closes the same private copy internally. Repeating identical bytes
            // must produce exactly the same full-source fingerprint without leaving a retained file.
            val second = SourceAttachmentBuilder.build(
                context = context,
                sessionId = "x006-source-capture-b",
                contentUri = sourceUri(source),
            ).getOrThrow()
            assertEquals(TARGET_SOURCE_BYTES, second.fingerprint.sizeBytes)
            assertEquals(expectedSha, second.fingerprint.sha256)
            assertEquals(baselineFiles, captureFileNames())
        } finally {
            source.delete()
        }
    }

    @Test
    fun failedEmptyAndMalformedCapturesLeaveNoPartialPrivateFiles() {
        val baselineFiles = captureFileNames()

        val missing = SourceAttachmentBuilder.capture(
            context = context,
            sessionId = "x006-missing",
            contentUri = "content://com.clw.aivideotranslator.x006.missing/source",
        )
        assertTrue("missing provider/source must fail", missing.isFailure)
        assertEquals("failed open must not retain a temp copy", baselineFiles, captureFileNames())

        val empty = sourceFile("x006-empty-${System.nanoTime()}.m4a")
        try {
            assertTrue(empty.createNewFile())
            val emptyResult = SourceAttachmentBuilder.capture(
                context = context,
                sessionId = "x006-empty",
                contentUri = sourceUri(empty),
            )
            assertTrue("empty source must fail closed", emptyResult.isFailure)
            val emptyError = emptyResult.exceptionOrNull()
            assertTrue(emptyError is SourceCaptureException)
            assertEquals(SourceReadStatus.EMPTY_SOURCE, (emptyError as SourceCaptureException).status)
            assertEquals("empty copy failure must clean the temp file", baselineFiles, captureFileNames())
        } finally {
            empty.delete()
        }

        val malformed = sourceFile("x006-malformed-${System.nanoTime()}.bin")
        try {
            FileOutputStream(malformed).use { out ->
                repeat(16) { out.write(ByteArray(64 * 1024)) }
            }
            val malformedResult = SourceAttachmentBuilder.capture(
                context = context,
                sessionId = "x006-malformed",
                contentUri = sourceUri(malformed),
            )
            assertTrue("non-media bytes must fail after copy/hash", malformedResult.isFailure)
            assertEquals("post-copy parse failure must clean the private temp file", baselineFiles, captureFileNames())
        } finally {
            malformed.delete()
        }
    }

    private fun makePaddedMediaSource(name: String, targetBytes: Long): File {
        val target = sourceFile(name)
        instrumentation.context.assets.open("source_capture/tone_a.m4a").use { input ->
            FileOutputStream(target).use { out -> input.copyTo(out, 64 * 1024) }
        }
        require(target.length() <= targetBytes)
        FileOutputStream(target, true).use { out ->
            val buffer = ByteArray(64 * 1024)
            var remaining = targetBytes - target.length()
            while (remaining > 0L) {
                val count = minOf(buffer.size.toLong(), remaining).toInt()
                out.write(buffer, 0, count)
                remaining -= count
            }
        }
        assertEquals(targetBytes, target.length())
        return target
    }

    private fun sourceFile(name: String): File =
        File(context.cacheDir, "p0_subtitles").apply { mkdirs() }.resolve(name)

    private fun sourceUri(file: File): String = FileProvider.getUriForFile(
        context,
        "${context.packageName}.subtitles",
        file,
    ).toString()

    private fun sha256Streaming(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
    }

    private fun captureFileNames(): Set<String> =
        captureDir.listFiles()?.mapTo(linkedSetOf()) { it.name }.orEmpty()

    private fun usedJavaHeapBytes(): Long {
        val runtime = Runtime.getRuntime()
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun metricToken(value: String): String = value.trim().replace(Regex("\\s+"), "_")

    companion object {
        private const val MIB = 1024.0 * 1024.0
        private const val TARGET_SOURCE_BYTES = 32L * 1024L * 1024L
    }
}
