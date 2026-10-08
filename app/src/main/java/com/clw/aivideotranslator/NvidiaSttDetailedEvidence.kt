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
    val parserVersion: String,
)

internal object NvidiaSttDetailedEvidenceParser {
    fun parse(body: String, httpStatus: Int = 200): NvidiaSttDetailedParse {
        // Parse accepted semantics first. Evidence must never rescue or reinterpret a response that
        // the production parser rejects.
        val result = NvidiaSttClient.parseResponse(body, httpStatus)
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(body)
        return NvidiaSttDetailedParse(result, evidence, NvidiaSttParserContract.ID)
    }
}

/**
 * Immutable transport-bound observation of one actual hosted STT call. The request profile,
 * accepted parse (with parser identity), raw-response provenance and transported-sample digest
 * are bound together at the single response-handling point of the STT transport boundary, so a
 * durable snapshot never has to trust independently supplied provenance parts. The constructor
 * is module-internal: production instances originate only from the transport boundary; tests may
 * build fakes, but the snapshot factory still rejects any part that drifts from current contracts.
 * Raw response bytes stay in memory only; this object carries their SHA-256, not the body.
 */
internal data class NvidiaSttTransportObservation internal constructor(
    val requestProfile: NvidiaSttRequestProfile,
    val parsed: NvidiaSttDetailedParse,
    /** SHA-256 of the exact WAV bytes handed to the HTTP transport for this response. */
    val sampleSha256: String,
) {
    init {
        require(sampleSha256.length == 64 && sampleSha256.all { it in '0'..'9' || it in 'a'..'f' }) {
            "invalid transported sample SHA-256"
        }
    }

    val rawResponseSha256: String get() = parsed.timingEvidence.rawResponseSha256
    val httpStatus: Int get() = parsed.result.httpStatus
    val parserVersion: String get() = parsed.parserVersion
}
