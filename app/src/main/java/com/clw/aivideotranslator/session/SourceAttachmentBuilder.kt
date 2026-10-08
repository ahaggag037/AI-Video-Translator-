package com.clw.aivideotranslator.session

import android.content.ContentResolver
import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

/**
 * Pure assembly of an immutable SourceAttachment from already-observed evidence.
 * Performs no I/O itself; refuses unreadable sources, unknown durations and ranges that
 * exceed the observed duration. The track descriptor is the extractor's first-audio-track
 * view at capture time; preparation-time provenance must still match it exactly when a
 * SourceSnapshot is built. This assembler does not bind the attachment into the session
 * manifest — store/epoch ownership remains a separate explicit step.
 */
object SourceAttachmentAssembler {
    fun assemble(
        sessionId: String,
        inspection: SourceContentInspection,
        durationMs: Long,
        audioTrack: SourceAudioTrack,
        requestedRange: PresentationIntervalUs? = null,
    ): SourceAttachment {
        require(inspection.status == SourceReadStatus.READABLE) {
            "source is not readable at capture: ${inspection.status}"
        }
        val fingerprint = requireNotNull(inspection.fingerprint) { "readable source without fingerprint" }
        require(durationMs > 0L) { "unknown source duration" }
        val durationUs = Math.multiplyExact(durationMs, 1_000L)
        val range = requestedRange
            ?: PresentationIntervalUs(PresentationTimeUs(0L), PresentationTimeUs(durationUs))
        require(range.end.value <= durationUs) { "selected range exceeds observed source duration" }
        return SourceAttachment(
            sessionId = sessionId,
            contentUri = inspection.observedContentUri,
            persistedReadGrantAtCapture = inspection.persistedReadGrantNow,
            fingerprint = fingerprint,
            durationUs = durationUs,
            selectedRange = range,
            audioTrack = audioTrack,
        )
    }
}

/** Distinct, machine-readable capture failure; never silently downgraded. */
internal class SourceCaptureException(val status: SourceReadStatus, cause: Throwable? = null) :
    IllegalStateException("source is not readable at capture: $status", cause)

/**
 * Blocking Android capture boundary for a freshly selected source. Call from an I/O dispatcher.
 *
 * TOCTOU discipline: the provider stream is opened EXACTLY ONCE. That single stream is copied
 * into a private immutable cache file while being hashed in the same pass, and container duration
 * plus the first-audio-track descriptor are then read from that exact private copy — never from a
 * second provider open. A mutable/cloud DocumentsProvider therefore cannot serve one version to
 * the identity read and a different version to the metadata reads. The copy is deleted after
 * capture; the durable attachment keeps only the digest/size, not media bytes. No locator or
 * digest is logged; failures surface as distinct [SourceCaptureException] statuses.
 *
 * Track selection policy mirrors SttAudioPreparer so capture-time ownership and preparation-time
 * provenance describe the same track. The builder does NOT bind the attachment into the session
 * manifest; store/epoch ownership remains the separate existing CAS step. Legacy picker/probe
 * behavior is unchanged. Cost note: one full copy pass of the source; X006 will qualify the
 * performance of full-source identity work on device.
 */
object SourceAttachmentBuilder {
    private const val BUFFER_BYTES = 64 * 1024

    fun build(
        context: Context,
        sessionId: String,
        contentUri: String,
        requestedRange: PresentationIntervalUs? = null,
    ): Result<SourceAttachment> = runCatching {
        val resolver = context.contentResolver
        val uri = Uri.parse(contentUri)
        if (uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank()) {
            throw SourceCaptureException(SourceReadStatus.UNSUPPORTED)
        }
        val persistedGrantNow = runCatching {
            resolver.persistedUriPermissions.any { permission ->
                permission.isReadPermission && permission.uri == uri
            }
        }.getOrDefault(false)

        val captureDir = File(context.cacheDir, "p0_source_capture").apply { mkdirs() }
        sweepStaleCaptures(captureDir)
        val copy = File.createTempFile("source-capture-", ".bin", captureDir)
        try {
            val fingerprint = copyAndHashOnce(resolver, uri, copy)
            val durationMs = readDurationMs(copy)
            val track = readFirstAudioTrack(copy)
            SourceAttachmentAssembler.assemble(
                sessionId = sessionId,
                inspection = SourceContentInspection(
                    observedContentUri = contentUri,
                    status = SourceReadStatus.READABLE,
                    fingerprint = fingerprint,
                    persistedReadGrantNow = persistedGrantNow,
                ),
                durationMs = durationMs,
                audioTrack = track,
                requestedRange = requestedRange,
            )
        } finally {
            if (!copy.delete()) copy.deleteOnExit()
        }
    }

    /** The hashed bytes ARE the copied bytes: one provider open, one pass, one identity. */
    private fun copyAndHashOnce(resolver: ContentResolver, uri: Uri, target: File): SourceFingerprint {
        val input = try {
            resolver.openInputStream(uri)
        } catch (error: SecurityException) {
            throw SourceCaptureException(SourceReadStatus.PERMISSION_MISSING, error)
        } catch (error: FileNotFoundException) {
            throw SourceCaptureException(SourceReadStatus.SOURCE_MISSING, error)
        } catch (error: IllegalArgumentException) {
            throw SourceCaptureException(SourceReadStatus.UNSUPPORTED, error)
        }
            ?: throw SourceCaptureException(SourceReadStatus.IO_FAILURE)

        val digest = MessageDigest.getInstance("SHA-256")
        var sizeBytes = 0L
        try {
            input.use { stream ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        val read = stream.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        digest.update(buffer, 0, read)
                        out.write(buffer, 0, read)
                        sizeBytes = Math.addExact(sizeBytes, read.toLong())
                    }
                }
            }
        } catch (error: IOException) {
            throw SourceCaptureException(SourceReadStatus.IO_FAILURE, error)
        } catch (error: ArithmeticException) {
            throw SourceCaptureException(SourceReadStatus.IO_FAILURE, error)
        }
        if (sizeBytes == 0L) throw SourceCaptureException(SourceReadStatus.EMPTY_SOURCE)
        return SourceFingerprint(hexLower(digest.digest()), sizeBytes)
    }

    private fun readDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            require(duration != null && duration > 0L) { "تعذر تحديد مدة الفيديو" }
            return duration
        } finally {
            retriever.release()
        }
    }

    /** Same first-audio-track policy as SttAudioPreparer; language metadata is fail-soft. */
    private fun readFirstAudioTrack(file: File): SourceAudioTrack {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            for (index in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(index)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (!mime.startsWith("audio/")) continue
                val language = runCatching {
                    if (format.containsKey(MediaFormat.KEY_LANGUAGE)) {
                        format.getString(MediaFormat.KEY_LANGUAGE)
                    } else null
                }.getOrNull()
                return SourceAudioTrack(
                    containerIndex = index,
                    mime = mime,
                    language = language,
                    sampleRateHz = format.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                    channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
                )
            }
            error("الفيديو لا يحتوي على مسار صوت")
        } finally {
            extractor.release()
        }
    }

    /** Best-effort removal of crash-leftover captures; never fails the current capture. */
    private fun sweepStaleCaptures(dir: File) {
        runCatching {
            dir.listFiles()?.forEach { stale ->
                if (stale.name.startsWith("source-capture-")) stale.delete()
            }
        }
    }

    private fun hexLower(bytes: ByteArray): String = buildString(bytes.size * 2) {
        val hex = "0123456789abcdef"
        bytes.forEach { byte ->
            val value = byte.toInt() and 0xff
            append(hex[value ushr 4])
            append(hex[value and 0x0f])
        }
    }
}
