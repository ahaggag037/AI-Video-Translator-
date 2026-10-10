package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.SampleClockMap
import java.math.BigDecimal

/**
 * Explicit evidence contract required before raw provider offsets may become application timing.
 * No default unit or origin exists here: callers must bind the exact hosted request profile,
 * observed word schema/path/field representation, externally established unit, and provider offset
 * origin to a named evidence profile.
 */
internal data class NvidiaSttTimingContract(
    val requestProfile: NvidiaSttRequestProfile,
    val schema: NvidiaWordTimingSchema,
    val schemaPath: String,
    val startField: String,
    val endField: String,
    val valueType: NvidiaRawJsonValueType,
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
        require(valueType == NvidiaRawJsonValueType.NUMBER || valueType == NvidiaRawJsonValueType.STRING) {
            "timing contract requires a numeric scalar representation"
        }
        require(evidenceProfile.isNotBlank() && evidenceProfile.length <= 512 && evidenceProfile.none(Char::isISOControl)) {
            "invalid timing evidence profile"
        }
    }

    companion object {
        private val START_FIELDS = setOf("start_time", "start", "start_ms")
        private val END_FIELDS = setOf("end_time", "end", "end_ms")
    }
}

/** Exact half-open [start, end) timing for one accepted provider word. */
internal data class NvidiaAuthoritativeWordTiming(
    val ordinal: Int,
    val text: String,
    val audioInterval: AudioIntervalUs,
    val presentationInterval: PresentationIntervalUs,
)

/**
 * X001 fail-closed timing boundary.
 *
 * Raw scalar magnitude is never used to infer units. Authority requires all of the following:
 * - the exact transport request profile and current fail-closed parser identity;
 * - raw timing evidence hashed to the exact accepted response;
 * - the exact prepared WAV digest used by that transport observation;
 * - one unambiguous response schema/path/field/value representation with an explicit unit+origin;
 * - a VERIFIED_AFFINE sample clock carrying its own evidence profile.
 *
 * Any mismatch rejects the mapping. No clamp, rounding, fallback unit, or presentation-zero default
 * exists here.
 */
internal object NvidiaSttAuthoritativeTimingMapper {
    fun map(
        evidence: NvidiaSttTimingEvidence,
        transport: NvidiaSttTransportObservation,
        preparedSampleSha256: String,
        contract: NvidiaSttTimingContract,
        sampleClock: SampleClockMap,
    ): List<NvidiaAuthoritativeWordTiming> {
        require(transport.requestProfile == contract.requestProfile) {
            "timing contract does not match observed STT request profile"
        }
        require(transport.parserVersion == NvidiaSttParserContract.ID) {
            "timing authority requires the current fail-closed STT parser"
        }
        require(transport.httpStatus in 200..299) { "timing authority requires a successful hosted response" }
        require(transport.rawResponseSha256 == evidence.rawResponseSha256) {
            "timing evidence is not bound to the accepted hosted response"
        }
        require(transport.sampleSha256 == preparedSampleSha256) {
            "timing evidence is not bound to the prepared WAV sample"
        }
        require(transport.result.words.all { it.startMs == null && it.endMs == null }) {
            "transport result already contains interpreted timing"
        }
        require(sampleClock.status == ClockVerificationStatus.VERIFIED_AFFINE &&
            !sampleClock.evidenceProfile.isNullOrBlank()) {
            "sample clock mapping lacks verified evidence"
        }
        require(evidence.sources.size == 1) {
            "authoritative timing requires exactly one observed word schema source"
        }
        val source = evidence.sources.single()
        require(source.schema == contract.schema && source.schemaPath == contract.schemaPath) {
            "observed timing schema does not match explicit contract"
        }
        require(source.words.isNotEmpty()) { "authoritative timing requires observed words" }
        require(source.words.size == transport.result.words.size) {
            "timing evidence word sequence does not match accepted transcript words"
        }
        require(contract.origin == NvidiaSttTimingContract.OffsetOrigin.UPLOADED_AUDIO_START) {
            "unsupported provider timing origin"
        }

        val mapped = source.words.mapIndexed { ordinal, word ->
            require(word.itemIndex == ordinal) { "observed timing word ordinals are not contiguous" }
            val acceptedWord = transport.result.words[ordinal]
            require(word.text == acceptedWord.text) {
                "timing evidence word text does not match accepted transcript word"
            }
            require(word.startFields.keys == setOf(contract.startField)) {
                "ambiguous or missing start timing field"
            }
            require(word.endFields.keys == setOf(contract.endField)) {
                "ambiguous or missing end timing field"
            }
            val startUs = exactOffsetUs(
                field = word.startFields.getValue(contract.startField),
                expectedType = contract.valueType,
                unit = contract.unit,
            )
            val endUs = exactOffsetUs(
                field = word.endFields.getValue(contract.endField),
                expectedType = contract.valueType,
                unit = contract.unit,
            )
            val audio = AudioIntervalUs(AudioTimeUs(startUs), AudioTimeUs(endUs))
            NvidiaAuthoritativeWordTiming(
                ordinal = ordinal,
                text = acceptedWord.text,
                audioInterval = audio,
                presentationInterval = sampleClock.mapVerified(audio),
            )
        }
        mapped.zipWithNext().forEach { (previous, current) ->
            require(current.audioInterval.start.value >= previous.audioInterval.end.value) {
                "provider word intervals overlap or are unsorted"
            }
        }
        return mapped
    }

    private fun exactOffsetUs(
        field: NvidiaRawTimingValueEvidence,
        expectedType: NvidiaRawJsonValueType,
        unit: NvidiaSttTimingContract.OffsetUnit,
    ): Long {
        require(field.jsonType == expectedType) { "timing scalar representation changed" }
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
