package com.example

import android.app.Application
import androidx.compose.material3.Surface
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
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class BlockPracticeTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun close() = store.clear()

    private fun start(spell: String, meaning: String, dark: Boolean): Pair<LexiViewModel, OfflineMorphology.Entry> {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().putString("current_wordbook_id", "3-CET4-顺序").commit()
        DataSafety.recoveryRequired = false
        val word = OfflineMorphology.apply(WordFormula(spell, spell, null, null, null, null, null, null, "", meaning, "", ""), "3-CET4-顺序")
        val dao = MemoryDao()
        runBlocking { dao.replaceAll(emptyList(), listOf(word)) }
        val vm = LexiViewModel(app, LexiRepository(dao)) { 1_800_000_000_000L }
        store.put("vm", vm)
        val entry = OfflineMorphology.displayEntry(word)!!
        compose.setContent { MyApplicationTheme(darkTheme = dark, dynamicColor = false) {
            Surface { EveningSiegeScreen(vm) }
        } }
        compose.waitUntil(15_000) { vm.allWords.value.size == 1 }
        compose.runOnIdle { vm.selectWordForAssembly(vm.allWords.value.single()) }
        compose.onNodeWithText("拼写与构词", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(700)
        return vm to entry
    }

    @Test fun `shared multi layer answer is recorded once and old slots cannot auto complete it`() {
        val (vm, entry) = start("unhappiness", "n. 不快乐", false)
        compose.onNodeWithText("unhappiness", substring = false).assertDoesNotExist()
        compose.onNodeWithTag("submit_blocks").assertIsNotEnabled()
        compose.runOnIdle { vm.choosePrefix(null); vm.chooseRoot(null); vm.chooseSuffix(null) }
        assertFalse(vm.assemblySuccess.value)
        assertEquals(0L, vm.allWords.value.single().lastReviewedAt)
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/shared-block-practice-light.png")
        entry.blocks.indices.forEach { compose.onNodeWithTag("block_choice_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("submit_blocks").performScrollTo().performClick()
        compose.waitUntil(15_000) { vm.assemblySuccess.value && vm.allWords.value.single().ebbinghausStage == 1 }
        compose.runOnIdle { vm.validateBlocks(entry.blocks) }
        assertEquals(1, vm.allWords.value.single().ebbinghausStage)
        compose.onNodeWithText("分块正确，可以继续学习。").performScrollTo().assertIsDisplayed()
        compose.onRoot().captureRoboImage("build/ui-preview/shared-block-success-light.png")
        compose.runOnIdle { vm.selectWordForAssembly(vm.allWords.value.single()) }
        compose.onNodeWithTag("submit_blocks").assertIsNotEnabled()
    }

    @Test fun `repeated spelling blocks can be removed cleared retried and scored without false roots`() {
        val (vm, entry) = start("anybody", "pron. 任何人", true)
        assertEquals(SharedLearningPack.Kind.SPELLING, entry.kind)
        assertTrue(entry.blocks.distinct().size < entry.blocks.size)
        compose.onNodeWithText("这是拼写辅助练习，字母块不代表词根或音节。").assertExists()
        compose.onNodeWithTag("block_choice_0").performScrollTo().performClick()
        compose.onNodeWithTag("picked_block_0").performScrollTo().performClick()
        compose.onNodeWithTag("block_choice_0").assertIsEnabled()
        entry.blocks.indices.reversed().forEach { compose.onNodeWithTag("block_choice_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("submit_blocks").performScrollTo().performClick()
        compose.waitUntil(15_000) { vm.allWords.value.single().lastReviewedAt > 0 }
        assertFalse(vm.assemblySuccess.value)
        compose.onNodeWithText("顺序不正确，请调整后重试。").assertExists()
        compose.onNodeWithTag("clear_blocks").performScrollTo().performClick()
        compose.onNodeWithTag("submit_blocks").assertIsNotEnabled()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/spelling-block-practice-dark.png")
        entry.blocks.indices.forEach { compose.onNodeWithTag("block_choice_$it").performScrollTo().performClick() }
        compose.onNodeWithTag("submit_blocks").performScrollTo().performClick()
        compose.waitUntil(15_000) { vm.assemblySuccess.value }
        assertEquals(0, vm.allWords.value.single().ebbinghausStage)
    }

    @Test fun `spelling explanation distinguishes chunks from syllables and includes source`() {
        val word = OfflineMorphology.apply(WordFormula("remember", "remember", null, null, null, null, null, null, "", "v. 记住", "", ""), "3-CET4-顺序")
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) { Surface { DisassemblyRoom(word, true, {}, {}, false) } } }
        compose.onNodeWithText("拼写分块", substring = false).assertExists()
        compose.onNodeWithText("词根 ROOT").assertDoesNotExist()
        compose.onNodeWithText("查看资料来源（浏览器联网）").performScrollTo().assertIsDisplayed()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/spelling-explanation-dark.png")
    }

    @Test fun `license texts are accessible without launching a browser`() {
        compose.setContent { MyApplicationTheme { Surface { LearningPackCredits() } } }
        compose.onNodeWithTag("learning_pack_credits").performClick()
        compose.onNodeWithText("阅读 CMUdict 版权与许可").performScrollTo().performClick()
        compose.onNodeWithText("Copyright (C) 1993-2015 Carnegie Mellon University.", substring = true).assertExists()
        compose.onNodeWithText("返回来源").performClick()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithTag("learning_pack_credits").assertIsDisplayed()
    }
}
