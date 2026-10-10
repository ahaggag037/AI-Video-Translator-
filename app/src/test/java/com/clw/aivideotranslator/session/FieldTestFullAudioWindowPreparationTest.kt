package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.junit.Test

class FieldTestFullAudioWindowPreparationTest {
    @Test fun acceptsReadableInspectionOnlyWhenItMatchesBoundFingerprint() {
        val attachment = attachment()
        FieldTestFullAudioWindowPreparation.requireCurrentSource(
            SourceContentInspection(
                observedContentUri = attachment.contentUri,
                status = SourceReadStatus.READABLE,
                fingerprint = attachment.fingerprint,
            ),
            attachment,
            "test",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsChangedSourceBytesUnderSameUri() {
        val attachment = attachment()
        FieldTestFullAudioWindowPreparation.requireCurrentSource(
            SourceContentInspection(
                observedContentUri = attachment.contentUri,
                status = SourceReadStatus.READABLE,
                fingerprint = SourceFingerprint("b".repeat(64), 123L),
            ),
            attachment,
            "test",
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnreadableSourceEvenIfLocatorMatches() {
        val attachment = attachment()
        FieldTestFullAudioWindowPreparation.requireCurrentSource(
            SourceContentInspection(
                observedContentUri = attachment.contentUri,
                status = SourceReadStatus.PERMISSION_MISSING,
            ),
            attachment,
            "test",
        )
    }

    private fun attachment() = SourceAttachment(
        sessionId = "session-field-test",
        contentUri = "content://example.provider/video/1",
        persistedReadGrantAtCapture = true,
        fingerprint = SourceFingerprint("a".repeat(64), 123L),
        durationUs = 120_000_000L,
        selectedRange = PresentationIntervalUs(PresentationTimeUs(0L), PresentationTimeUs(120_000_000L)),
        audioTrack = SourceAudioTrack(
            containerIndex = 0,
            mime = "audio/mp4a-latm",
            language = "en",
            sampleRateHz = 48_000,
            channelCount = 2,
        ),
    )
}
