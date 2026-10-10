package com.clw.aivideotranslator.pipeline

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PcmWindowFileWriterTest {
    @Test fun splitsStreamingPcmAtExactFrameBoundariesWithoutFullVideoFile() {
        val root = Files.createTempDirectory("pcm-window-writer").toFile()
        try {
            val windows = mutableListOf<ProductionSttAudioWindow>()
            val writer = PcmWindowFileWriter(
                outputDir = root,
                sampleRateHz = 1_000,
                windowDurationUs = 60_000_000L,
                observedFirstTrackPresentationUs = 250_000L,
                onWindowFinalized = windows::add,
            )
            val pcm = ByteArray(130_000 * 2) { index -> (index % 251).toByte() }

            // Feed deliberately awkward chunk sizes to prove chunk boundaries do not affect windows.
            var cursor = 0
            while (cursor < pcm.size) {
                var length = minOf(7_998, pcm.size - cursor)
                if (length % 2 != 0) length -= 1
                writer.append(pcm, cursor, length)
                cursor += length
            }
            writer.finish()
            writer.close()

            assertEquals(3, windows.size)
            assertEquals(listOf(0L, 60_000L, 120_000L), windows.map { it.sampleStartFrame })
            assertEquals(listOf(60_000L, 120_000L, 130_000L), windows.map { it.sampleEndFrame })
            assertEquals(listOf(60_000L, 60_000L, 10_000L), windows.map { it.durationMs })
            assertTrue(windows.all { it.observedFirstTrackPresentationUs == 250_000L })
            assertTrue(root.listFiles().orEmpty().none { it.name.contains("full", ignoreCase = true) })

            val reassembled = windows.flatMap { window -> window.file.readBytes().drop(44) }.toByteArray()
            assertArrayEquals(pcm, reassembled)
            windows.forEach(::assertCanonicalWav)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun finalizesFullWindowImmediatelyBeforeFinish() {
        val root = Files.createTempDirectory("pcm-window-immediate").toFile()
        try {
            val windows = mutableListOf<ProductionSttAudioWindow>()
            PcmWindowFileWriter(
                outputDir = root,
                sampleRateHz = 1_000,
                windowDurationUs = 1_000_000L,
                observedFirstTrackPresentationUs = 0L,
                onWindowFinalized = windows::add,
            ).use { writer ->
                writer.append(ByteArray(1_000 * 2))
                assertEquals(1, windows.size)
                assertTrue(windows.single().file.isFile)
                writer.append(ByteArray(500 * 2))
                assertEquals(1, windows.size)
                writer.finish()
                assertEquals(2, windows.size)
            }
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun abortKeepsHandedOffWindowButDeletesWriterOwnedPartialTail() {
        val root = Files.createTempDirectory("pcm-window-abort").toFile()
        try {
            val windows = mutableListOf<ProductionSttAudioWindow>()
            val writer = PcmWindowFileWriter(
                outputDir = root,
                sampleRateHz = 1_000,
                windowDurationUs = 1_000_000L,
                observedFirstTrackPresentationUs = 0L,
                onWindowFinalized = windows::add,
            )
            writer.append(ByteArray(1_500 * 2))
            assertEquals(1, windows.size)
            val handedOff = windows.single().file
            writer.close() // close before finish = abort only writer-owned work.

            assertTrue(handedOff.isFile)
            assertEquals(listOf(handedOff.canonicalFile), root.listFiles().orEmpty().map { it.canonicalFile })
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonFrameAlignedPcm() {
        val root = Files.createTempDirectory("pcm-window-invalid").toFile()
        try {
            PcmWindowFileWriter(
                outputDir = root,
                sampleRateHz = 48_000,
                windowDurationUs = 60_000_000L,
                observedFirstTrackPresentationUs = 0L,
                onWindowFinalized = {},
            ).use { writer ->
                writer.append(ByteArray(3))
            }
        } finally {
            root.deleteRecursively()
        }
    }

    private fun assertCanonicalWav(window: ProductionSttAudioWindow) {
        val bytes = window.file.readBytes()
        assertTrue(bytes.size > 44)
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(bytes, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(bytes, 36, 4, Charsets.US_ASCII))
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(window.sampleRateHz, buffer.getInt(24))
        assertEquals(1, buffer.getShort(22).toInt())
        assertEquals(16, buffer.getShort(34).toInt())
        assertEquals(bytes.size - 44, buffer.getInt(40))
    }
}
