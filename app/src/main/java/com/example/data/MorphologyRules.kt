package com.example.data

import java.util.Locale

/** Bounded, dictionary-backed spelling analysis, not a historical etymology generator.
 * Rules describe one productive outer affix. Both the target and base must be attested.
 * See docs/morphology-rules.md for exclusions, priority and spelling transformations.
 */
object MorphologyRules {
    const val VERSION = "2026.10.02.1"
    const val SOURCE = "https://dictionary.cambridge.org/grammar/british-grammar/word-formation"
    private val spelling = Regex("[a-z]{3,40}")
    private val posPattern = Regex("(?:^|[;；]\\s*)([a-z]+(?:\\s*[&/]\\s*[a-z]+)*)\\.", RegexOption.IGNORE_CASE)
    private val vowels = "aeiou"
    // Stress is not inferred from letters. Listed verbs require doubling in these forms.
    private val doublingBases = setOf("run", "stop", "plan", "begin", "prefer", "occur", "admit", "permit",
        "refer", "forget", "get", "fit", "sit", "swim", "shop", "drop", "rob", "hop", "slip", "step", "wrap", "win", "rat")
    data class Rule(val affix: String, val prefix: Boolean, val from: Set<String>, val to: Set<String>,
                    val meaning: String, val sense: Regex? = null) {
        val id get() = "rule:${if (prefix) "prefix" else "suffix"}:$affix"
    }
    private fun suffix(s: String, from: String, to: String, meaning: String, sense: String? = null) =
        Rule(s, false, from.split(',').toSet(), to.split(',').toSet(), meaning, sense?.toRegex())
    private fun prefix(s: String, pos: String, meaning: String, sense: String? = null) =
        Rule(s, true, pos.split(',').toSet(), pos.split(',').toSet(), meaning, sense?.toRegex())
    val rules = listOf(
        suffix("ness", "adj", "n", "性质、状态"),
        suffix("ly", "adj", "adv", "以某种方式"),
        suffix("ally", "adj", "adv", "以某种方式（-ic 形容词的常见变化）"),
        suffix("ful", "n,v", "adj", "具有、充满"),
        suffix("less", "n", "adj", "没有、缺少"),
        suffix("ment", "v", "n", "行为、结果或状态"),
        suffix("able", "v", "adj", "能够、适于"),
        suffix("er", "v", "n", "从事者或工具", "者|员|人|师|家|工|机|器"),
        suffix("est", "adj", "adj", "最高级", "最"),
        suffix("ing", "v", "v,n,adj", "-ing 形式"),
        suffix("ed", "v", "v,adj", "-ed 形式"),
        suffix("ship", "n", "n", "身份、关系或状态"),
        suffix("hood", "n,adj", "n", "时期、身份或状态"),
        suffix("dom", "n,adj", "n", "领域或状态"),
        suffix("ish", "n,adj", "adj", "具有某种特征"),
        suffix("al", "n", "adj", "与某事物有关"),
        suffix("ous", "n", "adj", "具有某种性质"),
        suffix("ity", "adj", "n", "性质或状态"),
        suffix("ion", "v", "n", "行为或结果"),
        suffix("ation", "v", "n", "行为、过程或结果"),
        suffix("ance", "v", "n", "行为、状态或性质"),
        suffix("ence", "v", "n", "行为、状态或性质"),
        suffix("or", "v", "n", "从事者或工具", "者|员|人|师|家|工|机|器"),
        suffix("ist", "n,adj", "n", "从事者或支持者", "者|员|人|师|家|工|手"),
        suffix("ism", "n,adj", "n", "思想、制度或行为", "主义|制度|行为|歧视|旅游"),
        suffix("ive", "v", "adj", "具有某种作用或倾向"),
        suffix("ize", "n,adj", "v", "使成为、使具有"),
        suffix("ise", "n,adj", "v", "使成为、使具有"),
        suffix("ify", "n,adj", "v", "使成为、使具有"),
        suffix("en", "adj", "v", "使变得"),
        suffix("s", "n", "n", "规则复数"),
        suffix("es", "n", "n", "规则复数"),
        prefix("un", "adj,v", "否定或反向", "不|无|非|未|难|解|松|卸|除"),
        prefix("re", "v", "再一次或返回", "再|重|复|回"),
        prefix("dis", "v,adj,n", "否定、分离或反向", "不|无|非|失|否|反|消|除|断|离|分|拆"),
        prefix("mis", "v,n", "错误、不当", "误|错|不当"),
        prefix("non", "adj,n", "非、没有", "非|不|无"),
        prefix("over", "v,n,adj", "过度或在上", "过|超|上"),
        prefix("under", "v,n,adj", "不足或在下", "不足|低|下"),
        prefix("pre", "v,n,adj", "在前、预先", "预|前"),
        prefix("post", "n,adj", "在后", "后"),
        prefix("inter", "v,n,adj", "之间、相互", "间|互"),
        prefix("anti", "n,adj", "反对、抵抗", "反|抗|防"),
        prefix("super", "n,adj", "超出、在上", "超|上|特"),
        prefix("sub", "n,adj", "在下、次级", "下|次|亚|分"),
        prefix("multi", "n,adj", "多个", "多"),
        prefix("semi", "n,adj", "一半、部分", "半|部分"),
        prefix("in", "adj", "否定", "不|无|非|未|缺"),
        prefix("im", "adj", "否定（in- 的拼写变体）", "不|无|非|未|缺"),
        prefix("il", "adj", "否定（in- 的拼写变体）", "不|无|非|未|缺"),
        prefix("ir", "adj", "否定（in- 的拼写变体）", "不|无|非|未|缺")
    )
    // These cannot safely be interpreted with the productive modern rules above.
    private val excluded = setOf("business", "hardly", "lately", "nearly", "mostly", "really",
        "flower", "corner", "winter", "summer", "dinner", "supper", "number", "order", "other",
        "reply", "relay", "relate", "release", "remain", "remark", "resort", "resign", "retire",
        "universe", "uniform", "union", "uncle", "understand", "undergo", "underlie", "overcome",
        "news", "means", "series", "species", "clothes", "goods", "woods", "customs", "arms",
        "apartness", "department", "apartment", "argument", "station", "ration", "mention", "onion",
        "legal", "fatal", "mineral", "passion", "recover", "liable", "formation", "compliment", "stocking",
        "preface", "disappoint", "disease", "dissolve", "gravity", "procession", "electronics", "economics",
        "mechanics", "statistics", "physics", "mathematics", "politics",
        "inflammable", "invaluable", "infamous", "inventory", "university", "universal",
        "surprise", "exercise", "enterprise", "comprise", "promise", "premise", "otherwise",
        "objective", "subjective", "executive", "finance", "importance", "passive", "indifferent",
        "competence", "ignorance", "fasten", "modify", "notify", "organize", "authorize", "respective")

