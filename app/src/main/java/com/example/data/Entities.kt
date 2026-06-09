package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "affix_components")
data class AffixComponent(
    @PrimaryKey val id: String,
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
    val nextReviewAt: Long = 0L
)

@Entity(tableName = "word_formulas")
data class WordFormula(
    @PrimaryKey val id: String, // e.g. "attract"
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
    val frequencyGroup: String = "高频词"
)
