package com.clw.aivideotranslator

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
    internal fun requestBody(sourceText: String): String {
        require(sourceText.isNotBlank() && sourceText.length <= 1_000) { "نص الوحدة غير صالح" }
        return JSONObject()
            .put("model", MODEL_ID)
            .put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", "en-ar"))
                .put(JSONObject().put("role", "user").put("content", sourceText)))
            .put("temperature", 0)
            .put("max_tokens", 1024)
            .put("stream", false)
            .toString()
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
                            require(body.contentLength() <= 65_536) { "استجابة الترجمة كبيرة جدًا" }
                            val source = body.source()
                            source.request(65_537)
                            require(source.buffer.size <= 65_536) { "استجابة الترجمة كبيرة جدًا" }
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
        try {
            val choices = JSONObject(body).getJSONArray("choices")
            require(choices.length() == 1)
            val choice = choices.getJSONObject(0)
            require(choice.getString("finish_reason") == "stop")
            val message = choice.getJSONObject("message")
            require(message.getString("role") == "assistant")
            require(!message.has("tool_calls") || message.isNull("tool_calls"))
            val text = message.get("content")
            require(text is String)
            return SubtitlePipeline.validateText(text)
        } catch (_: Exception) {
            error("استجابة ترجمة غير صالحة أو غير مكتملة؛ لم يتم إنشاء SRT")
        }
    }
}
