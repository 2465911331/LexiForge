package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.data.*
import com.example.ui.wordbookLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

private fun date(time: Long) = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(time))
private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private fun Context.reloadLearningData() {
    (activity() as? androidx.activity.ComponentActivity)?.let {
        it.viewModelStore.clear()
        it.recreate()
    }
}

@Composable
fun BackupSettingsCard(enabled: Boolean) {
    val context = LocalContext.current
    val service = remember { BackupRepository(context.applicationContext, LexiRepository.getDatabase(context).lexiDao()) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<RestorePreview?>(null) }
    var points by remember { mutableStateOf<List<File>?>(null) }
    var lastBackup by remember { mutableLongStateOf(context.getSharedPreferences("lexiforge_prefs", 0).getLong("last_verified_backup", 0)) }
    fun task(block: suspend () -> Unit) {
        if (busy) return
        busy = true
        message = null
        scope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (DataSafety.recoveryRequired) context.reloadLearningData()
                else message = "操作未完成：${e.localizedMessage ?: "无法读取或写入数据"}"
            } finally { busy = false }
        }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) task {
            service.export(uri)
            lastBackup = context.getSharedPreferences("lexiforge_prefs", 0).getLong("last_verified_backup", 0)
            message = "备份成功，已重新读取并验证文件。请妥善保存。"
        }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) task { preview = service.preview(uri) }
    }
    StudyCard("备份与恢复") {
        Text("备份包含全部词书、学习进度、作答记录、学习位置和设置。文件未加密，包含学习记录；不包含系统语音包。", style = MaterialTheme.typography.bodyMedium)
        Text(if (lastBackup == 0L) "尚无已验证的导出备份" else "最近已验证导出：${date(lastBackup)}", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { export.launch("LexiForge-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.ROOT).format(Date())}.lexiforge-backup") },
            enabled = enabled && !busy, modifier = Modifier.fillMaxWidth().testTag("export_backup")) { Text("导出备份") }
        OutlinedButton(onClick = { restore.launch(arrayOf("*/*")) }, enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth().testTag("import_backup")) { Text("从文件恢复") }
        TextButton(onClick = { task { points = withContext(Dispatchers.IO) { service.points() } } }, enabled = enabled && !busy,
            modifier = Modifier.fillMaxWidth().testTag("recovery_points")) { Text("查看本机恢复点") }
        Text("备份包含各词书的每日目标、检验记录和个人纠错。添加词书、重置进度或恢复前保存本机恢复点，日常保留最近 3 份。卸载应用会删除这些恢复点，请另外导出备份。新版备份需要 1.0.11 或更新版本恢复。", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, modifier = Modifier.testTag("backup_result")) }
    }
    points?.let { files ->
        AlertDialog(onDismissRequest = { points = null }, title = { Text("本机恢复点") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (files.isEmpty()) Text("暂无恢复点。替换词书、清空进度或恢复前会自动创建。")
                files.forEach { file -> TextButton(onClick = { points = null; task { preview = service.previewPoint(file) } }) { Text(date(file.lastModified())) } }
            }
        }, confirmButton = { TextButton(onClick = { points = null }) { Text("关闭") } })
    }
    preview?.let { item ->
        AlertDialog(onDismissRequest = { preview = null }, title = { Text("恢复预览") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("备份时间：${date(item.backup.manifest.createdAt)}")
                val data = item.backup.data
                Text("共 ${1 + data.otherBooks.size} 本词书 · 单词 ${item.backup.manifest.wordCount} 个\n作答记录 ${data.events.size} 条\n恢复后当前词书：${wordbookLabel(data.bookId)}")
                (listOf(data.bookId) + data.otherBooks.map { it.id }).forEach { Text("• ${wordbookLabel(it)}") }
                Text("将替换全部词书内容、学习记录和备份内设置。备份之后的学习记录会被替换。恢复前会保存当前数据为本机恢复点。")
                Text("恢复时会按本地解析包匹配全部词书，保留学习阶段、复习时间和已有作答记录。旧备份没有的历史不会补造。")
                Text("系统语音包不会恢复；换机后请重新试听本地英语。", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { TextButton(onClick = {
            preview = null
            task {
                service.restore(item)
                message = "恢复成功，正在重新载入…"
                context.reloadLearningData()
            }
        }, modifier = Modifier.testTag("confirm_restore")) { Text("替换并恢复") } },
            dismissButton = { TextButton(onClick = { preview = null }) { Text("取消") } })
    }
    if (busy) AlertDialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        title = { Text("正在处理学习数据") }, text = { Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth()); Text("完成后会显示校验结果，请稍候。")
        } }, confirmButton = {})
}