    private fun posLabels(label: String): Set<String> = label.lowercase(Locale.ROOT).split('&', '/').map { p -> when (p.trim()) {
            "vt", "vi" -> "v"; "a" -> "adj"; "ad" -> "adv"; else -> p.trim()
        }
    }.filter { it in setOf("n", "v", "adj", "adv") }.toSet()

    private fun senses(meaning: String): Map<String, String> {
        val markers = posPattern.findAll(meaning.trim()).toList()
        val text = meaning.trim()
        return markers.flatMapIndexed { index, match ->
            val definition = text.substring(match.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: text.length)
            posLabels(match.groupValues[1]).map { it to definition }
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.joinToString("；") }
    }

    fun partsOfSpeech(meaning: String): Set<String> = senses(meaning).keys

    class Lexicon(words: List<WordFormula>) {
        internal val pos = words.filter { spelling.matches(it.spell) }.groupBy { it.spell }
            .mapValues { (_, rows) -> rows.flatMap { partsOfSpeech(it.chineseMeaning) }.toSet() }
    }

    data class Analysis(val rule: Rule, val base: String, val basePos: String, val surface: String, val change: String) {
        val baseId get() = "rule:base:$basePos:$base:$surface"
        fun template(word: WordFormula): WordFormula {
            val parts = if (rule.prefix) "${rule.affix}- + $surface" else "$surface + -${rule.affix}"
            return word.copy(
                prefixId = if (rule.prefix) rule.id else null, rootId = baseId,
                suffixId = if (rule.prefix) null else rule.id,
                prefixPart = if (rule.prefix) "${rule.affix}-" else null, rootPart = surface,
                suffixPart = if (rule.prefix) null else "-${rule.affix}",
                literalFormula = "规则分块：$parts = ${word.spell}。词基 $base（$basePos.）；$change。" +
                    "词缀通常表示${rule.meaning}，具体词义以词典释义为准。")
        }
    }

