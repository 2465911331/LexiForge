package com.example.ui.screens

import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.*
import com.example.ui.utils.TtsHelper

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SystemSettingsScreen(viewModel: LexiViewModel) {
    val context = LocalContext.current
    val theme by viewModel.themeMode.collectAsStateWithLifecycle()
    val dynamic by viewModel.dynamicColor.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentWordbookId.collectAsStateWithLifecycle()
    val status by viewModel.wordbookStatus.collectAsStateWithLifecycle()
    val words by viewModel.allWords.collectAsStateWithLifecycle()
    var showReset by rememberSaveable { mutableStateOf(false) }
    var showBooks by rememberSaveable { mutableStateOf(false) }

    val loading = status is WordbookDownloadState.Loading
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("设置", "管理词书、外观和学习数据")
        StudyCard("当前词书") {
            Text(wordbookLabel(currentBook), style = MaterialTheme.typography.titleLarge)
            Text("已下载词书可离线切换，各自保留进度与学习位置。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = { showBooks = true }, enabled = !loading, modifier = Modifier.fillMaxWidth().testTag("manage_books")) {
                Text(if (loading) "正在处理词书…" else "我的词书")
            }
            when (val result = status) {
                is WordbookDownloadState.Loading -> { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(result.message) }
                is WordbookDownloadState.Success -> Text(result.message, color = MaterialTheme.colorScheme.primary)
                is WordbookDownloadState.Error -> Text(result.error, color = MaterialTheme.colorScheme.error)
                else -> Unit
            }
        }
        BackupSettingsCard(enabled = !loading)
        LearningHistoryCard(viewModel)
        StudyCard("离线分块学习") {
            val analyses = remember(words) { words.map(com.example.data.OfflineMorphology::displayEntry) }
            val spellingCount = analyses.count { it?.kind == com.example.data.SharedLearningPack.Kind.SPELLING }
            val wholeCount = analyses.count { it?.assembly != true }
            Text("当前词书 ${words.size} 词：构词分块 ${words.size - spellingCount - wholeCount} · 拼写分块 $spellingCount · 整词拼写 $wholeCount")
            Text("构词分块中：示范 ${analyses.count { it?.assembly == true && it.kind == null && !it.ruleDerived }}、规则 ${analyses.count { it?.ruleDerived == true }}、构词资料 ${analyses.count { it?.kind == com.example.data.SharedLearningPack.Kind.MORPHOLOGY }}、复合词 ${analyses.count { it?.kind == com.example.data.SharedLearningPack.Kind.COMPOUND }}。资料和规则推导未经逐词核对。", style = MaterialTheme.typography.bodySmall)
            Text("解析包 ${com.example.data.OfflineMorphology.VERSION}：共享构词资料、拼写辅助和读音提示均随应用提供，无需 AI API 或模型。拼写块不计入构词覆盖率，不能视为音节或词根。", style = MaterialTheme.typography.bodySmall)
            Text("词频暂未分级。不会根据文件顺序推断高频或低频。", style = MaterialTheme.typography.bodySmall)
            LearningPackCredits()
        }
        StudyCard("外观") {
            Text("主题", style = MaterialTheme.typography.bodyLarge)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("SYSTEM" to "跟随系统", "LIGHT" to "浅色", "DARK" to "深色").forEach { (id, label) ->
                    FilterChip(selected = theme == id, onClick = { viewModel.setThemeMode(id) }, label = { Text(label) }, modifier = Modifier.testTag("theme_$id"))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text("使用壁纸配色", style = MaterialTheme.typography.bodyLarge)
                    Text(if (Build.VERSION.SDK_INT >= 31) "让应用颜色跟随系统壁纸" else "此功能需要 Android 12 或更新版本", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = dynamic && Build.VERSION.SDK_INT >= 31, onCheckedChange = viewModel::setDynamicColor, enabled = Build.VERSION.SDK_INT >= 31, modifier = Modifier.testTag("dynamic_color"))
            }
        }
        SpeechSettingsCard()
        StudyCard("学习数据") {
            Text("只重置当前词书的学习阶段与复习时间；其他词书、词书内容和历史作答保留。操作前会保存本机恢复点。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { showReset = true }, enabled = !loading,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().testTag("reset_progress_button")) { Text("重置当前词书进度") }
        }
        Text("LexiForge · 开发预览版\n本地解析覆盖有限，未匹配的单词使用整词训练。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showReset) AlertDialog(
        onDismissRequest = { showReset = false }, title = { Text("重置当前词书进度？") },
        text = { Text("将重置当前词书的学习阶段和复习时间。其他词书与历史作答保留。先保存本机恢复点；保存失败则不会重置。") },
        confirmButton = { TextButton(onClick = { showReset = false; viewModel.resetAllLearningProgress() }, modifier = Modifier.testTag("confirm_reset")) { Text("确认清空", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { showReset = false }) { Text("取消") } }
    )
    if (showBooks) WordbookPicker(viewModel, currentBook, onDismiss = { showBooks = false })
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected = selected, onClick = null)
        Text(label, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun WordbookPicker(viewModel: LexiViewModel, currentBook: String, onDismiss: () -> Unit) {
    var selection by rememberSaveable { mutableStateOf(currentBook) }
    var mirror by rememberSaveable { mutableStateOf("github") }
    var confirming by rememberSaveable { mutableStateOf(false) }
    val status by viewModel.wordbookStatus.collectAsStateWithLifecycle()
    val loading = status is WordbookDownloadState.Loading
    val books by viewModel.books.collectAsStateWithLifecycle()
    val installed = books.associateBy { it.id }
    if (confirming) {
        AlertDialog(onDismissRequest = { confirming = false }, title = { Text("添加词书？") },
            text = { Text("将下载并添加“${wordbookLabel(selection)}”，已有词书和进度保留。下载或校验失败不会替换原有数据。") },
            confirmButton = { TextButton(enabled = !loading, onClick = {
                if (selection == "built-in") viewModel.loadBuiltInWordbook() else {
                    val name = java.net.URLEncoder.encode(selection, "UTF-8").replace("+", "%20")
                    val url = when (mirror) {
                        "jsdelivr" -> "https://cdn.jsdelivr.net/gh/KyleBing/english-vocabulary@master/json/$name.json"
                        "ghproxy" -> "https://ghproxy.net/https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/json/$name.json"
                        else -> "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/json/$name.json"
                    }
                    viewModel.loadKyleBingWordbook(selection, wordbookLabel(selection), url)
                }
                onDismiss()
            }, modifier = Modifier.testTag("confirm_replace_book")) { Text("添加并使用") } },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("返回选择") } })
    } else AlertDialog(onDismissRequest = onDismiss, title = { Text("我的词书") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            (availableWordbooks + installed.keys.filter { it !in availableWordbooks }.associateWith { wordbookLabel(it) }).forEach { (id, label) ->
                ChoiceRow(label, selection == id) { selection = id }
                installed[id]?.let { Text("已下载 · ${it.wordCount} 词 · 已练习 ${it.learnedCount} 词", style = MaterialTheme.typography.bodySmall) }
            }
            Text("各词书进度独立；相同拼写的不同义项不会自动合并。", style = MaterialTheme.typography.bodySmall)
            if (selection != "built-in" && selection !in installed) {
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
                Text("下载来源", style = MaterialTheme.typography.titleSmall)
                listOf("github" to "GitHub", "jsdelivr" to "jsDelivr", "ghproxy" to "ghproxy").forEach { (id, label) -> ChoiceRow(label, mirror == id) { mirror = id } }
                Text("外部词书来自 KyleBing/english-vocabulary；导入后匹配本地解析，未匹配的单词使用整词训练。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = { TextButton(enabled = !loading, onClick = {
        if (selection in installed) { viewModel.switchWordbook(selection); onDismiss() }
        else if (selection == "built-in") { viewModel.loadBuiltInWordbook(); onDismiss() }
        else confirming = true
    }, modifier = Modifier.testTag("prepare_replace_book")) { Text(if (selection in installed) "切换词书" else "添加词书…") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
