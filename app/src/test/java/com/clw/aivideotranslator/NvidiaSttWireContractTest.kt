package com.clw.aivideotranslator

import java.io.File
import okio.Buffer
import okhttp3.MultipartBody
import okhttp3.RequestBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NvidiaSttWireContractTest {
    @Test fun profileIdentityBindsEveryNonSecretRequestSemantic() {
        val profile = NvidiaSttWireContract.PROFILE
        assertEquals("nvidia", profile.providerId)
        assertEquals("nvidia/parakeet-ctc-1_1b-asr", profile.modelId)
        assertEquals(
            "https://1598d209-5e27-4d3c-8079-4751568b1081.invocation.api.nvcf.nvidia.com/v1/audio/transcriptions",
            profile.endpoint,
        )
        assertEquals("POST", profile.method)
        assertEquals("en-US", profile.language)
        assertEquals("True", profile.wordTimeOffsets)
        assertEquals("audio/wav", profile.fileMediaType)

        listOf(
            profile.copy(providerId = "other"),
            profile.copy(modelId = "other/model"),
            profile.copy(endpoint = "https://example.invalid/v1/audio/transcriptions"),
            profile.copy(language = "en-GB"),
            profile.copy(wordTimeOffsets = "False"),
        ).forEach { changed -> assertNotEquals(profile.profileId, changed.profileId) }
    }

    @Test fun serializedMultipartHasExactlyTheSignedSemanticFields() {
        val wav = File.createTempFile("stt-wire-contract", ".wav")
        try {
            wav.writeBytes(ByteArray(48) { index -> index.toByte() })
            val request = NvidiaSttWireContract.request("test-key", wav)
            assertEquals(NvidiaSttWireContract.PROFILE.endpoint, request.url.toString())
            assertEquals(NvidiaSttWireContract.PROFILE.method, request.method)

            val body = request.body as MultipartBody
            assertEquals(MultipartBody.FORM, body.type)
            assertEquals(3, body.parts.size)

            val language = body.parts[0]
            assertEquals("form-data; name=\"language\"", language.headers?.get("Content-Disposition"))
            assertEquals("en-US", language.body.utf8())

            val offsets = body.parts[1]
            assertEquals("form-data; name=\"word_time_offsets\"", offsets.headers?.get("Content-Disposition"))
            assertEquals("True", offsets.body.utf8())

            val file = body.parts[2]
            assertEquals(
                "form-data; name=\"file\"; filename=\"${wav.name}\"",
                file.headers?.get("Content-Disposition"),
            )
            assertEquals("audio/wav", file.body.contentType().toString())
            assertEquals(wav.readBytes().toList(), file.body.bytes().toList())
        } finally {
            wav.delete()
        }
    }

    private fun RequestBody.utf8(): String {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readUtf8()
    }

    private fun RequestBody.bytes(): ByteArray {
        val buffer = Buffer()
        writeTo(buffer)
        return buffer.readByteArray()
    }
}
