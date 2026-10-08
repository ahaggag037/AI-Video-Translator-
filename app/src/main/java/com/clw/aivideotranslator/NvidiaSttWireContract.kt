package com.clw.aivideotranslator

import com.clw.aivideotranslator.semantic.sha256Utf8
import java.io.File
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Versioned, non-secret semantic identity of the hosted NVIDIA STT request.
 * The audio bytes themselves are deliberately outside this profile; SourcePcmSampleIdentity owns them.
 */
internal data class NvidiaSttRequestProfile(
    val providerId: String,
    val modelId: String,
    val endpoint: String,
    val method: String,
    val language: String,
    val wordTimeOffsets: String,
    val fileMediaType: String,
) {
    init {
        require(providerId.isNotBlank() && modelId.isNotBlank()) { "invalid STT provider/model identity" }
        require(endpoint.startsWith("https://")) { "STT endpoint must use HTTPS" }
        require(method == "POST") { "unsupported STT method" }
        require(language.isNotBlank() && wordTimeOffsets.isNotBlank()) { "invalid STT form profile" }
        require(fileMediaType == "audio/wav") { "unsupported STT sample media type" }
    }

    val profileId: String
        get() {
            val fields = listOf(
                "nvidia-stt-request-profile-v1",
                providerId,
                modelId,
                endpoint,
                method,
                language,
                wordTimeOffsets,
                fileMediaType,
            )
            val canonical = fields.joinToString("") { value ->
                "${value.toByteArray(Charsets.UTF_8).size}:$value"
            }
            return "stt-profile-${sha256Utf8(canonical)}"
        }
}

/**
 * Single source for the current hosted Parakeet HTTP request semantics. The values mirror NVIDIA's
 * published HTTP example for this NVCF endpoint. Authorization is intentionally not identity data.
 */
internal object NvidiaSttWireContract {
    val PROFILE = NvidiaSttRequestProfile(
        providerId = "nvidia",
        modelId = "nvidia/parakeet-ctc-1_1b-asr",
        endpoint = "https://1598d209-5e27-4d3c-8079-4751568b1081.invocation.api.nvcf.nvidia.com/v1/audio/transcriptions",
        method = "POST",
        language = "en-US",
        wordTimeOffsets = "True",
        fileMediaType = "audio/wav",
    )

    fun multipartBody(wavFile: File): MultipartBody {
        require(wavFile.exists() && wavFile.length() > 44L) { "ملف WAV غير صالح" }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("language", PROFILE.language)
            .addFormDataPart("word_time_offsets", PROFILE.wordTimeOffsets)
            .addFormDataPart(
                "file",
                wavFile.name,
                wavFile.asRequestBody(PROFILE.fileMediaType.toMediaType()),
            )
            .build()
    }

    /**
     * Same multipart shape as the file-based overload, built from already-materialized sample bytes.
     * The durable transport path uses this so the exact streamed bytes are known and hashable before
     * the request can observe any mutation of the source path.
     */
    fun multipartBody(fileName: String, wavBytes: ByteArray): MultipartBody {
        require(wavBytes.size > 44) { "ملف WAV غير صالح" }
        return MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("language", PROFILE.language)
            .addFormDataPart("word_time_offsets", PROFILE.wordTimeOffsets)
            .addFormDataPart(
                "file",
                fileName,
                wavBytes.toRequestBody(PROFILE.fileMediaType.toMediaType()),
            )
            .build()
    }

    fun request(cleanApiKey: String, wavFile: File): Request {
        require(cleanApiKey.isNotBlank()) { "أدخل NVIDIA API Key أولًا" }
        return Request.Builder()
            .url(PROFILE.endpoint)
            .header("Authorization", "Bearer $cleanApiKey")
            .method(PROFILE.method, multipartBody(wavFile))
            .build()
    }

    fun request(cleanApiKey: String, fileName: String, wavBytes: ByteArray): Request {
        require(cleanApiKey.isNotBlank()) { "أدخل NVIDIA API Key أولًا" }
        return Request.Builder()
            .url(PROFILE.endpoint)
            .header("Authorization", "Bearer $cleanApiKey")
            .method(PROFILE.method, multipartBody(fileName, wavBytes))
            .build()
    }
}
