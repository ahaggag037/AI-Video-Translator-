package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionSourceVaultTest {
    @Test fun promotedCaptureSurvivesCapturedSourceCloseWithoutSecondCopy() {
        val root = Files.createTempDirectory("session-source-vault").toFile()
        try {
            val captureDir = File(root, "capture").apply { mkdirs() }
            val source = File(captureDir, "source.bin")
            val bytes = ByteArray(32 * 1024) { index -> (index % 251).toByte() }
            source.writeBytes(bytes)
            val attachment = attachment(sizeBytes = bytes.size.toLong())
            val captured = CapturedSource(source, attachment)
            val vault = SessionSourceVault(File(root, "vault"))

            val retained = vault.promote(captured)
            assertFalse(source.exists())
            assertTrue(retained.file.isFile)
            assertArrayEquals(bytes, retained.file.readBytes())

            captured.close()
            assertTrue(retained.file.isFile)
            assertArrayEquals(bytes, retained.file.readBytes())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun retainedSourceCanBeResolvedAfterOwnerReopen() {
        val root = Files.createTempDirectory("session-source-reopen").toFile()
        try {
            val source = File(root, "source.bin")
            val bytes = ByteArray(4_096) { 7 }
            source.writeBytes(bytes)
            val attachment = attachment(sizeBytes = bytes.size.toLong())
            CapturedSource(source, attachment).use { captured ->
                SessionSourceVault(File(root, "vault")).promote(captured)
            }

            val reopened = SessionSourceVault(File(root, "vault")).resolveOrNull(attachment)
            assertNotNull(reopened)
            assertEquals(bytes.size.toLong(), reopened!!.file.length())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun clearSessionRemovesRetainedMedia() {
        val root = Files.createTempDirectory("session-source-clear").toFile()
        try {
            val source = File(root, "source.bin")
            val bytes = ByteArray(1_024) { 9 }
            source.writeBytes(bytes)
            val attachment = attachment(sizeBytes = bytes.size.toLong())
            val vault = SessionSourceVault(File(root, "vault"))
            CapturedSource(source, attachment).use { captured -> vault.promote(captured) }

            vault.clearSession(attachment.sessionId)
            assertEquals(null, vault.resolveOrNull(attachment))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun resolveRejectsCorruptedRetainedLength() {
        val root = Files.createTempDirectory("session-source-corrupt").toFile()
        try {
            val source = File(root, "source.bin")
            val bytes = ByteArray(2_048) { 3 }
            source.writeBytes(bytes)
            val attachment = attachment(sizeBytes = bytes.size.toLong())
            val vault = SessionSourceVault(File(root, "vault"))
            val retained = CapturedSource(source, attachment).use { captured -> vault.promote(captured) }
            retained.file.appendBytes(byteArrayOf(1))

            vault.resolveOrNull(attachment)
        } finally {
            root.deleteRecursively()
        }
    }

    private fun attachment(sizeBytes: Long) = SourceAttachment(
        sessionId = "session-vault-test",
        contentUri = "content://example.provider/video/1",
        persistedReadGrantAtCapture = true,
        fingerprint = SourceFingerprint("a".repeat(64), sizeBytes),
        durationUs = 120_000_000L,
        selectedRange = PresentationIntervalUs(
            PresentationTimeUs(0L),
            PresentationTimeUs(120_000_000L),
        ),
        audioTrack = SourceAudioTrack(
            containerIndex = 0,
            mime = "audio/mp4a-latm",
            language = "en",
            sampleRateHz = 48_000,
            channelCount = 2,
        ),
    )
}
