package com.clw.aivideotranslator.subtitle.android

import org.junit.Assert.*
import org.junit.Test

class BundledArabicCoverageTest {
    @Test fun officialFullFaceCoversArabicLatinDigitsAndCommonProtectedIdentifiers() {
        listOf("مَرْحَبًا بِكُمْ", "Android 17 يعمل مع GPT-6", "السعر 1,250.50 جنيه؛ ١٢٫٥٪",
            "https://example.com/a?b=2", "ops@example.com", "(العربية/English)")
            .forEach { assertTrue("missing pinned-font coverage: $it", BundledArabicCoverage.supports(it)) }
    }

    @Test fun unsupportedEmojiCjkAndUnpairedSurrogatesDoNotBorrowDeviceFonts() {
        listOf("🙂", "中文", "\uD800").forEach { assertFalse(BundledArabicCoverage.supports(it)) }
        assertTrue(BundledArabicCoverage.supports("ب\u200Cب\u200Dب"))
    }
}
