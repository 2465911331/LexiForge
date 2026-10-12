package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "affix_components", primaryKeys = ["bookId", "id"])
data class AffixComponent(
    val id: String,
    val name: String,
    val type: String, // "PREFIX", "ROOT", "SUFFIX"
    val icon: String, // Emoji representation
    val literalMeaning: String,
    val anchorWord: String,
    val anchorDesc: String,
    val isMastered: Boolean = false,
    val reviewCount: Int = 0,
    val lastReviewedAt: Long = 0L,
    val ebbinghausStage: Int = 0,
    val nextReviewAt: Long = 0L,
    val bookId: String = "built-in"
)

@Entity(tableName = "word_formulas", primaryKeys = ["bookId", "id"])
data class WordFormula(
    val id: String, // stable within its source book
    val spell: String,
    val prefixId: String?,
    val rootId: String?,
    val suffixId: String?,
    val prefixPart: String?,  // e.g. "ad- (同化为at-)"
    val rootPart: String?,    // e.g. "tract"
    val suffixPart: String?,  // e.g. "-ive" etc.
    val literalFormula: String, // e.g. "at-(向) + tract(拉) = 往自己这拉 -> 吸引"
    val chineseMeaning: String,
    val sentenceEn: String,
    val sentenceZh: String,
    val isAssembled: Boolean = false,
    val lastReviewedAt: Long = 0L,
    val ebbinghausStage: Int = 0,
    val nextReviewAt: Long = 0L,
    val frequencyGroup: String = "未分级",
    val bookId: String = "built-in"
)

@Entity(tableName = "wordbooks")
data class Wordbook(@PrimaryKey val id: String, val lastWordId: String? = null,
    @androidx.room.ColumnInfo(defaultValue = "20") val dailyNewTarget: Int = 20)

@Entity(tableName = "library_state")
data class LibraryState(@PrimaryKey val id: Int = 0, val activeBookId: String = "built-in", val historyStartedAt: Long = 0)

data class BookOverview(val id: String, val lastWordId: String?, val wordCount: Int, val learnedCount: Int, val dailyNewTarget: Int = 20)

/** Append-only submitted answers. A reset changes progress, not the historical facts. */
@Entity(tableName = "review_events", indices = [androidx.room.Index(value = ["bookId", "sequence"]), androidx.room.Index(value = ["sequence"], unique = true)])
data class ReviewEvent(
    @PrimaryKey val id: String,
    val sessionId: String,
    val bookId: String,
    val targetId: String,
    val targetLabel: String,
    val targetKind: String,
    val mode: String,
    val prompt: String,
    val answer: String,
    val correct: Boolean,
    val hints: String,
    val attempt: Int,
    val answeredAt: Long,
    val elapsedMs: Long,
    val stageBefore: Int,
    val stageAfter: Int,
    val scheduleChanged: Boolean,
    val retryOf: String? = null,
    val schedulerVersion: String = "fixed-v1",
    val sequence: Long = 0,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val firstLearning: Boolean? = null,
    @androidx.room.ColumnInfo(defaultValue = "NULL") val dueReview: Boolean? = null
)

data class AnswerRequest(
    val id: String, val sessionId: String, val bookId: String, val targetId: String,
    val targetKind: String, val mode: String, val answer: String, val correct: Boolean,
    val hints: String, val attempt: Int, val answeredAt: Long, val elapsedMs: Long
)

@Entity(tableName = "content_corrections", primaryKeys = ["bookId", "wordId"])
data class ContentCorrection(val bookId: String, val wordId: String, val meaning: String,
    val sentenceEn: String, val sentenceZh: String, val note: String,
    val hideAnalysis: Boolean, val updatedAt: Long)
