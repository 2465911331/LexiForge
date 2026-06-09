package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AffixComponent
import com.example.data.AppDatabase
import com.example.data.LexiRepository
import com.example.data.WordFormula
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
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

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class KyleBingTranslation(
    val type: String?,
    val translation: String?
)

@com.squareup.moshi.JsonClass(generateAdapter = true)
data class KyleBingWord(
    val word: String?,
    val translations: List<KyleBingTranslation>?
)

class LexiViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application

    private val repository: LexiRepository

    // Database flow streams
    val allComponents: StateFlow<List<AffixComponent>>
    val allWords: StateFlow<List<WordFormula>>

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

    private val _isSecondDaySimulationActive = MutableStateFlow(false)
    val isSecondDaySimulationActive: StateFlow<Boolean> = _isSecondDaySimulationActive.asStateFlow()

    // Popup state to trigger the Dialog in UI
    private val _showEbbinghausUnlockDialog = MutableStateFlow(false)
    val showEbbinghausUnlockDialog: StateFlow<Boolean> = _showEbbinghausUnlockDialog.asStateFlow()

    private val _reviewFrequencyPref = MutableStateFlow("全部")
    val reviewFrequencyPref: StateFlow<String> = _reviewFrequencyPref.asStateFlow()

    fun setReviewFrequencyPref(pref: String) {
        _reviewFrequencyPref.value = pref
    }

    private var isBootInitialized = false

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

    // Vocabulary Book Download and Selected Book states
    private val _wordbookStatus = MutableStateFlow<WordbookDownloadState>(WordbookDownloadState.Idle)
    val wordbookStatus: StateFlow<WordbookDownloadState> = _wordbookStatus.asStateFlow()

    private val _currentWordbookId = MutableStateFlow("built-in")
    val currentWordbookId: StateFlow<String> = _currentWordbookId.asStateFlow()

    init {
        val database = LexiRepository.getDatabase(application)
        repository = LexiRepository(database.lexiDao())

        val pPrefs = application.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        _themeMode.value = pPrefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
        _dynamicColor.value = pPrefs.getBoolean("dynamic_color", true)
        _currentWordbookId.value = pPrefs.getString("current_wordbook_id", "built-in") ?: "built-in"

        allComponents = repository.allComponents
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        allWords = repository.allWords
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Combine filter streams to produce real-time dynamic Ebbinghaus word queue
        filteredWords = combine(
            allWords,
            _isEbbinghausFilterActive,
            _isSecondDaySimulationActive
        ) { words, isEbbinghaus, isSim ->
            if (isEbbinghaus) {
                // threshold time is current time + optional 24-hr simulation time shift
                val threshold = System.currentTimeMillis() + if (isSim) (24 * 3600 * 1000L) else 0L
                val due = words.filter { it.ebbinghausStage > 0 && it.nextReviewAt <= threshold }
                if (due.isEmpty()) {
                    // Fallback to begun words so list is not totally empty
                    words.filter { it.ebbinghausStage > 0 }
                } else {
                    due
                }
            } else {
                words
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        filteredComponents = combine(
            allComponents,
            _isEbbinghausFilterActive,
            _isSecondDaySimulationActive
        ) { components, isEbbinghaus, isSim ->
            if (isEbbinghaus) {
                val threshold = System.currentTimeMillis() + if (isSim) (24 * 3600 * 1000L) else 0L
                val due = components.filter { it.ebbinghausStage > 0 && it.nextReviewAt <= threshold }
                if (due.isEmpty()) {
                    components.filter { it.ebbinghausStage > 0 }
                } else {
                    due
                }
            } else {
                components
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        noonBlitzComponents = combine(
            allComponents,
            allWords,
            _isEbbinghausFilterActive,
            _isSecondDaySimulationActive
        ) { components, words, isEbbinghaus, isSim ->
            val rawList = if (isEbbinghaus) {
                val threshold = System.currentTimeMillis() + if (isSim) (24 * 3600 * 1000L) else 0L
                val due = components.filter { it.ebbinghausStage > 0 && it.nextReviewAt <= threshold }
                if (due.isEmpty()) {
                    components.filter { it.ebbinghausStage > 0 }
                } else {
                    due
                }
            } else {
                components
            }

            // Exclude components that have been reviewed today
            // Also exclude components whose anchorWord matches a word successfully studied/assembled today
            val studiedWordSpells = words.filter { word ->
                isReviewedToday(word.lastReviewedAt, isSim)
            }.map { it.spell.lowercase().trim() }.toSet()

            rawList.filter { comp ->
                val compReviewedToday = isReviewedToday(comp.lastReviewedAt, isSim)
                val anchorClean = comp.anchorWord.substringBefore("(").substringBefore(" ").trim().lowercase()
                val anchorStudiedToday = studiedWordSpells.contains(anchorClean)
                !compReviewedToday && !anchorStudiedToday
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        eveningSiegeWords = combine(
            allWords,
            _isEbbinghausFilterActive,
            _isSecondDaySimulationActive,
            _reviewFrequencyPref
        ) { words, isEbbinghaus, isSim, freqPref ->
            val rawList = if (isEbbinghaus) {
                val threshold = System.currentTimeMillis() + if (isSim) (24 * 3600 * 1000L) else 0L
                val due = words.filter { it.ebbinghausStage > 0 && it.nextReviewAt <= threshold }
                if (due.isEmpty()) {
                    words.filter { it.ebbinghausStage > 0 }
                } else {
                    due
                }
            } else {
                words
            }

            // Apply frequency filter
            val frequencyFiltered = if (freqPref != "全部") {
                rawList.filter { it.frequencyGroup == freqPref }
            } else {
                rawList
            }

            // Exclude words reviewed/studied today
            frequencyFiltered.filter { word ->
                !isReviewedToday(word.lastReviewedAt, isSim)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Initial launch and second-day auto-activation detection
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            updateOperationalTimeMode()

            val sharedPrefs = application.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
            val firstLaunchTime = sharedPrefs.getLong("first_launch_time", 0L)
            val nowTime = System.currentTimeMillis()
            if (firstLaunchTime == 0L) {
                // Fresh deployment, mark current timestamp as first launch
                sharedPrefs.edit().putLong("first_launch_time", nowTime).apply()
            }

            // Start observing words for target Ebbinghaus trigger logic
            allWords.collect { words ->
                if (words.isNotEmpty()) {
                    val count = words.count { it.isAssembled }
                    val decisionMade = sharedPrefs.getBoolean("ebbinghaus_pref_decision_made", false)
                    val defaultEnabled = sharedPrefs.getBoolean("ebbinghaus_pref_default_enabled", false)

                    if (count < 20) {
                        _isEbbinghausFilterActive.value = false
                    } else {
                        if (!isBootInitialized) {
                            isBootInitialized = true
                            if (decisionMade) {
                                _isEbbinghausFilterActive.value = defaultEnabled
                            } else {
                                _showEbbinghausUnlockDialog.value = true
                                _isEbbinghausFilterActive.value = false
                            }
                        } else {
                            if (!decisionMade && !_showEbbinghausUnlockDialog.value) {
                                _showEbbinghausUnlockDialog.value = true
                            }
                        }
                    }
                }
            }
        }
    }

    // Secondary window navigation controller
    fun navigateToSubScreen(screen: String, resetState: Boolean = true) {
        _activeSubScreen.value = screen
        // Reset assembly selections when navigating to keep state pristine
        if (resetState && (screen == "ASSEMBLY" || screen == "EVENING" || screen == "NONE")) {
            selectWordForAssembly(null)
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
            val nowHour = LocalTime.now().hour
            val nowMinute = LocalTime.now().minute
            val totalMinutes = nowHour * 60 + nowMinute
            // Noon Lightning defined as 11:00 AM (660 mins) to 2:30 PM (870 mins)
            val isNoonHour = totalMinutes in 660..870
            _currentActiveMode.value = if (isNoonHour) "NOON" else "EVENING"
        } else {
            _currentActiveMode.value = pref
        }
    }

    // Ebbinghaus Stage Intervals Table (represented in minutes)
    fun getNextEbbinghausTime(stage: Int, baseTimeMs: Long): Long {
        val minutes = when (stage) {
            0 -> 5         // Stage 1: 5 minutes
            1 -> 30        // Stage 2: 30 minutes
            2 -> 720       // Stage 3: 12 hours (720 min)
            3 -> 1440      // Stage 4: 1 day (1440 min)
            4 -> 2880      // Stage 5: 2 days (2880 min)
            5 -> 5760      // Stage 6: 4 days (5760 min)
            6 -> 10080     // Stage 7: 7 days (10080 min)
            else -> 21600  // Stage 8: 15 days (21600 min)
        }
        return baseTimeMs + (minutes * 60 * 1000L)
    }

    // Toggles for Ebbinghaus reviewed wordlists
    fun setEbbinghausFilterActive(active: Boolean) {
        val count = allWords.value.count { it.isAssembled }
        if (count >= 20) {
            _isEbbinghausFilterActive.value = active
        } else {
            _isEbbinghausFilterActive.value = false
        }
    }

    fun handleEbbinghausDecision(agreedToEnable: Boolean) {
        val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        sharedPrefs.edit()
            .putBoolean("ebbinghaus_pref_decision_made", true)
            .putBoolean("ebbinghaus_pref_default_enabled", agreedToEnable)
            .apply()
        _isEbbinghausFilterActive.value = agreedToEnable
        _showEbbinghausUnlockDialog.value = false
    }

    fun toggleSecondDaySimulation() {
        val newSimState = !_isSecondDaySimulationActive.value
        _isSecondDaySimulationActive.value = newSimState
        if (newSimState) {
            val count = allWords.value.count { it.isAssembled }
            if (count >= 20) {
                _isEbbinghausFilterActive.value = true
            }
        }
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

    // Component Learning Swiping Actions
    fun swipeRight(componentId: String) {
        viewModelScope.launch {
            val comp = allComponents.value.find { it.id == componentId }
            val currentStage = comp?.ebbinghausStage ?: 0
            val nextStage = (currentStage + 1).coerceAtMost(8)
            val now = System.currentTimeMillis()
            val nextReview = getNextEbbinghausTime(nextStage, now)

            repository.updateComponentEbbinghaus(
                id = componentId,
                isMastered = true,
                lastReviewedAt = now,
                ebbinghausStage = nextStage,
                nextReviewAt = nextReview
            )

            _swipeDeckIndex.value += 1
            _showAnchorId.value = null
        }
    }

    fun swipeLeft(componentId: String) {
        viewModelScope.launch {
            val comp = allComponents.value.find { it.id == componentId }
            val currentStage = comp?.ebbinghausStage ?: 0
            // Forget and reset to lower memory retention anchor
            val nextStage = if (currentStage > 1) 1 else 0
            val now = System.currentTimeMillis()
            val nextReview = getNextEbbinghausTime(nextStage, now)

            repository.updateComponentEbbinghaus(
                id = componentId,
                isMastered = false,
                lastReviewedAt = now,
                ebbinghausStage = nextStage,
                nextReviewAt = nextReview
            )

            _shakeTrigger.value += 1
            _swipeDeckIndex.value += 1
            _showAnchorId.value = null
        }
    }

    fun resetDeck() {
        _swipeDeckIndex.value = 0
    }

    fun showAnchor(componentId: String?) {
        _showAnchorId.value = componentId
    }

    // Word Analysis & Physical Assembly Actions
    fun selectWordForAssembly(word: WordFormula?) {
        _selectedWordId.value = word?.id
        _userPrefixSelection.value = null
        _userRootSelection.value = null
        _userSuffixSelection.value = null
        _assemblySuccess.value = false
    }

    fun validateSpelling(spellInput: String) {
        val wordId = _selectedWordId.value ?: return
        val word = allWords.value.find { it.id == wordId } ?: return

        if (spellInput.trim().equals(word.spell, ignoreCase = true)) {
            _assemblySuccess.value = true
            val now = System.currentTimeMillis()
            viewModelScope.launch {
                val currentStage = word.ebbinghausStage
                val nextStage = (currentStage + 1).coerceAtMost(8)
                val nextReview = getNextEbbinghausTime(nextStage, now)

                repository.updateWordEbbinghaus(
                    id = wordId,
                    assembled = true,
                    lastReviewedAt = now,
                    ebbinghausStage = nextStage,
                    nextReviewAt = nextReview
                )
            }
        } else {
            viewModelScope.launch {
                _shakeTrigger.value += 1
                val now = System.currentTimeMillis()
                val currentStage = word.ebbinghausStage
                val nextStage = if (currentStage > 1) 1 else 0
                val nextReview = getNextEbbinghausTime(nextStage, now)

                repository.updateWordEbbinghaus(
                    id = wordId,
                    assembled = false,
                    lastReviewedAt = now,
                    ebbinghausStage = nextStage,
                    nextReviewAt = nextReview
                )
            }
        }
    }

    fun choosePrefix(prefix: AffixComponent?) {
        _userPrefixSelection.value = prefix
        checkAssemblyComplete()
    }

    fun chooseRoot(root: AffixComponent?) {
        _userRootSelection.value = root
        checkAssemblyComplete()
    }

    fun chooseSuffix(suffix: AffixComponent?) {
        _userSuffixSelection.value = suffix
        checkAssemblyComplete()
    }

    private fun checkAssemblyComplete() {
        val wordId = _selectedWordId.value ?: return
        val word = allWords.value.find { it.id == wordId } ?: return

        val prefixSatisfied = word.prefixId == null || _userPrefixSelection.value?.id == word.prefixId
        val rootSatisfied = word.rootId == null || _userRootSelection.value?.id == word.rootId
        val suffixSatisfied = word.suffixId == null || _userSuffixSelection.value?.id == word.suffixId

        val prefixSelected = if (word.prefixId != null) _userPrefixSelection.value != null else true
        val rootSelected = if (word.rootId != null) _userRootSelection.value != null else true
        val suffixSelected = if (word.suffixId != null) _userSuffixSelection.value != null else true

        if (prefixSelected && rootSelected && suffixSelected) {
            val now = System.currentTimeMillis()
            if (prefixSatisfied && rootSatisfied && suffixSatisfied) {
                // Correctly assembled
                _assemblySuccess.value = true
                viewModelScope.launch {
                    val currentStage = word.ebbinghausStage
                    val nextStage = (currentStage + 1).coerceAtMost(8)
                    val nextReview = getNextEbbinghausTime(nextStage, now)

                    repository.updateWordEbbinghaus(
                        id = wordId,
                        assembled = true,
                        lastReviewedAt = now,
                        ebbinghausStage = nextStage,
                        nextReviewAt = nextReview
                    )
                }
            } else {
                // Incorrect assembly wiggles and penalizes memory stability
                viewModelScope.launch {
                    _shakeTrigger.value += 1
                    val currentStage = word.ebbinghausStage
                    val nextStage = if (currentStage > 1) 1 else 0
                    val nextReview = getNextEbbinghausTime(nextStage, now)

                    repository.updateWordEbbinghaus(
                        id = wordId,
                        assembled = false,
                        lastReviewedAt = now,
                        ebbinghausStage = nextStage,
                        nextReviewAt = nextReview
                    )

                    // Reset wrong blocks so user can easily retry
                    if (!prefixSatisfied) _userPrefixSelection.value = null
                    if (!rootSatisfied) _userRootSelection.value = null
                    if (!suffixSatisfied) _userSuffixSelection.value = null
                }
            }
        }
    }

    fun resetSelectedDisassembly(wordId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.updateWordEbbinghaus(
                id = wordId,
                assembled = false,
                lastReviewedAt = now,
                ebbinghausStage = 0,
                nextReviewAt = 0L
            )
        }
    }

    // Reset whole learning database to clean defaults
    fun resetAllLearningProgress() {
        viewModelScope.launch {
            repository.resetAllLearningData()

            _swipeDeckIndex.value = 0
            _selectedWordId.value = null
            _userPrefixSelection.value = null
            _userRootSelection.value = null
            _userSuffixSelection.value = null
            _assemblySuccess.value = false
            _isEbbinghausFilterActive.value = false
            _isSecondDaySimulationActive.value = false
            _showEbbinghausUnlockDialog.value = false
            isBootInitialized = false

            val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
            sharedPrefs.edit()
                .remove("ebbinghaus_pref_decision_made")
                .remove("ebbinghaus_pref_default_enabled")
                .apply()
        }
    }

    private fun isReviewedToday(lastReviewedAt: Long, isSim: Boolean): Boolean {
        if (lastReviewedAt == 0L) return false
        return try {
            val zone = java.time.ZoneId.systemDefault()
            val reviewedDate = java.time.Instant.ofEpochMilli(lastReviewedAt).atZone(zone).toLocalDate()
            val physicalToday = java.time.LocalDate.now(zone)
            val simulatedToday = if (isSim) physicalToday.plusDays(1) else physicalToday
            reviewedDate == physicalToday || reviewedDate == simulatedToday
        } catch (e: Exception) {
            false
        }
    }

    fun loadBuiltInWordbook() {
        viewModelScope.launch {
            _wordbookStatus.value = WordbookDownloadState.Loading("正在清空、重塑装载系统原装 Premium 优质拼装词单...")
            try {
                repository.resetToDefaultBuiltIn()
                val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit().putString("current_wordbook_id", "built-in").apply()
                _currentWordbookId.value = "built-in"
                
                // Clear state
                _swipeDeckIndex.value = 0
                _selectedWordId.value = null
                _userPrefixSelection.value = null
                _userRootSelection.value = null
                _userSuffixSelection.value = null
                _assemblySuccess.value = false

                _wordbookStatus.value = WordbookDownloadState.Success("🎉 已成功恢复并加载内置 Premium 纯正学术拼装词库！")
            } catch (e: Exception) {
                _wordbookStatus.value = WordbookDownloadState.Error("重置原装词库失败: ${e.localizedMessage}")
            }
        }
    }

    fun loadKyleBingWordbook(bookId: String, bookLabel: String, url: String) {
        viewModelScope.launch {
            _wordbookStatus.value = WordbookDownloadState.Loading("正在从 GitHub 远程安全连接并下载「${bookLabel}」词库文件...")
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
                        response.body?.string()
                    }
                }
                
                if (jsonStr.isNullOrEmpty()) {
                    throw java.io.IOException("下载的词库内容为空")
                }
                
                _wordbookStatus.value = WordbookDownloadState.Loading("下载成功，正在进行智能多态词根自适应分拆分析与拓扑配方生成...")
                
                val moshi = Moshi.Builder()
                    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                val itemType = Types.newParameterizedType(List::class.java, KyleBingWord::class.java)
                val adapter = moshi.adapter<List<KyleBingWord>>(itemType)
                val rawList = adapter.fromJson(jsonStr) ?: emptyList()
                
                if (rawList.isEmpty()) {
                    throw java.io.IOException("解析后词汇记录数为 0")
                }
                
                // For accurate vocabulary analysis, we process all words instead of capping at 800.
                // We split them into frequencies: High ~ 2500, Mid ~ 1500, Low for rest.
                
                // Ensure standard components are loaded first in case database count was altered
                repository.checkAndPrepopulate()
                
                // Collect existing pure components (excluding previous virtual ones starting with root_v_)
                val standardComponents = repository.allComponents.first().filter { !it.id.startsWith("root_v_") }
                
                val newWords = mutableListOf<WordFormula>()
                
                var highCount = 0
                var midCount = 0
                var lowCount = 0
                
                rawList.forEachIndexed { index, item ->
                    val w = item.word?.trim() ?: return@forEachIndexed
                    val m = item.translations?.joinToString("; ") { "${it.type ?: ""}. ${it.translation ?: ""}".trim() } ?: ""
                    if (w.isEmpty() || m.isEmpty()) return@forEachIndexed
                    
                    var formula = analyzeMorphology(w, m)
                    
                    val freqGroup = when {
                        index < 2500 -> {
                            highCount++
                            "高频词"
                        }
                        index < 4000 -> {
                            midCount++
                            "中频词"
                        }
                        else -> {
                            lowCount++
                            "低频词"
                        }
                    }
                    
                    formula = formula.copy(frequencyGroup = freqGroup)
                    newWords.add(formula)
                }
                
                // Extract missing dynamic components
                val mergedComponents = standardComponents.toMutableList()
                val existingIds = mergedComponents.map { it.id }.toSet()
                
                val addedDynamicIds = mutableSetOf<String>()
                
                newWords.forEach { w ->
                    if (w.prefixId != null && !existingIds.contains(w.prefixId) && !addedDynamicIds.contains(w.prefixId)) {
                        mergedComponents.add(
                            AffixComponent(
                                id = w.prefixId,
                                name = w.prefixId.removePrefix("pref_") + "-",
                                type = "PREFIX",
                                icon = "🔄",
                                literalMeaning = w.prefixPart?.substringAfter("- ") ?: "前缀",
                                anchorWord = w.spell,
                                anchorDesc = "由云端自适应算力智能切割生成"
                            )
                        )
                        addedDynamicIds.add(w.prefixId)
                    }
                    if (w.rootId != null && !existingIds.contains(w.rootId) && !addedDynamicIds.contains(w.rootId)) {
                        mergedComponents.add(
                            AffixComponent(
                                id = w.rootId,
                                name = w.rootId.removePrefix("root_"),
                                type = "ROOT",
                                icon = "⚙️",
                                literalMeaning = w.rootPart ?: "核心词根",
                                anchorWord = w.spell,
                                anchorDesc = "由云端自适应算力智能切割生成"
                            )
                        )
                        addedDynamicIds.add(w.rootId)
                    }
                    if (w.suffixId != null && !existingIds.contains(w.suffixId) && !addedDynamicIds.contains(w.suffixId)) {
                        mergedComponents.add(
                            AffixComponent(
                                id = w.suffixId,
                                name = "-" + w.suffixId.removePrefix("suff_"),
                                type = "SUFFIX",
                                icon = "🔗",
                                literalMeaning = w.suffixPart?.substringAfter("- ") ?: "后缀",
                                anchorWord = w.spell,
                                anchorDesc = "由云端自适应算力智能切割生成"
                            )
                        )
                        addedDynamicIds.add(w.suffixId)
                    }
                }
                
                repository.replaceWordbook(mergedComponents, newWords)
                
                // Save settings
                val sharedPrefs = app.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
                sharedPrefs.edit()
                    .putString("current_wordbook_id", bookId)
                    .apply()
                _currentWordbookId.value = bookId
                
                // Clear session indexes safely
                _swipeDeckIndex.value = 0
                _selectedWordId.value = null
                _userPrefixSelection.value = null
                _userRootSelection.value = null
                _userSuffixSelection.value = null
                _assemblySuccess.value = false
                
                _wordbookStatus.value = WordbookDownloadState.Success(
                    "🎉 已成功导入「${bookLabel}」系列总计 ${newWords.size} 个单词。\n📊 分析拆分：高频词 $highCount 个，中频词 $midCount 个，低频词 $lowCount 个。"
                )
            } catch (e: Exception) {
                _wordbookStatus.value = WordbookDownloadState.Error("连线分析失败: ${e.localizedMessage}。请稍后重试或检查极速网络。")
            }
        }
    }

    private fun analyzeMorphology(word: String, meaning: String): WordFormula {
        val cleanWord = word.trim().lowercase()
        
        var prefixId: String? = null
        var prefixPart: String? = null
        var rootId: String? = null
        var rootPart: String? = null
        var suffixId: String? = null
        var suffixPart: String? = null
        
        val knownPrefixes = listOf(
            Triple("pref_inter", "inter", "inter- (在...之间)"),
            Triple("pref_trans", "trans", "trans- (横穿、跨越)"),
            Triple("pref_pre", "pre", "pre- (在...之前)"),
            Triple("pref_dis", "dis", "dis- (相反、否定)"),
            Triple("pref_sub", "sub", "sub- (在...之下)"),
            Triple("pref_re", "re", "re- (往回、再次)"),
            Triple("pref_pro", "pro", "pro- (向前)"),
            Triple("pref_de", "de", "de- (向下、分离)"),
            Triple("pref_co", "con", "con- (共同、一起)"),
            Triple("pref_co", "com", "com- (共同、一起)"),
            Triple("pref_co", "co", "co- (共同、一起)"),
            Triple("pref_im", "im", "im- (向内 / 否定)"),
            Triple("pref_im", "in", "in- (向内 / 否定)"),
            Triple("pref_ad", "ad", "ad- (去、往)"),
            Triple("pref_ad", "at", "at- (去、往)"),
            Triple("pref_ad", "ac", "ac- (去、往)"),
            Triple("pref_ex", "ex", "ex- (向外、出)"),
            Triple("pref_ex", "e", "e- (向外、出)"),
            Triple("pref_un", "un", "un- (不、非)"),
            Triple("pref_en", "en", "en- (使...)"),
            Triple("pref_em", "em", "em- (使...)"),
            Triple("pref_ab", "ab", "ab- (离去、相反)"),
            Triple("pref_ob", "ob", "ob- (反对、离开)"),
            Triple("pref_per", "per", "per- (贯穿、完全)"),
            Triple("pref_sur", "sur", "sur- (超过)"),
            Triple("pref_out", "out", "out- (超过、向外)"),
            Triple("pref_over", "over", "over- (过度、在...之上)")
        )
        
        var wordRemaining = cleanWord
        
        for (p in knownPrefixes) {
            if (cleanWord.startsWith(p.second) && cleanWord.length >= p.second.length + 2) {
                prefixId = p.first
                prefixPart = p.third
                wordRemaining = cleanWord.substring(p.second.length)
                break
            }
        }
        
        val knownSuffixes = listOf(
            Triple("suff_ion", "tion", "-tion (名词后缀，表动作/状态)"),
            Triple("suff_ion", "sion", "-sion (名词后缀，表动作/状态)"),
            Triple("suff_ion", "ion", "-ion (名词后缀，表动作/状态)"),
            Triple("suff_able", "able", "-able (形容词后缀，能...的)"),
            Triple("suff_able", "ible", "-ible (形容词后缀，能...的)"),
            Triple("suff_ive", "ive", "-ive (形容词后缀，表倾向/属性)"),
            Triple("suff_ment", "ment", "-ment (名词后缀，表结果/手段)"),
            Triple("suff_ity", "ity", "-ity (名词后缀，表性质/状态)"),
            Triple("suff_ly", "ly", "-ly (副词/形容词后缀)"),
            Triple("suff_al", "al", "-al (形容词/名词后缀)"),
            Triple("suff_ous", "ous", "-ous (形容词后缀)"),
            Triple("suff_ate", "ate", "-ate (动词/形容词后缀)"),
            Triple("suff_ic", "ic", "-ic (形容词后缀)"),
            Triple("suff_ism", "ism", "-ism (名词后缀，主义/学说)"),
            Triple("suff_ist", "ist", "-ist (名词后缀，从事...的人)"),
            Triple("suff_less", "less", "-less (形容词后缀，无...)"),
            Triple("suff_ness", "ness", "-ness (名词后缀，状态)"),
            Triple("suff_ful", "ful", "-ful (形容词后缀，充满...的)"),
            Triple("suff_ship", "ship", "-ship (名词后缀，关系/状态)")
        )
        
        for (s in knownSuffixes) {
            if (wordRemaining.endsWith(s.second) && wordRemaining.length >= s.second.length + 2) {
                suffixId = s.first
                suffixPart = s.third
                wordRemaining = wordRemaining.substring(0, wordRemaining.length - s.second.length)
                break
            }
        }
        
        val knownRoots = listOf(
            Triple("root_tract", "tract", "tract (拉、引)"),
            Triple("root_spect", "spect", "spect (看、视)"),
            Triple("root_port", "port", "port (运、移)"),
            Triple("root_ject", "ject", "ject (扔、投)"),
            Triple("root_dict", "dict", "dict (说、言)"),
            Triple("root_vis", "vis", "vis (看、见)"),
            Triple("root_vis", "vid", "vid (看、见)"),
            Triple("root_mit", "mit", "mit (送、派)"),
            Triple("root_mit", "miss", "miss (送、派)"),
            Triple("root_tend", "tend", "tend (伸、拉)"),
            Triple("root_tend", "tens", "tens (伸、拉)"),
            Triple("root_cap", "cap", "cap (拿、抓)"),
            Triple("root_cap", "cept", "cept (拿、抓)"),
            Triple("root_cap", "cip", "cip (拿、抓)"),
            Triple("root_scrib", "scribe", "scribe (写、记)"),
            Triple("root_scrib", "scrib", "scrib (写、记)"),
            Triple("root_scrib", "script", "script (写、记)"),
            Triple("root_press", "press", "press (压、挤)"),
            Triple("root_struct", "struct", "struct (建造、组装)"),
            Triple("root_sens", "sens", "sens (感觉)"),
            Triple("root_sens", "sent", "sent (感觉)"),
            Triple("root_fin", "fin", "fin (界限、末尾)"),
            Triple("root_fer", "fer", "fer (带、运送)"),
            Triple("root_duc", "duct", "duct (物理引导)"),
            Triple("root_duc", "duc", "duc (物理引导)"),
            Triple("root_form", "form", "form (形式)"),
            Triple("root_act", "act", "act (行动)"),
            Triple("root_serv", "serv", "serv (服务、保持)"),
            Triple("root_ply", "ply", "ply (折、叠)"),
            Triple("root_plic", "plic", "plic (折、叠)"),
            Triple("root_volve", "volve", "volve (卷、滚)"),
            Triple("root_voc", "voc", "voc (声音、叫喊)"),
            Triple("root_voke", "voke", "voke (声音、叫喊)")
        )
        
        var foundRootId: String? = null
        var foundRootPart: String? = null
        
        for (r in knownRoots) {
            if (wordRemaining.contains(r.second)) {
                foundRootId = r.first
                foundRootPart = r.third
                break
            }
        }
        
        if (foundRootId != null) {
            rootId = foundRootId
            rootPart = foundRootPart ?: cleanWord
        } else {
            rootId = null
            rootPart = if (prefixId == null && suffixId == null) {
                cleanWord
            } else {
                "${wordRemaining} 核心"
            }
        }
        
        val formulaBuilder = StringBuilder()
        if (prefixPart != null) formulaBuilder.append("$prefixPart + ")
        formulaBuilder.append(rootPart)
        if (suffixPart != null) formulaBuilder.append(" + $suffixPart")
        if (prefixId == null && suffixId == null && foundRootId == null) {
            formulaBuilder.append(" = 基础词汇 -> $meaning")
        } else {
            formulaBuilder.append(" = 辅助拼装 -> $meaning")
        }
        val literalFormula = formulaBuilder.toString()
        
        return WordFormula(
            id = cleanWord,
            spell = word.trim(),
            prefixId = prefixId,
            rootId = rootId,
            suffixId = suffixId,
            prefixPart = prefixPart,
            rootPart = rootPart,
            suffixPart = suffixPart,
            literalFormula = literalFormula,
            chineseMeaning = meaning,
            sentenceEn = "An essential vocabulary word to remember: ${word.trim()}.",
            sentenceZh = "一个非常关键、需要牢记的核心词汇：${word.trim()}。",
            isAssembled = false,
            lastReviewedAt = 0L,
            ebbinghausStage = 0,
            nextReviewAt = 0L
        )
    }
}
