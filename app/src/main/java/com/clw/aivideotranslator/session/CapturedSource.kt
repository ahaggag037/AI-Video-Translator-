package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.SttAudioPreparer
import java.io.Closeable
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Operation-owned bytes from SourceAttachmentBuilder's one provider read. The source path is never
 * exposed; all decoding uses this copy. One preparation per capture, one unique WAV, no shared sweep.
 * This is an in-process ownership contract, not protection against a malicious same-UID writer.
 */
internal class CapturedSource(
    private val sourceCopy: File,
    val attachment: SourceAttachment,
) : Closeable {
    private var preparedFile: File? = null
    private var closed = false
    private var sourcePromoted = false

    @Synchronized fun requireMatches(expected: SourceAttachment) {
        check(!closed) { "source capture is closed" }
        // Read-grant availability can change without changing source bytes/selection. It was an
        // observation when the immutable attachment was created, not part of this operation's proof.
        require(attachment.copy(persistedReadGrantAtCapture = expected.persistedReadGrantAtCapture) == expected) {
            "captured source no longer matches the bound attachment"
        }
    }

    /**
     * Transfers the already-hashed private capture into stable application-owned session storage.
     * The move occurs before any decode from this owner, so the exact provider bytes proven during
     * capture become the downstream media source without a second provider read or a second copy.
     */
    @Synchronized internal fun promoteSourceTo(target: File): File {
        check(!closed) { "source capture is closed" }
        check(!sourcePromoted) { "source capture was already promoted" }
        check(preparedFile == null) { "source capture cannot be promoted after audio preparation" }
        require(sourceCopy.isFile) { "captured source bytes are missing" }
        require(sourceCopy.length() == attachment.fingerprint.sizeBytes) {
            "captured source size no longer matches attachment fingerprint"
        }
        val parent = requireNotNull(target.parentFile) { "retained source has no parent directory" }
        require(parent.mkdirs() || parent.isDirectory) { "cannot create retained-source directory" }
        require(!target.exists()) { "retained source target already exists" }

        try {
            Files.move(sourceCopy.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(sourceCopy.toPath(), target.toPath())
        }
        require(target.isFile && target.length() == attachment.fingerprint.sizeBytes) {
            "retained source promotion failed"
        }
        sourcePromoted = true
        return target
    }

    @Synchronized fun prepareFirstMinute(): DetailedSttAudioPreparation {
        check(!closed) { "source capture is closed" }
        check(!sourcePromoted) { "promoted source must be decoded by its session owner" }
        check(preparedFile == null) { "capture already prepared; create a new operation to retry" }
        val wav = File.createTempFile("captured-stt-", ".wav", sourceCopy.parentFile)
        preparedFile = wav // Own cleanup even if decoding fails halfway through.
        return SttAudioPreparer.preparePrivateSourceFirstMinute(sourceCopy, wav).getOrThrow()
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        preparedFile?.let { file ->
            if (file.exists() && !file.delete()) file.deleteOnExit()
        }
        if (!sourcePromoted && sourceCopy.exists() && !sourceCopy.delete()) {
            sourceCopy.deleteOnExit()
        }
    }
}
