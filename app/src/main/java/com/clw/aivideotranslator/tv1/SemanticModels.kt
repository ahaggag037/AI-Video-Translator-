package com.clw.aivideotranslator.tv1

/** Source identity kept independent from rendered subtitle state. */
data class SourceAssetRef(
    val uri: String,
    val displayName: String? = null,
    val durationUs: Long? = null,
) {
    init {
        require(uri.isNotBlank()) { "source uri must not be blank" }
        require(durationUs == null || durationUs > 0L) { "source duration must be positive" }
    }
}

/**
 * Word timing is sample/audio-relative until SampleClockMap is applied.
 * Stable IDs are deterministic within one captured source snapshot.
 */
data class SourceWord(
    val id: String,
    val text: String,
    val sampleStartUs: TimeUs,
    val sampleEndUs: TimeUs,
    val confidence: Double? = null,
) {
    init {
        require(id.isNotBlank()) { "word id must not be blank" }
        require(text.isNotBlank()) { "word text must not be blank" }
        require(sampleEndUs.value > sampleStartUs.value) { "word timing must be positive" }
        require(confidence == null || confidence in 0.0..1.0) { "word confidence out of range" }
    }
}

/**
 * Semantic translation ownership. Presentation timing is explicit and independent from
 * future display extension/layout. sourceWordIds may be null only for a legacy bridge
 * where the current P0 model did not persist word identity.
 */
data class SemanticSourceUnit(
    val id: String,
    val sourceText: String,
    val presentationStartUs: TimeUs,
    val presentationEndUs: TimeUs,
    val sourceWordIds: List<String>? = null,
) {
    init {
        require(id.isNotBlank()) { "semantic unit id must not be blank" }
        require(sourceText.isNotBlank()) { "semantic source text must not be blank" }
        require(presentationEndUs.value > presentationStartUs.value) { "semantic unit timing must be positive" }
        sourceWordIds?.let {
            require(it.isNotEmpty()) { "known source word identity must not be empty" }
            require(it.none(String::isBlank)) { "source word id must not be blank" }
            require(it.distinct().size == it.size) { "source word ids must be unique" }
        }
    }
}

enum class TranslationOrigin {
    PROVIDER,
    MANUAL,
}

/** Immutable translation revision. New provider/manual output creates a new revision. */
data class TranslationRevision(
    val revisionId: String,
    val sourceUnitId: String,
    val text: String,
    val origin: TranslationOrigin,
    val createdAtEpochMs: Long,
    val requestSignature: String? = null,
) {
    init {
        require(revisionId.isNotBlank()) { "revision id must not be blank" }
        require(sourceUnitId.isNotBlank()) { "source unit id must not be blank" }
        require(text.isNotBlank()) { "translation text must not be blank" }
        require(createdAtEpochMs >= 0L) { "revision timestamp must be non-negative" }
        if (origin == TranslationOrigin.PROVIDER) {
            require(!requestSignature.isNullOrBlank()) {
                "provider revision requires request signature"
            }
        }
    }
}

/** Semantic cue contains authored meaning/timing only; visual layout is a separate layer. */
data class SemanticCue(
    val sourceUnitId: String,
    val translationRevisionId: String,
    val speechStartUs: TimeUs,
    val speechEndUs: TimeUs,
    val text: String,
) {
    init {
        require(sourceUnitId.isNotBlank()) { "cue source id must not be blank" }
        require(translationRevisionId.isNotBlank()) { "cue revision id must not be blank" }
        require(speechEndUs.value > speechStartUs.value) { "cue timing must be positive" }
        require(text.isNotBlank()) { "cue text must not be blank" }
    }
}

data class TranslationSessionManifest(
    val schemaVersion: Int = 1,
    val sessionId: String,
    val source: SourceAssetRef,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long,
    val sourceSnapshotId: String? = null,
    val segmentationId: String? = null,
) {
    init {
        require(schemaVersion == 1) { "unsupported session manifest schema" }
        require(sessionId.matches(Regex("[A-Za-z0-9_-]{1,96}"))) { "invalid session id" }
        require(createdAtEpochMs >= 0L && updatedAtEpochMs >= createdAtEpochMs) {
            "invalid session timestamps"
        }
        require(revision >= 0L) { "manifest revision must be non-negative" }
    }
}
