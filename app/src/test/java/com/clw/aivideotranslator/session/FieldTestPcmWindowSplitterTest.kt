package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.SttAudioProfile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldTestPcmWindowSplitterTest {
    @Test fun oneFullDecodeSplitsIntoContiguousSixtySecondWindowsWithoutChangingPcm() {
        val root = Files.createTempDirectory("pcm-window-test").toFile()
        try {
            val source = File(root, "full.wav")
            val sampleRate = 1_000
            val totalFrames = 130_000
            val pcm = ByteArray(totalFrames * 2) { index -> (index % 251).toByte() }
            writeCanonicalWav(source, sampleRate, pcm)
            val full = SttAudioProfile(
                file = source,
                sampleRateHz = sampleRate,
                channelCount = 1,
                bitsPerSample = 16,
                sourceStartUs = 0L,
                sourceEndUs = 130_000_000L,
                durationMs = 130_000L,
            )

            val windows = FieldTestPcmWindowSplitter.split(full, File(root, "windows"))

            assertEquals(3, windows.size)
            assertEquals(listOf(0L, 60_000_000L, 120_000_000L), windows.map { it.window.startUs })
            assertEquals(listOf(60_000_000L, 120_000_000L, 130_000_000L), windows.map { it.window.endUs })
            assertEquals(listOf(60_000L, 60_000L, 10_000L), windows.map { it.profile.durationMs })
            assertTrue(windows.all { it.profile.file.length() > 44L })

            val joined = windows.flatMap { window ->
                window.profile.file.readBytes().drop(44)
            }.toByteArray()
            assertArrayEquals(pcm, joined)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun nonZeroAudioOriginIsPreservedAcrossEveryWindow() {
        val root = Files.createTempDirectory("pcm-origin-test").toFile()
        try {
            val source = File(root, "full.wav")
            val sampleRate = 1_000
            val pcm = ByteArray(61_000 * 2)
            writeCanonicalWav(source, sampleRate, pcm)
            val full = SttAudioProfile(
                file = source,
                sampleRateHz = sampleRate,
                channelCount = 1,
                bitsPerSample = 16,
                sourceStartUs = 250_000L,
                sourceEndUs = 61_250_000L,
                durationMs = 61_000L,
            )

            val windows = FieldTestPcmWindowSplitter.split(full, File(root, "windows"))

            assertEquals(2, windows.size)
            assertEquals(250_000L, windows[0].window.startUs)
            assertEquals(60_250_000L, windows[0].window.endUs)
            assertEquals(60_250_000L, windows[1].window.startUs)
            assertEquals(61_250_000L, windows[1].window.endUs)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun codecPaddingPastVideoEndIsTrimmedWithoutShiftingAudioOrigin() {
        val root = Files.createTempDirectory("pcm-padding-test").toFile()
        try {
            val source = File(root, "full.wav")
            val sampleRate = 1_000
            val pcm = ByteArray(61_500 * 2) { index -> (index % 199).toByte() }
            writeCanonicalWav(source, sampleRate, pcm)
            val full = SttAudioProfile(
                file = source,
                sampleRateHz = sampleRate,
                channelCount = 1,
                bitsPerSample = 16,
                sourceStartUs = 250_000L,
                sourceEndUs = 61_750_000L,
                durationMs = 61_500L,
            )

            val windows = FieldTestPcmWindowSplitter.split(
                fullProfile = full,
                outputDir = File(root, "windows"),
                sourceEndLimitUs = 61_250_000L,
            )

            assertEquals(2, windows.size)
            assertEquals(250_000L, windows.first().window.startUs)
            assertEquals(61_250_000L, windows.last().window.endUs)
            assertEquals(listOf(60_000L, 1_000L), windows.map { it.profile.durationMs })
            val joined = windows.flatMap { it.profile.file.readBytes().drop(44) }.toByteArray()
            assertArrayEquals(pcm.copyOf(61_000 * 2), joined)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsProfileWhoseTimelineDoesNotMatchPcmFrames() {
        val root = Files.createTempDirectory("pcm-invalid-test").toFile()
        try {
            val source = File(root, "full.wav")
            writeCanonicalWav(source, 1_000, ByteArray(1_000 * 2))
            FieldTestPcmWindowSplitter.split(
                SttAudioProfile(
                    file = source,
                    sampleRateHz = 1_000,
                    channelCount = 1,
                    bitsPerSample = 16,
                    sourceStartUs = 0L,
                    sourceEndUs = 999_000L,
                    durationMs = 1_000L,
                ),
                File(root, "windows"),
            )
        } finally {
            root.deleteRecursively()
        }
    }

    private fun writeCanonicalWav(file: File, sampleRateHz: Int, pcm: ByteArray) {
        require(pcm.isNotEmpty() && pcm.size % 2 == 0)
        val dataBytes = pcm.size
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes + 36)
            put("WAVE".toByteArray(Charsets.US_ASCII))
            put("fmt ".toByteArray(Charsets.US_ASCII))
            putInt(16)
            putShort(1.toShort())
            putShort(1.toShort())
            putInt(sampleRateHz)
            putInt(sampleRateHz * 2)
            putShort(2.toShort())
            putShort(16.toShort())
            put("data".toByteArray(Charsets.US_ASCII))
            putInt(dataBytes)
        }.array()
        file.outputStream().use { output ->
            output.write(header)
            output.write(pcm)
        }
    }
}
