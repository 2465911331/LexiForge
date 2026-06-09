package com.example.ui.screens

import kotlin.math.roundToInt
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.IconButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.ui.LexiViewModel
import com.example.ui.components.FrequencyBadge

@Composable
fun MainDashboard(
    viewModel: LexiViewModel,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf("DASHBOARD") } // DASHBOARD, WORD_LIST, SETTINGS
    val activeSubScreen by viewModel.activeSubScreen.collectAsStateWithLifecycle()
    val showUnlockDialog by viewModel.showEbbinghausUnlockDialog.collectAsStateWithLifecycle()

    // Professional custom back press handler:
    // 1. If in other tabs (WORD_LIST, SETTINGS), return to DASHBOARD tab.
    // 2. If in secondary screns/subscreens of DASHBOARD, return to primary cockpit (NONE).
    // 3. Otherwise (on DASHBOARD head and subscreen is NONE), back goes to system home / exits normally.
    BackHandler(enabled = (activeTab != "DASHBOARD" || activeSubScreen != "NONE")) {
        if (activeTab != "DASHBOARD") {
            activeTab = "DASHBOARD"
        } else if (activeSubScreen != "NONE") {
            viewModel.navigateToSubScreen("NONE")
        }
    }

    if (showUnlockDialog) {
        AlertDialog(
            onDismissRequest = { /* Force explicit decision */ },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "📈 解锁脑科学记忆追踪",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "恭喜！您已成功物理拼装掌握了 20 个单词！\n\n系统现已自适应解锁「艾宾浩斯自适应复习表」。该机制依据忘却生理常态曲率线，精准帮您锁定每日最核心需要趁热打铁的少部分配方词汇。\n\n现在，您是否选择立即「开启」此模式，并设置后续每次进入软件时，默认优先推荐以此记忆模式流程开展训练？(随时可在设置中自主覆写开关)",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.handleEbbinghausDecision(true) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("是，默认开启学习")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { viewModel.handleEbbinghausDecision(false) }
                ) {
                    Text("否，稍后在设置中自主启用", color = MaterialTheme.colorScheme.error)
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceVariant, // M3 Light Lavender Tonal
                tonalElevation = 8.dp,
                modifier = Modifier.testTag("main_navigation_bar")
            ) {
                NavigationBarItem(
                    selected = activeTab == "DASHBOARD",
                    onClick = { activeTab = "DASHBOARD" },
                    icon = { Icon(Icons.Default.Home, contentDescription = "总控制中心") },
                    label = { Text("总控制中心", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "WORD_LIST",
                    onClick = { activeTab = "WORD_LIST" },
                    icon = { Icon(Icons.Default.List, contentDescription = "总单词表") },
                    label = { Text("总单词表", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )

                NavigationBarItem(
                    selected = activeTab == "SETTINGS",
                    onClick = { activeTab = "SETTINGS" },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "系统设置") },
                    label = { Text("系统设置", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedTextColor = MaterialTheme.colorScheme.onSurface,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer
                    )
                )
            }
        }
    ) { innerPadding ->
        Crossfade(
            targetState = activeTab,
            modifier = Modifier.padding(innerPadding)
        ) { currentTab ->
            when (currentTab) {
                "DASHBOARD" -> {
                    // Switch nested displays for secondary windows
                    when (activeSubScreen) {
                        "SWIPE", "NOON" -> NoonBlitzScreen(
                            viewModel = viewModel,
                            onNavigateToEvening = { viewModel.navigateToSubScreen("ASSEMBLY") }
                        )
                        "ASSEMBLY", "EVENING" -> EveningSiegeScreen(
                            viewModel = viewModel
                        )
                        "COMPONENTS_OVERVIEW" -> ComponentsOverviewScreen(
                            viewModel = viewModel
                        )
                        else -> DashboardCockpitView(
                            viewModel = viewModel
                        )
                    }
                }
                "WORD_LIST" -> GeneralWordListScreen(
                    viewModel = viewModel,
                    onNavigateToAssembly = {
                        activeTab = "DASHBOARD"
                        viewModel.navigateToSubScreen("ASSEMBLY", resetState = false)
                    }
                )
                "SETTINGS" -> SystemSettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun DashboardCockpitView(
    viewModel: LexiViewModel
) {
    val components by viewModel.allComponents.collectAsStateWithLifecycle()
    val words by viewModel.allWords.collectAsStateWithLifecycle()
    val timePref by viewModel.timePreference.collectAsStateWithLifecycle()
    val activeTimeMode by viewModel.currentActiveMode.collectAsStateWithLifecycle()
    val isEbbinghausActive by viewModel.isEbbinghausFilterActive.collectAsStateWithLifecycle()
    val isSimulatedTomorrow by viewModel.isSecondDaySimulationActive.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val isActualSecondDay = remember(context) {
        val sharedPrefs = context.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE)
        val firstLaunchTime = sharedPrefs.getLong("first_launch_time", 0L)
        if (firstLaunchTime == 0L) false else (System.currentTimeMillis() - firstLaunchTime) >= 24 * 3600 * 1000L
    }
    val isSecondDayEbbinghaus = isEbbinghausActive && (isSimulatedTomorrow || isActualSecondDay)

    // Calculations
    val masteredComponents = components.count { it.isMastered }
    val assembledWords = words.count { it.isAssembled }

    val componentsPercentage = if (components.isNotEmpty()) masteredComponents.toFloat() / components.size else 0f
    val wordsPercentage = if (words.isNotEmpty()) assembledWords.toFloat() / words.size else 0f

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Upper Title block - EtymoLogic Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Beautifully designed application brand logo based on LexiForge icon:
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.secondary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // Two miniature cards overlapping resembling the "L" and "F" physical assembly structure
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Card 1 (Left): L Part
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = 6.dp)
                                .size(14.dp, 20.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "L",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }

                        // Central Magnetic Connection indicator
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                        )

                        // Card 2 (Right): F Part
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .offset(x = (-6).dp)
                                .size(14.dp, 20.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "F",
                                color = MaterialTheme.colorScheme.tertiary,
                                fontWeight = FontWeight.Black,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
                Column(modifier = Modifier.padding(end = 8.dp)) {
                    Text(
                        text = "LexiForge 词汇熔炉",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
            }

            if (isEbbinghausActive) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📈 艾宾浩斯有效",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time-Segmented Workflow Toggle Row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                .padding(4.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                listOf("AUTO", "NOON", "EVENING").forEach { p ->
                    val isSel = timePref == p
                    val btnBg = if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent
                    val textCol = if (isSel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                    val lbl = when (p) {
                        "AUTO" -> "⚖️ 自适应模式"
                        "NOON" -> "☀️ 午间闪电战"
                        else -> "🌙 晚间阵地战"
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(btnBg)
                            .clickable { viewModel.setTimePreference(p) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = lbl,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = textCol
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Interactive Learning Launcher Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(24.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (isSecondDayEbbinghaus) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("📈 艾宾浩斯磁吸复习台 (已学单词复习)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("第二天的艾宾浩斯智能巩固：利用磁吸拼装高强度复习已学真词，不显示词缀及词根的释义！", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    } else if (activeTimeMode == "NOON") {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("☀️ 午间碎片闪电战 (快速刷词配对)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("通过极光速双向划卡，建立核心50个英语词缀/词根直觉映射。专注核心，不背废词！", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    } else {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text("🌙 晚间真题阵地战 (词级物理拆解组装)", color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("连贯解构四级真题长难词。将核心组件准确吸附卡位在拼装台上，深刻理解造词精髓！", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = {
                        val destination = if (isSecondDayEbbinghaus) "ASSEMBLY" else if (activeTimeMode == "NOON") "SWIPE" else "ASSEMBLY"
                        viewModel.navigateToSubScreen(destination)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("进入", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Frequency Selection Row
        val freqPref by viewModel.reviewFrequencyPref.collectAsStateWithLifecycle()
        val freqOptions = listOf("全部", "高频词", "中频词", "低频词")
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("🎯 复习偏好:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(end = 8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    freqOptions.forEach { opt ->
                        val isSelected = opt == freqPref
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                .clickable { viewModel.setReviewFrequencyPref(opt) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = opt,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Progress Guages Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(28.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ProgressRingWidget(
                    percentage = componentsPercentage,
                    title = "词根组件掌握",
                    countStr = "$masteredComponents / ${components.size}",
                    color = MaterialTheme.colorScheme.primary
                )

                Box(
                    modifier = Modifier
                        .height(84.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )

                ProgressRingWidget(
                    percentage = wordsPercentage,
                    title = "四级真词已物理组装",
                    countStr = "$assembledWords / ${words.size}",
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "🧩 各路高频核心组件激活概览：",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        val prefixes = components.filter { it.type == "PREFIX" }
        val roots = components.filter { it.type == "ROOT" }
        val suffixes = components.filter { it.type != "PREFIX" && it.type != "ROOT" }

        // Pick one representative of each type to display in the main cockpit (total 3 core preview components)
        val corePrefix = prefixes.firstOrNull()
        val coreRoot = roots.firstOrNull()
        val coreSuffix = suffixes.firstOrNull()
        val dashboardCoreItems = listOfNotNull(corePrefix, coreRoot, coreSuffix)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            dashboardCoreItems.forEach { item ->
                val (typeColor, typeBg) = when (item.type) {
                    "PREFIX" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    "ROOT" -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
                    else -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(92.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (item.isMastered) typeColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .padding(10.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(typeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.icon, 
                                    fontSize = 12.sp,
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
                            }
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(
                                        if (item.isMastered) typeColor else MaterialTheme.colorScheme.outlineVariant,
                                        CircleShape
                                    )
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = item.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Text(
                            text = item.literalMeaning.split("/").first().trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }
                }
            }
            if (dashboardCoreItems.size < 3) {
                repeat(3 - dashboardCoreItems.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Professional Button to secondary screen
        Button(
            onClick = { viewModel.navigateToSubScreen("COMPONENTS_OVERVIEW") },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "查看其他高频核心组件特征库 →",
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // High fidelity daily progress trend chart
        DailyProgressChartCard(
            components = components,
            words = words
        )
    }
}

@Composable
fun DailyProgressChartCard(
    components: List<com.example.data.AffixComponent>,
    words: List<com.example.data.WordFormula>,
    modifier: Modifier = Modifier
) {
    val masteredComponents = components.count { it.isMastered }
    val assembledWords = words.count { it.isAssembled }
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val gridLineColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, gridLineColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Adaptive Header & Legend Layout: Keeps title & legend robust with zero overflow
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "📈 脑科学每日进展追踪",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f, fill = false)
                )

                // Compact Legend
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(primaryColor)
                        )
                        Text("词根", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(secondaryColor)
                        )
                        Text("词汇/真词", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Subtitle on its own full line to allow wrapping and avoid pushing the legend
            Text(
                text = "展示最近 7 日脑科学物理拼装与词根记忆留存热度趋势",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Generate correct adaptive weekday names ending in '今天'
            val dayNamesMap = listOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")
            val days = List(7) { offset ->
                if (offset == 6) {
                    "今天"
                } else {
                    val backDays = 6 - offset
                    val tempCal = java.util.Calendar.getInstance()
                    tempCal.add(java.util.Calendar.DAY_OF_YEAR, -backDays)
                    val dow = tempCal.get(java.util.Calendar.DAY_OF_WEEK)
                    dayNamesMap[dow - 1]
                }
            }

            // Real-time calculated progress database timestamps
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
            calendar.set(java.util.Calendar.MINUTE, 0)
            calendar.set(java.util.Calendar.SECOND, 0)
            calendar.set(java.util.Calendar.MILLISECOND, 0)
            val startOfToday = calendar.timeInMillis

            val componentHistory = List(7) { idx ->
                if (idx == 6) {
                    masteredComponents
                } else {
                    val deltaDays = 6 - idx
                    val endOfPastDay = startOfToday - (deltaDays - 1) * 24 * 3600 * 1000L
                    components.count { 
                        it.isMastered && it.lastReviewedAt != 0L && it.lastReviewedAt < endOfPastDay 
                    }
                }
            }

            val wordHistory = List(7) { idx ->
                if (idx == 6) {
                    assembledWords
                } else {
                    val deltaDays = 6 - idx
                    val endOfPastDay = startOfToday - (deltaDays - 1) * 24 * 3600 * 1000L
                    words.count { 
                        it.isAssembled && it.lastReviewedAt != 0L && it.lastReviewedAt < endOfPastDay 
                    }
                }
            }

            val maxVal = maxOf(
                (componentHistory.maxOrNull() ?: 0),
                (wordHistory.maxOrNull() ?: 0),
                25
            ).toFloat()

            // Canvas Chart Graphics with auto adaptation logic
            Canvas(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                val width = size.width
                val height = size.height

                val paddingLeft = 32.dp.toPx()
                val paddingRight = 8.dp.toPx()
                val paddingTop = 12.dp.toPx()
                val paddingBottom = 22.dp.toPx()

                val chartWidth = width - paddingLeft - paddingRight
                val chartHeight = height - paddingTop - paddingBottom

                // Draw horizontal line rules & scale labels
                val gridLinesCount = 4
                for (i in 0..gridLinesCount) {
                    val progressRatio = i.toFloat() / gridLinesCount
                    val yValue = (maxVal * progressRatio).toInt()
                    val yPos = height - paddingBottom - (progressRatio * chartHeight)

                    drawLine(
                        color = gridLineColor.copy(alpha = 0.5f),
                        start = Offset(paddingLeft, yPos),
                        end = Offset(width - paddingRight, yPos),
                        strokeWidth = 1.dp.toPx()
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        yValue.toString(),
                        10.dp.toPx(),
                        yPos + 3.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = onSurfaceVariantColor
                            textSize = 8.sp.toPx()
                            isAntiAlias = true
                        }
                    )
                }

                // Draw customized dual dynamic bars
                val columnsCount = days.size
                val barSpacing = chartWidth / columnsCount
                val barGroupWidth = barSpacing * 0.7f
                val barWidth = barGroupWidth * 0.4f

                for (idx in 0 until columnsCount) {
                    val centerX = paddingLeft + (idx * barSpacing) + (barSpacing / 2)

                    val compVal = componentHistory[idx]
                    val wordVal = wordHistory[idx]

                    val compHeight = (compVal / maxVal) * chartHeight
                    val wordHeight = (wordVal / maxVal) * chartHeight

                    val compX = centerX - barWidth - 1.5.dp.toPx()
                    val wordX = centerX + 1.5.dp.toPx()

                    val baseLineY = height - paddingBottom

                    if (compHeight > 0) {
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(compX, baseLineY - compHeight),
                            size = Size(barWidth, compHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }

                    if (wordHeight > 0) {
                        drawRoundRect(
                            color = secondaryColor,
                            topLeft = Offset(wordX, baseLineY - wordHeight),
                            size = Size(barWidth, wordHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx(), 3.dp.toPx())
                        )
                    }

                    // Labeling on X Axis
                    drawContext.canvas.nativeCanvas.drawText(
                        days[idx],
                        centerX,
                        height - 5.dp.toPx(),
                        android.graphics.Paint().apply {
                            color = onSurfaceVariantColor
                            textSize = 9.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ComponentsOverviewScreen(
    viewModel: LexiViewModel
) {
    val components by viewModel.allComponents.collectAsStateWithLifecycle()
    var selectedCategory by remember { mutableStateOf("ALL") } // ALL, PREFIX, ROOT, SUFFIX

    val filteredList = remember(components, selectedCategory) {
        when (selectedCategory) {
            "PREFIX" -> components.filter { it.type == "PREFIX" }
            "ROOT" -> components.filter { it.type == "ROOT" }
            "SUFFIX" -> components.filter { it.type != "PREFIX" && it.type != "ROOT" }
            else -> {
                val prefixes = components.filter { it.type == "PREFIX" }
                val roots = components.filter { it.type == "ROOT" }
                val suffixes = components.filter { it.type != "PREFIX" && it.type != "ROOT" }
                prefixes + roots + suffixes
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // App header with Back Icon Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            IconButton(
                onClick = { viewModel.navigateToSubScreen("NONE") },
                modifier = Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "返回",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Column {
                Text(
                    text = "🧩 高频核心组件特征库",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "全量精选展示共 ${components.size} 种前缀、词根与词化后缀配置",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Categorized segment tabs for seamless filtration
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val categories = listOf(
                Pair("ALL", "全部 (${components.size})"),
                Pair("PREFIX", "前缀"),
                Pair("ROOT", "词根"),
                Pair("SUFFIX", "后缀")
            )

            categories.forEach { (cat, label) ->
                val isSelected = selectedCategory == cat
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
                        .clickable { selectedCategory = cat }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 10.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fully responsive adaptive-sized grid list to perfectly support diverse viewport sizes
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 100.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(filteredList) { item ->
                val (typeColor, typeBg) = when (item.type) {
                    "PREFIX" -> Pair(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                    "ROOT" -> Pair(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.secondaryContainer)
                    else -> Pair(MaterialTheme.colorScheme.tertiary, MaterialTheme.colorScheme.tertiaryContainer)
                }

                Box(
                    modifier = Modifier
                        .height(115.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .border(
                            width = 1.5.dp,
                            color = if (item.isMastered) typeColor else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(typeBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.icon, 
                                    fontSize = 14.sp,
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
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (item.isMastered) typeColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = if (item.isMastered) "已激活" else "待激活",
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (item.isMastered) typeColor else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = item.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Text(
                            text = item.literalMeaning.split("/").first().trim(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = when (item.type) {
                                "PREFIX" -> "[重要前缀]"
                                "ROOT" -> "[动作核心]"
                                else -> "[词性判定]"
                            },
                            color = typeColor.copy(alpha = 0.8f),
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun isNetworkAvailable(context: android.content.Context): Boolean {
    val cm = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
    if (cm != null) {
        val activeNetwork = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
    return false
}

private fun playWordListAudio(
    context: android.content.Context,
    word: String,
    tts: android.speech.tts.TextToSpeech?,
    ttsReady: Boolean
) {
    val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    if (!isNetworkAvailable(context)) {
        mainHandler.post {
            if (ttsReady && tts != null) {
                try {
                    tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "list_fallback_offline")
                } catch (ex: Exception) {
                    ex.printStackTrace()
                }
            }
        }
        return
    }

    try {
        val encodedWord = java.net.URLEncoder.encode(word, "UTF-8")
        val audioUrl = "https://dict.youdao.com/dictvoice?type=2&audio=$encodedWord"
        
        Thread {
            try {
                val mp = android.media.MediaPlayer.create(context, android.net.Uri.parse(audioUrl))
                if (mp != null) {
                    mp.setOnCompletionListener { player ->
                        player.release()
                    }
                    mp.setOnErrorListener { player, _, _ ->
                        player.release()
                        // Fallback to offline TTS
                        mainHandler.post {
                            if (ttsReady && tts != null) {
                                try {
                                    tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "list_fallback_err")
                                } catch (ex: Exception) {
                                    ex.printStackTrace()
                                }
                            }
                        }
                        true
                    }
                    mp.start()
                } else {
                    mainHandler.post {
                        if (ttsReady && tts != null) {
                            try {
                                tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "list_fallback_null")
                            } catch (ex: Exception) {
                                ex.printStackTrace()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                mainHandler.post {
                    if (ttsReady && tts != null) {
                        try {
                            tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "list_fallback_thread_exc")
                        } catch (ex: Exception) {
                            ex.printStackTrace()
                        }
                    }
                }
            }
        }.start()
    } catch (e: Exception) {
        e.printStackTrace()
        if (ttsReady && tts != null) {
            try {
                tts.speak(word, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "list_fallback_exc")
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }
    }
}

@Composable
fun GeneralWordListScreen(viewModel: LexiViewModel, onNavigateToAssembly: () -> Unit) {
    val context = LocalContext.current
    var tts by remember { mutableStateOf<android.speech.tts.TextToSpeech?>(null) }
    var ttsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val appCtx = context.applicationContext
        var speech: android.speech.tts.TextToSpeech? = null
        val listener = android.speech.tts.TextToSpeech.OnInitListener { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                speech?.let { s ->
                    try {
                        var result = s.setLanguage(java.util.Locale.US)
                        if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA ||
                            result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                            result = s.setLanguage(java.util.Locale.ENGLISH)
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    ttsReady = true
                }
            }
        }
        try {
            speech = com.example.ui.utils.TtsHelper.createTextToSpeech(appCtx, listener)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        tts = speech
        onDispose {
            speech?.stop()
            speech?.shutdown()
        }
    }

    val words by viewModel.filteredWords.collectAsStateWithLifecycle()
    val isEbbinghausActive by viewModel.isEbbinghausFilterActive.collectAsStateWithLifecycle()
    val allWordsState by viewModel.allWords.collectAsStateWithLifecycle()
    val assembledWords = allWordsState.count { it.isAssembled }
    var searchTxt by remember { mutableStateOf("") }

    val showWords = remember(words, searchTxt) {
        if (searchTxt.isEmpty()) words else words.filter {
            it.spell.contains(searchTxt, ignoreCase = true) || it.chineseMeaning.contains(searchTxt)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        // Upper controls
        Text("📔 四级总单词表", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("在此处集中探索词源配方或完成艾宾浩斯强化复习。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

        Spacer(modifier = Modifier.height(12.dp))

        // Search text field
        TextField(
            value = searchTxt,
            onValueChange = { searchTxt = it },
            placeholder = { Text("搜索单词拼写或核心释义...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = MaterialTheme.colorScheme.surface,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Inline Ebbinghaus selection switch
        val isLocked = assembledWords < 20
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (isLocked) MaterialTheme.colorScheme.surfaceVariant else if (isEbbinghausActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isLocked) "📋 显示全部词汇资源 (🧠 艾宾浩斯未解锁)" else if (isEbbinghausActive) "📈 艾宾浩斯智能复习表 (生效中)" else "📋 显示全部词汇资源",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (isEbbinghausActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (isLocked) "物理拼装完毕满 20 个单词将自动提示开机默认推荐开启自适应复习 (当前进度: $assembledWords / 20)" else if (isEbbinghausActive) "根据脑科学记忆周期自动过滤出今天应当立即巩固的词汇总数：${showWords.size}" else "展示数据库中加载的所有词汇组装配方",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = if (isLocked) false else isEbbinghausActive,
                onCheckedChange = { viewModel.setEbbinghausFilterActive(it) },
                enabled = !isLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                    uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.testTag("ebbinghaus_inline_toggle")
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (showWords.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🏖️", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isEbbinghausActive) "当前尚无艾宾浩斯复习任务！\n请先进入「总控制中心」开启拼装以生成你的定制记忆树。" else "未找到匹配您搜索条件的历史词汇",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(showWords) { word ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false)
                                ) {
                                    Text(
                                        text = word.spell,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Black,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f, fill = false),
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = androidx.compose.ui.text.TextStyle(
                                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                                includeFontPadding = false
                                            )
                                        )
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    FrequencyBadge(frequencyGroup = word.frequencyGroup)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .requiredSize(26.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .clickable {
                                                playWordListAudio(context, word.spell, tts, ttsReady)
                                            }
                                            .testTag("speak_word_list_btn_${word.id}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "播放读音",
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }

                                val completionCol = if (word.isAssembled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                                val completionBg = if (word.isAssembled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(completionBg)
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (word.isAssembled) "🧩 物理组装完成" else "🔧 待拼装挑战",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = completionCol
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "释义：${word.chineseMeaning}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)

                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text("📝 物理配方：${word.literalFormula}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                                    if (word.ebbinghausStage > 0) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "📈 经典记忆周阶：第 ${word.ebbinghausStage}/8 阶  |  ",
                                                fontSize = 9.sp,
                                                color = MaterialTheme.colorScheme.secondary,
                                                fontWeight = FontWeight.Bold
                                            )
                                            val remainingMins = ((word.nextReviewAt - System.currentTimeMillis()) / 60000).toInt()
                                            val timeStr = if (remainingMins <= 0) "⏰ 现在需要复习！" else "⏳ 距离下次复习大约 $remainingMins 分钟"
                                            Text(
                                                text = timeStr,
                                                fontSize = 9.sp,
                                                color = if (remainingMins <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = word.sentenceEn, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Medium)
                            Text(text = word.sentenceZh, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))

                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = {
                                        viewModel.selectWordForAssembly(word)
                                        onNavigateToAssembly()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("enter_assembly_btn_${word.id}"),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Build,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("进入物理拼装", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
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
fun SystemSettingsScreen(viewModel: LexiViewModel) {
    val context = LocalContext.current
    var ttsStatus by remember { mutableStateOf("🔍 正在检测系统语音引擎状态...") }
    var ttsLanguageInstalled by remember { mutableStateOf(false) }

    val enginesList = remember(context) { com.example.ui.utils.TtsHelper.getInstalledTtsEngines(context) }
    val sharedPrefs = remember(context) { context.getSharedPreferences("lexiforge_prefs", android.content.Context.MODE_PRIVATE) }
    var selectedEnginePackage by remember { mutableStateOf(sharedPrefs.getString("selected_tts_engine", "system")) }

    DisposableEffect(selectedEnginePackage) {
        val appCtx = context.applicationContext
        var speech: android.speech.tts.TextToSpeech? = null
        val listener = android.speech.tts.TextToSpeech.OnInitListener { status ->
            if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                speech?.let { s ->
                    try {
                        val result = s.isLanguageAvailable(java.util.Locale.US)
                        if (result == android.speech.tts.TextToSpeech.LANG_AVAILABLE ||
                            result == android.speech.tts.TextToSpeech.LANG_COUNTRY_AVAILABLE) {
                            ttsStatus = "🟢 本地英语（US）离线语音包已就绪"
                            ttsLanguageInstalled = true
                        } else if (result == android.speech.tts.TextToSpeech.LANG_MISSING_DATA) {
                            ttsStatus = "🟡 检测到系统缺少英语离线语音包"
                            ttsLanguageInstalled = false
                        } else if (result == android.speech.tts.TextToSpeech.LANG_NOT_SUPPORTED) {
                            ttsStatus = "❌ 引擎不支持英语（US）离线或暂无权限"
                            ttsLanguageInstalled = false
                        } else {
                            ttsStatus = "🟢 系统TTS引擎已激活，若无声音推荐安装离线包"
                            ttsLanguageInstalled = true
                        }
                    } catch (e: Exception) {
                        ttsStatus = "⚠️ 语音检测异常，将通过默认通道播报"
                        ttsLanguageInstalled = true
                    }
                }
            } else {
                ttsStatus = "⚠️ 系统TTS语音引擎初始化失败"
                ttsLanguageInstalled = false
            }
        }
        try {
            speech = com.example.ui.utils.TtsHelper.createTextToSpeech(appCtx, listener)
        } catch (e: Exception) {
            ttsStatus = "⚠️ TTS引擎异常: ${e.localizedMessage}"
            ttsLanguageInstalled = false
        }
        onDispose {
            try {
                speech?.shutdown()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    val timePref by viewModel.timePreference.collectAsStateWithLifecycle()
    val isEbbinghausActive by viewModel.isEbbinghausFilterActive.collectAsStateWithLifecycle()
    val isSimulatedTomorrow by viewModel.isSecondDaySimulationActive.collectAsStateWithLifecycle()
    val words by viewModel.allWords.collectAsStateWithLifecycle()
    val assembledWords = words.count { it.isAssembled }
    val isLocked = assembledWords < 20

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text("⚙️ 系统自适应设置", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
        Text("校准自适应双时段模式，自适应系统深浅色与动态配色色彩，或进行内存重置。", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))

        Spacer(modifier = Modifier.height(16.dp))

        // Card 0: Theme Settings
        val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
        val dynamicColorPref by viewModel.dynamicColor.collectAsStateWithLifecycle()

        Card(
            modifier = Modifier.fillMaxWidth().testTag("theme_settings_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🎨 系统主题与自适应配色", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("深度融合 Material Design 3 规范。支持深色模式且具备 Android 12+ 智能取色，开启后会自动拾取一加/OPPO/小米/华为或原生系统的壁纸色调：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                Text("配色主题模式：", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SYSTEM" to "🌗 跟随系统",
                        "LIGHT" to "☀️ 浅色主题",
                        "DARK" to "🌙 深色主题"
                    ).forEach { (mode, label) ->
                        val isSel = themeMode == mode
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .clickable { viewModel.setThemeMode(mode) }
                                .border(
                                    width = if (isSel) 1.5.dp else 1.dp,
                                    color = if (isSel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "✨ Android 12+ 动态高精度取色",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "基于 Wallpaper-picker 高级取色引擎，使 App 全局视觉基调完美沉浸并契合您的壁纸颜色偏好。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = dynamicColorPref,
                        onCheckedChange = { viewModel.setDynamicColor(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("dynamic_color_toggle")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1: Time Pref
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("⏱️ 时段自适应策略校准", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("系统会切分日间学习和晚间物理精研，可再次手动覆写：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(10.dp))

                listOf("AUTO", "NOON", "EVENING").forEach { type ->
                    val isSel = timePref == type
                    val label = when (type) {
                        "AUTO" -> "⚖️ 自适应模式 (11:00-14:30 触发闪电战，其余触发阵地战)"
                        "NOON" -> "☀️ 正午防线覆写 (强制进入词缀卡片闪电速刷模式)"
                        else -> "🌙 深夜堡垒覆写 (强制进入拆词物理拼装台实验室)"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable { viewModel.setTimePreference(type) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                .background(if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(text = label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = if (isSel) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1.5: Ebbinghaus Mode Switch (关闭艾宾浩斯模式)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🧠 艾宾浩斯记忆模式管理", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("学习词汇满 20 个后将自动激活艾宾浩斯追踪。您能在此处自主选择是否停用它：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                val rowBg = if (isLocked) {
                    MaterialTheme.colorScheme.surfaceVariant
                } else if (!isEbbinghausActive) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(rowBg)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isLocked) "🔒 艾宾浩斯记忆模式 (未解锁)" else "🚫 关闭艾宾浩斯模式 (停用智能复习机制)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isLocked) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (!isEbbinghausActive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isLocked) "需要完成 20 个物理单词拼装学习以解锁此模式自适应管理 (当前已拼装: $assembledWords/20)" else if (!isEbbinghausActive) "当前已彻底关闭艾宾浩斯记忆模式，系统将全自动展示所有单词配方并免除自适应过滤。" else "艾宾浩斯曲线正在后台温健运行，自适应筛选并推送每日核心巩固词汇。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = if (isLocked) false else !isEbbinghausActive,
                        onCheckedChange = { viewModel.setEbbinghausFilterActive(!it) },
                        enabled = !isLocked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("ebbinghaus_settings_toggle")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: Ebbinghaus tomorrow simulation
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🧬 艾宾浩斯脑科学模拟仓", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("记忆曲线依赖时间流动，为方便您免除等待 24小时，可开启时空模拟器：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSimulatedTomorrow) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isSimulatedTomorrow) "📅 模拟已推移至第二天 (Simulate Day 2)" else "⏳ 使用 system 标准真实时间流",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSimulatedTomorrow) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isSimulatedTomorrow) "当前仿真时间已推移24小时，艾宾浩斯复习队列已动态过滤并激活。" else "真实物理时间，适合日常自主训练、长期跟踪留存。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isSimulatedTomorrow,
                        onCheckedChange = { viewModel.toggleSecondDaySimulation() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier.testTag("simulate_tomorrow_toggle")
                    )
                }

                if (isEbbinghausActive) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(8.dp)
                    ) {
                        Text(
                            "💡 艾宾浩斯提醒：明天打开软件将全自动触发本复习模式！当前模拟开启后已实现联动自适应拦截及显示。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2: External Wordbook from KyleBing English-Vocabulary
        Card(
            modifier = Modifier.fillMaxWidth().testTag("kylebing_wordbook_settings_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "📚 词库自适应选择与物理拼装装载",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "连接并下载 KyleBing 官方学术词书。下载后系统将采用自适应拆词拓扑机制，全自动计算并组装物理拼拆模型：",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Currently active book badge
                val currentBookId by viewModel.currentWordbookId.collectAsStateWithLifecycle()
                val downloadStatus by viewModel.wordbookStatus.collectAsStateWithLifecycle()
                var selectedTempBookId by remember(currentBookId) { mutableStateOf(currentBookId) }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "当前激活词书：${
                            when (currentBookId) {
                                "built-in" -> "🌟 内置 Premium 拼装配方词单"
                                "3-CET4-顺序" -> "大学英语四级 (CET4)"
                                "4-CET6-顺序" -> "大学英语六级 (CET6)"
                                "5-考研-顺序" -> "硕士研究生入学考试 (Graduate)"
                                "6-托福-顺序" -> "托福核心词单 (TOEFL)"
                                "7-SAT-顺序" -> "SAT 词汇精选 (SAT)"
                                "2-高中-顺序" -> "高中英语词汇 (High School)"
                                "1-初中-顺序" -> "初中英语词汇 (Middle School)"
                                else -> currentBookId
                            }
                        }",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                val wordbooks = listOf(
                    Triple("built-in", "🌟 内置 Premium 词库", "系统原生 Premium 学术词词根公式卡与极客动画配方"),
                    Triple("3-CET4-顺序", "📕 大学英语四级 (CET4)", "高频四级核心词汇。自动分析并匹配前后缀，不匹配词自动匹配其词根与自适应拆解"),
                    Triple("4-CET6-顺序", "📘 大学英语六级 (CET6)", "CET-6 精选六级核心高真词单。自动计算匹配拓扑零件"),
                    Triple("5-考研-顺序", "🎓 考研词汇 (kaoyan)", "全国硕士研究生招生考试高频词，物理拼解自适应强化辅导"),
                    Triple("6-托福-顺序", "🌐 托福词海精选 (toefl)", "托福高频硬核学术词库。自适应拦截与多模块拼装卡对齐"),
                    Triple("7-SAT-顺序", "🔥 SAT 核心词汇 (sat)", "SAT 学术词难词词库挑战。形态自适应高级公式拼解"),
                    Triple("2-高中-顺序", "🏫 高中英语词单 (highschool)", "高中高频词汇必备，多维度物理拆解配方合成"),
                    Triple("1-初中-顺序", "🎒 初中英语词单 (middleschool)", "初中基础词汇核心框架，简单快速组装分析")
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    wordbooks.forEach { (id, label, desc) ->
                        val isSelected = selectedTempBookId == id
                        val isCurrentlyActive = currentBookId == id
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else Color.Transparent
                                )
                                .clickable { selectedTempBookId = id }
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = isSelected,
                                onClick = { selectedTempBookId = id }
                            )
                            
                            Spacer(modifier = Modifier.width(6.dp))
                            
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isCurrentlyActive) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(4.dp))
                                                .background(MaterialTheme.colorScheme.primary)
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        ) {
                                            Text("正在学习", fontSize = 8.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                                Text(
                                    text = desc,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                var selectedMirror by remember { mutableStateOf("ghproxy") }
                val mirrors = listOf(
                    "ghproxy" to "🚀 GHProxy (推荐优先)",
                    "gcore" to "⚡ Gcore CDN",
                    "fastly" to "🌩️ Fastly CDN",
                    "jsdelivr" to "🌐 jsDelivr CDN",
                    "gitmirror" to "🪞 GitMirror",
                    "raw" to "GitHub (需VPN)"
                )

                if (selectedTempBookId != "built-in") {
                    Text(
                        text = "下载通道选择 (若下载失败或超时，请切换节点)：",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        mirrors.forEach { (id, name) ->
                            val isMirrorSelected = selectedMirror == id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { selectedMirror = id }
                                    .background(
                                        if (isMirrorSelected) MaterialTheme.colorScheme.primaryContainer 
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .border(
                                        1.dp, 
                                        if (isMirrorSelected) MaterialTheme.colorScheme.primary 
                                        else Color.Transparent, 
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontWeight = if (isMirrorSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isMirrorSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Import trigger button
                Button(
                    onClick = {
                        val selectedLabel = wordbooks.find { it.first == selectedTempBookId }?.second ?: selectedTempBookId
                        if (selectedTempBookId == "built-in") {
                            viewModel.loadBuiltInWordbook()
                        } else {
                            val encodedBookId = java.net.URLEncoder.encode(selectedTempBookId, "UTF-8").replace("+", "%20")
                            val urlTemplate = when (selectedMirror) {
                                "ghproxy" -> "https://ghproxy.net/https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/json/${encodedBookId}.json"
                                "gcore" -> "https://gcore.jsdelivr.net/gh/KyleBing/english-vocabulary@master/json/${encodedBookId}.json"
                                "fastly" -> "https://fastly.jsdelivr.net/gh/KyleBing/english-vocabulary@master/json/${encodedBookId}.json"
                                "jsdelivr" -> "https://cdn.jsdelivr.net/gh/KyleBing/english-vocabulary@master/json/${encodedBookId}.json"
                                "gitmirror" -> "https://raw.gitmirror.com/KyleBing/english-vocabulary/master/json/${encodedBookId}.json"
                                else -> "https://raw.githubusercontent.com/KyleBing/english-vocabulary/master/json/${encodedBookId}.json"
                            }
                            viewModel.loadKyleBingWordbook(selectedTempBookId, selectedLabel, urlTemplate)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(42.dp).testTag("kylebing_import_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTempBookId == "built-in") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                        contentColor = if (selectedTempBookId == "built-in") MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp),
                    enabled = downloadStatus !is com.example.ui.WordbookDownloadState.Loading
                ) {
                    val btnText = if (selectedTempBookId == "built-in") "🔄 恢复并回退系统内置 Premium 拼单" else "⚡ 导入并拓扑自适应物理拆词模型"
                    Text(btnText, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Download/Parsing Status Box
                if (downloadStatus !is com.example.ui.WordbookDownloadState.Idle) {
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    val bgCol = when (downloadStatus) {
                        is com.example.ui.WordbookDownloadState.Loading -> MaterialTheme.colorScheme.surfaceVariant
                        is com.example.ui.WordbookDownloadState.Success -> MaterialTheme.colorScheme.primaryContainer
                        is com.example.ui.WordbookDownloadState.Error -> MaterialTheme.colorScheme.errorContainer
                        else -> Color.Transparent
                    }
                    
                    val txtCol = when (downloadStatus) {
                        is com.example.ui.WordbookDownloadState.Loading -> MaterialTheme.colorScheme.onSurfaceVariant
                        is com.example.ui.WordbookDownloadState.Success -> MaterialTheme.colorScheme.onPrimaryContainer
                        is com.example.ui.WordbookDownloadState.Error -> MaterialTheme.colorScheme.onErrorContainer
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    
                    val borderCol = when (downloadStatus) {
                        is com.example.ui.WordbookDownloadState.Loading -> MaterialTheme.colorScheme.outlineVariant
                        is com.example.ui.WordbookDownloadState.Success -> MaterialTheme.colorScheme.primary
                        is com.example.ui.WordbookDownloadState.Error -> MaterialTheme.colorScheme.error.copy(alpha = 0.3f)
                        else -> Color.Transparent
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(bgCol)
                            .border(1.dp, borderCol, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (downloadStatus is com.example.ui.WordbookDownloadState.Loading) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = txtCol
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = when (downloadStatus) {
                                        is com.example.ui.WordbookDownloadState.Loading -> "🔔 系统处理中"
                                        is com.example.ui.WordbookDownloadState.Success -> "✅ 处理成功"
                                        is com.example.ui.WordbookDownloadState.Error -> "❌ 发生了异常"
                                        else -> ""
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = txtCol
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            val statusMsg = when (val s = downloadStatus) {
                                is com.example.ui.WordbookDownloadState.Loading -> s.message
                                is com.example.ui.WordbookDownloadState.Success -> s.message
                                is com.example.ui.WordbookDownloadState.Error -> s.error
                                else -> ""
                            }
                            Text(
                                text = statusMsg,
                                fontSize = 10.sp,
                                color = txtCol,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 2.5: Offline Voice Engine Config
        Card(
            modifier = Modifier.fillMaxWidth().testTag("offline_voice_settings_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🔊 英语离线语音包配置", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("保障在弱网、无信号或无流量物理断网状态下，仍可100%零延时流畅播报：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                // Real-time Status Badge
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (ttsLanguageInstalled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "检测结果：$ttsStatus",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (ttsLanguageInstalled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                        Text(
                            text = if (ttsLanguageInstalled) "您的手机已全自动配置英语离线播报。断网无压力！" else "暂时无法100%在全部断网离线下本地解码，请点击下方进行一键配置升级。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Action Call-to-action Button
                Button(
                    onClick = {
                        try {
                            val intent = android.content.Intent().apply {
                                action = android.speech.tts.TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
                                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "无法直连，请在手机「设置」中手动配置TTS选项", android.widget.Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(40.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("🚀 一键配置/更新系统本地离线语音包", fontSize = 11.sp, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text("🔌 切换本地语音合成 (TTS) 引擎", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text("若一加/OPPO等机型的原生默认TTS引擎(或TTS Accessibility Engine)在断网时无法发声，建议安装并在此选中 Google TTS (系统将智能尝试多引擎自动兼容 fallback)：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(8.dp))

                // Default Intelligent fallback
                val isSystemSel = selectedEnginePackage == "system" || selectedEnginePackage.isNullOrEmpty()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSystemSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable {
                            sharedPrefs.edit().putString("selected_tts_engine", "system").apply()
                            selectedEnginePackage = "system"
                            android.widget.Toast.makeText(context, "已切换为：系统默认与多引擎智能自适应推荐", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.material3.RadioButton(
                        selected = isSystemSel,
                        onClick = {
                            sharedPrefs.edit().putString("selected_tts_engine", "system").apply()
                            selectedEnginePackage = "system"
                            android.widget.Toast.makeText(context, "已切换为：系统默认与多引擎智能自适应推荐", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column {
                        Text("系统内置智能托管 (自适应推荐)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        Text("优先推荐 Google TTS 或支持流畅 US 英语发音的本地引擎", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Dynamic options for installed ones
                enginesList.forEach { eng ->
                    val isSel = selectedEnginePackage == eng.name
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                            .clickable {
                                sharedPrefs.edit().putString("selected_tts_engine", eng.name).apply()
                                selectedEnginePackage = eng.name
                                android.widget.Toast.makeText(context, "已切换为：${eng.label ?: eng.name}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.RadioButton(
                            selected = isSel,
                            onClick = {
                                sharedPrefs.edit().putString("selected_tts_engine", eng.name).apply()
                                selectedEnginePackage = eng.name
                                android.widget.Toast.makeText(context, "已切换为：${eng.label ?: eng.name}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column {
                            Text(eng.label ?: eng.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text(eng.name, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Detailed step-by-step documentation card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(12.dp)
                ) {
                    Column {
                        Text("💡 极其便捷的手动配置与排查指南：", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "1. 【手动设置】：打开您的手机「设置」 ➔ 搜索「文本转语音」或「无障碍/系统设置 ➔ 文本转语音 (TTS) 输出」。\n" +
                            "2. 【选择引擎】：将「首选引擎」推荐设置为「谷歌文字转语音 (Google TTS)」或系统自带厂商高音质引擎。\n" +
                            "3. 【下载英语】：点击首选引擎右侧的【设置图标】➔ 选择【安装语音数据】➔ 选择英语 (美国/英国) 点击【下载安装】即可激活 100% 完美的永久本地离线播报！\n" +
                            "4. 【安全备份】：当您断网时，我们将自动由在线有道流瞬间零缝跌落切换至本地离线引擎，保障任何极区均可发音。",
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Card 3: Reset
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("🚨 存档危险操作区", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text("一键将整个 app 所有的词根卡片熟练度、物理拼装记录、艾宾浩斯记忆时间恢复出厂零进度状态：", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.resetAllLearningProgress() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reset_progress_button")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onError
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("删除档案并重置所有学习进度", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onError)
                }
            }
        }
    }
}


@Composable
fun ProgressRingWidget(
    percentage: Float,
    title: String,
    countStr: String,
    color: Color
) {
    val ringBg = MaterialTheme.colorScheme.outlineVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.width(132.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(76.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 8.dp.toPx()
                // Base background ring - light lavender neutral outline gray
                drawArc(
                    color = ringBg,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth)
                )

                // Foreground active arc
                drawArc(
                    color = color,
                    startAngle = -90f,
                    sweepAngle = percentage * 360f,
                    useCenter = false,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(percentage * 100).roundToInt()}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )

        Text(
            text = countStr,
            fontSize = 11.sp,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
