package com.clw.aivideotranslator.subtitle.android

import android.icu.util.VersionInfo
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clw.aivideotranslator.semantic.TextPolicy
import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Synthetic native controls only: no human readability verdict or X003/X004 activation. */
@RunWith(AndroidJUnit4::class)
class SubtitleLayoutInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private fun font() = SubtitleFonts.loadExperimentCandidate(context)
    private fun environment() = "api=${Build.VERSION.SDK_INT};icu=${VersionInfo.ICU_VERSION};candidate=full-regular-2.012"

    @Test fun packagedFontIsExactPinnedCandidateWithActualStyle() {
        val font = font()
        assertEquals("472abe37ec7a7ce61aa2ca6f01d9c4299f6add431da828f054cd1b41ac0ed5a4", font.profile.assetSha256)
        assertEquals(400, font.typeface.weight)
        assertFalse(font.typeface.isItalic)
        assertTrue(font.supports("مَرْحَبًا Android 17 GPT-6 1,250.50"))
        assertFalse(font.supports("🙂"))
        assertTrue(context.assets.open("licenses/notosansarabic_ofl.txt").bufferedReader().use { it.readText() }.contains("SIL OPEN FONT LICENSE"))
    }

    @Test fun nativeBoundaryProviderPreservesDiacriticAndProtectedUrl() {
        val canonical = TextPolicy.canonicalView("بَ https://example.com/averylongpath")
        val boundaries = AndroidTextBoundaryProvider().boundaries(canonical.displayCanonical, canonical.protectedRanges)
        assertFalse(1 in boundaries.graphemeOffsets)
        assertTrue(boundaries.legalLineBreakOffsets.all { it in boundaries.graphemeOffsets })
        assertTrue(canonical.protectedRanges.isNotEmpty())
        canonical.protectedRanges.forEach { range ->
            assertTrue(boundaries.legalLineBreakOffsets.none { it > range.start && it < range.endExclusive })
        }
    }

    @Test fun emergencyTokenWrapAndMissingGlyphCannotBecomeRenderable() {
        val engine = SubtitleLayoutEngine()
        val geometry = FrameGeometry(426, 240)
        assertFalse(engine.layout("A".repeat(80), geometry, font(), environment()) is SubtitleLayoutResult.Fits)
        val unsupported = engine.layout("مرحبا 🙂", geometry, font(), environment())
        assertEquals(SubtitleLayoutResult.ReviewRequired("BUNDLED_FONT_GLYPH_UNSUPPORTED"), unsupported)
        assertTrue(engine.layout("مرحبا بكم", geometry, font(), environment()) is SubtitleLayoutResult.Fits)
    }

    @Test fun recordNativeGeometryOutcomesWithoutInventingReadabilityPassRate() {
        val fixtures = linkedMapOf(
            "arabic" to "مَرْحَبًا بِكُمْ",
            "mixed" to "يعمل Android 17 مع GPT-6 الآن.",
            "decimal" to "السعر 1,250.50 جنيه في 2026.",
        )
        val geometries = linkedMapOf(
            "240p" to FrameGeometry(426, 240), "480p" to FrameGeometry(854, 480),
            "720p" to FrameGeometry(1280, 720), "1080p" to FrameGeometry(1920, 1080),
            "portrait" to FrameGeometry(360, 640), "square" to FrameGeometry(480, 480),
            "letterbox" to FrameGeometry(1280, 720, PixelRect(0, 90, 1280, 630)),
        )
        val results = JSONArray()
        val loaded = font()
        val engine = SubtitleLayoutEngine()
        for ((geometryId, geometry) in geometries) for ((fixtureId, text) in fixtures) {
            val result = engine.layout(text, geometry, loaded, environment())
            val row = JSONObject().put("fixture", fixtureId).put("geometry", geometryId).put("status", result.status.name)
            when (result) {
                is SubtitleLayoutResult.Fits -> {
                    val d = result.descriptor
                    val canonical = TextPolicy.canonicalView(text)
                    val boundaries = AndroidTextBoundaryProvider().boundaries(d.text, canonical.protectedRanges)
                    assertTrue(SubtitleLineCoverage.usesLegalBreaks(d.text.length, d.lineRanges, boundaries))
                    assertTrue(d.safeRect.contains(d.inkBounds) && d.safeRect.contains(d.boxBounds))
                    assertTrue(d.fontPx >= SubtitleDefaults.floorFontPx(geometry))
                    row.put("fontPx", d.fontPx).put("lineCount", d.lineRanges.size)
                }
                is SubtitleLayoutResult.Overflow -> row.put("reason", result.reason)
                is SubtitleLayoutResult.ReviewRequired -> row.put("reason", result.reason)
            }
            results.put(row)
        }
        val report = JSONObject().put("scope", "X003_PARTIAL_NATIVE_CONTROLS_NOT_HUMAN_ACCEPTANCE")
            .put("buildSha", InstrumentationRegistry.getArguments().getString("buildSha") ?: "UNAVAILABLE")
            .put("api", Build.VERSION.SDK_INT).put("icu", VersionInfo.ICU_VERSION.toString())
            .put("device", Build.MODEL).put("fontSha256", loaded.profile.assetSha256)
            .put("humanReadability", "NOT_MEASURED").put("previewExportParity", "NOT_MEASURED")
            .put("results", results)
        val directory = File(context.filesDir, "x003-evidence").apply { check(mkdirs() || isDirectory) }
        File(directory, "layout-metrics.json").writeText(report.toString(2), Charsets.UTF_8)
    }
}
