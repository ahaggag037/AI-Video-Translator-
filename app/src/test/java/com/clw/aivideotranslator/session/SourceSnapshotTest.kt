package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SourceSnapshotTest {
    private fun unverified() = SourceSnapshot(
        sessionId = "session-1",
        sourceAttachmentId = "source-${"a".repeat(64)}",
        transcript = "Hello world.",
        words = listOf(
            SourceSnapshotWord(0, "Hello", 0.9),
            SourceSnapshotWord(1, "world.", null),
        ),
        pcmSample = SourcePcmSampleIdentity("b".repeat(64), 96_044, 48_000, 48_000, 1, 16),
        stt = SourceSttProvenance("nvidia", "parakeet-ctc-1.1b-en-us", "p0-stt-v1", "legacy-parser-v1", "c".repeat(64), 200),
        clock = SourceClockProvenance(500_000, ClockVerificationStatus.UNVERIFIED),
    )

    private fun rejected(block: () -> Unit) {
        try { block(); fail("must fail closed") } catch (_: IllegalArgumentException) { }
    }

    @Test fun unverifiedSnapshotRoundTripsWithoutPersistingInterpretedWordTimes() {
        val value = unverified()
        val decoded = SourceSnapshotCodec.decode(SourceSnapshotCodec.encode(value))
        assertEquals(value, decoded)
        assertEquals(value.sourceTextHash, decoded.sourceTextHash)
        assertTrue(isSafeId(value.snapshotId))
        assertTrue(decoded.words.all { it.audioInterval == null })
        assertEquals(1_000_000L, decoded.pcmSample.derivedPcmDurationUs)
    }

    @Test fun verifiedAffineSnapshotRequiresAllWordIntervalsAndEvidenceProfile() {
        val verified = unverified().copy(
            words = listOf(
                SourceSnapshotWord(0, "Hello", 0.9, AudioIntervalUs(AudioTimeUs(0), AudioTimeUs(400_000))),
                SourceSnapshotWord(1, "world.", null, AudioIntervalUs(AudioTimeUs(500_000), AudioTimeUs(900_000))),
            ),
            clock = SourceClockProvenance(500_000, ClockVerificationStatus.VERIFIED_AFFINE, 1_000, "x001-fixture-v1"),
        )
        assertEquals(verified, SourceSnapshotCodec.decode(SourceSnapshotCodec.encode(verified)))
        rejected { verified.copy(clock = SourceClockProvenance(500_000, ClockVerificationStatus.UNVERIFIED)) }
        rejected { unverified().copy(clock = SourceClockProvenance(500_000, ClockVerificationStatus.VERIFIED_AFFINE, 1_000, "x001")) }
        rejected { SourceClockProvenance(500_000, ClockVerificationStatus.VERIFIED_AFFINE, null, "x001") }
    }

    @Test fun everyDurableSemanticOrProvenanceFieldChangesSnapshotIdentity() {
        val original = unverified()
        val variants = listOf(
            original.copy(sessionId = "session-2"),
            original.copy(sourceAttachmentId = "source-${"d".repeat(64)}"),
            original.copy(transcript = "Hello changed."),
            original.copy(words = original.words.mapIndexed { i, w -> if (i == 0) w.copy(rawText = "Hi") else w }),
            original.copy(words = original.words.mapIndexed { i, w -> if (i == 0) w.copy(confidence = 0.8) else w }),
            original.copy(pcmSample = original.pcmSample.copy(wavSha256 = "d".repeat(64))),
            original.copy(pcmSample = original.pcmSample.copy(pcmFrameCount = 47_999)),
            original.copy(stt = original.stt.copy(parserVersion = "parser-v2")),
            original.copy(stt = original.stt.copy(rawResponseSha256 = "d".repeat(64))),
            original.copy(clock = original.clock.copy(observedPresentationOriginUs = 600_000)),
        )
        assertEquals(variants.size, variants.map { it.snapshotId }.toSet().size)
        variants.forEach { assertNotEquals(original.snapshotId, it.snapshotId) }
    }

    @Test fun identityAndSourceTextHashDetectValidJsonMutation() {
        val json = JSONObject(SourceSnapshotCodec.encode(unverified()))
        rejected { SourceSnapshotCodec.decode(JSONObject(json.toString()).put("transcript", "tampered").toString()) }
        rejected { SourceSnapshotCodec.decode(JSONObject(json.toString()).put("sourceTextHash", "d".repeat(64)).toString()) }
        val words = JSONObject(json.toString()).getJSONArray("words")
        words.getJSONObject(0).put("rawText", "tampered")
        rejected { SourceSnapshotCodec.decode(JSONObject(json.toString()).put("words", words).toString()) }
    }

    @Test fun codecRejectsSchemaDriftAndNumericCoercion() {
        val json = SourceSnapshotCodec.encode(unverified())
        listOf(
            JSONObject(json).put("schemaVersion", 2),
            JSONObject(json).put("unexpected", true),
            JSONObject(json).put("pcmSample", JSONObject(JSONObject(json).getJSONObject("pcmSample").toString()).put("pcmFrameCount", "48000")),
            JSONObject(json).apply { remove("stt") },
        ).forEach { rejected { SourceSnapshotCodec.decode(it.toString()) } }
    }

    @Test fun unverifiedClockCannotLaunderLegacyNormalizedOffsets() {
        rejected {
            unverified().copy(words = listOf(
                SourceSnapshotWord(0, "Hello", 0.9, AudioIntervalUs(AudioTimeUs(0), AudioTimeUs(1_000))),
                SourceSnapshotWord(1, "world.", null, AudioIntervalUs(AudioTimeUs(2_000), AudioTimeUs(3_000))),
            ))
        }
    }
}
