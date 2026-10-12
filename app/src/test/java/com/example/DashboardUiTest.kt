package com.example

import android.app.Application
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.LexiRepository
import com.example.ui.LexiViewModel
import com.example.ui.screens.MainDashboard
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
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w393dp-h851dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardUiTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val store = ViewModelStore()
    private val dao = MemoryDao()
    private val clock = AtomicLong(1_800_000_000_000L)
    private lateinit var vm: LexiViewModel
    @After fun clear() = store.clear()

    private fun launch(dark: Boolean = false, fontScale: Float = 1f) {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        vm = LexiViewModel(app, LexiRepository(dao), clock::get)
        store.put("vm", vm)
        compose.setContent {
            val theme by vm.themeMode.collectAsState()
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, fontScale)) {
                MyApplicationTheme(darkTheme = if (theme == "SYSTEM") dark else theme == "DARK", dynamicColor = false) {
                    MainDashboard(vm)
                }
            }
        }
        compose.waitUntil(15_000) { vm.allWords.value.size == 18 }
    }

    private fun mark(id: String, deadline: Long) {
        compose.runOnIdle { runBlocking { dao.updateWordEbbinghaus(id, true, clock.get() - 1000, 1, deadline) } }
        compose.waitUntil(15_000) { vm.allWords.value.any { it.id == id && it.nextReviewAt == deadline } }
    }

    @Test fun `home daily plan prioritizes due words despite a stale training filter`() {
        launch()
        mark("attract", clock.get())
        mark("import", clock.get() + 300_000)
        compose.runOnIdle { vm.setReviewFrequencyPref("高频词") }
        compose.waitUntil(15_000) { vm.dailyPlan.value.dueCount == 1 }
        compose.onNodeWithTag("start_study").performClick()
        compose.waitUntil(15_000) { vm.eveningSiegeWords.value.firstOrNull()?.id == "attract" }
        assertTrue(vm.dailyPlanActive.value)
        assertEquals(17, vm.eveningSiegeWords.value.size)
        assertFalse(vm.eveningSiegeWords.value.any { it.id == "import" })
        compose.onNodeWithTag("main_navigation_bar").assertDoesNotExist()
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithTag("main_navigation_bar").assertExists()
    }

    @Test fun `list search is independent and detail training returns to the same filtered list`() {
        launch()
        compose.runOnIdle { vm.setEbbinghausFilterActive(true) }
        compose.onNodeWithTag("tab_WORD_LIST").performClick()
        compose.onNodeWithTag("word_search").performTextInput("  SENSITIVE  ")
        compose.onNodeWithTag("word_sensitive").assertExists().performClick()
        compose.onNodeWithTag("practice_word").performClick()
        compose.waitUntil(15_000) { vm.selectedWordId.value == "sensitive" }
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithTag("word_detail_back").assertExists().performClick()
        compose.onNodeWithTag("word_search").assertTextContains("  SENSITIVE  ")
        compose.onNodeWithTag("word_sensitive").assertExists()
        compose.onNodeWithTag("filter_DUE").performClick()
        compose.onNodeWithText("没有找到匹配的单词").assertExists()
    }

    @Test fun `due count updates at deadline without reopening list`() {
        launch()
        mark("attract", clock.get() + 300_000)
        compose.onNodeWithTag("tab_WORD_LIST").performClick()
        compose.onNodeWithTag("filter_DUE").performClick()
        compose.onNodeWithText("此分类暂无单词").assertExists()
        clock.addAndGet(300_000)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("word_attract").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun `reset and wordbook replacement require separate confirmation`() {
        launch()
        mark("attract", clock.get() + 300_000)
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("reset_progress_button").performScrollTo().performClick()
        compose.onNodeWithText("取消").performClick()
        assertTrue(vm.allWords.value.first { it.id == "attract" }.lastReviewedAt > 0)
        compose.onNodeWithTag("reset_progress_button").performClick()
        compose.onNodeWithTag("confirm_reset").performClick()
        compose.waitUntil(15_000) { vm.allWords.value.all { it.lastReviewedAt == 0L } }
        compose.onNodeWithTag("manage_books").performScrollTo().performClick()
        compose.onNodeWithText("大学英语四级").performClick()
        compose.onNodeWithTag("prepare_replace_book").performClick()
        compose.onNodeWithTag("confirm_replace_book").assertExists()
        assertEquals("built-in", vm.currentWordbookId.value)
        compose.onNodeWithText("返回选择").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals("built-in", vm.currentWordbookId.value)
    }

    @Test fun `capture three screens in light and dark themes`() {
        launch()
        mark("attract", clock.get())
        mark("import", clock.get() + 300_000)
        java.io.File("build/ui-preview").mkdirs()
        for (dark in listOf(false, true)) {
            compose.runOnIdle { vm.setThemeMode(if (dark) "DARK" else "LIGHT") }
            for ((tab, name) in listOf("DASHBOARD" to "today", "WORD_LIST" to "words", "SETTINGS" to "settings")) {
                compose.onNodeWithTag("tab_$tab").performClick()
                compose.mainClock.advanceTimeBy(700)
                compose.waitForIdle()
                compose.onRoot().captureRoboImage("build/ui-preview/${name}-${if (dark) "dark" else "light"}.png")
            }
        }
    }

    @Test fun `large text keeps primary action and settings reachable`() {
        launch(dark = true, fontScale = 1.5f)
        compose.onNodeWithTag("start_study").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("返回", substring = false).performClick()
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("theme_LIGHT").performScrollTo().performClick()
        compose.runOnIdle { assertEquals("LIGHT", vm.themeMode.value) }
        compose.onNodeWithTag("reset_progress_button").performScrollTo().assertIsDisplayed()
        compose.mainClock.advanceTimeBy(700)
        compose.waitForIdle()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/settings-large-text.png")
    }

    @Test fun `local speech preference persists and preview exposes missing engine instead of silent failure`() {
        launch()
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("local_speech_only").performScrollTo().performClick().assertIsOn()
        val app = ApplicationProvider.getApplicationContext<Application>()
        assertTrue(app.getSharedPreferences("lexiforge_prefs", 0).getBoolean("local_speech_only", false))
        compose.onNodeWithTag("test_local_speech").performScrollTo().performClick()
        compose.onNodeWithTag("speech_diagnostics").performScrollTo().assertTextContains("未发现 TTS 语音引擎", substring = true)
        java.io.File("build/ui-preview").mkdirs()
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("build/ui-preview/offline-speech-settings.png")
    }

    @Test fun `backup controls and empty recovery list remain reachable with large text`() {
        launch(dark = true, fontScale = 1.3f)
        compose.onNodeWithTag("tab_SETTINGS").performClick()
        compose.onNodeWithTag("export_backup").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("import_backup").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithTag("recovery_points").performScrollTo().performClick()
        compose.waitUntil(15_000) { compose.onAllNodesWithText("本机恢复点").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("export_backup").performScrollTo()
        java.io.File("build/ui-preview").mkdirs()
        compose.mainClock.advanceTimeBy(700)
        compose.onRoot().captureRoboImage("build/ui-preview/backup-settings.png")
    }
}
