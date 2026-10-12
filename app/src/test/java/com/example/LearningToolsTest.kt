package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearningToolsTest {
    private val now = 1_800_000_000_000L
    private fun word() = BuiltInWordbook.words.single { it.spell == "sensitive" }.copy(lastReviewedAt = now - 1000, nextReviewAt = now, ebbinghausStage = 2)
    private fun event(n: Long, correct: Boolean, mode: String = "SPELLING", time: Long = now, hints: String = "") = ReviewEvent(
        "event$n", "s$n", "built-in", word().id, word().spell, "WORD", mode, "敏感的", "answer", correct, hints,
        1, time, 200, 0, 0, false, sequence = n)

    @Test fun `recall question strips answer spelling and rejects non Chinese prompts`() {
        assertFalse(RecallTools.prompt("adj. sensitive；敏感的（ＳＥＮＳＩＴＩＶＥ）").contains("sensitive", true))
        assertTrue(RecallTools.canTest(word()))
        assertFalse(RecallTools.canTest(word().copy(chineseMeaning = "adj. sensitive —")))
        assertFalse(RecallTools.canTest(word().copy(lastReviewedAt = 0)))
    }

    @Test fun `mistakes require delayed independent recall and return on later failure`() {
        val wrong = event(1, false)
        val assisted = event(2, true, hints = "ANALYSIS", time = now + 300001)
        val immediate = event(3, true, "ERROR_RECALL", now + 1)
        assertEquals(1, RecallTools.mistakes(listOf(word()), listOf(wrong, assisted, immediate)).size)
        val delayed = event(4, true, "RECALL", now + 300000)
        assertTrue(RecallTools.mistakes(listOf(word()), listOf(wrong, delayed)).isEmpty())
        val again = event(5, false, time = now + 300001)
        assertEquals(2, RecallTools.mistakes(listOf(word()), listOf(wrong, delayed, again)).single().errors)
        assertEquals(1, RecallTools.mistakes(listOf(word()), listOf(wrong.copy(bookId = "other"), again)).single().errors)
    }

    @Test fun `mistakes use full history beyond latest hundred and only word answers`() {
        val history = listOf(event(1, false)) + (2L..160).map { event(it, true, "BLOCKS") }
        assertEquals(1, RecallTools.mistakes(listOf(word()), history).size)
        assertTrue(RecallTools.mistakes(listOf(word()), listOf(event(1, false).copy(targetKind = "COMPONENT"))).isEmpty())
    }

    @Test fun `word families have evidence and spelling chunks do not create families`() {
        val curated = WordFamilies.build(BuiltInWordbook.words)
        assertEquals(setOf("import", "export", "transport"), curated.single { it.key == "root:root_port" }.words.map { it.spell }.toSet())
        val imported = WordbookImporter.parse("""[{"word":"happy","translations":[{"type":"adj","translation":"快乐的"}]},{"word":"happiness","translations":[{"type":"n","translation":"幸福"}]},{"word":"happily","translations":[{"type":"adv","translation":"快乐地"}]}]""", "3-CET4-顺序")
        assertEquals(setOf("happy", "happiness", "happily"), WordFamilies.build(imported.words).single { it.key == "base:happy" }.words.map { it.spell }.toSet())
        val spellingOnly = imported.words.map { it.copy(prefixId = null, rootId = null, suffixId = null, literalFormula = "随意拼块 happy + ness") }
        assertTrue(WordFamilies.build(spellingOnly).isEmpty())
        val raw = BuiltInWordbook.words.single { it.spell == "import" }
        val hidden = ContentTools.apply(raw, ContentCorrection("built-in", raw.id, raw.chineseMeaning, "", "", "拆解有疑问", true, now))
        assertFalse(WordFamilies.build(BuiltInWordbook.words.filterNot { it.id == raw.id } + hidden).flatMap { it.words }.any { it.id == raw.id })
    }

    @Test fun correctionsRoundtrip() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataSafety.recoveryRequired = false
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.lexiDao()
            dao.ensureLibrary("built-in", now)
            dao.installBook("built-in", BuiltInWordbook.components, listOf(word()))
            dao.installBook("other", emptyList(), listOf(word().copy(chineseMeaning = "其他义项")))
            dao.selectBook("built-in")
            val repo = LexiRepository(dao, context)
            val original = dao.wordSnapshot().single()
            val edit = ContentCorrection("built-in", original.id, "adj. 个人修订释义", "An example.", "示例。", "离线参考", true, now)
            repo.saveCorrection(edit)
            val effective = repo.allWords.first().single()
            assertEquals(edit.meaning, effective.chineseMeaning)
            assertEquals(original.ebbinghausStage, effective.ebbinghausStage)
            assertNull(OfflineMorphology.displayEntry(effective))
            assertEquals(original, dao.wordSnapshot().single())
            assertEquals("其他义项", dao.wordsForBook("other").single().chineseMeaning)
            val service = BackupRepository(context, dao)
            val before = service.snapshot()
            val out = ByteArrayOutputStream().also { BackupFormat.write(before, it) }
            service.restore(service.preview(BackupFormat.read(out.toByteArray().inputStream())))
            assertEquals(listOf(edit), dao.correctionSnapshot())
            assertEquals(edit.meaning, repo.allWords.first().single().chineseMeaning)
            val report = CorrectionExport.report(before)
            assertTrue(report.contains("personal-unreviewed")); assertTrue(report.contains(edit.note))
            assertFalse(report.contains("ebbinghausStage")); assertFalse(report.contains("review_events"))
            repo.removeCorrection("built-in", original.id)
            assertTrue(dao.correctionSnapshot().isEmpty())
            assertEquals(original.chineseMeaning, repo.allWords.first().single().chineseMeaning)
            assertEquals(original.ebbinghausStage, dao.wordSnapshot().single().ebbinghausStage)
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.validate(before.copy(corrections = listOf(edit.copy(wordId = "missing")))) }
            Unit
        } finally { db.close() }
    }

    @Test fun recallGrading() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        DataSafety.recoveryRequired = false
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.lexiDao()
            dao.ensureLibrary()
            dao.installBook("built-in", emptyList(), listOf(word()))
            val repo = LexiRepository(dao)
            val request = AnswerRequest("a1", "session", "built-in", word().id, "WORD", "RECALL", "wrong", true, "", 1, now, 100)
            assertTrue(repo.recordAnswer(request, DataSafety.generation))
            assertFalse(dao.eventSnapshot().single().correct) // grade against the stored spelling, not the supplied Boolean
            assertFalse(repo.recordAnswer(request, DataSafety.generation))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.recordAnswer(request.copy(id = "a2", hints = "ANALYSIS"), DataSafety.generation) } }
            assertEquals(1, dao.eventSnapshot().size)
            val snapshot = BackupRepository(context, dao).snapshot()
            assertEquals(snapshot, ByteArrayOutputStream().also { BackupFormat.write(snapshot, it) }.toByteArray().inputStream().use(BackupFormat::read).data)
        } finally { db.close() }
    }
}
