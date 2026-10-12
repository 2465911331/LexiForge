package com.example.data

data class WordFamily(val key: String, val label: String, val evidence: String, val words: List<WordFormula>)

object WordFamilies {
    fun build(words: List<WordFormula>): List<WordFamily> {
        val groups = linkedMapOf<String, MutableList<WordFormula>>()
        val labels = mutableMapOf<String, String>()
        val notes = mutableMapOf<String, MutableSet<String>>()
        for (w in words) {
            val entry = OfflineMorphology.displayEntry(w) ?: continue
            val rule = MorphologyRules.displayAnalysis(w)
            val shared = SharedLearningPack.familyBase(w)
            val root = if (entry.kind == null && !entry.ruleDerived) w.rootId else null
            val key = when { rule != null -> "base:${rule.base}"; shared != null -> "base:$shared"; root != null -> "root:$root"; else -> continue }
            labels[key] = if (key.startsWith("base:")) "词基 ${key.substringAfter(':')}" else "词根 ${w.rootPart.orEmpty().substringBefore(' ')}"
            groups.getOrPut(key) { mutableListOf() } += w
            notes.getOrPut(key) { linkedSetOf() } += when { rule != null -> "本地规则推导"; shared != null -> "MorphyNet 构词路径"; else -> "本地示范词根" }
        }
        return groups.mapNotNull { (key, members) ->
            val bases = if (key.startsWith("base:")) words.filter { it.spell == key.substringAfter(':') && !it.literalFormula.startsWith("个人修订：") } else emptyList()
            val all = (members + bases).distinctBy { it.bookId to it.id }.sortedBy { it.spell }
            if (all.size < 2) null else WordFamily(key, labels.getValue(key), notes.getValue(key).joinToString("、"), all)
        }.sortedBy { it.label }
    }
}
