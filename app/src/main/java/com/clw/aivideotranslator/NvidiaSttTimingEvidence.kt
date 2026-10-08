package com.clw.aivideotranslator

import org.json.JSONArray
import org.json.JSONObject

internal enum class NvidiaWordTimingSchema {
    RESULTS_ALTERNATIVES_WORDS,
    WORDS_INFO_WORDS,
    ROOT_WORDS,
}

internal data class NvidiaRawWordTimingEvidence(
    val text: String,
    val startFields: Map<String, Double>,
    val endFields: Map<String, Double>,
)

internal data class NvidiaWordTimingSourceEvidence(
    val schema: NvidiaWordTimingSchema,
    val schemaPath: String,
    val words: List<NvidiaRawWordTimingEvidence>,
)

internal data class NvidiaSttTimingEvidence(
    val sources: List<NvidiaWordTimingSourceEvidence>,
) {
    val presentSchemas: Set<NvidiaWordTimingSchema> = sources.map { it.schema }.toSet()
    val hasMultipleWordSchemas: Boolean = presentSchemas.size > 1
}

/**
 * X001 diagnostic-only adapter.
 *
 * This deliberately preserves raw schema/field provenance without selecting a timing unit,
 * authoritative word schema, or changing NvidiaSttClient.normalizeTimes().
 */
internal object NvidiaSttTimingEvidenceInspector {
    fun inspect(body: String): NvidiaSttTimingEvidence {
        val root = JSONObject(body)
        val sources = mutableListOf<NvidiaWordTimingSourceEvidence>()

        root.optJSONArray("results")?.let { results ->
            for (resultIndex in 0 until results.length()) {
                val result = results.optJSONObject(resultIndex) ?: continue
                val alternatives = result.optJSONArray("alternatives") ?: continue
                if (alternatives.length() == 0) continue
                val alternative = alternatives.optJSONObject(0) ?: continue
                alternative.optJSONArray("words")?.let { words ->
                    sources += sourceEvidence(
                        schema = NvidiaWordTimingSchema.RESULTS_ALTERNATIVES_WORDS,
                        schemaPath = "$.results[$resultIndex].alternatives[0].words",
                        array = words,
                    )
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

        return NvidiaSttTimingEvidence(sources = sources)
    }

    private fun sourceEvidence(
        schema: NvidiaWordTimingSchema,
        schemaPath: String,
        array: JSONArray,
    ): NvidiaWordTimingSourceEvidence {
        val words = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val text = item.optString("word").ifBlank { item.optString("text") }
                if (text.isBlank()) continue
                add(
                    NvidiaRawWordTimingEvidence(
                        text = text,
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

    private fun timingFields(item: JSONObject, names: List<String>): Map<String, Double> = buildMap {
        names.forEach { name ->
            item.optNullableDouble(name)?.let { value -> put(name, value) }
        }
    }

    private fun JSONObject.optNullableDouble(name: String): Double? {
        if (!has(name) || isNull(name)) return null
        return when (val value = opt(name)) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull()
            else -> null
        }
    }
}
