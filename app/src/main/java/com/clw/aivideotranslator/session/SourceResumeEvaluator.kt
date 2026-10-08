package com.clw.aivideotranslator.session

/** Fence a probe across blocking provider/file I/O; evaluate again against the CURRENT manifest. */
data class SourceProbeToken(
    val sessionId: String,
    val manifestRevision: Long,
    val epoch: Long,
    val attachmentId: String,
) {
    companion object {
        fun from(manifest: SessionManifest): SourceProbeToken = SourceProbeToken(
            manifest.sessionId, manifest.revision, manifest.epoch,
            requireNotNull(manifest.activeSourceAttachmentRef) { "no source to probe" },
        )
    }
}

enum class SourceReadStatus { READABLE, PERMISSION_MISSING, SOURCE_MISSING, IO_FAILURE, UNSUPPORTED }

data class SourceReadObservation(
    val token: SourceProbeToken,
    val observedContentUri: String,
    val status: SourceReadStatus,
    val fingerprint: SourceFingerprint? = null,
    val persistedReadGrantNow: Boolean = false,
) {
    init {
        require((status == SourceReadStatus.READABLE) == (fingerprint != null)) {
            "only a complete readable source may carry identity evidence"
        }
    }
}

enum class SourceAvailability {
    UNBOUND, LEGACY_UNBOUND, CHECK_REQUIRED, STALE_OBSERVATION, CORRUPT_BINDING,
    AVAILABLE, PERMISSION_MISSING, SOURCE_MISSING, SOURCE_CHANGED, IO_FAILURE, UNSUPPORTED,
}

data class SourceResumeAssessment(
    val availability: SourceAvailability,
    val persistedReadGrantNow: Boolean = false,
)

/**
 * Pure evidence classifier, not an activation gate. AVAILABLE is only a point-in-time source match.
 * It does not authorize a clock map, STT snapshot, render or provider submission; those contracts and
 * operation-time source revalidation remain required. A stored grant never substitutes for a probe.
 */
object SourceResumeEvaluator {
    fun evaluate(
        current: SessionManifest,
        attachment: SourceAttachment?,
        observation: SourceReadObservation?,
    ): SourceResumeAssessment {
        fun result(state: SourceAvailability) = SourceResumeAssessment(state)
        when (current.sourceBindingState) {
            SourceBindingState.UNBOUND -> return result(SourceAvailability.UNBOUND)
            SourceBindingState.LEGACY_UNBOUND -> return result(SourceAvailability.LEGACY_UNBOUND)
            else -> Unit
        }
        if (attachment == null || attachment.sessionId != current.sessionId ||
            attachment.attachmentId != current.activeSourceAttachmentRef) {
            return result(SourceAvailability.CORRUPT_BINDING)
        }
        if (observation == null) return result(SourceAvailability.CHECK_REQUIRED)
        if (observation.token != SourceProbeToken.from(current) ||
            observation.observedContentUri != attachment.contentUri) {
            return result(SourceAvailability.STALE_OBSERVATION)
        }
        return when (observation.status) {
            SourceReadStatus.PERMISSION_MISSING -> result(SourceAvailability.PERMISSION_MISSING)
            SourceReadStatus.SOURCE_MISSING -> result(SourceAvailability.SOURCE_MISSING)
            SourceReadStatus.IO_FAILURE -> result(SourceAvailability.IO_FAILURE)
            SourceReadStatus.UNSUPPORTED -> result(SourceAvailability.UNSUPPORTED)
            SourceReadStatus.READABLE -> if (observation.fingerprint != attachment.fingerprint) {
                result(SourceAvailability.SOURCE_CHANGED)
            } else SourceResumeAssessment(SourceAvailability.AVAILABLE, observation.persistedReadGrantNow)
        }
    }
}
