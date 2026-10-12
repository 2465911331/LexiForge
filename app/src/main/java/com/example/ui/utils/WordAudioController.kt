package com.example.ui.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class WordAudioController(
    private val scope: CoroutineScope,
    private val networkAvailable: () -> Boolean,
    private val preferLocal: () -> Boolean,
    private val online: suspend (String) -> Boolean,
    engines: () -> List<SpeechEngineInfo>,
    factory: (SpeechEngineInfo) -> LocalSpeechEngine
) {
    private val mutableReport = MutableStateFlow(SpeechReport())
    val report = mutableReport.asStateFlow()
    private val local = OfflineSpeech(engines, factory) { mutableReport.value = it }
    private var job: Job? = null

    fun play(word: String, localOnly: Boolean = false) {
        if (word.isBlank()) return
        val previous = job
        previous?.cancel()
        job = scope.launch {
            previous?.join()
            if (!localOnly && !preferLocal() && networkAvailable()) {
                mutableReport.value = SpeechReport("正在获取在线发音…")
                val played = try { withTimeoutOrNull(6_000) { online(word) } == true }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { false }
                if (played) { mutableReport.value = SpeechReport("在线播放完成", success = true); return@launch }
            }
            local.speak(word)
        }
    }

    fun stop() { job?.cancel(); local.close() }
}
