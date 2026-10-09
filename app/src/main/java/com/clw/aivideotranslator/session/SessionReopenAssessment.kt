package com.clw.aivideotranslator.session

/**
 * Local durable-snapshot integrity is deliberately separate from current source availability.
 * A valid semantic snapshot does not make the source readable, and a readable source does not make
 * a corrupt snapshot resumable. Controller/UI activation must require the states it actually needs.
 */
enum class SourceSnapshotAvailability {
    NOT_BOUND,
    AVAILABLE,
    CORRUPT_BINDING,
    STALE_OBSERVATION,
}

/**
 * Durable STT state exposed to a future controller without giving reopen any network capability.
 * RECEIVED_AVAILABLE means a complete accepted snapshot can be adopted locally; SENT is always an
 * unknown remote outcome and must never be turned into an automatic re-POST.
 */
enum class SttReopenDisposition {
    NOT_APPLICABLE,
    SAFE_TO_SUBMIT,
    UNKNOWN_REMOTE_OUTCOME,
    RECEIVED_AVAILABLE,
    ADOPTED,
    RECOVERED_RECEIVED,
    STALE_ATTEMPT,
    CORRUPT_JOURNAL,
}

data class SessionReopenAssessment(
    val source: SourceResumeAssessment,
    val snapshotAvailability: SourceSnapshotAvailability,
    val snapshot: SourceSnapshot? = null,
    val sttDisposition: SttReopenDisposition = SttReopenDisposition.NOT_APPLICABLE,
) {
    init {
        require((snapshotAvailability == SourceSnapshotAvailability.AVAILABLE) == (snapshot != null)) {
            "only an available snapshot may be exposed for resume"
        }
        if (sttDisposition == SttReopenDisposition.RECOVERED_RECEIVED ||
            sttDisposition == SttReopenDisposition.ADOPTED) {
            require(snapshotAvailability == SourceSnapshotAvailability.AVAILABLE && snapshot != null) {
                "adopted STT state requires the exact active snapshot"
            }
        }
    }
}
