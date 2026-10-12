package com.example

import com.example.data.*
import org.junit.Assert.*
import org.junit.Test

class MorphologyRulesTest {
    @Test fun `upstream combined part of speech labels are interpreted without guessing prose`() {
        assertEquals(setOf("n", "v", "adj"), MorphologyRules.partsOfSpeech("n & vt. 名词和动词; a. 形容词"))
        assertEquals(emptySet<String>(), MorphologyRules.partsOfSpeech("未知词性"))
    }
    private fun word(s: String, meaning: String) = WordFormula(s, s, null, null, null, null, null, null, "", meaning, "", "")
    private fun data(vararg pairs: Pair<String, String>) = BackupData("3-CET4-顺序",
        pairs.map { word(it.first, it.second) }, emptyList(), BackupPreferences("SYSTEM", true, false, false, "system"))
    // These are unit tests of the bounded rules, independent of the shared-data fallback.
    private fun analyze(base: String, baseMeaning: String, target: String, targetMeaning: String): WordFormula {
        val words = data(base to baseMeaning, target to targetMeaning).words
        val w = words.last()
        return MorphologyRules.analyze(w, MorphologyRules.Lexicon(words))?.template(w) ?: w
    }

    @Test fun `new derivational families preserve original bases and exact spelling`() {
        val cases = listOf(
            arrayOf("inform", "v. 通知", "information", "n. 信息", "inform"),
            arrayOf("rely", "v. 依赖", "reliance", "n. 依赖", "reli"),
            arrayOf("qualify", "v. 使有资格", "qualification", "n. 资格", "qualific"),
            arrayOf("memory", "n. 记忆", "memorize", "v. 记住", "memor"),
            arrayOf("organize", "v. 组织", "organization", "n. 组织", "organiz"),
            arrayOf("perform", "v. 表演", "performance", "n. 表演", "perform"),
            arrayOf("depend", "v. 依赖", "dependence", "n. 依赖", "depend"),
            arrayOf("act", "v. 行动", "active", "adj. 活跃的", "act"),
            arrayOf("create", "v. 创造", "creative", "adj. 有创造力的", "creat"),
            arrayOf("act", "v. 表演", "actor", "n. 演员", "act"),
            arrayOf("art", "n. 艺术", "artist", "n. 艺术家", "art"),
            arrayOf("piano", "n. 钢琴", "pianist", "n. 钢琴家", "pian"),
            arrayOf("biology", "n. 生物学", "biologist", "n. 生物学家", "biolog"),
            arrayOf("social", "adj. 社会的", "socialism", "n. 社会主义", "social"),
            arrayOf("modern", "adj. 现代的", "modernize", "v. 使现代化", "modern"),
            arrayOf("modern", "adj. 现代的", "modernise", "v. 使现代化", "modern"),
            arrayOf("simple", "adj. 简单的", "simplify", "v. 简化", "simpl"),
            arrayOf("beauty", "n. 美丽", "beautify", "v. 美化", "beaut"),
            arrayOf("wide", "adj. 宽的", "widen", "v. 加宽", "wid"),
            arrayOf("dark", "adj. 暗的", "darken", "v. 变暗", "dark"),
            arrayOf("decide", "v. 决定", "decision", "n. 决定", "decis"),
            arrayOf("divide", "v. 划分", "division", "n. 分开", "divis"),
            arrayOf("conclude", "v. 总结", "conclusion", "n. 结论", "conclus"),
            arrayOf("admit", "v. 允许进入", "admission", "n. 准入", "admiss"),
            arrayOf("revise", "v. 修订", "revision", "n. 修订", "revis"),
            arrayOf("confuse", "v. 混淆", "confusion", "n. 混乱", "confus")
        )
        cases.forEach { c ->
            val w = analyze(c[0], c[1], c[2], c[3])
            assertTrue(c[2], OfflineMorphology.canAssemble(w))
            assertEquals(c[2], c[4], w.rootPart)
            assertTrue(w.literalFormula.contains("词基 ${c[0]}（"))
            assertEquals(w.spell, listOfNotNull(w.prefixPart, w.rootPart, w.suffixPart).joinToString("") { it.trim('-') })
        }
        assertEquals(MorphologyRules.rules.size, MorphologyRules.rules.map { it.id }.distinct().size)
    }

