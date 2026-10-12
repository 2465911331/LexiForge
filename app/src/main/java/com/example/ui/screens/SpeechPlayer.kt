package com.example.ui.screens

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.ui.utils.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

@Composable
internal fun rememberWordAudioController(): WordAudioController {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    val owner = LocalLifecycleOwner.current
    val controller = remember(context, scope) {
        WordAudioController(scope,
            networkAvailable = {
                val manager = context.getSystemService(ConnectivityManager::class.java)
                val caps = manager?.getNetworkCapabilities(manager.activeNetwork)
                caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            },
            preferLocal = { context.getSharedPreferences("lexiforge_prefs", 0).getBoolean("local_speech_only", false) },
            online = ::playOnlineWord,
            engines = { TtsHelper.engineChoices(context) },
            factory = { AndroidSpeechEngine(context, it) }
        )
    }
    DisposableEffect(controller, owner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) controller.stop() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); controller.stop() }
    }
    return controller
}

@Composable
internal fun rememberWordSpeaker(): (String) -> Unit {
    val context = LocalContext.current
    val controller = rememberWordAudioController()
    LaunchedEffect(controller) {
        controller.report.collect { result ->
            if (result.message.startsWith("未发现") || result.message.startsWith("本地发音未成功")) {
                Toast.makeText(context, "本地发音不可用，请到设置 → 发音 → 试听本地英语查看原因。", Toast.LENGTH_LONG).show()
            }
        }
    }
    return remember(controller) { { word -> controller.play(word) } }
}

/** Await completion, including asynchronous decoder/network failure; cancellation always releases. */
private suspend fun playOnlineWord(word: String): Boolean {
    val player = MediaPlayer()
    try {
        return suspendCancellableCoroutine { continuation ->
            player.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            player.setOnPreparedListener { if (continuation.isActive) it.start() }
            player.setOnCompletionListener { if (continuation.isActive) continuation.resume(true) }
            player.setOnErrorListener { _, _, _ -> if (continuation.isActive) continuation.resume(false); true }
            player.setDataSource("https://dict.youdao.com/dictvoice?type=2&audio=${java.net.URLEncoder.encode(word, "UTF-8")}")
            player.prepareAsync()
        }
    } finally { runCatching { player.release() } }
}
