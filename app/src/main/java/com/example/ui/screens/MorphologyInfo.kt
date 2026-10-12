package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.OfflineMorphology
import com.example.data.WordFormula

@Composable
fun MorphologyInfo(word: WordFormula) {
    val entry = OfflineMorphology.displayEntry(word)
    val uri = LocalUriHandler.current
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("morphology_info")) {
        Text(entry?.kind?.label ?: if (entry?.ruleDerived == true) "规则推导 · 拼写分块" else if (entry?.assembly == true) "本地词源提示 · 拼写分块" else "整词拼写", style = MaterialTheme.typography.titleMedium)
        Text(if (entry != null) word.literalFormula else OfflineMorphology.WHOLE_WORD_NOTE, style = MaterialTheme.typography.bodyLarge)
        if (entry != null) {
            Text(when {
                entry.kind == com.example.data.SharedLearningPack.Kind.SPELLING -> "字母块用于拼写练习；音节数和重音来自另一份读音词典，不等于字母块数量。"
                entry.kind != null -> "来自独立本地资料；不要求词基也在当前词书。构词关系不能直接推出全部词义，发现疑点时以词书释义和可靠词典为准。"
                entry.ruleDerived -> "根据当前词书中的词基、词性和拼写规则推导，尚未经逐词核对；不代表历史词源或全部词义。"
                else -> "已对照参考资料整理；词源与联想用于辅助记忆，不代表全部词义或完整历史演变。"
            }, style = MaterialTheme.typography.bodySmall)
            Text("本地解析包 ${OfflineMorphology.VERSION} · 正文离线可用", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = {
                runCatching { uri.openUri(entry.source) }.onFailure {
                    android.widget.Toast.makeText(context, "没有可用的浏览器，解析正文仍可离线阅读。", android.widget.Toast.LENGTH_LONG).show()
                }
            }) { Text(if (entry.kind != null) "查看资料来源（浏览器联网）" else if (entry.ruleDerived) "查看构词规则参考（浏览器联网）" else "查看词源参考（浏览器联网）") }
        }
        Text("词频：未分级，尚无可靠统计依据。", style = MaterialTheme.typography.bodySmall)
    }
}
