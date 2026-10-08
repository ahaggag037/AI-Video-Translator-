package com.clw.aivideotranslator

import com.clw.aivideotranslator.provider.ProtocolOutcome
import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.provider.TranslationResponseClassifier
import com.clw.aivideotranslator.provider.TransportOutcome
import com.clw.aivideotranslator.semantic.TranslationProfile
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object NvidiaTranslationClient {
    const val MODEL_ID = "nvidia/riva-translate-4b-instruct-v2"
    const val ENDPOINT = "https://integrate.api.nvidia.com/v1/chat/completions"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(120, TimeUnit.SECONDS)
        .followRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    // IDs and times never cross this interface. One request owns exactly one text result.
    internal fun requestBody(sourceText: String): String =
        requestBody(TranslationProfile(), sourceText)

    internal fun requestBody(plan: TranslationRequestPlan): String =
        requestBody(plan.profile, plan.exactSourceText)

    private fun requestBody(profile: TranslationProfile, sourceText: String): String {
        require(sourceText.isNotBlank() && sourceText.length <= 1_000) { "نص الوحدة غير صالح" }
        return JSONObject()
            .put("model", profile.model)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", profile.systemContent))
                .put(JSONObject().put("role", "user").put("content", sourceText)))
            .put("temperature", profile.temperature)
            .put("max_tokens", profile.maxTokens)
            .put("stream", profile.stream)
            .toString()
    }

    /**
     * Additive V1 transport entry point. It preserves the exact legacy request body while
     * returning structured provider dimensions for durable session/recovery code.
     *
     * An OkHttp failure is conservatively treated as an unknown remote outcome because the
     * callback alone cannot prove the request body never reached the provider. This prevents
     * callers from blindly re-posting a request that may already have executed remotely.
     */
    suspend fun translateDetailed(apiKey: String, sourceText: String): TranslationProviderOutcome =
        executeDetailed(apiKey, ENDPOINT, requestBody(sourceText))

    suspend fun translateDetailed(apiKey: String, plan: TranslationRequestPlan): TranslationProviderOutcome {
        NvidiaTranslationPlanContract.requireSupported(plan)
        return executeDetailed(apiKey, plan.profile.endpoint, requestBody(plan))
    }

    private suspend fun executeDetailed(
        apiKey: String,
        endpoint: String,
        body: String,
    ): TranslationProviderOutcome {
        require(apiKey.trim().isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        val request = Request.Builder().url(endpoint)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "application/json")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) {
                        continuation.resume(
                            TranslationResponseClassifier.transportFailure(mayHaveBeenSubmitted = true)
                        )
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    val outcome = try {
                        response.use {
                            val retryAfterMs = parseRetryAfterMs(it.header("Retry-After"))
                            val responseBody = it.body
                            if (responseBody.contentLength() > TranslationResponseClassifier.MAX_BODY_BYTES) {
                                oversizedResponse(it.code, retryAfterMs)
                            } else {
                                val source = responseBody.source()
                                source.request((TranslationResponseClassifier.MAX_BODY_BYTES + 1).toLong())
                                if (source.buffer.size > TranslationResponseClassifier.MAX_BODY_BYTES) {
                                    oversizedResponse(it.code, retryAfterMs)
                                } else {
                                    TranslationResponseClassifier.classify(
                                        httpStatus = it.code,
                                        body = source.readUtf8(),
                                        retryAfterMs = retryAfterMs,
                                    )
                                }
                            }
                        }
                    } catch (_: IOException) {
                        TranslationResponseClassifier.transportFailure(mayHaveBeenSubmitted = true)
                    }
                    if (continuation.isActive) continuation.resume(outcome)
                }
            })
        }
    }

    suspend fun translate(apiKey: String, sourceText: String): String {
        require(apiKey.trim().isNotEmpty()) { "أدخل NVIDIA API Key أولًا" }
        val request = Request.Builder().url(ENDPOINT)
            .header("Authorization", "Bearer ${apiKey.trim()}")
            .header("Accept", "application/json")
            .post(requestBody(sourceText).toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(
                        IOException("تعذر الاتصال بـ NVIDIA؛ تحقق من الشبكة وأعد المحاولة"))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            when (it.code) {
                                200 -> Unit
                                401, 403 -> error("NVIDIA: المفتاح غير صالح أو لا يملك صلاحية هذا النموذج")
                                429 -> error("NVIDIA: تم بلوغ حد الطلبات؛ انتظر ثم أعد المحاولة")
                                202 -> error("NVIDIA: الطلب ما زال معلقًا؛ لم تُعتمد ترجمة")
                                else -> error("فشل الترجمة: NVIDIA HTTP ${it.code}")
                            }
                            val body = it.body
                            if (body.contentLength() > 65_536) {
                                invalidResponse("استجابة الترجمة كبيرة جدًا")
                            }
                            val source = body.source()
                            source.request(65_537)
                            if (source.buffer.size > 65_536) {
                                invalidResponse("استجابة الترجمة كبيرة جدًا")
                            }
                            parseResponse(source.readUtf8())
                        }
                    }
                    if (continuation.isActive) result.fold(
                        onSuccess = { continuation.resume(it) },
                        onFailure = { continuation.resumeWithException(it) },
                    )
                }
            })
        }
    }

    internal fun parseResponse(body: String): String {
        val root = runCatching { JSONObject(body) }.getOrElse {
            invalidResponse("استجابة NVIDIA ليست JSON صالحًا؛ لم يتم إنشاء SRT")
        }
        val choices = root.optJSONArray("choices")
            ?: invalidResponse("استجابة NVIDIA بلا choices؛ لم يتم إنشاء SRT")
        if (choices.length() == 0) {
            invalidResponse("استجابة NVIDIA بلا نتيجة ترجمة؛ لم يتم إنشاء SRT")
        }

        val choice = (0 until choices.length())
            .mapNotNull { index -> choices.optJSONObject(index) }
            .firstOrNull { it.optJSONObject("message") != null }
            ?: invalidResponse("استجابة NVIDIA بلا message؛ لم يتم إنشاء SRT")

        val finishReason = if (!choice.has("finish_reason") || choice.isNull("finish_reason")) {
            null
        } else {
            choice.optString("finish_reason").trim().ifEmpty { null }
        }
        when (finishReason) {
            null, "stop" -> Unit
            "length" -> invalidResponse("NVIDIA أوقفت الترجمة بسبب حد الطول؛ لم يتم إنشاء SRT")
            "content_filter" -> invalidResponse("NVIDIA أوقفت الترجمة بمرشح المحتوى؛ لم يتم إنشاء SRT")
            else -> invalidResponse("استجابة NVIDIA انتهت بحالة غير متوقعة ($finishReason)؛ لم يتم إنشاء SRT")
        }

        val message = choice.getJSONObject("message")
        if (message.has("role") && !message.isNull("role")) {
            val role = message.optString("role").trim()
            if (role.isNotEmpty() && role != "assistant") {
                invalidResponse("استجابة NVIDIA بدور غير متوقع؛ لم يتم إنشاء SRT")
            }
        }

        // Some OpenAI-compatible NVIDIA responses include `tool_calls: []` even though no
        // tool was invoked. Empty metadata is harmless; an actual non-empty tool call is not
        // a translation result and must still be rejected.
        if (message.has("tool_calls") && !message.isNull("tool_calls")) {
            when (val toolCalls = message.opt("tool_calls")) {
                is JSONArray -> if (toolCalls.length() > 0) {
                    invalidResponse("استجابة NVIDIA احتوت tool_calls فعلية غير متوقعة؛ لم يتم إنشاء SRT")
                }
                else -> invalidResponse("استجابة NVIDIA احتوت tool_calls بصيغة غير متوقعة؛ لم يتم إنشاء SRT")
            }
        }

        val text = extractTextContent(message.opt("content"))
        return try {
            SubtitlePipeline.validateText(text)
        } catch (_: IllegalArgumentException) {
            invalidResponse("استجابة NVIDIA احتوت نص ترجمة غير صالح؛ لم يتم إنشاء SRT")
        }
    }

    internal fun parseRetryAfterMs(value: String?): Long? {
        val seconds = value?.trim()?.toLongOrNull() ?: return null
        if (seconds < 0L) return null
        return runCatching { Math.multiplyExact(seconds, 1_000L) }.getOrNull()
    }

    private fun oversizedResponse(httpStatus: Int, retryAfterMs: Long?): TranslationProviderOutcome =
        TranslationProviderOutcome(
            transport = TransportOutcome.RESPONSE_RECEIVED,
            protocol = ProtocolOutcome.MALFORMED,
            httpStatus = httpStatus,
            retryAfterMs = retryAfterMs,
            diagnosticCode = "BODY_TOO_LARGE",
        )

    private fun extractTextContent(content: Any?): String {
        val text = when (content) {
            is String -> content
            is JSONArray -> buildString {
                for (index in 0 until content.length()) {
                    when (val part = content.opt(index)) {
                        is String -> append(part)
                        is JSONObject -> {
                            val type = part.optString("type")
                            val value = part.opt("text")
                            if (value is String && (type.isBlank() || type == "text" || type == "output_text")) {
                                append(value)
                            }
                        }
                    }
                }
            }
            is JSONObject -> {
                val value = content.opt("text")
                if (value !is String) {
                    invalidResponse("استجابة NVIDIA بلا نص ترجمة؛ لم يتم إنشاء SRT")
                }
                value
            }
            else -> invalidResponse("استجابة NVIDIA بلا نص ترجمة؛ لم يتم إنشاء SRT")
        }
        if (text.isBlank()) {
            invalidResponse("استجابة NVIDIA بلا نص ترجمة؛ لم يتم إنشاء SRT")
        }
        return text
    }

    private fun invalidResponse(message: String): Nothing = throw IllegalStateException(message)
}
