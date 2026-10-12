package com.example

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.LexiViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LearningFlowTest {
    @Test fun `swipe removes just one card and returns it when five minutes elapse`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
            val vm = LexiViewModel(app, LexiRepository(MemoryDao())) { 1_800_000_000_000L + testScheduler.currentTime }
            store.put("vm", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.noonBlitzComponents.collect {} }
            runCurrent()
            val original = vm.noonBlitzComponents.value
            assertEquals(24, original.size)
            vm.swipeLeft(original.first().id)
            runCurrent()
            assertEquals(original.drop(1).map { it.id }, vm.noonBlitzComponents.value.map { it.id })
            vm.setEbbinghausFilterActive(true)
            runCurrent()
            assertTrue(vm.noonBlitzComponents.value.isEmpty())
            advanceTimeBy(300_000)
            runCurrent()
            assertEquals(listOf(original.first().id), vm.noonBlitzComponents.value.map { it.id })
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }

    @Test fun `wrong answer remains retryable without a second spaced repetition credit`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = ViewModelStore()
        try {
            val app = ApplicationProvider.getApplicationContext<Application>()
            app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
            val dao = MemoryDao()
            val vm = LexiViewModel(app, LexiRepository(dao)) { 1_800_000_000_000L + testScheduler.currentTime }
            store.put("vm", vm)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.eveningSiegeWords.collect {} }
            runCurrent()
            vm.selectWordForAssembly(vm.allWords.value.first { it.id == "sensitive" })
            vm.validateSpelling("wrong")
            runCurrent()
            assertEquals("sensitive", vm.selectedWordId.value)
            assertFalse(vm.assemblySuccess.value)
            assertFalse(vm.eveningSiegeWords.value.any { it.id == "sensitive" })
            vm.validateSpelling("sensitive")
            runCurrent()
            assertTrue(vm.assemblySuccess.value)
            assertEquals(0, dao.wordSnapshot().first { it.id == "sensitive" }.ebbinghausStage)
            advanceTimeBy(300_000)
            runCurrent()
            vm.selectWordForAssembly(vm.allWords.value.first { it.id == "sensitive" })
            vm.validateSpelling("sensitive")
            vm.validateSpelling("sensitive") // double submit must be ignored
            runCurrent()
            val result = dao.wordSnapshot().first { it.id == "sensitive" }
            assertEquals(1, result.ebbinghausStage)
            assertEquals(result.lastReviewedAt + 300_000, result.nextReviewAt)
        } finally {
            store.clear()
            Dispatchers.resetMain()
        }
    }
}

