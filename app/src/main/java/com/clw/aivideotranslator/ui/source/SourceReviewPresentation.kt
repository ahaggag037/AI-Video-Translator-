package com.clw.aivideotranslator.ui.source

import com.clw.aivideotranslator.VideoMetadata
import com.clw.aivideotranslator.source.CandidateRejectReason
import com.clw.aivideotranslator.source.CandidateSourceState

internal enum class SourceReviewPhase {
    IDLE,
    PROBING,
    READY,
    REJECTED,
    ACTIVATING,
    ACTIVATED,
}

internal data class SourceReviewPresentation(
    val phase: SourceReviewPhase,
    val primaryMessage: String,
    val supportingMessage: String? = null,
    val displayName: String? = null,
    val durationLabel: String? = null,
    val resolutionLabel: String? = null,
    val sizeLabel: String? = null,
    val audioLabel: String? = null,
    val errorMessage: String? = null,
    val isBusy: Boolean = false,
    val canChangeVideo: Boolean = false,
    val canAdoptVideo: Boolean = false,
) {
    companion object {
        fun from(
            state: CandidateSourceState,
            metadata: VideoMetadata?,
        ): SourceReviewPresentation = when (state) {
            CandidateSourceState.Idle -> SourceReviewPresentation(
                phase = SourceReviewPhase.IDLE,
                primaryMessage = "اختر فيديو للمتابعة.",
                supportingMessage = "سيتم فحص الفيديو والصوت قبل تغيير المصدر المحفوظ.",
                canChangeVideo = true,
            )

            is CandidateSourceState.Probing -> SourceReviewPresentation(
                phase = SourceReviewPhase.PROBING,
                primaryMessage = "جارٍ فحص الفيديو والصوت قبل اعتماد المصدر.",
                supportingMessage = "لن يتم استبدال المصدر المحفوظ قبل اكتمال الفحص واعتماد الفيديو.",
                isBusy = true,
                canChangeVideo = true,
            )

            is CandidateSourceState.Ready -> {
                val trustedMetadata = metadata?.takeIf { it.displayName == state.displayName }
                SourceReviewPresentation(
                    phase = SourceReviewPhase.READY,
                    primaryMessage = "راجع المصدر قبل اعتماده.",
                    supportingMessage = "اعتماد الفيديو هو الخطوة الوحيدة التي تسمح بتغيير المصدر المحفوظ.",
                    displayName = state.displayName,
                    durationLabel = trustedMetadata?.durationLabel,
                    resolutionLabel = trustedMetadata?.resolutionLabel,
                    sizeLabel = trustedMetadata?.sizeLabel,
                    audioLabel = trustedMetadata?.let { if (it.hasAudio) "الصوت جاهز" else "لا يوجد صوت صالح" },
                    errorMessage = state.lastActivationError,
                    canChangeVideo = true,
                    canAdoptVideo = true,
                )
            }

            is CandidateSourceState.Rejected -> SourceReviewPresentation(
                phase = SourceReviewPhase.REJECTED,
                primaryMessage = "تعذر اعتماد الفيديو المحدد.",
                supportingMessage = "المصدر المحفوظ لم يتغير.",
                errorMessage = rejectionMessage(state.reason),
                canChangeVideo = true,
            )

            is CandidateSourceState.Activating -> SourceReviewPresentation(
                phase = SourceReviewPhase.ACTIVATING,
                primaryMessage = "جارٍ اعتماد المصدر بأمان…",
                supportingMessage = "سيبقى العمل السابق مرجع الاسترداد حتى يكتمل الاعتماد.",
                isBusy = true,
            )

            is CandidateSourceState.Activated -> SourceReviewPresentation(
                phase = SourceReviewPhase.ACTIVATED,
                primaryMessage = "تم اعتماد المصدر.",
                supportingMessage = "يمكن متابعة إعداد الترجمة.",
            )
        }

        private fun rejectionMessage(reason: CandidateRejectReason): String = when (reason) {
            CandidateRejectReason.NO_AUDIO -> "لا يحتوي الفيديو على مسار صوت صالح للترجمة."
            CandidateRejectReason.ACCESS_LIMITED ->
                "تعذر الوصول إلى الفيديو. اختره مرة أخرى واسمح للتطبيق بالوصول إليه."
            CandidateRejectReason.INVALID -> "الفيديو المحدد غير صالح أو لا يمكن قراءة بياناته."
            CandidateRejectReason.PROBE_FAILED -> "تعذر فحص الفيديو. اختر فيديو آخر أو حاول مجددًا."
        }
    }
}
