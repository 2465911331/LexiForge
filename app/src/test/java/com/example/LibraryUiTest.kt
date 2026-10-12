package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.*
import com.example.ui.LexiViewModel
import com.example.ui.screens.*
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LibraryUiTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var vm: LexiViewModel
    private var now = 1_800_000_000_000L
    private val a = "3-CET4-顺序"
    private val b = "4-CET6-顺序"
    private fun word() = BuiltInWordbook.words.single { it.spell == "sensitive" }
    private fun awaitData(condition: () -> Boolean) {
        compose.waitUntil(15000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    @Before fun setup() {
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        DataSafety.recoveryRequired = false
        LexiRepository.closeDatabase()
        app.deleteDatabase("lexiforge_database")
        db = LexiRepository.getDatabase(app)
        runBlocking {
            db.lexiDao().ensureLibrary(a, now)
            db.lexiDao().installBook(a, emptyList(), listOf(word().copy(ebbinghausStage = 3, lastReviewedAt = now - 10000, nextReviewAt = now - 1000)))
            db.lexiDao().installBook(b, emptyList(), listOf(word().copy(chineseMeaning = "adj. 灵敏的")))
            db.lexiDao().selectBook(a)
        }
        vm = LexiViewModel(app, LexiRepository(db.lexiDao(), app)) { now }
        store.put("vm", vm)
    }
    @After fun cleanup() {
        store.clear()
        LexiRepository.closeDatabase()
        app.deleteDatabase("lexiforge_database")
    }

    @Test fun `downloaded books switch offline and show independent progress`() {
        compose.setContent {
            // Keep the same subscription as the real dashboard while settings are displayed.
            val words by vm.allWords.collectAsState()
            MyApplicationTheme(darkTheme = false, dynamicColor = false) { Surface { SystemSettingsScreen(vm) } }
        }
        awaitData { vm.books.value.size == 2 && vm.currentWordbookId.value == a }
        compose.onNodeWithTag("manage_books").performScrollTo().performClick()
        compose.onNodeWithText("大学英语六级").performScrollTo().performClick()
        File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/library-books-light.png")
        compose.onNodeWithTag("prepare_replace_book").performClick()
        awaitData { vm.currentWordbookId.value == b && vm.allWords.value.singleOrNull()?.bookId == b }
        assertEquals(0, vm.allWords.value.single().ebbinghausStage)
        compose.onNodeWithTag("manage_books").performScrollTo().performClick()
        compose.onNodeWithText("大学英语四级").performScrollTo().performClick()
        compose.onNodeWithTag("prepare_replace_book").performClick()
        awaitData { vm.currentWordbookId.value == a && vm.allWords.value.singleOrNull()?.bookId == a }
        assertEquals(3, vm.allWords.value.single().ebbinghausStage)
        assertEquals(2, vm.books.value.size)
    }

    private fun toolsHome() {
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) { MainDashboard(vm) } }
        awaitData { vm.currentWordbookId.value == a && vm.allWords.value.isNotEmpty() }
        compose.onNodeWithTag("open_learning_tools").performScrollTo().performClick()
        File("build/ui-preview").mkdirs()
    }

    @Test fun personalAndExamPractice() {
        toolsHome()
        compose.onNodeWithTag("open_personal").performScrollTo().performClick()
        compose.onNodeWithTag("personal_count").assertTextContains("1 词", substring = true)
        compose.onNodeWithText("到期复习：1 词").assertExists()
        compose.onRoot().captureRoboImage("build/ui-preview/personal-review.png")
        compose.onNodeWithTag("start_personal").performScrollTo().performClick()
        compose.onNodeWithTag("recall_answer").performTextInput("sensative")
        compose.onNodeWithTag("submit_recall").performScrollTo().performClick()
        awaitData { vm.recallSession.value?.result != null }
        assertEquals("PERSONAL_RECALL", runBlocking { db.lexiDao().eventSnapshot().single().mode })
        compose.onNodeWithTag("next_recall").performScrollTo().performClick()
        compose.onNodeWithText("返回学习工具").performClick()
        compose.onNodeWithText("返回", substring = false).performClick()
        now += RecallTools.RETEST_DELAY
        compose.onNodeWithTag("open_exam").performScrollTo().performClick()
        compose.onNodeWithTag("exam_cloze").performClick()
        compose.onNodeWithTag("exam_count").assertTextContains("本轮 1 题", substring = true)
        compose.onNodeWithTag("start_exam").performScrollTo().performClick()
        compose.onNodeWithTag("recall_prompt").assertTextEquals("My skin is ____ to sunlight.")
        compose.onNodeWithText("sensitive", substring = true).assertDoesNotExist()
        compose.onNodeWithText(word().sentenceZh).assertDoesNotExist()
        compose.onRoot().captureRoboImage("build/ui-preview/exam-cloze.png")
        compose.onNodeWithTag("recall_answer").performTextInput("sensitive")
        compose.onNodeWithTag("submit_recall").performScrollTo().performClick()
        awaitData { vm.recallSession.value?.result?.correct == true }
        val events = runBlocking { db.lexiDao().eventSnapshot() }
        assertEquals("EXAM_CLOZE", events.last().mode)
        assertEquals("CONTEXT", events.last().hints)
        assertEquals(1, RecallTools.mistakes(vm.allWords.value, events).size)
        compose.onNodeWithText(word().sentenceEn).assertExists()
        compose.onNodeWithTag("next_recall").performScrollTo().performClick()
        compose.onNodeWithTag("recall_summary").assertTextContains("1 / 1", substring = true)
    }

    @Test fun familyIndependentPractice() {
        runBlocking { db.lexiDao().replaceAll(BuiltInWordbook.components, BuiltInWordbook.words.map { it.copy(lastReviewedAt = now - 10000) }) }
        toolsHome()
        compose.onNodeWithTag("open_families").performScrollTo().performClick()
        awaitData { compose.onAllNodesWithTag("family_root:root_port").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("family_root:root_port").performScrollTo().performClick()
        compose.onNodeWithTag("start_family_recall").performClick()
        compose.onNodeWithText("词族独立检验").assertExists()
        compose.onNodeWithText("export", substring = true).assertDoesNotExist()
        assertEquals(setOf("export", "import", "transport"), vm.recallSession.value!!.words.map { it.spell }.toSet())
        compose.onNodeWithTag("give_up_recall").performScrollTo().performClick()
        awaitData { vm.recallSession.value?.result != null }
        val event = runBlocking { db.lexiDao().eventSnapshot().single() }
        assertEquals("FAMILY_RECALL", event.mode)
        assertFalse(event.correct)
    }

    @Test fun recallWithoutHints() {
        toolsHome()
        compose.onNodeWithTag("start_recall").performClick()
        compose.onAllNodesWithText("sensitive", substring = true).assertCountEquals(0)
        compose.onNodeWithText("播放读音").assertDoesNotExist()
        compose.onNodeWithTag("recall_prompt").assertTextContains("敏感的", substring = true)
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/ui-preview/recall-no-hints.png")
        compose.onNodeWithTag("recall_answer").performTextInput("sensative")
        compose.onNodeWithTag("submit_recall").performScrollTo().performClick()
        awaitData { vm.recallSession.value?.result != null }
        compose.onNodeWithTag("submit_recall").assertDoesNotExist()
        compose.onNodeWithTag("recall_solution").assertTextContains("sensitive", substring = true)
        val first = runBlocking { db.lexiDao().eventSnapshot().single() }
        assertEquals("RECALL", first.mode); assertEquals("", first.hints); assertEquals(1, first.attempt)
        compose.onNodeWithTag("next_recall").performScrollTo().performClick()
        compose.onNodeWithTag("recall_summary").assertTextContains("0 / 1", substring = true)
        compose.onNodeWithText("返回学习工具").performClick()
        compose.onNodeWithTag("open_mistakes").performScrollTo().performClick()
        compose.onNodeWithText("sensitive").assertExists()
        now += RecallTools.RETEST_DELAY
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        compose.onNodeWithTag("start_error_recall").performClick()
        compose.onNodeWithTag("recall_answer").performTextInput("sensitive")
        compose.onNodeWithTag("submit_recall").performClick()
        awaitData { vm.recallSession.value?.result?.correct == true }
        compose.onNodeWithTag("next_recall").performClick()
        compose.onNodeWithText("返回学习工具").performClick()
        awaitData { vm.activeEvents.value.size == 2 }
        compose.onNodeWithText("暂无待巩固错词。新的错误会自动加入。").assertExists()
    }

    @Test fun personalCorrection() {
        toolsHome()
        compose.onNodeWithTag("open_corrections").performScrollTo().performClick()
        compose.onNodeWithTag("edited_only").performClick()
        compose.onNodeWithTag("correct_sensitive").assertDoesNotExist()
        compose.onNodeWithTag("edited_only").performClick()
        compose.onNodeWithTag("correct_sensitive").performClick()
        compose.onNodeWithTag("correction_meaning").performScrollTo().performTextReplacement("adj. 体贴他人感受的")
        compose.onNodeWithTag("correction_note").performScrollTo().performTextInput("个人参考记录")
        compose.onNodeWithTag("save_correction").performScrollTo().performClick()
        awaitData { vm.corrections.value.size == 1 && !vm.toolBusy.value }
        assertEquals(3, runBlocking { db.lexiDao().wordSnapshot().single().ebbinghausStage })
        awaitData { vm.allWords.value.single().chineseMeaning == "adj. 体贴他人感受的" }
        assertEquals(word().chineseMeaning, runBlocking { db.lexiDao().wordSnapshot().single().chineseMeaning })
        compose.onNodeWithText("返回", substring = false).performScrollTo().performClick()
        compose.onNodeWithTag("edited_only").performClick()
        compose.onNodeWithTag("correct_sensitive").assertExists().performClick()
        val file = File(app.cacheDir, "corrections.json")
        compose.runOnIdle { vm.exportCorrections(android.net.Uri.fromFile(file)) }
        awaitData { !vm.toolBusy.value && file.exists() }
        assertTrue(file.readText().contains("个人参考记录"))
        assertFalse(file.readText().contains("ebbinghausStage"))
        compose.onNodeWithTag("correction_meaning").performScrollTo()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/ui-preview/personal-correction.png")
        compose.onNodeWithTag("restore_original").performScrollTo().performClick()
        compose.onNodeWithTag("confirm_restore_original").performClick()
        awaitData { vm.corrections.value.isEmpty() && !vm.toolBusy.value }
        assertEquals(3, runBlocking { db.lexiDao().wordSnapshot().single().ebbinghausStage })
        awaitData { vm.allWords.value.single().chineseMeaning == word().chineseMeaning }
    }

    @Test fun familyNavigation() {
        runBlocking { db.lexiDao().replaceAll(BuiltInWordbook.components, BuiltInWordbook.words) }
        toolsHome()
        compose.onNodeWithTag("open_families").performScrollTo().performClick()
        awaitData { compose.onAllNodesWithTag("family_root:root_port").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("family_root:root_port").performScrollTo().performClick()
        compose.onNodeWithText("词根 port").assertExists()
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/ui-preview/word-family.png")
        compose.onAllNodesWithText("进入单词训练")[0].performScrollTo().performClick()
        awaitData { vm.activeSubScreen.value == "ASSEMBLY" }
        assertEquals("export", vm.selectedWordId.value)
        compose.onNodeWithText("返回", substring = false).performClick()
        awaitData { vm.activeSubScreen.value == "LEARNING_TOOLS" }
        compose.onNodeWithText("词根 port").assertExists()
    }

    @Test fun `daily plan saves validates counts corrections and changes day`() {
        compose.setContent { MyApplicationTheme(darkTheme = false, dynamicColor = false) { MainDashboard(vm) } }
        awaitData { vm.dailyPlan.value.bookId == a }
        compose.runOnIdle { vm.switchWordbook(b) }
        awaitData { vm.dailyPlan.value.bookId == b && vm.currentWordbookId.value == b }
        compose.onNodeWithTag("edit_daily_plan").performScrollTo().performClick()
        compose.onNodeWithTag("daily_target_input").performTextReplacement("201")
        compose.onNodeWithTag("save_daily_plan").assertIsNotEnabled()
        compose.onNodeWithTag("daily_target_input").performTextReplacement("0")
        compose.onNodeWithTag("save_daily_plan").performClick()
        awaitData { vm.dailyPlan.value.target == 0 }
        compose.onNodeWithTag("start_study").assertIsNotEnabled()
        compose.onNodeWithTag("edit_daily_plan").performScrollTo().performClick()
        compose.onNodeWithTag("daily_target_input").performTextReplacement("1")
        compose.waitForIdle()
        File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/daily-plan-editor.png")
        compose.onNodeWithTag("save_daily_plan").performClick()
        awaitData { vm.dailyPlan.value.target == 1 && vm.dailyPlan.value.queue.size == 1 }
        assertEquals(20, runBlocking { db.lexiDao().bookSnapshot().single { it.id == a }.dailyNewTarget })
        compose.onNodeWithTag("start_study").performScrollTo().performClick()
        awaitData { vm.selectedWordId.value == word().id }
        compose.onNodeWithText("拼写与构词", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("sensative")
        compose.onNodeWithText("提交拼写").performScrollTo().performClick()
        awaitData { vm.dailyPlan.value.newStarted == 1 }
        assertEquals(0, vm.dailyPlan.value.newDone)
        assertEquals(1, vm.dailyPlan.value.queue.size)
        compose.onNode(hasSetTextAction()).performTextReplacement("sensitive")
        compose.onNodeWithText("提交拼写").performClick()
        awaitData { vm.dailyPlan.value.newDone == 1 && vm.assemblySuccess.value }
        assertTrue(vm.dailyPlan.value.queue.isEmpty())
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithTag("daily_new_progress").assertTextEquals("1 / 1")
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/ui-preview/daily-plan-complete.png")
        now = DailyPlanner.day(now).end
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(2))
        awaitData { vm.dailyPlan.value.newDone == 0 && vm.dailyPlan.value.dueCount == 1 }
        compose.onNodeWithTag("start_study").assertIsEnabled()
    }

    @Test fun `spelling hint retry dedup and history are visible to learner`() {
        var history by mutableStateOf(false)
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) {
            Surface { if (history) LearningHistoryCard(vm) else EveningSiegeScreen(vm) }
        } }
        awaitData { vm.allWords.value.singleOrNull()?.bookId == a }
        compose.runOnIdle { vm.selectWordForAssembly(vm.allWords.value.single()) }
        compose.waitForIdle() // analysis is displayed before switching to the test
        compose.onNodeWithText("拼写与构词", useUnmergedTree = true).performClick()
        compose.onNode(hasSetTextAction()).performTextInput("sensative")
        compose.onNodeWithText("提交拼写").performScrollTo().performClick()
        awaitData { runBlocking { db.lexiDao().eventSnapshot().size == 1 } }
        compose.onNodeWithText("提交拼写").performClick()
        compose.runOnIdle { assertEquals(1, runBlocking { db.lexiDao().eventSnapshot().size }) }
        compose.onNode(hasSetTextAction()).performTextReplacement("sensitive")
        compose.onNodeWithText("提交拼写").performScrollTo().performClick()
        awaitData { vm.assemblySuccess.value }
        val events = runBlocking { db.lexiDao().eventSnapshot() }
        assertEquals(2, events.size)
        assertTrue(events.all { "ANALYSIS" in it.hints })
        assertEquals(events.first().id, events.last().retryOf)
        assertEquals(2, events.last().attempt)
        assertFalse(events.last().scheduleChanged)
        compose.runOnIdle { history = true }
        compose.onNodeWithTag("learning_history").performClick()
        compose.onNodeWithText("错误后的重试").assertExists()
        compose.onAllNodesWithText("看过解析").assertCountEquals(2)
        File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/library-history-dark.png")
    }
}
