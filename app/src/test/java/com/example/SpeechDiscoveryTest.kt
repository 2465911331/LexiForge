package com.example

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import android.content.pm.ServiceInfo
import android.speech.tts.TextToSpeech
import androidx.test.core.app.ApplicationProvider
import com.example.ui.utils.TtsHelper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SpeechDiscoveryTest {
    @Test fun `service discovery refreshes after installation and honors explicit preference`() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        app.getSharedPreferences("lexiforge_prefs", 0).edit().clear().commit()
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        fun add(packageId: String) {
            val resolved = ResolveInfo().apply {
                nonLocalizedLabel = packageId
                serviceInfo = ServiceInfo().apply {
                    name = "$packageId.SpeechService"
                    packageName = packageId
                    enabled = true
                    applicationInfo = ApplicationInfo().apply { packageName = packageId; enabled = true }
                }
            }
            shadowOf(app.packageManager).addResolveInfoForIntent(intent, resolved)
        }
        add("test.first")
        assertTrue(TtsHelper.getInstalledTtsEngines(app).any { it.packageName == "test.first" })
        add("test.new")
        assertTrue(TtsHelper.getInstalledTtsEngines(app).any { it.packageName == "test.new" })
        app.getSharedPreferences("lexiforge_prefs", 0).edit().putString("selected_tts_engine", "test.new").commit()
        assertEquals("test.new", TtsHelper.engineChoices(app).first().packageName)
    }

    @Test fun `voice data installer targets the exact chosen engine`() {
        val intent = TtsHelper.installIntent("test.accessibility.engine")
        assertEquals("test.accessibility.engine", intent.`package`)
        assertEquals(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA, intent.action)
    }
}
