package com.clw.aivideotranslator.session

/** In-memory adoption lease, never a replacement for source-content or clock verification. */
internal data class SourceBindingToken(
    val sessionId: String,
    val revision: Long,
    val epoch: Long,
    val state: SourceBindingState,
    val attachmentRef: String?,
    val snapshotRef: String?,
) {
    companion object {
        fun from(manifest: SessionManifest) = SourceBindingToken(
            manifest.sessionId, manifest.revision, manifest.epoch, manifest.sourceBindingState,
            manifest.activeSourceAttachmentRef, manifest.activeSourceSnapshotRef,
        )
    }
}

/** A single locked read. Null attachment in a bound manifest denotes invalid durable evidence. */
internal data class SourceResumeInputs(
    val manifest: SessionManifest,
    val attachment: SourceAttachment?,
)
