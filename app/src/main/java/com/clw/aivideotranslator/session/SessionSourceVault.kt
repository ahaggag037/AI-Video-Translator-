package com.clw.aivideotranslator.session

import java.io.File

internal data class RetainedSessionSource(
    val attachment: SourceAttachment,
    val file: File,
) {
    init {
        require(file.isFile) { "retained source is missing" }
        require(file.length() == attachment.fingerprint.sizeBytes) {
            "retained source size does not match attachment"
        }
    }
}

/**
 * Application-private owner for the exact bytes already proven by [SourceAttachmentBuilder].
 *
 * Promotion is a filesystem move of the capture produced by the one provider copy+hash pass. It
 * therefore avoids re-opening the provider for downstream decode/preview/export. The attachment ID
 * (which binds the full source SHA-256 and selection metadata) names the immutable media file.
 */
internal class SessionSourceVault(
    private val root: File,
) {
    init {
        require(root.mkdirs() || root.isDirectory) { "cannot create retained-source root" }
    }

    fun promote(captured: CapturedSource): RetainedSessionSource {
        val attachment = captured.attachment
        require(isSafeId(attachment.sessionId)) { "invalid retained-source session id" }
        require(isSafeId(attachment.attachmentId)) { "invalid retained-source attachment id" }
        val sessionDir = File(root, attachment.sessionId)
        require(sessionDir.mkdirs() || sessionDir.isDirectory) {
            "cannot create retained-source session directory"
        }
        val target = File(sessionDir, "${attachment.attachmentId}.media")
        if (target.isFile) {
            require(target.length() == attachment.fingerprint.sizeBytes) {
                "existing retained source size does not match attachment"
            }
            return RetainedSessionSource(attachment, target)
        }
        val promoted = captured.promoteSourceTo(target)
        return RetainedSessionSource(attachment, promoted)
    }

    fun resolveOrNull(attachment: SourceAttachment): RetainedSessionSource? {
        require(isSafeId(attachment.sessionId)) { "invalid retained-source session id" }
        require(isSafeId(attachment.attachmentId)) { "invalid retained-source attachment id" }
        val file = File(File(root, attachment.sessionId), "${attachment.attachmentId}.media")
        if (!file.isFile) return null
        require(file.length() == attachment.fingerprint.sizeBytes) {
            "retained source size does not match attachment"
        }
        return RetainedSessionSource(attachment, file)
    }

    fun clearSession(sessionId: String) {
        require(isSafeId(sessionId)) { "invalid retained-source session id" }
        val sessionDir = File(root, sessionId)
        if (sessionDir.exists()) {
            require(sessionDir.deleteRecursively()) { "cannot clear retained-source session" }
        }
    }
}
