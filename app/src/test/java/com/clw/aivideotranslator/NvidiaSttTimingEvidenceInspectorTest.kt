package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaSttTimingEvidenceInspectorTest {
    @Test fun rawTimingEvidencePreservesSchemaPathFieldTypeAndText() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "results": [
                {
                  "alternatives": [
                    {
                      "words": [
                        {
                          "word": "hello",
                          "start_time": 1.25,
                          "start_ms": "1250",
                          "end_time": true,
                          "end_ms": null
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(setOf(NvidiaWordTimingSchema.RESULTS_ALTERNATIVES_WORDS), evidence.presentSchemas)
        assertFalse(evidence.hasMultipleWordSchemas)
        val source = evidence.sources.single()
        assertEquals("$.results[0].alternatives[0].words", source.schemaPath)
        val word = source.words.single()
        assertEquals(0, word.itemIndex)
        assertEquals("$.results[0].alternatives[0].words[0]", word.itemPath)
        assertEquals("hello", word.text)

        val startTime = requireNotNull(word.startFields["start_time"])
        assertEquals(NvidiaRawJsonValueType.NUMBER, startTime.jsonType)
        assertEquals("1.25", startTime.rawText)
        assertEquals(1.25, startTime.parsedNumber!!, 0.0)

        val startMs = requireNotNull(word.startFields["start_ms"])
        assertEquals(NvidiaRawJsonValueType.STRING, startMs.jsonType)
        assertEquals("1250", startMs.rawText)
        assertEquals(1250.0, startMs.parsedNumber!!, 0.0)

        val nonNumeric = requireNotNull(word.endFields["end_time"])
        assertEquals(NvidiaRawJsonValueType.BOOLEAN, nonNumeric.jsonType)
        assertEquals("true", nonNumeric.rawText)
        assertNull(nonNumeric.parsedNumber)

        val explicitNull = requireNotNull(word.endFields["end_ms"])
        assertEquals(NvidiaRawJsonValueType.NULL, explicitNull.jsonType)
        assertEquals("null", explicitNull.rawText)
        assertNull(explicitNull.parsedNumber)
    }

    @Test fun multipleWordSchemasDoNotSilentlyMerge() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "words_info": {
                "words": [
                  {"word": "from-info", "start": 1.0, "end": 2.0}
                ]
              },
              "words": [
                {"word": "from-root", "start_ms": 1000, "end_ms": 2000}
              ]
            }
            """.trimIndent()
        )

        assertTrue(evidence.hasMultipleWordSchemas)
        assertEquals(
            setOf(NvidiaWordTimingSchema.WORDS_INFO_WORDS, NvidiaWordTimingSchema.ROOT_WORDS),
            evidence.presentSchemas,
        )
        assertEquals(2, evidence.sources.size)
        assertEquals("from-info", evidence.sources[0].words.single().text)
        assertEquals("from-root", evidence.sources[1].words.single().text)
        assertEquals("$.words_info.words", evidence.sources[0].schemaPath)
        assertEquals("$.words", evidence.sources[1].schemaPath)
    }

    @Test fun blankTextTimingItemKeepsOriginalIndexAndPath() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "words": [
                {"word": "", "start": 1.0, "end": 2.0},
                {"word": "visible", "start": 2.0, "end": 3.0}
              ]
            }
            """.trimIndent()
        )

        val words = evidence.sources.single().words
        assertEquals(2, words.size)
        assertNull(words[0].text)
        assertEquals(0, words[0].itemIndex)
        assertEquals("$.words[0]", words[0].itemPath)
        assertEquals("visible", words[1].text)
        assertEquals(1, words[1].itemIndex)
        assertEquals("$.words[1]", words[1].itemPath)
    }

    @Test fun allResultsAlternativesAreEnumeratedAsSeparateEvidenceSources() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "results": [
                {
                  "alternatives": [
                    {"words": [{"word": "first", "start": 1, "end": 2}]},
                    {"words": [{"word": "second", "start": 1.1, "end": 2.1}]}
                  ]
                }
              ]
            }
            """.trimIndent()
        )

        assertEquals(2, evidence.sources.size)
        assertEquals("$.results[0].alternatives[0].words", evidence.sources[0].schemaPath)
        assertEquals("first", evidence.sources[0].words.single().text)
        assertEquals("$.results[0].alternatives[1].words", evidence.sources[1].schemaPath)
        assertEquals("second", evidence.sources[1].words.single().text)
    }

    @Test fun emptyPresentSchemaStillRemainsVisibleAsEvidence() {
        val evidence = NvidiaSttTimingEvidenceInspector.inspect(
            """
            {
              "words_info": {"words": []},
              "words": [
                {"word": "root-only", "start": "3.5", "end": "4.0"}
              ]
            }
            """.trimIndent()
        )

        assertTrue(evidence.hasMultipleWordSchemas)
        assertEquals(2, evidence.sources.size)
        assertTrue(evidence.sources.first().words.isEmpty())
        val start = requireNotNull(evidence.sources.last().words.single().startFields["start"])
        assertEquals(NvidiaRawJsonValueType.STRING, start.jsonType)
        assertEquals("3.5", start.rawText)
        assertEquals(3.5, start.parsedNumber!!, 0.0)
    }
}
