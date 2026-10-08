package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationRecord

const val TRANSLATION_SESSION_SCHEMA_VERSION = 1

data class SessionManifest(
    val schemaVersion: Int = TRANSLATION_SESSION_SCHEMA_VERSION,
    val sessionId: String,
    val revision: Long,
    val epoch: Long,
    val activeEntryRefs: Map<String, String>,
) {
    init {
        require(schemaVersion == TRANSLATION_SESSION_SCHEMA_VERSION) { "unsupported session schema" }
        require(isSafeId(sessionId)) { "invalid session id" }
        require(revision >= 0L) { "negative session revision" }
        require(epoch >= 0L) { "negative session epoch" }
        require(activeEntryRefs.keys.all(::isSafeId) && activeEntryRefs.values.all(::isSafeId)) {
            "invalid entry reference"
        }
    }
}

data class StoredTranslationEntry(
    val schemaVersion: Int = TRANSLATION_SESSION_SCHEMA_VERSION,
    val revisionId: String,
    val record: TranslationRecord,
) {
    init {
        require(schemaVersion == TRANSLATION_SESSION_SCHEMA_VERSION) { "unsupported entry schema" }
        require(isSafeId(revisionId)) { "invalid entry revision id" }
        require(isSafeId(record.unitId)) { "invalid unit id" }
    }
}

enum class RequestReceiptPhase {
    PREPARED,
    SENT,
    RECEIVED,
}

data class RequestReceipt(
    val schemaVersion: Int = TRANSLATION_SESSION_SCHEMA_VERSION,
    val attemptId: String,
    val sessionId: String,
    val unitId: String,
    val epoch: Long,
    val requestSignature: String,
    val expectedManifestRevision: Long,
    val expectedActiveEntryRevisionId: String?,
    val phase: RequestReceiptPhase,
    val outcome: TranslationProviderOutcome? = null,
) {
    init {
        require(schemaVersion == TRANSLATION_SESSION_SCHEMA_VERSION) { "unsupported receipt schema" }
        require(isSafeId(attemptId) && isSafeId(sessionId) && isSafeId(unitId)) { "invalid receipt identity" }
        require(epoch >= 0L) { "negative receipt epoch" }
        require(requestSignature.isNotBlank()) { "blank request signature" }
        require(expectedManifestRevision >= 0L) { "negative expected manifest revision" }
        require(expectedActiveEntryRevisionId == null || isSafeId(expectedActiveEntryRevisionId)) {
            "invalid expected entry revision"
        }
        if (phase == RequestReceiptPhase.RECEIVED) require(outcome != null) { "received receipt requires outcome" }
        if (phase != RequestReceiptPhase.RECEIVED) require(outcome == null) { "pre-response receipt cannot contain outcome" }
    }
}

enum class ReceiptRecoveryDisposition {
    SAFE_TO_PLAN_NEW_ATTEMPT,
    UNKNOWN_REMOTE_OUTCOME,
    RECEIVED_AVAILABLE,
}

fun RequestReceipt.recoveryDisposition(): ReceiptRecoveryDisposition = when (phase) {
    RequestReceiptPhase.PREPARED -> ReceiptRecoveryDisposition.SAFE_TO_PLAN_NEW_ATTEMPT
    RequestReceiptPhase.SENT -> ReceiptRecoveryDisposition.UNKNOWN_REMOTE_OUTCOME
    RequestReceiptPhase.RECEIVED -> ReceiptRecoveryDisposition.RECEIVED_AVAILABLE
}

fun RequestReceipt.adoptionFence(): RequestAdoptionFence = RequestAdoptionFence(
    sessionId = sessionId,
    epoch = epoch,
    requestSignature = requestSignature,
    expectedManifestRevision = expectedManifestRevision,
    unitId = unitId,
    expectedActiveEntryRevisionId = expectedActiveEntryRevisionId,
)

internal fun isSafeId(value: String): Boolean =
    value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
