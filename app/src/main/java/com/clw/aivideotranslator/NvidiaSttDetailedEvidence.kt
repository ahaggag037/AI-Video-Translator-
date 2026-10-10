package com.clw.aivideotranslator

/** Versioned identity of the fail-closed STT parser semantics used by durable transport. */
internal object NvidiaSttParserContract {
    const val ID = "nvidia-stt-unverified-timing-parser-v2"
}

/**
 * Diagnostic/durability bridge only. Accepted text/confidence follows the production fail-closed
 * parser; raw timing evidence is captured beside it without selecting a schema, unit, or origin.
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
        // Parse accepted text semantics first. Evidence must never rescue or reinterpret a response
        // that the production parser rejects, and raw offsets remain uninterpreted until X001 proof.
        val result = NvidiaSttClient.parseResponseWithoutTimingAuthority(body, httpStatus)
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(body)
        return NvidiaSttDetailedParse(result, evidence, NvidiaSttParserContract.ID)
    }
}

private fun isLowerSha256Evidence(value: String): Boolean =
    value.length == 64 && value.all { it in '0'..'9' || it in 'a'..'f' }

/**
 * Immutable REDACTED transport-bound observation of one actual hosted STT call, constructed only at
 * the single response-handling point of the STT transport boundary. It deliberately carries NO
 * verbatim response body: accepted result + parser identity + digests only. `rawResponseSha256` is
 * SHA-256 of the UTF-8 JSON text exactly as decoded by the response handler (UTF-8 text identity,
 * not transport-octet identity; identical definition to NvidiaSttTimingEvidenceInspector).
 * `sampleSha256` is SHA-256 of the exact WAV bytes that were materialized in memory and streamed by
 * this request — pinned BEFORE the request body could observe any mutation of the source path.
 * The module-internal constructor means production instances originate only from the transport
 * boundary; tests may build fakes, but the snapshot factory re-verifies every checkable component
 * (profile currency, parser identity, HTTP status, sample digest against a fresh file inspection).
 */
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
