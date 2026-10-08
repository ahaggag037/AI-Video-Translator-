package com.clw.aivideotranslator.semantic

import java.security.MessageDigest

data class SourceWord(
    val id: String,
    val rawText: String,
    val audioInterval: AudioIntervalUs,
    val confidence: Double?,
    val timingPrecisionUs: Long = 1_000L,
) {
    init {
        require(id.isNotBlank())
        require(rawText.isNotBlank())
        require(timingPrecisionUs > 0L)
    }
}

data class SemanticSourceUnit(
    val id: String,
    val orderedWordIds: List<String>,
    val sourceText: String,
    val sourceTextHash: String,
    val sourceInterval: PresentationIntervalUs,
    val segmentationVersion: String,
    val warnings: Set<String> = emptySet(),
) {
    init {
        require(id.isNotBlank())
        require(orderedWordIds.isNotEmpty())
        require(orderedWordIds.toSet().size == orderedWordIds.size)
        require(sourceText.isNotBlank())
        require(sourceTextHash.isNotBlank())
        require(segmentationVersion.isNotBlank())
    }
}

data class MachineTranslationRevision(
    val id: String,
    val text: String,
    val requestSignature: String,
) {
    init { require(id.isNotBlank() && text.isNotBlank() && requestSignature.isNotBlank()) }
}

data class ManualTranslationRevision(
    val id: String,
    val text: String,
    val basedOnSourceTextHash: String,
    val basedOnMachineRevisionId: String? = null,
) {
    init { require(id.isNotBlank() && text.isNotBlank() && basedOnSourceTextHash.isNotBlank()) }
}

enum class TranslationReviewState {
    MACHINE_CANDIDATE,
    APPROVED,
    REVIEW_REQUIRED,
    REBASE_REQUIRED,
}

data class TranslationRecord(
    val unitId: String,
    val machineRevisions: List<MachineTranslationRevision>,
    val activeMachineRevisionId: String?,
    val manualRevision: ManualTranslationRevision?,
    val reviewState: TranslationReviewState,
) {
    init {
        require(unitId.isNotBlank())
        val ids = machineRevisions.map { it.id }
        require(ids.toSet().size == ids.size) { "duplicate machine revision id" }
        require(activeMachineRevisionId == null || activeMachineRevisionId in ids) {
            "active machine revision must exist"
        }
    }

    fun effectiveText(): String? = manualRevision?.text
        ?: activeMachineRevisionId?.let { activeId ->
            machineRevisions.first { it.id == activeId }.text
        }
}

data class SemanticCue(
    val id: String,
    val sourceUnitId: String,
    val effectiveTranslationRevisionId: String,
    val text: String,
    val speechInterval: PresentationIntervalUs,
) {
    init {
        require(id.isNotBlank())
        require(sourceUnitId.isNotBlank())
        require(effectiveTranslationRevisionId.isNotBlank())
        require(text.isNotBlank())
    }
}

fun sha256Utf8(text: String): String = MessageDigest.getInstance("SHA-256")
    .digest(text.toByteArray(Charsets.UTF_8))
    .joinToString("") { byte -> "%02x".format(byte) }
