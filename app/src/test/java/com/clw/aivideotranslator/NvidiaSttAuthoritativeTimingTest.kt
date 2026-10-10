package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.CueIndex
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class NvidiaSttAuthoritativeTimingTest {
    private val sampleSha256 = "a".repeat(64)

    private fun contract(
        unit: NvidiaSttTimingContract.OffsetUnit,
        requestProfile: NvidiaSttRequestProfile = NvidiaSttWireContract.PROFILE,
        schema: NvidiaWordTimingSchema = NvidiaWordTimingSchema.ROOT_WORDS,
        schemaPath: String = "$.words",
        startField: String = "start_time",
        endField: String = "end_time",
        valueType: NvidiaRawJsonValueType = NvidiaRawJsonValueType.NUMBER,
    ) = NvidiaSttTimingContract(
        requestProfile = requestProfile,
        schema = schema,
        schemaPath = schemaPath,
        startField = startField,
        endField = endField,
        valueType = valueType,
        unit = unit,
        origin = NvidiaSttTimingContract.OffsetOrigin.UPLOADED_AUDIO_START,
        evidenceProfile = "x001-provider-contract-fixture-v1",
    )

    private fun verifiedClock(originUs: Long = 500_000L, evidenceProfile: String? = "x001-device-origin-fixture-v1") =
        SampleClockMap(
            presentationOrigin = PresentationTimeUs(originUs),
            precisionUs = 1L,
            status = ClockVerificationStatus.VERIFIED_AFFINE,
            evidenceProfile = evidenceProfile,
        )

    private fun transport(
        evidence: NvidiaSttTimingEvidence,
        requestProfile: NvidiaSttRequestProfile = NvidiaSttWireContract.PROFILE,
        rawResponseSha256: String = evidence.rawResponseSha256,
        sampleSha256: String = this.sampleSha256,
        result: NvidiaSttResult = NvidiaSttClient.parseResponseWithoutTimingAuthority(evidence.rawResponseUtf8),
        parserVersion: String = NvidiaSttParserContract.ID,
    ) = NvidiaSttTransportObservation(
        requestProfile = requestProfile,
        result = result,
        parserVersion = parserVersion,
        rawResponseSha256 = rawResponseSha256,
        sampleSha256 = sampleSha256,
    )

    private fun mapped(
        evidence: NvidiaSttTimingEvidence,
        timingContract: NvidiaSttTimingContract,
        clock: SampleClockMap = verifiedClock(),
        transport: NvidiaSttTransportObservation = transport(evidence),
        preparedSampleSha256: String = sampleSha256,
    ) = NvidiaSttAuthoritativeTimingMapper.map(
        evidence = evidence,
        transport = transport,
        preparedSampleSha256 = preparedSampleSha256,
        contract = timingContract,
        sampleClock = clock,
    )

    private fun rejected(block: () -> Unit) {
        try {
            block()
            fail("must fail closed")
        } catch (_: Exception) {
            // expected
        }
    }

    @Test fun explicitMillisecondsAndVerifiedNonzeroOriginMapExactlyAndStayHalfOpen() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {"text":"a b","words":[
              {"word":"a","start_time":20,"end_time":80},
              {"word":"b","start_time":80,"end_time":120}
            ]}
            """.trimIndent()
        )

        val result = mapped(
            evidence = evidence,
            timingContract = contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
        )

        assertEquals(
            AudioIntervalUs(AudioTimeUs(20_000L), AudioTimeUs(80_000L)),
            result[0].audioInterval,
        )
        assertEquals(
            PresentationIntervalUs(PresentationTimeUs(520_000L), PresentationTimeUs(580_000L)),
            result[0].presentationInterval,
        )
        assertEquals(result[0].presentationInterval.end.value, result[1].presentationInterval.start.value)
        assertEquals(620_000L, result[1].presentationInterval.end.value)

        val index = CueIndex(result) { it.presentationInterval }
        assertEquals(0, requireNotNull(index.activeAt(PresentationTimeUs(520_000L))).ordinal)
        assertEquals(0, requireNotNull(index.activeAt(PresentationTimeUs(579_999L))).ordinal)
        assertEquals(1, requireNotNull(index.activeAt(PresentationTimeUs(580_000L))).ordinal)
        assertNull(index.activeAt(PresentationTimeUs(620_000L)))
    }

    @Test fun sameMagnitudeHasNoImplicitUnitAndExplicitUnitsProduceDifferentMappings() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val milliseconds = mapped(
            evidence,
            contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
            verifiedClock(0L),
        ).single().audioInterval
        val seconds = mapped(
            evidence,
            contract(NvidiaSttTimingContract.OffsetUnit.SECONDS),
            verifiedClock(0L),
        ).single().audioInterval

        assertEquals(20_000L, milliseconds.start.value)
        assertEquals(80_000L, milliseconds.end.value)
        assertEquals(20_000_000L, seconds.start.value)
        assertEquals(80_000_000L, seconds.end.value)
        assertNotEquals(milliseconds, seconds)
    }

    @Test fun unverifiedOrReceiptlessSampleClockCannotAuthorizeProviderOffsets() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val unverified = SampleClockMap(
            presentationOrigin = PresentationTimeUs(500_000L),
            precisionUs = 1L,
            status = ClockVerificationStatus.UNVERIFIED,
        )

        rejected {
            mapped(evidence, contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS), unverified)
        }
        rejected {
            mapped(
                evidence,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                verifiedClock(evidenceProfile = null),
            )
        }
    }

    @Test fun multipleSchemasCannotSilentlyDonateTimingAuthority() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "text":"a",
              "words_info":{"words":[{"word":"a","start_time":20,"end_time":80}]},
              "words":[{"word":"a","start_time":20,"end_time":80}]
            }
            """.trimIndent()
        )

        rejected {
            mapped(evidence, contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS))
        }
    }

    @Test fun conflictingTimingFieldsAndRepresentationDriftFailClosed() {
        val conflicting = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {"text":"a","words":[{
              "word":"a",
              "start_time":20,"start_ms":20,
              "end_time":80,"end_ms":80
            }]}
            """.trimIndent()
        )
        rejected {
            mapped(conflicting, contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS))
        }

        val strings = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":"20","end_time":"80"}]}"""
        )
        rejected {
            mapped(strings, contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS))
        }
        val explicitlyString = mapped(
            strings,
            contract(
                unit = NvidiaSttTimingContract.OffsetUnit.MILLISECONDS,
                valueType = NvidiaRawJsonValueType.STRING,
            ),
        ).single()
        assertEquals(20_000L, explicitlyString.audioInterval.start.value)
    }

    @Test fun requestProfileAndSchemaPathMustMatchTheExactEvidenceContract() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val wrongProfileTransport = transport(
            evidence,
            requestProfile = NvidiaSttWireContract.PROFILE.copy(language = "en-GB"),
        )
        rejected {
            mapped(
                evidence,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                transport = wrongProfileTransport,
            )
        }
        rejected {
            mapped(
                evidence,
                contract(
                    unit = NvidiaSttTimingContract.OffsetUnit.MILLISECONDS,
                    schemaPath = "$.results[0].alternatives[0].words",
                ),
            )
        }
    }

    @Test fun responseAndPreparedSampleBindingsMustMatchTheTransportReceipt() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        rejected {
            mapped(
                evidence,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                transport = transport(evidence, rawResponseSha256 = "b".repeat(64)),
            )
        }
        rejected {
            mapped(
                evidence,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                preparedSampleSha256 = "c".repeat(64),
            )
        }
    }

    @Test fun alreadyInterpretedLegacyTimingCannotCrossTheAuthorityBoundary() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":20,"end_time":80}]}"""
        )
        val legacyTimedResult = NvidiaSttClient.parseResponse(evidence.rawResponseUtf8)
        rejected {
            mapped(
                evidence,
                contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS),
                transport = transport(evidence, result = legacyTimedResult),
            )
        }
    }

    @Test fun subMicrosecondOffsetsAndOverlappingIntervalsAreRejectedInsteadOfRoundedOrClamped() {
        val subMicrosecond = NvidiaSttTimingEvidenceInspector.inspect(
            """{"text":"a","words":[{"word":"a","start_time":0.0000001,"end_time":0.000002}]}"""
        )
        rejected {
            mapped(
                subMicrosecond,
                contract(NvidiaSttTimingContract.OffsetUnit.SECONDS),
                verifiedClock(0L),
            )
        }

        val overlapping = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {"text":"a b","words":[
              {"word":"a","start_time":20,"end_time":80},
              {"word":"b","start_time":70,"end_time":100}
            ]}
            """.trimIndent()
        )
        rejected {
            mapped(overlapping, contract(NvidiaSttTimingContract.OffsetUnit.MILLISECONDS))
        }
    }
}
