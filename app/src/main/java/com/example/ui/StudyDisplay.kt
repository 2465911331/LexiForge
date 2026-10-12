package com.example.ui

import com.example.data.ReviewScheduler
import com.example.data.WordFormula

enum class WordListFilter(val label: String) {
    ALL("全部"), NEW("未学"), DUE("待复习"), LEARNED("已练习")
}

fun WordFormula.isDueAt(now: Long) = ReviewScheduler.isDue(lastReviewedAt, nextReviewAt, now)

fun filterStudyWords(words: List<WordFormula>, filter: WordListFilter, query: String, now: Long): List<WordFormula> {
    val search = query.trim()
    return words.filter { word ->
        val matches = when (filter) {
            WordListFilter.ALL -> true
            WordListFilter.NEW -> word.lastReviewedAt == 0L
            WordListFilter.DUE -> word.isDueAt(now)
            WordListFilter.LEARNED -> word.lastReviewedAt > 0L
        }
        matches && (search.isEmpty() || word.spell.contains(search, true) || word.chineseMeaning.contains(search, true))
    }.let { if (filter == WordListFilter.DUE) it.sortedBy { word -> word.nextReviewAt } else it }
}

fun wordStudyStatus(word: WordFormula, now: Long): String = when {
    word.lastReviewedAt == 0L -> "未学"
    word.isDueAt(now) -> "待复习"
    word.ebbinghausStage == 0 -> "待重练"
    else -> "已练习"
}

fun wordReviewDescription(word: WordFormula, now: Long): String = when {
    word.lastReviewedAt == 0L -> "完成首次练习后安排复习"
    word.isDueAt(now) -> "已到复习时间"
    else -> {
        val minutes = ((word.nextReviewAt - now + 59_999) / 60_000).coerceAtLeast(1)
        when {
            minutes < 60 -> "$minutes 分钟后复习"
            minutes < 1440 -> "${(minutes + 59) / 60} 小时内复习"
            else -> "${(minutes + 1439) / 1440} 天内复习"
        }
    }
}

val availableWordbooks = linkedMapOf(
    "built-in" to "内置体验词书",
    "3-CET4-顺序" to "大学英语四级",
    "4-CET6-顺序" to "大学英语六级",
    "5-考研-顺序" to "考研英语",
    "6-托福-顺序" to "托福词汇",
    "7-SAT-顺序" to "SAT 词汇",
    "2-高中-顺序" to "高中英语",
    "1-初中-顺序" to "初中英语"
)

fun wordbookLabel(id: String) = availableWordbooks[id] ?: id

fun hasTeachingExample(word: WordFormula) = word.sentenceEn.isNotBlank() &&
    !word.sentenceEn.startsWith("An essential vocabulary word to remember:", ignoreCase = true)
