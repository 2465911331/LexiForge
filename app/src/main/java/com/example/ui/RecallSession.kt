package com.example.ui

import com.example.data.WordFormula

data class RecallResult(val answer: String, val correct: Boolean)
data class RecallSession(val id: String, val words: List<WordFormula>, val errorsOnly: Boolean,
    val generation: Long, val index: Int = 0, val correctCount: Int = 0,
    val result: RecallResult? = null, val finished: Boolean = false,
    val mode: String = if (errorsOnly) "ERROR_RECALL" else "RECALL") {
    val title: String get() = when (mode) {
        "ERROR_RECALL" -> "错词独立检验"
        "PERSONAL_RECALL" -> "个性化复习"
        "FAMILY_RECALL" -> "词族独立检验"
        "EXAM_RECALL" -> "考试词汇回忆"
        "EXAM_CLOZE" -> "考试语境填空"
        else -> "无提示检验"
    }
}
