package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.*
import com.example.ui.LexiViewModel

@Composable
private fun RoundSize(value: Int, onChange: (Int) -> Unit) {
    Text("本轮题量", style = MaterialTheme.typography.titleSmall)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 20, 30).forEach { n -> FilterChip(value == n, { onChange(n) }, label = { Text("$n 题") }) }
    }
}

@Composable
fun PersonalPracticePanel(vm: LexiViewModel, book: String, words: List<WordFormula>, events: List<ReviewEvent>, now: Long, busy: Boolean) {
    var limit by rememberSaveable(book) { mutableStateOf(20) }
    var errorsOnly by rememberSaveable(book) { mutableStateOf(false) }
    val dueCount = words.count { ReviewScheduler.isDue(it.lastReviewedAt, it.nextReviewAt, now) }
    val candidates = remember(words, events, dueCount, errorsOnly) { PracticePlanner.personal(words, events, now, errorsOnly) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        StudyCard("按你的记录安排") {
            Text("先复习到期词，再处理错词、近期失误和缺少独立检验的词。只安排已学词，不额外增加今日新词量。")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(!errorsOnly, { errorsOnly = false }, label = { Text("综合复习") })
                FilterChip(errorsOnly, { errorsOnly = true }, label = { Text("集中错词") }, modifier = Modifier.testTag("personal_errors_only"))
            }
            RoundSize(limit) { limit = it }
            val chosen = candidates.take(limit)
            Text("本轮可安排 ${chosen.size} 词", style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("personal_count"))
            chosen.groupBy { it.priority }.forEach { (priority, group) ->
                Text("${when (priority) { 0 -> "到期复习"; 1 -> "待巩固错词"; 2 -> "近期出现失误"; 3 -> "缺少独立答对记录"; else -> "已学词抽查" }}：${group.size} 词")
            }
            if (chosen.isEmpty()) Text("暂无符合条件的词，请先完成学习或切换复习范围。")
            Button(onClick = { vm.startPersonalPractice(limit, errorsOnly) }, enabled = !busy && chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().testTag("start_personal")) { Text("开始个性化复习") }
        }
        Text("薄弱项参考最近 10 次首次作答。提示练习与独立回忆分别记录；当前使用明确排序规则，未预测遗忘概率，也未更改原复习间隔。", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun ExamPracticePanel(vm: LexiViewModel, book: String, words: List<WordFormula>, events: List<ReviewEvent>, busy: Boolean) {
    var limit by rememberSaveable(book) { mutableStateOf(20) }
    var cloze by rememberSaveable(book) { mutableStateOf(false) }
    val candidates = remember(words, events, book, cloze) { PracticePlanner.exam(words, events, book, cloze) }
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("四、六级词汇专项 · 词书练习题，非真题或模拟整卷", style = MaterialTheme.typography.titleMedium)
        if (!PracticePlanner.examBook(book)) {
            Text("请先在设置中下载或切换大学英语四级、六级词书，再完成一些单词学习。")
        } else {
            StudyCard("选择专项") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!cloze, { cloze = false }, label = { Text("词汇回忆") })
                    FilterChip(cloze, { cloze = true }, label = { Text("语境填空") }, modifier = Modifier.testTag("exam_cloze"))
                }
                Text(if (cloze) "根据现有例句填写缺失单词。提交前隐藏中文译文和拆词，提交后显示完整例句。按当前词条拼写判分。"
                    else "根据中文释义回忆英文拼写，不提供首字母或选项。")
                RoundSize(limit) { limit = it }
                Text("可用 ${candidates.size} 词 · 本轮 ${minOf(limit, candidates.size)} 题", modifier = Modifier.testTag("exam_count"))
                if (candidates.isEmpty()) Text("暂无可用题目。仅使用已学词；语境题还要求例句包含一次完整目标词，缺少合格内容不会编造题目。")
                Button(onClick = { vm.startExamPractice(limit, cloze) }, enabled = !busy && candidates.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().testTag("start_exam")) { Text("开始专项练习") }
            }
        }
        Text("优先安排该专项中尚未练过或最久未练的词。语境填空包含例句提示，答对不会作为无提示通过移出错词本。练习分数不换算四、六级考试成绩。", style = MaterialTheme.typography.bodySmall)
    }
}
