package com.example

import android.app.Application
import android.os.Looper
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.data.LexiRepository
import com.example.ui.LexiViewModel
import com.example.ui.screens.EveningSiegeScreen
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicLong
import java.time.Duration
import com.example.data.BuiltInWordbook
import com.example.ui.screens.DisassemblyRoom

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ReviewScreenTest {
    @get:Rule val compose = createComposeRule()
    private val store = ViewModelStore()
    @After fun clear() = store.clear()

    private fun ruleBook() = com.example.data.WordbookImporter.parse("""[
        {"word":"happy","translations":[{"type":"adj","translation":"快乐的"}]},
        {"word":"happiness","translations":[{"type":"n","translation":"幸福"}]}
    ]""", "3-CET4-顺序")

    @Test fun `rule explanation shows base and spelling change instead of historical root`() {
        val word = ruleBook().words.single { it.spell == "happiness" }
        compose.setContent { MyApplicationTheme(darkTheme = false, dynamicColor = false) {
            androidx.compose.material3.Surface { DisassemblyRoom(word, true, {}, {}, false) }
        } }
        compose.onNodeWithText("规则推导 · 拼写分块").assertExists()
        compose.onNodeWithText("词根 ROOT").assertDoesNotExist()
        compose.onNodeWithText("词基 BASE").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("happi", substring = false).assertExists()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/rule-morphology.png")
    }

    @Test fun `rule generated blocks can be selected and recorded as a real learning answer`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().putString("current_wordbook_id", "3-CET4-顺序").commit()
        com.example.data.DataSafety.recoveryRequired = false
        val book = ruleBook()
        val dao = MemoryDao()
        kotlinx.coroutines.runBlocking { dao.replaceAll(book.components, book.words) }
        val vm = LexiViewModel(app, LexiRepository(dao)) { 1_800_000_000_000L }
        store.put("vm", vm)
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) {
            androidx.compose.material3.Surface { EveningSiegeScreen(vm) }
        } }
        compose.waitUntil(15_000) { vm.allWords.value.size == 2 }
        compose.runOnIdle { vm.selectWordForAssembly(vm.allWords.value.single { it.spell == "happiness" }) }
        compose.onNodeWithText("拼写与构词", useUnmergedTree = true).performClick()
        compose.mainClock.advanceTimeBy(700)
        val target = book.words.single { it.spell == "happiness" }
        // The cards live in a horizontal LazyRow; scroll its outer vertical viewport explicitly.
        compose.onNodeWithTag("assembly_scroller").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1000f) }
        compose.onNodeWithTag("option_${target.rootId}").assertIsDisplayed()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/rule-assembly-options.png")
        compose.onNodeWithTag("option_${target.rootId}").performClick()
        compose.runOnIdle { assertNotNull("Base click must reach the slot", vm.userRootSelection.value) }
        compose.onNodeWithTag("assembly_scroller").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, 1000f) }
        compose.onNodeWithTag("option_${target.suffixId}").assertIsDisplayed().performClick()
        compose.waitUntil(15_000) { vm.assemblySuccess.value && vm.allWords.value.single { it.spell == "happiness" }.ebbinghausStage == 1 }
        compose.onNodeWithText("拼写分块正确，可以继续学习。").performScrollTo().assertIsDisplayed()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/rule-assembly-success.png")
    }

    @Test fun `unreviewed legacy segments show whole word explanation without fake roots`() {
        val old = BuiltInWordbook.words.first().copy(id = "remember", spell = "remember", chineseMeaning = "v. 记住",
            prefixPart = "re-", rootPart = "member 核心", literalFormula = "错误自动拆分")
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) {
            androidx.compose.material3.Surface { DisassemblyRoom(old, true, {}, {}, false) }
        } }
        compose.onNodeWithText("整词拼写").assertExists()
        compose.onNodeWithText("词根 ROOT").assertDoesNotExist()
        compose.onNodeWithText("错误自动拆分").assertDoesNotExist()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/offline-whole-word.png")
    }

    @Test fun `local explanation exposes provenance and usable spelling segments`() {
        compose.setContent { MyApplicationTheme(darkTheme = true, dynamicColor = false) {
            androidx.compose.material3.Surface { DisassemblyRoom(BuiltInWordbook.words.first(), true, {}, {}, false) }
        } }
        compose.onNodeWithText("本地词源提示 · 拼写分块").assertExists()
        compose.onNodeWithText("查看词源参考（浏览器联网）").performScrollTo().assertIsDisplayed()
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/offline-morphology.png")
    }

    @Test fun `last answer keeps feedback and becomes a blank attempt when due again`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        val clock = AtomicLong(1_800_000_000_000L)
        val vm = LexiViewModel(app, LexiRepository(MemoryDao()), clock::get)
        store.put("vm", vm)
        compose.setContent { MyApplicationTheme { EveningSiegeScreen(vm) } }
        compose.waitUntil(15_000) { vm.allWords.value.size == 18 }
        compose.runOnIdle {
            vm.setEbbinghausFilterActive(true)
            vm.selectWordForAssembly(vm.allWords.value.first { it.id == "sensitive" })
        }
        compose.onNodeWithText("拼写与构词", useUnmergedTree = true).performClick()
        compose.onNodeWithText("sensitive", substring = false).assertDoesNotExist()
        compose.mainClock.advanceTimeBy(700)
        java.io.File("build/ui-preview").mkdirs()
        compose.onRoot().captureRoboImage("build/ui-preview/spelling-practice.png")
        compose.onNode(hasSetTextAction()).performTextInput("sensitive")
        compose.onNodeWithText("提交拼写").performScrollTo().performClick()
        compose.waitUntil(15_000) { vm.assemblySuccess.value && vm.allWords.value.first { it.id == "sensitive" }.lastReviewedAt > 0 }
        compose.onNodeWithText("🎉 拼写正确！可以继续学习下一个单词了。").assertExists()
        assertTrue(vm.eveningSiegeWords.value.isEmpty())

        clock.addAndGet(300_000)
        // Robolectric's main looper has virtual time; advance it to deliver the clock's delayed tick.
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        compose.waitUntil(15_000) { vm.eveningSiegeWords.value.any { it.id == "sensitive" } && !vm.assemblySuccess.value }
        compose.onNode(hasSetTextAction()).assert(
            SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString(""))
        )
        compose.onNodeWithText("提交拼写").assertExists()
    }
}
