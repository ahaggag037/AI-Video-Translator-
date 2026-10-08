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

fun interface SessionStoreFaultInjector {
    fun afterRecoveryEntryPublished(sessionId: String, unitId: String, revisionId: String)

    fun afterAtomicPayloadWritten(file: File) = Unit

    fun afterSourceAttachmentPublished(sessionId: String, attachmentId: String) = Unit

    fun afterSourceSnapshotPublished(sessionId: String, snapshotId: String) = Unit

    companion object {
        val NONE = SessionStoreFaultInjector { _, _, _ -> }
    }
}

class TranslationSessionStore(
    private val sessionsRoot: File,
    private val faultInjector: SessionStoreFaultInjector = SessionStoreFaultInjector.NONE,
) {
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

    /**
     * Additive B012 prerequisite, not wired to production UI. Only binds a NEW empty session.
     * Reattachment/migration of legacy/manual history needs an explicit snapshot/rebase contract.
     * Publish the immutable object before its manifest reference; a failed manifest leaves an orphan
     * that the same CAS retry can reuse. One application-owned store instance must own this root.
     */
    fun bindInitialSourceAttachment(
        sessionId: String,
        expectedRevision: Long,
        attachment: SourceAttachment,
    ): SessionManifest = synchronized(writerLock) {
        val current = readManifestUnlocked(sessionId)
        check(current.revision == expectedRevision) { "stale session revision" }
        require(attachment.sessionId == sessionId) { "source session mismatch" }
        check(current.sourceBindingState == SourceBindingState.UNBOUND && current.activeEntryRefs.isEmpty()) {
            "initial source binding requires a new empty session; migration or reattachment must be explicit"
        }
        writeImmutableSourceAttachmentUnlocked(attachment)
        faultInjector.afterSourceAttachmentPublished(sessionId, attachment.attachmentId)
        val next = current.copy(
            revision = Math.addExact(current.revision, 1L),
            epoch = Math.addExact(current.epoch, 1L),
            sourceBindingState = SourceBindingState.ATTACHMENT_BOUND,
            activeSourceAttachmentRef = attachment.attachmentId,
        )
        writeManifestUnlocked(next)
        next
    }

    fun readActiveSourceAttachment(sessionId: String): SourceAttachment? = synchronized(writerLock) {
        val manifest = readManifestUnlocked(sessionId)
        val ref = manifest.activeSourceAttachmentRef ?: return@synchronized null
        readSourceAttachmentUnlocked(sessionId, ref)
    }

    /**
     * Publish the accepted STT/source-text snapshot only after a durable attachment exists.
     * This remains additive and unwired: an UNVERIFIED clock snapshot is durable semantic evidence,
     * not permission to reinterpret provider units or render against a guessed clock.
     */
    fun bindInitialSourceSnapshot(
        sessionId: String,
        expectedRevision: Long,
        snapshot: SourceSnapshot,
    ): SessionManifest = synchronized(writerLock) {
        val current = readManifestUnlocked(sessionId)
        check(current.revision == expectedRevision) { "stale session revision" }
        require(snapshot.sessionId == sessionId) { "snapshot session mismatch" }
        check(current.sourceBindingState == SourceBindingState.ATTACHMENT_BOUND &&
            current.activeSourceSnapshotRef == null && current.activeEntryRefs.isEmpty()) {
            "initial source snapshot binding requires attachment-bound empty session"
        }
        val attachmentRef = requireNotNull(current.activeSourceAttachmentRef) { "source attachment missing" }
        require(snapshot.sourceAttachmentId == attachmentRef) { "snapshot source attachment mismatch" }
        readSourceAttachmentUnlocked(sessionId, attachmentRef)
        writeImmutableSourceSnapshotUnlocked(snapshot)
        faultInjector.afterSourceSnapshotPublished(sessionId, snapshot.snapshotId)
        val next = current.copy(
            revision = Math.addExact(current.revision, 1L),
            epoch = Math.addExact(current.epoch, 1L),
            sourceBindingState = SourceBindingState.SNAPSHOT_BOUND,
            activeSourceSnapshotRef = snapshot.snapshotId,
        )
        writeManifestUnlocked(next)
        next
    }

    fun readActiveSourceSnapshot(sessionId: String): SourceSnapshot? = synchronized(writerLock) {
        val manifest = readManifestUnlocked(sessionId)
        val ref = manifest.activeSourceSnapshotRef ?: return@synchronized null
        val snapshot = readSourceSnapshotUnlocked(sessionId, ref)
        require(snapshot.sourceAttachmentId == manifest.activeSourceAttachmentRef) { "source snapshot binding mismatch" }
        snapshot
    }

    private fun readSourceAttachmentUnlocked(sessionId: String, ref: String): SourceAttachment {
        require(isSafeId(sessionId) && isSafeId(ref)) { "invalid source attachment path" }
        val file = File(sessionDir(sessionId), "sources/$ref.json")
        val json = readBoundedUtf8(file, SourceAttachmentCodec.MAX_BYTES, "source attachment too large")
        return SourceAttachmentCodec.decode(json).also {
            require(it.sessionId == sessionId && it.attachmentId == ref) { "source attachment identity mismatch" }
        }
    }

    private fun readSourceSnapshotUnlocked(sessionId: String, ref: String): SourceSnapshot {
        require(isSafeId(sessionId) && isSafeId(ref)) { "invalid source snapshot path" }
        val file = File(sessionDir(sessionId), "snapshots/$ref.json")
        val json = readBoundedUtf8(file, SourceSnapshotCodec.MAX_BYTES, "source snapshot too large")
        return SourceSnapshotCodec.decode(json).also {
            require(it.sessionId == sessionId && it.snapshotId == ref) { "source snapshot identity mismatch" }
        }
    }

    private fun readBoundedUtf8(file: File, maxBytes: Int, errorMessage: String): String {
        val json = file.inputStream().use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            while (true) {
                val remaining = maxBytes + 1 - output.size()
                require(remaining > 0) { errorMessage }
                val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (count == -1) break
                output.write(buffer, 0, count)
                require(output.size() <= maxBytes) { errorMessage }
            }
            output.toByteArray().toString(Charsets.UTF_8)
        }
        return json
    }

    private fun writeImmutableSourceAttachmentUnlocked(attachment: SourceAttachment) {
        val directory = File(sessionDir(attachment.sessionId), "sources").apply {
            require(mkdirs() || isDirectory) { "cannot create source directory" }
        }
        val destination = File(directory, "${attachment.attachmentId}.json")
        if (destination.exists()) {
            require(readSourceAttachmentUnlocked(attachment.sessionId, attachment.attachmentId) == attachment) {
                "immutable source attachment collision"
            }
            return
        }
        val bytes = SourceAttachmentCodec.encode(attachment).toByteArray(Charsets.UTF_8)
        publishImmutableFile(directory, destination, attachment.attachmentId, bytes, "cannot publish source attachment")
    }

    private fun writeImmutableSourceSnapshotUnlocked(snapshot: SourceSnapshot) {
        val directory = File(sessionDir(snapshot.sessionId), "snapshots").apply {
            require(mkdirs() || isDirectory) { "cannot create source snapshot directory" }
        }
        val destination = File(directory, "${snapshot.snapshotId}.json")
        if (destination.exists()) {
            require(readSourceSnapshotUnlocked(snapshot.sessionId, snapshot.snapshotId) == snapshot) {
                "immutable source snapshot collision"
            }
            return
        }
        val bytes = SourceSnapshotCodec.encode(snapshot).toByteArray(Charsets.UTF_8)
        publishImmutableFile(directory, destination, snapshot.snapshotId, bytes, "cannot publish source snapshot")
    }

    private fun publishImmutableFile(
        directory: File,
        destination: File,
        identity: String,
        bytes: ByteArray,
        errorMessage: String,
    ) {
        val temp = File(directory, ".$identity.${UUID.randomUUID()}.tmp")
        try {
            FileOutputStream(temp).use { output -> output.write(bytes); output.fd.sync() }
            check(temp.renameTo(destination)) { errorMessage }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

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

        writeImmutableEntryUnlocked(sessionId, receipt.unitId, entry)
        faultInjector.afterRecoveryEntryPublished(sessionId, receipt.unitId, entry.revisionId)
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
        val directory = File(sessionDir(receipt.sessionId), "requests").apply {
            require(mkdirs() || isDirectory) { "cannot create request directory" }
        }
        val file = File(directory, "${receipt.attemptId}.json")
        val existing = if (file.exists()) readReceiptFileUnlocked(file) else null

        if (existing != null) {
            validateReceiptTransition(existing, receipt)
            if (existing == receipt) return@synchronized existing
        } else {
            require(receipt.phase == RequestReceiptPhase.PREPARED) { "receipt must begin PREPARED" }
        }

        when (receipt.phase) {
            RequestReceiptPhase.PREPARED -> requireCurrentReceiptFence(receipt, manifest, "prepare")
            RequestReceiptPhase.SENT -> {
                require(existing?.phase == RequestReceiptPhase.PREPARED) { "SENT must advance a durable PREPARED receipt" }
                requireCurrentReceiptFence(receipt, manifest, "send")
            }
            RequestReceiptPhase.RECEIVED -> Unit
        }

        writeAtomicTextUnlocked(file, SessionCodec.encodeReceipt(receipt))
        receipt
    }

    fun markSentIfCurrent(sent: RequestReceipt): RequestReceipt {
        require(sent.phase == RequestReceiptPhase.SENT) { "markSentIfCurrent requires SENT receipt" }
        return writeReceipt(sent)
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

    private fun requireCurrentReceiptFence(receipt: RequestReceipt, manifest: SessionManifest, verb: String) {
        val fence = SessionFencing.check(receipt.adoptionFence(), manifest, receipt.requestSignature)
        check(fence == AdoptionFenceResult.CURRENT) { "cannot $verb request under stale fence: $fence" }
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
            faultInjector.afterAtomicPayloadWritten(file)
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
