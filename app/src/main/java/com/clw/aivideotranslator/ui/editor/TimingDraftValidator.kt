package com.clw.aivideotranslator.ui.editor

internal data class TimingDraft(
    val startUs: Long,
    val endUs: Long,
)

internal enum class TimingDraftError {
    START_INVALID,
    END_INVALID,
    NEGATIVE_START,
    START_NOT_BEFORE_END,
    AFTER_VIDEO_END,
    OVERLAPS_PREVIOUS,
    OVERLAPS_NEXT,
}

internal data class TimingDraftValidation(
    val errors: Set<TimingDraftError>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

internal object TimingDraftValidator {
    fun validate(
        draft: TimingDraft,
        videoDurationUs: Long? = null,
        previousEffectiveEndUs: Long? = null,
        nextEffectiveStartUs: Long? = null,
    ): TimingDraftValidation {
        val errors = buildSet {
            if (draft.startUs < 0L) add(TimingDraftError.NEGATIVE_START)
            if (draft.startUs >= draft.endUs) add(TimingDraftError.START_NOT_BEFORE_END)
            if (videoDurationUs != null && draft.endUs > videoDurationUs) {
                add(TimingDraftError.AFTER_VIDEO_END)
            }
            if (previousEffectiveEndUs != null && draft.startUs < previousEffectiveEndUs) {
                add(TimingDraftError.OVERLAPS_PREVIOUS)
            }
            if (nextEffectiveStartUs != null && draft.endUs > nextEffectiveStartUs) {
                add(TimingDraftError.OVERLAPS_NEXT)
            }
        }
        return TimingDraftValidation(errors)
    }
}
