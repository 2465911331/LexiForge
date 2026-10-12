package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AffixComponent
import com.example.data.ReviewScheduler
import com.example.data.reviewClock
import com.example.data.LexiRepository
import com.example.data.WordFormula
import com.example.data.AnswerRequest
import com.example.data.DataSafety
import com.example.data.DailyPlanner
import com.example.data.DailyInputs
import com.example.data.DailyPlan
import com.example.data.ContentCorrection
import com.example.data.RecallTools
import com.example.data.PracticePlanner
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import java.util.UUID
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar


import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed class WordbookDownloadState {
    object Idle : WordbookDownloadState()
    data class Loading(val message: String) : WordbookDownloadState()
    data class Success(val message: String) : WordbookDownloadState()
    data class Error(val error: String) : WordbookDownloadState()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LexiViewModel(
    application: Application,
    private val repository: LexiRepository,
    private val currentTimeMillis: () -> Long
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        LexiRepository(LexiRepository.getDatabase(application).lexiDao(), application),
        System::currentTimeMillis
    )

    private val app = application
    private val _requiresDataReload = MutableStateFlow(false)
    val requiresDataReload: StateFlow<Boolean> = _requiresDataReload.asStateFlow()


    // Database flow streams
    val allComponents: StateFlow<List<AffixComponent>>
    val allWords: StateFlow<List<WordFormula>>
    val originalWords = repository.originalWords.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val corrections = repository.corrections.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeEvents = repository.activeEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _recallSession = MutableStateFlow<RecallSession?>(null)
    val recallSession = _recallSession.asStateFlow()
    private val _toolBusy = MutableStateFlow(false)
    val toolBusy = _toolBusy.asStateFlow()
    private val _toolMessage = MutableStateFlow<String?>(null)
    val toolMessage = _toolMessage.asStateFlow()
    private var recallStarted = 0L
    private val _editWordId = MutableStateFlow<String?>(null)
    val editWordId = _editWordId.asStateFlow()
    private var returnToTools = false
    fun practiceFamilyWord(word: com.example.data.WordFormula) {
        setReviewFrequencyPref("全部")
        setEbbinghausFilterActive(false)
        selectWordForAssembly(word)
        navigateToSubScreen("ASSEMBLY", resetState = false)
        returnToTools = true
    }
    fun openContentEditor(wordId: String) {
        _editWordId.value = wordId
        navigateToSubScreen("CONTENT_EDIT")
    }
    fun dismissToolMessage() { _toolMessage.value = null }
    fun endRecall() { _recallSession.value = null }
    fun startPersonalPractice(limit: Int, mistakesOnly: Boolean) {
        val words = allWords.value.filter { it.bookId == _currentWordbookId.value }
        beginPractice(PracticePlanner.personal(words, activeEvents.value, currentTimeMillis(), mistakesOnly).map { it.word }, "PERSONAL_RECALL", limit)
    }
    fun startExamPractice(limit: Int, cloze: Boolean) {
        beginPractice(PracticePlanner.exam(allWords.value, activeEvents.value, _currentWordbookId.value, cloze),
            if (cloze) "EXAM_CLOZE" else "EXAM_RECALL", limit)
    }
    fun startFamilyPractice(key: String) {
        val family = com.example.data.WordFamilies.build(allWords.value.filter { it.bookId == _currentWordbookId.value }).find { it.key == key }
        beginPractice(family?.words.orEmpty().filter(RecallTools::canTest), "FAMILY_RECALL", 20)
    }
    private fun beginPractice(candidates: List<WordFormula>, mode: String, limit: Int) {
        if (_toolBusy.value) return
        val words = candidates.filter { it.bookId == _currentWordbookId.value }.take(limit.coerceIn(1, 30))
        if (words.isEmpty()) { _toolMessage.value = "暂无符合条件的已学词。请先学习词条；语境填空还需要可用例句。"; return }
        _toolMessage.value = null
        _recallSession.value = RecallSession(UUID.randomUUID().toString(), words, false, DataSafety.generation, mode = mode)
        recallStarted = android.os.SystemClock.elapsedRealtime()
    }
    fun startRecall(errorsOnly: Boolean) {
        if (_toolBusy.value) return
        val source = if (errorsOnly) RecallTools.mistakes(allWords.value, activeEvents.value).map { it.word }
            else allWords.value.sortedWith(compareBy<com.example.data.WordFormula> { it.nextReviewAt }.thenBy { it.id })
        val words = source.filter { it.bookId == _currentWordbookId.value && RecallTools.canTest(it) }.take(20)
        if (words.isEmpty()) { _toolMessage.value = "暂无可检验的已学单词（需要中文释义）。"; return }
        _toolMessage.value = null
        _recallSession.value = RecallSession(UUID.randomUUID().toString(), words, errorsOnly, DataSafety.generation)
        recallStarted = android.os.SystemClock.elapsedRealtime()
    }
    fun submitRecall(answer: String) {
        val session = _recallSession.value ?: return
        if (_toolBusy.value || session.finished || session.result != null || answer.isBlank() || answer.length > 512) return
        val word = session.words[session.index]
        val correct = answer.trim().equals(word.spell, ignoreCase = true)
        _toolBusy.value = true
        viewModelScope.launch {
            try {
                val request = AnswerRequest(UUID.randomUUID().toString(), session.id, word.bookId, word.id, "WORD",
                    session.mode, answer.trim(), correct, if (session.mode == "EXAM_CLOZE") "CONTEXT" else "", 1,
                    currentTimeMillis(), android.os.SystemClock.elapsedRealtime() - recallStarted)
                check(repository.recordAnswer(request, session.generation)) { "学习数据已变化，请退出后重新检验" }
                if (_recallSession.value?.id == session.id) _recallSession.value = session.copy(
                    result = RecallResult(answer.trim(), correct), correctCount = session.correctCount + if (correct) 1 else 0)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _toolMessage.value = "未保存本次检验：${e.localizedMessage}"
            } finally { _toolBusy.value = false }
        }
    }
    fun nextRecall() {
        val session = _recallSession.value ?: return
        if (_toolBusy.value || session.result == null) return
        _recallSession.value = if (session.index == session.words.lastIndex) session.copy(finished = true)
            else session.copy(index = session.index + 1, result = null)
        recallStarted = android.os.SystemClock.elapsedRealtime()
    }
    fun saveContentCorrection(c: ContentCorrection) {
        if (_toolBusy.value) return
        _toolBusy.value = true
        viewModelScope.launch {
            try { repository.saveCorrection(c); _toolMessage.value = "个人修订已保存，学习进度保留。" }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; _toolMessage.value = "保存失败：${e.localizedMessage}" }
            finally { _toolBusy.value = false }
        }
    }
    fun removeContentCorrection(bookId: String, wordId: String) {
        if (_toolBusy.value) return
        _toolBusy.value = true
        viewModelScope.launch {
            try { repository.removeCorrection(bookId, wordId); _toolMessage.value = "已恢复词书原文，学习进度保留。" }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; _toolMessage.value = "恢复失败：${e.localizedMessage}" }
            finally { _toolBusy.value = false }
        }
    }
    fun exportCorrections(uri: android.net.Uri) {
        if (_toolBusy.value) return
        _toolBusy.value = true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { com.example.data.CorrectionExport.export(app, uri) }
                _toolMessage.value = "纠错清单已保存；未发送到网络。"
            } catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e; _toolMessage.value = "导出失败：${e.localizedMessage}" }
            finally { _toolBusy.value = false }
        }
    }

    // Core Time-Segment configuration
    // AUTO: uses current hour. NOON: noon blitz, EVENING: evening siege
    private val _timePreference = MutableStateFlow("AUTO")
    val timePreference: StateFlow<String> = _timePreference.asStateFlow()

    // Adaptive Theme configuration (SYSTEM, LIGHT, DARK)
    private val _themeMode = MutableStateFlow("SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    // Material 3 Dynamic Wallpaper Color adaptation
    private val _dynamicColor = MutableStateFlow(true)
    val dynamicColor: StateFlow<Boolean> = _dynamicColor.asStateFlow()

    // Calculated operational workflow mode
    private val _currentActiveMode = MutableStateFlow("NOON")
    val currentActiveMode: StateFlow<String> = _currentActiveMode.asStateFlow()

    // Dashboard Sub-screen Navigation (NONE = main dashboard view, SWIPE = noon blitz, ASSEMBLY = evening siege)
    private val _activeSubScreen = MutableStateFlow("NONE")
    val activeSubScreen: StateFlow<String> = _activeSubScreen.asStateFlow()

    // Ebbinghaus Memory Algorithm State variables
    private val _isEbbinghausFilterActive = MutableStateFlow(false)
    val isEbbinghausFilterActive: StateFlow<Boolean> = _isEbbinghausFilterActive.asStateFlow()

    private val _reviewFrequencyPref = MutableStateFlow("全部")
    val reviewFrequencyPref: StateFlow<String> = _reviewFrequencyPref.asStateFlow()

    fun setReviewFrequencyPref(pref: String) {
        _reviewFrequencyPref.value = pref
        selectWordForAssembly(null)
    }

    val reviewTime = reviewClock(currentTimeMillis).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(0), currentTimeMillis()
    )
    private val dailyInputs = reviewTime.map { DailyPlanner.day(it) }.distinctUntilChanged()
        .flatMapLatest { repository.dailyInputs(it.start, it.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyInputs())
    val dailyPlan = combine(dailyInputs, reviewTime) { input, now -> DailyPlanner.calculate(input, now) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DailyPlan())
    private val _dailyPlanActive = MutableStateFlow(false)
    val dailyPlanActive = _dailyPlanActive.asStateFlow()
    private val _planSaving = MutableStateFlow(false)
    val planSaving = _planSaving.asStateFlow()
    private val _planSaved = MutableStateFlow(0L)
    val planSaved = _planSaved.asStateFlow()
    private val _planError = MutableStateFlow<String?>(null)
    val planError = _planError.asStateFlow()
    fun dismissPlanError() { _planError.value = null }
    fun saveDailyTarget(target: Int) {
        if (_planSaving.value) return
        val book = _currentWordbookId.value
        _planSaving.value = true
        _planError.value = null
        viewModelScope.launch {
            try { repository.setDailyTarget(book, target); _planSaved.value++ }
            catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _planError.value = e.localizedMessage ?: "计划保存失败，请重试"
            } finally { _planSaving.value = false }
        }
    }
    fun startDailyStudy() {
        if (dailyPlan.value.bookId != _currentWordbookId.value) return
        _reviewFrequencyPref.value = "全部"
        _isEbbinghausFilterActive.value = false
        _dailyPlanActive.value = true
        selectWordForAssembly(null)
        _activeSubScreen.value = "ASSEMBLY"
    }
    private var recordingComponent = false
    private var recordingWord = false
    private data class AnswerSession(val id: String = UUID.randomUUID().toString(),
        var attempt: Int = 0, var started: Long = android.os.SystemClock.elapsedRealtime(),
        val hints: MutableSet<String> = linkedSetOf())
    private var wordSession = AnswerSession()
    private var componentSession = AnswerSession()
    private var componentKey: String? = null
    private var lastSubmission: String? = null
    private var lastComponentSubmission: String? = null
    private val _answerError = MutableStateFlow<String?>(null)
    val answerError = _answerError.asStateFlow()
    fun dismissAnswerError() { _answerError.value = null }
    fun markWordHint(hint: String) { if (hint in setOf("ANALYSIS", "AUDIO")) wordSession.hints += hint }
    fun onWordAnswerEdited() { lastSubmission = null }
    fun beginComponent(component: AffixComponent?) {
        val key = component?.let { "${it.bookId}/${it.id}" }
        if (componentKey != key) { componentKey = key; componentSession = AnswerSession() }
    }

    // Derived Ebbinghaus Adaptive lists for Word and Components
    val filteredWords: StateFlow<List<WordFormula>>
    val filteredComponents: StateFlow<List<AffixComponent>>
    val noonBlitzComponents: StateFlow<List<AffixComponent>>
    val eveningSiegeWords: StateFlow<List<WordFormula>>

    // Swipe card indexes and anchor previews
    private val _swipeDeckIndex = MutableStateFlow(0)
    val swipeDeckIndex: StateFlow<Int> = _swipeDeckIndex.asStateFlow()

    private val _showAnchorId = MutableStateFlow<String?>(null)
    val showAnchorId: StateFlow<String?> = _showAnchorId.asStateFlow()

    // Assembly game selections
    private val _selectedWordId = MutableStateFlow<String?>(null)
    val selectedWordId: StateFlow<String?> = _selectedWordId.asStateFlow()

    private val _userPrefixSelection = MutableStateFlow<AffixComponent?>(null)
    val userPrefixSelection: StateFlow<AffixComponent?> = _userPrefixSelection.asStateFlow()

    private val _userRootSelection = MutableStateFlow<AffixComponent?>(null)
    val userRootSelection: StateFlow<AffixComponent?> = _userRootSelection.asStateFlow()

    private val _userSuffixSelection = MutableStateFlow<AffixComponent?>(null)
    val userSuffixSelection: StateFlow<AffixComponent?> = _userSuffixSelection.asStateFlow()

    private val _shakeTrigger = MutableStateFlow(0)
    val shakeTrigger: StateFlow<Int> = _shakeTrigger.asStateFlow()

    private val _assemblySuccess = MutableStateFlow(false)
    val assemblySuccess: StateFlow<Boolean> = _assemblySuccess.asStateFlow()
    private val _assemblyAttempt = MutableStateFlow(0)
    val assemblyAttempt: StateFlow<Int> = _assemblyAttempt.asStateFlow()

    // Vocabulary Book Download and Selected Book states
    private val _wordbookStatus = MutableStateFlow<WordbookDownloadState>(WordbookDownloadState.Idle)
    val wordbookStatus: StateFlow<WordbookDownloadState> = _wordbookStatus.asStateFlow()

    private val _currentWordbookId = MutableStateFlow("built-in")
    val currentWordbookId: StateFlow<String> = _currentWordbookId.asStateFlow()
    val books = repository.books.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentEvents = repository.recentEvents.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val eventCount = repository.eventCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val libraryState = repository.libraryState.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    init {
        val pPrefs = application.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        _themeMode.value = pPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        _dynamicColor.value = pPrefs.getBoolean("dynamic_color", true)
        _isEbbinghausFilterActive.value = pPrefs.getBoolean("ebbinghaus_pref_default_enabled", false)
        _currentWordbookId.value = pPrefs.getString("current_wordbook_id", "built-in") ?: "built-in"

        allComponents = repository.allComponents
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allWords = repository.allWords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        filteredWords = combine(allWords, _isEbbinghausFilterActive, reviewTime) { words, dueOnly, now ->
            if (dueOnly) ReviewScheduler.words(words, now, true) else words
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

        filteredComponents = combine(allComponents, _isEbbinghausFilterActive, reviewTime) { components, dueOnly, now ->
            if (dueOnly) ReviewScheduler.components(components, now, true) else components
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

        noonBlitzComponents = combine(allComponents, _isEbbinghausFilterActive, reviewTime) { components, dueOnly, now ->
            ReviewScheduler.components(components, now, dueOnly)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

        val ordinaryQueue = combine(allWords, _isEbbinghausFilterActive, reviewTime, _reviewFrequencyPref) { words, dueOnly, now, frequency ->
            ReviewScheduler.words(words, now, dueOnly).filter {
                frequency == "全部" || it.frequencyGroup == frequency
            }
        }
        eveningSiegeWords = combine(ordinaryQueue, dailyPlan, _dailyPlanActive) { ordinary, plan, active ->
            if (active) plan.queue else ordinary
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(0), emptyList())

        viewModelScope.launch {
            repository.checkAndPrepopulate(_currentWordbookId.value == "built-in")
            updateOperationalTimeMode()
        }
        viewModelScope.launch {
            repository.libraryState.collect { state ->
                if (state != null && state.activeBookId != _currentWordbookId.value) {
                    _currentWordbookId.value = state.activeBookId
                    _recallSession.value = null
                    returnToTools = false
                    _editWordId.value = null
                    _toolMessage.value = null
                    selectWordForAssembly(null)
                    _activeSubScreen.value = "NONE"
                    _dailyPlanActive.value = false
                    componentKey = null
                    _showAnchorId.value = null
                }
            }
        }
    }

    // Secondary window navigation controller
    fun navigateToSubScreen(screen: String, resetState: Boolean = true) {
        if (screen != "LEARNING_TOOLS") _recallSession.value = null
        _dailyPlanActive.value = false
        val destination = if (screen == "NONE" && returnToTools) "LEARNING_TOOLS" else screen
        returnToTools = false
        _activeSubScreen.value = destination
        // Reset assembly selections when navigating to keep state pristine
        if (resetState && (screen == "ASSEMBLY" || screen == "EVENING" || screen == "NONE")) {
            val saved = books.value.find { it.id == _currentWordbookId.value }?.lastWordId
            selectWordForAssembly(if (screen == "NONE") null else allWords.value.find { it.id == saved })
        }
    }

    // Dual time-segmented adaptive workflow calculations
    fun setTimePreference(pref: String) {
        _timePreference.value = pref
        updateOperationalTimeMode()
    }

    fun updateOperationalTimeMode() {
        val pref = _timePreference.value
        if (pref == "AUTO") {
            val calendar = Calendar.getInstance()
            val nowHour = calendar.get(Calendar.HOUR_OF_DAY)
            val nowMinute = calendar.get(Calendar.MINUTE)
            val totalMinutes = nowHour * 60 + nowMinute
            // Noon Lightning defined as 11:00 AM (660 mins) to 2:30 PM (870 mins)
            val isNoonHour = totalMinutes in 660..870
            _currentActiveMode.value = if (isNoonHour) "NOON" else "EVENING"
        } else {
            _currentActiveMode.value = pref
        }
    }

    fun getNextEbbinghausTime(stage: Int, baseTimeMs: Long): Long =
        ReviewScheduler.nextReviewAt(stage, baseTimeMs)

    fun setEbbinghausFilterActive(active: Boolean) {
        _isEbbinghausFilterActive.value = active
        app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
            .edit().putBoolean("ebbinghaus_pref_default_enabled", active).apply()
        resetDeck()
        selectWordForAssembly(null)
    }

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putString("theme_mode", mode).apply()
    }

    fun setDynamicColor(enabled: Boolean) {
        _dynamicColor.value = enabled
        val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit().putBoolean("dynamic_color", enabled).apply()
    }

    fun swipeRight(componentId: String) = recordComponent(componentId, true)

    fun swipeLeft(componentId: String) = recordComponent(componentId, false)

    private fun recordComponent(componentId: String, correct: Boolean) {
        if (recordingComponent) return
        val comp = noonBlitzComponents.value.find { it.id == componentId } ?: return
        val submission = "${DataSafety.generation}/${comp.bookId}/${comp.id}/${comp.lastReviewedAt}"
        if (lastComponentSubmission == submission) return
        lastComponentSubmission = submission
        beginComponent(comp)
        recordingComponent = true
        val dataGeneration = com.example.data.DataSafety.generation
        val session = componentSession
        val request = AnswerRequest(UUID.randomUUID().toString(), session.id, comp.bookId, comp.id, "COMPONENT",
            "COMPONENT_CARD", if (correct) "认识" else "不认识", correct, session.hints.sorted().joinToString(","),
            session.attempt + 1, currentTimeMillis(), android.os.SystemClock.elapsedRealtime() - session.started)
        viewModelScope.launch {
            try {
                if (!repository.recordAnswer(request, dataGeneration)) { lastComponentSubmission = null; return@launch }
                session.attempt++
                session.started = android.os.SystemClock.elapsedRealtime()
                _showAnchorId.value = null
                if (!correct) _shakeTrigger.value += 1
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastComponentSubmission = null
                _answerError.value = "作答未保存：${e.localizedMessage}。请重试。"
            } finally {
                recordingComponent = false
            }
        }
    }

    fun resetDeck() {
        _swipeDeckIndex.value = 0
    }

    fun showAnchor(componentId: String?) {
        if (componentId != null) {
            beginComponent(allComponents.value.find { it.id == componentId })
            componentSession.hints += "ANCHOR"
        }
        _showAnchorId.value = componentId
    }

    // Word Analysis & Physical Assembly Actions
    fun selectWordForAssembly(word: WordFormula?) {
        wordSession = AnswerSession()
        lastSubmission = null
        _assemblyAttempt.value += 1
        _selectedWordId.value = word?.id
        _userPrefixSelection.value = null
        _userRootSelection.value = null
        _userSuffixSelection.value = null
        _assemblySuccess.value = false
        if (word != null) viewModelScope.launch {
            try { repository.rememberWord(word.bookId, word.id) }
            catch (e: Exception) { if (e is kotlinx.coroutines.CancellationException) throw e }
        }
    }

    fun validateSpelling(spellInput: String) {
        val word = allWords.value.find { it.id == _selectedWordId.value } ?: return
        if (spellInput.isBlank() || spellInput.length > 512) return
        recordWordAnswer(word, spellInput.trim().equals(word.spell, ignoreCase = true), "SPELLING", spellInput.trim())
    }

    fun validateBlocks(parts: List<String>) {
        val word = allWords.value.find { it.id == _selectedWordId.value } ?: return
        val expected = com.example.data.OfflineMorphology.displayEntry(word)?.blocks.orEmpty()
        if (expected.size < 2 || parts.size != expected.size) return
        recordWordAnswer(word, parts == expected, "BLOCKS", parts.joinToString(" | "))
    }

    private fun recordWordAnswer(word: WordFormula, correct: Boolean, mode: String, answer: String) {
        if (recordingWord || _assemblySuccess.value) return
        val submission = "$mode/$answer"
        if (lastSubmission == submission) return
        lastSubmission = submission
        recordingWord = true
        val dataGeneration = com.example.data.DataSafety.generation
        val session = wordSession
        val hints = session.hints + if (mode == "SPELLING") emptySet() else setOf("CHOICES")
        val request = AnswerRequest(UUID.randomUUID().toString(), session.id, word.bookId, word.id, "WORD",
            mode, answer, correct, hints.sorted().joinToString(","), session.attempt + 1,
            currentTimeMillis(), android.os.SystemClock.elapsedRealtime() - session.started)
        viewModelScope.launch {
            try {
                if (!repository.recordAnswer(request, dataGeneration)) { lastSubmission = null; return@launch }
                session.attempt++
                session.started = android.os.SystemClock.elapsedRealtime()
                if (session === wordSession && dataGeneration == DataSafety.generation) {
                    if (correct) _assemblySuccess.value = true else _shakeTrigger.value += 1
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                lastSubmission = null
                _answerError.value = "作答未保存：${e.localizedMessage}。请重试。"
            } finally {
                recordingWord = false
            }
        }
    }

    fun choosePrefix(prefix: AffixComponent?) {
        onWordAnswerEdited()
        _userPrefixSelection.value = prefix
        checkAssemblyComplete()
    }

    fun chooseRoot(root: AffixComponent?) {
        onWordAnswerEdited()
        _userRootSelection.value = root
        checkAssemblyComplete()
    }

    fun chooseSuffix(suffix: AffixComponent?) {
        onWordAnswerEdited()
        _userSuffixSelection.value = suffix
        checkAssemblyComplete()
    }

    private fun checkAssemblyComplete() {
        val wordId = _selectedWordId.value ?: return
        val word = allWords.value.find { it.id == wordId } ?: return
        if (!com.example.data.OfflineMorphology.canAssemble(word)) return
        if (com.example.data.OfflineMorphology.displayEntry(word)?.blocks?.isNotEmpty() == true) return

        val prefixSatisfied = word.prefixId == null || _userPrefixSelection.value?.id == word.prefixId
        val rootSatisfied = word.rootId == null || _userRootSelection.value?.id == word.rootId
        val suffixSatisfied = word.suffixId == null || _userSuffixSelection.value?.id == word.suffixId

        val prefixSelected = if (word.prefixId != null) _userPrefixSelection.value != null else true
        val rootSelected = if (word.rootId != null) _userRootSelection.value != null else true
        val suffixSelected = if (word.suffixId != null) _userSuffixSelection.value != null else true

        if (prefixSelected && rootSelected && suffixSelected) {
            recordWordAnswer(word, prefixSatisfied && rootSatisfied && suffixSatisfied, "CONSTRUCTION",
                listOfNotNull(_userPrefixSelection.value?.name, _userRootSelection.value?.name, _userSuffixSelection.value?.name).joinToString(" | "))
            if (!prefixSatisfied) _userPrefixSelection.value = null
            if (!rootSatisfied) _userRootSelection.value = null
            if (!suffixSatisfied) _userSuffixSelection.value = null
        }
    }

    // Reset whole learning database to clean defaults
    fun switchWordbook(bookId: String) {
        if (_wordbookStatus.value is WordbookDownloadState.Loading) return
        _wordbookStatus.value = WordbookDownloadState.Loading("正在切换词书…")
        viewModelScope.launch {
            try {
                repository.selectBook(bookId)
                _currentWordbookId.value = bookId
                selectWordForAssembly(null)
                _activeSubScreen.value = "NONE"
                _wordbookStatus.value = WordbookDownloadState.Success("已切换到「${wordbookLabel(bookId)}」，原词书进度已保留。")
            } catch (e: Exception) {
                _wordbookStatus.value = WordbookDownloadState.Error("切换未完成：${e.localizedMessage}")
            }
        }
    }

    fun resetAllLearningProgress() {
        viewModelScope.launch {
            _wordbookStatus.value = WordbookDownloadState.Loading("正在保存恢复点并清空进度…")
            try {
            repository.resetAllLearningData()

            _swipeDeckIndex.value = 0
            _selectedWordId.value = null
            _userPrefixSelection.value = null
            _userRootSelection.value = null
            _userSuffixSelection.value = null
            _assemblySuccess.value = false
            _isEbbinghausFilterActive.value = false

            val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .remove("ebbinghaus_pref_decision_made")
                .remove("ebbinghaus_pref_default_enabled")
                .apply()
            _wordbookStatus.value = WordbookDownloadState.Success("已清空学习进度，清空前的数据已保留在本机恢复点。")
            } catch (e: Exception) {
                _requiresDataReload.value = com.example.data.DataSafety.recoveryRequired
                _wordbookStatus.value = WordbookDownloadState.Error("未能完成清空：${e.localizedMessage}")
            }
        }
    }

    fun loadBuiltInWordbook() {
        if (_wordbookStatus.value is WordbookDownloadState.Loading) return
        _wordbookStatus.value = WordbookDownloadState.Loading("正在启用内置词书…")
        viewModelScope.launch {
            _wordbookStatus.value = WordbookDownloadState.Loading("正在恢复内置基础词书...")
            try {
                repository.resetToDefaultBuiltIn()
                val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("current_wordbook_id", "built-in").apply()
                _currentWordbookId.value = "built-in"
                _reviewFrequencyPref.value = "全部"
                
                // Clear state
                _swipeDeckIndex.value = 0
                _selectedWordId.value = null
                _userPrefixSelection.value = null
                _userRootSelection.value = null
                _userSuffixSelection.value = null
                _assemblySuccess.value = false

                _wordbookStatus.value = WordbookDownloadState.Success("已启用内置词书，其他词书与原有进度已保留。")
            } catch (e: Exception) {
                _requiresDataReload.value = com.example.data.DataSafety.recoveryRequired
                _wordbookStatus.value = WordbookDownloadState.Error("重置原装词库失败: ${e.localizedMessage}")
            }
        }
    }

    fun loadKyleBingWordbook(bookId: String, bookLabel: String, url: String) {
        if (_wordbookStatus.value is WordbookDownloadState.Loading) return
        _wordbookStatus.value = WordbookDownloadState.Loading("正在下载「$bookLabel」…")
        viewModelScope.launch {
            _wordbookStatus.value = WordbookDownloadState.Loading("正在下载「${bookLabel}」词书…")
            try {
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(url)
                    .build()
                
                val jsonStr = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw java.io.IOException("HTTP 错误: ${response.code}")
                        response.body?.byteStream()?.use(com.example.data.WordbookImporter::read)
                    }
                }
                
                if (jsonStr.isNullOrEmpty()) {
                    throw java.io.IOException("下载的词库内容为空")
                }
                
                _wordbookStatus.value = WordbookDownloadState.Loading("下载完成，正在匹配本地解析…")
                
                val imported = withContext(Dispatchers.IO) {
                    com.example.data.WordbookImporter.parse(jsonStr, bookId)
                }
                val newWords = imported.words
                repository.replaceWordbook(imported.components, newWords, bookId)
                // Save settings
                val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("current_wordbook_id", bookId)
                    .apply()
                _currentWordbookId.value = bookId
                _reviewFrequencyPref.value = "全部"
                
                // Clear session indexes safely
                _swipeDeckIndex.value = 0
                _selectedWordId.value = null
                _userPrefixSelection.value = null
                _userRootSelection.value = null
                _userSuffixSelection.value = null
                _assemblySuccess.value = false
                
                val analyses = newWords.map(com.example.data.OfflineMorphology::displayEntry)
                val spellingCount = analyses.count { it?.kind == com.example.data.SharedLearningPack.Kind.SPELLING }
                val wholeCount = analyses.count { it?.assembly != true }
                _wordbookStatus.value = WordbookDownloadState.Success(
                    "已导入「${bookLabel}」，共 ${newWords.size} 词：构词分块 ${newWords.size - spellingCount - wholeCount}，拼写分块 $spellingCount，整词拼写 $wholeCount。词频暂未分级。"
                )
            } catch (e: Exception) {
                _requiresDataReload.value = com.example.data.DataSafety.recoveryRequired
                _wordbookStatus.value = WordbookDownloadState.Error("词书导入失败：${e.localizedMessage}。请检查网络或更换下载来源后重试。")
            }
        }
    }

}
