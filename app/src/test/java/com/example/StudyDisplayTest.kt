package com.example

import com.example.data.BuiltInWordbook
import com.example.ui.*
import org.junit.Assert.*
import org.junit.Test

class StudyDisplayTest {
    private val now = 1_800_000_000_000L

    @Test fun `due includes forgotten and legacy words while new excludes all attempted words`() {
        val base = BuiltInWordbook.words.first()
        val fresh = base.copy(id = "new", lastReviewedAt = 0, nextReviewAt = 0)
        val forgotten = base.copy(id = "forgotten", isAssembled = false, lastReviewedAt = now - 500, nextReviewAt = now, ebbinghausStage = 0)
        val future = base.copy(id = "future", lastReviewedAt = now - 500, nextReviewAt = now + 1)
        val legacy = base.copy(id = "legacy", lastReviewedAt = now - 500, nextReviewAt = 0)
        val words = listOf(fresh, forgotten, future, legacy)
        assertEquals(listOf("new"), filterStudyWords(words, WordListFilter.NEW, "", now).map { it.id })
        assertEquals(listOf("legacy", "forgotten"), filterStudyWords(words, WordListFilter.DUE, "", now).map { it.id })
        assertEquals(3, filterStudyWords(words, WordListFilter.LEARNED, "", now).size)
        assertEquals("待复习", wordStudyStatus(forgotten, now))
        assertEquals("1 分钟后复习", wordReviewDescription(future, now))
    }

    @Test fun `search trims input and placeholders are not teaching examples`() {
        val word = BuiltInWordbook.words.first()
        assertEquals(listOf(word), filterStudyWords(listOf(word), WordListFilter.ALL, "  ${word.spell.uppercase()}  ", now))
        assertFalse(hasTeachingExample(word.copy(sentenceEn = "An essential vocabulary word to remember: ${word.spell}.")))
        assertTrue(hasTeachingExample(word))
    }
}
