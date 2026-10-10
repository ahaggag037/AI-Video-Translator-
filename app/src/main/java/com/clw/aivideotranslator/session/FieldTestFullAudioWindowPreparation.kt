package com.clw.aivideotranslator.session

import android.content.Context
import android.net.Uri
import com.clw.aivideotranslator.SttAudioPreparer
import com.clw.aivideotranslator.SttAudioProfile
import java.io.File

internal data class FieldTestFullAudioPreparation(
    val fullProfile: SttAudioProfile,
    val windows: List<FieldTestPreparedSttWindow>,
)

/**
 * Non-canonical round-2 preparation path.
 *
 * The currently bound content URI is verified against the immutable attachment fingerprint before
 * and after the one full audio decode. Decoder output may contain normal codec padding beyond the
 * container duration, so only frame-exact PCM that lies inside the attachment timeline is exposed
 * as provider windows. The original non-zero audio presentation origin is preserved.
 */
internal object FieldTestFullAudioWindowPreparation {
    fun prepare(
        context: Context,
        attachment: SourceAttachment,
        outputDir: File,
    ): Result<FieldTestFullAudioPreparation> = runCatching {
        require(attachment.selectedRange.start.value == 0L &&
            attachment.selectedRange.end.value == attachment.durationUs
        ) { "field-test full-video STT requires the complete selected source" }

        val before = SourceContentProbe.inspect(context.contentResolver, attachment.contentUri)
        requireCurrentSource(before, attachment, "before full audio decode")

        // The legacy-named public surface accepts an explicit duration. One call for the complete
        // attachment avoids repeated decoder startup and repeated prefix decoding.
        val fullProfile = SttAudioPreparer.prepareFirstMinute(
            context = context,
            sourceUri = Uri.parse(attachment.contentUri),
            durationUs = attachment.durationUs,
        ).getOrThrow()
        require(fullProfile.sourceStartUs >= 0L && fullProfile.sourceStartUs < attachment.durationUs) {
            "decoded full audio starts outside the source attachment"
        }
        require(fullProfile.sourceEndUs > fullProfile.sourceStartUs) {
            "decoded full audio has no positive presentation interval"
        }

        val after = SourceContentProbe.inspect(context.contentResolver, attachment.contentUri)
        requireCurrentSource(after, attachment, "after full audio decode")
        require(before.fingerprint == after.fingerprint) { "source changed while full audio was decoded" }

        val windows = FieldTestPcmWindowSplitter.split(
            fullProfile = fullProfile,
            outputDir = outputDir,
            sourceEndLimitUs = attachment.durationUs,
        )
        require(windows.first().window.startUs == fullProfile.sourceStartUs) {
            "prepared STT windows shifted the decoded audio origin"
        }
        require(windows.last().window.endUs <= attachment.durationUs) {
            "prepared STT windows exceed the source attachment"
        }
        FieldTestFullAudioPreparation(fullProfile, windows)
    }

    internal fun requireCurrentSource(
        inspection: SourceContentInspection,
        attachment: SourceAttachment,
        stage: String,
    ) {
        require(inspection.observedContentUri == attachment.contentUri) { "$stage: source locator changed" }
        require(inspection.status == SourceReadStatus.READABLE) { "$stage: source is not readable" }
        require(inspection.fingerprint == attachment.fingerprint) { "$stage: source fingerprint changed" }
    }
}
