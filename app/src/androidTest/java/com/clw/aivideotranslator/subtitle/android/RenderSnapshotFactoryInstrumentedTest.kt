package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.ArabicSubtitleCue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RenderSnapshotFactoryInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val font by lazy { SubtitleFonts.loadExperimentCandidate(context) }
    private val environment =
        "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"
    private val geometry = FrameGeometry(1080, 1920)
    private val factory = RenderSnapshotFactory()

    @Test
    fun samePresentationRevisionBuildsOneDeterministicDescriptorTimeline() {
        val cues = listOf(
            ArabicSubtitleCue("u0001", 500L, 1_500L, "مرحبًا بك"),
            ArabicSubtitleCue("u0002", 2_000L, 3_250L, "هذه ترجمة ثابتة"),
        )

        val first = factory.build(cues, geometry, font, environment)
        val second = factory.build(cues, geometry, font, environment)
        assertTrue(first is RenderSnapshotBuildResult.Ready)
        assertTrue(second is RenderSnapshotBuildResult.Ready)
        first as RenderSnapshotBuildResult.Ready
        second as RenderSnapshotBuildResult.Ready

        assertEquals(first.snapshot.snapshotId, second.snapshot.snapshotId)
        assertEquals(first.snapshot.cues, second.snapshot.cues)
        assertEquals(font.profile, first.snapshot.fontProfile)
        assertEquals(environment, first.snapshot.rendererEnvironment)

        val firstCue = first.snapshot.timeline.locate(500_000L).active
        val gap = first.snapshot.timeline.locate(1_750_000L).active
        val secondCue = first.snapshot.timeline.locate(2_000_000L).active
        assertEquals("u0001", firstCue?.cueId)
        assertEquals(null, gap)
        assertEquals("u0002", secondCue?.cueId)
        assertEquals(500_000L, firstCue?.startUs)
        assertEquals(1_500_000L, firstCue?.endUs)
        assertEquals("مرحبًا بك", firstCue?.request?.descriptor?.text)
        assertTrue(firstCue?.request?.requestId?.startsWith(first.snapshot.snapshotId) == true)
    }

    @Test
    fun anySemanticCueChangeCreatesNewSnapshotAndRasterIdentity() {
        val original = ready(
            listOf(ArabicSubtitleCue("u0001", 0L, 1_000L, "النص الأول"))
        )
        val edited = ready(
            listOf(ArabicSubtitleCue("u0001", 0L, 1_000L, "النص المعدل"))
        )

        assertNotEquals(original.snapshotId, edited.snapshotId)
        assertNotEquals(original.cues.single().request.requestId, edited.cues.single().request.requestId)
        assertEquals("النص الأول", original.cues.single().request.descriptor.text)
        assertEquals("النص المعدل", edited.cues.single().request.descriptor.text)
    }

    @Test
    fun layoutFailureRejectsWholeSnapshotInsteadOfPublishingPartialRasterTruth() {
        val result = factory.build(
            presentationCues = listOf(
                ArabicSubtitleCue("u0001", 0L, 1_000L, "قصير"),
                ArabicSubtitleCue("u0002", 1_100L, 2_000L, "س".repeat(4_000)),
            ),
            geometry = FrameGeometry(640, 360),
            font = font,
            rendererEnvironment = environment,
        )

        assertTrue("overflow/review must reject snapshot atomically: $result", result is RenderSnapshotBuildResult.Rejected)
        result as RenderSnapshotBuildResult.Rejected
        assertEquals("u0002", result.cueId)
        assertTrue(result.status != SubtitleLayoutStatus.FITS)
    }

    @Test(expected = IllegalArgumentException::class)
    fun overlappingPresentationCuesAreRejectedBeforeLayout() {
        factory.build(
            presentationCues = listOf(
                ArabicSubtitleCue("u0001", 0L, 1_500L, "أول"),
                ArabicSubtitleCue("u0002", 1_000L, 2_000L, "ثان"),
            ),
            geometry = geometry,
            font = font,
            rendererEnvironment = environment,
        )
    }

    private fun ready(cues: List<ArabicSubtitleCue>): RenderSnapshot {
        val result = factory.build(cues, geometry, font, environment)
        assertTrue(result is RenderSnapshotBuildResult.Ready)
        return (result as RenderSnapshotBuildResult.Ready).snapshot
    }
}
