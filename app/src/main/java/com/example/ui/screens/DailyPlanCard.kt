package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LexiViewModel
import com.example.ui.wordbookLabel

@Composable
fun DailyPlanCard(viewModel: LexiViewModel, onWords: () -> Unit, onSettings: () -> Unit) {
    val plan by viewModel.dailyPlan.collectAsStateWithLifecycle()
    val currentBook by viewModel.currentWordbookId.collectAsStateWithLifecycle()
    val saving by viewModel.planSaving.collectAsStateWithLifecycle()
    val saved by viewModel.planSaved.collectAsStateWithLifecycle()
    val error by viewModel.planError.collectAsStateWithLifecycle()
    var edit by rememberSaveable(currentBook) { mutableStateOf(false) }
    var input by rememberSaveable(currentBook) { mutableStateOf("") }
    var savedAtOpen by rememberSaveable(currentBook) { mutableLongStateOf(0L) }
    val ready = currentBook == plan.bookId
    if (!edit) StudyCard("${wordbookLabel(currentBook)} · 每日计划") {
        if (!ready) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("正在读取此词书的计划…")
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${plan.newDone} / ${plan.todayGoal}", style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("daily_new_progress"))
                    Text("今日新词已完成", style = MaterialTheme.typography.bodyMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("${plan.dueCount}", style = MaterialTheme.typography.headlineLarge)
                    Text("当前待复习", style = MaterialTheme.typography.bodyMedium)
                }
            }
            LinearProgressIndicator(progress = { if (plan.todayGoal == 0) 0f else (plan.newDone.toFloat() / plan.todayGoal).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Text("${if (plan.target == 0) "只复习" else "每日目标 ${plan.target} 个新词"} · 今日已复习 ${plan.reviewed} 个旧词",
                style = MaterialTheme.typography.bodyMedium)
            Text(when {
                plan.dueCount > 0 -> "先复习到期单词，再完成今天的新词。"
                plan.queue.isNotEmpty() -> "答对后计入新词完成数；错词继续练习，不会重复计数。"
                plan.todayGoal > 0 && plan.newDone >= plan.todayGoal -> "今日新词已完成，当前没有到期任务。"
                plan.target == 0 -> "今天只安排复习，当前没有到期任务。"
                else -> "没有可安排的新词。可浏览单词，或在设置中添加词书。"
            }, style = MaterialTheme.typography.bodyMedium)
            Button(onClick = viewModel::startDailyStudy, enabled = plan.queue.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("start_study")) {
                Text(if (plan.queue.isEmpty()) "当前计划已完成" else "开始今日学习 · ${plan.queue.size} 词")
            }
            TextButton(onClick = { input = plan.target.toString(); savedAtOpen = saved; viewModel.dismissPlanError(); edit = true },
                modifier = Modifier.fillMaxWidth().testTag("edit_daily_plan")) { Text("调整每日目标") }
            Text("按手机本地日期更新。到期任务随时间增加；自由练习中的首次学习也计入今天。词书剩余不足时按实际数量安排。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onWords) { Text("浏览单词") }
            TextButton(onClick = onSettings) { Text("管理词书") }
        }
    }
    if (edit) {
        BackHandler { if (!saving) edit = false }
        StudyCard("每日学习目标") {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("仅设置${wordbookLabel(currentBook)}，其他词书保持各自目标。")
                Column {
                    listOf(0, 10, 20, 30, 50).chunked(3).forEach { presets ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            presets.forEach { n ->
                                FilterChip(selected = input == n.toString(), onClick = { input = n.toString() }, enabled = !saving,
                                    modifier = Modifier.weight(1f), label = { Text(if (n == 0) "只复习" else "$n 词") })
                            }
                            repeat(3 - presets.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                val valid = input.toIntOrNull()?.let { it in 0..200 } == true
                OutlinedTextField(value = input, onValueChange = { input = it }, label = { Text("每日新词（0～200）") },
                    singleLine = true, enabled = !saving, isError = !valid,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth().testTag("daily_target_input"))
                Text("保存后今天立即生效。调低目标不撤销已开始的新词；到期复习不设数量上限。", style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(enabled = !saving, onClick = { edit = false }, modifier = Modifier.weight(1f)) { Text("取消") }
                Button(enabled = !saving && input.toIntOrNull()?.let { it in 0..200 } == true,
                    onClick = { viewModel.saveDailyTarget(input.toInt()) }, modifier = Modifier.weight(1f).testTag("save_daily_plan")) { Text("保存计划") }
            }
        }
    }
    LaunchedEffect(saved) { if (saved > savedAtOpen) edit = false }
}
