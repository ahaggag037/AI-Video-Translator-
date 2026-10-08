package com.clw.aivideotranslator.subtitle.android

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class SubtitleLayoutTypesTest {
    @Test fun acceptedDescriptorRequiresInkAndBoxInsideSafeArea() {
        val safe = PixelRect(10, 10, 190, 90)
        val descriptor = SubtitleLayoutDescriptor(
            text = "مرحبا",
            fontPx = 24,
            lineRanges = listOf(0..4),
            inkBounds = PixelRect(50, 40, 150, 65),
            boxBounds = PixelRect(40, 30, 160, 75),
            safeRect = safe,
            fontAssetHash = "a".repeat(64),
            rendererEnvironment = "android-api-test",
        )
        assertEquals(SubtitleLayoutStatus.FITS, SubtitleLayoutResult.Fits(descriptor).status)
    }

    @Test fun descriptorFailsClosedWhenBoxEscapesSafeArea() {
        try {
            SubtitleLayoutDescriptor(
                text = "مرحبا",
                fontPx = 24,
                lineRanges = listOf(0..4),
                inkBounds = PixelRect(50, 40, 150, 65),
                boxBounds = PixelRect(0, 30, 160, 75),
                safeRect = PixelRect(10, 10, 190, 90),
                fontAssetHash = "a".repeat(64),
                rendererEnvironment = "android-api-test",
            )
            fail("unsafe authored layout must not be constructible")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