    @Test fun `negative in family requires correct initial and same sense negation`() {
        listOf(arrayOf("active", "inactive"), arrayOf("possible", "impossible"),
            arrayOf("legal", "illegal"), arrayOf("regular", "irregular"), arrayOf("moral", "immoral")).forEach { c ->
            assertTrue(c[1], OfflineMorphology.canAssemble(analyze(c[0], "adj. 合适的", c[1], "adj. 不合适的")))
        }
        listOf(arrayOf("legal", "inlegal"), arrayOf("regular", "imregular"), arrayOf("active", "ilactive"),
            arrayOf("valuable", "invaluable"), arrayOf("flammable", "inflammable"), arrayOf("famous", "infamous")).forEach { c ->
            assertFalse(c[1], OfflineMorphology.canAssemble(analyze(c[0], "adj. 好的", c[1], "adj. 不好的")))
        }
        assertFalse(OfflineMorphology.canAssemble(analyze("patient", "adj. 耐心的", "impatient", "adj. 急切的; n. 不耐烦的人")))
        assertTrue(OfflineMorphology.canAssemble(analyze("patient", "adj. 耐心的", "impatient", "adj. 不耐烦的; n. 急切者")))
    }

    @Test fun `new rules reject false friends ambiguous etymology and missing lexicon evidence`() {
        listOf(arrayOf("act", "v. 表演", "actor", "n. 作用"),
            arrayOf("art", "n. 艺术", "artist", "v. 艺术家"),
            arrayOf("simple", "n. 简单", "simplify", "n. 简化"),
            arrayOf("enter", "v. 进入", "enterprise", "n. 企业; v. 从事"),
            arrayOf("object", "v. 反对", "objective", "adj. 客观的"),
            arrayOf("fine", "v. 罚款", "finance", "n. 财政"),
            arrayOf("import", "v. 进口", "importance", "n. 重要性"),
            arrayOf("pass", "v. 经过", "passive", "adj. 被动的"),
            arrayOf("different", "adj. 不同的", "indifferent", "adj. 不感兴趣的"),
            arrayOf("compete", "v. 竞争", "competence", "n. 能力"),
            arrayOf("author", "n. 作家", "authorize", "v. 授权"),
            arrayOf("clam", "v. 夹住", "clamsion", "n. 行为")).forEach { c ->
            assertFalse(c[2], OfflineMorphology.canAssemble(analyze(c[0], c[1], c[2], c[3])))
        }
        val missing = data("organization" to "n. 组织", "impossible" to "adj. 不可能的")
        assertTrue(missing.words.all { MorphologyRules.analyze(it, MorphologyRules.Lexicon(missing.words)) == null })
    }

