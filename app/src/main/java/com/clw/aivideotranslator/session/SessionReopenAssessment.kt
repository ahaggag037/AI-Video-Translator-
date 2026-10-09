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

data class SessionReopenAssessment(
    val source: SourceResumeAssessment,
    val snapshotAvailability: SourceSnapshotAvailability,
    val snapshot: SourceSnapshot? = null,
) {
    init {
        require((snapshotAvailability == SourceSnapshotAvailability.AVAILABLE) == (snapshot != null)) {
            "only an available snapshot may be exposed for resume"
        }
    }
}
