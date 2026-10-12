package com.example.ui.screens

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.utils.TtsHelper

@Composable
internal fun SpeechSettingsCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("lexiforge_prefs", 0) }
    val controller = rememberWordAudioController()
    val report by controller.report.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf(prefs.getString("selected_tts_engine", "system") ?: "system") }
    var localOnly by remember { mutableStateOf(prefs.getBoolean("local_speech_only", false)) }
    var showEngines by rememberSaveable { mutableStateOf(false) }
    var refresh by remember { mutableStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refresh++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val engines = remember(refresh, showEngines) { TtsHelper.getInstalledTtsEngines(context) }
    val default = remember(refresh) { TtsHelper.defaultEngine(context) }
    val target = if (selected == "system") default else selected
    val targetLabel = engines.find { it.packageName == target }?.label ?: target ?: "未检测到默认引擎"

    fun openSystemSpeechSettings() {
        try { context.startActivity(Intent("com.android.settings.TTS_SETTINGS")) }
        catch (_: Exception) {
            try {
                context.startActivity(Intent(Settings.ACTION_SETTINGS))
                Toast.makeText(context, "请搜索“文字转语音”，检查英语语音和媒体音量。", Toast.LENGTH_LONG).show()
            } catch (_: Exception) { Toast.makeText(context, "请手动打开手机的文字转语音设置。", Toast.LENGTH_LONG).show() }
        }
    }
    StudyCard("发音") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text("仅使用本地语音", style = MaterialTheme.typography.bodyLarge)
                Text("开启后不请求在线单词音频；关闭时，断网或在线播放失败也会尝试本地语音。", style = MaterialTheme.typography.bodySmall)
            }
            Switch(localOnly, onCheckedChange = {
                localOnly = it
                prefs.edit().putBoolean("local_speech_only", it).apply()
                controller.stop()
            }, modifier = Modifier.testTag("local_speech_only"))
        }
        Text("首选引擎：$targetLabel", style = MaterialTheme.typography.bodyMedium)
        Text("首选不可用时会尝试其他已安装引擎。语音包必须属于实际使用的引擎。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { controller.stop(); showEngines = true }, modifier = Modifier.fillMaxWidth()) { Text("选择设备语音引擎") }
        Button(onClick = { controller.stop(); controller.play("Hello. Welcome to LexiForge.", localOnly = true) }, modifier = Modifier.fillMaxWidth().testTag("test_local_speech")) {
            Text("试听本地英语")
        }
        Text(report.message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("speech_diagnostics"))
        if (report.success) Text("如未听到声音，请检查媒体音量和蓝牙输出。兼容模式仍建议开启飞行模式复测。", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = {
            if (target != null && engines.any { it.packageName == target }) {
                try { context.startActivity(TtsHelper.installIntent(target)) }
                catch (_: Exception) {
                    Toast.makeText(context, "该引擎未提供语音包安装入口，请在其设置中检查英语语音。", Toast.LENGTH_LONG).show()
                    openSystemSpeechSettings()
                }
            } else openSystemSpeechSettings()
        }, modifier = Modifier.fillMaxWidth().testTag("install_speech_data")) { Text("打开首选引擎的语音包页面") }
        TextButton(onClick = ::openSystemSpeechSettings, modifier = Modifier.fillMaxWidth()) { Text("打开系统语音设置") }
        Text("下载结束后返回这里重新试听。系统语音助手与可供应用调用的离线英语语音不一定相同。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (showEngines) AlertDialog(onDismissRequest = { showEngines = false }, title = { Text("设备语音引擎") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            (listOf("system" to "系统默认") + engines.map { it.packageName to it.label }).forEach { (id, label) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected == id, role = Role.RadioButton, onClick = {
                    selected = id
                    prefs.edit().putString("selected_tts_engine", id).apply()
                    controller.stop()
                    showEngines = false
                }), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected == id, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(label)
                        if (id != "system") Text(id, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (engines.isEmpty()) Text("没有发现可调用的 TTS 服务，请先安装或启用语音引擎。")
        }
    }, confirmButton = { TextButton(onClick = { showEngines = false }) { Text("关闭") } })
}
