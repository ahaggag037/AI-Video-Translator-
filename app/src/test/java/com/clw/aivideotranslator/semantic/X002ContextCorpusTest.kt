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
        val corpusIds = (0 until corpus.length()).map { corpus.getJSONObject(it).getString("id") }.toSet()
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
        }
    }
}
