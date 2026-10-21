package com.clw.aivideotranslator.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.File

internal object ProductOutputNames {
    fun base(raw: String): String {
        val collapsed = raw
            .replace(Regex("[\\p{Cntrl}/\\\\]+"), "_")
            .replace(Regex("\\s+"), "_")
            .replace(Regex("_+"), "_")
            .trim(' ', '_', '.')
            .replace("..", ".")
            .trim('.')
            .take(96)
            .trim(' ', '_', '.')
        return collapsed.ifBlank { "translated_video" }
    }

    fun srt(rawBase: String): String = "${base(rawBase)}.srt"
    fun mp4(rawBase: String): String = "${base(rawBase)}.mp4"
}

internal data class PersistedProductOutput(
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
)

/** Scoped-storage publisher for Android 10+. Pending rows are invisible until bytes are complete. */
internal object ProductOutputStore {
    fun saveSrt(context: Context, baseName: String, srtUtf8: String): PersistedProductOutput {
        require(srtUtf8.isNotBlank()) { "SRT output is empty" }
        val bytes = srtUtf8.toByteArray(Charsets.UTF_8)
        return publish(
            context = context,
            collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            displayName = ProductOutputNames.srt(baseName),
            mimeType = "application/x-subrip",
            relativePath = "${Environment.DIRECTORY_DOWNLOADS}/AI Video Translator",
        ) { uri ->
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "cannot open SRT MediaStore output" }
                output.write(bytes)
                output.flush()
            }
            bytes.size.toLong()
        }
    }

    fun saveValidatedMp4(context: Context, baseName: String, file: File): PersistedProductOutput {
        require(file.isFile && file.length() > 0L) { "validated MP4 file is missing" }
        val expectedSize = file.length()
        return publish(
            context = context,
            collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            displayName = ProductOutputNames.mp4(baseName),
            mimeType = "video/mp4",
            relativePath = "${Environment.DIRECTORY_MOVIES}/AI Video Translator",
        ) { uri ->
            var written = 0L
            context.contentResolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "cannot open MP4 MediaStore output" }
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count == 0) continue
                        output.write(buffer, 0, count)
                        written = Math.addExact(written, count.toLong())
                    }
                }
                output.flush()
            }
            require(written == expectedSize) { "published MP4 size differs from validated source" }
            written
        }
    }

    private fun publish(
        context: Context,
        collection: Uri,
        displayName: String,
        mimeType: String,
        relativePath: String,
        write: (Uri) -> Long,
    ): PersistedProductOutput {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = requireNotNull(resolver.insert(collection, values)) { "MediaStore insert failed" }
        return try {
            val size = write(uri)
            require(size > 0L) { "published output is empty" }
            val finalized = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            require(resolver.update(uri, finalized, null, null) == 1) { "MediaStore finalize failed" }
            PersistedProductOutput(uri, displayName, size)
        } catch (error: Throwable) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
