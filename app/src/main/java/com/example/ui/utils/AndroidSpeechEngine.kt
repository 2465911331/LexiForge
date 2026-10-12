package com.example.ui.utils

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

object TtsHelper {
    /** Query services directly, without initializing or permanently caching an engine list. */
    @Suppress("DEPRECATION")
    fun getInstalledTtsEngines(context: Context): List<SpeechEngineInfo> = runCatching {
        context.packageManager.queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0)
            .mapNotNull { result -> result.serviceInfo?.takeIf { it.enabled && it.applicationInfo.enabled }?.let {
                SpeechEngineInfo(it.packageName, result.loadLabel(context.packageManager)?.toString() ?: it.packageName)
            } }.distinctBy { it.packageName }.sortedBy { it.label }
    }.getOrDefault(emptyList())

    @Suppress("DEPRECATION")
    fun defaultEngine(context: Context): String? = runCatching {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.TTS_DEFAULT_SYNTH)
    }.getOrNull()

    fun engineChoices(context: Context): List<SpeechEngineInfo> {
        val installed = getInstalledTtsEngines(context)
        val preferred = context.getSharedPreferences("lexiforge_prefs", 0).getString("selected_tts_engine", "system")
        val system = defaultEngine(context)
        return installed.sortedBy { when (it.packageName) { preferred -> 0; system -> 1; else -> 2 } }
    }

    fun installIntent(enginePackage: String) = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).setPackage(enginePackage)
}

private fun Voice.local() = LocalVoice(name, locale, isNetworkConnectionRequired,
    !(features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) ?: false), quality)

class AndroidSpeechEngine(private val context: Context, private val engine: SpeechEngineInfo) : LocalSpeechEngine {
    private val main = Handler(Looper.getMainLooper())
    private var speech: TextToSpeech? = null
    private var pending: Pair<String, CancellableContinuation<Int>>? = null
    private var sequence = 0L

    override suspend fun initialize(): Boolean {
      val initialized = suspendCancellableCoroutine<Boolean> { continuation ->
        val instance = TextToSpeech(context.applicationContext, { status ->
            // Posting also handles OEMs that report initialization during the constructor.
            main.post { if (continuation.isActive) continuation.resume(status == TextToSpeech.SUCCESS) }
        }, engine.packageName)
        speech = instance
        instance.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) = finish(utteranceId, TextToSpeech.SUCCESS)
            @Deprecated("Platform compatibility callback")
            override fun onError(utteranceId: String?) = finish(utteranceId, TextToSpeech.ERROR)
            override fun onError(utteranceId: String?, errorCode: Int) = finish(utteranceId, errorCode)
            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId, TextToSpeech.ERROR)
        })
        continuation.invokeOnCancellation { main.post { if (speech === instance) close() } }
      }
      if (initialized) speech?.setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
          .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
      return initialized
    }

    private fun finish(id: String?, code: Int) { main.post {
        val request = pending
        if (request != null && request.first == id) {
            pending = null
            if (request.second.isActive) request.second.resume(code)
        }
    } }

    override fun voices() = speech?.voices.orEmpty().map { it.local() }

    override fun selectVoice(voice: LocalVoice): Boolean {
        val tts = speech ?: return false
        val chosen = tts.voices?.find { it.name == voice.name } ?: return false
        if (tts.setVoice(chosen) != TextToSpeech.SUCCESS) return false
        // Verify that an OEM engine didn't silently substitute a network/default voice.
        return tts.voice?.local()?.let { it.locale.language == "en" && it.installed && !it.networkRequired } == true
    }

    @Suppress("DEPRECATION")
    override fun selectLegacyEnglish(): Boolean {
        val tts = speech ?: return false
        for (locale in listOf(Locale.US, Locale.UK, Locale.ENGLISH)) {
            if (tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) continue
            val actual = tts.voice?.local()
            if (actual != null) {
                if (actual.locale.language == "en" && actual.installed && !actual.networkRequired) return true
                continue
            }
            val features = tts.getFeatures(locale).orEmpty()
            if (TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS in features &&
                TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS !in features) continue
            return true // Legacy capability is confirmed only by a completed local-only test.
        }
        return false
    }

    @Suppress("DEPRECATION")
    override suspend fun speak(text: String): Int = suspendCancellableCoroutine { continuation ->
        val tts = speech
        if (tts == null) { continuation.resume(TextToSpeech.ERROR); return@suspendCancellableCoroutine }
        val id = "lexiforge_${++sequence}"
        pending = id to continuation
        val params = Bundle().apply {
            putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, AudioManager.STREAM_MUSIC)
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1f)
            putString(TextToSpeech.Engine.KEY_FEATURE_EMBEDDED_SYNTHESIS, "true")
            putString(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SYNTHESIS, "false")
        }
        continuation.invokeOnCancellation { main.post {
            if (pending?.first == id) { pending = null; runCatching { tts.stop() } }
        } }
        try {
            if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, params, id) != TextToSpeech.SUCCESS) finish(id, TextToSpeech.ERROR)
        } catch (_: Exception) { finish(id, TextToSpeech.ERROR) }
    }

    override fun close() {
        val old = speech
        speech = null
        pending?.second?.cancel()
        pending = null
        runCatching { old?.stop() }
        runCatching { old?.shutdown() }
    }
}
