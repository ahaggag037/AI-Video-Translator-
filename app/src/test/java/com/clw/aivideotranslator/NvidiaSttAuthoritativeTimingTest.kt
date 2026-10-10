package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.fail
import org.junit.Test

class NvidiaSttAuthoritativeTimingTest {
    private fun contract(
        unit: NvidiaSttTimingContract.OffsetUnit,
        requestProfile: NvidiaSttRequestProfile = NvidiaSttWireContract.PROFILE,
        schema: NvidiaWordTimingSchema = NvidiaWordTimingSchema.ROOT_WORDS,
        schemaPath: String = "$.words",
        startField: String = "start_time",
        endField: String = "end_time",
    ) = NvidiaSttTimingContract(
        requestProfile = requestProfile,
        schema = schema,
        schemaPath = schemaPath,
        startField = startField,
        endField = endField,
        unit = unit,
        origin = NvidiaSttTimingContract.OffsetOrigin.UPLOADED_AUDIO_START,
        evidenceProfile = "x001-provider-contract-fixture-v1",
    )

    private fun verifiedClock(originUs: Long = 500_000L) = SampleClockMap(
        presentationOrigin = PresentationTimeUs(originUs),
        precisionUs = 1L,
        status = ClockVerificationStatus.VERIFIED_AFFINE,
        evidenceProfile = "x001-device-origin-fixture-v1",
    )

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("must fail closed")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test fun explicitMillisecondsAndVerifiedNonzeroOriginMapExactly() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {"words":[
              {"word":"a","start_time":20,"end_time":80},
              {"word":"b","start_time":80,"end_time":120}
            ]}
            """.trimIndent()
        )

        val mapped = NvidiaSttAuthoritativeTimingMapper.map(
            evidence = evidence,
            observedRequestProfile = NvidiaSttWireContract.PROFILE,
            contract = contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
            sampleClock = verifiedClock(),
        )

        assertEquals(
            AudioIntervalUs(AudioTimeUs(20_000L), AudioTimeUs(80_000L)),
            mapped[0].audioInterval,
        )
        assertEquals(
            PresentationIntervalUs(PresentationTimeUs(520_000L), PresentationTimeUs(580_000L)),
            mapped[0].presentationInterval,
        )
        assertEquals(580_000L, mapped[0].presentationInterval.end.value)
        assertEquals(mapped[0].presentationInterval.end.value, mapped[1].presentationInterval.start.value)
        assertEquals(620_000L, mapped[1].presentationInterval.end.value)
    }

    @Test fun sameMagnitudeHasNoImplicitUnitAndExplicitUnitsProduceDifferentMappings() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val milliseconds = NvidiaSttAuthoritativeTimingMapper.map(
            evidence,
            NvidiaSttWireContract.PROFILE,
            contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
            verifiedClock(0L),
        ).single().audioInterval
        val seconds = NvidiaSttAuthoritativeTimingMapper.map(
            evidence,
            NvidiaSttWireContract.PROFILE,
            contract(NvidiaSttTimingContract.OffsetUnit.SECONDS),
            verifiedClock(0L),
        ).single().audioInterval

        assertEquals(20_000L, milliseconds.start.value)
        assertEquals(80_000L, milliseconds.end.value)
        assertEquals(20_000_000L, seconds.start.value)
        assertEquals(80_000_000L, seconds.end.value)
        assertNotEquals(milliseconds, seconds)
    }

    @Test fun unverifiedSampleClockCannotAuthorizeProviderOffsets() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val unverified = SampleClockMap(
            presentationOrigin = PresentationTimeUs(500_000L),
            precisionUs = 1L,
            status = ClockVerificationStatus.UNVERIFIED,
        )

        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                unverified,
            )
        }
    }

    @Test fun multipleSchemasCannotSilentlyDonateTimingAuthority() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "words_info":{"words":[{"word":"a","start_time":20,"end_time":80}]},
              "words":[{"word":"a","start_time":20,"end_time":80}]
            }
            """.trimIndent()
        )

        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                verifiedClock(),
            )
        }
    }

    @Test fun conflictingTimingFieldsFailClosedInsteadOfChoosingPrecedence() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {"words":[{
              "word":"a",
              "start_time":20,"start_ms":20,
              "end_time":80,"end_ms":80
            }]}
            """.trimIndent()
        )

        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                verifiedClock(),
            )
        }
    }

    @Test fun requestProfileAndSchemaPathMustMatchTheExactEvidenceContract() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )

        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE.copy(language = "en-GB"),
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                verifiedClock(),
            )
        }
        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE,
                contract(
                    unit = NvidiaSttTimingContract.OffsetUnit.MILLISECONDS,
                    schemaPath = "$.results[0].alternatives[0].words",
                ),
                verifiedClock(),
            )
        }
    }

    @Test fun subMicrosecondOffsetsAreRejectedInsteadOfRounded() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"words":[{"word":"a","start_time":0.0000001,"end_time":0.000002}]}"""
        )

        rejected {
            NvidiaSttAuthoritativeTimingMapper.map(
                evidence,
                NvidiaSttWireContract.PROFILE,
                contract(NvidiaSttTimingContract.OffsetUnit.SECONDS),
                verifiedClock(0L),
            )
        }
    }
}
