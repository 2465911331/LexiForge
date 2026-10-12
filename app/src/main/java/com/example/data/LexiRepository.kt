package com.example.data

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.withLock

class LexiRepository(private val lexiDao: LexiDao, private val context: Context? = null) {
    val books = lexiDao.observeBooks()
    val libraryState = lexiDao.observeLibraryState()
    val recentEvents = lexiDao.recentEvents()
    val eventCount = lexiDao.eventCount()
    val corrections = lexiDao.observeCorrections()
    val activeEvents = lexiDao.activeEvents()
    val originalWords = lexiDao.getAllWords()

    suspend fun saveCorrection(c: ContentCorrection) = protectedChange {
        ContentTools.validate(c)
        require(lexiDao.libraryState()?.activeBookId == c.bookId && lexiDao.wordById(c.bookId, c.wordId) != null) { "词书已切换或词条不存在" }
        lexiDao.saveCorrection(c)
    }
    suspend fun removeCorrection(bookId: String, wordId: String) = protectedChange {
        require(lexiDao.libraryState()?.activeBookId == bookId) { "词书已切换" }
        lexiDao.deleteCorrection(bookId, wordId)
    }
    fun dailyInputs(start: Long, end: Long) = lexiDao.dailyChanges().map { lexiDao.dailyInputs(start, end) }

    suspend fun setDailyTarget(bookId: String, target: Int) = DataSafety.write {
        require(target in 0..200) { "每日新词目标应为 0～200" }
        require(lexiDao.libraryState()?.activeBookId == bookId) { "词书已切换，请重新设置" }
        lexiDao.saveDailyTarget(bookId, target)
    }

    suspend fun initializeLibrary() = DataSafety.write {
        val fallback = context?.getSharedPreferences("lexiforge_prefs", 0)?.getString("current_wordbook_id", "built-in") ?: "built-in"
        lexiDao.ensureLibrary(fallback, System.currentTimeMillis())
        val state = lexiDao.libraryState()!!
        if (state.historyStartedAt == 0L) lexiDao.saveLibraryState(state.copy(historyStartedAt = System.currentTimeMillis()))
    }

    suspend fun selectBook(bookId: String) = DataSafety.write {
        lexiDao.selectBook(bookId)
        DataSafety.generation++
    }

    suspend fun rememberWord(bookId: String, wordId: String) = DataSafety.write {
        if (lexiDao.libraryState()?.activeBookId == bookId && lexiDao.wordById(bookId, wordId) != null)
            lexiDao.savePosition(bookId, wordId)
    }

    suspend fun recordAnswer(request: AnswerRequest, generation: Long): Boolean = DataSafety.mutex.withLock {
        if (DataSafety.recoveryRequired || generation != DataSafety.generation ||
            (lexiDao.libraryState()?.activeBookId ?: "built-in") != request.bookId) return@withLock false
        val raw = if (request.targetKind == "WORD") lexiDao.wordById(request.bookId, request.targetId) else null
        val word = raw?.let { ContentTools.apply(it, lexiDao.correctionFor(it.bookId, it.id)) }
        val component = if (request.targetKind == "COMPONENT") lexiDao.componentById(request.bookId, request.targetId) else null
        if (word == null && component == null) return@withLock false
        val before = word?.ebbinghausStage ?: component!!.ebbinghausStage
        val last = word?.lastReviewedAt ?: component!!.lastReviewedAt
        val due = word?.nextReviewAt ?: component!!.nextReviewAt
        val isRecall = request.mode in RecallTools.modes
        val isCloze = request.mode == "EXAM_CLOZE"
        if (isRecall) require(word != null && RecallTools.canTest(word) && request.hints.isEmpty() && request.attempt == 1) { "检验题目或作答状态无效" }
        if (request.mode.startsWith("EXAM_")) require(PracticePlanner.examBook(request.bookId)) { "请先切换到四级或六级词书" }
        if (isCloze) require(word != null && RecallTools.canTest(word) && PracticePlanner.cloze(word) != null && request.hints == "CONTEXT" && request.attempt == 1) { "语境题目或作答状态无效" }
        val correct = if (isRecall || isCloze) request.answer.trim().equals(word!!.spell, ignoreCase = true) else request.correct
        val changes = !correct || ReviewScheduler.canAdvance(last, due, request.answeredAt)
        val after = if (changes) ReviewScheduler.nextStage(before, correct) else before
        val previous = lexiDao.lastEvent(request.bookId, request.targetId, request.targetKind)
        val retry = previous?.takeIf { !it.correct && request.answeredAt >= it.answeredAt && request.answeredAt < due }?.id
        val event = ReviewEvent(request.id, request.sessionId, request.bookId, request.targetId,
            word?.spell ?: component!!.name, request.targetKind, request.mode,
            if (isCloze) PracticePlanner.cloze(word!!)!! else if (isRecall) RecallTools.prompt(word!!.chineseMeaning) else word?.chineseMeaning ?: component!!.literalMeaning, request.answer, correct, request.hints,
            request.attempt, request.answeredAt, request.elapsedMs.coerceIn(0, 86400000), before, after, changes, retry,
            firstLearning = if (word != null) last == 0L else null,
            dueReview = if (word != null) ReviewScheduler.isDue(last, due, request.answeredAt) else null)
        lexiDao.commitAnswer(event, if (changes) ReviewScheduler.nextReviewAt(after, request.answeredAt) else due)
    }