    @Test fun `pos parsing is locale independent and supports slash labels without matching prose`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))
            assertEquals(setOf("v", "n", "adj"), MorphologyRules.partsOfSpeech("  VI/VT. 行动；N. 行为; ADJ. 有效的"))
            assertEquals(emptySet<String>(), MorphologyRules.partsOfSpeech("释义中包含 adj. 字样不构成词性标签"))
        } finally { java.util.Locale.setDefault(original) }
    }

    @Test fun `a formerly whole word gains rules without losing progress and backup remains stable`() {
        val old = data("possible" to "adj. 可能的", "impossible" to "adj. 不可能的")
        val snapshot = old.copy(words = old.words.map { it.copy(isAssembled = true, ebbinghausStage = 5, lastReviewedAt = 1000, nextReviewAt = 9000) })
        val repaired = OfflineMorphology.prepare(snapshot)
        val w = repaired.words.single { it.spell == "impossible" }
        assertTrue(OfflineMorphology.canAssemble(w))
        assertEquals(5, w.ebbinghausStage); assertEquals(9000L, w.nextReviewAt); assertTrue(w.isAssembled)
        assertEquals(repaired, OfflineMorphology.prepare(repaired))
        val out = java.io.ByteArrayOutputStream()
        BackupFormat.write(repaired, out)
        assertEquals(repaired, OfflineMorphology.prepare(BackupFormat.read(out.toByteArray().inputStream()).data))
    }

    @Test fun `productive affixes require an attested base and reconstruct the surface spelling`() {
        val cases = listOf(
            arrayOf("happy", "adj. 快乐的", "happiness", "n. 幸福", "happi", "y 变为 i"),
            arrayOf("happy", "adj. 快乐的", "happily", "adv. 快乐地", "happi", "y 变为 i"),
            arrayOf("make", "v. 制造", "making", "n. 制作", "mak", "e 去掉"),
            arrayOf("run", "v. 跑", "running", "n. 跑步", "runn", "双写"),
            arrayOf("lie", "v. 躺", "lying", "v. 躺着", "ly", "ie 变为 y"),
            arrayOf("simple", "adj. 简单的", "simply", "adv. 简单地", "simp", "le 改写为 ly"),
            arrayOf("possible", "adj. 可能的", "possibility", "n. 可能性", "possibil", "-ble 变为 -bil"),
            arrayOf("create", "v. 创造", "creation", "n. 创造", "creat", "e 去掉"),
            arrayOf("basic", "adj. 基本的", "basically", "adv. 基本上", "basic", "-ally"),
            arrayOf("care", "n. 关心", "careless", "adj. 粗心的", "care", "拼写不变"),
            arrayOf("employ", "v. 雇用", "employment", "n. 雇用", "employ", "拼写不变"),
            arrayOf("read", "v. 阅读", "readable", "adj. 可读的", "read", "拼写不变"),
            arrayOf("teach", "v. 教", "teacher", "n. 教师", "teach", "拼写不变"),
            arrayOf("child", "n. 儿童", "childhood", "n. 童年", "child", "拼写不变"),
            arrayOf("friend", "n. 朋友", "friendship", "n. 友谊", "friend", "拼写不变")
        )
        cases.forEach { c ->
            val result = analyze(c[0], c[1], c[2], c[3])
            assertTrue(c[2], OfflineMorphology.canAssemble(result))
            assertTrue(OfflineMorphology.displayEntry(result)!!.ruleDerived)
            assertEquals(c[4], result.rootPart)
            assertTrue(result.literalFormula, result.literalFormula.contains(c[5]))
            assertEquals(result.spell, listOfNotNull(result.prefixPart, result.rootPart, result.suffixPart).joinToString("") { it.trim('-') })
        }
    }

    @Test fun `prefixes require compatible pos and a meaning cue`() {
        listOf(arrayOf("happy", "adj. 快乐的", "unhappy", "adj. 不快乐的"),
            arrayOf("write", "v. 写", "rewrite", "v. 重写"),
            arrayOf("heat", "v. 加热", "overheat", "v. 过热"),
            arrayOf("understand", "v. 理解", "misunderstand", "v. 误解")).forEach { c ->
            assertTrue(c[2], OfflineMorphology.canAssemble(analyze(c[0], c[1], c[2], c[3])))
        }
        assertFalse(OfflineMorphology.canAssemble(analyze("sign", "v. 签名", "resign", "v. 辞职")))
        assertFalse(OfflineMorphology.canAssemble(analyze("ply", "v. 从事", "reply", "v. 回复")))
    }

    @Test fun `missing base wrong pos unknown pos and false friends stay whole`() {
        val missing = data("happiness" to "n. 幸福")
        assertNull(MorphologyRules.analyze(missing.words.single(), MorphologyRules.Lexicon(missing.words)))
        listOf(arrayOf("busy", "adj. 忙碌的", "business", "n. 商业"),
            arrayOf("hard", "adj. 困难的", "hardly", "adv. 几乎不"),
            arrayOf("flow", "v. 流动", "flower", "n. 花"),
            arrayOf("member", "n. 成员", "remember", "v. 记住"),
            arrayOf("friend", "n. 朋友", "friendly", "adj. 友好的"),
            arrayOf("quick", "n. 快", "quickly", "adv. 快速地"),
            arrayOf("happy", "快乐的", "happiness", "n. 幸福"),
            arrayOf("new", "adj. 新的", "news", "n. 新闻"),
            arrayOf("leg", "n. 腿", "legal", "adj. 法律的"),
            arrayOf("miner", "n. 矿工", "mineral", "adj. 矿物的"),
            arrayOf("format", "v. 格式化", "formation", "n. 形成"),
            arrayOf("comply", "v. 遵守", "compliment", "n. 赞美"),
            arrayOf("lie", "v. 躺", "liable", "adj. 有责任的"),
            arrayOf("quick", "adj. 快的", "Quickly", "adv. 快地")).forEach { c ->
            assertFalse(c[2], OfflineMorphology.canAssemble(analyze(c[0], c[1], c[2], c[3])))
        }
    }

    @Test fun `ambiguous bases are not selected arbitrarily and spelling exclusions resolve hope versus hop`() {
        val ambiguous = data("sing" to "v. 唱", "singe" to "v. 烧焦", "singer" to "n. 歌唱家")
        assertNull(MorphologyRules.analyze(ambiguous.words.single { it.spell == "singer" }, MorphologyRules.Lexicon(ambiguous.words)))
        val hope = OfflineMorphology.prepare(data("hop" to "v. 跳", "hope" to "v. 希望", "hoping" to "v. 希望着"))
        assertTrue(hope.words.single { it.spell == "hoping" }.literalFormula.contains("词基 hope"))
    }

    @Test fun `plural spelling rules and y changes are bounded`() {
        listOf(arrayOf("book", "books", "book"), arrayOf("city", "cities", "citi"),
            arrayOf("box", "boxes", "box")).forEach { c ->
            val result = analyze(c[0], "n. 名词", c[1], "n. 复数")
            assertTrue(c[1], OfflineMorphology.canAssemble(result)); assertEquals(c[2], result.rootPart)
        }
        assertFalse(OfflineMorphology.canAssemble(analyze("city", "n. 城市", "citys", "n. 城市")))
    }

    @Test fun `outer suffix has explicit priority over prefix without inventing nested roots`() {
        val prepared = OfflineMorphology.prepare(data("unhappy" to "adj. 不快乐的", "happily" to "adv. 快乐地", "unhappily" to "adv. 不快乐地"))
        val target = prepared.words.single { it.spell == "unhappily" }
        assertNull(target.prefixId)
        assertEquals("unhappi", target.rootPart)
        assertEquals("-ly", target.suffixPart)
    }

    @Test fun `normalization preserves progress and rule components survive backup round trip`() {
        val old = data("happy" to "adj. 快乐的", "happiness" to "n. 幸福")
        val initial = OfflineMorphology.prepare(old.copy(words = old.words.map { it.copy(isAssembled = true, ebbinghausStage = 3, lastReviewedAt = 100, nextReviewAt = 200) }))
        val learned = initial.copy(components = initial.components.map { it.copy(reviewCount = 7, isMastered = true) })
        assertEquals(learned, OfflineMorphology.prepare(learned))
        assertTrue(learned.components.all(OfflineMorphology::isTrustedComponent))
        assertEquals(3, learned.words.first().ebbinghausStage)
        BackupFormat.validate(learned)
        val out = java.io.ByteArrayOutputStream()
        BackupFormat.write(learned, out)
        assertEquals(learned, BackupFormat.read(out.toByteArray().inputStream()).data)
    }

    @Test fun `tampered rule fields do not enable assembly and curated examples retain priority`() {
        val w = analyze("happy", "adj. 快乐的", "happiness", "n. 幸福")
        assertFalse(OfflineMorphology.canAssemble(w.copy(rootPart = "happy")))
        assertFalse(OfflineMorphology.canAssemble(w.copy(literalFormula = "pretend")))
        assertFalse(OfflineMorphology.isTrustedComponent(MorphologyRules.component(w.rootId!!)!!.copy(literalMeaning = "fake")))
        val book = OfflineMorphology.prepare(data("tract" to "n. 地带", "attract" to "v. 吸引"))
        assertFalse(OfflineMorphology.displayEntry(book.words.single { it.spell == "attract" })!!.ruleDerived)
    }
}
