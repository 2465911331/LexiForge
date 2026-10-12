package com.example.data

import java.util.zip.GZIPInputStream

/** Shared lexical evidence, independent of the user's current book and learning state. */
object SharedLearningPack {
    const val VERSION = "2026.10.02.2"
    enum class Kind(val label: String) {
        MORPHOLOGY("构词资料分块"), COMPOUND("复合词分块"), SPELLING("拼写分块")
    }
    data class Analysis(val kind: Kind, val blocks: List<String>, val detail: String, val source: String) {
        val formula get() = "${kind.label}：${blocks.joinToString(" + ")}。$detail"
        fun template(word: WordFormula) = word.copy(prefixId = null, rootId = null, suffixId = null,
            prefixPart = null, rootPart = null, suffixPart = null, literalFormula = formula)
    }

    // Keep rows compact; parse only the requested word, not all object graphs on startup.
    private fun load(name: String): Map<String, String> {
        val stream = requireNotNull(javaClass.getResourceAsStream("/learning/$name.tsv.gz")) { "Missing offline pack: $name" }
        val result = HashMap<String, String>()
        GZIPInputStream(stream).bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                val split = line.indexOf('\t')
                require(split > 0)
                val key = line.substring(0, split)
                val row = line.substring(split + 1)
                result[key] = result[key]?.let { "$it\n$row" } ?: row
            }
        }
        return result
    }
    private val morphology by lazy { load("morphynet") }
    private val spelling by lazy { load("spelling") }
    // Explicit modern compounds only. No arbitrary dictionary substring matching.
    internal val compounds = listOf(
        "air|port", "air|craft", "air|line", "air|mail", "bed|room", "bath|room", "class|room",
        "black|board", "white|board", "book|case", "book|shop", "book|store", "book|mark",
        "day|light", "day|time", "earth|quake", "ear|phone", "eye|sight", "eye|brow",
        "fire|fighter", "fire|wood", "fire|place", "foot|ball", "foot|print", "foot|step",
        "hand|bag", "hand|book", "hand|writing", "head|ache", "head|phone", "home|work",
        "house|work", "life|time", "moon|light", "news|paper", "note|book", "pass|port",
        "post|card", "rain|coat", "rain|fall", "rain|bow", "rail|way", "rail|road",
        "school|bag", "sea|food", "sea|side", "sea|water", "snow|man", "snow|fall",
        "sun|light", "sun|rise", "sun|set", "sun|shine", "text|book", "tooth|brush",
        "tooth|paste", "tooth|ache", "week|end", "work|place", "work|shop"
    ).map { it.split('|') }.associateBy { it.joinToString("") }

    fun construction(word: WordFormula): Analysis? {
        if (!Regex("[a-z]{3,40}").matches(word.spell)) return null
        val pos = MorphologyRules.partsOfSpeech(word.chineseMeaning)
        compounds[word.spell]?.takeIf { "n" in pos }?.let {
            return Analysis(Kind.COMPOUND, it, "LexiForge 本地复合词表；按现代组成词分块，整体词义以词书释义为准。",
                "https://github.com/2465911331/LexiForge")
        }
        val rows = morphology[word.spell]?.lines()?.map { it.split('\t') }
            ?.filter { it.size == 3 && it[0] in pos } ?: return null
        if (rows.isEmpty() || rows.map { it[1] }.distinct().size != 1) return null
        val row = rows.first()
        val parts = row[1].split('|')
        require(parts.size in 2..6 && parts.all { it.isNotEmpty() } && parts.joinToString("") == word.spell)
        return Analysis(Kind.MORPHOLOGY, parts, "词族路径：${row[2]}。MorphyNet 资料推导，未经逐词核对。",
            "https://github.com/kbatsuren/MorphyNet")
    }

    fun spelling(word: WordFormula): Analysis? {
        val fields = spelling[word.spell]?.split('\t') ?: return null
        require(fields.size == 3)
        val parts = fields[0].split('|')
        require(parts.size in 2..6 && parts.all { it.isNotEmpty() } && parts.joinToString("") == word.spell)
        val stress = fields[2].takeIf { it.isNotBlank() }?.let { "，主重音在第 $it 个音节" }.orEmpty()
        return Analysis(Kind.SPELLING, parts,
            "按 Moby 排版断词位置辅助记字母，不是词根或音节边界。CMUdict 美式参考读音：${fields[1]} 个音节$stress（词典首个读音）。音节与字母块不逐块对应，播放读音以所选语音引擎为准。",
            "https://www.gutenberg.org/ebooks/3204")
    }

    fun familyBase(word: WordFormula): String? {
        if (displayAnalysis(word)?.kind != Kind.MORPHOLOGY) return null
        val pos = MorphologyRules.partsOfSpeech(word.chineseMeaning)
        val bases = morphology[word.spell]?.lines()?.map { it.split('\t') }
            ?.filter { it.size == 3 && it[0] in pos }?.map { it[2].substringBefore(" → ") }?.distinct().orEmpty()
        return bases.singleOrNull()?.takeIf { Regex("[a-z]{2,40}").matches(it) }
    }

    fun displayAnalysis(word: WordFormula): Analysis? {
        if (word.prefixId != null || word.rootId != null || word.suffixId != null ||
            word.prefixPart != null || word.rootPart != null || word.suffixPart != null) return null
        // Only trust exactly matching current data, never old generated formula text alone.
        val analysis = when {
            word.literalFormula.startsWith("拼写分块：") -> spelling(word)
            word.literalFormula.startsWith("构词资料分块：") || word.literalFormula.startsWith("复合词分块：") -> construction(word)
            else -> null
        }
        return analysis?.takeIf { it.formula == word.literalFormula }
    }

    fun resourceText(name: String): String = requireNotNull(javaClass.getResourceAsStream("/learning/$name"))
        .bufferedReader(Charsets.UTF_8).use { it.readText() }
}
