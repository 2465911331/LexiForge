package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AffixComponent
import com.example.data.WordFormula
import com.example.ui.LexiViewModel
import com.example.ui.components.FrequencyBadge
import kotlin.math.roundToInt

@Composable
fun EveningSiegeScreen(
    viewModel: LexiViewModel,
    modifier: Modifier = Modifier
) {
    AnswerSaveError(viewModel)
    val components by viewModel.allComponents.collectAsStateWithLifecycle()
    val words by viewModel.eveningSiegeWords.collectAsStateWithLifecycle()
    val allWords by viewModel.allWords.collectAsStateWithLifecycle()
    val selectedWordId by viewModel.selectedWordId.collectAsStateWithLifecycle()
    val isEbbinghausActive by viewModel.isEbbinghausFilterActive.collectAsStateWithLifecycle()
    val dailyPlanActive by viewModel.dailyPlanActive.collectAsStateWithLifecycle()

    val activeWord = allWords.find { it.id == selectedWordId }
    var labTab by remember { mutableStateOf("DECONSTRUCT") }

    var previousQueuedIds by remember { mutableStateOf(emptySet<String>()) }

    // Preserve feedback when an answer leaves the queue, then start a fresh attempt when it is due again.
    LaunchedEffect(words, selectedWordId) {
        val queuedIds = words.map { it.id }.toSet()
        val currentId = selectedWordId
        if (currentId == null && words.isNotEmpty()) {
            viewModel.selectWordForAssembly(words.first())
        } else if (currentId != null && currentId in queuedIds && currentId !in previousQueuedIds) {
            viewModel.selectWordForAssembly(words.first { it.id == currentId })
        }
        previousQueuedIds = queuedIds
    }

    // Evening deep dark layout theme (cyan/blue blueprint style)
    val eveningBgBrush = Brush.verticalGradient(
        listOf(
            Color(0xFF0F172A), // Slate 900
            Color(0xFF020617)  // Slate 950
        )
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background) // High Density Light theme background
            .padding(16.dp)
    ) {
        // Back Navigation Row to Dashboard Cockpit
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { viewModel.navigateToSubScreen("NONE") }
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "返回",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "返回",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        if (words.isEmpty() && activeWord == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏝️", fontSize = 54.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "当前没有待学或到期的单词",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "到期任务会自动更新。返回今日页面可开始学习；单词列表支持自由练习。",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        } else {
            // Upper switcher deck replaces the complex LazyRow word checklist
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                val realIndex = words.indexOfFirst { it.id == selectedWordId }
                val currentIndex = if (realIndex != -1) realIndex else 0
                val hasPrev = realIndex > 0 && words.isNotEmpty()
                val hasNext = (realIndex == -1 || realIndex < words.size - 1) && words.isNotEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous Word option
                    IconButton(
                        onClick = {
                            if (hasPrev) {
                                viewModel.selectWordForAssembly(words[currentIndex - 1])
                            }
                        },
                        enabled = hasPrev,
                        modifier = Modifier.testTag("prev_word_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "上一个单词",
                            tint = if (hasPrev) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }

                    // Dynamic detail display
                    val activeWord = allWords.find { it.id == selectedWordId } ?: words.firstOrNull()
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (activeWord != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (labTab == "ASSEMBLE") "拼写与构词练习" else activeWord.spell,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (activeWord.isAssembled) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "曾答对",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${if (dailyPlanActive) "今日计划" else if (isEbbinghausActive) "到期复习" else "单词训练"} · 队列剩余 ${words.size} 个",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            Text(
                                text = "加载中...",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Next Word option
                    IconButton(
                        onClick = {
                            if (hasNext) {
                                viewModel.selectWordForAssembly(if (realIndex == -1) words[0] else words[currentIndex + 1])
                            }
                        },
                        enabled = hasNext,
                        modifier = Modifier.testTag("next_word_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "下一个单词",
                            tint = if (hasNext) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Center experimental panel (Enlarged)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                val selectedWord = allWords.find { it.id == selectedWordId } ?: words.firstOrNull()

                if (selectedWord == null) {
                    // Empty safety blocker card for active word
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Default.Build,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(72.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "单词练习",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "选择一个单词，查看构词提示并进行拼装或拼写练习。",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    ActiveLaboratoryPanel(
                        word = selectedWord,
                        components = components,
                        viewModel = viewModel,
                        isEbbinghausActive = isEbbinghausActive,
                        labTab = labTab,
                        onTabChange = { labTab = it }
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveLaboratoryPanel(
    word: WordFormula,
    components: List<AffixComponent>,
    viewModel: LexiViewModel,
    isEbbinghausActive: Boolean,
    labTab: String,
    onTabChange: (String) -> Unit
) {
    var isSliced by remember { mutableStateOf(false) }
    val attempt by viewModel.assemblyAttempt.collectAsStateWithLifecycle()

    // Reset slice state whenever word changes
    LaunchedEffect(word.id, attempt) {
        isSliced = false
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // Lab switch Tabs (Material 3 style) - Centered and Shrunk
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .padding(2.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { onTabChange("DECONSTRUCT") },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (labTab == "DECONSTRUCT") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (labTab == "DECONSTRUCT") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("学习解析", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }

            Button(
                onClick = { onTabChange("ASSEMBLE") },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (labTab == "ASSEMBLE") MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (labTab == "ASSEMBLE") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                shape = RoundedCornerShape(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(11.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("拼写与构词", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Main experimental container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                .padding(16.dp)
        ) {
            if (labTab == "DECONSTRUCT") {
                val attempt by viewModel.assemblyAttempt.collectAsStateWithLifecycle()
                LaunchedEffect(word.bookId, word.id, attempt) { viewModel.markWordHint("ANALYSIS") }
                DisassemblyRoom(
                    word = word,
                    isSliced = isSliced,
                    onSliceTrigger = { isSliced = true },
                    onReconstructTrigger = {
                        isSliced = false
                    },
                    isEbbinghausActive = isEbbinghausActive
                )
            } else {
                AssemblyPuzzleLab(
                    word = word,
                    components = components,
                    viewModel = viewModel,
                    isEbbinghausActive = isEbbinghausActive
                )
            }
        }
    }
}

fun formatPartPart(part: String?): Pair<String, String> {
    if (part == null) return Pair("", "")
    val trimmed = part.trim()
    if (!trimmed.contains(" ")) return Pair(trimmed, "")
    val core = trimmed.substringBefore(" ").substringBefore("(").trim()
    val remainder = trimmed.substringAfter(core).trim()
    val desc = if (remainder.startsWith("(") && remainder.endsWith(")")) {
        remainder.removePrefix("(").removeSuffix(")")
    } else {
        remainder
    }
    return Pair(core, desc)
}

@Composable
fun DisassemblyRoom(
    word: WordFormula,
    isSliced: Boolean,
    onSliceTrigger: () -> Unit,
    onReconstructTrigger: () -> Unit,
    isEbbinghausActive: Boolean
) {
    val speak = rememberWordSpeaker()
    LaunchedEffect(word.id, speak) { speak(word.spell) }

    val blockEntry = com.example.data.OfflineMorphology.displayEntry(word)
    if (blockEntry?.blocks?.isNotEmpty() == true) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(word.spell, style = MaterialTheme.typography.headlineMedium)
            Text(word.chineseMeaning)
            Button(onClick = { speak(word.spell) }) { Text("播放读音") }
            Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium) {
                Text(blockEntry.blocks.joinToString(" · "), Modifier.fillMaxWidth().padding(20.dp), style = MaterialTheme.typography.headlineSmall)
            }
            MorphologyInfo(word)
            Text("切换到“拼写与构词”即可练习分块排序。")
        }
        return
    }

    if (!com.example.data.OfflineMorphology.canAssemble(word)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(word.spell, style = androidx.compose.material3.MaterialTheme.typography.headlineMedium)
            Text(word.chineseMeaning)
            Button(onClick = { speak(word.spell) }) { Text("播放读音") }
            MorphologyInfo(word)
            Text("切换到“拼写与构词”即可练习整词拼写。")
        }
        return
    }

    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = "🧪 单词：${word.spell}",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = androidx.compose.ui.text.TextStyle(
                        platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                            includeFontPadding = false
                        )
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .requiredSize(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable {
                            speak(word.spell)
                        }
                        .testTag("speak_word_btn"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "播放读音",
                        tint = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Text(
                text = "中文释义：${word.chineseMeaning}",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            MorphologyInfo(word)

            // Physical Word slicing model
            if (!isSliced) {
                // Single consolidated raw block
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(84.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = word.spell,
                        fontSize = if (word.spell.length > 12) 20.sp else if (word.spell.length > 8) 26.sp else 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = "点击下方按钮查看构词提示。拆解与联想用于辅助记忆。",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                // Slices spaced apart with springs animations
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (word.prefixPart != null) {
                        val (core, desc) = formatPartPart(word.prefixPart)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("前缀 PREFIX", fontSize = 9.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = core, 
                                    fontSize = if (core.length > 5) 12.sp else 15.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (desc.isNotEmpty() && !isEbbinghausActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.primary.copy(alpha = 0.82f), textAlign = TextAlign.Center, lineHeight = 11.sp)
                                }
                            }
                        }
                    }

                    if (word.prefixPart != null && word.rootPart != null) {
                        Text(text = "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }

                    if (word.rootPart != null) {
                        val (core, desc) = formatPartPart(word.rootPart)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(10.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(if (word.rootId?.startsWith("rule:base:") == true) "词基 BASE" else "词根 ROOT", fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = core, 
                                    fontSize = if (core.length > 5) 12.sp else 15.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.tertiary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (desc.isNotEmpty() && !isEbbinghausActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.82f), textAlign = TextAlign.Center, lineHeight = 11.sp)
                                }
                            }
                        }
                    }

                    if (word.rootPart != null && word.suffixPart != null) {
                        Text(text = "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.Black)
                    }

                    if (word.suffixPart != null) {
                        val (core, desc) = formatPartPart(word.suffixPart)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(10.dp))
                                .border(1.5.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(10.dp))
                                .padding(horizontal = 4.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("后缀 SUFFIX", fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = core, 
                                    fontSize = if (core.length > 5) 12.sp else 15.sp, 
                                    fontWeight = FontWeight.Black, 
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                                if (desc.isNotEmpty() && !isEbbinghausActive) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(desc, fontSize = 9.sp, color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.82f), textAlign = TextAlign.Center, lineHeight = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Explanatory reverse derivation logic representation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(14.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
                        .padding(14.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Info,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                "构词提示",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        val formulaText = if (isEbbinghausActive) {
                            val prefixCore = word.prefixPart?.substringBefore(" ")?.trim() ?: ""
                            val rootCore = word.rootPart?.substringBefore(" ")?.trim() ?: ""
                            val suffixCore = word.suffixPart?.substringBefore(" ")?.trim() ?: ""
                            listOf(prefixCore, rootCore, suffixCore).filter { it.isNotEmpty() }.joinToString(" + ") + " = ${word.spell}"
                        } else {
                            word.literalFormula
                        }
                        val evolutionTip = if (isEbbinghausActive) {
                            "复习时先尝试回忆构词含义，再结合单词释义检查理解。"
                        } else {
                            "💡 构词说明和联想用于辅助记忆，不代表全部词义，也不能任意组合出新单词。请结合释义与例句学习。"
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = formulaText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = evolutionTip,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Action CTA Bottom triggers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            if (!isSliced) {
                Button(
                    onClick = onSliceTrigger,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(48.dp)
                        .testTag("slice_word_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("查看构词拆解", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            } else {
                Button(
                    onClick = onReconstructTrigger,
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(48.dp)
                        .testTag("reconstruct_word_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("收起构词拆解", color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AssemblyPuzzleLab(
    word: WordFormula,
    components: List<AffixComponent>,
    viewModel: LexiViewModel,
    isEbbinghausActive: Boolean
) {
    val blockEntry = com.example.data.OfflineMorphology.displayEntry(word)
    if (blockEntry?.blocks?.isNotEmpty() == true) {
        BlockPractice(word, blockEntry, viewModel)
        return
    }
    val selectedPrefix by viewModel.userPrefixSelection.collectAsStateWithLifecycle()
    val selectedRoot by viewModel.userRootSelection.collectAsStateWithLifecycle()
    val selectedSuffix by viewModel.userSuffixSelection.collectAsStateWithLifecycle()
    val shakeTrigger by viewModel.shakeTrigger.collectAsStateWithLifecycle()
    val isSuccess by viewModel.assemblySuccess.collectAsStateWithLifecycle()
    val attempt by viewModel.assemblyAttempt.collectAsStateWithLifecycle()

    // Rapid shaking animation using jetpack compose's Animatable
    val errorActionOffset = remember { Animatable(0f) }

    LaunchedEffect(shakeTrigger) {
        if (shakeTrigger > 0) {
            // Trigger rapid displacement mismatch offset
            repeat(4) {
                errorActionOffset.animateTo(-25f, spring(dampingRatio = 0.2f, stiffness = 1500f))
                errorActionOffset.animateTo(25f, spring(dampingRatio = 0.2f, stiffness = 1500f))
            }
            errorActionOffset.animateTo(0f)
        }
    }

    // Distractors pool generator (select distractors dynamically)
    val prefixOptions = remember(word.id, components) {
        if (word.prefixId == null) {
            emptyList()
        } else {
            val corr = components.find { it.id == word.prefixId } ?: AffixComponent(
                id = word.prefixId,
                name = word.prefixPart ?: word.prefixId,
                type = "PREFIX",
                icon = "🧩",
                literalMeaning = "特征描述未收录",
                anchorWord = "",
                anchorDesc = ""
            )
            val allOtherPrefs = components.filter { it.type == "PREFIX" && it.id != word.prefixId && it.name != corr.name }.distinctBy { it.name }.shuffled()
            (listOf(corr) + allOtherPrefs.take(3)).shuffled()
        }
    }

    val rootOptions = remember(word.id, components) {
        if (word.rootId == null) {
            emptyList()
        } else {
            val corr = components.find { it.id == word.rootId } ?: AffixComponent(
                id = word.rootId,
                name = word.rootPart ?: word.rootId,
                type = "ROOT",
                icon = "⚙️",
                literalMeaning = "特征描述未收录",
                anchorWord = "",
                anchorDesc = ""
            )
            val allOtherRoots = components.filter { it.type == "ROOT" && it.id != word.rootId && it.name != corr.name }.distinctBy { it.name }.shuffled()
            (listOf(corr) + allOtherRoots.take(3)).shuffled()
        }
    }

    val suffixOptions = remember(word.id, components) {
        if (word.suffixId == null) {
            emptyList()
        } else {
            val corr = components.find { it.id == word.suffixId } ?: AffixComponent(
                id = word.suffixId,
                name = word.suffixPart ?: word.suffixId,
                type = "SUFFIX",
                icon = "🏷️",
                literalMeaning = "特征描述未收录",
                anchorWord = "",
                anchorDesc = ""
            )
            val allOtherSuff = components.filter { it.type == "SUFFIX" && it.id != word.suffixId && it.name != corr.name }.distinctBy { it.name }.shuffled()
            (listOf(corr) + allOtherSuff.take(2)).shuffled()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val pw = maxWidth.value / 100f
        val ph = maxHeight.value / 100f

        val slotWidth = (24f * pw).coerceIn(76f, 105f).dp
        val slotHeight = (12f * ph).coerceIn(58f, 76f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("assembly_scroller"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Targets clues desk
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "🎯 释义：${word.chineseMeaning}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (com.example.ui.hasTeachingExample(word)) {
                val formattedSentence = Regex(Regex.escape(word.spell), RegexOption.IGNORE_CASE).replace(word.sentenceEn, "_______")
                Text(
                    text = "练习例句：$formattedSentence",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary, // light purple highlight
                    fontWeight = FontWeight.SemiBold
                )

                Text(
                    text = "翻译：${word.sentenceZh}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val isSpellingMode = !com.example.data.OfflineMorphology.canAssemble(word)
            var spellInput by remember(word.id, attempt) { mutableStateOf("") }
            val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

            if (isSpellingMode) {
                // Spelling mode view instead of components view
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(errorActionOffset.value.roundToInt(), 0) }
                        .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                        .border(
                            width = if (shakeTrigger > 0 && errorActionOffset.value != 0f) 2.dp else 1.dp,
                            color = if (shakeTrigger > 0 && errorActionOffset.value != 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "✍️ 本词采用整词拼写，不使用未经核对的拆分",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    if (isSuccess) {
                        Text(
                            text = "🎉 拼写正确！可以继续学习下一个单词了。",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        androidx.compose.material3.OutlinedTextField(
                            value = spellInput,
                            onValueChange = { spellInput = it; viewModel.onWordAnswerEdited() },
                            label = { Text("输入拼写") },
                            modifier = Modifier.fillMaxWidth(0.9f),
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                imeAction = androidx.compose.ui.text.input.ImeAction.Done
                            ),
                            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                                onDone = {
                                    keyboardController?.hide()
                                    viewModel.validateSpelling(spellInput)
                                }
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                keyboardController?.hide()
                                viewModel.validateSpelling(spellInput)
                            },
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            Text("提交拼写")
                        }
                    }
                }
            } else {
            // Magnetic assembly slots
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(errorActionOffset.value.roundToInt(), 0) }
                    .background(MaterialTheme.colorScheme.background, RoundedCornerShape(16.dp))
                    .border(
                        width = if (shakeTrigger > 0 && errorActionOffset.value != 0f) 2.dp else 1.dp,
                        color = if (shakeTrigger > 0 && errorActionOffset.value != 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(16.dp)
                    )
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🧩 MODULE MAGNET SLOTS / 核心组装台",
                    fontSize = 11.sp,
                    color = if (shakeTrigger > 0 && errorActionOffset.value != 0f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Prefix Slot (Themed color - Blue)
                    AssemblyTargetSlot(
                        title = "前缀 PREFIX",
                        expectedId = word.prefixId,
                        selectedComponent = selectedPrefix,
                        required = word.prefixId != null,
                        color = MaterialTheme.colorScheme.primary,
                        slotWidth = slotWidth,
                        slotHeight = slotHeight,
                        onClear = { viewModel.choosePrefix(null) }
                    )

                    Text(text = "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    // Root Slot (Themed color - Gold)
                    AssemblyTargetSlot(
                        title = if (word.rootId?.startsWith("rule:base:") == true) "词基 BASE" else "词根 ROOT",
                        expectedId = word.rootId,
                        selectedComponent = selectedRoot,
                        required = word.rootId != null,
                        color = MaterialTheme.colorScheme.tertiary,
                        slotWidth = slotWidth,
                        slotHeight = slotHeight,
                        onClear = { viewModel.chooseRoot(null) }
                    )

                    Text(text = "+", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 20.sp, fontWeight = FontWeight.Bold)

                    // Suffix Slot (Themed color - Dark Teal)
                    AssemblyTargetSlot(
                        title = "后缀 SUFFIX",
                        expectedId = word.suffixId,
                        selectedComponent = selectedSuffix,
                        required = word.suffixId != null,
                        color = MaterialTheme.colorScheme.secondary,
                        slotWidth = slotWidth,
                        slotHeight = slotHeight,
                        onClear = { viewModel.chooseSuffix(null) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Options pool
            if (isSuccess) {
                // Decryption detailed explanation in soft dynamic green success block
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(14.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "拼写分块正确，可以继续学习。",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        val successFormula = if (isEbbinghausActive) {
                            val prefixCore = word.prefixPart?.substringBefore(" ")?.trim() ?: ""
                            val rootCore = word.rootPart?.substringBefore(" ")?.trim() ?: ""
                            val suffixCore = word.suffixPart?.substringBefore(" ")?.trim() ?: ""
                            listOf(prefixCore, rootCore, suffixCore).filter { it.isNotEmpty() }.joinToString(" + ") + " = ${word.spell}"
                        } else {
                            word.literalFormula
                        }

                        Text(
                            text = "拼写分块提示：$successFormula",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "本次拼装正确，可以继续下一个单词。请按计划复习巩固。",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else {
                // Cards selections pool
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "📦 COMPONENTS POOL / 点击卡片吸附至卡槽",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )

                    // Render Prefixes row
                    if (word.prefixId != null && selectedPrefix == null) {
                        Column {
                            Text("选择可用 [前缀] (PREFIX):", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                            LazyRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(prefixOptions) { item ->
                                    OptionBlock(item = item, color = MaterialTheme.colorScheme.primary, isEbbinghausActive = isEbbinghausActive) {
                                        viewModel.choosePrefix(item)
                                    }
                                }
                            }
                        }
                    }

                    // Render Roots row
                    if (word.rootId != null && selectedRoot == null) {
                        Column {
                            Text(if (word.rootId?.startsWith("rule:base:") == true) "选择合适 [词基] (BASE):" else "选择合适 [词根] (ROOT):", fontSize = 11.sp, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                            LazyRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(rootOptions) { item ->
                                    OptionBlock(item = item, color = MaterialTheme.colorScheme.tertiary, isEbbinghausActive = isEbbinghausActive) {
                                        viewModel.chooseRoot(item)
                                    }
                                }
                            }
                        }
                    }

                    // Render Suffixes row
                    if (word.suffixId != null && selectedSuffix == null) {
                        Column {
                            Text("选择匹配 [后缀] (SUFFIX):", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                            LazyRow(
                                modifier = Modifier.padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                items(suffixOptions) { item ->
                                    OptionBlock(item = item, color = MaterialTheme.colorScheme.secondary, isEbbinghausActive = isEbbinghausActive) {
                                        viewModel.chooseSuffix(item)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
fun AssemblyTargetSlot(
    title: String,
    expectedId: String?,
    selectedComponent: AffixComponent?,
    required: Boolean,
    color: Color,
    slotWidth: Dp,
    slotHeight: Dp,
    onClear: () -> Unit
) {
    val slotBorderColor = if (required) color.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(slotWidth)
    ) {
        val titleSize = (slotWidth.value * 0.09f).coerceIn(8f, 11f).sp
        Text(
            text = title,
            fontSize = titleSize,
            color = if (required) color else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val innerWidth = slotWidth - 6.dp

        if (!required) {
            // Slot is visually disabled
            Box(
                modifier = Modifier
                    .size(width = innerWidth, height = slotHeight)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                val disabledTextSize = (slotWidth.value * 0.12f).coerceIn(10f, 14f).sp
                Text("空置", fontSize = disabledTextSize, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            }
        } else if (selectedComponent != null) {
            // Component sits in
            val customBg = when (selectedComponent.type) {
                "PREFIX" -> MaterialTheme.colorScheme.primaryContainer
                "ROOT" -> MaterialTheme.colorScheme.tertiaryContainer
                else -> MaterialTheme.colorScheme.secondaryContainer
            }

            Box(
                modifier = Modifier
                    .size(width = innerWidth, height = slotHeight)
                    .background(customBg, RoundedCornerShape(10.dp))
                    .border(1.5.dp, color, RoundedCornerShape(10.dp))
                    .clickable { onClear() }
                    .padding(4.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val iconTextSize = (slotWidth.value * 0.16f).coerceIn(11f, 18f).sp
                    Text(
                        text = selectedComponent.icon,
                        fontSize = iconTextSize,
                        modifier = Modifier.padding(bottom = 2.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                            )
                        )
                    )
                    val labelTextSize = (slotWidth.value * 0.13f).coerceIn(10f, 14f).sp
                    Text(
                        text = selectedComponent.name,
                        fontSize = labelTextSize,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                    val hintTextSize = (slotWidth.value * 0.08f).coerceIn(7f, 10f).sp
                    Text(
                        text = "点击移除",
                        fontSize = hintTextSize,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            // Empty waiting compartment target
            Box(
                modifier = Modifier
                    .size(width = innerWidth, height = slotHeight)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(10.dp))
                    .border(
                        width = 1.dp,
                        color = slotBorderColor,
                        shape = RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                val waitingTextSize = (slotWidth.value * 0.11f).coerceIn(10f, 13f).sp
                Text(
                    text = "🔋 待吸附",
                    fontSize = waitingTextSize,
                    color = color.copy(alpha = 0.6f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun OptionBlock(
    item: AffixComponent,
    color: Color,
    isEbbinghausActive: Boolean = false,
    onSelect: () -> Unit
) {
    val blockBg = when (item.type) {
        "PREFIX" -> MaterialTheme.colorScheme.primaryContainer
        "ROOT" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    Box(
        modifier = Modifier
            .background(blockBg, RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("option_${item.id}"),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = item.icon, 
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = androidx.compose.ui.text.TextStyle(
                    platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                        includeFontPadding = false
                    ),
                    lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                        alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                        trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                    )
                )
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    text = item.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!isEbbinghausActive) {
                    Text(
                        text = item.literalMeaning.split("/").first(),
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
