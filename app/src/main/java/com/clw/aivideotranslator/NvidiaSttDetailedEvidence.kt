package com.clw.aivideotranslator

/** Versioned identity of the currently accepted legacy STT parser/normalizer semantics. */
internal object NvidiaSttParserContract {
    const val ID = "nvidia-stt-legacy-parser-v1"
}

/**
 * Diagnostic/durability bridge only. The accepted parser result remains exactly NvidiaSttClient's
 * current legacy output; timing evidence is captured beside it without selecting a schema or unit.
 */
internal data class NvidiaSttDetailedParse(
    val result: NvidiaSttResult,
    val timingEvidence: NvidiaSttTimingEvidence,
)

internal object NvidiaSttDetailedEvidenceParser {
    fun parse(body: String, httpStatus: Int = 200): NvidiaSttDetailedParse {
        // Parse accepted semantics first. Evidence must never rescue or reinterpret a response that
        // the production parser rejects.
        val result = NvidiaSttClient.parseResponse(body, httpStatus)
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(body)
        return NvidiaSttDetailedParse(result, evidence)
    }
}
