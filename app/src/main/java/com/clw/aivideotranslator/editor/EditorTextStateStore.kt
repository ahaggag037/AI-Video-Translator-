package com.clw.aivideotranslator.editor

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

internal data class ManualTextRevision(
    val id: String,
    val text: String,
    val basedOnMachineTextHash: String,
)

internal data class UnitEditorTextState(
    val revisions: List<ManualTextRevision> = emptyList(),
    val activeRevisionIndex: Int = -1,
    val pendingDraft: String? = null,
) {
    init {
        require(activeRevisionIndex in -1 until revisions.size) { "active manual revision index out of range" }
        require(revisions.map { it.id }.toSet().size == revisions.size) { "duplicate manual revision id" }
    }

    val hasManualTruth: Boolean get() = activeRevisionIndex >= 0

    fun visibleText(machineText: String): String = pendingDraft
        ?: activeManualText()
        ?: machineText

    fun exportText(machineText: String): String = activeManualText() ?: machineText

    private fun activeManualText(): String? =
        if (activeRevisionIndex >= 0) revisions[activeRevisionIndex].text else null
}

internal data class DurableEditorTextState(
    val sessionId: String,
    val revision: Long = 0L,
    val units: Map<String, UnitEditorTextState> = emptyMap(),
)

/**
 * Atomic sidecar under the canonical session directory. Pending text is durable recovery work;
 * only an active committed manual revision changes export truth.
 */
