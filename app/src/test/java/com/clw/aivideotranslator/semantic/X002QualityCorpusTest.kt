package com.clw.aivideotranslator.semantic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X002QualityCorpusTest {
    private fun corpus(): JSONObject {
        val stream = requireNotNull(javaClass.classLoader?.getResourceAsStream("translation_v1/quality_corpus.json"))
        return stream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
    }

    @Test fun n25SourceCorpusHasExactSamplingShapeWithoutReferenceAnswers() {
        val root = corpus()
        assertEquals(2, root.getInt("schemaVersion"))
        val cases = root.getJSONArray("cases")
        assertEquals(48, cases.length())

        val ids = mutableSetOf<String>()
        var adversarial = 0
        var integrity = 0
        val tagsSeen = mutableSetOf<String>()
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index)
            assertTrue(ids.add(item.getString("id")))
            assertTrue(item.getString("source").isNotBlank())
            assertTrue(!item.has("target") && !item.has("referenceArabic") && !item.has("score"))
            val tags = item.getJSONArray("tags")
            for (tagIndex in 0 until tags.length()) {
                val tag = tags.getString(tagIndex)
                tagsSeen += tag
                if (tag == "adversarial") adversarial++
                if (tag == "integrity") integrity++
            }
        }
        assertEquals(12, adversarial)
        assertEquals(12, integrity)
        listOf("technical", "business", "conversation", "pronoun", "negation", "number").forEach { required ->
            assertTrue("missing required X002 coverage: $required", required in tagsSeen)
        }
    }

    @Test fun adversarialAndIntegritySubsetsAreExplicitRatherThanInferredFromText() {
        val cases = corpus().getJSONArray("cases")
        val adversarialIds = mutableListOf<String>()
        val integrityIds = mutableListOf<String>()
        for (index in 0 until cases.length()) {
            val item = cases.getJSONObject(index)
            val tags = item.getJSONArray("tags")
            val set = (0 until tags.length()).map(tags::getString).toSet()
            if ("adversarial" in set) adversarialIds += item.getString("id")
            if ("integrity" in set) integrityIds += item.getString("id")
        }
        assertEquals(12, adversarialIds.size)
        assertEquals(12, integrityIds.size)
        assertTrue(adversarialIds.intersect(integrityIds.toSet()).isEmpty())
    }
}
