package com.clw.aivideotranslator.ui.editor

internal data class EditorCueSelection(
    val unitId: String?,
    val index: Int,
    val count: Int,
    val canPrevious: Boolean,
    val canNext: Boolean,
)

/**
 * Keeps editor cue selection tied to durable unit identity while deriving the visible index from the
 * latest ordered unit list. This prevents list refreshes from silently moving the user to a different
 * cue merely because indices changed.
 */
internal class EditorCueNavigator {
    private var selectedUnitId: String? = null

    fun reconcile(unitIds: List<String>): EditorCueSelection {
        require(unitIds.distinct().size == unitIds.size) { "duplicate editor cue id" }

        if (unitIds.isEmpty()) {
            selectedUnitId = null
            return EditorCueSelection(
                unitId = null,
                index = 0,
                count = 0,
                canPrevious = false,
                canNext = false,
            )
        }

        if (selectedUnitId !in unitIds) {
            selectedUnitId = unitIds.first()
        }

        return selectionFor(unitIds)
    }

    fun next(unitIds: List<String>): EditorCueSelection {
        val current = reconcile(unitIds)
        if (current.unitId == null || !current.canNext) return current

        selectedUnitId = unitIds[current.index + 1]
        return selectionFor(unitIds)
    }

    fun previous(unitIds: List<String>): EditorCueSelection {
        val current = reconcile(unitIds)
        if (current.unitId == null || !current.canPrevious) return current

        selectedUnitId = unitIds[current.index - 1]
        return selectionFor(unitIds)
    }

    private fun selectionFor(unitIds: List<String>): EditorCueSelection {
        val id = selectedUnitId
        val index = if (id == null) -1 else unitIds.indexOf(id)
        check(index >= 0) { "selected editor cue must exist in current unit list" }
        return EditorCueSelection(
            unitId = id,
            index = index,
            count = unitIds.size,
            canPrevious = index > 0,
            canNext = index < unitIds.lastIndex,
        )
    }
}