internal class EditorTextStateStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    private val lock = Any()

    init {
        require(sessionsRoot.mkdirs() || sessionsRoot.isDirectory) { "cannot create session root" }
    }

    fun read(sessionId: String): DurableEditorTextState = synchronized(lock) {
        requireSafeId(sessionId)
        validateSession(sessionId)
        readUnlocked(sessionId)
    }

    fun readUnit(sessionId: String, unitId: String): UnitEditorTextState = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        readUnlocked(sessionId).units[unitId] ?: UnitEditorTextState()
    }

    fun setPendingDraft(
        sessionId: String,
        unitId: String,
        machineText: String,
        draftText: String,
    ): DurableEditorTextState = mutate(sessionId, unitId) { current ->
        val normalized = draftText
        val effectiveCommitted = current.exportText(machineText)
        current.copy(pendingDraft = normalized.takeUnless { it == effectiveCommitted })
    }

    fun clearPendingDraft(sessionId: String, unitId: String): DurableEditorTextState =
        mutate(sessionId, unitId) { it.copy(pendingDraft = null) }

    fun commitPendingDraft(
        sessionId: String,
        unitId: String,
        machineText: String,
    ): DurableEditorTextState = mutate(sessionId, unitId) { current ->
        val pending = requireNotNull(current.pendingDraft) { "no pending manual draft" }
        require(pending.isNotBlank()) { "manual text cannot be blank" }
        val keptHistory = current.revisions.take(current.activeRevisionIndex + 1)
        val next = ManualTextRevision(
            id = "manual-${UUID.randomUUID()}",
            text = pending,
            basedOnMachineTextHash = sha256(machineText),
        )
        current.copy(
            revisions = keptHistory + next,
            activeRevisionIndex = keptHistory.size,
            pendingDraft = null,
        )
    }

    fun undo(sessionId: String, unitId: String): Boolean = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        val state = readUnlocked(sessionId)
        val current = state.units[unitId] ?: UnitEditorTextState()
        if (current.pendingDraft != null) {
            writeUnitUnlocked(state, unitId, current.copy(pendingDraft = null))
            return@synchronized true
        }
        if (current.activeRevisionIndex < 0) return@synchronized false
        writeUnitUnlocked(
            state,
            unitId,
            current.copy(activeRevisionIndex = current.activeRevisionIndex - 1),
        )
        true
    }

    fun redo(sessionId: String, unitId: String): Boolean = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        val state = readUnlocked(sessionId)
        val current = state.units[unitId] ?: UnitEditorTextState()
        if (current.pendingDraft != null) return@synchronized false
        val nextIndex = current.activeRevisionIndex + 1
        if (nextIndex >= current.revisions.size) return@synchronized false
        writeUnitUnlocked(state, unitId, current.copy(activeRevisionIndex = nextIndex))
        true
    }

    fun restoreMachine(sessionId: String, unitId: String): DurableEditorTextState =
        mutate(sessionId, unitId) { current ->
            current.copy(activeRevisionIndex = -1, pendingDraft = null)
        }

    private fun mutate(
        sessionId: String,
        unitId: String,
        transform: (UnitEditorTextState) -> UnitEditorTextState,
    ): DurableEditorTextState = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        val state = readUnlocked(sessionId)
        val current = state.units[unitId] ?: UnitEditorTextState()
        writeUnitUnlocked(state, unitId, transform(current))
    }

    private fun writeUnitUnlocked(
        state: DurableEditorTextState,
        unitId: String,
        unit: UnitEditorTextState,
    ): DurableEditorTextState {
        val nextUnits = state.units.toMutableMap().apply {
            if (unit == UnitEditorTextState()) remove(unitId) else put(unitId, unit)
        }
        val next = state.copy(
            revision = Math.addExact(state.revision, 1L),
            units = nextUnits.toMap(),
        )
        writeUnlocked(next)
        return next
    }

    private fun readUnlocked(sessionId: String): DurableEditorTextState {
        val file = stateFile(sessionId)
        if (!file.isFile) return DurableEditorTextState(sessionId = sessionId)
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == 1) { "unsupported editor text schema" }
        require(root.getString("sessionId") == sessionId) { "editor text session mismatch" }
        val unitsObject = root.getJSONObject("units")
        val units = buildMap {
            unitsObject.keys().forEach { unitId ->
                requireSafeId(unitId)
                val value = unitsObject.getJSONObject(unitId)
                val revisionsJson = value.getJSONArray("revisions")
                val revisions = buildList {
                    for (index in 0 until revisionsJson.length()) {
                        val item = revisionsJson.getJSONObject(index)
                        add(
                            ManualTextRevision(
                                id = item.getString("id"),
                                text = item.getString("text"),
                                basedOnMachineTextHash = item.getString("basedOnMachineTextHash"),
                            ),
                        )
                    }
                }
                val pending = if (value.isNull("pendingDraft")) null else value.getString("pendingDraft")
                put(
                    unitId,
                    UnitEditorTextState(
                        revisions = revisions,
                        activeRevisionIndex = value.getInt("activeRevisionIndex"),
                        pendingDraft = pending,
                    ),
                )
            }
        }
        return DurableEditorTextState(
            sessionId = sessionId,
            revision = root.getLong("revision"),
            units = units,
        )
    }

    private fun writeUnlocked(state: DurableEditorTextState) {
        val units = JSONObject()
        state.units.toSortedMap().forEach { (unitId, value) ->
            val revisions = JSONArray()
            value.revisions.forEach { revision ->
                revisions.put(
                    JSONObject()
                        .put("id", revision.id)
                        .put("text", revision.text)
                        .put("basedOnMachineTextHash", revision.basedOnMachineTextHash),
                )
            }
            units.put(
                unitId,
                JSONObject()
                    .put("activeRevisionIndex", value.activeRevisionIndex)
                    .put("pendingDraft", value.pendingDraft ?: JSONObject.NULL)
                    .put("revisions", revisions),
            )
        }
        val payload = JSONObject()
            .put("schemaVersion", 1)
            .put("sessionId", state.sessionId)
            .put("revision", state.revision)
            .put("units", units)
            .toString()
        atomicWrite(stateFile(state.sessionId), payload)
    }

    private fun stateFile(sessionId: String): File {
        val sessionDir = File(sessionsRoot, sessionId)
        require(sessionDir.isDirectory) { "session directory missing" }
        return File(sessionDir, FILE_NAME)
    }

    private fun atomicWrite(target: File, payload: String) {
        val temp = File(target.parentFile, "${target.name}.tmp")
        FileOutputStream(temp).use { stream ->
            stream.write(payload.toByteArray(Charsets.UTF_8))
            stream.fd.sync()
        }
        try {
            Files.move(
                temp.toPath(),
                target.toPath(),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun requireSafeId(value: String) {
        require(SAFE_ID.matches(value)) { "unsafe durable editor id" }
    }

    private fun sha256(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }

    companion object {
        private const val FILE_NAME = "editor_text.json"
        private val SAFE_ID = Regex("[A-Za-z0-9._-]+")
    }
}
