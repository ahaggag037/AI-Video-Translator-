package com.clw.aivideotranslator

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

data class NvidiaWord(
    val text: String,
    val startMs: Long?,
    val endMs: Long?,
    val confidence: Double?,
)

data class NvidiaSttResult(
    val transcript: String,
    val words: List<NvidiaWord>,
    val httpStatus: Int,
) {
    val firstWordStartMs: Long? get() = words.firstOrNull()?.startMs
    val lastWordEndMs: Long? get() = words.lastOrNull()?.endMs
}

object NvidiaSttClient {
    const val MODEL_LABEL = "NVIDIA Parakeet CTC 1.1B (en-US)"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .callTimeout(4, TimeUnit.MINUTES)
        .build()

    fun transcribeEnglishSample(apiKey: String, wavFile: File): Result<NvidiaSttResult> = runCatching {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        require(wavFile.exists() && wavFile.length() > 44L) { "ملف WAV غير صالح" }

        val request = NvidiaSttWireContract.request(cleanKey, wavFile)

        client.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                val detail = runCatching {
                    JSONObject(body).optString("detail").ifBlank { body.take(300) }
                }.getOrDefault(body.take(300))
                error("NVIDIA HTTP ${response.code}: ${detail.ifBlank { "فشل الطلب" }}")
            }
            // X001: hosted offsets are preserved only as diagnostic raw evidence until unit+origin
            // are proven. Production transport must never infer a timing unit from scalar magnitude.
            parseResponseWithoutTimingAuthority(body, response.code)
        }
    }

    /**
     * Additive transport boundary for durable-session evidence plumbing. The exact sample bytes are
     * materialized in memory and hashed BEFORE the request exists, the request streams exactly those
     * bytes, and the redacted observation (accepted text/confidence + parser identity + UTF-8 text
     * digest of the response + pre-send sample digest) is bound at the single response-handling
     * point. Raw provider offsets are deliberately not interpreted here while X001 is unverified.
     * The verbatim response body never enters the durable observation graph.
     */
    internal fun transcribeEnglishSampleDetailed(
        apiKey: String,
        wavFile: File,
    ): Result<NvidiaSttTransportObservation> = runCatching {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        require(wavFile.exists() && wavFile.length() > 44L) { "ملف WAV غير صالح" }

        // Authoritative sample anchor: these in-memory bytes are what the request will stream.
        val sampleBytes = wavFile.readBytes()
        require(sampleBytes.size.toLong() == wavFile.length()) { "prepared WAV changed while being read" }
        val sampleSha256 = sha256Bytes(sampleBytes)

        val request = NvidiaSttWireContract.request(cleanKey, wavFile.name, sampleBytes)

        client.newCall(request).execute().use { response ->
            bindDetailedResponse(response.body.string(), response.code, sampleSha256)
        }
    }

    /**
     * Single response-handling point for the durable STT path. Separated from the network call so
     * the failure/binding contract is unit-testable without a provider. Failure semantics mirror
     * the legacy handler; a non-2xx status yields an exception, never an observation.
     */
    internal fun bindDetailedResponse(
        body: String,
        httpStatus: Int,
        sampleSha256: String,
    ): NvidiaSttTransportObservation {
        if (httpStatus !in 200..299) {
            val detail = runCatching {
                JSONObject(body).optString("detail").ifBlank { body.take(300) }
            }.getOrDefault(body.take(300))
            error("NVIDIA HTTP $httpStatus: ${detail.ifBlank { "فشل الطلب" }}")
        }
        return NvidiaSttTransportObservation(
            requestProfile = NvidiaSttWireContract.PROFILE,
            result = parseResponseWithoutTimingAuthority(body, httpStatus),
            parserVersion = NvidiaSttParserContract.ID,
            rawResponseSha256 = NvidiaSttTimingEvidenceInspector.inspect(body).rawResponseSha256,
            sampleSha256 = sampleSha256,
        )
    }

    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

    /**
     * Historical P0 comparator parser used only by synthetic regression fixtures. It intentionally
     * retains the pre-X001 magnitude heuristic so tests can demonstrate the legacy ambiguity. Never
     * call this from provider transport, durable adoption, preview, or export code.
     */
    internal fun parseResponse(body: String, httpStatus: Int = 200): NvidiaSttResult {
        val parsed = parseWireResponse(body)
        return NvidiaSttResult(
            transcript = parsed.transcript,
            words = normalizeLegacyTimes(parsed.words),
            httpStatus = httpStatus,
        )
    }

    /**
     * Production-safe parse while X001 is open: preserve accepted text/confidence, but expose no
     * interpreted offset at all. Raw timing fields remain available only through
     * [NvidiaSttTimingEvidenceInspector] for evidence capture and explicit mapping.
     */
    internal fun parseResponseWithoutTimingAuthority(body: String, httpStatus: Int = 200): NvidiaSttResult {
        val parsed = parseWireResponse(body)
        return NvidiaSttResult(
            transcript = parsed.transcript,
            words = parsed.words.map { raw ->
                NvidiaWord(
                    text = raw.text,
                    startMs = null,
                    endMs = null,
                    confidence = raw.confidence,
                )
            },
            httpStatus = httpStatus,
        )
    }

    private fun parseWireResponse(body: String): ParsedWireResponse {
        val root = JSONObject(body)
        val words = mutableListOf<RawWord>()
        val transcriptParts = mutableListOf<String>()

        root.optString("text").takeIf { it.isNotBlank() }?.let(transcriptParts::add)
        root.optString("transcript").takeIf { it.isNotBlank() }?.let(transcriptParts::add)

        val results = root.optJSONArray("results")
        if (results != null) {
            for (i in 0 until results.length()) {
                val result = results.optJSONObject(i) ?: continue
                val alternatives = result.optJSONArray("alternatives") ?: continue
                if (alternatives.length() == 0) continue
                val alternative = alternatives.optJSONObject(0) ?: continue
                alternative.optString("transcript")
                    .takeIf { it.isNotBlank() }
                    ?.let(transcriptParts::add)
                readWords(alternative.optJSONArray("words"), words)
            }
        }

        val wordsInfo = root.optJSONObject("words_info")
        readWords(wordsInfo?.optJSONArray("words"), words)
        readWords(root.optJSONArray("words"), words)

        val transcript = transcriptParts
            .joinToString(" ")
            .replace(Regex("\\s+"), " ")
            .trim()
        require(transcript.isNotBlank()) { "استجابة NVIDIA لا تحتوي نص تفريغ معروف البنية" }
        return ParsedWireResponse(transcript, words)
    }

    private fun readWords(array: JSONArray?, target: MutableList<RawWord>) {
        if (array == null) return
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val text = item.optString("word").ifBlank { item.optString("text") }
            if (text.isBlank()) continue
            target += RawWord(
                text = text,
                start = item.optNullableDouble("start_time")
                    ?: item.optNullableDouble("start")
                    ?: item.optNullableDouble("start_ms"),
                end = item.optNullableDouble("end_time")
                    ?: item.optNullableDouble("end")
                    ?: item.optNullableDouble("end_ms"),
                confidence = item.optNullableDouble("confidence"),
            )
        }
    }

    private fun normalizeLegacyTimes(raw: List<RawWord>): List<NvidiaWord> {
        if (raw.isEmpty()) return emptyList()
        val maxObserved = raw.mapNotNull { it.end ?: it.start }.maxOrNull() ?: 0.0
        val valuesAreSeconds = maxObserved in 0.0..120.0

        fun toMs(value: Double?): Long? = value?.let {
            if (valuesAreSeconds) (it * 1_000.0).toLong() else it.toLong()
        }

        return raw.map {
            NvidiaWord(
                text = it.text,
                startMs = toMs(it.start),
                endMs = toMs(it.end),
                confidence = it.confidence,
            )
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

    private data class ParsedWireResponse(
        val transcript: String,
        val words: List<RawWord>,
    )

    private data class RawWord(
        val text: String,
        val start: Double?,
        val end: Double?,
        val confidence: Double?,
    )
}
