package com.example.data

import com.squareup.moshi.Moshi
import java.io.InputStream
import java.util.Locale

data class ImportedWordbook(val words: List<WordFormula>, val components: List<AffixComponent>)

/** Supports the upstream legacy JSON array and simple/sentence/full JSONL records. */
object WordbookImporter {
    const val MAX_DOWNLOAD_BYTES = 24 * 1024 * 1024
    private val adapter = Moshi.Builder().build().adapter(Any::class.java)

    fun read(input: InputStream): String {
        val bytes = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(bytes.size() + count <= MAX_DOWNLOAD_BYTES) { "词书超过当前导入容量" }
            bytes.write(buffer, 0, count)
        }
        return bytes.toString("UTF-8")
    }

    fun parse(text: String, bookId: String): ImportedWordbook {
        require(bookId in OfflineMorphology.supportedBooks && bookId != "built-in") { "不支持此词书来源" }
        val source = text.trim().removePrefix("\uFEFF").trimStart()
        val records = if (source.startsWith("[")) {
            (adapter.fromJson(source) as? List<*>) ?: error("词书格式无效")
        } else source.lineSequence().filter { it.isNotBlank() }.map { adapter.fromJson(it) }.toList()
        require(records.isNotEmpty() && records.size <= 20000) { "词书为空或条目过多" }
        val words = records.map { record ->
            val item = record as? Map<*, *> ?: error("词条格式无效")
            val fullWord = item.map("content").map("word")
            val full = fullWord.map("content")
            val spelling = (item.string("word").ifBlank { item.string("headWord") }).trim()
            require(spelling.isNotBlank()) { "词书包含空单词，未替换当前词书" }
            val translations = (item["translations"] ?: full["trans"]) as? List<*> ?: emptyList<Any>()
            val meaning = translations.map { value ->
                val t = value as? Map<*, *> ?: error("释义格式无效")
                val definition = t.string("translation").ifBlank { t.string("tranCn") }
                require(definition.isNotBlank()) { "词书包含空释义" }
                val pos = t.string("type").ifBlank { t.string("pos") }.trim().trimEnd('.')
                if (pos.isBlank()) definition else "$pos. $definition"
            }.distinct().joinToString("; ")
            require(meaning.isNotBlank()) { "词书包含无释义的单词" }
            val sentences = (item["sentences"] ?: full.map("sentence")["sentences"]) as? List<*>
            val first = sentences?.firstOrNull() as? Map<*, *>
            val sentence = first?.string("sContent")?.ifBlank { first.string("sentence") }.orEmpty()
            val translation = first?.string("sCn")?.ifBlank { first.string("translation") }.orEmpty()
            val word = WordFormula(
                id = fullWord.string("wordId").ifBlank {
                    if (spelling == spelling.lowercase(Locale.ROOT)) spelling else "case:$spelling"
                }, spell = spelling,
                prefixId = null, rootId = null, suffixId = null,
                prefixPart = null, rootPart = null, suffixPart = null,
                literalFormula = "", chineseMeaning = meaning,
                sentenceEn = sentence, sentenceZh = translation, frequencyGroup = "未分级")
            word
        }
        val merged = words.groupBy { it.id }.values.map { duplicates ->
            require(duplicates.map { it.spell }.distinct().size == 1) { "词书存在指向不同单词的重复标识" }
            val first = duplicates.first()
            val example = duplicates.firstOrNull { it.sentenceEn.isNotBlank() }
            first.copy(
                chineseMeaning = duplicates.flatMap { it.chineseMeaning.split("; ") }.distinct().joinToString("; "),
                sentenceEn = example?.sentenceEn.orEmpty(), sentenceZh = example?.sentenceZh.orEmpty())
        }
        val lexicon = MorphologyRules.Lexicon(merged)
        val analyzed = merged.map { OfflineMorphology.apply(it, bookId, lexicon) }
        val components = OfflineMorphology.componentsFor(analyzed)
        BackupFormat.validate(BackupData(bookId, analyzed, components, BackupPreferences("SYSTEM", true, false, false, "system")))
        return ImportedWordbook(analyzed, components)
    }

    private fun Map<*, *>.map(key: String) = this[key] as? Map<*, *> ?: emptyMap<Any, Any>()
    private fun Map<*, *>.string(key: String) = this[key] as? String ?: ""
}