    fun analyze(word: WordFormula, lexicon: Lexicon): Analysis? {
        val candidates = candidates(word, lexicon.pos)
        // One outer suffix first; otherwise one prefix. Never choose between competing bases.
        val preferred = candidates.filter { !it.rule.prefix }.ifEmpty { candidates.filter { it.rule.prefix } }
        return preferred.distinctBy { Triple(it.rule.id, it.base, it.surface) }.singleOrNull()
    }

    private fun candidates(word: WordFormula, lexicon: Map<String, Set<String>>): List<Analysis> {
        if (!spelling.matches(word.spell) || word.spell in excluded) return emptyList()
        val targetSenses = senses(word.chineseMeaning)
        val targetPos = targetSenses.keys
        return rules.flatMap { rule ->
            val eligiblePos = targetPos.intersect(rule.to).filter { rule.sense?.containsMatchIn(targetSenses.getValue(it)) != false }.toSet()
            if (eligiblePos.isEmpty())
                return@flatMap emptyList()
            val surface = if (rule.prefix && word.spell.startsWith(rule.affix)) word.spell.removePrefix(rule.affix)
                else if (!rule.prefix && word.spell.endsWith(rule.affix)) word.spell.removeSuffix(rule.affix)
                else return@flatMap emptyList()
            if (surface.length < 2) return@flatMap emptyList()
            baseCandidates(surface, rule).mapNotNull { (base, change) ->
                if (!spelling.matches(base)) return@mapNotNull null
                val pos = lexicon[base].orEmpty().intersect(rule.from)
                    .let { if (rule.prefix) it.intersect(eligiblePos) else it }.sorted().firstOrNull()
                    ?: return@mapNotNull null
                Analysis(rule, base, pos, surface, change)
            }
        }
    }

