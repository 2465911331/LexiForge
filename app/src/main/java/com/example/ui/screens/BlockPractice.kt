package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OfflineMorphology
import com.example.data.WordFormula
import com.example.ui.LexiViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BlockPractice(word: WordFormula, entry: OfflineMorphology.Entry, viewModel: LexiViewModel) {
    val attempt by viewModel.assemblyAttempt.collectAsStateWithLifecycle()
    val success by viewModel.assemblySuccess.collectAsStateWithLifecycle()
    val speak = rememberWordSpeaker()
    // Use indices rather than strings so repeated chunks remain independently selectable.
    val choices = remember(word.id, entry.blocks, attempt) {
        val indices = entry.blocks.indices.toList()
        indices.shuffled().let { shuffled -> if (shuffled == indices) shuffled.drop(1) + shuffled.first() else shuffled }
    }
    var selected by remember(word.id, entry.blocks, attempt) { mutableStateOf(emptyList<Int>()) }
    var wrong by remember(word.id, attempt) { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).testTag("block_practice"),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${entry.kind?.label}练习", style = MaterialTheme.typography.titleLarge)
        Text(word.chineseMeaning, style = MaterialTheme.typography.headlineSmall)
        OutlinedButton(onClick = { viewModel.markWordHint("AUDIO"); speak(word.spell) }) { Text("播放读音") }
        Text("按顺序点选字母块。点已选字母块可撤回，选满后提交。")
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp)) {
            FlowRow(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selected.isEmpty()) Text("待拼写 · ${entry.blocks.size} 块", Modifier.padding(8.dp))
                selected.forEach { index ->
                    InputChip(selected = true, onClick = { selected = selected - index; wrong = false; viewModel.onWordAnswerEdited() },
                        enabled = !success, label = { Text(entry.blocks[index]) }, modifier = Modifier.testTag("picked_block_$index"))
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            choices.forEach { index ->
                OutlinedButton(onClick = { selected = selected + index; wrong = false; viewModel.onWordAnswerEdited() },
                    enabled = !success && index !in selected, modifier = Modifier.testTag("block_choice_$index")) {
                    Text(entry.blocks[index])
                }
            }
        }
        if (wrong && !success) Text("顺序不正确，请调整后重试。", color = MaterialTheme.colorScheme.error)
        if (!success) {
            Button(onClick = {
                val parts = selected.map { entry.blocks[it] }
                wrong = parts != entry.blocks
                viewModel.validateBlocks(parts)
            }, enabled = selected.size == entry.blocks.size, modifier = Modifier.fillMaxWidth().testTag("submit_blocks")) { Text("提交分块") }
            TextButton(onClick = { selected = emptyList(); wrong = false; viewModel.onWordAnswerEdited() }, enabled = selected.isNotEmpty(), modifier = Modifier.testTag("clear_blocks")) { Text("清空重排") }
        } else {
            Text("分块正确，可以继续学习。", color = MaterialTheme.colorScheme.primary)
            Text(word.spell, style = MaterialTheme.typography.headlineMedium)
            MorphologyInfo(word)
        }
        if (entry.kind == com.example.data.SharedLearningPack.Kind.SPELLING && !success) {
            Text("这是拼写辅助练习，字母块不代表词根或音节。", style = MaterialTheme.typography.bodySmall)
        }
    }
}
