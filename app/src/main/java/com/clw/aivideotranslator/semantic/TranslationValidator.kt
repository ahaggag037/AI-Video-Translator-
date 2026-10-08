package com.clw.aivideotranslator.semantic

import java.math.BigDecimal

enum class TranslationValidationState {
    PASS,
    PASS_WITH_WARNING,
    REVIEW_REQUIRED,
    NON_RETRYABLE_FAILURE,
}

data class TranslationValidationResult(
    val state: TranslationValidationState,
    val canonicalTarget: String?,
    val warnings: Set<String>,
)

object TranslationValidator {
    private val urlPattern = Regex("(?i)https?://\\S+")
    private val emailPattern = Regex("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}")
    private val numberPattern = Regex("[-+]?[0-9٠-٩۰-۹]+(?:[.,٫٬][0-9٠-٩۰-۹]+)*(?:\\s*%)?")
    private val highRiskWarnings = setOf(
        "URL_MISMATCH",
        "EMAIL_MISMATCH",
        "NUMBER_FACT_MISMATCH",
        "NUMERIC_FORMAT_AMBIGUOUS",
        "BIDI_CONTROL_REVIEW",
        "WRONG_LANGUAGE_SUSPECTED",
    )

    fun validate(sourceText: String, rawTarget: String): TranslationValidationResult {
        if (rawTarget.isBlank() || rawTarget.length > 4_000) {
            return TranslationValidationResult(
                TranslationValidationState.NON_RETRYABLE_FAILURE,
                canonicalTarget = null,
                warnings = setOf("INVALID_TARGET_LENGTH"),
            )
        }
        val canonical = runCatching { TextPolicy.canonicalView(rawTarget) }.getOrElse {
            return TranslationValidationResult(
                TranslationValidationState.NON_RETRYABLE_FAILURE,
                canonicalTarget = null,
                warnings = setOf("INVALID_UNICODE"),
            )
        }
        val warnings = linkedSetOf<String>()
        warnings += canonical.warnings

        if (opaqueValues(sourceText, urlPattern) != opaqueValues(canonical.displayCanonical, urlPattern)) {
            warnings += "URL_MISMATCH"
        }
        if (opaqueValues(sourceText, emailPattern) != opaqueValues(canonical.displayCanonical, emailPattern)) {
            warnings += "EMAIL_MISMATCH"
        }

        val sourceNumbers = numericFacts(sourceText)
        val targetNumbers = numericFacts(canonical.displayCanonical)
        if (sourceNumbers.ambiguous || targetNumbers.ambiguous) warnings += "NUMERIC_FORMAT_AMBIGUOUS"
        if (sourceNumbers.values != targetNumbers.values) warnings += "NUMBER_FACT_MISMATCH"

        val targetLetters = canonical.displayCanonical.count { Character.isLetter(it) }
        if (targetLetters >= 10) {
            val arabicLetters = canonical.displayCanonical.count(::isArabicLetter)
            if (arabicLetters.toDouble() / targetLetters.toDouble() < 0.50) {
                warnings += "WRONG_LANGUAGE_SUSPECTED"
            }
        }

        val sourceScalars = sourceText.codePointCount(0, sourceText.length)
        val targetScalars = canonical.displayCanonical.codePointCount(0, canonical.displayCanonical.length)
        if (sourceScalars >= 20) {
            val ratio = targetScalars.toDouble() / sourceScalars.toDouble()
            if (ratio < 0.35 || ratio > 3.5) warnings += "LENGTH_RATIO_SUSPECT"
        }

        val state = when {
            warnings.any { it in highRiskWarnings } -> TranslationValidationState.REVIEW_REQUIRED
            warnings.isNotEmpty() -> TranslationValidationState.PASS_WITH_WARNING
            else -> TranslationValidationState.PASS
        }
        return TranslationValidationResult(state, canonical.displayCanonical, warnings)
    }

    private data class NumericFacts(val values: List<String>, val ambiguous: Boolean)

    private fun numericFacts(text: String): NumericFacts {
        var ambiguous = false
        val values = numberPattern.findAll(text).mapNotNull { match ->
            val parsed = canonicalNumber(match.value)
            if (parsed == null) {
                ambiguous = true
                null
            } else parsed
        }.sorted().toList()
        return NumericFacts(values, ambiguous)
    }

    private fun canonicalNumber(raw: String): String? {
        val compact = raw.filterNot(Char::isWhitespace)
        val percent = compact.endsWith('%')
        var value = if (percent) compact.dropLast(1) else compact
        value = buildString(value.length) {
            value.forEach { ch ->
                append(
                    when (ch) {
                        in '٠'..'٩' -> ('0'.code + (ch.code - '٠'.code)).toChar()
                        in '۰'..'۹' -> ('0'.code + (ch.code - '۰'.code)).toChar()
                        '٫' -> '.'
                        '٬' -> ','
                        else -> ch
                    }
                )
            }
        }
        val commaCount = value.count { it == ',' }
        val dotCount = value.count { it == '.' }
        value = when {
            commaCount > 0 && dotCount == 1 -> {
                val whole = value.substringBefore('.')
                if (!whole.matches(Regex("[-+]?\\d{1,3}(,\\d{3})+"))) return null
                value.replace(",", "")
            }
            commaCount > 0 && dotCount == 0 -> {
                if (!value.matches(Regex("[-+]?\\d{1,3}(,\\d{3})+"))) return null
                value.replace(",", "")
            }
            commaCount == 0 && dotCount <= 1 -> value
            else -> return null
        }
        val decimal = runCatching { BigDecimal(value) }.getOrNull() ?: return null
        val normalized = decimal.stripTrailingZeros().toPlainString()
        return if (percent) "$normalized%" else normalized
    }

    private fun opaqueValues(text: String, pattern: Regex): List<String> =
        pattern.findAll(text)
            .map { it.value.trimEnd('.', ',', ';', ':', '!', '?') }
            .sorted()
            .toList()

    private fun isArabicLetter(ch: Char): Boolean = Character.isLetter(ch) && (
        ch.code in 0x0600..0x06FF ||
            ch.code in 0x0750..0x077F ||
            ch.code in 0x08A0..0x08FF ||
            ch.code in 0xFB50..0xFDFF ||
            ch.code in 0xFE70..0xFEFF
        )
}
