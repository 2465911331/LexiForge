package com.example

import com.example.data.BuiltInWordbook
import org.junit.Assert.*
import org.junit.Test

class BuiltInWordbookTest {
    @Test fun `every assembly spells the whole word and references the correct component type`() {
        val components = BuiltInWordbook.components.associateBy { it.id }
        BuiltInWordbook.words.forEach { word ->
            listOf(word.prefixId to "PREFIX", word.rootId to "ROOT", word.suffixId to "SUFFIX").forEach { (id, type) ->
                if (id != null) assertEquals("${word.id}: $id", type, components[id]?.type)
            }
            val spelled = listOfNotNull(word.prefixPart, word.rootPart, word.suffixPart).joinToString("") { it.trim('-') }
            assertEquals(word.id, word.spell, spelled)
            assertTrue(word.id, word.sentenceEn.contains(word.spell))
            assertTrue(word.id, word.sentenceZh.isNotBlank())
        }
    }

    @Test fun `all anchors refer to retained words and no invented variants are included`() {
        val ids = BuiltInWordbook.words.map { it.id }
        assertEquals(18, ids.size)
        assertEquals(ids.size, ids.distinct().size)
        BuiltInWordbook.components.forEach { assertTrue(ids.contains(it.anchorWord.substringBefore(" "))) }
        assertFalse(BuiltInWordbook.words.any { it.spell in setOf("commited", "commiting", "attractly", "visibled") })
        assertEquals("suff_ure", BuiltInWordbook.words.first { it.id == "structure" }.suffixId)
        assertNull(BuiltInWordbook.words.first { it.id == "sensitive" }.suffixId)
    }
}
