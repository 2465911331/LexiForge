package com.example.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.offline.*

@Composable
fun OfflineAssistantCard(word: String, meaning: String, embedded: Boolean = BuildConfig.EMBEDDED_MODEL,
                         engine: OfflineEngine? = null) {
    if (!embedded) return
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val controller = remember(word, meaning, engine) { OfflineAssistantController(engine ?: AndroidOfflineEngine(context), scope) }
    val state by controller.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var expanded by rememberSaveable(word) { mutableStateOf(false) }
    var selected by rememberSaveable(word) { mutableStateOf(AssistantTask.EXPLAIN) }
    DisposableEffect(controller, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.stop() }
        lifecycle.addObserver(observer)
        onDispose { controller.stop(); lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth().testTag("offline_assistant_card")) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("离线学习助手 · 测试", style = MaterialTheme.typography.titleMedium)
            Text("MiniCPM5-1B · 全部在本机生成", style = MaterialTheme.typography.bodySmall)
            if (!expanded) {
                Text("解释释义、提供助记和例句草稿。首次使用需准备约 688 MB 模型；生成时会占用较多内存。", style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.testTag("open_offline_assistant")) { Text("使用离线助手") }
            } else {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AssistantTask.entries.forEach { task ->
                        FilterChip(selected = task == selected, onClick = { selected = task }, enabled = !state.busy, label = { Text(task.label) })
                    }
                }
                Text("生成内容可能有误，仅作学习草稿。不会改写词库、答案或进度。助记联想不代表真实词源。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (state.status.isNotBlank()) Text(state.status, style = MaterialTheme.typography.bodySmall)
                if (state.error.isNotBlank()) Text(state.error, color = MaterialTheme.colorScheme.error)
                if (state.text.isNotBlank()) SelectionContainer { Text(state.text, modifier = Modifier.testTag("offline_assistant_output")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.busy) {
                        OutlinedButton(onClick = controller::stop, modifier = Modifier.testTag("stop_offline_generation")) { Text("停止生成") }
                    } else {
                        Button(onClick = { controller.start(word, meaning, selected) }, modifier = Modifier.testTag("generate_offline")) { Text(if (state.text.isBlank()) "生成草稿" else "重新生成") }
                        TextButton(onClick = { controller.clear(); expanded = false }) { Text("收起") }
                    }
                }
                Text("模型：MiniCPM5-1B Q4_K_M（Apache-2.0）\n推理：llama.cpp（MIT）· 无云端 AI 调用", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
