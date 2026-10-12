package com.example.data

import java.util.Locale

/** Curated records take priority over bounded, dictionary-backed spelling rules. */
object OfflineMorphology {
    const val VERSION = SharedLearningPack.VERSION
    const val WHOLE_WORD_NOTE = "暂无适用的构词或拼写分块资料，本词采用整词拼写；短词和不可可靠拆解的词无需强拆。"
    val supportedBooks = setOf("built-in", "3-CET4-顺序", "4-CET6-顺序", "5-考研-顺序",
        "6-托福-顺序", "7-SAT-顺序", "2-高中-顺序", "1-初中-顺序")

    data class Entry(val template: WordFormula, val meanings: List<String>, val source: String, val ruleDerived: Boolean = false,
                     val blocks: List<String> = emptyList(), val kind: SharedLearningPack.Kind? = null) {
        val assembly: Boolean get() = blocks.size >= 2 || template.prefixId != null || template.suffixId != null
    }

    private val meanings = mapOf(
        "attract" to listOf("吸引"), "import" to listOf("进口", "引入", "输入"),
        "export" to listOf("出口", "输出"), "reject" to listOf("拒绝", "抛弃"),
        "project" to listOf("项目", "计划", "投射"), "predict" to listOf("预测", "预言"),
        "describe" to listOf("描述", "描写", "形容"), "commit" to listOf("承诺", "投入", "犯", "交付"),
        "submit" to listOf("提交", "屈服", "呈送"), "extend" to listOf("延长", "扩展", "伸出", "扩大"),
        "visible" to listOf("可见", "看得见"), "inspect" to listOf("检查", "视察"),
        "express" to listOf("表达", "表示"), "structure" to listOf("结构", "构造"),
        "sensitive" to listOf("敏感", "体贴"), "definition" to listOf("定义", "释义"),
        "transfer" to listOf("转移", "调动", "转账"), "transport" to listOf("运输", "运送")
    )
    val entries: List<Entry> = BuiltInWordbook.words.map {
        Entry(it, meanings.getValue(it.spell), "https://www.etymonline.com/word/${it.spell}")
    }
    private val bySpelling = entries.associateBy { it.template.spell }
    private val components = BuiltInWordbook.components.associateBy { it.id }

    fun lookup(word: WordFormula): Entry? {
        // Uppercase proper names and unrecognised senses are intentionally not matched.
        val spelling = word.spell.trim()
        if (spelling != spelling.lowercase(Locale.ROOT)) return null
        val entry = bySpelling[spelling] ?: return null
        return entry.takeIf { it.meanings.any(word.chineseMeaning::contains) }
    }

    fun displayEntry(word: WordFormula): Entry? = lookup(word)?.takeIf {
        val t = it.template
        word.prefixId == t.prefixId && word.rootId == t.rootId && word.suffixId == t.suffixId &&
            word.prefixPart == t.prefixPart && word.rootPart == t.rootPart && word.suffixPart == t.suffixPart &&
            word.literalFormula == t.literalFormula
    } ?: MorphologyRules.displayAnalysis(word)?.let {
        Entry(it.template(word), emptyList(), MorphologyRules.SOURCE, ruleDerived = true)
    } ?: SharedLearningPack.displayAnalysis(word)?.let {
        Entry(it.template(word), emptyList(), it.source, blocks = it.blocks, kind = it.kind)
    }

    fun canAssemble(word: WordFormula) = displayEntry(word)?.assembly == true

    fun apply(word: WordFormula, bookId: String, lexicon: MorphologyRules.Lexicon? = null): WordFormula {
        val template = if (bookId in supportedBooks) lookup(word)?.template ?: run {
            // Curated spellings with an unrecognised sense stay excluded from rule fallback.
            if (word.spell in bySpelling) null else {
                // Preserve the existing checked rule path; use the shared pack when its base is absent.
                lexicon?.let { MorphologyRules.analyze(word, it)?.template(word) }
                    ?: SharedLearningPack.construction(word)?.template(word)
                    ?: SharedLearningPack.spelling(word)?.template(word)
            }
        } else null
        val placeholder = word.sentenceEn.startsWith("An essential vocabulary word to remember:", ignoreCase = true)
        return word.copy(
            prefixId = template?.prefixId, rootId = template?.rootId, suffixId = template?.suffixId,
            prefixPart = template?.prefixPart, rootPart = template?.rootPart, suffixPart = template?.suffixPart,
            literalFormula = template?.literalFormula ?: WHOLE_WORD_NOTE,
            sentenceEn = if (placeholder) "" else word.sentenceEn,
            sentenceZh = if (placeholder) "" else word.sentenceZh,
            frequencyGroup = "未分级"
        )
    }

    fun isTrustedComponent(component: AffixComponent): Boolean {
        val template = components[component.id] ?: MorphologyRules.component(component.id) ?: return false
        return component.name == template.name && component.type == template.type &&
            component.literalMeaning == template.literalMeaning && component.anchorDesc == template.anchorDesc
    }

    fun componentsFor(words: List<WordFormula>): List<AffixComponent> = words
        .flatMap { listOfNotNull(it.prefixId, it.rootId, it.suffixId) }.distinct()
        .mapNotNull { components[it] ?: MorphologyRules.component(it) }

    fun prepare(data: BackupData): BackupData {
        val lexicon = MorphologyRules.Lexicon(data.words)
        val words = data.words.map { apply(it, data.bookId, lexicon) }
        val required = words.flatMap { listOfNotNull(it.prefixId, it.rootId, it.suffixId) }.toSet()
        val old = data.components.associateBy { it.id }
        // Keep unreviewed legacy rows and their progress for backups; they are excluded from training.
        val updated = old.toMutableMap()
        (components + componentsFor(words).associateBy { it.id }).forEach { (id, template) ->
            if (id in old || id in required) {
                val previous = old[id]
                updated[id] = if (previous == null) template else template.copy(
                    isMastered = previous.isMastered, reviewCount = previous.reviewCount,
                    lastReviewedAt = previous.lastReviewedAt, ebbinghausStage = previous.ebbinghausStage,
                    nextReviewAt = previous.nextReviewAt)
            }
        }
        return data.copy(words = words.sortedBy { it.id }, components = updated.values.sortedBy { it.id },
            events = data.events.sortedBy { it.sequence },
            dailyTargets = data.dailyTargets.filterValues { it != 20 },
            corrections = data.corrections.sortedWith(compareBy<ContentCorrection> { it.bookId }.thenBy { it.wordId }),
            otherBooks = data.otherBooks.map { book ->
                val prepared = prepare(BackupData(book.id, book.words, book.components, data.preferences))
                book.copy(words = prepared.words, components = prepared.components)
            }.sortedBy { it.id })
    }

    fun validatePack() {
        require(entries.map { it.template.spell }.distinct().size == entries.size)
        entries.forEach { entry ->
            val t = entry.template
            if (entry.assembly) {
                val joined = listOfNotNull(t.prefixPart, t.rootPart, t.suffixPart).joinToString("") { it.trim('-') }
                require(joined == t.spell) { "Local segmentation does not reconstruct ${t.spell}" }
            }
            listOf(t.prefixId to "PREFIX", t.rootId to "ROOT", t.suffixId to "SUFFIX").forEach { (id, type) ->
                require(id == null || components[id]?.type == type)
            }
            require(entry.meanings.isNotEmpty() && entry.source.startsWith("https://www.etymonline.com/word/"))
        }
    }
}
