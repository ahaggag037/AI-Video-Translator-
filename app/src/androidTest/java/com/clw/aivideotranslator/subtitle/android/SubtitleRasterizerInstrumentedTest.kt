package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SubtitleRasterizerInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun font() = SubtitleFonts.loadExperimentCandidate(context)
    private fun environment() = "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Test
    fun acceptedDescriptorProducesImmutableFullFrameRasterWithoutReflow() {
        val geometry = FrameGeometry(1280, 720)
        val loaded = font()
        val descriptor = requireFits("يعمل Android 17 مع GPT-6 الآن.", geometry, loaded)
        val result = SubtitleRasterizer().rasterize(descriptor, geometry, loaded)
        val raster = (result as SubtitleRasterResult.Ready).raster
        try {
            assertFalse(raster.bitmap.isMutable)
            assertEquals(geometry.uprightWidthPx, raster.bitmap.width)
            assertEquals(geometry.uprightHeightPx, raster.bitmap.height)
            assertEquals(0, raster.bitmap.getPixel(0, 0) ushr 24)

            val box = descriptor.boxBounds
            val centerPixel = raster.bitmap.getPixel((box.left + box.right) / 2, (box.top + box.bottom) / 2)
            assertTrue("accepted subtitle box must produce visible raster content", centerPixel ushr 24 > 0)
        } finally {
            raster.bitmap.recycle()
        }
    }

    @Test
    fun descriptorWithWrongPinnedFontIdentityFailsClosed() {
        val geometry = FrameGeometry(854, 480)
        val loaded = font()
        val descriptor = requireFits("مَرْحَبًا بِكُمْ", geometry, loaded)
        val tampered = descriptor.copy(fontAssetHash = "0".repeat(64))
        assertEquals(
            SubtitleRasterResult.Rejected("FONT_IDENTITY_MISMATCH"),
            SubtitleRasterizer().rasterize(tampered, geometry, loaded),
        )
    }

    @Test
    fun two1080pPublishedRastersFitN23SixteenMiBCacheEnvelope() {
        val geometry = FrameGeometry(1920, 1080)
        val loaded = font()
        val firstDescriptor = requireFits("الترجمة الأولى واضحة.", geometry, loaded)
        val secondDescriptor = requireFits("الترجمة التالية واضحة أيضًا.", geometry, loaded)
        val rasterizer = SubtitleRasterizer()
        val first = (rasterizer.rasterize(firstDescriptor, geometry, loaded) as SubtitleRasterResult.Ready).raster
        val second = (rasterizer.rasterize(secondDescriptor, geometry, loaded) as SubtitleRasterResult.Ready).raster
        try {
            assertTrue(first.byteCount > 0L)
            assertTrue(second.byteCount > 0L)
            assertTrue(
                "current+next 1080p rasters must fit the N23 16 MiB raster-cache envelope",
                first.byteCount + second.byteCount <= 16L * 1024L * 1024L,
            )
        } finally {
            first.bitmap.recycle()
            second.bitmap.recycle()
        }
    }

    @Test
    fun rasterAboveSupportedMemoryEnvelopeIsRejectedBeforeBitmapAllocation() {
        val loaded = font()
        val supportedGeometry = FrameGeometry(1920, 1080)
        val descriptor = requireFits("اختبار الذاكرة", supportedGeometry, loaded)
        val oversizedGeometry = FrameGeometry(3000, 2000)
        assertEquals(
            SubtitleRasterResult.Rejected("RASTER_MEMORY_BUDGET_EXCEEDED"),
            SubtitleRasterizer().rasterize(descriptor, oversizedGeometry, loaded),
        )
    }

    private fun requireFits(
        text: String,
        geometry: FrameGeometry,
        loaded: LoadedSubtitleFont,
    ): SubtitleLayoutDescriptor {
        val result = SubtitleLayoutEngine().layout(text, geometry, loaded, environment())
        assertTrue("fixture must be accepted by the native layout engine", result is SubtitleLayoutResult.Fits)
        return (result as SubtitleLayoutResult.Fits).descriptor
    }
}
