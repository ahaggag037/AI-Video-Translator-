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

    private val client = buildHttpClient()

    internal fun buildHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.MINUTES)
        .writeTimeout(3, TimeUnit.MINUTES)
        .callTimeout(4, TimeUnit.MINUTES)
        .followRedirects(NvidiaSttWireContract.FOLLOW_REDIRECTS)
        .retryOnConnectionFailure(NvidiaSttWireContract.RETRY_ON_CONNECTION_FAILURE)
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

    internal fun transcribeEnglishSampleDetailed(
        apiKey: String,
        wavFile: File,
    ): Result<NvidiaSttTransportObservation> = runCatching {
        val cleanKey = apiKey.trim()
        require(cleanKey.isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        require(wavFile.exists() && wavFile.length() > 44L) { "ملف WAV غير صالح" }

        val sampleBytes = wavFile.readBytes()
        require(sampleBytes.size.toLong() == wavFile.length()) { "prepared WAV changed while being read" }
        val sampleSha256 = sha256Bytes(sampleBytes)
        val request = NvidiaSttWireContract.request(cleanKey, wavFile.name, sampleBytes)

        client.newCall(request).execute().use { response ->
            bindDetailedResponse(response.body.string(), response.code, sampleSha256)
        }
    }

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
            result = parseResponse(body, httpStatus),
            parserVersion = NvidiaSttParserContract.ID,
            rawResponseSha256 = NvidiaSttTimingEvidenceInspector.inspect(body).rawResponseSha256,
            sampleSha256 = sampleSha256,
        )
    }

    private fun sha256Bytes(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }

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

        return NvidiaSttResult(
            transcript = transcript,
            words = normalizeTimes(words),
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

    /**
     * NVIDIA Riva ASR WordInfo defines start_time/end_time as millisecond offsets relative to the
     * beginning of the submitted audio. Do not infer seconds from numeric magnitude: short valid
     * millisecond offsets such as 80..100 would otherwise be corrupted by a 1000x heuristic.
     */
    private fun normalizeTimes(raw: List<RawWord>): List<NvidiaWord> = raw.map { word ->
        NvidiaWord(
            text = word.text,
            startMs = word.start?.toLong(),
            endMs = word.end?.toLong(),
            confidence = word.confidence,
        )
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
