package com.clw.aivideotranslator.semantic

import org.junit.Assert.*
import org.junit.Test

class TextPolicyTest {
    @Test fun usesNfcWithoutCompatibilityFoldingOrDigitConversion() {
        val raw = "أهلا ١٢٫٥ Android"
        val view = TextPolicy.canonicalView(raw)
        assertTrue(view.displayCanonical.contains("أهلا"))
        assertTrue(view.displayCanonical.contains("١٢٫٥"))
        assertTrue(view.displayCanonical.contains("Android"))
    }

    @Test fun protectsUrlsAndEmailsAsOpaqueSpansAndCanonicalOffsetsStayValid() {
        val raw = "أ https://example.com/a?x=1&y=2 ثم test+tag@example.com"
        val view = TextPolicy.canonicalView(raw)
        assertTrue(view.displayCanonical.contains("https://example.com/a?x=1&y=2"))
        assertTrue(view.displayCanonical.contains("test+tag@example.com"))
        assertEquals(2, view.protectedRanges.size)
        view.protectedRanges.forEach { range ->
            val value = view.displayCanonical.substring(range.start, range.endExclusive)
            assertTrue(value.startsWith("http") || value.contains('@'))
        }
    }

    @Test fun protectedOffsetsRemainValidAfterCanonicalWhitespaceTrim() {
        val raw = "   https://example.com/x ثم test+tag@example.com   "
        val view = TextPolicy.canonicalView(raw)
        assertEquals("https://example.com/x ثم test+tag@example.com", view.displayCanonical)
        assertEquals(2, view.protectedRanges.size)
        assertEquals(
            listOf("https://example.com/x", "test+tag@example.com"),
            view.protectedRanges.map { range ->
                view.displayCanonical.substring(range.start, range.endExclusive)
            },
        )
    }

    @Test fun preservesJoinersAndFlagsBidiOverridesForReview() {
        val joiners = TextPolicy.canonicalView("ع\u200Dر\u200Cبي")
        assertTrue(joiners.displayCanonical.contains('\u200D'))
        assertTrue(joiners.displayCanonical.contains('\u200C'))
        assertFalse(joiners.requiresReview)

        val bidi = TextPolicy.canonicalView("abc\u202Edef")
        assertTrue(bidi.requiresReview)
        assertTrue("BIDI_CONTROL_REVIEW" in bidi.warnings)
    }

    @Test fun invalidSurrogateFailsClosed() {
        try {
            TextPolicy.canonicalView("broken \uD83D")
            fail("unpaired surrogate must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
