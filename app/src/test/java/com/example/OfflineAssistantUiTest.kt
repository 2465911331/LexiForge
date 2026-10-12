package com.example

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.offline.OfflineEngine
import com.example.ui.screens.OfflineAssistantCard
import kotlinx.coroutines.awaitCancellation
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-mdpi")
class OfflineAssistantUiTest {
    @get:Rule val compose = createComposeRule()
    private class Engine(private val waiting: Boolean = false) : OfflineEngine {
        var calls = 0
        var cancelled = false
        override suspend fun generate(prompt: String, onStatus: (String) -> Unit, onText: (String) -> Unit) {
            calls++
            onText("例句草稿：She is happy. 她很高兴。")
            if (waiting) awaitCancellation()
        }
        override fun cancel() { cancelled = true }
    }
    private fun launch(engine: Engine, embedded: Boolean = true) {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    OfflineAssistantCard("happy", "adj. 快乐的", embedded, engine)
                }
            }
        }
    }
    @Test fun `opening card requires deliberate generation and output is a draft`() {
        val engine = Engine()
        launch(engine)
        compose.onNodeWithTag("open_offline_assistant").performClick()
        compose.runOnIdle { assertEquals(0, engine.calls) }
        compose.onNodeWithTag("generate_offline").performScrollTo().performClick()
        compose.onNodeWithTag("offline_assistant_output").assertTextContains("例句草稿：She is happy. 她很高兴。")
        compose.onNodeWithText("收起").performScrollTo().performClick()
        compose.onNodeWithTag("offline_assistant_output").assertDoesNotExist()
        compose.onNodeWithTag("open_offline_assistant").assertExists()
    }
    @Test fun `stop button calls engine and restores generation controls`() {
        val engine = Engine(waiting = true)
        launch(engine)
        compose.onNodeWithTag("open_offline_assistant").performClick()
        compose.onNodeWithTag("generate_offline").performScrollTo().performClick()
        compose.onNodeWithText("例句草稿", substring = false).assertIsNotEnabled()
        compose.onNodeWithTag("stop_offline_generation").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(engine.cancelled) }
        compose.onNodeWithTag("generate_offline").assertExists()
    }
    @Test fun `standard build does not advertise unbundled inference`() {
        launch(Engine(), embedded = false)
        compose.onNodeWithTag("offline_assistant_card").assertDoesNotExist()
    }

    @Test fun `leaving word details cancels inference`() {
        val engine = Engine(waiting = true)
        val visible = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (visible.value) OfflineAssistantCard("happy", "adj. 快乐的", true, engine)
                else Text("已离开详情")
            }
        }
        compose.onNodeWithTag("open_offline_assistant").performClick()
        compose.onNodeWithTag("generate_offline").performClick()
        compose.runOnIdle { visible.value = false }
        compose.onNodeWithText("已离开详情").assertExists()
        compose.runOnIdle { assertTrue(engine.cancelled) }
    }
}
