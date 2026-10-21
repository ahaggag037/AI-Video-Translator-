package com.clw.aivideotranslator.editor

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONObject

data class DisplayTimingInterval(
    val startUs: Long,
    val endUs: Long,
) {
    init {
        require(startUs >= 0L) { "display timing start cannot be negative" }
        require(startUs < endUs) { "display timing start must be before end" }
    }
}

data class UnitDisplayTimingState(
    val committedOverride: DisplayTimingInterval? = null,
    val pendingDraft: DisplayTimingInterval? = null,
) {
    fun visibleInterval(sourceInterval: DisplayTimingInterval): DisplayTimingInterval =
        pendingDraft ?: committedOverride ?: sourceInterval

    fun exportInterval(sourceInterval: DisplayTimingInterval): DisplayTimingInterval =
        committedOverride ?: sourceInterval
}

data class DurableDisplayTimingState(
    val sessionId: String,
    val revision: Long = 0L,
    val units: Map<String, UnitDisplayTimingState> = emptyMap(),
)

/** Pending timing is recovery-durable but only committed overrides change display/export truth. */
class DisplayTimingStateStore(
    private val sessionsRoot: File,
    private val validateSession: (String) -> Unit,
) {
    private val lock = Any()

    init {
        require(sessionsRoot.mkdirs() || sessionsRoot.isDirectory) { "cannot create session root" }
    }

    fun read(sessionId: String): DurableDisplayTimingState = synchronized(lock) {
        requireSafeId(sessionId)
        validateSession(sessionId)
        readUnlocked(sessionId)
    }

    fun readUnit(sessionId: String, unitId: String): UnitDisplayTimingState = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        readUnlocked(sessionId).units[unitId] ?: UnitDisplayTimingState()
    }

    fun setPendingDraft(
        sessionId: String,
        unitId: String,
        sourceInterval: DisplayTimingInterval,
        draft: DisplayTimingInterval,
    ): DurableDisplayTimingState = mutate(sessionId, unitId) { current ->
        val committed = current.committedOverride ?: sourceInterval
        current.copy(pendingDraft = draft.takeUnless { it == committed })
    }

    fun commitPendingDraft(
        sessionId: String,
        unitId: String,
        sourceInterval: DisplayTimingInterval,
        videoDurationUs: Long?,
        previousEffectiveEndUs: Long?,
        nextEffectiveStartUs: Long?,
    ): DurableDisplayTimingState = mutate(sessionId, unitId) { current ->
        val draft = requireNotNull(current.pendingDraft) { "no pending timing draft" }
        require(videoDurationUs == null || draft.endUs <= videoDurationUs) {
            "display timing exceeds video duration"
        }
        require(previousEffectiveEndUs == null || draft.startUs >= previousEffectiveEndUs) {
            "display timing overlaps previous cue"
        }
        require(nextEffectiveStartUs == null || draft.endUs <= nextEffectiveStartUs) {
            "display timing overlaps next cue"
        }
        current.copy(
            committedOverride = draft.takeUnless { it == sourceInterval },
            pendingDraft = null,
        )
    }

    fun resetToSource(sessionId: String, unitId: String): DurableDisplayTimingState =
        mutate(sessionId, unitId) { UnitDisplayTimingState() }

    private fun mutate(
        sessionId: String,
        unitId: String,
        transform: (UnitDisplayTimingState) -> UnitDisplayTimingState,
    ): DurableDisplayTimingState = synchronized(lock) {
        requireSafeId(sessionId)
        requireSafeId(unitId)
        validateSession(sessionId)
        val state = readUnlocked(sessionId)
        val current = state.units[unitId] ?: UnitDisplayTimingState()
        val nextUnit = transform(current)
        val nextUnits = state.units.toMutableMap().apply {
            if (nextUnit == UnitDisplayTimingState()) remove(unitId) else put(unitId, nextUnit)
        }
        val next = state.copy(
            revision = Math.addExact(state.revision, 1L),
            units = nextUnits.toMap(),
        )
        writeUnlocked(next)
        next
    }

    private fun readUnlocked(sessionId: String): DurableDisplayTimingState {
        val file = stateFile(sessionId)
        if (!file.isFile) return DurableDisplayTimingState(sessionId)
        val root = JSONObject(file.readText(Charsets.UTF_8))
        require(root.getInt("schemaVersion") == SCHEMA_VERSION) { "unsupported display timing schema" }
        require(root.getString("sessionId") == sessionId) { "display timing session mismatch" }
        val unitsJson = root.getJSONObject("units")
        val units = buildMap {
            unitsJson.keys().forEach { unitId ->
                requireSafeId(unitId)
                val value = unitsJson.getJSONObject(unitId)
                put(
                    unitId,
                    UnitDisplayTimingState(
                        committedOverride = intervalOrNull(value, "committedOverride"),
                        pendingDraft = intervalOrNull(value, "pendingDraft"),
                    ),
                )
            }
        }
        return DurableDisplayTimingState(
            sessionId = sessionId,
            revision = root.getLong("revision"),
            units = units,
        )
    }

    private fun intervalOrNull(owner: JSONObject, key: String): DisplayTimingInterval? {
        if (owner.isNull(key)) return null
        val value = owner.getJSONObject(key)
        return DisplayTimingInterval(
            startUs = value.getLong("startUs"),
            endUs = value.getLong("endUs"),
        )
    }

    private fun writeUnlocked(state: DurableDisplayTimingState) {
        val units = JSONObject()
        state.units.toSortedMap().forEach { (unitId, value) ->
            units.put(
                unitId,
                JSONObject()
                    .put("committedOverride", intervalJson(value.committedOverride))
                    .put("pendingDraft", intervalJson(value.pendingDraft)),
            )
        }
        val payload = JSONObject()
            .put("schemaVersion", SCHEMA_VERSION)
            .put("sessionId", state.sessionId)
            .put("revision", state.revision)
            .put("units", units)
            .toString()
        atomicWrite(stateFile(state.sessionId), payload)
    }

    private fun intervalJson(value: DisplayTimingInterval?): Any = value?.let {
        JSONObject().put("startUs", it.startUs).put("endUs", it.endUs)
    } ?: JSONObject.NULL

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
        require(SAFE_ID.matches(value)) { "unsafe durable timing id" }
    }

    private companion object {
        const val FILE_NAME = "display_timing.json"
        const val SCHEMA_VERSION = 1
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
    }
}
