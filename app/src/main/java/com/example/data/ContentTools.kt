package com.example.data

object ContentTools {
    fun validate(c: ContentCorrection) {
        require(c.meaning.isNotBlank() && c.meaning.length <= 2000) { "释义不能为空，最多 2000 字" }
        require(c.sentenceEn.length <= 4000 && c.sentenceZh.length <= 4000 && c.note.length <= 2000) { "例句或纠错说明过长" }
        require(c.updatedAt in 1..253402300799999L) { "纠错时间无效" }
    }
    fun apply(word: WordFormula, correction: ContentCorrection?): WordFormula {
        val c = correction?.takeIf { it.bookId == word.bookId && it.wordId == word.id } ?: return word
        val changed = word.copy(chineseMeaning = c.meaning, sentenceEn = c.sentenceEn, sentenceZh = c.sentenceZh)
        return if (c.hideAnalysis || c.meaning != word.chineseMeaning) changed.copy(prefixId = null, rootId = null,
            suffixId = null, prefixPart = null, rootPart = null, suffixPart = null,
            literalFormula = "个人修订：此词使用整词训练，原始拆解已停用。") else changed
    }
    fun applyAll(words: List<WordFormula>, corrections: List<ContentCorrection>): List<WordFormula> {
        val byId = corrections.associateBy { it.bookId to it.wordId }
        return words.map { apply(it, byId[it.bookId to it.id]) }
    }
}

data class MistakeWord(val word: WordFormula, val errors: Int, val lastWrongAt: Long)

object RecallTools {
    val modes = setOf("RECALL", "ERROR_RECALL", "PERSONAL_RECALL", "FAMILY_RECALL", "EXAM_RECALL")
    const val RETEST_DELAY = 300000L
    // English glosses can contain the tested spelling; remove Latin text from the question entirely.
    fun prompt(meaning: String): String = java.text.Normalizer.normalize(meaning, java.text.Normalizer.Form.NFKC).replace(Regex("[A-Za-z]+(?:['’][A-Za-z]+)*"), "")
        .replace(Regex("[ .;:：；()（）/]+"), " ").trim()
    fun canTest(word: WordFormula) = word.lastReviewedAt > 0 && Regex("[\u3400-\u4DBF\u4E00-\u9FFF]").containsMatchIn(prompt(word.chineseMeaning))
    fun mistakes(words: List<WordFormula>, events: List<ReviewEvent>): List<MistakeWord> {
        val index = words.associateBy { it.bookId to it.id }
        val grouped = events.filter { it.targetKind == "WORD" }.groupBy { it.bookId to it.targetId }
        return grouped.mapNotNull { (key, history) ->
            val word = index[key] ?: return@mapNotNull null
            var wrong: ReviewEvent? = null
            var count = 0
            history.sortedBy { it.sequence }.forEach { e ->
                if (!e.correct) { wrong = e; count++ }
                else if (e.mode in modes && e.hints.isEmpty() && e.attempt == 1 &&
                    wrong?.let { e.answeredAt >= it.answeredAt + RETEST_DELAY } == true) wrong = null
            }
            wrong?.let { MistakeWord(word, count, it.answeredAt) }
        }.sortedWith(compareByDescending<MistakeWord> { it.errors }.thenByDescending { it.lastWrongAt }.thenBy { it.word.id })
    }
}
