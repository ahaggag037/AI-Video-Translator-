package com.clw.aivideotranslator.source

import com.clw.aivideotranslator.VideoMetadata
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CandidateProbeClassifierTest {
    @Test fun audioVideoBecomesReadyCandidate() {
        val metadata = VideoMetadata(
            displayName = "clip.mp4",
            durationMs = 62_000L,
            width = 1920,
            height = 1080,
            sizeBytes = 10_000_000L,
            hasAudio = true,
        )

        assertEquals(
            CandidateProbeResult.Ready("clip.mp4"),
            CandidateProbeClassifier.fromMetadata(metadata),
        )
    }

    @Test fun videoWithoutAudioIsRejectedBeforeDurableActivation() {
        val metadata = VideoMetadata(
            displayName = "silent.mp4",
            durationMs = 1_000L,
            width = 1280,
            height = 720,
            sizeBytes = 1_000L,
            hasAudio = false,
        )

        assertEquals(CandidateProbeResult.NoAudio, CandidateProbeClassifier.fromMetadata(metadata))
    }

    @Test fun accessFailureIsTypedWithoutLeakingProviderDetails() {
        val result = CandidateProbeClassifier.fromFailure(SecurityException("content://private/video"))
        assertEquals(CandidateProbeResult.AccessLimited, result)
    }

    @Test fun invalidMetadataFailureIsTypedInvalid() {
        val result = CandidateProbeClassifier.fromFailure(IllegalArgumentException("bad metadata"))
        assertEquals(CandidateProbeResult.Invalid, result)
    }

    @Test fun unknownProbeFailureUsesSafeUserMessage() {
        val result = CandidateProbeClassifier.fromFailure(IOException("content://private/video"))
        assertTrue(result is CandidateProbeResult.Failed)
        val message = (result as CandidateProbeResult.Failed).message
        assertEquals("تعذر فحص الفيديو.", message)
        assertFalse(message.contains("content://"))
    }
}
