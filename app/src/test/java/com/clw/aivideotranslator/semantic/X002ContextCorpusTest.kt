package com.clw.aivideotranslator.semantic

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class X002ContextCorpusTest {
    private fun resource(path: String): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream(path))
        return stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    @Test fun boundedContextFixtureIsSourceOnlyAndBounded() {
        val corpus = resource("translation_v1/quality_corpus.json").getJSONArray("cases")
        val corpusById = (0 until corpus.length()).associate { index ->
            val item = corpus.getJSONObject(index)
            item.getString("id") to item.getString("source")
        }
        val corpusIds = corpusById.keys
        val root = resource("translation_v1/x002_context_windows.json")
        assertEquals(1, root.getInt("schemaVersion"))
        val cases = root.getJSONArray("cases")
        assertEquals(12, cases.length())

        val seen = mutableSetOf<String>()
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index)
            val id = item.getString("id")
            assertTrue(seen.add(id))
            assertTrue("unknown corpus case $id", id in corpusIds)
            val before = item.optJSONArray("before")
            val after = item.optJSONArray("after")
            assertTrue((before?.length() ?: 0) <= 1)
            assertTrue((after?.length() ?: 0) <= 1)
            assertTrue((before?.length() ?: 0) + (after?.length() ?: 0) > 0)
            listOfNotNull(before, after).forEach { side ->
                for (sideIndex in 0 until side.length()) {
                    assertTrue(side.getString(sideIndex).isNotBlank())
                }
            }
            listOf("target", "referenceArabic", "score", "legacyText", "semanticText").forEach { forbidden ->
                assertFalse("context fixture leaked $forbidden", item.has(forbidden))
            }

            val request = X002SemanticRequestPlanner.plan(
                target = X002SourceContextUnit("target:$id", requireNotNull(corpusById[id])),
                beforeContext = (0 until (before?.length() ?: 0)).map { sideIndex ->
                    X002SourceContextUnit("before:$id:$sideIndex", requireNotNull(before).getString(sideIndex))
                },
                afterContext = (0 until (after?.length() ?: 0)).map { sideIndex ->
                    X002SourceContextUnit("after:$id:$sideIndex", requireNotNull(after).getString(sideIndex))
                },
                mode = X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT,
            )
            assertEquals(X002SemanticRequestMode.BOUNDED_SOURCE_CONTEXT, request.mode)
            assertTrue(request.providerUserContent.contains(requireNotNull(corpusById[id])))
            assertTrue(request.providerUserContent.length <= X002SemanticRequestPlanner.MAX_PROVIDER_USER_CHARS)
            assertTrue(request.providerRequestSignature.isNotBlank())
            assertTrue(request.bindingSignature.isNotBlank())
        }
    }
}
