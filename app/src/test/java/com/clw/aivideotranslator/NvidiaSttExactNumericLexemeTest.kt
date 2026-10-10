package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class NvidiaSttExactNumericLexemeTest {
    private val sampleSha256 = "d".repeat(64)

    private fun map(body: String) = run {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(body)
        val transport = NvidiaSttTransportObservation(
            requestProfile = NvidiaSttWireContract.PROFILE,
            result = NvidiaSttClient.parseResponseWithoutTimingAuthority(body),
            parserVersion = NvidiaSttParserContract.ID,
            rawResponseSha256 = evidence.rawResponseSha256,
            sampleSha256 = sampleSha256,
        )
        NvidiaSttAuthoritativeTimingMapper.map(
            evidence = evidence,
            transport = transport,
            preparedSampleSha256 = sampleSha256,
            contract = NvidiaSttTimingContract(
                requestProfile = NvidiaSttWireContract.PROFILE,
                schema = NvidiaWordTimingSchema.ROOT_WORDS,
                schemaPath = "$.words",
                startField = "start_time",
                endField = "end_time",
                valueType = NvidiaRawJsonValueType.NUMBER,
                unit = NvidiaSttTimingContract.OffsetUnit.SECONDS,
                origin = NvidiaSttTimingContract.OffsetOrigin.UPLOADED_AUDIO_START,
                evidenceProfile = "x001-exact-json-number-fixture-v1",
            ),
            sampleClock = SampleClockMap(
                presentationOrigin = PresentationTimeUs(0L),
                precisionUs = 1L,
                status = ClockVerificationStatus.VERIFIED_AFFINE,
                evidenceProfile = "x001-exact-json-number-clock-fixture-v1",
            ),
        )
    }

    @Test fun exactOneMicrosecondJsonNumberMapsWithoutRounding() {
        val mapped = map(
            """{"text":"a","words":[{"word":"a","start_time":0,"end_time":0.000001}]}"""
        ).single()

        assertEquals(0L, mapped.audioInterval.start.value)
        assertEquals(1L, mapped.audioInterval.end.value)
    }

    @Test fun rawSubMicrosecondRemainderCannotDisappearThroughJsonObjectNumberNormalization() {
        val body = """
            {"text":"a","words":[{
              "word":"a",
              "start_time":0,
              "end_time":0.00000100000000000000000000000000000000000001
            }]}
        """.trimIndent()

        try {
            map(body)
            fail("exact raw numeric lexeme has a sub-microsecond remainder and must fail closed")
        } catch (_: IllegalArgumentException) {
            // expected: longValueExact must see the raw response lexeme, not a rounded Double rendering.
        }
    }
}
