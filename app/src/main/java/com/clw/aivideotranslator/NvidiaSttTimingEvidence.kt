package com.clw.aivideotranslator

import java.security.MessageDigest
import org.json.JSONArray
import org.json.JSONObject

internal enum class NvidiaWordTimingSchema {
    RESULTS_ALTERNATIVES_WORDS,
    WORDS_INFO_WORDS,
    ROOT_WORDS,
}

internal enum class NvidiaRawJsonValueType {
    NUMBER,
    STRING,
    BOOLEAN,
    NULL,
    OBJECT,
    ARRAY,
    OTHER,
}

internal data class NvidiaRawTimingValueEvidence(
    val jsonType: NvidiaRawJsonValueType,
    /** Parser-rendered scalar view. Exact source lexeme remains in NvidiaSttTimingEvidence.rawResponseUtf8. */
    val rawText: String,
    val parsedNumber: Double?,
)

internal data class NvidiaRawWordTimingEvidence(
    val itemIndex: Int,
    val itemPath: String,
    val text: String?,
    val startFields: Map<String, NvidiaRawTimingValueEvidence>,
    val endFields: Map<String, NvidiaRawTimingValueEvidence>,
)

internal data class NvidiaWordTimingSourceEvidence(
    val schema: NvidiaWordTimingSchema,
    val schemaPath: String,
    val words: List<NvidiaRawWordTimingEvidence>,
)

internal data class NvidiaSttTimingEvidence(
    /**
     * Verbatim provider response supplied to the inspector. Diagnostic-only: callers must not log,
     * relay, or persist this unredacted because it may contain transcript/private media content.
     * Keeping it here prevents JSON parsing from irreversibly normalizing numeric lexemes.
     */
    val rawResponseUtf8: String,
    val rawResponseSha256: String,
    val sources: List<NvidiaWordTimingSourceEvidence>,
) {
    val presentSchemas: Set<NvidiaWordTimingSchema> = sources.map { it.schema }.toSet()
    val hasMultipleWordSchemas: Boolean = presentSchemas.size > 1
}

/**
 * X001 diagnostic-only adapter.
 *
 * This deliberately preserves raw schema/field provenance without selecting a timing unit,
 * authoritative word schema, or changing NvidiaSttClient.normalizeTimes(). Parsed scalar type/text
 * is retained alongside an optional numeric parse, while the verbatim response remains available
 * as the source of truth for exact JSON lexemes. The production parser remains intentionally
 * separate.
 */
internal object NvidiaSttTimingEvidenceInspector {
    fun inspect(body: String): NvidiaSttTimingEvidence {
        val root = JSONObject(body)
        val sources = mutableListOf<NvidiaWordTimingSourceEvidence>()

        root.optJSONArray("results")?.let { results ->
            for (resultIndex in 0 until results.length()) {
                val result = results.optJSONObject(resultIndex) ?: continue
                val alternatives = result.optJSONArray("alternatives") ?: continue
                for (alternativeIndex in 0 until alternatives.length()) {
                    val alternative = alternatives.optJSONObject(alternativeIndex) ?: continue
                    alternative.optJSONArray("words")?.let { words ->
                        sources += sourceEvidence(
                            schema = NvidiaWordTimingSchema.RESULTS_ALTERNATIVES_WORDS,
                            schemaPath = "$.results[$resultIndex].alternatives[$alternativeIndex].words",
                            array = words,
                        )
                    }
                }
            }
        }

        root.optJSONObject("words_info")?.optJSONArray("words")?.let { words ->
            sources += sourceEvidence(
                schema = NvidiaWordTimingSchema.WORDS_INFO_WORDS,
                schemaPath = "$.words_info.words",
                array = words,
            )
        }

        root.optJSONArray("words")?.let { words ->
            sources += sourceEvidence(
                schema = NvidiaWordTimingSchema.ROOT_WORDS,
                schemaPath = "$.words",
                array = words,
            )
        }

        return NvidiaSttTimingEvidence(
            rawResponseUtf8 = body,
            rawResponseSha256 = sha256Utf8(body),
            sources = sources,
        )
    }

    private fun sourceEvidence(
        schema: NvidiaWordTimingSchema,
        schemaPath: String,
        array: JSONArray,
    ): NvidiaWordTimingSourceEvidence {
        val words = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val wordText = item.optionalNonBlankString("word") ?: item.optionalNonBlankString("text")
                add(
                    NvidiaRawWordTimingEvidence(
                        itemIndex = index,
                        itemPath = "$schemaPath[$index]",
                        text = wordText,
                        startFields = timingFields(item, listOf("start_time", "start", "start_ms")),
                        endFields = timingFields(item, listOf("end_time", "end", "end_ms")),
                    )
                )
            }
        }
        return NvidiaWordTimingSourceEvidence(
            schema = schema,
            schemaPath = schemaPath,
            words = words,
        )
    }

    private fun timingFields(
        item: JSONObject,
        names: List<String>,
    ): Map<String, NvidiaRawTimingValueEvidence> = buildMap {
        names.forEach { name ->
            if (item.has(name)) put(name, timingValue(item.opt(name)))
        }
    }

    private fun timingValue(value: Any?): NvidiaRawTimingValueEvidence {
        if (value == null || value === JSONObject.NULL) {
            return NvidiaRawTimingValueEvidence(
                jsonType = NvidiaRawJsonValueType.NULL,
                rawText = "null",
                parsedNumber = null,
            )
        }
        val type = when (value) {
            is Number -> NvidiaRawJsonValueType.NUMBER
            is String -> NvidiaRawJsonValueType.STRING
            is Boolean -> NvidiaRawJsonValueType.BOOLEAN
            is JSONObject -> NvidiaRawJsonValueType.OBJECT
            is JSONArray -> NvidiaRawJsonValueType.ARRAY
            else -> NvidiaRawJsonValueType.OTHER
        }
        val rawText = when (value) {
            is String -> value
            else -> value.toString()
        }
        val parsed = when (value) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
        return NvidiaRawTimingValueEvidence(
            jsonType = type,
            rawText = rawText,
            parsedNumber = parsed,
        )
    }

    private fun JSONObject.optionalNonBlankString(name: String): String? {
        if (!has(name) || isNull(name)) return null
        return (opt(name) as? String)?.takeIf { it.isNotBlank() }
    }

    private fun sha256Utf8(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
}
