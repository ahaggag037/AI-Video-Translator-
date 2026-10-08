package com.clw.aivideotranslator.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportValidatorTest {
    private fun observation(
        sizeBytes: Long = 1_000_000L,
        durationUs: Long? = 5_000_000L,
        readable: Boolean = true,
        tracks: List<MediaTrackObservation> = listOf(
            MediaTrackObservation(ExportValidator.VIDEO_H264_MIME),
            MediaTrackObservation(ExportValidator.AUDIO_AAC_MIME),
        ),
    ) = ExportMediaObservation(sizeBytes, durationUs, readable, tracks)

    @Test fun supportedH264AacOutputWithinN24DurationToleranceIsAccepted() {
        val result = ExportValidator.evaluate(
            observation = observation(durationUs = 5_250_000L),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = true,
        )
        assertTrue(result.isAccepted)
        assertTrue(result.failures.isEmpty())
    }

    @Test fun durationBeyondN24ToleranceFailsClosed() {
        val result = ExportValidator.evaluate(
            observation = observation(durationUs = 5_250_001L),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = true,
        )
        assertEquals(setOf(ExportMediaFailure.DURATION_MISMATCH), result.failures)
    }

    @Test fun sourceAudioRequiresAnAacOutputTrack() {
        val result = ExportValidator.evaluate(
            observation = observation(tracks = listOf(MediaTrackObservation(ExportValidator.VIDEO_H264_MIME))),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = true,
        )
        assertTrue(ExportMediaFailure.AUDIO_TRACK_MISSING in result.failures)
        assertFalse(result.isAccepted)
    }

    @Test fun silentSourceDoesNotInventAnAudioRequirement() {
        val result = ExportValidator.evaluate(
            observation = observation(tracks = listOf(MediaTrackObservation(ExportValidator.VIDEO_H264_MIME))),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = false,
        )
        assertTrue(result.isAccepted)
    }

    @Test fun nonH264OrNonAacTracksAreNotAcceptedAsSupportedProfile() {
        val result = ExportValidator.evaluate(
            observation = observation(
                tracks = listOf(
                    MediaTrackObservation("video/hevc"),
                    MediaTrackObservation("audio/opus"),
                )
            ),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = true,
        )
        assertTrue(ExportMediaFailure.UNSUPPORTED_VIDEO_CODEC in result.failures)
        assertTrue(ExportMediaFailure.UNSUPPORTED_AUDIO_CODEC in result.failures)
    }

    @Test fun emptyUnreadableOutputWithNoVideoFailsForEachObservableReason() {
        val result = ExportValidator.evaluate(
            observation = observation(
                sizeBytes = 0L,
                durationUs = null,
                readable = false,
                tracks = emptyList(),
            ),
            expectedDurationUs = 5_000_000L,
            sourceHasAudio = false,
        )
        assertTrue(ExportMediaFailure.EMPTY_OUTPUT in result.failures)
        assertTrue(ExportMediaFailure.UNREADABLE_OUTPUT in result.failures)
        assertTrue(ExportMediaFailure.DURATION_MISMATCH in result.failures)
        assertTrue(ExportMediaFailure.VIDEO_TRACK_MISSING in result.failures)
    }

    @Test fun trackObservationRejectsBackwardsPtsWithoutRepairingIt() {
        try {
            MediaTrackObservation(
                mime = ExportValidator.AUDIO_AAC_MIME,
                firstPresentationTimeUs = 2_000L,
                lastPresentationTimeUs = 1_000L,
            )
            throw AssertionError("backwards PTS must not be clamped")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
