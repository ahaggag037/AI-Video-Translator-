package com.clw.aivideotranslator.app

import com.clw.aivideotranslator.NvidiaSttResult
import com.clw.aivideotranslator.NvidiaWord
import com.clw.aivideotranslator.SourceUnit
import com.clw.aivideotranslator.TranslationEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProductPresentationUnitResolverTest {
    @Test fun keepsExactLiveUnitsWhenIdsMatchAcceptedEntries() {
        val live = listOf(SourceUnit("u0001", 100L, 900L, "hello"))
        val entries = listOf(TranslationEntry("u0001", "مرحبا"))

        assertEquals(live, ProductPresentationUnitResolver.resolve(live, entries, null))
    }

    @Test fun reconstructsDeterministicUnitsFromRecoveredTimedStt() {
        val timed = NvidiaSttResult(
            transcript = "hello world",
            words = listOf(
                NvidiaWord("hello", 100L, 600L, 0.99),
                NvidiaWord("world", 700L, 1_200L, 0.98),
            ),
            httpStatus = 200,
        )
        val entries = listOf(TranslationEntry("u0001", "مرحبا بالعالم"))

        val resolved = ProductPresentationUnitResolver.resolve(null, entries, timed)

        assertEquals(listOf("u0001"), resolved?.map { it.id })
        assertEquals("hello world", resolved?.single()?.sourceText)
        assertEquals(100L, resolved?.single()?.startMs)
        assertEquals(1_200L, resolved?.single()?.endMs)
    }

    @Test fun failsClosedWhenRecoveredUnitIdsDoNotMatchAcceptedEntries() {
        val timed = NvidiaSttResult(
            transcript = "hello",
            words = listOf(NvidiaWord("hello", 100L, 600L, 0.99)),
            httpStatus = 200,
        )
        val entries = listOf(TranslationEntry("different", "مرحبا"))

        assertNull(ProductPresentationUnitResolver.resolve(null, entries, timed))
    }
}
