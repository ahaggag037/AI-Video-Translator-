package com.clw.aivideotranslator.session

import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream
import org.json.JSONObject

private const val ACTIVE_SESSION_SCHEMA_VERSION = 1

internal data class ActiveSessionPointer(
    val schemaVersion: Int = ACTIVE_SESSION_SCHEMA_VERSION,
    val sessionId: String,
) {
    init {
        require(schemaVersion == ACTIVE_SESSION_SCHEMA_VERSION) { "unsupported active-session schema" }
        require(isSafeId(sessionId)) { "invalid active session id" }
    }
}

internal object ActiveSessionPointerCodec {
    const val MAX_BYTES = 4_096

    fun encode(pointer: ActiveSessionPointer): String = JSONObject()
        .put("schemaVersion", pointer.schemaVersion)
        .put("sessionId", pointer.sessionId)
        .toString()
        .also(::requireBounded)

    fun decode(json: String): ActiveSessionPointer {
        requireBounded(json)
        val root = JSONObject(json)
        require(root.keys().asSequence().toSet() == setOf("schemaVersion", "sessionId")) {
            "unexpected active-session fields"
        }
        val rawSchema = root.get("schemaVersion")
        require(rawSchema is Int) { "active-session schema has wrong type" }
        val rawSession = root.get("sessionId")
        require(rawSession is String) { "active-session id has wrong type" }
        return ActiveSessionPointer(schemaVersion = rawSchema, sessionId = rawSession)
    }

    private fun requireBounded(json: String) {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "active-session pointer too large" }
    }
}

/**
 * Root-level pointer to the one V1 session the UI owns. It never guesses from directory timestamps.
 * A target session is validated before publication; switching is compare-and-set so a stale Activity
 * cannot silently replace a newer owner. The pointer is intentionally separate from session content:
 * a crash before pointer publication leaves the previous session active, while a crash after finishWrite
 * always points at a manifest that was already validated.
 */
internal class ActiveSessionRegistry(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
    private val afterPayloadWritten: (File) -> Unit = {},
) {
    private val writerLock = Any()
    private val pointerFile = File(sessionsRoot, "active_session.json")

    init {
        require(sessionsRoot.mkdirs() || sessionsRoot.isDirectory) { "cannot create session root" }
    }

    fun readActiveSessionId(): String? = synchronized(writerLock) {
        readPointerUnlocked(validateTarget = true)?.sessionId
    }

    fun activateSession(sessionId: String, expectedActiveSessionId: String?): String = synchronized(writerLock) {
        require(isSafeId(sessionId)) { "invalid active session id" }
        require(expectedActiveSessionId == null || isSafeId(expectedActiveSessionId)) {
            "invalid expected active session id"
        }
        val current = readPointerUnlocked(validateTarget = true)?.sessionId
        check(current == expectedActiveSessionId) { "stale active-session pointer" }
        validateSession(sessionId)
        writeAtomic(ActiveSessionPointerCodec.encode(ActiveSessionPointer(sessionId = sessionId)))
        sessionId
    }

    private fun readPointerUnlocked(validateTarget: Boolean): ActiveSessionPointer? {
        if (!pointerFile.exists()) return null
        require(pointerFile.isFile) { "active-session pointer is not a file" }
        val bytes = AtomicFile(pointerFile).openRead().use { input ->
            val data = input.readBytes()
            require(data.size <= ActiveSessionPointerCodec.MAX_BYTES) { "active-session pointer too large" }
            data
        }
        val pointer = ActiveSessionPointerCodec.decode(bytes.toString(Charsets.UTF_8))
        if (validateTarget) validateSession(pointer.sessionId)
        return pointer
    }

    private fun writeAtomic(value: String) {
        val atomicFile = AtomicFile(pointerFile)
        var stream: FileOutputStream? = null
        try {
            stream = atomicFile.startWrite()
            stream.write(value.toByteArray(Charsets.UTF_8))
            afterPayloadWritten(pointerFile)
            stream.fd.sync()
            atomicFile.finishWrite(stream)
            stream = null
        } finally {
            if (stream != null) atomicFile.failWrite(stream)
        }
    }
}
