package com.clw.aivideotranslator.session

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

internal fun isSafeId(value: String): Boolean =
    value.length in 1..128 && value.all { it.isLetterOrDigit() || it == '-' || it == '_' || it == '.' }
