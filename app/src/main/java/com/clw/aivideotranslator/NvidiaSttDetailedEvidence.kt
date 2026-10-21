package com.clw.aivideotranslator

/**
 * Versioned identity of accepted hosted STT parser semantics. V2 removes the legacy magnitude-based
 * seconds heuristic and follows NVIDIA Riva ASR WordInfo: start/end offsets are milliseconds relative
 * to the beginning of the submitted audio.
 */
internal object NvidiaSttParserContract {
    const val ID = "nvidia-stt-riva-ms-parser-v2"
}

/**
 * Diagnostic/durability bridge only. The accepted parser result remains exactly NvidiaSttClient's
 * current output; timing evidence is captured beside it without authorizing a media clock map.
 * NOTE: NvidiaSttDetailedParse nests NvidiaSttTimingEvidence.rawResponseUtf8 (the verbatim provider
 * body). It is X001-diagnostic-only and must never flow into durable/persisted/relayed structures.
 */
internal data class NvidiaSttDetailedParse(
    val result: NvidiaSttResult,
    val timingEvidence: NvidiaSttTimingEvidence,
    val parserVersion: String,
)

internal object NvidiaSttDetailedEvidenceParser {
    fun parse(body: String, httpStatus: Int = 200): NvidiaSttDetailedParse {
        val result = NvidiaSttClient.parseResponse(body, httpStatus)
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(body)
        return NvidiaSttDetailedParse(result, evidence, NvidiaSttParserContract.ID)
    }
}

private fun isLowerSha256Evidence(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

internal data class NvidiaSttTransportObservation internal constructor(
    val requestProfile: NvidiaSttRequestProfile,
    val result: NvidiaSttResult,
    val parserVersion: String,
    val rawResponseSha256: String,
    val sampleSha256: String,
) {
    init {
        require(parserVersion.isNotBlank() && parserVersion.length <= 512 &&
            parserVersion.none(Char::isISOControl)) { "invalid accepted STT parser identity" }
        require(result.httpStatus in 100..599) { "invalid STT HTTP status" }
        require(isLowerSha256Evidence(rawResponseSha256)) { "invalid raw response SHA-256" }
        require(isLowerSha256Evidence(sampleSha256)) { "invalid transported sample SHA-256" }
    }

    val httpStatus: Int get() = result.httpStatus
}
