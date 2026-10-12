package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PracticePlannerTest {
    private val now = 1_800_000_000_000L
    private val book = "3-CET4-顺序"
    private fun word(id: String = "sensitive") = BuiltInWordbook.words.single { it.spell == "sensitive" }.copy(
        id = id, spell = id, bookId = book, lastReviewedAt = now - 10000, nextReviewAt = now + 1000)
    private fun event(id: Long, target: String, correct: Boolean, mode: String = "RECALL") = ReviewEvent(
        "e$id", "session$id", book, target, target, "WORD", mode, "敏感的", target, correct, "", 1,
        now - 1000, 500, 1, 1, false, sequence = id)

    @Test fun personalPriority() {
        val words = listOf(word("new").copy(lastReviewedAt = 0), word("weak"), word("due").copy(nextReviewAt = now), word("unverified"), word("strong"))
        val events = listOf(event(1, "weak", false), event(2, "strong", true))
        val queue = PracticePlanner.personal(words, events, now)
        assertEquals(listOf("due", "weak", "unverified", "strong"), queue.map { it.word.id })
        assertEquals(listOf(0, 1, 3, 4), queue.map { it.priority })
        assertEquals(listOf("weak"), PracticePlanner.personal(words, events, now, true).map { it.word.id })
        assertTrue(queue.first().reason.contains("到期"))
    }

    @Test fun recentHistoryAndBookIsolation() {
        val history = listOf(event(1, "sensitive", false)) + (2L..13).map { event(it, "sensitive", true) }
        val independent = history.map { it.copy(answeredAt = now + RecallTools.RETEST_DELAY) }
        val cleared = independent.dropLast(1) + independent.last().copy(answeredAt = now + 2 * RecallTools.RETEST_DELAY)
        val candidate = PracticePlanner.personal(listOf(word()), cleared + event(20, "sensitive", false).copy(bookId = "other"), now).single()
        assertEquals(0, candidate.failures)
        assertEquals(4, candidate.priority)
        val assisted = listOf(event(1, "sensitive", true, "SPELLING").copy(hints = "ANALYSIS"))
        assertEquals(3, PracticePlanner.personal(listOf(word()), assisted, now).single().priority)
    }

    @Test fun personalRotatesEquallyStrongWords() {
        val words = listOf(word("alpha"), word("beta"))
        val events = listOf(event(1, "beta", true), event(2, "alpha", true))
        assertEquals(listOf("beta", "alpha"), PracticePlanner.personal(words, events, now).map { it.word.id })
        assertEquals(listOf("alpha", "beta"), PracticePlanner.personal(words, events + event(3, "beta", true), now).map { it.word.id })
    }

    @Test fun clozeHasNoLeakedSpelling() {
        val w = word().copy(sentenceEn = "My skin is sensitive to sunlight.")
        assertEquals("My skin is ____ to sunlight.", PracticePlanner.cloze(w))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "Sensitive people are often sensitive to noise.")))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "An essential vocabulary word to remember: sensitive.")))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "No useful example available here.")))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "This skin-sensitive area needs careful protection.")))
        assertNull(PracticePlanner.cloze(w.copy(spell = "student", sentenceEn = "The student's report was very clear.")))
        assertNull(PracticePlanner.cloze(w.copy(spell = "sense", sentenceEn = "This nonsense makes sense to me.")))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "sensitive.")))
        assertNull(PracticePlanner.cloze(w.copy(sentenceEn = "____ is sensitive to sunlight.")))
    }

    @Test fun examEligibilityAndRotation() {
        val first = word().copy(sentenceEn = "My skin is sensitive to sunlight.")
        val second = word("plain").copy(sentenceEn = "Please use plain language in your report.")
        val words = listOf(first, second, word("new").copy(lastReviewedAt = 0), first.copy(bookId = "4-CET6-顺序"))
        val history = listOf(event(1, first.id, true, "EXAM_CLOZE").copy(hints = "CONTEXT"))
        assertEquals(listOf("plain", "sensitive"), PracticePlanner.exam(words, history, book, true).map { it.id })
        assertTrue(PracticePlanner.exam(words, history, "built-in", true).isEmpty())
        val correction = ContentCorrection(book, first.id, first.chineseMeaning, "A missing target example has no question.", "", "", false, now)
        assertTrue(PracticePlanner.exam(listOf(ContentTools.apply(first, correction)), history, book, true).isEmpty())
    }

    @Test fun contextualCorrectDoesNotClearMistake() {
        val wrong = event(1, "sensitive", false)
        val right = event(2, "sensitive", true, "EXAM_CLOZE").copy(hints = "CONTEXT", answeredAt = now + RecallTools.RETEST_DELAY)
        assertEquals(1, RecallTools.mistakes(listOf(word()), listOf(wrong, right)).size)
        assertTrue(RecallTools.mistakes(listOf(word()), listOf(wrong, right.copy(mode = "PERSONAL_RECALL", hints = ""))).isEmpty())
    }

    @Test fun examWritesAndBackup() = runBlocking {
        DataSafety.recoveryRequired = false
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        try {
            val dao = db.lexiDao()
            dao.ensureLibrary(book)
            dao.installBook(book, emptyList(), listOf(word()))
            val repo = LexiRepository(dao)
            val request = AnswerRequest("a", "s", book, "sensitive", "WORD", "EXAM_CLOZE", "incorrect", true, "CONTEXT", 1, now, 100)
            assertTrue(repo.recordAnswer(request, DataSafety.generation))
            val event = dao.eventSnapshot().single()
            assertFalse(event.correct)
            assertEquals("My skin is ____ to sunlight.", event.prompt)
            assertFalse(repo.recordAnswer(request, DataSafety.generation))
            assertThrows(IllegalArgumentException::class.java) { runBlocking { repo.recordAnswer(request.copy(id = "b", hints = ""), DataSafety.generation) } }
            val data = BackupRepository(context, dao).snapshot()
            val bytes = ByteArrayOutputStream().also { BackupFormat.write(data, it) }.toByteArray()
            assertEquals(data, BackupFormat.read(bytes.inputStream()).data)
            assertThrows(IllegalArgumentException::class.java) { BackupFormat.read(asV4(bytes).inputStream()) }
            val old = data.copy(events = data.events.map { it.copy(mode = "RECALL", hints = "") }, corrections = listOf(
                ContentCorrection(book, "sensitive", "个人释义", "", "", "参考", true, now)))
            val oldBytes = ByteArrayOutputStream().also { BackupFormat.write(old, it) }.toByteArray()
            assertEquals(old, BackupFormat.read(asV4(oldBytes).inputStream()).data)
        } finally { db.close() }
    }

    private fun asV4(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip -> ZipInputStream(bytes.inputStream()).use { input ->
            while (true) {
                val entry = input.nextEntry ?: break
                val data = input.readBytes()
                zip.putNextEntry(ZipEntry(entry.name))
                zip.write(if (entry.name == "manifest.json") data.toString(Charsets.UTF_8)
                    .replace("\"formatVersion\":5", "\"formatVersion\":4").replace("\"minReaderVersion\":5", "\"minReaderVersion\":4").toByteArray() else data)
                zip.closeEntry()
            }
        } }
    }.toByteArray()
}
