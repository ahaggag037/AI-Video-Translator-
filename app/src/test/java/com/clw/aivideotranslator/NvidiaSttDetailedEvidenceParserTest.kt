package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaSttDetailedEvidenceParserTest {
    @Test fun detailedParsePreservesTextAndRawOffsetsWithoutInterpretingTiming() {
        val body = """
            {
              "text": "Hello world.",
              "words": [
                {"word": "Hello", "start": 0.08, "end": 0.20, "confidence": 0.9},
                {"word": "world.", "start": 0.22, "end": 0.80, "confidence": 0.8}
              ]
            }
        """.trimIndent()

        val detailed = NvidiaSttDetailedEvidenceParser.parse(body, 206)

        assertEquals("Hello world.", detailed.result.transcript)
        assertEquals(listOf("Hello", "world."), detailed.result.words.map { it.text })
        assertEquals(206, detailed.result.httpStatus)
        assertTrue(detailed.result.words.all { it.startMs == null && it.endMs == null })
        assertEquals(body, detailed.timingEvidence.rawResponseUtf8)
        assertEquals(NvidiaSttTimingEvidenceInspector.inspect(body), detailed.timingEvidence)
        assertEquals("0.08", detailed.timingEvidence.sources.single().words.first().startFields.getValue("start").rawText)
        assertEquals("0.80", detailed.timingEvidence.sources.single().words.last().endFields.getValue("end").rawText)
        assertEquals(NvidiaSttParserContract.ID, detailed.parserVersion)
    }

    @Test fun evidenceDoesNotMergeMultipleSchemasIntoAcceptedTiming() {
        val body = """
            {
              "text": "accepted",
              "words_info": {
                "words": [{"word": "info", "start_ms": 1000, "end_ms": 1200}]
              },
              "words": [
                {"word": "root", "start_ms": 1300, "end_ms": 1500}
              ]
            }
        """.trimIndent()

        val detailed = NvidiaSttDetailedEvidenceParser.parse(body)

        assertEquals(listOf("info", "root"), detailed.result.words.map { it.text })
        assertTrue(detailed.result.words.all { it.startMs == null && it.endMs == null })
        assertTrue(detailed.timingEvidence.hasMultipleWordSchemas)
        assertEquals(2, detailed.timingEvidence.sources.size)
        assertNull(detailed.result.firstWordStartMs)
        assertNull(detailed.result.lastWordEndMs)
    }
}
