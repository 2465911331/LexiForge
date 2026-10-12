package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ReviewScheduler
import com.example.data.WordFormula
import com.example.ui.*

@Composable
fun MainDashboard(viewModel: LexiViewModel, modifier: Modifier = Modifier) {
    var tab by rememberSaveable { mutableStateOf("DASHBOARD") }
    val subScreen by viewModel.activeSubScreen.collectAsStateWithLifecycle()
    val savedTabs = rememberSaveableStateHolder()
    BackHandler(subScreen != "NONE" || tab != "DASHBOARD") {
        if (subScreen != "NONE") viewModel.navigateToSubScreen("NONE") else tab = "DASHBOARD"
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (subScreen == "NONE") NavigationBar(Modifier.testTag("main_navigation_bar"), containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                listOf("DASHBOARD" to "今日", "WORD_LIST" to "单词", "SETTINGS" to "设置").forEach { (id, label) ->
                    NavigationBarItem(
                        selected = tab == id, onClick = { tab = id },
                        icon = { Icon(when (id) {
                            "DASHBOARD" -> Icons.Default.Home
                            "WORD_LIST" -> Icons.AutoMirrored.Filled.List
                            else -> Icons.Default.Settings
                        }, contentDescription = null) },
                        label = { Text(label) }, modifier = Modifier.testTag("tab_$id")
                    )
                }
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).imePadding()) {
            when (subScreen) {
                "LEARNING_TOOLS" -> savedTabs.SaveableStateProvider("LEARNING_TOOLS") { LearningToolsScreen(viewModel) }
                "CONTENT_EDIT" -> ContentCorrectionScreen(viewModel)
                "ASSEMBLY", "EVENING" -> EveningSiegeScreen(viewModel)
                "SWIPE", "NOON" -> NoonBlitzScreen(viewModel, onNavigateToEvening = {
                    viewModel.setReviewFrequencyPref("全部")
                    viewModel.setEbbinghausFilterActive(false)
                    viewModel.navigateToSubScreen("ASSEMBLY")
                })
                "COMPONENTS_OVERVIEW" -> ComponentsOverviewScreen(viewModel)
                else -> savedTabs.SaveableStateProvider(tab) {
                    when (tab) {
                        "WORD_LIST" -> GeneralWordListScreen(viewModel) {
                            viewModel.navigateToSubScreen("ASSEMBLY", resetState = false)
                        }
                        "SETTINGS" -> SystemSettingsScreen(viewModel)
                        else -> DashboardCockpitView(viewModel, onWords = { tab = "WORD_LIST" }, onSettings = { tab = "SETTINGS" })
                    }
                }
            }
        }
    }
}

