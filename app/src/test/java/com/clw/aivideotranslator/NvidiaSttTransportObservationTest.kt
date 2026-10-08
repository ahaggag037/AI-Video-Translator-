package com.clw.aivideotranslator

import java.io.File
import java.security.MessageDigest
import okhttp3.MultipartBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NvidiaSttTransportObservationTest {
    private val responseBody = """
        {
          "text": "Hello world.",
          "words": [
            {"word":"Hello","start":0.10,"end":0.25,"confidence":0.9},
            {"word":"world.","start":0.30,"end":0.80,"confidence":0.8}
          ]
        }
    """.trimIndent()

    private fun sampleDigest() = "e".repeat(64)

    private fun utf8Sha(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun expectFailure(block: () -> Unit): Exception {
        try {
            block()
            fail("must fail")
        } catch (error: Exception) {
            return error
        }
    }

    @Test fun successfulResponseBindsProfileResultParserDigestsAndStatus() {
        val observation = NvidiaSttClient.bindDetailedResponse(responseBody, 200, sampleDigest())
        assertEquals(NvidiaSttWireContract.PROFILE, observation.requestProfile)
        assertEquals("Hello world.", observation.result.transcript)
        assertEquals(listOf("Hello", "world."), observation.result.words.map { it.text })
        assertEquals(NvidiaSttParserContract.ID, observation.parserVersion)
        assertEquals(utf8Sha(responseBody), observation.rawResponseSha256)
        assertEquals(sampleDigest(), observation.sampleSha256)
        assertEquals(200, observation.httpStatus)
    }

    @Test fun observationGraphIsStructurallyRedacted() {
        // The durable provenance type must not expose the verbatim body, the timing-evidence graph
        // that nests it, or the old combined detailed-parse shape.
        val fieldNames = NvidiaSttTransportObservation::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.contains("rawResponseUtf8") })
        assertFalse(fieldNames.any { it.contains("timingEvidence") })
        assertFalse(fieldNames.any { it.contains("parsed") })
        assertEquals(
            setOf("requestProfile", "result", "parserVersion", "rawResponseSha256", "sampleSha256"),
            fieldNames.toSet(),
        )
    }

    @Test fun nonSuccessStatusesNeverProduceAnObservationAndKeepLegacyFailureShape() {
        listOf(400, 401, 403, 429, 500).forEach { code ->
            val error = expectFailure {
                NvidiaSttClient.bindDetailedResponse("""{"detail":"throttled"}""", code, sampleDigest())
            }
            assertEquals("NVIDIA HTTP $code: throttled", error.message)
        }

        // Non-JSON error bodies fall back to the truncated raw body exactly like the legacy handler.
        val fallback = expectFailure {
            NvidiaSttClient.bindDetailedResponse("<html>bad gateway</html>", 502, sampleDigest())
        }
        assertEquals("NVIDIA HTTP 502: <html>bad gateway</html>", fallback.message)
    }

    @Test fun transportedBytesMultipartMatchesLegacyFileMultipartShape() {
        val bytes = ByteArray(256) { index -> ((index * 7) and 0x7F).toByte() }
        val wav = File.createTempFile("wire-equivalence", ".wav").apply { writeBytes(bytes) }
        try {
            val fromFile = NvidiaSttWireContract.multipartBody(wav)
            val fromBytes = NvidiaSttWireContract.multipartBody(wav.name, bytes)
            assertEquals(fromFile.contentLength(), fromBytes.contentLength())
            assertEquals(renderNormalized(fromFile), renderNormalized(fromBytes))
        } finally {
            wav.delete()
        }
    }

    @Test fun observationRejectsMalformedDigestsAndParserIdentity() {
        val result = NvidiaSttClient.parseResponse(responseBody, 200)
        val goodRaw = utf8Sha(responseBody)
        val goodSample = sampleDigest()
        expectFailure {
            NvidiaSttTransportObservation(NvidiaSttWireContract.PROFILE, result, " ", goodRaw, goodSample)
        }
        expectFailure {
            NvidiaSttTransportObservation(NvidiaSttWireContract.PROFILE, result, NvidiaSttParserContract.ID, "zz", goodSample)
        }
        expectFailure {
            NvidiaSttTransportObservation(
                NvidiaSttWireContract.PROFILE, result, NvidiaSttParserContract.ID,
                "A".repeat(64), goodSample,
            )
        }
        expectFailure {
            NvidiaSttTransportObservation(
                NvidiaSttWireContract.PROFILE, result, NvidiaSttParserContract.ID, goodRaw, "e".repeat(63),
            )
        }
        // Positive control: the well-formed observation constructs.
        NvidiaSttTransportObservation(
            NvidiaSttWireContract.PROFILE, result, NvidiaSttParserContract.ID, goodRaw, goodSample,
        )
    }

    private fun renderNormalized(body: MultipartBody): String {
        val buffer = Buffer()
        body.writeTo(buffer)
        val boundary = body.contentType().parameter("boundary")
            ?: error("multipart boundary missing")
        return buffer.readUtf8().replace(boundary, "<BOUNDARY>")
    }
}
