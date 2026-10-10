package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import java.math.BigDecimal

/**
 * Explicit evidence contract required before raw provider offsets may become application timing.
 * No default unit or origin exists here: callers must bind the exact hosted request profile,
 * observed word schema/fields, externally established unit, and provider offset origin.
 */
internal data class NvidiaSttTimingContract(
    val requestProfile: NvidiaSttRequestProfile,
    val schema: NvidiaWordTimingSchema,
    val schemaPath: String,
    val startField: String,
    val endField: String,
    val unit: OffsetUnit,
    val origin: OffsetOrigin,
    val evidenceProfile: String,
) {
    enum class OffsetUnit { MILLISECONDS, SECONDS }
    enum class OffsetOrigin { UPLOADED_AUDIO_START }

    init {
        require(schemaPath.startsWith("$") && schemaPath.length <= 512 && schemaPath.none(Char::isISOControl)) {
            "invalid timing schema path"
        }
        require(startField in START_FIELDS) { "unsupported start timing field" }
        require(endField in END_FIELDS) { "unsupported end timing field" }
        require(evidenceProfile.isNotBlank() && evidenceProfile.length <= 512 && evidenceProfile.none(Char::isISOControl)) {
            "invalid timing evidence profile"
        }
    }

    companion object {
        private val START_FIELDS = setOf("start_time", "start", "start_ms")
        private val END_FIELDS = setOf("end_time", "end", "end_ms")
    }
}

/** Exact half-open [start, end) timing for one observed provider word. */
internal data class NvidiaAuthoritativeWordTiming(
    val ordinal: Int,
    val text: String?,
    val audioInterval: AudioIntervalUs,
    val presentationInterval: PresentationIntervalUs,
)

/**
 * X001 fail-closed timing boundary.
 *
 * Raw scalar magnitude is never used to infer units. Mapping is permitted only when the caller
 * supplies an explicit provider contract and a VERIFIED_AFFINE sample clock. The provider contract
 * is also pinned to the exact request profile and exact single response schema/path/field pair so a
 * different HTTP representation cannot silently inherit timing semantics from another one.
 */
internal object NvidiaSttAuthoritativeTimingMapper {
    fun map(
        evidence: NvidiaSttTimingEvidence,
        observedRequestProfile: NvidiaSttRequestProfile,
        contract: NvidiaSttTimingContract,
        sampleClock: SampleClockMap,
    ): List<NvidiaAuthoritativeWordTiming> {
        require(observedRequestProfile == contract.requestProfile) {
            "timing contract does not match observed STT request profile"
        }
        require(evidence.sources.size == 1) {
            "authoritative timing requires exactly one observed word schema source"
        }
        val source = evidence.sources.single()
        require(source.schema == contract.schema && source.schemaPath == contract.schemaPath) {
            "observed timing schema does not match explicit contract"
        }
        require(source.words.isNotEmpty()) { "authoritative timing requires observed words" }
        require(contract.origin == NvidiaSttTimingContract.OffsetOrigin.UPLOADED_AUDIO_START) {
            "unsupported provider timing origin"
        }

        return source.words.mapIndexed { ordinal, word ->
            require(word.itemIndex == ordinal) { "observed timing word ordinals are not contiguous" }
            require(word.startFields.keys == setOf(contract.startField)) {
                "ambiguous or missing start timing field"
            }
            require(word.endFields.keys == setOf(contract.endField)) {
                "ambiguous or missing end timing field"
            }
            val startUs = exactOffsetUs(word.startFields.getValue(contract.startField), contract.unit)
            val endUs = exactOffsetUs(word.endFields.getValue(contract.endField), contract.unit)
            val audio = AudioIntervalUs(AudioTimeUs(startUs), AudioTimeUs(endUs))
            NvidiaAuthoritativeWordTiming(
                ordinal = ordinal,
                text = word.text,
                audioInterval = audio,
                presentationInterval = sampleClock.mapVerified(audio),
            )
        }
    }

    private fun exactOffsetUs(
        field: NvidiaRawTimingValueEvidence,
        unit: NvidiaSttTimingContract.OffsetUnit,
    ): Long {
        require(field.jsonType == NvidiaRawJsonValueType.NUMBER || field.jsonType == NvidiaRawJsonValueType.STRING) {
            "timing field must be a numeric scalar"
        }
        val decimal = try {
            BigDecimal(field.rawText)
        } catch (error: NumberFormatException) {
            throw IllegalArgumentException("timing field is not an exact decimal", error)
        }
        require(decimal.signum() >= 0) { "negative provider timing offset" }
        val scale = when (unit) {
            NvidiaSttTimingContract.OffsetUnit.MILLISECONDS -> 1_000L
            NvidiaSttTimingContract.OffsetUnit.SECONDS -> 1_000_000L
        }
        return try {
            decimal.multiply(BigDecimal.valueOf(scale)).longValueExact()
        } catch (error: ArithmeticException) {
            throw IllegalArgumentException("timing offset cannot be represented exactly in microseconds", error)
        }
    }
}