@Composable
internal fun PageHeading(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun StudyCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
fun DashboardCockpitView(viewModel: LexiViewModel, onWords: () -> Unit, onSettings: () -> Unit) {
    val words by viewModel.allWords.collectAsStateWithLifecycle()
    val components by viewModel.allComponents.collectAsStateWithLifecycle()
    val now by viewModel.reviewTime.collectAsStateWithLifecycle()
    val unseen = words.count { it.lastReviewedAt == 0L }
    val practiced = words.size - unseen
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        PageHeading("今日学习", "LexiForge · 词汇熔炉")
        DailyPlanCard(viewModel, onWords, onSettings)
        StudyCard("检验与巩固") {
            Text("个性化复习、考试专项、词族学习与内容纠错。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { viewModel.navigateToSubScreen("LEARNING_TOOLS") }, modifier = Modifier.fillMaxWidth().testTag("open_learning_tools")) { Text("打开学习工具") }
        }
        StudyCard("构词部件") {
            Text("学习词根、词基和词缀，再用单词练习巩固。", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = {
                viewModel.setEbbinghausFilterActive(false)
                viewModel.resetDeck()
                viewModel.navigateToSubScreen("SWIPE")
            }, enabled = components.isNotEmpty(), modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("start_components")) {
                Text("练习构词卡片")
            }
            TextButton(onClick = { viewModel.navigateToSubScreen("COMPONENTS_OVERVIEW") }, modifier = Modifier.fillMaxWidth()) {
                Text("浏览构词部件库 · ${components.size} 个")
            }
        }
        StudyCard("词书进度") {
            Text("已练习 $practiced / ${words.size} 词", style = MaterialTheme.typography.titleMedium)
            LinearProgressIndicator(progress = { if (words.isEmpty()) 0f else practiced.toFloat() / words.size }, modifier = Modifier.fillMaxWidth())
            Text("统计曾完成作答的单词，包含仍需重练的词，不代表已经掌握。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun GeneralWordListScreen(viewModel: LexiViewModel, onNavigateToAssembly: () -> Unit) {
    val corrections by viewModel.corrections.collectAsStateWithLifecycle()
    val words by viewModel.allWords.collectAsStateWithLifecycle()
    val now by viewModel.reviewTime.collectAsStateWithLifecycle()
    val book by viewModel.currentWordbookId.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(WordListFilter.ALL) }
    var detailId by rememberSaveable { mutableStateOf<String?>(null) }
    val detail = words.find { it.id == detailId }
    val speak = rememberWordSpeaker()
    BackHandler(detailId != null) { detailId = null }
    if (detail != null) {
        WordDetail(detail, now, corrected = corrections.any { it.wordId == detail.id }, onCorrect = { viewModel.openContentEditor(detail.id) }, onBack = { detailId = null }, onSpeak = { speak(detail.spell) }, onPractice = {
            viewModel.setReviewFrequencyPref("全部")
            viewModel.setEbbinghausFilterActive(false)
            viewModel.selectWordForAssembly(detail)
            onNavigateToAssembly()
        })
        return
    }
    val shown = remember(words, filter, query, now) { filterStudyWords(words, filter, query, now) }
    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Spacer(Modifier.height(8.dp))
        PageHeading("单词", "${wordbookLabel(book)} · ${words.size} 词")
        OutlinedTextField(value = query, onValueChange = { query = it }, singleLine = true,
            placeholder = { Text("搜索单词或释义") }, leadingIcon = { Icon(Icons.Default.Search, null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "清空搜索") } },
            modifier = Modifier.fillMaxWidth().testTag("word_search"))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            WordListFilter.entries.forEach { option ->
                val count = filterStudyWords(words, option, "", now).size
                FilterChip(selected = filter == option, onClick = { filter = option }, label = { Text("${option.label} $count") }, modifier = Modifier.testTag("filter_${option.name}"))
            }
        }
        Text("${shown.size} 个结果 · 点击单词查看详情", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f).testTag("word_list"), contentPadding = PaddingValues(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (shown.isEmpty()) item {
                StudyCard(if (query.isNotBlank()) "没有找到匹配的单词" else "此分类暂无单词") {
                    Text(if (words.isEmpty()) "请在设置中选择词书。" else "可以清空搜索或选择其他分类。")
                    TextButton(onClick = { query = ""; filter = WordListFilter.ALL }) { Text("显示全部单词") }
                }
            }
            items(shown, key = { it.id }) { word ->
                Card(onClick = { detailId = word.id }, modifier = Modifier.fillMaxWidth().testTag("word_${word.id}"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(word.spell, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            Text(word.chineseMeaning, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(wordStudyStatus(word, now), style = MaterialTheme.typography.labelMedium,
                                color = if (word.isDueAt(now)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { speak(word.spell) }) { Icon(Icons.Default.VolumeUp, "播放 ${word.spell} 的读音") }
                    }
                }
            }
        }
    }
}

@Composable
private fun WordDetail(word: WordFormula, now: Long, corrected: Boolean, onCorrect: () -> Unit, onBack: () -> Unit, onSpeak: () -> Unit, onPractice: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = onBack, modifier = Modifier.testTag("word_detail_back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(8.dp)); Text("返回单词列表")
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(word.spell, modifier = Modifier.weight(1f), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = onSpeak) { Icon(Icons.Default.VolumeUp, "播放读音") }
        }
        Text(word.chineseMeaning, style = MaterialTheme.typography.titleMedium)
        if (corrected) Text("个人修订 · 可在内容纠错中查看原文", style = MaterialTheme.typography.labelMedium)
        TextButton(onClick = onCorrect, modifier = Modifier.testTag("edit_word_content")) { Text("内容纠错") }
        Text("${wordStudyStatus(word, now)} · ${wordReviewDescription(word, now)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        Button(onClick = onPractice, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("practice_word")) {
            Text(if (word.isDueAt(now)) "复习这个单词" else "练习这个单词")
        }
        if (word.lastReviewedAt > 0 && !word.isDueAt(now)) Text("提前练习不会提高复习阶段。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        StudyCard("学习解析") {
            MorphologyInfo(word)
        }
        if (hasTeachingExample(word)) StudyCard("例句") {
            Text(word.sentenceEn, style = MaterialTheme.typography.bodyLarge)
            if (word.sentenceZh.isNotBlank()) Text(word.sentenceZh, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OfflineAssistantCard(word.spell, word.chineseMeaning)
    }
}

@Composable
fun ComponentsOverviewScreen(viewModel: LexiViewModel) {
    val components by viewModel.allComponents.collectAsStateWithLifecycle()
    val now by viewModel.reviewTime.collectAsStateWithLifecycle()
    var category by rememberSaveable { mutableStateOf("ALL") }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TextButton(onClick = { viewModel.navigateToSubScreen("NONE") }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Text("返回") }
        PageHeading("构词部件库", "查看词根、词基与词缀提示")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("ALL" to "全部", "PREFIX" to "前缀", "ROOT" to "词根 / 词基", "SUFFIX" to "后缀").forEach { (id, name) ->
                FilterChip(selected = category == id, onClick = { category = id }, label = { Text(name) })
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val shown = components.filter { category == "ALL" || it.type == category }
            if (shown.isEmpty()) item { Text("此分类暂无内容") }
            items(shown, key = { it.id }) { component ->
                StudyCard(component.name) {
                    Text(component.literalMeaning)
                    if (component.anchorWord.isNotBlank()) Text("${component.anchorWord} · ${component.anchorDesc}", style = MaterialTheme.typography.bodyMedium)
                    Text(when {
                        component.lastReviewedAt == 0L -> "未练习"
                        ReviewScheduler.isDue(component.lastReviewedAt, component.nextReviewAt, now) -> "待复习"
                        else -> "已练习"
                    }, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
