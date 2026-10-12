package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.data.BackupRepository
import com.example.data.LexiRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataStartup(content: @Composable () -> Unit) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var ready by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(attempt) {
        failure = null
        try {
            withContext(Dispatchers.IO) {
                val database = LexiRepository.getDatabase(context)
                // Opening explicitly here prevents Room errors escaping through a screen's Flow.
                database.openHelper.writableDatabase
                BackupRepository(context.applicationContext, database.lexiDao()).resumePending()
                LexiRepository(database.lexiDao(), context.applicationContext).initializeLibrary()
                BackupRepository(context.applicationContext, database.lexiDao()).repairOfflineContent()
            }
            val prefs = context.getSharedPreferences("lexiforge_prefs", 0)
            if (prefs.getBoolean("reload_learning_state", false)) {
                (context as? androidx.activity.ComponentActivity)?.viewModelStore?.clear()
                check(prefs.edit().remove("reload_learning_state").commit()) { "无法完成数据重新载入，请重试" }
            }
            ready = true
        } catch (e: Exception) {
            failure = "${e.javaClass.simpleName}: ${e.message.orEmpty().take(1200)}"
        }
    }
    val diagnostics = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) scope.launch {
            notice = try {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use {
                        it.write("LexiForge ${com.example.BuildConfig.VERSION_NAME}\nExpected database schema: 6\nAndroid API: ${android.os.Build.VERSION.SDK_INT}\n${failure.orEmpty()}\nNo learning records included.\n")
                    } ?: error("无法写入所选位置")
                }
                "诊断已导出。"
            } catch (e: Exception) { "诊断导出失败，请更换位置重试。" }
        }
    }
    if (ready) content() else Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text(if (failure == null) "正在检查学习数据" else "暂时无法打开学习数据", style = MaterialTheme.typography.headlineSmall)
            if (failure == null) LinearProgressIndicator(Modifier.fillMaxWidth()) else {
                Text("应用没有自动清空数据库。请勿卸载应用或清除存储。可能是数据版本不兼容、空间不足或上次恢复尚未完成。")
                Text("重试会尝试完成已确认的数据替换；持续失败时，请导出诊断供排查。应用没有删除已有本机恢复点。")
                Button(onClick = {
                    LexiRepository.closeDatabase()
                    attempt++
                }) { Text("重试打开") }
                OutlinedButton(onClick = { diagnostics.launch("LexiForge-data-diagnostic.txt") }) { Text("导出诊断") }
                notice?.let { Text(it) }
            }
        }
    }
}
