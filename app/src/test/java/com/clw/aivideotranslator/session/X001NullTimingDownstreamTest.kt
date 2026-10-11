package com.clw.aivideotranslator.session

import com.clw.aivideotranslator.NvidiaSttClient
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** X001 regression: untimed hosted text must stop before any P0-F timeline can be minted. */
class X001NullTimingDownstreamTest {
    @Test fun failClosedHostedResultCannotSilentlyEnterLegacyTimelinePlanner() {
        val result = NvidiaSttClient.parseResponseWithoutTimingAuthority(
            """
            {"text":"Hello world.","words":[
              {"word":"Hello","start":0.10,"end":0.25,"confidence":0.9},
              {"word":"world.","start":0.30,"end":0.80,"confidence":0.8}
            ]}
            """.trimIndent(),
            200,
        )

        assertTrue(result.words.isNotEmpty())
        result.words.forEach { word ->
            assertNull(word.startMs)
            assertNull(word.endMs)
        }

        try {
            LegacyParityTranslationPlanner.plan(result)
            fail("unverified hosted offsets must not mint legacy source units")
        } catch (error: IllegalArgumentException) {
            assertTrue(error.message?.contains("توقيت") == true)
        }
    }
}
