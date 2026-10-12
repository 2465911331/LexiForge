package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class OfflineMorphologyTest {
    private fun word(spell: String, meaning: String) = BuiltInWordbook.words.first().copy(
        id = spell, spell = spell, chineseMeaning = meaning, frequencyGroup = "高频词")
    private fun data(words: List<WordFormula>, components: List<AffixComponent> = BuiltInWordbook.components) =
        BackupData("3-CET4-顺序", words, components, BackupPreferences("SYSTEM", true, false, false, "system"))

    @Before fun clearGate() { DataSafety.recoveryRequired = false }

    @Test fun `all local segments reconstruct spelling and references exist`() {
        OfflineMorphology.validatePack()
        assertEquals(18, OfflineMorphology.entries.size)
        assertEquals(16, OfflineMorphology.entries.count { it.assembly })
        assertFalse(OfflineMorphology.canAssemble(BuiltInWordbook.words.first { it.spell == "sensitive" }))
    }

    @Test fun `unknown word cannot inherit substring generated parts or fake examples`() {
        val old = word("remember", "v. 记住").copy(prefixId = "pref_re", rootPart = "member 核心",
            literalFormula = "猜测", sentenceEn = "An essential vocabulary word to remember: remember.", sentenceZh = "模板")
        val corrected = OfflineMorphology.apply(old, "3-CET4-顺序")
        assertNull(corrected.prefixId); assertNull(corrected.rootId); assertNull(corrected.rootPart)
        assertEquals("未分级", corrected.frequencyGroup)
        assertEquals("", corrected.sentenceEn)
        assertEquals(SharedLearningPack.Kind.SPELLING, OfflineMorphology.displayEntry(corrected)?.kind)
        assertTrue(corrected.literalFormula.contains("不是词根或音节边界"))
        assertTrue(OfflineMorphology.canAssemble(corrected))
    }

    @Test fun `unknown sense proper name and unsupported source do not inherit a segmentation`() {
        assertNull(OfflineMorphology.lookup(word("import", "n. 重要性")))
        assertNull(OfflineMorphology.lookup(word("Import", "n. 进口")))
        assertFalse(OfflineMorphology.canAssemble(OfflineMorphology.apply(word("import", "v. 进口"), "unknown-source")))
        assertTrue(OfflineMorphology.canAssemble(OfflineMorphology.apply(word("import", "v. 进口"), "3-CET4-顺序")))
    }

    @Test fun `forged or stale construction never becomes an assembly answer`() {
        val original = BuiltInWordbook.words.first()
        assertFalse(OfflineMorphology.canAssemble(original.copy(rootPart = "wrong")))
        assertFalse(OfflineMorphology.canAssemble(original.copy(literalFormula = "未核对")))
    }

    @Test fun `legacy imports merge meanings without deriving frequency from position`() {
        val json = """[
          {"word":"import","translations":[{"type":"v","translation":"进口"}]},
          {"word":"remember","translations":[{"type":"v","translation":"记住"}]},
          {"word":"import","translations":[{"type":"n","translation":"进口商品"}]},
          {"word":"May","translations":[{"type":"n","translation":"五月"}]},
          {"word":"may","translations":[{"type":"v","translation":"可能"}]}
        ]"""
        val result = WordbookImporter.parse(json, "3-CET4-顺序")
        assertEquals(4, result.words.size)
        assertTrue(result.words.all { it.frequencyGroup == "未分级" })
        assertEquals("v. 进口; n. 进口商品", result.words.first { it.spell == "import" }.chineseMeaning)
        assertTrue(result.words.any { it.id == "case:May" })
        assertTrue(result.words.any { it.id == "may" })
        assertNull(result.words.first { it.spell == "remember" }.prefixId)
        assertEquals(setOf("pref_im", "root_port"), result.components.map { it.id }.toSet())
    }

    @Test fun `full jsonl retains source id and first real sentence without using wordRank`() {
        val json = """{"wordRank":1,"headWord":"import","content":{"word":{"wordId":"CET4_123","content":{"trans":[{"pos":"v","tranCn":"进口"}],"sentence":{"sentences":[{"sContent":"We import tea.","sCn":"我们进口茶叶。"}]}}}}}"""
        val word = WordbookImporter.parse(json, "3-CET4-顺序").words.single()
        assertEquals("CET4_123", word.id)
        assertEquals("We import tea.", word.sentenceEn)
        assertEquals("未分级", word.frequencyGroup)
    }

    @Test fun `sentence jsonl preserves example and invalid records reject whole import`() {
        val line = """{"word":"remember","translations":[{"type":"v","translation":"记住"}],"sentences":[{"sentence":"Remember this word.","translation":"记住这个单词。"}]}"""
        assertEquals("Remember this word.", WordbookImporter.parse(line, "3-CET4-顺序").words.single().sentenceEn)
        assertThrows(IllegalArgumentException::class.java) { WordbookImporter.parse("$line\n{\"word\":\"bad\"}", "3-CET4-顺序") }
        assertThrows(IllegalArgumentException::class.java) { WordbookImporter.parse("[]", "3-CET4-顺序") }
    }

    @Test fun `content correction preserves word state and archive component progress`() {
        val old = word("remember", "v. 记住").copy(isAssembled = true, lastReviewedAt = 12345,
            nextReviewAt = 99999, ebbinghausStage = 4)
        val invented = AffixComponent("root_v_member", "member", "ROOT", "", "猜测", "remember", "未审核", reviewCount = 8)
        val prepared = OfflineMorphology.prepare(data(listOf(old), BuiltInWordbook.components + invented))
        val corrected = prepared.words.single()
        assertEquals(old.id, corrected.id); assertEquals(old.lastReviewedAt, corrected.lastReviewedAt)
        assertEquals(old.nextReviewAt, corrected.nextReviewAt); assertEquals(old.ebbinghausStage, corrected.ebbinghausStage)
        assertEquals(old.isAssembled, corrected.isAssembled)
        assertEquals(invented, prepared.components.first { it.id == invented.id })
        assertFalse(OfflineMorphology.isTrustedComponent(invented))
        assertEquals(prepared, OfflineMorphology.prepare(prepared))
    }

    @Test fun `existing external data repairs once with a backup and old backup cannot reenable guesses`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexiforge_prefs", 0).edit().clear().putString("current_wordbook_id", "3-CET4-顺序").commit()
        File(context.noBackupFilesDir, "recovery").deleteRecursively()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val old = word("remember", "v. 记住").copy(lastReviewedAt = 12345, nextReviewAt = 67890, ebbinghausStage = 2)
            db.lexiDao().replaceAll(BuiltInWordbook.components, listOf(old))
            val backup = BackupRepository(context, db.lexiDao())
            assertTrue(backup.repairOfflineContent())
            assertFalse(backup.repairOfflineContent())
            assertEquals(1, backup.points().size)
            val original = backup.points().single().inputStream().use(BackupFormat::read)
            assertEquals("高频词", original.data.words.single().frequencyGroup)
            backup.restore(backup.preview(original))
            val restored = backup.snapshot().words.single()
            assertNull(restored.prefixId)
            assertEquals("未分级", restored.frequencyGroup)
            assertEquals(2, restored.ebbinghausStage)
            assertEquals(67890L, restored.nextReviewAt)
        } finally { db.close() }
    }

    @Test fun `spelling only wordbook is not replaced by built in content on startup`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val spelling = OfflineMorphology.apply(word("remember", "v. 记住"), "3-CET4-顺序")
            db.lexiDao().replaceAll(emptyList(), listOf(spelling))
            val repo = LexiRepository(db.lexiDao(), context)
            repo.checkAndPrepopulate(false)
            assertEquals(listOf(spelling), repo.allWords.first())
            assertTrue(repo.allComponents.first().isEmpty())
        } finally { db.close() }
    }

    @Test fun `optional downloaded upstream books pass importer and backup validation`() {
        val samples = File("../../content-samples")
        Assume.assumeTrue("Download upstream samples as documented to run this integration check", File(samples, "cet4-legacy.json").isFile)
        listOf("cet4-legacy.json" to "3-CET4-顺序", "cet6-legacy.json" to "4-CET6-顺序").forEach { (file, id) ->
            val result = WordbookImporter.parse(File(samples, file).readText(), id)
            assertTrue(result.words.size > 3000)
            assertTrue(result.words.all { it.frequencyGroup == "未分级" })
            result.words.filter(OfflineMorphology::canAssemble).forEach { assertNotNull(OfflineMorphology.displayEntry(it)) }
            println("UPSTREAM $id words=${result.words.size} localSegments=${result.words.count(OfflineMorphology::canAssemble)}")
            val rules = result.words.filter { OfflineMorphology.displayEntry(it)?.ruleDerived == true }
            assertTrue("Rules must improve actual dictionary coverage", rules.size > 100)
            java.io.File("build/ui-preview").mkdirs()
            java.io.File("build/ui-preview/rules-$id.tsv").writeText(rules.joinToString("\n") { "${it.spell}\t${it.chineseMeaning}\t${it.literalFormula}" })
            println("RULES $id generated=${rules.size}")
            val snapshot = BackupData(id, result.words, result.components, BackupPreferences("SYSTEM", true, false, false, "system"))
            val normalized = OfflineMorphology.prepare(snapshot)
            assertEquals(snapshot.words.sortedBy { it.id }, normalized.words)
            assertEquals(snapshot.components.sortedBy { it.id }, normalized.components)
        }
    }
}
