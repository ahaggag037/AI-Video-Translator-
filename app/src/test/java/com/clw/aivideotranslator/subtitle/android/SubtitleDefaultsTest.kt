package com.clw.aivideotranslator.subtitle.android

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleDefaultsTest {
    @Test fun lowResolutionProfileHonorsAbsoluteFontFloor() {
        val geometry = FrameGeometry(426, 240)
        assertEquals(16, SubtitleDefaults.preferredFontPx(geometry))
        assertEquals(14, SubtitleDefaults.floorFontPx(geometry))
        val safe = SubtitleDefaults.safeRect(geometry)
        assertTrue(safe.left > 0)
        assertTrue(safe.top > 0)
        assertTrue(safe.right < 426)
        assertTrue(safe.bottom < 240)
    }

    @Test fun portraitAndLandscapeUseSameShortSideScale() {
        val portrait = FrameGeometry(1080, 1920)
        val landscape = FrameGeometry(1920, 1080)
        assertEquals(SubtitleDefaults.preferredFontPx(landscape), SubtitleDefaults.preferredFontPx(portrait))
        assertEquals(SubtitleDefaults.floorFontPx(landscape), SubtitleDefaults.floorFontPx(portrait))
    }

    @Test fun safeAreaBelongsToVideoContentRectNotWholeUiFrame() {
        val geometry = FrameGeometry(
            uprightWidthPx = 1920,
            uprightHeightPx = 1080,
            videoContentRect = PixelRect(160, 90, 1760, 990),
        )
        val safe = SubtitleDefaults.safeRect(geometry)
        assertTrue(safe.left > 160)
        assertTrue(safe.top > 90)
        assertTrue(safe.right < 1760)
        assertTrue(safe.bottom < 990)
    }
}
