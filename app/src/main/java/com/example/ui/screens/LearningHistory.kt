package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LexiViewModel
import com.example.ui.wordbookLabel
import java.text.DateFormat
import java.util.Date

@Composable
fun AnswerSaveError(viewModel: LexiViewModel) {
    val message by viewModel.answerError.collectAsStateWithLifecycle()
    message?.let { AlertDialog(onDismissRequest = viewModel::dismissAnswerError,
        title = { Text("未保存本次作答") }, text = { Text(it) },
        confirmButton = { TextButton(onClick = viewModel::dismissAnswerError) { Text("返回重试") } }) }
}

@Composable
fun LearningHistoryCard(viewModel: LexiViewModel) {
    val events by viewModel.recentEvents.collectAsStateWithLifecycle()
    val total by viewModel.eventCount.collectAsStateWithLifecycle()
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val book by viewModel.currentWordbookId.collectAsStateWithLifecycle()
    var show by remember { mutableStateOf(false) }
    fun time(value: Long) = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))
    StudyCard("学习记录") {
        Text("当前词书已保存 $total 次作答")
        Text(state?.historyStartedAt?.takeIf { it > 0 }?.let { "详细记录自 ${time(it)} 开始。旧版进度保留，过去的作答历史不会补造。" }
            ?: "升级后开始记录每次作答；旧版学习阶段继续保留。", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { show = true }, modifier = Modifier.fillMaxWidth().testTag("learning_history")) { Text("查看作答记录") }
        Text("区分题型、提示与重试；全部记录随备份导出。清空当前学习进度不会改写历史作答。", style = MaterialTheme.typography.bodySmall)
    }
    if (show) AlertDialog(onDismissRequest = { show = false }, title = { Text("${wordbookLabel(book)} · 作答记录") },
        text = {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item { Text("共 $total 条，展示最近 100 条。完整记录保存在本机和导出备份内。", style = MaterialTheme.typography.bodySmall) }
                if (events.isEmpty()) item { Text("暂无作答记录，完成一次练习后即可查看。") }
                items(events, key = { it.id }) { e ->
                    Column(Modifier.testTag("event_${e.id}"), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(e.targetLabel, style = MaterialTheme.typography.titleMedium)
                        val mode = when (e.mode) { "PERSONAL_RECALL" -> "个性化复习"; "FAMILY_RECALL" -> "词族检验"; "EXAM_RECALL" -> "考试词汇回忆"; "EXAM_CLOZE" -> "考试语境填空"; "RECALL" -> "无提示检验"; "ERROR_RECALL" -> "错词独立检验"; "SPELLING" -> "整词拼写"; "BLOCKS" -> "分块排序"; "CONSTRUCTION" -> "构词选择"; else -> "词根卡片自评" }
                        Text("$mode · ${if (e.correct) "答对 / 认识" else "答错 / 不认识"}", color = if (e.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                        Text("${time(e.answeredAt)} · 本次第 ${e.attempt} 次 · ${e.elapsedMs / 1000} 秒", style = MaterialTheme.typography.bodySmall)
                        val hints = e.hints.split(',').mapNotNull { when (it) {
                            "CONTEXT" -> "例句语境"; "CHOICES" -> "选项辅助"; "ANALYSIS" -> "看过解析"; "AUDIO" -> "点击读音"; "ANCHOR" -> "助记提示"; else -> null
                        } }.joinToString("、")
                        Text(if (hints.isEmpty()) "未使用提示" else hints, style = MaterialTheme.typography.bodySmall)
                        Text("题目：${e.prompt}\n作答：${e.answer}", style = MaterialTheme.typography.bodyMedium)
                        if (e.retryOf != null) Text("错误后的重试", style = MaterialTheme.typography.bodySmall)
                        Text(if (e.scheduleChanged) "复习阶段 ${e.stageBefore} → ${e.stageAfter}" else "本次为提前练习 / 即时纠正，复习阶段不增加", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider()
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { show = false }) { Text("关闭") } })
}
