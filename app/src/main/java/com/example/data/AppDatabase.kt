package com.example.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LexiDao {
    @Query("SELECT * FROM content_corrections ORDER BY bookId, wordId")
    suspend fun correctionSnapshot(): List<ContentCorrection>

    @Query("SELECT * FROM content_corrections WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in') ORDER BY wordId")
    fun observeCorrections(): Flow<List<ContentCorrection>>

    @Query("SELECT * FROM content_corrections WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun correctionFor(bookId: String, wordId: String): ContentCorrection?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCorrection(correction: ContentCorrection)

    @Query("DELETE FROM content_corrections WHERE bookId = :bookId AND wordId = :wordId")
    suspend fun deleteCorrection(bookId: String, wordId: String)

    @Query("DELETE FROM content_corrections")
    suspend fun deleteCorrections()

    @Query("SELECT * FROM review_events WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in') ORDER BY sequence")
    fun activeEvents(): Flow<List<ReviewEvent>>
    @Query("SELECT * FROM library_state WHERE id = 0")
    suspend fun libraryState(): LibraryState?

    @Query("SELECT * FROM library_state WHERE id = 0")
    fun observeLibraryState(): Flow<LibraryState?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveLibraryState(state: LibraryState)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveBook(book: Wordbook)

    @Query("SELECT * FROM wordbooks ORDER BY id")
    suspend fun bookSnapshot(): List<Wordbook>

    @Query("SELECT b.id, b.lastWordId, b.dailyNewTarget, COUNT(w.id) AS wordCount, COALESCE(SUM(CASE WHEN w.lastReviewedAt > 0 THEN 1 ELSE 0 END), 0) AS learnedCount FROM wordbooks b LEFT JOIN word_formulas w ON w.bookId = b.id GROUP BY b.id ORDER BY b.id")
    fun observeBooks(): Flow<List<BookOverview>>

    @Query("UPDATE wordbooks SET dailyNewTarget = :target WHERE id = :bookId")
    suspend fun saveDailyTarget(bookId: String, target: Int)

    // Observe all tables affecting the plan; read their values together in dailyInputs().
    @Query("SELECT (SELECT COUNT(*) FROM word_formulas) + (SELECT COUNT(*) FROM review_events) + (SELECT COUNT(*) FROM wordbooks) + (SELECT COUNT(*) FROM library_state) + (SELECT COUNT(*) FROM content_corrections)")
    fun dailyChanges(): Flow<Int>

    @Query("SELECT * FROM review_events WHERE bookId = :bookId AND answeredAt >= :start AND answeredAt < :end ORDER BY sequence")
    suspend fun eventsBetween(bookId: String, start: Long, end: Long): List<ReviewEvent>

    @Transaction
    suspend fun dailyInputs(start: Long, end: Long): DailyInputs {
        val book = libraryState()?.activeBookId ?: "built-in"
        return DailyInputs(book, bookSnapshot().find { it.id == book }?.dailyNewTarget ?: 20,
            ContentTools.applyAll(wordsForBook(book), correctionSnapshot()), eventsBetween(book, start, end))
    }

    @Query("SELECT * FROM word_formulas WHERE bookId = :bookId")
    suspend fun wordsForBook(bookId: String): List<WordFormula>

    @Query("SELECT * FROM affix_components WHERE bookId = :bookId")
    suspend fun componentsForBook(bookId: String): List<AffixComponent>

    @Query("SELECT * FROM word_formulas WHERE bookId = :bookId AND id = :id")
    suspend fun wordById(bookId: String, id: String): WordFormula?

    @Query("SELECT * FROM affix_components WHERE bookId = :bookId AND id = :id")
    suspend fun componentById(bookId: String, id: String): AffixComponent?

    @Query("UPDATE wordbooks SET lastWordId = :wordId WHERE id = :bookId")
    suspend fun savePosition(bookId: String, wordId: String)

    @Query("SELECT * FROM review_events ORDER BY sequence")
    suspend fun eventSnapshot(): List<ReviewEvent>

    @Query("SELECT * FROM review_events WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in') ORDER BY sequence DESC LIMIT 100")
    fun recentEvents(): Flow<List<ReviewEvent>>

    @Query("SELECT COUNT(*) FROM review_events WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    fun eventCount(): Flow<Int>

    @Query("SELECT * FROM review_events WHERE bookId = :bookId AND targetId = :targetId AND targetKind = :kind ORDER BY sequence DESC LIMIT 1")
    suspend fun lastEvent(bookId: String, targetId: String, kind: String): ReviewEvent?

    @Query("SELECT COALESCE(MAX(sequence), 0) + 1 FROM review_events")
    suspend fun nextEventSequence(): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: ReviewEvent): Long

    @Query("DELETE FROM review_events")
    suspend fun deleteEvents()

    @Query("DELETE FROM wordbooks")
    suspend fun deleteBooks()

    @Query("DELETE FROM word_formulas")
    suspend fun deleteLibraryWords()

    @Query("DELETE FROM affix_components")
    suspend fun deleteLibraryComponents()

    @Transaction
    suspend fun ensureLibrary(bookId: String = "built-in", startedAt: Long = 0) {
        if (libraryState() != null) return
        // Fresh installs and in-memory callers; a real v3 database uses the explicit migration.
        val words = wordSnapshot()
        val components = componentSnapshot()
        deleteLibraryWords()
        deleteLibraryComponents()
        insertWords(words.map { it.copy(bookId = bookId) })
        insertComponents(components.map { it.copy(bookId = bookId) })
        saveBook(Wordbook(bookId))
        saveLibraryState(LibraryState(activeBookId = bookId, historyStartedAt = startedAt))
    }

    @Transaction
    suspend fun selectBook(bookId: String) {
        require(bookSnapshot().any { it.id == bookId }) { "此词书尚未下载" }
        saveLibraryState((libraryState() ?: LibraryState()).copy(activeBookId = bookId))
    }

    @Transaction
    suspend fun installBook(bookId: String, components: List<AffixComponent>, words: List<WordFormula>) {
        ensureLibrary()
        // Reimporting the same source is idempotent; it must not reset learning progress.
        if (bookSnapshot().none { it.id == bookId } || (wordsForBook(bookId).isEmpty() && componentsForBook(bookId).isEmpty())) {
            saveBook(Wordbook(bookId))
            insertComponents(components.map { it.copy(bookId = bookId) })
            insertWords(words.map { it.copy(bookId = bookId) })
        }
        selectBook(bookId)
    }

    @Transaction
    suspend fun commitAnswer(event: ReviewEvent, nextReviewAt: Long): Boolean {
        if ((libraryState()?.activeBookId ?: "built-in") != event.bookId) return false
        if (insertEvent(event.copy(sequence = nextEventSequence())) == -1L) return false
        if (event.scheduleChanged) {
            if (event.targetKind == "WORD") updateWordEbbinghaus(event.targetId, event.correct, event.answeredAt, event.stageAfter, nextReviewAt)
            else updateComponentEbbinghaus(event.targetId, event.correct, event.answeredAt, event.stageAfter, nextReviewAt)
        }
        return true
    }

    @Transaction
    suspend fun librarySnapshot(preferences: BackupPreferences, fallbackBookId: String): BackupData {
        val state = libraryState()
        val active = state?.activeBookId ?: fallbackBookId
        val books = bookSnapshot()
        fun cleanWords(words: List<WordFormula>) = words.map { it.copy(bookId = "built-in") }.sortedBy { it.id }
        fun cleanComponents(items: List<AffixComponent>) = items.map { it.copy(bookId = "built-in") }.sortedBy { it.id }
        return BackupData(active, cleanWords(wordSnapshot()), cleanComponents(componentSnapshot()), preferences,
            otherBooks = books.filter { it.id != active }.map { BackupBook(it.id, cleanWords(wordsForBook(it.id)), cleanComponents(componentsForBook(it.id))) },
            events = eventSnapshot(), positions = books.mapNotNull { b -> b.lastWordId?.let { b.id to it } }.toMap(),
            historyStartedAt = state?.historyStartedAt ?: 0,
            dailyTargets = books.filter { it.dailyNewTarget != 20 }.associate { it.id to it.dailyNewTarget },
            corrections = correctionSnapshot())
    }

    @Transaction
    suspend fun restoreLibrary(data: BackupData) {
        deleteCorrections()
        deleteLibraryWords()
        deleteLibraryComponents()
        deleteEvents()
        deleteBooks()
        (listOf(BackupBook(data.bookId, data.words, data.components)) + data.otherBooks).forEach { book ->
            saveBook(Wordbook(book.id, data.positions[book.id], data.dailyTargets[book.id] ?: 20))
            insertComponents(book.components.map { it.copy(bookId = book.id) })
            insertWords(book.words.map { it.copy(bookId = book.id) })
        }
        data.events.forEach { insertEvent(it) }
        data.corrections.forEach { saveCorrection(it) }
        saveLibraryState(LibraryState(activeBookId = data.bookId, historyStartedAt = data.historyStartedAt))
    }
    @Transaction
    suspend fun backupSnapshot(): Pair<List<WordFormula>, List<AffixComponent>> = wordSnapshot() to componentSnapshot()

    @Transaction
    suspend fun replaceAll(components: List<AffixComponent>, words: List<WordFormula>) {
        val bookId = libraryState()?.activeBookId ?: "built-in"
        deleteAllWordFormulas()
        deleteAllComponents()
        insertComponents(components.map { it.copy(bookId = bookId) })
        insertWords(words.map { it.copy(bookId = bookId) })
    }

    @Transaction
    suspend fun resetProgress() {
        resetAllComponents()
        resetAllWords()
    }
    @Query("SELECT * FROM word_formulas WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun wordSnapshot(): List<WordFormula>

    @Query("SELECT * FROM affix_components WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun componentSnapshot(): List<AffixComponent>

    // Content correction is atomic, and retained entries keep the user's progress.
    @Transaction
    suspend fun installBuiltInWordbook(preserveProgress: Boolean) {
        val oldWords = if (preserveProgress) wordSnapshot().associateBy { it.id } else emptyMap()
        val oldComponents = if (preserveProgress) componentSnapshot().associateBy { it.id } else emptyMap()
        val words = BuiltInWordbook.words.map { corrected ->
            oldWords[corrected.id]?.let { old -> corrected.copy(
                isAssembled = old.isAssembled, lastReviewedAt = old.lastReviewedAt,
                ebbinghausStage = old.ebbinghausStage, nextReviewAt = old.nextReviewAt
            ) } ?: corrected
        }
        val components = BuiltInWordbook.components.map { corrected ->
            oldComponents[corrected.id]?.let { old -> corrected.copy(
                isMastered = old.isMastered, reviewCount = old.reviewCount,
                lastReviewedAt = old.lastReviewedAt, ebbinghausStage = old.ebbinghausStage,
                nextReviewAt = old.nextReviewAt
            ) } ?: corrected
        }
        deleteAllWordFormulas()
        deleteAllComponents()
        insertComponents(components)
        insertWords(words)
    }

    @Query("SELECT * FROM affix_components WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    fun getAllComponents(): Flow<List<AffixComponent>>

    @Query("SELECT * FROM affix_components WHERE type = :type AND bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    fun getComponentsByType(type: String): Flow<List<AffixComponent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComponents(components: List<AffixComponent>)

    @Query("UPDATE affix_components SET isMastered = :isMastered, reviewCount = reviewCount + 1, lastReviewedAt = :lastReviewedAt, ebbinghausStage = :ebbinghausStage, nextReviewAt = :nextReviewAt WHERE id = :id AND bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun updateComponentEbbinghaus(id: String, isMastered: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long)

    @Query("UPDATE affix_components SET isMastered = :isMastered, reviewCount = reviewCount + 1 WHERE id = :id AND bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun updateComponentMastery(id: String, isMastered: Boolean)

    @Query("SELECT * FROM word_formulas WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    fun getAllWords(): Flow<List<WordFormula>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWords(words: List<WordFormula>)

    @Query("UPDATE word_formulas SET isAssembled = :assembled, lastReviewedAt = :lastReviewedAt, ebbinghausStage = :ebbinghausStage, nextReviewAt = :nextReviewAt WHERE id = :id AND bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun updateWordEbbinghaus(id: String, assembled: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long)

    @Query("UPDATE word_formulas SET isAssembled = :assembled WHERE id = :id AND bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun updateWordAssembly(id: String, assembled: Boolean)

    @Query("SELECT COUNT(*) FROM affix_components WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun getAffixCount(): Int

    @Query("UPDATE affix_components SET isMastered = 0, reviewCount = 0, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0 WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun resetAllComponents()

    @Query("UPDATE word_formulas SET isAssembled = 0, lastReviewedAt = 0, ebbinghausStage = 0, nextReviewAt = 0 WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun resetAllWords()

    @Query("DELETE FROM word_formulas WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun deleteAllWordFormulas()

    @Query("DELETE FROM affix_components WHERE bookId = COALESCE((SELECT activeBookId FROM library_state WHERE id = 0), 'built-in')")
    suspend fun deleteAllComponents()
}

@Database(entities = [AffixComponent::class, WordFormula::class, Wordbook::class, LibraryState::class, ReviewEvent::class, ContentCorrection::class], version = 6, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun lexiDao(): LexiDao
}
