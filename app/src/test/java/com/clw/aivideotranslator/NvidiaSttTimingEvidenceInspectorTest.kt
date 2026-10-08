package com.clw.aivideotranslator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NvidiaSttTimingEvidenceInspectorTest {
    @Test fun rawTimingEvidencePreservesSchemaPathAndFieldName() {
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
                          "start_ms": 1250,
                          "end_time": 2.5,
                          "end_ms": 2500
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
        assertEquals("hello", word.text)
        assertEquals(mapOf("start_time" to 1.25, "start_ms" to 1250.0), word.startFields)
        assertEquals(mapOf("end_time" to 2.5, "end_ms" to 2500.0), word.endFields)
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
        assertEquals(mapOf("start" to 3.5), evidence.sources.last().words.single().startFields)
    }
}
