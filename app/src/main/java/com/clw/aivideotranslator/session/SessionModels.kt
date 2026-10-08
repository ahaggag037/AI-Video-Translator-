package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.provider.TranslationProviderOutcome
import com.clw.aivideotranslator.semantic.TranslationRecord

const val TRANSLATION_MANIFEST_SCHEMA_VERSION = 2
const val TRANSLATION_ENTRY_SCHEMA_VERSION = 1
const val TRANSLATION_RECEIPT_SCHEMA_VERSION = 1

enum class SourceBindingState {
    /** New v2 session before any source is attached. */
    UNBOUND,

    /** Manifest migrated from v1, which had no durable source ownership. */
    LEGACY_UNBOUND,

    /** A durable source attachment is selected, but no accepted source snapshot is active yet. */
    ATTACHMENT_BOUND,

    /** Both durable source attachment and accepted source snapshot are active. */
    SNAPSHOT_BOUND,
}

data class SessionManifest(
    val schemaVersion: Int = TRANSLATION_MANIFEST_SCHEMA_VERSION,
    val sessionId: String,
    val revision: Long,
    val epoch: Long,
    val activeEntryRefs: Map<String, String>,
    val sourceBindingState: SourceBindingState = SourceBindingState.UNBOUND,
    val activeSourceAttachmentRef: String? = null,
    val activeSourceSnapshotRef: String? = null,
) {
    init {
        require(schemaVersion == TRANSLATION_MANIFEST_SCHEMA_VERSION) { "unsupported manifest schema" }
        require(isSafeId(sessionId)) { "invalid session id" }
        require(revision >= 0L) { "negative session revision" }
        require(epoch >= 0L) { "negative session epoch" }
        require(activeEntryRefs.keys.all(::isSafeId) && activeEntryRefs.values.all(::isSafeId)) {
            "invalid entry reference"
        }
        require(activeSourceAttachmentRef == null || isSafeId(activeSourceAttachmentRef)) {
            "invalid source attachment reference"
        }
        require(activeSourceSnapshotRef == null || isSafeId(activeSourceSnapshotRef)) {
            "invalid source snapshot reference"
        }
        when (sourceBindingState) {
            SourceBindingState.UNBOUND,
            SourceBindingState.LEGACY_UNBOUND,
            -> require(activeSourceAttachmentRef == null && activeSourceSnapshotRef == null) {
                "unbound source state cannot carry active source references"
            }
            SourceBindingState.ATTACHMENT_BOUND -> require(
                activeSourceAttachmentRef != null && activeSourceSnapshotRef == null
            ) { "attachment-bound state requires only an attachment reference" }
            SourceBindingState.SNAPSHOT_BOUND -> require(
                activeSourceAttachmentRef != null && activeSourceSnapshotRef != null
            ) { "snapshot-bound state requires attachment and snapshot references" }
        }
    }
}

data class StoredTranslationEntry(
    val schemaVersion: Int = TRANSLATION_ENTRY_SCHEMA_VERSION,
    val revisionId: String,
    val record: TranslationRecord,
) {
    init {
        require(schemaVersion == TRANSLATION_ENTRY_SCHEMA_VERSION) { "unsupported entry schema" }
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
    val schemaVersion: Int = TRANSLATION_RECEIPT_SCHEMA_VERSION,
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
        require(schemaVersion == TRANSLATION_RECEIPT_SCHEMA_VERSION) { "unsupported receipt schema" }
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
    value != "." && value != ".." && value.length in 1..128 &&
        value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }

