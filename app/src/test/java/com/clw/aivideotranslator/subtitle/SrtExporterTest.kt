package com.clw.aivideotranslator.subtitle

import com.clw.aivideotranslator.semantic.PresentationIntervalUs
import com.clw.aivideotranslator.semantic.PresentationTimeUs
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SrtExporterTest {
    private fun item(start: Long, end: Long, text: String) = SrtItem(
        "c1",
        text,
        PresentationIntervalUs(PresentationTimeUs(start), PresentationTimeUs(end)),
    )

    @Test fun timestampsStayAsciiUnderArabicLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale("ar", "EG"))
            val srt = SrtExporter.export(listOf(item(1_234_000, 2_345_000, "مرحبا")), SrtClockPolicy.ORIGINAL_VIDEO)
            assertTrue(srt.contains("00:00:01,234 --> 00:00:02,345"))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun exportedRangeClockSubtractsOriginExactly() {
        val srt = SrtExporter.export(
            listOf(item(5_500_000, 6_500_000, "مرحبا")),
            SrtClockPolicy.EXPORTED_RANGE,
            exportRangeStartUs = 5_000_000,
        )
        assertTrue(srt.contains("00:00:00,500 --> 00:00:01,500"))
    }

    @Test fun subMillisecondIntervalThatCollapsesInSrtPrecisionFailsClosed() {
        try {
            SrtExporter.export(
                listOf(item(100, 900, "قصير")),
                SrtClockPolicy.ORIGINAL_VIDEO,
            )
            fail("SRT must not emit equal timestamps after millisecond quantization")
        } catch (error: IllegalArgumentException) {
            assertEquals("SRT_TIMELINE_NOT_REPRESENTABLE", error.message)
        }
    }

    @Test fun visualLineBreaksAreNotCanonicalSrtOwnership() {
        val srt = SrtExporter.export(
            listOf(item(0, 1_000_000, "السطر الدلالي\nيبقى نصا واحدا")),
            SrtClockPolicy.ORIGINAL_VIDEO,
        )
        assertTrue(srt.contains("السطر الدلالي يبقى نصا واحدا"))
    }

    @Test fun unsafeArrowFailsExportWithoutMutatingSemanticText() {
        val original = "literal --> marker"
        try {
            SrtExporter.export(listOf(item(0, 1_000_000, original)), SrtClockPolicy.ORIGINAL_VIDEO)
            fail("unsafe SRT syntax must not be silently rewritten")
        } catch (_: IllegalArgumentException) {
            assertEquals("literal --> marker", original)
        }
    }
}