    val allComponents: Flow<List<AffixComponent>> = combine(lexiDao.getAllComponents(), lexiDao.getAllWords()) { components, words ->
        val referenced = words.flatMap { listOfNotNull(it.prefixId, it.rootId, it.suffixId) }.toSet()
        components.filter { OfflineMorphology.isTrustedComponent(it) && (!it.id.startsWith("rule:") || it.id in referenced) }
    }
    val allWords: Flow<List<WordFormula>> = combine(originalWords, corrections) { words, edits -> ContentTools.applyAll(words, edits) }

    suspend fun updateComponentMastery(id: String, isMastered: Boolean): Boolean =
        updateProgress { lexiDao.updateComponentMastery(id, isMastered) }

    suspend fun updateComponentEbbinghaus(id: String, isMastered: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long): Boolean =
        updateProgress { lexiDao.updateComponentEbbinghaus(id, isMastered, lastReviewedAt, ebbinghausStage, nextReviewAt) }

    suspend fun updateWordAssembly(id: String, assembled: Boolean): Boolean =
        updateProgress { lexiDao.updateWordAssembly(id, assembled) }

    suspend fun updateWordEbbinghaus(id: String, assembled: Boolean, lastReviewedAt: Long, ebbinghausStage: Int, nextReviewAt: Long): Boolean =
        updateProgress { lexiDao.updateWordEbbinghaus(id, assembled, lastReviewedAt, ebbinghausStage, nextReviewAt) }

    private suspend fun updateProgress(block: suspend () -> Unit): Boolean {
        val generation = DataSafety.generation
        return DataSafety.mutex.withLock {
            if (DataSafety.recoveryRequired || generation != DataSafety.generation) false else { block(); true }
        }
    }

    suspend fun resetAllLearningData() {
        protectedChange { lexiDao.resetProgress() }
    }

    suspend fun replaceWordbook(components: List<AffixComponent>, words: List<WordFormula>, bookId: String? = null) {
        if (bookId == null) protectedChange { lexiDao.replaceAll(components, words) }
        else {
            val cleanWords = words.map { it.copy(bookId = "built-in") }
            val cleanComponents = components.map { it.copy(bookId = "built-in") }
            BackupFormat.validate(BackupData(bookId, cleanWords, cleanComponents, BackupPreferences("SYSTEM", true, false, false, "system")))
            protectedChange { lexiDao.installBook(bookId, cleanComponents, cleanWords) }
        }
    }

    suspend fun resetToDefaultBuiltIn() {
        replaceWordbook(BuiltInWordbook.components, BuiltInWordbook.words, "built-in")
    }

    suspend fun checkAndPrepopulate(isBuiltIn: Boolean = true) {
        DataSafety.write {
            val state = lexiDao.libraryState()
            if (state != null) {
                if (state.activeBookId == "built-in" && lexiDao.wordSnapshot().isEmpty())
                    lexiDao.installBuiltInWordbook(preserveProgress = true)
                return@write
            }
            val restored = context?.getSharedPreferences("lexiforge_prefs", 0)
                ?.getBoolean("preserve_restored_content", false) == true
            if (!restored && (isBuiltIn || (lexiDao.getAffixCount() == 0 && lexiDao.wordSnapshot().isEmpty()))) {
                lexiDao.installBuiltInWordbook(preserveProgress = true)
            }
        }
    }

    private suspend fun protectedChange(block: suspend () -> Unit) = DataSafety.write {
        context?.let { BackupRepository(it, lexiDao).createPointLocked() }
        block()
        DataSafety.generation++
    }

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lexiforge_database"
                )
                .addMigrations(LibraryMigration(context.applicationContext), DailyPlanMigration(context.applicationContext), DailyPlanMigration(context.applicationContext, 5))
                .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDatabase() = synchronized(this) {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}
