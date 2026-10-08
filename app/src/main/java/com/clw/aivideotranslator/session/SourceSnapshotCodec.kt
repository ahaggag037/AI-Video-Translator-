package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.AudioIntervalUs
import com.clw.aivideotranslator.semantic.AudioTimeUs
import com.clw.aivideotranslator.semantic.ClockVerificationStatus
import org.json.JSONArray
import org.json.JSONObject

object SourceSnapshotCodec {
    const val MAX_BYTES = 1_048_576

    fun encode(value: SourceSnapshot): String {
        val words = JSONArray()
        value.words.forEach { word ->
            words.put(JSONObject()
                .put("ordinal", word.ordinal)
                .put("rawText", word.rawText)
                .put("confidenceBits", word.confidence?.toRawBits() ?: JSONObject.NULL)
                .put("audioStartUs", word.audioInterval?.start?.value ?: JSONObject.NULL)
                .put("audioEndUs", word.audioInterval?.end?.value ?: JSONObject.NULL))
        }
        return JSONObject()
            .put("schemaVersion", SOURCE_SNAPSHOT_SCHEMA_VERSION)
            .put("snapshotId", value.snapshotId)
            .put("sessionId", value.sessionId)
            .put("sourceAttachmentId", value.sourceAttachmentId)
            .put("transcript", value.transcript)
            .put("sourceTextHash", value.sourceTextHash)
            .put("words", words)
            .put("pcmSample", JSONObject()
                .put("wavSha256", value.pcmSample.wavSha256)
                .put("wavSizeBytes", value.pcmSample.wavSizeBytes)
                .put("pcmFrameCount", value.pcmSample.pcmFrameCount)
                .put("sampleRateHz", value.pcmSample.sampleRateHz)
                .put("channelCount", value.pcmSample.channelCount)
                .put("bitsPerSample", value.pcmSample.bitsPerSample))
            .put("stt", JSONObject()
                .put("providerId", value.stt.providerId)
                .put("modelId", value.stt.modelId)
                .put("requestProfileId", value.stt.requestProfileId)
                .put("parserVersion", value.stt.parserVersion)
                .put("rawResponseSha256", value.stt.rawResponseSha256)
                .put("httpStatus", value.stt.httpStatus))
            .put("clock", JSONObject()
                .put("observedPresentationOriginUs", value.clock.observedPresentationOriginUs)
                .put("verificationStatus", value.clock.verificationStatus.name)
                .put("precisionUs", value.clock.precisionUs ?: JSONObject.NULL)
                .put("evidenceProfile", value.clock.evidenceProfile ?: JSONObject.NULL))
            .toString().also(::requireBounded)
    }

    fun decode(json: String): SourceSnapshot {
        requireBounded(json)
        val root = JSONObject(json)
        root.requireKeys(setOf("schemaVersion", "snapshotId", "sessionId", "sourceAttachmentId", "transcript",
            "sourceTextHash", "words", "pcmSample", "stt", "clock"))
        require(root.strictLong("schemaVersion") == SOURCE_SNAPSHOT_SCHEMA_VERSION.toLong()) {
            "unsupported source snapshot schema"
        }
        val pcm = root.getJSONObject("pcmSample").also {
            it.requireKeys(setOf("wavSha256", "wavSizeBytes", "pcmFrameCount", "sampleRateHz", "channelCount", "bitsPerSample"))
        }
        val stt = root.getJSONObject("stt").also {
            it.requireKeys(setOf("providerId", "modelId", "requestProfileId", "parserVersion", "rawResponseSha256", "httpStatus"))
        }
        val clock = root.getJSONObject("clock").also {
            it.requireKeys(setOf("observedPresentationOriginUs", "verificationStatus", "precisionUs", "evidenceProfile"))
        }
        val wordsJson = root.getJSONArray("words")
        val words = buildList {
            for (index in 0 until wordsJson.length()) {
                val word = wordsJson.getJSONObject(index)
                word.requireKeys(setOf("ordinal", "rawText", "confidenceBits", "audioStartUs", "audioEndUs"))
                val start = word.nullableStrictLong("audioStartUs")
                val end = word.nullableStrictLong("audioEndUs")
                require((start == null) == (end == null)) { "partial source word interval" }
                val confidenceBits = word.nullableStrictLong("confidenceBits")
                add(SourceSnapshotWord(
                    ordinal = Math.toIntExact(word.strictLong("ordinal")),
                    rawText = word.strictString("rawText"),
                    confidence = confidenceBits?.let(Double::fromBits),
                    audioInterval = if (start == null) null else AudioIntervalUs(AudioTimeUs(start), AudioTimeUs(requireNotNull(end))),
                ))
            }
        }
        val value = SourceSnapshot(
            sessionId = root.strictString("sessionId"),
            sourceAttachmentId = root.strictString("sourceAttachmentId"),
            transcript = root.strictString("transcript"),
            words = words,
            pcmSample = SourcePcmSampleIdentity(
                wavSha256 = pcm.strictString("wavSha256"),
                wavSizeBytes = pcm.strictLong("wavSizeBytes"),
                pcmFrameCount = pcm.strictLong("pcmFrameCount"),
                sampleRateHz = Math.toIntExact(pcm.strictLong("sampleRateHz")),
                channelCount = Math.toIntExact(pcm.strictLong("channelCount")),
                bitsPerSample = Math.toIntExact(pcm.strictLong("bitsPerSample")),
            ),
            stt = SourceSttProvenance(
                providerId = stt.strictString("providerId"),
                modelId = stt.strictString("modelId"),
                requestProfileId = stt.strictString("requestProfileId"),
                parserVersion = stt.strictString("parserVersion"),
                rawResponseSha256 = stt.strictString("rawResponseSha256"),
                httpStatus = Math.toIntExact(stt.strictLong("httpStatus")),
            ),
            clock = SourceClockProvenance(
                observedPresentationOriginUs = clock.strictLong("observedPresentationOriginUs"),
                verificationStatus = runCatching {
                    ClockVerificationStatus.valueOf(clock.strictString("verificationStatus"))
                }.getOrElse { throw IllegalArgumentException("invalid clock verification status") },
                precisionUs = clock.nullableStrictLong("precisionUs"),
                evidenceProfile = if (clock.isNull("evidenceProfile")) null else clock.strictString("evidenceProfile"),
            ),
        )
        require(root.strictString("sourceTextHash") == value.sourceTextHash) { "source text hash mismatch" }
        require(root.strictString("snapshotId") == value.snapshotId) { "source snapshot identity mismatch" }
        return value
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "source snapshot too large" }
    }

    private fun JSONObject.nullableStrictLong(key: String): Long? = if (isNull(key)) null else strictLong(key)

    private fun JSONObject.strictLong(key: String): Long = when (val value = get(key)) {
        is Int -> value.toLong()
        is Long -> value
        else -> throw IllegalArgumentException("source snapshot integer field has wrong type: $key")
    }

    private fun JSONObject.strictString(key: String): String {
        val value = get(key)
        require(value is String) { "source snapshot text field has wrong type: $key" }
        return value
    }

    private fun JSONObject.requireKeys(expected: Set<String>) {
        require(keys().asSequence().toSet() == expected) { "unexpected source snapshot fields" }
    }
}
