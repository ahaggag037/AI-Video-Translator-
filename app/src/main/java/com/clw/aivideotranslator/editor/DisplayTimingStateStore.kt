package com.clw.aivideotranslator.editor

import java.io.File
import java.io.FileOutputStream
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.Locale
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

data class TimingFieldDraft(
    val startText: String,
    val endText: String,
)

data class UnitDisplayTimingState(
    val committedOverride: DisplayTimingInterval? = null,
    val pendingDraft: DisplayTimingInterval? = null,
    val pendingFieldDraft: TimingFieldDraft? = null,
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
        current.copy(
            pendingDraft = draft.takeUnless { it == committed },
            pendingFieldDraft = null,
        )
    }

    /**
     * Persists exactly what the user typed even when it is temporarily invalid. A parsed interval is
     * retained only when the two fields already form a valid positive interval. Neither form changes
     * export truth until an explicit successful commit.
     */
    fun setPendingFieldDraft(
        sessionId: String,
        unitId: String,
        startText: String,
        endText: String,
    ): DurableDisplayTimingState = mutate(sessionId, unitId) { current ->
        val parsedStart = parseTimecodeUs(startText)
        val parsedEnd = parseTimecodeUs(endText)
        val parsedInterval = if (
            parsedStart != null && parsedEnd != null && parsedStart >= 0L && parsedStart < parsedEnd
        ) {
            DisplayTimingInterval(parsedStart, parsedEnd)
        } else {
            null
        }
        current.copy(
            pendingDraft = parsedInterval,
            pendingFieldDraft = TimingFieldDraft(startText = startText, endText = endText),
        )
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
        commitValidated(
            current = current,
            draft = draft,
            sourceInterval = sourceInterval,
            videoDurationUs = videoDurationUs,
            previousEffectiveEndUs = previousEffectiveEndUs,
            nextEffectiveStartUs = nextEffectiveStartUs,
        )
    }

    fun commitPendingFieldDraft(
        sessionId: String,
        unitId: String,
        sourceInterval: DisplayTimingInterval,
        videoDurationUs: Long?,
        previousEffectiveEndUs: Long?,
        nextEffectiveStartUs: Long?,
    ): DurableDisplayTimingState = mutate(sessionId, unitId) { current ->
        val fields = requireNotNull(current.pendingFieldDraft) { "no pending timing field draft" }
        val startUs = requireNotNull(parseTimecodeUs(fields.startText)) { "invalid timing start field" }
        val endUs = requireNotNull(parseTimecodeUs(fields.endText)) { "invalid timing end field" }
        require(startUs < endUs) { "display timing start must be before end" }
        val draft = DisplayTimingInterval(startUs, endUs)
        commitValidated(
            current = current,
            draft = draft,
            sourceInterval = sourceInterval,
            videoDurationUs = videoDurationUs,
            previousEffectiveEndUs = previousEffectiveEndUs,
            nextEffectiveStartUs = nextEffectiveStartUs,
        )
    }

    fun resetToSource(sessionId: String, unitId: String): DurableDisplayTimingState =
        mutate(sessionId, unitId) { UnitDisplayTimingState() }

    private fun commitValidated(
        current: UnitDisplayTimingState,
        draft: DisplayTimingInterval,
        sourceInterval: DisplayTimingInterval,
        videoDurationUs: Long?,
        previousEffectiveEndUs: Long?,
        nextEffectiveStartUs: Long?,
    ): UnitDisplayTimingState {
        require(videoDurationUs == null || draft.endUs <= videoDurationUs) {
            "display timing exceeds video duration"
        }
        require(previousEffectiveEndUs == null || draft.startUs >= previousEffectiveEndUs) {
            "display timing overlaps previous cue"
        }
        require(nextEffectiveStartUs == null || draft.endUs <= nextEffectiveStartUs) {
            "display timing overlaps next cue"
        }
        return current.copy(
            committedOverride = draft.takeUnless { it == sourceInterval },
            pendingDraft = null,
            pendingFieldDraft = null,
        )
    }

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
                        pendingFieldDraft = fieldDraftOrNull(value, "pendingFieldDraft"),
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
        if (!owner.has(key) || owner.isNull(key)) return null
        val value = owner.getJSONObject(key)
        return DisplayTimingInterval(
            startUs = value.getLong("startUs"),
            endUs = value.getLong("endUs"),
        )
    }

    private fun fieldDraftOrNull(owner: JSONObject, key: String): TimingFieldDraft? {
        if (!owner.has(key) || owner.isNull(key)) return null
        val value = owner.getJSONObject(key)
        return TimingFieldDraft(
            startText = value.getString("startText"),
            endText = value.getString("endText"),
        )
    }

    private fun writeUnlocked(state: DurableDisplayTimingState) {
        val units = JSONObject()
        state.units.toSortedMap().forEach { (unitId, value) ->
            units.put(
                unitId,
                JSONObject()
                    .put("committedOverride", intervalJson(value.committedOverride))
                    .put("pendingDraft", intervalJson(value.pendingDraft))
                    .put("pendingFieldDraft", fieldDraftJson(value.pendingFieldDraft)),
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

    private fun fieldDraftJson(value: TimingFieldDraft?): Any = value?.let {
        JSONObject().put("startText", it.startText).put("endText", it.endText)
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

    private fun parseTimecodeUs(value: String): Long? {
        val match = TIMECODE.matchEntire(value.trim()) ?: return null
        val minutes = match.groupValues[1].toLongOrNull() ?: return null
        val seconds = match.groupValues[2].toLongOrNull() ?: return null
        val millis = match.groupValues[3].toLongOrNull() ?: return null
        if (seconds !in 0L..59L || millis !in 0L..999L) return null
        return try {
            Math.multiplyExact(
                Math.addExact(
                    Math.multiplyExact(minutes, 60_000L),
                    Math.addExact(Math.multiplyExact(seconds, 1_000L), millis),
                ),
                1_000L,
            )
        } catch (_: ArithmeticException) {
            null
        }
    }

    @Suppress("unused")
    private fun formatTimecodeUs(valueUs: Long): String {
        require(valueUs >= 0L)
        val totalMs = valueUs / 1_000L
        return String.format(
            Locale.ROOT,
            "%02d:%02d.%03d",
            totalMs / 60_000L,
            (totalMs / 1_000L) % 60L,
            totalMs % 1_000L,
        )
    }

    private fun requireSafeId(value: String) {
        require(SAFE_ID.matches(value)) { "unsafe durable timing id" }
    }

    private companion object {
        const val FILE_NAME = "display_timing.json"
        const val SCHEMA_VERSION = 1
        val SAFE_ID = Regex("[A-Za-z0-9._-]+")
        val TIMECODE = Regex("^(\\d+):(\\d{2})\\.(\\d{3})$")
    }
}
