package com.clw.aivideotranslator.session

data class RequestAdoptionFence(
    val sessionId: String,
    val epoch: Long,
    val requestSignature: String,
    val expectedManifestRevision: Long,
    val unitId: String,
    val expectedActiveEntryRevisionId: String?,
) {
    init {
        require(isSafeId(sessionId))
        require(epoch >= 0L)
        require(requestSignature.isNotBlank())
        require(expectedManifestRevision >= 0L)
        require(isSafeId(unitId))
        require(expectedActiveEntryRevisionId == null || isSafeId(expectedActiveEntryRevisionId))
    }
}

enum class AdoptionFenceResult {
    CURRENT,
    STALE_EPOCH,
    STALE_MANIFEST_REVISION,
    STALE_ENTRY_REVISION,
    SIGNATURE_MISMATCH,
}

object SessionFencing {
    fun check(
        fence: RequestAdoptionFence,
        manifest: SessionManifest,
        responseRequestSignature: String,
    ): AdoptionFenceResult {
        if (manifest.epoch != fence.epoch) return AdoptionFenceResult.STALE_EPOCH
        if (manifest.revision != fence.expectedManifestRevision) return AdoptionFenceResult.STALE_MANIFEST_REVISION
        if (manifest.activeEntryRefs[fence.unitId] != fence.expectedActiveEntryRevisionId) {
            return AdoptionFenceResult.STALE_ENTRY_REVISION
        }
        if (responseRequestSignature != fence.requestSignature) return AdoptionFenceResult.SIGNATURE_MISMATCH
        return AdoptionFenceResult.CURRENT
    }
}
