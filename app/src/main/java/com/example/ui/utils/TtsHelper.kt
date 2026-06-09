package com.example.ui.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.EngineInfo

object TtsHelper {

    private var cachedEngines: List<EngineInfo>? = null

    // Helper to fetch list of all available engines with in-memory caching
    fun getInstalledTtsEngines(context: Context): List<EngineInfo> {
        val cached = cachedEngines
        if (cached != null) return cached
        
        var temp: TextToSpeech? = null
        try {
            temp = TextToSpeech(context.applicationContext, null)
            val list = temp.engines ?: emptyList()
            if (list.isNotEmpty()) {
                cachedEngines = list
            }
            return list
        } catch (e: Exception) {
            e.printStackTrace()
            return emptyList()
        } finally {
            try {
                temp?.shutdown()
            } catch (e: Exception) {}
        }
    }

    // Smartly select the best default TTS engine based on package availability and user preference
    fun getBestEnginePackage(context: Context): String? {
        val sharedPrefs = context.getSharedPreferences("lexiforge_prefs", Context.MODE_PRIVATE)
        val savedEngine = sharedPrefs.getString("selected_tts_engine", null)
        
        // If user manually selected "system" default, we return null to initialize the default system engine
        if (savedEngine == "system") {
            return null
        }
        
        // If user has a valid saved engine, return it
        if (!savedEngine.isNullOrEmpty()) {
            return savedEngine
        }

        // Auto-detect best engine:
        val engines = getInstalledTtsEngines(context)
        if (engines.isNotEmpty()) {
            // First choice: Google Speech Services (very standard, robust offline English speech synthesis)
            val googleTts = engines.find { it.name == "com.google.android.tts" }
            if (googleTts != null) {
                return googleTts.name
            }

            // Second choice: Specific system engines containing words like "speech", "tts", "voicetalk"
            val voiceFriendly = engines.find {
                it.name.contains("google", ignoreCase = true) ||
                it.name.contains("speech", ignoreCase = true) ||
                it.name.contains("tts", ignoreCase = true)
            }
            if (voiceFriendly != null) {
                return voiceFriendly.name
            }
            
            // Third choice: Standard system-preferred engines
            // To be safe, if we fall back to OPPO/OnePlus voice engines, some of them are named:
            // "com.coloros.speech" or "com.heytap.speech" or "com.oppo.tts"
            val bbkBelt = engines.find {
                it.name.contains("coloros", ignoreCase = true) ||
                it.name.contains("heytap", ignoreCase = true) ||
                it.name.contains("oppo", ignoreCase = true) ||
                it.name.contains("oneplus", ignoreCase = true)
            }
            if (bbkBelt != null) {
                return bbkBelt.name
            }
        }
        
        // Return null to fall back to standard constructor which initializes the current system default
        return null
    }

    // Creates TextToSpeech instance safely with a selected package or default system
    fun createTextToSpeech(
        context: Context,
        listener: TextToSpeech.OnInitListener
    ): TextToSpeech {
        val appCtx = context.applicationContext
        val engineOpt = getBestEnginePackage(appCtx)
        return try {
            if (engineOpt != null) {
                TextToSpeech(appCtx, listener, engineOpt)
            } else {
                TextToSpeech(appCtx, listener)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            // Ultimate fallback
            TextToSpeech(appCtx, listener)
        }
    }
}
