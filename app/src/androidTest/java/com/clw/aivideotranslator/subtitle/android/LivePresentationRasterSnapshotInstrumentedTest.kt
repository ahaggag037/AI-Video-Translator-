package com.clw.aivideotranslator.subtitle.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.ArabicSubtitleCue
import com.clw.aivideotranslator.VideoMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LivePresentationRasterSnapshotInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun snapshotFreezesExactCueTextUprightGeometryAndAudioTruth() {
        val metadata = VideoMetadata(
            displayName = "rotated.mp4",
            durationMs = 5_000L,
            width = 240,
            height = 426,
            sizeBytes = 1_000L,
            rotationDegrees = 90,
            hasAudio = true,
        )
        val cues = listOf(
            ArabicSubtitleCue("u0001", 500L, 1_500L, "مرحبا بالعالم"),
            ArabicSubtitleCue("u0002", 2_000L, 3_000L, "هذه ترجمة ثانية"),
        )

        val snapshot = LivePresentationRasterSnapshotFactory.build(context, metadata, cues).getOrThrow()

        assertEquals(426, snapshot.geometry.uprightWidthPx)
        assertEquals(240, snapshot.geometry.uprightHeightPx)
        assertTrue(snapshot.sourceHasAudio)
        assertEquals(2, snapshot.timeline.cues.size)
        assertEquals(cues.map { it.text }, snapshot.timeline.cues.map { it.request.descriptor.text })
        assertEquals(500_000L, snapshot.timeline.cues.first().startUs)
        assertEquals(3_000_000L, snapshot.timeline.cues.last().endUs)
        snapshot.timeline.cues.forEach { cue ->
            assertEquals(snapshot.geometry, cue.request.geometry)
            assertEquals(cue.request.descriptor, cue.request.descriptor.copy())
        }
    }

    @Test
    fun missingVideoGeometryFailsClosedBeforePublishingSnapshot() {
        val metadata = VideoMetadata(
            displayName = "unknown.mp4",
            durationMs = 5_000L,
            width = null,
            height = null,
            sizeBytes = null,
        )
        val result = LivePresentationRasterSnapshotFactory.build(
            context,
            metadata,
            listOf(ArabicSubtitleCue("u0001", 0L, 1_000L, "ترجمة")),
        )
        assertTrue(result.isFailure)
    }
}
