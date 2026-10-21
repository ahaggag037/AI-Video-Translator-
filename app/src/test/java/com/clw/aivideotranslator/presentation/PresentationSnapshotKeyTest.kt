package com.clw.aivideotranslator.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class PresentationSnapshotKeyTest {
    private val style = SubtitleStyle(
        fontFamilyId = "noto-sans-arabic",
        fontWeight = 600,
        fontSizeSp = 42f,
        lineHeightSp = 52f,
        textColorArgb = 0xFFFFFFFF,
        opacity = 1f,
        alignment = SubtitleTextAlignment.CENTER,
        outlineWidthDp = 2f,
        shadowEnabled = true,
        backgroundEnabled = false,
        anchor = SubtitleAnchor.BOTTOM_CENTER,
        offsetXFraction = 0f,
        offsetYFraction = 0f,
        safeMarginFraction = 0.05f,
        maxWidthFraction = 0.86f,
        maxLines = 2,
    )

    private val base = PresentationTruth(
        cueId = "cue-1",
        textRevisionId = "manual-7",
        effectiveText = "هذه ترجمة عربية",
        displayStartUs = 1_000_000L,
        displayEndUs = 3_000_000L,
        style = style,
        frameGeometry = FrameGeometry(widthPx = 1920, heightPx = 1080),
        fontProfile = PinnedFontProfile(
            profileId = "noto-arabic-v1",
            familyId = "noto-sans-arabic",
            weight = 600,
            assetSha256 = "font-sha",
        ),
    )

    @Test fun sameTruthAlwaysProducesSameSnapshotKey() {
        assertEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(base.copy()),
        )
    }

    @Test fun textRevisionChangesSnapshotKey() {
        assertNotEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(base.copy(textRevisionId = "manual-8")),
        )
    }

    @Test fun displayTimingChangesSnapshotKey() {
        assertNotEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(base.copy(displayEndUs = 3_100_000L)),
        )
    }

    @Test fun styleChangesSnapshotKey() {
        assertNotEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(base.copy(style = style.copy(fontSizeSp = 44f))),
        )
    }

    @Test fun frameGeometryChangesSnapshotKey() {
        assertNotEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(base.copy(frameGeometry = FrameGeometry(1280, 720))),
        )
    }

    @Test fun pinnedFontProfileChangesSnapshotKey() {
        assertNotEquals(
            PresentationSnapshotKey.from(base),
            PresentationSnapshotKey.from(
                base.copy(fontProfile = base.fontProfile.copy(assetSha256 = "different-font-sha")),
            ),
        )
    }
}
