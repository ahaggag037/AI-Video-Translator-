package com.clw.aivideotranslator.session

import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** App-private field-test journal. It is deliberately separate from the canonical session manifest. */
internal class FieldTestSttWindowJournalStore(
    private val root: File,
) {
    private val writerLock = Any()

    init {
        require(root.mkdirs() || root.isDirectory) { "cannot create field-test STT journal root" }
    }

    fun readOrNull(sessionId: String, attemptId: String): FieldTestSttWindowReceipt? = synchronized(writerLock) {
        require(isSafeId(sessionId) && isSafeId(attemptId)) { "invalid field-test STT journal path" }
        val file = receiptFile(sessionId, attemptId, createDirectory = false)
        if (!file.isFile) null else read(file).also { receipt ->
            require(receipt.sessionId == sessionId && receipt.attemptId == attemptId) {
                "field-test STT receipt path identity mismatch"
            }
        }
    }

    fun persistPrepared(receipt: FieldTestSttWindowReceipt): FieldTestSttWindowReceipt = synchronized(writerLock) {
        require(receipt.phase == FieldTestSttWindowAttemptPhase.PREPARED && receipt.result == null) {
            "field-test STT attempt must begin PREPARED"
        }
        val file = receiptFile(receipt.sessionId, receipt.attemptId, createDirectory = true)
        val existing = if (file.isFile) read(file) else null
        if (existing != null) {
            require(existing.phase == FieldTestSttWindowAttemptPhase.PREPARED) {
                "cannot replace field-test STT attempt after submission"
            }
            requireSameOperation(existing, receipt, allowPreparedRefresh = true)
            if (existing == receipt) return@synchronized existing
        }
        write(file, receipt)
        receipt
    }

    fun markSent(receipt: FieldTestSttWindowReceipt): FieldTestSttWindowReceipt = synchronized(writerLock) {
        require(receipt.phase == FieldTestSttWindowAttemptPhase.SENT && receipt.result == null) {
            "markSent requires SENT without a result"
        }
        val file = receiptFile(receipt.sessionId, receipt.attemptId, createDirectory = false)
        require(file.isFile) { "field-test PREPARED receipt is missing" }
        val existing = read(file)
        if (existing == receipt) return@synchronized existing
        require(existing.phase == FieldTestSttWindowAttemptPhase.PREPARED) { "SENT must advance PREPARED" }
        requireSameOperation(existing, receipt, allowPreparedRefresh = false)
        write(file, receipt)
        receipt
    }

    fun persistReceived(receipt: FieldTestSttWindowReceipt): FieldTestSttWindowReceipt = synchronized(writerLock) {
        require(receipt.phase == FieldTestSttWindowAttemptPhase.RECEIVED && receipt.result != null) {
            "persistReceived requires RECEIVED with a result"
        }
        val file = receiptFile(receipt.sessionId, receipt.attemptId, createDirectory = false)
        require(file.isFile) { "field-test SENT receipt is missing" }
        val existing = read(file)
        if (existing == receipt) return@synchronized existing
        require(existing.phase == FieldTestSttWindowAttemptPhase.SENT) { "RECEIVED must advance SENT" }
        requireSameOperation(existing, receipt, allowPreparedRefresh = false)
        write(file, receipt)
        receipt
    }

    fun clearSession(sessionId: String) = synchronized(writerLock) {
        require(isSafeId(sessionId)) { "invalid field-test STT session id" }
        val directory = File(root, sessionId)
        if (directory.exists()) require(directory.deleteRecursively()) { "cannot clear field-test STT journal" }
    }

    private fun requireSameOperation(
        existing: FieldTestSttWindowReceipt,
        next: FieldTestSttWindowReceipt,
        allowPreparedRefresh: Boolean,
    ) {
        require(existing.attemptId == next.attemptId &&
            existing.sessionId == next.sessionId &&
            existing.sourceAttachmentId == next.sourceAttachmentId &&
            existing.windowIndex == next.windowIndex &&
            existing.startUs == next.startUs &&
            existing.endUs == next.endUs
        ) { "field-test STT operation identity changed" }
        if (!allowPreparedRefresh) {
            require(existing.requestProfileId == next.requestProfileId) { "field-test STT request profile changed after prepare" }
            require(existing.sampleSha256 == next.sampleSha256) { "field-test STT sample changed after prepare" }
        }
    }

    private fun receiptFile(sessionId: String, attemptId: String, createDirectory: Boolean): File {
        val directory = File(root, sessionId)
        if (createDirectory) require(directory.mkdirs() || directory.isDirectory) {
            "cannot create field-test STT session journal"
        }
        return File(directory, "$attemptId.json")
    }

    private fun read(file: File): FieldTestSttWindowReceipt {
        val bytes = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            while (true) {
                val remaining = FieldTestSttWindowReceiptCodec.MAX_BYTES + 1 - output.size()
                require(remaining > 0) { "field-test STT receipt exceeds size limit" }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count == -1) break
                output.write(buffer, 0, count)
                require(output.size() <= FieldTestSttWindowReceiptCodec.MAX_BYTES) {
                    "field-test STT receipt exceeds size limit"
                }
            }
            output.toByteArray()
        }
        return FieldTestSttWindowReceiptCodec.decode(bytes.toString(Charsets.UTF_8))
    }

    private fun write(file: File, receipt: FieldTestSttWindowReceipt) {
        val bytes = FieldTestSttWindowReceiptCodec.encode(receipt).toByteArray(Charsets.UTF_8)
        val parent = requireNotNull(file.parentFile) { "field-test STT receipt has no parent directory" }
        require(parent.isDirectory) { "field-test STT receipt parent is missing" }
        val temp = File(parent, ".${file.name}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { stream ->
                stream.write(bytes)
                stream.flush()
                stream.fd.sync()
            }
            require(temp.length() == bytes.size.toLong()) { "field-test STT temp receipt size mismatch" }
            Files.move(
                temp.toPath(),
                file.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
            require(file.isFile) { "field-test STT atomic receipt publish failed" }
        } catch (error: Throwable) {
            if (temp.exists()) temp.delete()
            throw error
        }
    }
}
