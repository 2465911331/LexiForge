package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.SharedLearningPack

@Composable
fun LearningPackCredits() {
    var selected by remember { mutableStateOf<String?>(null) }
    TextButton(onClick = { selected = "credits" }, modifier = Modifier.testTag("learning_pack_credits")) { Text("资料来源与开源许可（离线）") }
    selected?.let { page ->
        val body = remember(page) { if (page == "credits") null else SharedLearningPack.resourceText("licenses/$page") }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("离线学习资料许可") },
            text = {
                Column(Modifier.fillMaxWidth().heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (body != null) SelectionContainer { Text(body, style = MaterialTheme.typography.bodySmall) }
                    else {
                        Text("MorphyNet · Khuyagbaatar Batsuren、Gábor Bella、Fausto Giunchiglia。源自 Wiktionary 构词资料；按 CC BY-SA 3.0 提供。本应用进行了词性、例外与拼写过滤、最多三层展开及格式转换；结果未经逐词核对。")
                        Text("来源：https://github.com/kbatsuren/MorphyNet\n版本：378144f64df58c78db5245af19d16a511ccecf3a", style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { selected = "CC-BY-SA-3.0.txt" }) { Text("阅读 CC BY-SA 3.0 全文") }
                        Text("CMUdict · Carnegie Mellon University。仅采用词典首个美式读音的音节数与主重音位置，不包含音频。来源：https://github.com/cmusphinx/cmudict")
                        TextButton(onClick = { selected = "CMUdict.txt" }) { Text("阅读 CMUdict 版权与许可") }
                        Text("Moby Hyphenator II · Grady Ward。作者于 2001 年 1 月授予公共领域；使用排版断词位置辅助拼写。来自 Project Gutenberg #3204，保留完整原始说明和使用条款。来源：https://www.gutenberg.org/ebooks/3204")
                        TextButton(onClick = { selected = "Moby.txt" }) { Text("阅读 Moby 原始说明与条款") }
                        Text("LexiForge 本地复合词表为项目整理。各份数据保留各自许可；MorphyNet 派生数据按 CC BY-SA 3.0 提供。完整清单、校验值与生成步骤随源码提供。")
                    }
                }
            }, confirmButton = { TextButton(onClick = { selected = if (page == "credits") null else "credits" }) { Text(if (page == "credits") "关闭" else "返回来源") } })
    }
}
