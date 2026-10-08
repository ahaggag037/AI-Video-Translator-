package com.clw.aivideotranslator

import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
            parseResponse(body, response.code)
        }
    }

    /**
     * Additive same-response detailed path for durable-session/X001 evidence plumbing.
     * The accepted parse AND the high-fidelity raw timing evidence are derived from the exact
     * same HTTP response body of this one request — never from a re-request or a reconstructed
     * body. Raw response bytes stay in memory only; durable layers keep their SHA-256.
     * Legacy transcribeEnglishSample semantics, request shape and failure behavior are unchanged.
     */
    fun transcribeEnglishSampleDetailed(apiKey: String, wavFile: File): Result<NvidiaSttDetailedParse> = runCatching {
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
            NvidiaSttDetailedEvidenceParser.parse(body, response.code)
        }
    }

    internal fun parseResponse(body: String, httpStatus: Int = 200): NvidiaSttResult {
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

        val normalizedWords = normalizeTimes(words)
        return NvidiaSttResult(
            transcript = transcript,
            words = normalizedWords,
            httpStatus = httpStatus,
        )
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

    private fun normalizeTimes(raw: List<RawWord>): List<NvidiaWord> {
        if (raw.isEmpty()) return emptyList()
        val maxObserved = raw.mapNotNull { it.end ?: it.start }.maxOrNull() ?: 0.0
        // Riva's ASR protobuf describes word offsets in milliseconds. Some HTTP/realtime
        // representations use seconds, so keep a small compatibility guard for values
        // that are clearly second-scale for a one-minute prototype sample.
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

    private data class RawWord(
        val text: String,
        val start: Double?,
        val end: Double?,
        val confidence: Double?,
    )
}
