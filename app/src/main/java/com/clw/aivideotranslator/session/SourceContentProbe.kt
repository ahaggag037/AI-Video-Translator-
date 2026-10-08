package com.clw.aivideotranslator.session

import android.content.ContentResolver
import android.net.Uri
import java.io.FileNotFoundException
import java.io.IOException
import java.security.MessageDigest

data class SourceContentInspection(
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

/**
 * Blocking Android source-evidence adapter. Call from an I/O dispatcher.
 *
 * A persisted grant is reported as one observation only; it never substitutes for opening and
 * hashing the current bytes. Resume uses the full byte-stream digest so a provider cannot silently
 * replace media under the same content URI/size. No locator or digest is logged here.
 */
object SourceContentProbe {
    private const val BUFFER_BYTES = 64 * 1024

    fun inspect(resolver: ContentResolver, contentUri: String): SourceContentInspection {
        val uri = runCatching { Uri.parse(contentUri) }.getOrNull()
        if (uri == null || uri.scheme != ContentResolver.SCHEME_CONTENT || uri.authority.isNullOrBlank()) {
            return SourceContentInspection(contentUri, SourceReadStatus.UNSUPPORTED)
        }

        val persistedGrantNow = runCatching {
            resolver.persistedUriPermissions.any { permission ->
                permission.isReadPermission && permission.uri == uri
            }
        }.getOrDefault(false)

        return try {
            val input = resolver.openInputStream(uri)
                ?: return SourceContentInspection(contentUri, SourceReadStatus.IO_FAILURE,
                    persistedReadGrantNow = persistedGrantNow)
            input.use { stream ->
                val digest = MessageDigest.getInstance("SHA-256")
                val buffer = ByteArray(BUFFER_BYTES)
                var sizeBytes = 0L
                while (true) {
                    val count = stream.read(buffer)
                    if (count == -1) break
                    if (count == 0) continue
                    sizeBytes = Math.addExact(sizeBytes, count.toLong())
                    digest.update(buffer, 0, count)
                }
                if (sizeBytes == 0L) {
                    SourceContentInspection(contentUri, SourceReadStatus.EMPTY_SOURCE,
                        persistedReadGrantNow = persistedGrantNow)
                } else {
                    SourceContentInspection(
                        observedContentUri = contentUri,
                        status = SourceReadStatus.READABLE,
                        fingerprint = SourceFingerprint(hexLower(digest.digest()), sizeBytes),
                        persistedReadGrantNow = persistedGrantNow,
                    )
                }
            }
        } catch (_: SecurityException) {
            SourceContentInspection(contentUri, SourceReadStatus.PERMISSION_MISSING)
        } catch (_: FileNotFoundException) {
            SourceContentInspection(contentUri, SourceReadStatus.SOURCE_MISSING,
                persistedReadGrantNow = persistedGrantNow)
        } catch (_: IOException) {
            SourceContentInspection(contentUri, SourceReadStatus.IO_FAILURE,
                persistedReadGrantNow = persistedGrantNow)
        } catch (_: ArithmeticException) {
            SourceContentInspection(contentUri, SourceReadStatus.IO_FAILURE,
                persistedReadGrantNow = persistedGrantNow)
        } catch (_: IllegalArgumentException) {
            SourceContentInspection(contentUri, SourceReadStatus.UNSUPPORTED,
                persistedReadGrantNow = persistedGrantNow)
        }
    }

    fun probe(
        resolver: ContentResolver,
        token: SourceProbeToken,
        attachment: SourceAttachment,
    ): SourceReadObservation {
        require(token.sessionId == attachment.sessionId && token.attachmentId == attachment.attachmentId) {
            "probe token does not own source attachment"
        }
        val inspection = inspect(resolver, attachment.contentUri)
        return SourceReadObservation(
            token = token,
            observedContentUri = inspection.observedContentUri,
            status = inspection.status,
            fingerprint = inspection.fingerprint,
            persistedReadGrantNow = inspection.persistedReadGrantNow,
        )
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
