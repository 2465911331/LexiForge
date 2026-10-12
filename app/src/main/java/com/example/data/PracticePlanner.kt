package com.example.data

data class ReviewCandidate(val word: WordFormula, val reason: String, val priority: Int, val failures: Int, val lastSequence: Long)

/** Explainable local selection; the existing fixed interval scheduler remains unchanged. */
object PracticePlanner {
    val newModes = setOf("PERSONAL_RECALL", "FAMILY_RECALL", "EXAM_RECALL", "EXAM_CLOZE")
    fun personal(words: List<WordFormula>, events: List<ReviewEvent>, now: Long, mistakesOnly: Boolean = false): List<ReviewCandidate> {
        val mistakes = RecallTools.mistakes(words, events).associateBy { it.word.bookId to it.word.id }
        val history = events.filter { it.targetKind == "WORD" && it.attempt == 1 }
            .groupBy { it.bookId to it.targetId }
        return words.filter(RecallTools::canTest).mapNotNull { w ->
            val key = w.bookId to w.id
            val pending = mistakes[key]
            if (mistakesOnly && pending == null) return@mapNotNull null
            val recent = history[key].orEmpty().sortedByDescending { it.sequence }.take(10)
            val failures = recent.count { !it.correct }
            val independent = recent.any { it.correct && it.mode in RecallTools.modes && it.hints.isEmpty() }
            val due = ReviewScheduler.isDue(w.lastReviewedAt, w.nextReviewAt, now)
            val priority = when { due -> 0; pending != null -> 1; failures > 0 -> 2; !independent -> 3; else -> 4 }
            ReviewCandidate(w, when (priority) {
                0 -> if (pending != null) "已到期 · 仍有错词记录" else "已到期复习"
                1 -> "待独立检验的错词"
                2 -> "最近 ${recent.size} 次首次作答中错 $failures 次"
                3 -> "尚无近期独立答对记录"
                else -> "已学词抽查"
            }, priority, failures, recent.firstOrNull()?.sequence ?: 0L)
        }.sortedWith(compareBy<ReviewCandidate> { it.priority }.thenByDescending { it.failures }
            .thenBy { if (it.priority == 0) it.word.nextReviewAt else it.lastSequence }.thenBy { it.word.id })
    }

    fun examBook(bookId: String): Boolean = bookId in setOf("3-CET4-顺序", "4-CET6-顺序")

    // Use only a literal whole-word occurrence from an actual example. Never fabricate a context.
    fun cloze(word: WordFormula): String? {
        if (!Regex("[a-zA-Z]+(?:[-'][a-zA-Z]+)*").matches(word.spell)) return null
        val sentence = word.sentenceEn.trim()
        if (sentence.length !in 15..1000 || sentence.contains("____") || sentence.contains('\n')) return null
        if (sentence.contains("An essential vocabulary word to remember", true) ||
            sentence.contains("暂无") || sentence.contains("no example", true)) return null
        val target = Regex("(?i)(?<![\\p{L}\\p{N}_'’\\-])${Regex.escape(word.spell)}(?![\\p{L}\\p{N}_'’\\-])")
        if (target.findAll(sentence).count() != 1) return null
        val masked = target.replace(sentence, "____")
        // Substrings elsewhere (such as hopeful for hope) would reveal the answer.
        if (masked.contains(word.spell, ignoreCase = true)) return null
        if (Regex("[A-Za-z]+(?:'[A-Za-z]+)*").findAll(masked).count() < 3) return null
        return masked
    }

    fun exam(words: List<WordFormula>, events: List<ReviewEvent>, bookId: String, cloze: Boolean): List<WordFormula> {
        if (!examBook(bookId)) return emptyList()
        val mode = if (cloze) "EXAM_CLOZE" else "EXAM_RECALL"
        val last = events.filter { it.bookId == bookId && it.targetKind == "WORD" && it.mode == mode }
            .groupBy { it.targetId }.mapValues { (_, attempts) -> attempts.maxOf { it.sequence } }
        return words.filter { it.bookId == bookId && RecallTools.canTest(it) && (!cloze || PracticePlanner.cloze(it) != null) }
            .sortedWith(compareBy<WordFormula> { last[it.id] ?: 0L }.thenBy { it.id })
    }
}
