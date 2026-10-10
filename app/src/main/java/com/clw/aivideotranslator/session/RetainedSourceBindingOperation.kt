package com.clw.aivideotranslator.session

import android.content.Context
import com.clw.aivideotranslator.semantic.PresentationIntervalUs

internal data class RetainedSourceBindingResult(
    val manifest: SessionManifest,
    val attachment: SourceAttachment,
    val retainedSource: RetainedSessionSource,
) {
    init {
        require(manifest.sessionId == attachment.sessionId) { "bound source session mismatch" }
        require(retainedSource.attachment == attachment) { "retained source attachment mismatch" }
        require(manifest.activeSourceAttachmentRef == attachment.attachmentId) {
            "manifest did not bind retained source attachment"
        }
    }
}

/**
 * Production source-selection operation: one provider open/copy/hash, then filesystem promotion of
 * those exact verified bytes into the session vault, then the existing manifest CAS bind.
 *
 * If the bind fails or is stale, this operation removes only the newly-created session vault. A
 * process death after promotion but before bind may leave an unreferenced private file; it cannot be
 * mistaken for active media because no manifest points at its attachment ID and cleanup can reclaim
 * it later. No second provider read is needed on the successful path.
 */
internal object RetainedSourceBindingOperation {
    fun capturePromoteAndBindInitialSource(
        context: Context,
        store: TranslationSessionStore,
        vault: SessionSourceVault,
        sessionId: String,
        contentUri: String,
        requestedRange: PresentationIntervalUs? = null,
        onCaptureProgress: (SourceCaptureProgress) -> Unit = {},
    ): Result<RetainedSourceBindingResult> = runCatching {
        val token = SourceBindingToken.from(store.readManifest(sessionId))
        val captured = SourceAttachmentBuilder.capture(
            context = context,
            sessionId = sessionId,
            contentUri = contentUri,
            requestedRange = requestedRange,
            onProgress = onCaptureProgress,
        ).getOrThrow()

        captured.use { owned ->
            val attachment = owned.attachment
            val retained = vault.promote(owned)
            try {
                val manifest = store.bindInitialSourceAttachmentIfCurrent(token, attachment)
                RetainedSourceBindingResult(
                    manifest = manifest,
                    attachment = attachment,
                    retainedSource = retained,
                )
            } catch (error: Throwable) {
                runCatching { vault.clearSession(sessionId) }
                throw error
            }
        }
    }
}
