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
 * exists here. Numeric JSON tokens are converted from their exact raw-response lexemes rather than
 * JSONObject/Double renderings, so a sub-microsecond remainder cannot disappear before validation.
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

        // JSONObject normalizes numeric tokens. Build a strict path->number-lexeme index from the
        // already hash-bound raw response so exact decimal validation does not depend on Double.
        val exactNumberLexemes = StrictJsonNumberLexemeIndex.index(evidence.rawResponseUtf8)

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
                fieldPath = "${word.itemPath}.${contract.startField}",
                expectedType = contract.valueType,
                unit = contract.unit,
                exactNumberLexemes = exactNumberLexemes,
            )
            val endUs = exactOffsetUs(
                field = word.endFields.getValue(contract.endField),
                fieldPath = "${word.itemPath}.${contract.endField}",
                expectedType = contract.valueType,
                unit = contract.unit,
                exactNumberLexemes = exactNumberLexemes,
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
        fieldPath: String,
        expectedType: NvidiaRawJsonValueType,
        unit: NvidiaSttTimingContract.OffsetUnit,
        exactNumberLexemes: Map<String, String>,
    ): Long {
        require(field.jsonType == expectedType) { "timing scalar representation changed" }
        val exactDecimalText = when (expectedType) {
            NvidiaRawJsonValueType.NUMBER -> exactNumberLexemes[fieldPath]
                ?: throw IllegalArgumentException("exact numeric timing lexeme is missing")
            NvidiaRawJsonValueType.STRING -> field.rawText
            else -> error("unsupported timing scalar representation")
        }
        val decimal = try {
            BigDecimal(exactDecimalText)
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

/**
 * Strict JSON scanner used only to retain exact numeric token lexemes at stable JSON paths.
 * It does not select a provider schema or unit. Duplicate object keys and non-standard JSON are
 * rejected so authority fails closed instead of inheriting JSONObject's normalization/leniency.
 */
private class StrictJsonNumberLexemeIndex private constructor(private val source: String) {
    private var offset = 0
    private val values = linkedMapOf<String, String>()

    fun parse(): Map<String, String> {
        skipWhitespace()
        parseValue("$")
        skipWhitespace()
        require(offset == source.length) { "trailing data after hosted JSON response" }
        return values.toMap()
    }

    private fun parseValue(path: String) {
        skipWhitespace()
        require(offset < source.length) { "unexpected end of hosted JSON response" }
        when (source[offset]) {
            '{' -> parseObject(path)
            '[' -> parseArray(path)
            '"' -> parseString()
            't' -> consumeLiteral("true")
            'f' -> consumeLiteral("false")
            'n' -> consumeLiteral("null")
            '-', in '0'..'9' -> {
                val lexeme = parseNumber()
                require(values.put(path, lexeme) == null) { "duplicate numeric JSON path" }
            }
            else -> throw IllegalArgumentException("non-standard hosted JSON token at offset $offset")
        }
    }

    private fun parseObject(path: String) {
        expect('{')
        skipWhitespace()
        if (consumeIf('}')) return
        val keys = mutableSetOf<String>()
        while (true) {
            skipWhitespace()
            require(offset < source.length && source[offset] == '"') { "JSON object key must be quoted" }
            val key = parseString()
            require(keys.add(key)) { "duplicate JSON object key" }
            skipWhitespace()
            expect(':')
            parseValue(childPath(path, key))
            skipWhitespace()
            when {
                consumeIf('}') -> return
                consumeIf(',') -> Unit
                else -> throw IllegalArgumentException("expected ',' or '}' in hosted JSON object")
            }
        }
    }

    private fun parseArray(path: String) {
        expect('[')
        skipWhitespace()
        if (consumeIf(']')) return
        var index = 0
        while (true) {
            parseValue("$path[$index]")
            index += 1
            skipWhitespace()
            when {
                consumeIf(']') -> return
                consumeIf(',') -> Unit
                else -> throw IllegalArgumentException("expected ',' or ']' in hosted JSON array")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val decoded = StringBuilder()
        while (offset < source.length) {
            val character = source[offset++]
            when {
                character == '"' -> return decoded.toString()
                character == '\\' -> {
                    require(offset < source.length) { "unterminated JSON escape" }
                    when (val escaped = source[offset++]) {
                        '"', '\\', '/' -> decoded.append(escaped)
                        'b' -> decoded.append('\b')
                        'f' -> decoded.append('\u000c')
                        'n' -> decoded.append('\n')
                        'r' -> decoded.append('\r')
                        't' -> decoded.append('\t')
                        'u' -> {
                            require(offset + 4 <= source.length) { "short JSON unicode escape" }
                            val digits = source.substring(offset, offset + 4)
                            require(digits.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                                "invalid JSON unicode escape"
                            }
                            decoded.append(digits.toInt(16).toChar())
                            offset += 4
                        }
                        else -> throw IllegalArgumentException("invalid JSON escape")
                    }
                }
                character.code < 0x20 -> throw IllegalArgumentException("unescaped JSON control character")
                else -> decoded.append(character)
            }
        }
        throw IllegalArgumentException("unterminated JSON string")
    }

    private fun parseNumber(): String {
        val start = offset
        consumeIf('-')
        require(offset < source.length) { "incomplete JSON number" }
        if (consumeIf('0')) {
            require(offset >= source.length || source[offset] !in '0'..'9') { "leading zero in JSON number" }
        } else {
            require(source[offset] in '1'..'9') { "invalid JSON number" }
            while (offset < source.length && source[offset] in '0'..'9') offset += 1
        }
        if (consumeIf('.')) {
            require(offset < source.length && source[offset] in '0'..'9') { "fraction requires digits" }
            while (offset < source.length && source[offset] in '0'..'9') offset += 1
        }
        if (offset < source.length && (source[offset] == 'e' || source[offset] == 'E')) {
            offset += 1
            if (offset < source.length && (source[offset] == '+' || source[offset] == '-')) offset += 1
            require(offset < source.length && source[offset] in '0'..'9') { "exponent requires digits" }
            while (offset < source.length && source[offset] in '0'..'9') offset += 1
        }
        return source.substring(start, offset)
    }

    private fun consumeLiteral(literal: String) {
        require(source.regionMatches(offset, literal, 0, literal.length)) { "invalid JSON literal" }
        offset += literal.length
    }

    private fun childPath(parent: String, key: String): String =
        if (key.isNotEmpty() && key.all { it == '_' || it.isLetterOrDigit() }) {
            "$parent.$key"
        } else {
            "$parent['${key.replace("\\", "\\\\").replace("'", "\\'")}']"
        }

    private fun skipWhitespace() {
        while (offset < source.length && source[offset] in charArrayOf(' ', '\n', '\r', '\t')) offset += 1
    }

    private fun expect(expected: Char) {
        require(offset < source.length && source[offset] == expected) { "expected '$expected' in hosted JSON" }
        offset += 1
    }

    private fun consumeIf(expected: Char): Boolean {
        if (offset < source.length && source[offset] == expected) {
            offset += 1
            return true
        }
        return false
    }

    companion object {
        fun index(source: String): Map<String, String> = StrictJsonNumberLexemeIndex(source).parse()
    }
}
