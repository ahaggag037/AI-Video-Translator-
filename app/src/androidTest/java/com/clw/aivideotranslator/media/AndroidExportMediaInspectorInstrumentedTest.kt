package com.clw.aivideotranslator.media

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.UUID
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AndroidExportMediaInspectorInstrumentedTest {
    private lateinit var root: File

    @Before
    fun setUp() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        root = File(targetContext.cacheDir, "x004-export-inspector-${UUID.randomUUID()}").apply {
            check(mkdirs() || isDirectory)
        }
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun realDeviceMediaExtractorPublishesAudioSampleAndPtsEvidence() {
        val testContext = InstrumentationRegistry.getInstrumentation().context
        val fixture = File(root, "tone_a.m4a")
        testContext.assets.open("source_capture/tone_a.m4a").use { input ->
            fixture.outputStream().use { output -> input.copyTo(output) }
        }

        val observation = AndroidExportMediaInspector.inspect(fixture)

        assertTrue(observation.readable)
        assertEquals(fixture.length(), observation.sizeBytes)
        assertTrue((observation.durationUs ?: 0L) > 0L)
        val audioTrack = observation.tracks.single { it.mime.startsWith("audio/") }
        assertTrue((audioTrack.sampleCount ?: 0L) > 0L)
        assertNotNull(audioTrack.firstPresentationTimeUs)
        assertNotNull(audioTrack.lastPresentationTimeUs)
        assertTrue(audioTrack.firstPresentationTimeUs!! >= 0L)
        assertTrue(audioTrack.lastPresentationTimeUs!! >= audioTrack.firstPresentationTimeUs!!)
        assertTrue(observation.tracks.none { it.mime.startsWith("video/") })
    }

    @Test
    fun missingFileFailsClosedWithoutInventingTracks() {
        val observation = AndroidExportMediaInspector.inspect(File(root, "missing.mp4"))

        assertFalse(observation.readable)
        assertEquals(0L, observation.sizeBytes)
        assertEquals(null, observation.durationUs)
        assertTrue(observation.tracks.isEmpty())
    }

    @Test
    fun malformedMediaFailsClosedWithoutPartialEvidence() {
        val malformed = File(root, "malformed.mp4").apply {
            writeText("not-a-media-container", Charsets.UTF_8)
        }

        val observation = AndroidExportMediaInspector.inspect(malformed)

        assertFalse(observation.readable)
        assertEquals(malformed.length(), observation.sizeBytes)
        assertEquals(null, observation.durationUs)
        assertTrue(observation.tracks.isEmpty())
    }
}
