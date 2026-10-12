package com.example.ui.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

data class SpeechEngineInfo(val packageName: String, val label: String)
data class LocalVoice(val name: String, val locale: Locale, val networkRequired: Boolean, val installed: Boolean, val quality: Int = 0)
data class SpeechReport(val message: String = "尚未试听本地语音", val enginePackage: String? = null, val voiceName: String? = null, val success: Boolean = false)

/** Platform boundary lets tests exercise delayed initialization and asynchronous synthesis errors. */
interface LocalSpeechEngine {
    suspend fun initialize(): Boolean
    fun voices(): List<LocalVoice>
    fun selectVoice(voice: LocalVoice): Boolean
    fun selectLegacyEnglish(): Boolean
    suspend fun speak(text: String): Int
    fun close()
}

fun offlineEnglishVoices(voices: List<LocalVoice>): List<LocalVoice> = voices
    .filter { it.locale.language == "en" && !it.networkRequired && it.installed }
    .sortedWith(compareBy<LocalVoice> { when (it.locale.country) { "US" -> 0; "GB" -> 1; else -> 2 } }
        .thenByDescending { it.quality }.thenBy { it.name })

class OfflineSpeech(
    private val engines: () -> List<SpeechEngineInfo>,
    private val factory: (SpeechEngineInfo) -> LocalSpeechEngine,
    private val report: (SpeechReport) -> Unit
) {
    private var cached: LocalSpeechEngine? = null
    private var cachedInfo: SpeechEngineInfo? = null
    private var cachedVoice: String? = null

    suspend fun speak(text: String) {
        val failures = mutableListOf<String>()
        val choices = engines()
        if (cachedInfo?.packageName !in choices.map { it.packageName }) close()
        for (info in choices) {
            val engine = if (cachedInfo == info) cached!! else {
                close()
                factory(info).also { cached = it; cachedInfo = info }
            }
            try {
                // Reuse a known local voice until an error, preference change or lifecycle stop.
                if (cachedVoice != null) {
                    report(SpeechReport("正在使用 ${info.label} 播放本地语音…", info.packageName, cachedVoice))
                    val code = withTimeoutOrNull(10_000) { engine.speak(text) }
                    if (code == 0) {
                        report(SpeechReport("本地播放完成 · ${info.label} · $cachedVoice", info.packageName, cachedVoice, true))
                        return
                    }
                    close()
                    failures += "${info.label}：播放失败${code?.let { "（$it）" } ?: "（超时）"}"
                    continue
                }
                report(SpeechReport("正在连接 ${info.label}…", info.packageName))
                if (withTimeoutOrNull(6_000) { engine.initialize() } != true) {
                    failures += "${info.label}：初始化失败或超时"
                    close()
                    continue
                }
                val advertised = engine.voices()
                val candidates = offlineEnglishVoices(advertised).take(3)
                val voiceNames = if (advertised.isEmpty()) {
                    // Some older OEM engines expose languages but no Voice list.
                    if (engine.selectLegacyEnglish()) listOf("英语兼容模式") else emptyList()
                } else candidates.map { it.name }
                if (voiceNames.isEmpty()) failures += "${info.label}：未找到已安装的离线英语音色"
                for (name in voiceNames) {
                    val voice = candidates.find { it.name == name }
                    if (voice != null && !engine.selectVoice(voice)) continue
                    report(SpeechReport("正在试听 ${info.label} · $name…", info.packageName, name))
                    val code = withTimeoutOrNull(10_000) { engine.speak(text) }
                    if (code == 0) {
                        cachedVoice = name
                        report(SpeechReport("本地播放完成 · ${info.label} · $name", info.packageName, name, true))
                        return
                    }
                    failures += "${info.label} / $name：播放失败${code?.let { "（$it）" } ?: "（超时）"}"
                }
            } catch (cancelled: CancellationException) {
                close()
                throw cancelled
            } catch (error: Exception) {
                failures += "${info.label}：${error.localizedMessage ?: "语音服务异常"}"
            }
            close()
        }
        report(SpeechReport(if (choices.isEmpty()) "未发现 TTS 语音引擎。请安装或启用支持英语的文字转语音引擎。" else
            "本地发音未成功，请在对应引擎中下载英语语音包后重新试听。\n${failures.distinct().joinToString("\n")}"))
    }

    fun close() {
        runCatching { cached?.close() }
        cached = null
        cachedInfo = null
        cachedVoice = null
    }
}
