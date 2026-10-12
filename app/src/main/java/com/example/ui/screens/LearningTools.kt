package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.LexiViewModel
import com.example.ui.wordbookLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
private fun ToolMessage(vm: LexiViewModel) {
    val message by vm.toolMessage.collectAsStateWithLifecycle()
    message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("tool_message")) }
}

@Composable
fun LearningToolsScreen(vm: LexiViewModel) {
    val words by vm.allWords.collectAsStateWithLifecycle()
    val originals by vm.originalWords.collectAsStateWithLifecycle()
    val events by vm.activeEvents.collectAsStateWithLifecycle()
    val edits by vm.corrections.collectAsStateWithLifecycle()
    val book by vm.currentWordbookId.collectAsStateWithLifecycle()
    val session by vm.recallSession.collectAsStateWithLifecycle()
    val busy by vm.toolBusy.collectAsStateWithLifecycle()
    val now by vm.reviewTime.collectAsStateWithLifecycle()
    var editedOnly by rememberSaveable(book) { mutableStateOf(false) }
    var page by rememberSaveable(book) { mutableStateOf("HOME") }
    var query by rememberSaveable(book, page) { mutableStateOf("") }
    var selected by rememberSaveable(book) { mutableStateOf<String?>(null) }
    var familyId by rememberSaveable(book) { mutableStateOf<String?>(null) }
    val mistakes = remember(words, events) { RecallTools.mistakes(words, events) }
    val families by produceState(emptyList<WordFamily>(), words) {
        value = withContext(Dispatchers.Default) { WordFamilies.build(words) }
    }
    fun back() {
        vm.dismissToolMessage()
        when { session != null -> vm.endRecall(); selected != null -> selected = null
            familyId != null -> familyId = null; page != "HOME" -> page = "HOME"
            else -> vm.navigateToSubScreen("NONE") }
    }
    BackHandler { back() }
    if (session != null) { RecallScreen(vm, ::back); return }
    if (selected != null) {
        val raw = originals.find { it.id == selected && it.bookId == book }
        if (raw != null) { CorrectionEditor(vm, raw, edits.find { it.wordId == raw.id && it.bookId == book }, ::back); return }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { it?.let(vm::exportCorrections) }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = ::back) { Text("返回") }
        PageHeading(when (page) { "PERSONAL" -> "个性化复习"; "EXAM" -> "考试专项"; "ERRORS" -> "错词复习"; "FAMILIES" -> "词族学习"; "CORRECTIONS" -> "内容纠错"; else -> "检验与巩固" }, wordbookLabel(book))
        ToolMessage(vm)
        when (page) {
            "HOME" -> Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                StudyCard("个性化复习与考试专项") {
                    Text("结合到期时间与近期作答安排复习，或练习四、六级词汇回忆与语境填空。")
                    Button(onClick = { page = "PERSONAL" }, modifier = Modifier.fillMaxWidth().testTag("open_personal")) { Text("个性化复习") }
                    OutlinedButton(onClick = { page = "EXAM" }, modifier = Modifier.fillMaxWidth().testTag("open_exam")) { Text("考试专项") }
                }
                StudyCard("无提示检验") {
                    Text("从已学词中安排最多 20 题。只显示中文释义，提交后才揭晓答案，每题只记录首次作答。")
                    Button(onClick = { vm.startRecall(false) }, enabled = !busy && words.any(RecallTools::canTest), modifier = Modifier.fillMaxWidth().testTag("start_recall")) { Text("开始无提示检验") }
                    if (words.none(RecallTools::canTest)) Text("先学习一些有中文释义的单词，再来检验。", style = MaterialTheme.typography.bodySmall)
                }
                StudyCard("错词复习") {
                    Text("待巩固 ${mistakes.size} 词。普通提示练习和看答案后立即答对，都不会直接移出错词本。")
                    OutlinedButton(onClick = { page = "ERRORS" }, modifier = Modifier.fillMaxWidth().testTag("open_mistakes")) { Text("查看错词") }
                }
                StudyCard("词族学习") {
                    Text("当前词书有 ${families.size} 组可追溯词族。普通拼写分块和相似字母不会作为词族依据。")
                    OutlinedButton(onClick = { page = "FAMILIES" }, modifier = Modifier.fillMaxWidth().testTag("open_families")) { Text("浏览词族") }
                }
                StudyCard("内容纠错") {
                    Text("修订释义、例句或停用有问题的拆解；个人修订与原文分别保存，可以恢复或导出。")
                    OutlinedButton(onClick = { page = "CORRECTIONS" }, modifier = Modifier.fillMaxWidth().testTag("open_corrections")) { Text("查找并纠错 · 已修订 ${edits.size} 词") }
                }
            }
            "PERSONAL" -> PersonalPracticePanel(vm, book, words, events, now, busy)
            "EXAM" -> ExamPracticePanel(vm, book, words, events, busy)
            "ERRORS" -> {
                Text("至少距最近答错 5 分钟后，独立检验首次答对才移出。此规则用于避免即时记住答案，不代表已永久掌握。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = { vm.startRecall(true) }, enabled = !busy && mistakes.any { RecallTools.canTest(it.word) }, modifier = Modifier.fillMaxWidth().testTag("start_error_recall")) { Text("检验错词 · 每轮最多 20 题") }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (mistakes.isEmpty()) item { Text("暂无待巩固错词。新的错误会自动加入。") }
                    items(mistakes, key = { it.word.id }) { item -> StudyCard(item.word.spell) {
                        Text(item.word.chineseMeaning)
                        Text("累计答错 ${item.errors} 次", style = MaterialTheme.typography.bodySmall)
                        if (!RecallTools.canTest(item.word)) Text("缺少可用中文释义，请先在内容纠错中补充。", style = MaterialTheme.typography.bodySmall)
                    } }
                }
            }
            "FAMILIES" -> {
                val family = families.find { it.key == familyId }
                if (family != null) {
                    Text(family.label, style = MaterialTheme.typography.titleLarge)
                    Text("${family.evidence}。规则与资料关系未经逐词审核；具体词义以每个词条为准。", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { vm.startFamilyPractice(family.key) }, enabled = !busy && family.words.any(RecallTools::canTest),
                        modifier = Modifier.fillMaxWidth().testTag("start_family_recall")) { Text("检验本词族已学词 · 最多 20 题") }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(family.words, key = { it.id }) { word -> StudyCard(word.spell) {
                            Text(word.chineseMeaning)
                            MorphologyInfo(word)
                            OutlinedButton(onClick = { vm.practiceFamilyWord(word) }, modifier = Modifier.fillMaxWidth()) { Text("进入单词训练") }
                            TextButton(onClick = { vm.dismissToolMessage(); selected = word.id }) { Text("纠正此词内容") }
                        } }
                    }
                } else {
                    OutlinedTextField(query, { query = it }, label = { Text("搜索词基、词根或单词") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    val shown = families.filter { it.label.contains(query.trim(), true) || it.words.any { w -> w.spell.contains(query.trim(), true) } }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (shown.isEmpty()) item { Text("暂无匹配的可靠词族；不会为了覆盖率强行归组。") }
                        items(shown, key = { it.key }) { f -> Card(onClick = { familyId = f.key }, modifier = Modifier.fillMaxWidth().testTag("family_${f.key}")) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${f.label} · ${f.words.size} 词", style = MaterialTheme.typography.titleMedium)
                                Text(f.words.take(5).joinToString(" · ") { it.spell })
                                Text(f.evidence, style = MaterialTheme.typography.bodySmall)
                            }
                        } }
                    }
                }
            }
            "CORRECTIONS" -> {
                OutlinedButton(onClick = { exporter.launch("LexiForge-content-corrections.json") }, enabled = !busy,
                    modifier = Modifier.fillMaxWidth().testTag("export_corrections")) { Text("导出全部词书的纠错清单") }
                Text("只保存到你选择的位置，不自动上传。完整学习备份也包含这些修订。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(query, { query = it }, label = { Text("搜索要纠错的单词或释义") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("correction_search"))
                FilterChip(editedOnly, { editedOnly = !editedOnly }, label = { Text("只看已修订 · ${edits.size}") }, modifier = Modifier.testTag("edited_only"))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val editedIds = edits.map { it.wordId }.toSet()
                    val shown = words.filter { (!editedOnly || it.id in editedIds) && (it.spell.contains(query.trim(), true) || it.chineseMeaning.contains(query.trim(), true)) }
                    if (shown.isEmpty()) item { Text("没有匹配词条。") }
                    items(shown, key = { it.id }) { w -> Card(onClick = { vm.dismissToolMessage(); selected = w.id }, modifier = Modifier.fillMaxWidth().testTag("correct_${w.id}")) {
                        Column(Modifier.padding(16.dp)) { Text(w.spell, style = MaterialTheme.typography.titleMedium); Text(w.chineseMeaning)
                            if (edits.any { it.wordId == w.id }) Text("个人修订", style = MaterialTheme.typography.labelSmall) }
                    } }
                }
            }
        }
    }
}

