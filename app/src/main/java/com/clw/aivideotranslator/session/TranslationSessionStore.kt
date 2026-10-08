package com.clw.aivideotranslator.session

import android.util.AtomicFile
import com.clw.aivideotranslator.semantic.TranslationRequestPlan
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class RecoveredCandidateCommitResult(
    val recoveryPlan: ReceiptRecoveryPlan,
    val manifest: SessionManifest,
    val committedEntry: StoredTranslationEntry? = null,
)

class TranslationSessionStore(private val sessionsRoot: File) {
    private val writerLock = Any()

    init { require(sessionsRoot.mkdirs() || sessionsRoot.isDirectory) { "cannot create session root" } }

    fun createSession(sessionId: String): SessionManifest = synchronized(writerLock) {
        require(isSafeId(sessionId)) { "invalid session id" }
        val directory = sessionDir(sessionId).apply {
            require(mkdirs() || isDirectory) { "cannot create session directory" }
        }
        val manifestFile = File(directory, "manifest.json")
        if (manifestFile.exists()) return@synchronized readManifestUnlocked(sessionId)
        val manifest = SessionManifest(sessionId = sessionId, revision = 0L, epoch = 0L, activeEntryRefs = emptyMap())
        writeManifestUnlocked(manifest)
        manifest
    }

    fun readManifest(sessionId: String): SessionManifest = synchronized(writerLock) { readManifestUnlocked(sessionId) }

    fun readActiveEntry(sessionId: String, unitId: String): StoredTranslationEntry? = synchronized(writerLock) {
        require(isSafeId(unitId)) { "invalid unit id" }
        val manifest = readManifestUnlocked(sessionId)
        val revisionId = manifest.activeEntryRefs[unitId] ?: return@synchronized null
        readEntryUnlocked(sessionId, unitId, revisionId)
    }

    fun commitEntry(sessionId: String, expectedRevision: Long, entry: StoredTranslationEntry): SessionManifest =
        synchronized(writerLock) {
            val current = readManifestUnlocked(sessionId)
            check(current.revision == expectedRevision) { "stale session revision" }
            val unitId = entry.record.unitId
            writeImmutableEntryUnlocked(sessionId, unitId, entry)
            val next = current.copy(
                revision = Math.addExact(current.revision, 1L),
                activeEntryRefs = current.activeEntryRefs + (unitId to entry.revisionId),
            )
            writeManifestUnlocked(next)
            next
        }

    fun adoptRecoveredCandidate(
        sessionId: String,
        attemptId: String,
        requestPlan: TranslationRequestPlan,
    ): RecoveredCandidateCommitResult = synchronized(writerLock) {
        require(isSafeId(sessionId) && isSafeId(attemptId)) { "invalid recovery identity" }

        val currentManifest = readManifestUnlocked(sessionId)
        val receipt = readReceiptUnlocked(sessionId, attemptId)
        val recoveryPlan = ReceiptRecoveryPlanner.plan(receipt, currentManifest, requestPlan)
        if (recoveryPlan.action != ReceiptRecoveryAction.READY_TO_ADOPT) {
            return@synchronized RecoveredCandidateCommitResult(
                recoveryPlan = recoveryPlan,
                manifest = currentManifest,
            )
        }

        val candidateText = requireNotNull(receipt.outcome?.candidateText) { "adoptable receipt missing candidate" }
        val recoveryIds = RecoveryRevisionIdentity.forReceipt(receipt)
        val currentRecord = currentManifest.activeEntryRefs[receipt.unitId]?.let { revisionId ->
            readEntryUnlocked(sessionId, receipt.unitId, revisionId).record
        }
        val merged = RecoveredCandidateAdopter.mergeIntoHistory(
            current = currentRecord,
            unitId = receipt.unitId,
            rawProviderText = candidateText,
            requestSignature = receipt.requestSignature,
            machineRevisionId = recoveryIds.machineRevisionId,
        )
        val entry = StoredTranslationEntry(
            revisionId = recoveryIds.entryRevisionId,
            record = merged,
        )

        // Publish immutable history first. If the process dies before manifest publication,
        // the same frozen receipt deterministically reproduces the same immutable bytes and IDs.
        writeImmutableEntryUnlocked(sessionId, receipt.unitId, entry)
        val nextManifest = currentManifest.copy(
            revision = Math.addExact(currentManifest.revision, 1L),
            activeEntryRefs = currentManifest.activeEntryRefs + (receipt.unitId to entry.revisionId),
        )
        writeManifestUnlocked(nextManifest)
        RecoveredCandidateCommitResult(
            recoveryPlan = recoveryPlan,
            manifest = nextManifest,
            committedEntry = entry,
        )
    }

    fun bumpEpoch(sessionId: String, expectedRevision: Long): SessionManifest = synchronized(writerLock) {
        val current = readManifestUnlocked(sessionId)
        check(current.revision == expectedRevision) { "stale session revision" }
        val next = current.copy(
            revision = Math.addExact(current.revision, 1L),
            epoch = Math.addExact(current.epoch, 1L),
        )
        writeManifestUnlocked(next)
        next
    }

