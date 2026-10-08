package com.clw.aivideotranslator.session

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

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
        val manifest = SessionManifest(
            sessionId = sessionId,
            revision = 0L,
            epoch = 0L,
            activeEntryRefs = emptyMap(),
        )
        writeManifestUnlocked(manifest)
        manifest
    }

    fun readManifest(sessionId: String): SessionManifest = synchronized(writerLock) {
        readManifestUnlocked(sessionId)
    }

    fun readActiveEntry(sessionId: String, unitId: String): StoredTranslationEntry? = synchronized(writerLock) {
        require(isSafeId(unitId)) { "invalid unit id" }
        val manifest = readManifestUnlocked(sessionId)
        val revisionId = manifest.activeEntryRefs[unitId] ?: return@synchronized null
        readEntryUnlocked(sessionId, unitId, revisionId)
    }

    fun commitEntry(
        sessionId: String,
        expectedRevision: Long,
        entry: StoredTranslationEntry,
    ): SessionManifest = synchronized(writerLock) {
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
        val atomicFile = AtomicFile(File(directory, "manifest.json"))
        val bytes = SessionCodec.encodeManifest(manifest).toByteArray(Charsets.UTF_8)
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

    private fun writeImmutableEntryUnlocked(
        sessionId: String,
        unitId: String,
        entry: StoredTranslationEntry,
    ) {
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
            FileOutputStream(temp).use { output ->
                output.write(bytes)
                output.fd.sync()
            }
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
            require(entry.revisionId == revisionId && entry.record.unitId == unitId) {
                "entry identity mismatch"
            }
        }
    }

    private fun sessionDir(sessionId: String): File = File(sessionsRoot, sessionId)
}