@Composable
private fun RecallScreen(vm: LexiViewModel, onBack: () -> Unit) {
    val session by vm.recallSession.collectAsStateWithLifecycle()
    val busy by vm.toolBusy.collectAsStateWithLifecycle()
    val s = session ?: return
    var input by rememberSaveable(s.id, s.index) { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("结束本轮") }
        Text(s.title, style = MaterialTheme.typography.headlineMedium)
        ToolMessage(vm)
        if (s.finished) {
            Text("本轮首次答对 ${s.correctCount} / ${s.words.size}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("recall_summary"))
            Text("每次答案都已保存。错词可继续巩固；查看答案后的即时记忆不算独立通过。")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("返回学习工具") }
        } else {
            Text("第 ${s.index + 1} / ${s.words.size} 题", style = MaterialTheme.typography.titleMedium)
            StudyCard(if (s.mode == "EXAM_CLOZE") "填入例句中缺失的单词" else "根据中文释义写出英文") {
                Text(if (s.mode == "EXAM_CLOZE") PracticePlanner.cloze(s.words[s.index]).orEmpty() else RecallTools.prompt(s.words[s.index].chineseMeaning),
                    style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("recall_prompt"))
            }
            if (s.result == null) {
                OutlinedTextField(input, { input = it }, label = { Text("英文拼写") }, singleLine = true, enabled = !busy,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth().testTag("recall_answer"))
                Button(onClick = { vm.submitRecall(input) }, enabled = !busy && input.isNotBlank() && input.length <= 512,
                    modifier = Modifier.fillMaxWidth().testTag("submit_recall")) { Text(if (busy) "正在保存…" else "提交答案") }
                TextButton(onClick = { vm.submitRecall("（不会）") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("give_up_recall")) { Text("暂时不会，记为答错") }
            } else {
                val result = s.result!!
                Text(if (result.correct) "首次作答正确" else "本次答错，已加入错词本", color = if (result.correct) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Text("正确拼写：${s.words[s.index].spell}", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.testTag("recall_solution"))
                Text("你的答案：${result.answer}")
                if (s.mode == "EXAM_CLOZE") {
                    Text(s.words[s.index].sentenceEn)
                    Text(s.words[s.index].sentenceZh)
                    Text("本题有例句语境，不作为无提示通过移出错词本。", style = MaterialTheme.typography.bodySmall)
                }
                Text("本题不再重复提交。提前练习不会连续提高复习阶段。", style = MaterialTheme.typography.bodySmall)
                Button(onClick = vm::nextRecall, modifier = Modifier.fillMaxWidth().testTag("next_recall")) { Text(if (s.index == s.words.lastIndex) "查看本轮结果" else "下一题") }
            }
        }
    }
}

@Composable
fun ContentCorrectionScreen(vm: LexiViewModel) {
    val words by vm.originalWords.collectAsStateWithLifecycle()
    val edits by vm.corrections.collectAsStateWithLifecycle()
    val id by vm.editWordId.collectAsStateWithLifecycle()
    val word = words.find { it.id == id }
    if (word == null) Text("正在读取词条…", Modifier.padding(24.dp))
    else CorrectionEditor(vm, word, edits.find { it.wordId == word.id && it.bookId == word.bookId }) { vm.navigateToSubScreen("NONE") }
}

@Composable
private fun CorrectionEditor(vm: LexiViewModel, word: WordFormula, correction: ContentCorrection?, onBack: () -> Unit) {
    val busy by vm.toolBusy.collectAsStateWithLifecycle()
    var meaning by rememberSaveable(word.bookId, word.id, correction?.updatedAt) { mutableStateOf(correction?.meaning ?: word.chineseMeaning) }
    var english by rememberSaveable(word.bookId, word.id, correction?.updatedAt) { mutableStateOf(correction?.sentenceEn ?: word.sentenceEn) }
    var chinese by rememberSaveable(word.bookId, word.id, correction?.updatedAt) { mutableStateOf(correction?.sentenceZh ?: word.sentenceZh) }
    var note by rememberSaveable(word.bookId, word.id, correction?.updatedAt) { mutableStateOf(correction?.note.orEmpty()) }
    var hide by rememberSaveable(word.bookId, word.id, correction?.updatedAt) { mutableStateOf(correction?.hideAnalysis ?: false) }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, enabled = !busy) { Text("返回") }
        PageHeading("内容纠错 · ${word.spell}", "个人修订仅作用于当前词书，不改变拼写与学习进度。")
        ToolMessage(vm)
        Text("词书原文：${word.chineseMeaning}\n原始拆解：${word.literalFormula}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(meaning, { meaning = it }, label = { Text("修订释义（必填）") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("correction_meaning"))
        OutlinedTextField(english, { english = it }, label = { Text("英文例句") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(chinese, { chinese = it }, label = { Text("例句译文") }, enabled = !busy, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(note, { note = it }, label = { Text("纠错理由或参考来源") }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("correction_note"))
        Row { Text("停用此词的原始拆解", Modifier.weight(1f)); Switch(hide, { hide = it }, enabled = !busy, modifier = Modifier.testTag("hide_analysis")) }
        Text("修改释义会自动切换为整词训练，避免沿用不匹配的拆解。个人修订尚未经共同审核。", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { vm.saveContentCorrection(ContentCorrection(word.bookId, word.id, meaning.trim(), english.trim(), chinese.trim(), note.trim(), hide, System.currentTimeMillis())) },
            enabled = !busy && meaning.isNotBlank() && meaning.length <= 2000 && english.length <= 4000 && chinese.length <= 4000 && note.length <= 2000,
            modifier = Modifier.fillMaxWidth().testTag("save_correction")) { Text("保存个人修订") }
        if (correction != null) OutlinedButton(onClick = { confirm = true }, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("restore_original")) { Text("恢复词书原文") }
    }
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("恢复词书原文？") },
        text = { Text("删除此词的个人修订，保留原词条、作答历史和学习进度。操作前保存本机恢复点。") },
        confirmButton = { TextButton(onClick = { confirm = false; vm.removeContentCorrection(word.bookId, word.id) }, modifier = Modifier.testTag("confirm_restore_original")) { Text("恢复原文") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("取消") } })
}