    fun writeReceipt(receipt: RequestReceipt): RequestReceipt = synchronized(writerLock) {
        val manifest = readManifestUnlocked(receipt.sessionId)
        if (receipt.phase == RequestReceiptPhase.PREPARED) {
            check(manifest.epoch == receipt.epoch) { "cannot prepare request for stale epoch" }
            check(manifest.revision == receipt.expectedManifestRevision) { "cannot prepare request for stale manifest" }
            check(manifest.activeEntryRefs[receipt.unitId] == receipt.expectedActiveEntryRevisionId) {
                "cannot prepare request for stale entry"
            }
        }
        val directory = File(sessionDir(receipt.sessionId), "requests").apply {
            require(mkdirs() || isDirectory) { "cannot create request directory" }
        }
        val file = File(directory, "${receipt.attemptId}.json")
        if (file.exists()) {
            val existing = readReceiptFileUnlocked(file)
            validateReceiptTransition(existing, receipt)
            if (existing == receipt) return@synchronized existing
        } else {
            require(receipt.phase == RequestReceiptPhase.PREPARED) { "receipt must begin PREPARED" }
        }
        writeAtomicTextUnlocked(file, SessionCodec.encodeReceipt(receipt))
        receipt
    }

    fun readReceipt(sessionId: String, attemptId: String): RequestReceipt = synchronized(writerLock) {
        readReceiptUnlocked(sessionId, attemptId)
    }

    fun listReceipts(sessionId: String): List<RequestReceipt> = synchronized(writerLock) {
        require(isSafeId(sessionId)) { "invalid session id" }
        readManifestUnlocked(sessionId)
        val directory = File(sessionDir(sessionId), "requests")
        if (!directory.isDirectory) return@synchronized emptyList()
        directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?.map(::readReceiptFileUnlocked)
            ?: emptyList()
    }

    private fun validateReceiptTransition(existing: RequestReceipt, next: RequestReceipt) {
        require(
            existing.attemptId == next.attemptId && existing.sessionId == next.sessionId &&
                existing.unitId == next.unitId && existing.epoch == next.epoch &&
                existing.requestSignature == next.requestSignature &&
                existing.expectedManifestRevision == next.expectedManifestRevision &&
                existing.expectedActiveEntryRevisionId == next.expectedActiveEntryRevisionId
        ) { "receipt identity changed" }
        if (existing == next) return
        require(next.phase.ordinal == existing.phase.ordinal + 1) { "invalid receipt phase transition" }
    }

    private fun readReceiptUnlocked(sessionId: String, attemptId: String): RequestReceipt {
        require(isSafeId(sessionId) && isSafeId(attemptId)) { "invalid receipt path" }
        val file = File(sessionDir(sessionId), "requests/$attemptId.json")
        require(file.isFile) { "request receipt missing" }
        return readReceiptFileUnlocked(file).also { receipt ->
            require(receipt.sessionId == sessionId && receipt.attemptId == attemptId) { "receipt identity mismatch" }
        }
    }

    private fun readReceiptFileUnlocked(file: File): RequestReceipt {
        val bytes = AtomicFile(file).openRead().use { input ->
            val data = input.readBytes()
            require(data.size <= SessionCodec.RECEIPT_MAX_BYTES) { "receipt exceeds size limit" }
            data
        }
        return SessionCodec.decodeReceipt(bytes.toString(Charsets.UTF_8))
    }

    private fun readManifestUnlocked(sessionId: String): SessionManifest {
        require(isSafeId(sessionId)) { "invalid session id" }
        val file = File(sessionDir(sessionId), "manifest.json")
        require(file.isFile) { "session manifest missing" }
        val json = AtomicFile(file).openRead().use { input ->
            val bytes = input.readBytes()
            require(bytes.size <= SessionCodec.MANIFEST_MAX_BYTES) { "manifest exceeds size limit" }
            bytes.toString(Charsets.UTF_8)
        }
        return SessionCodec.decodeManifest(json).also { manifest ->
            require(manifest.sessionId == sessionId) { "session identity mismatch" }
        }
    }

    private fun writeManifestUnlocked(manifest: SessionManifest) {
        val directory = sessionDir(manifest.sessionId).apply {
            require(mkdirs() || isDirectory) { "cannot create session directory" }
        }
        writeAtomicTextUnlocked(File(directory, "manifest.json"), SessionCodec.encodeManifest(manifest))
    }

    private fun writeAtomicTextUnlocked(file: File, value: String) {
        val atomicFile = AtomicFile(file)
        val bytes = value.toByteArray(Charsets.UTF_8)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            stream = null
        } finally {
            if (stream != null) atomicFile.failWrite(stream)
        }
    }

    private fun writeImmutableEntryUnlocked(sessionId: String, unitId: String, entry: StoredTranslationEntry) {
        require(isSafeId(unitId) && isSafeId(entry.revisionId)) { "invalid entry path" }
        val directory = File(sessionDir(sessionId), "entries/$unitId").apply {
            require(mkdirs() || isDirectory) { "cannot create entry directory" }
        }
        val destination = File(directory, "${entry.revisionId}.json")
        val bytes = SessionCodec.encodeEntry(entry).toByteArray(Charsets.UTF_8)
        if (destination.exists()) {
            require(destination.readBytes().contentEquals(bytes)) { "immutable entry collision" }
            return
        }
        val temp = File(directory, ".${entry.revisionId}.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output -> output.write(bytes); output.fd.sync() }
            check(temp.renameTo(destination)) { "cannot publish immutable entry" }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    private fun readEntryUnlocked(sessionId: String, unitId: String, revisionId: String): StoredTranslationEntry {
        require(isSafeId(revisionId)) { "invalid revision id" }
        val file = File(sessionDir(sessionId), "entries/$unitId/$revisionId.json")
        require(file.isFile) { "active entry missing" }
        val bytes = file.readBytes()
        require(bytes.size <= SessionCodec.ENTRY_MAX_BYTES) { "entry exceeds size limit" }
        return SessionCodec.decodeEntry(bytes.toString(Charsets.UTF_8)).also { entry ->
            require(entry.revisionId == revisionId && entry.record.unitId == unitId) { "entry identity mismatch" }
        }
    }

    private fun sessionDir(sessionId: String): File = File(sessionsRoot, sessionId)
}
