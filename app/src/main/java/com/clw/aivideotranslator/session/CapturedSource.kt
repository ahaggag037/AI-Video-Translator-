package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.SttAudioPreparer
import java.io.Closeable
import java.io.File

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

    @Synchronized fun requireMatches(expected: SourceAttachment) {
        check(!closed) { "source capture is closed" }
        // Read-grant availability can change without changing source bytes/selection. It was an
        // observation when the immutable attachment was created, not part of this operation's proof.
        require(attachment.copy(persistedReadGrantAtCapture = expected.persistedReadGrantAtCapture) == expected) {
            "captured source no longer matches the bound attachment"
        }
    }

    @Synchronized fun prepareFirstMinute(): DetailedSttAudioPreparation {
        check(!closed) { "source capture is closed" }
        check(preparedFile == null) { "capture already prepared; create a new operation to retry" }
        val wav = File.createTempFile("captured-stt-", ".wav", sourceCopy.parentFile)
        preparedFile = wav // Own cleanup even if decoding fails halfway through.
        return SttAudioPreparer.preparePrivateSourceFirstMinute(sourceCopy, wav).getOrThrow()
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        listOfNotNull(preparedFile, sourceCopy).forEach { file ->
            if (file.exists() && !file.delete()) file.deleteOnExit()
        }
    }
}