    private fun baseCandidates(surface: String, rule: Rule): List<Pair<String, String>> {
        if (rule.prefix) {
            val initial = surface.firstOrNull() ?: return emptyList()
            val valid = when (rule.affix) {
                "im" -> initial in "bmp"; "il" -> initial == 'l'; "ir" -> initial == 'r'
                "in" -> initial !in "blmpr"; else -> true
            }
            return if (valid) listOf(surface to "词基拼写不变") else emptyList()
        }
        val s = rule.affix
        if (s == "ally") return if (surface.endsWith("ic")) listOf(surface to "-ic 词基后连接 -ally") else emptyList()
        val result = mutableListOf<Pair<String, String>>()
        // Reconstruct only changes licensed for this affix, never arbitrary suffix stripping.
        if (s != "es" || surface.endsWith("s") || surface.endsWith("x") || surface.endsWith("z") ||
            surface.endsWith("ch") || surface.endsWith("sh")) {
            if (s != "s" || !(surface.endsWith("s") || surface.endsWith("x") || surface.endsWith("z") ||
                    surface.endsWith("ch") || surface.endsWith("sh") || Regex("[^aeiou]y$").containsMatchIn(surface))) {
                if (s != "ion" || surface.endsWith("t") || surface.endsWith("ss"))
                    result += surface to "词基拼写不变"
            }
        }
        if (s in setOf("ness", "ly", "ful", "ment", "er", "est", "ed", "es", "able", "less", "ance") &&
            surface.endsWith("i") && surface.length > 2 && surface[surface.lastIndex - 1] !in vowels) {
            result += surface.dropLast(1) + "y" to "词基末尾 y 变为 i"
        }
        if (s in setOf("ing", "ed", "er", "est", "able", "ity", "ion", "ation", "ance", "ence", "or", "ive", "ize", "ise", "ify", "ism", "ist", "en") &&
            !surface.endsWith("e") && (s != "ion" || surface.endsWith("at") || surface.endsWith("iz") || surface.endsWith("vis") || surface.endsWith("fus"))) {
            result += surface + "e" to "词基末尾 e 去掉后连接词缀"
        }
        if (s in setOf("ing", "ed", "er", "est") && surface.length >= 4 &&
            surface.last() == surface[surface.lastIndex - 1] && surface.last() !in "aeiouwxy" &&
            surface[surface.lastIndex - 2] in vowels) {
            result += surface.dropLast(1) to "本词双写词基末尾 ${surface.last()} 后连接词缀"
        }
        if (s == "ing" && surface.endsWith("y")) result += surface.dropLast(1) + "ie" to "词基末尾 ie 变为 y"
        if (s == "ly") {
            result += surface + "le" to "词基末尾 le 改写为 ly（分块时去掉 le，再接 -ly）"
            if (surface in setOf("tru", "du", "whol")) result += surface + "e" to "本词去掉末尾 e 后连接 -ly"
        }
        if (s == "ity" && surface.endsWith("bil")) result += surface.dropLast(2) + "le" to "词基末尾 -ble 变为 -bil 后连接 -ity"
        if (s in setOf("ify", "ist", "ize", "ise") && surface.last() !in vowels) result += surface + "y" to "本词去掉词基末尾 y 后连接词缀"
        if (s == "ation" && surface.endsWith("ific")) result += surface.dropLast(2) + "y" to "词基末尾 -ify 改写为 -ific 后连接 -ation"
        if (s == "ist" && surface == "pian") result += "piano" to "piano 去掉末尾 o 后连接 -ist"
        // These are bounded word-family alternations, not permission to replace any d/t with s.
        val changedBase = when {
            s == "ion" && surface.endsWith("cis") -> surface.dropLast(1) + "de"
            s == "ion" && surface.endsWith("vis") -> surface.dropLast(1) + "de"
            s == "ion" && surface.endsWith("clus") -> surface.dropLast(1) + "de"
            s == "ion" && surface.endsWith("ss") -> (surface.dropLast(2) + "t").takeIf { it.endsWith("mit") }
            else -> null
        }
        if (changedBase != null) result += changedBase to "本词词基 $changedBase 改写为 $surface 后连接 -ion"
        return result.filterNot { (base, _) ->
            base == surface && ((s in setOf("ing", "ed", "er", "est") && base in doublingBases) ||
                (s == "ing" && base.endsWith("e") && !base.endsWith("ee") && !base.endsWith("ye") && !base.endsWith("oe")))
        }.distinctBy { it.first }
    }

    /** Revalidate persisted spelling and rule metadata; legacy guessed roots cannot enter here. */
    fun displayAnalysis(word: WordFormula): Analysis? {
        val fields = word.rootId?.split(':') ?: return null
        if (fields.size != 5 || fields[0] != "rule" || fields[1] != "base") return null
        return candidates(word, mapOf(fields[3] to setOf(fields[2]))).firstOrNull {
            val t = it.template(word)
            t.rootId == word.rootId && t.prefixId == word.prefixId && t.suffixId == word.suffixId &&
                t.prefixPart == word.prefixPart && t.rootPart == word.rootPart && t.suffixPart == word.suffixPart &&
                t.literalFormula == word.literalFormula
        }
    }

    fun component(id: String): AffixComponent? {
        rules.firstOrNull { it.id == id }?.let { r -> return AffixComponent(id,
            if (r.prefix) "${r.affix}-" else "-${r.affix}", if (r.prefix) "PREFIX" else "SUFFIX", "🧩",
            r.meaning, "", "本地构词规则；只描述常见用法，不能直接推出整个单词的词义。") }
        val f = id.split(':')
        if (f.size != 5 || f[0] != "rule" || f[1] != "base" || f[2] !in setOf("n", "v", "adj", "adv") ||
            !spelling.matches(f[3]) || !Regex("[a-z]{2,40}").matches(f[4])) return null
        return AffixComponent(id, f[4], "ROOT", "📖", "词基原形：${f[3]}", f[3],
            "规则推导使用的词基（${f[2]}.），不是历史词根。当前分块拼写：${f[4]}。")
    }
}
