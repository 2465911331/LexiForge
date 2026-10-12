package com.example.offline

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.example.BuildConfig
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex

fun interface TextListener { fun onText(bytes: ByteArray) }

internal object NativeLlama {
    init { System.loadLibrary("lexiforge_llama") }
    external fun begin()
    external fun cancel()
    external fun generate(path: String, prompt: ByteArray, maxTokens: Int, listener: TextListener)
}

interface OfflineEngine {
    suspend fun generate(prompt: String, onStatus: (String) -> Unit, onText: (String) -> Unit)
    fun cancel()
}

class AndroidOfflineEngine(private val context: Context) : OfflineEngine {
    private val ownsSession = AtomicBoolean(false)
    override suspend fun generate(prompt: String, onStatus: (String) -> Unit, onText: (String) -> Unit) {
        check(BuildConfig.EMBEDDED_MODEL) { "当前安装包未内置模型，请安装离线模型测试版" }
        check(Build.SUPPORTED_ABIS.contains("arm64-v8a")) { "离线模型测试版需要 64 位 ARM 安卓设备" }
        check(sessionLock.tryLock()) { "上一项生成正在释放内存，请稍后重试" }
        try {
            withContext(Dispatchers.IO) {
                val coroutine = currentCoroutineContext()
                val model = ModelFiles().prepare(File(context.noBackupFilesDir, "offline-models"),
                    { context.assets.open("models/${ModelFiles.NAME}") }, { coroutine.ensureActive() }, onStatus)
                val info = ActivityManager.MemoryInfo()
                (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(info)
                check(!info.lowMemory && info.availMem >= 1200L * 1024 * 1024) { "可用内存不足，请关闭其他应用后重试" }
                coroutine.ensureActive()
                NativeLlama.begin()
                ownsSession.set(true)
                // A cancellation arriving between begin() and ownership is rechecked before JNI.
                coroutine.ensureActive()
                onStatus("载入模型并生成中…")
                NativeLlama.generate(model.absolutePath, prompt.toByteArray(Charsets.UTF_8), 256) { bytes ->
                    if (coroutine.isActive) {
                        val text = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.IGNORE)
                            .onUnmappableCharacter(CodingErrorAction.IGNORE).decode(ByteBuffer.wrap(bytes)).toString()
                        onText(OfflinePrompt.visibleText(text))
                    }
                }
                coroutine.ensureActive()
            }
        } finally { ownsSession.set(false); sessionLock.unlock() }
    }
    override fun cancel() { if (ownsSession.get()) NativeLlama.cancel() }
    companion object { private val sessionLock = Mutex() }
}

data class AssistantState(val busy: Boolean = false, val status: String = "", val text: String = "", val error: String = "")

class OfflineAssistantController(
    private val engine: OfflineEngine,
    private val scope: CoroutineScope,
    private val timeoutMs: Long = 120_000
) {
    private val mutableState = MutableStateFlow(AssistantState())
    val state = mutableState.asStateFlow()
    private var work: Job? = null
    private var timedOut = false
    fun start(word: String, meaning: String, task: AssistantTask) {
        if (mutableState.value.busy) return
        timedOut = false
        mutableState.value = AssistantState(busy = true, status = "正在准备…")
        work = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val owner = currentCoroutineContext()
            val timer = launch {
                delay(timeoutMs)
                timedOut = true
                engine.cancel()
                owner.cancel()
            }
            try {
                engine.generate(OfflinePrompt.create(word, meaning, task),
                    { message -> if (owner.isActive) mutableState.update { it.copy(status = message) } },
                    { text -> if (owner.isActive) mutableState.update { it.copy(text = text) } })
                mutableState.update { it.copy(status = if (it.text.isBlank()) "未生成内容，请重试" else "草稿生成完毕") }
            } catch (_: CancellationException) {
                mutableState.update { it.copy(status = if (timedOut) "生成超时，已停止" else "已停止 · 已生成的部分仅供参考") }
            } catch (e: Throwable) {
                if (e is VirtualMachineError && e !is OutOfMemoryError) throw e
                mutableState.update { it.copy(status = "", error = if (e is OutOfMemoryError) "内存不足，请关闭其他应用后重试" else e.message ?: "模型运行失败，请重试") }
            } finally {
                timer.cancel()
                mutableState.update { it.copy(busy = false) }
            }
        }
    }
    fun stop() {
        if (!mutableState.value.busy) return
        mutableState.update { it.copy(status = "正在停止并释放内存…") }
        engine.cancel()
        work?.cancel()
    }
    fun clear() { if (!mutableState.value.busy) mutableState.value = AssistantState() }
}
