package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.DetailedSttAudioPreparation
import com.clw.aivideotranslator.SttAudioPreparer
import java.io.Closeable
import java.io.File

/**
 * Operation-owned bytes from SourceAttachmentBuilder's one provider read. The source path is never
 * exposed; all decoding uses this copy. Exactly one prepared WAV may be live at a time so long-video
 * processing stays disk-bounded while requests remain sequential and fail-closed.
 */
internal class CapturedSource(
    private val sourceCopy: File,
    val attachment: SourceAttachment,
) : Closeable {
    private var preparedFile: File? = null
    private var closed = false

    @Synchronized fun requireMatches(expected: SourceAttachment) {
        check(!closed) { "source capture is closed" }
        require(attachment.copy(persistedReadGrantAtCapture = expected.persistedReadGrantAtCapture) == expected) {
            "captured source no longer matches the bound attachment"
        }
    }

    @Synchronized fun prepareFirstMinute(): DetailedSttAudioPreparation {
        check(!closed) { "source capture is closed" }
        check(preparedFile == null) { "capture already has a live prepared STT window" }
        val wav = File.createTempFile("captured-stt-", ".wav", sourceCopy.parentFile)
        preparedFile = wav
        return SttAudioPreparer.preparePrivateSourceFirstMinute(sourceCopy, wav).getOrThrow()
    }

    @Synchronized fun prepareWindow(window: SttWindow): DetailedSttAudioPreparation {
        check(!closed) { "source capture is closed" }
        check(preparedFile == null) { "capture already has a live prepared STT window" }
        require(window.endUs <= attachment.durationUs) { "STT window exceeds source duration" }
        val wav = File.createTempFile("captured-stt-${window.index}-", ".wav", sourceCopy.parentFile)
        preparedFile = wav
        return SttAudioPreparer.preparePrivateSourceWindow(
            sourceCopy = sourceCopy,
            outputFile = wav,
            startUs = window.startUs,
            endUs = window.endUs,
        ).getOrThrow()
    }

    /**
     * A video timeline may outlive its audio track or contain a >60s audio gap. That is not an STT
     * transport failure and must not create a provider request. The preparer owns the exact internal
     * no-sample diagnostic; every other decoder/media failure remains fatal and fail-closed.
     */
    @Synchronized fun prepareWindowOrNull(window: SttWindow): DetailedSttAudioPreparation? {
        return try {
            prepareWindow(window)
        } catch (error: IllegalArgumentException) {
            releasePreparedWindow()
            if (error.message == NO_AUDIO_WINDOW_MESSAGE) null else throw error
        } catch (error: Throwable) {
            releasePreparedWindow()
            throw error
        }
    }

    /** Release a completed/rejected window before preparing the next one. */
    @Synchronized fun releasePreparedWindow() {
        val file = preparedFile ?: return
        preparedFile = null
        if (file.exists() && !file.delete()) file.deleteOnExit()
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        listOfNotNull(preparedFile, sourceCopy).forEach { file ->
            if (file.exists() && !file.delete()) file.deleteOnExit()
        }
        preparedFile = null
    }

    private companion object {
        const val NO_AUDIO_WINDOW_MESSAGE = "STT window contains no audio sample"
    }
}