/** Deterministic flow-backed DAO for ViewModel tests; real Room upgrade is tested separately. */
internal class MemoryDao : LexiDao {
    private val edits = MutableStateFlow<List<ContentCorrection>>(emptyList())
    override suspend fun correctionSnapshot() = edits.value.sortedWith(compareBy<ContentCorrection> { it.bookId }.thenBy { it.wordId })
    override fun observeCorrections() = combine(edits, library) { e, s -> e.filter { it.bookId == (s?.activeBookId ?: "built-in") } }
    override suspend fun correctionFor(bookId: String, wordId: String) = edits.value.find { it.bookId == bookId && it.wordId == wordId }
    override suspend fun saveCorrection(correction: ContentCorrection) { edits.value = edits.value.filterNot { it.bookId == correction.bookId && it.wordId == correction.wordId } + correction }
    override suspend fun deleteCorrection(bookId: String, wordId: String) { edits.value = edits.value.filterNot { it.bookId == bookId && it.wordId == wordId } }
    override suspend fun deleteCorrections() { edits.value = emptyList() }
    override fun activeEvents() = combine(events, library) { e, s -> e.filter { it.bookId == (s?.activeBookId ?: "built-in") }.sortedBy { it.sequence } }
    private val library = MutableStateFlow<LibraryState?>(null)
    private val books = MutableStateFlow<List<Wordbook>>(emptyList())
    private val events = MutableStateFlow<List<ReviewEvent>>(emptyList())
    private val active get() = library.value?.activeBookId ?: "built-in"
    override suspend fun libraryState() = library.value
    override fun observeLibraryState() = library
    override suspend fun saveLibraryState(state: LibraryState) { library.value = state }
    override suspend fun saveBook(book: Wordbook) { books.value = (books.value.filterNot { it.id == book.id } + book).sortedBy { it.id } }
    override suspend fun bookSnapshot() = books.value
    override fun observeBooks() = combine(books, words) { b, w -> b.map { BookOverview(it.id, it.lastWordId, w.count { x -> x.bookId == it.id }, w.count { x -> x.bookId == it.id && x.lastReviewedAt > 0 }, it.dailyNewTarget) } }
    override suspend fun saveDailyTarget(bookId: String, target: Int) { books.value = books.value.map { if (it.id == bookId) it.copy(dailyNewTarget = target) else it } }
    override fun dailyChanges() = combine(words, events, books, library, edits) { w, e, b, s, c -> listOf(w, e, b, s, c).hashCode() }
    override suspend fun eventsBetween(bookId: String, start: Long, end: Long) = events.value.filter { it.bookId == bookId && it.answeredAt >= start && it.answeredAt < end }.sortedBy { it.sequence }
    override suspend fun wordsForBook(bookId: String) = words.value.filter { it.bookId == bookId }
    override suspend fun componentsForBook(bookId: String) = components.value.filter { it.bookId == bookId }
    override suspend fun wordById(bookId: String, id: String) = words.value.find { it.bookId == bookId && it.id == id }
    override suspend fun componentById(bookId: String, id: String) = components.value.find { it.bookId == bookId && it.id == id }
    override suspend fun savePosition(bookId: String, wordId: String) { books.value = books.value.map { if (it.id == bookId) it.copy(lastWordId = wordId) else it } }
    override suspend fun eventSnapshot() = events.value.sortedBy { it.sequence }
    override suspend fun nextEventSequence() = (events.value.maxOfOrNull { it.sequence } ?: 0) + 1
    override fun recentEvents() = combine(events, library) { e, s -> e.filter { it.bookId == (s?.activeBookId ?: "built-in") }.sortedByDescending { it.answeredAt }.take(100) }
    override fun eventCount() = combine(events, library) { e, s -> e.count { it.bookId == (s?.activeBookId ?: "built-in") } }
    override suspend fun lastEvent(bookId: String, targetId: String, kind: String) = events.value.lastOrNull { it.bookId == bookId && it.targetId == targetId && it.targetKind == kind }
    override suspend fun insertEvent(event: ReviewEvent): Long {
        if (events.value.any { it.id == event.id }) return -1
        events.value += event
        return events.value.size.toLong()
    }
    override suspend fun deleteEvents() { events.value = emptyList() }
    override suspend fun deleteBooks() { books.value = emptyList() }
    override suspend fun deleteLibraryWords() { words.value = emptyList() }
    override suspend fun deleteLibraryComponents() { components.value = emptyList() }
    private val words = MutableStateFlow<List<WordFormula>>(emptyList())
    private val components = MutableStateFlow<List<AffixComponent>>(emptyList())
    override fun getAllWords() = combine(words, library) { w, s -> w.filter { it.bookId == (s?.activeBookId ?: "built-in") } }
    override fun getAllComponents() = combine(components, library) { c, s -> c.filter { it.bookId == (s?.activeBookId ?: "built-in") } }
    override fun getComponentsByType(type: String) = getAllComponents().map { list -> list.filter { it.type == type } }
    override suspend fun wordSnapshot() = words.value.filter { it.bookId == active }
    override suspend fun componentSnapshot() = components.value.filter { it.bookId == active }
    override suspend fun getAffixCount() = componentSnapshot().size
    override suspend fun insertWords(words: List<WordFormula>) { this.words.value = (this.words.value + words).associateBy { it.bookId to it.id }.values.toList() }
    override suspend fun insertComponents(components: List<AffixComponent>) { this.components.value = (this.components.value + components).associateBy { it.bookId to it.id }.values.toList() }
    override suspend fun deleteAllWordFormulas() { words.value = words.value.filterNot { it.bookId == active } }
    override suspend fun deleteAllComponents() { components.value = components.value.filterNot { it.bookId == active } }
    override suspend fun updateComponentEbbinghaus(id: String, isMastered: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long) {
        components.value = components.value.map { if (it.id == id && it.bookId == active) it.copy(isMastered = isMastered, reviewCount = it.reviewCount + 1, lastReviewedAt = lastReviewedAt, ebbinghausStage = ebbinghausStage, nextReviewAt = nextReviewAt) else it }
    }
    override suspend fun updateWordEbbinghaus(id: String, assembled: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long) {
        words.value = words.value.map { if (it.id == id && it.bookId == active) it.copy(isAssembled = assembled, lastReviewedAt = lastReviewedAt, ebbinghausStage = ebbinghausStage, nextReviewAt = nextReviewAt) else it }
    }
    override suspend fun updateComponentMastery(id: String, isMastered: Boolean) {
        components.value = components.value.map { if (it.id == id && it.bookId == active) it.copy(isMastered = isMastered) else it }
    }
    override suspend fun updateWordAssembly(id: String, assembled: Boolean) {
        words.value = words.value.map { if (it.id == id && it.bookId == active) it.copy(isAssembled = assembled) else it }
    }
    override suspend fun resetAllComponents() {
        components.value = components.value.map { if (it.bookId == active) it.copy(isMastered = false, reviewCount = 0, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0) else it }
    }
    override suspend fun resetAllWords() {
        words.value = words.value.map { if (it.bookId == active) it.copy(isAssembled = false, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0) else it }
    }
}
