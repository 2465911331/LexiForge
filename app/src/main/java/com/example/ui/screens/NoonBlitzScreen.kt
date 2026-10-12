package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.example.data.AffixComponent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.LexiViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun NoonBlitzScreen(
    viewModel: LexiViewModel,
    modifier: Modifier = Modifier,
    onNavigateToEvening: () -> Unit
) {
    AnswerSaveError(viewModel)
    val components by viewModel.noonBlitzComponents.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(components.firstOrNull()?.bookId, components.firstOrNull()?.id) {
        viewModel.beginComponent(components.firstOrNull())
    }
    val currentIndex = 0
    val showAnchorId by viewModel.showAnchorId.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val vw = screenWidth.value / 100f
        val vh = screenHeight.value / 100f

        val verticalPadding = (2f * vh).coerceIn(8f, 20f).dp
        val horizontalPadding = (4f * vw).coerceIn(12f, 24f).dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Back button row to Dashboard Cockpit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.navigateToSubScreen("NONE") }
                    .padding(vertical = 4.dp),
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

            // Header stats
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = (1f * vh).coerceIn(4f, 12f).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "构词卡片",
                        fontSize = (4.5f * vw).coerceIn(16f, 22f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "回忆含义，再标记是否记得",
                        fontSize = (3f * vw).coerceIn(10f, 13f).sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                        .padding(horizontal = (3f * vw).coerceIn(8f, 16f).dp, vertical = (1f * vh).coerceIn(4f, 8f).dp)
                ) {
                    Text(
                        text = "待学 ${components.size} 张",
                        fontSize = (3.5f * vw).coerceIn(11f, 15f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height((1.5f * vh).coerceIn(8f, 16f).dp))

            // Main swipe deck container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (currentIndex >= components.size || components.isEmpty()) {
                    // Swept card summary page
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight(0.96f)
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                            .border(2.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(24.dp))
                            .padding((5f * vw).coerceIn(16f, 32f).dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "✨",
                                fontSize = (15f * vw).coerceIn(48f, 72f).sp
                            )
                            Spacer(modifier = Modifier.height((2f * vh).coerceIn(8f, 16f).dp))
                            Text(
                                text = "暂时没有待学卡片",
                                fontSize = (5f * vw).coerceIn(18f, 24f).sp,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height((1.5f * vh).coerceIn(6f, 12f).dp))
                            Text(
                                text = "到达复习时间后，卡片会自动重新出现。也可以继续练习单词。",
                                fontSize = (3.5f * vw).coerceIn(11f, 15f).sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = (4f * vw).coerceIn(8f, 24f).dp)
                            )
                            Spacer(modifier = Modifier.height((4f * vh).coerceIn(16f, 32f).dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { viewModel.resetDeck() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                    shape = RoundedCornerShape((3f * vw).coerceIn(8f, 14f).dp),
                                    modifier = Modifier.height((11f * vh).coerceIn(38f, 50f).dp)
                                ) {
                                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size((4f * vw).coerceIn(14f, 20f).dp))
                                    Spacer(modifier = Modifier.width((1.5f * vw).coerceIn(4f, 8f).dp))
                                    Text("刷新队列", color = MaterialTheme.colorScheme.surface, fontSize = (3.5f * vw).coerceIn(11f, 14f).sp)
                                }
                                Spacer(modifier = Modifier.width((3f * vw).coerceIn(8f, 16f).dp))
                                Button(
                                    onClick = { onNavigateToEvening() },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape((3f * vw).coerceIn(8f, 14f).dp),
                                    modifier = Modifier.height((11f * vh).coerceIn(38f, 50f).dp)
                                ) {
                                    Text("练习单词", color = MaterialTheme.colorScheme.onPrimary, fontSize = (3.5f * vw).coerceIn(11f, 14f).sp)
                                }
                            }
                        }
                    }
                } else {
                    // Generate cards from back to front to render nicely, or just render the current one and the next one underneath for performance
                    val nextIndex = currentIndex + 1
                    if (nextIndex < components.size) {
                        val nextComp = components[nextIndex]
                        ComponentCard(
                            component = nextComp,
                            isFrontCard = false,
                            onSwipeLeft = {},
                            onSwipeRight = {},
                            onAnchorClick = {}
                        )
                    }

                    // Foreground active swipe card
                    val currentComp = components[currentIndex]
                    androidx.compose.runtime.key(currentComp.id) {
                        ComponentCard(
                            component = currentComp,
                            isFrontCard = true,
                            onSwipeLeft = { viewModel.swipeLeft(currentComp.id) },
                            onSwipeRight = { viewModel.swipeRight(currentComp.id) },
                            onAnchorClick = { viewModel.showAnchor(currentComp.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height((1.5f * vh).coerceIn(8f, 16f).dp))

            // Action labels description
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = (6f * vw).coerceIn(12f, 32f).dp, vertical = (1f * vh).coerceIn(4f, 12f).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "👈 向左：重修/未懂",
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold,
                    fontSize = (3.5f * vw).coerceIn(11f, 14f).sp
                )
                Text(
                    text = "向右：掌握/已识 👉",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = (3.5f * vw).coerceIn(11f, 14f).sp
                )
            }

            Spacer(modifier = Modifier.height((0.5f * vh).coerceIn(2f, 8f).dp))
        }

        // Anchor bottom sheet dialog
        AnimatedVisibility(
            visible = showAnchorId != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val anchorComp = components.find { it.id == showAnchorId }
            if (anchorComp != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .pointerInput(Unit) {
                            // Suppress taps on layout to close
                            detectDragGestures { _, _ -> }
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .border(1.5.dp, MaterialTheme.colorScheme.tertiary, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .verticalScroll(rememberScrollState())
                            .padding((5f * vw).coerceIn(16f, 28f).dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.Lightbulb,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size((6f * vw).coerceIn(18f, 28f).dp)
                                )
                                Spacer(modifier = Modifier.width((2f * vw).coerceIn(4f, 8f).dp))
                                Text(
                                    text = "认知记忆锚点",
                                    fontSize = (4.5f * vw).coerceIn(16f, 20f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            IconButton(
                                onClick = { viewModel.showAnchor(null) }
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.outline)
                            }
                        }

                        Spacer(modifier = Modifier.height((2f * vh).coerceIn(8f, 16f).dp))

                        Text(
                            text = "查看部件与参考词：",
                            fontSize = (3.5f * vw).coerceIn(11f, 15f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height((1.5f * vh).coerceIn(6f, 12f).dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                                .padding((3.5f * vw).coerceIn(12f, 20f).dp)
                        ) {
                            Column {
                                Text(
                                    text = "🎯 熟词密码：${anchorComp.anchorWord}",
                                    fontSize = (5f * vw).coerceIn(16f, 22f).sp,
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Spacer(modifier = Modifier.height((1f * vh).coerceIn(4f, 8f).dp))
                                Text(
                                    text = anchorComp.anchorDesc,
                                    fontSize = (3.5f * vw).coerceIn(11f, 14f).sp,
                                    lineHeight = (5f * vw).coerceIn(16f, 22f).sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height((3f * vh).coerceIn(16f, 28f).dp))

                        Button(
                            onClick = { viewModel.showAnchor(null) },
                            modifier = Modifier.fillMaxWidth().height((11f * vh).coerceIn(40f, 52f).dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onTertiaryContainer),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("收到！瞬间懂了", color = MaterialTheme.colorScheme.surface, fontSize = (3.8f * vw).coerceIn(12f, 15f).sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ComponentCard(
    component: AffixComponent,
    isFrontCard: Boolean,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onAnchorClick: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    // Animation states
    val swipeOffsetX = remember(component.id) { Animatable(0f) }

    // If swipe state completes externally, fly it out
    var dismissedLeft by remember(component.id) { mutableStateOf(false) }
    var dismissedRight by remember(component.id) { mutableStateOf(false) }

    val threshold = 280f

    // Return to center or complete swipe
    LaunchedEffect(dismissedLeft) {
        if (dismissedLeft) {
            swipeOffsetX.animateTo(-1200f)
            onSwipeLeft()
        }
    }

    LaunchedEffect(dismissedRight) {
        if (dismissedRight) {
            swipeOffsetX.animateTo(1200f)
            onSwipeRight()
        }
    }

    val typeColor = when (component.type) {
        "PREFIX" -> MaterialTheme.colorScheme.primary // Prefixes themed in Blue
        "ROOT" -> MaterialTheme.colorScheme.tertiary   // Roots themed in Gold
        else -> MaterialTheme.colorScheme.secondary     // Suffixes themed in Dark teal/cyan
    }

    val typeBg = when (component.type) {
        "PREFIX" -> MaterialTheme.colorScheme.primaryContainer
        "ROOT" -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.secondaryContainer
    }

    val labelText = when (component.type) {
        "PREFIX" -> "PREFIX前缀"
        "ROOT" -> if (component.id.startsWith("rule:base:")) "BASE词基" else "ROOT词根"
        else -> "SUFFIX后缀"
    }

    val rotation = swipeOffsetX.value / 15f

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .offset { IntOffset(swipeOffsetX.value.roundToInt(), (swipeOffsetX.value / 8f).roundToInt()) }
            .rotate(rotation)
            .pointerInput(isFrontCard) {
                if (!isFrontCard) return@pointerInput
                detectDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            if (swipeOffsetX.value > threshold) {
                                dismissedRight = true
                            } else if (swipeOffsetX.value < -threshold) {
                                dismissedLeft = true
                            } else {
                                swipeOffsetX.animateTo(0f, spring())
                            }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            swipeOffsetX.snapTo(swipeOffsetX.value + dragAmount.x)
                        }
                    }
                )
            }
            .testTag("component_card_${component.id}")
    ) {
        val cvw = maxWidth.value / 100f
        val cvh = maxHeight.value / 100f

        Card(
            modifier = Modifier.fillMaxSize(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = if (isFrontCard) 8.dp else 2.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shape = RoundedCornerShape((4f * cvw).coerceIn(16f, 28f).dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding((4f * cvw).coerceIn(12f, 24f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top section - Type Badge & Review indicators
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(typeBg, RoundedCornerShape((2f * cvw).coerceIn(6f, 12f).dp))
                            .border(1.dp, typeColor, RoundedCornerShape((2f * cvw).coerceIn(6f, 12f).dp))
                            .padding(horizontal = (3f * cvw).coerceIn(6f, 14f).dp, vertical = (1f * cvh).coerceIn(4f, 8f).dp)
                    ) {
                        Text(
                            text = labelText,
                            fontSize = (3.5f * cvw).coerceIn(10f, 13f).sp,
                            fontWeight = FontWeight.Bold,
                            color = typeColor
                        )
                    }

                    if (component.reviewCount > 0) {
                        Text(
                            text = "已复习 ${component.reviewCount} 次",
                            fontSize = (3.5f * cvw).coerceIn(10f, 13f).sp,
                            color = MaterialTheme.colorScheme.outline,
                            fontWeight = FontWeight.SemiBold
                        )
                    } else {
                        Text(
                            text = "NEW",
                            fontSize = (3.5f * cvw).coerceIn(10f, 13f).sp,
                            color = MaterialTheme.colorScheme.tertiary,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                // Middle section - Big Root Term & Graphical Semantic
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = component.name,
                        fontSize = (12f * cvw).coerceIn(30f, 42f).sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Serif,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height((1.5f * cvh).coerceIn(4f, 12f).dp))

                    // Minimalist physical geometric representation
                    Box(
                        modifier = Modifier
                            .size((22f * cvw).coerceIn(56f, 84f).dp)
                            .clip(CircleShape)
                            .background(typeBg)
                            .border(1.5.dp, typeColor, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = component.icon,
                            fontSize = (8f * cvw).coerceIn(20f, 32f).sp,
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

                    Spacer(modifier = Modifier.height((1.5f * cvh).coerceIn(4f, 12f).dp))

                    Text(
                        text = component.literalMeaning,
                        fontSize = (4.5f * cvw).coerceIn(14f, 20f).sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height((2f * cvh).coerceIn(4f, 16f).dp))

                // Bottom section - Anchor CTA button
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isFrontCard) {
                        Button(
                            onClick = onAnchorClick,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((11f * cvh).coerceIn(38f, 48f).dp)
                                .testTag("anchor_button"),
                            shape = RoundedCornerShape((2f * cvw).coerceIn(8f, 12f).dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                        ) {
                            Icon(
                                Icons.Filled.Lightbulb,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size((4.5f * cvw).coerceIn(14f, 18f).dp)
                            )
                            Spacer(modifier = Modifier.width((1.5f * cvw).coerceIn(4f, 8f).dp))
                            Text(
                                "解锁密码: \"${component.anchorWord}\" 锚点",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                fontSize = (3.5f * cvw).coerceIn(10f, 13f).sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    } else {
                        // Dummy spacer for placeholder preview cards
                        Spacer(modifier = Modifier.height((11f * cvh).coerceIn(38f, 48f).dp))
                    }
                }
            }
        }
    }
}
