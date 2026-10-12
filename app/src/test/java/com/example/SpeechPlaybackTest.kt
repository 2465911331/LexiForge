package com.example

import com.example.ui.utils.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

@OptIn(ExperimentalCoroutinesApi::class)
class SpeechPlaybackTest {
    private val engineInfo = SpeechEngineInfo("test.local", "本地引擎")
    private val offlineUs = LocalVoice("us-local", Locale.US, false, true)

    @Test fun `network and uninstalled voices are excluded while other English accents are allowed`() {
        val voices = listOf(
            offlineUs.copy(name = "online", networkRequired = true),
            offlineUs.copy(name = "download-pending", installed = false),
            offlineUs.copy(name = "chinese", locale = Locale.CHINESE),
            offlineUs.copy(name = "uk-local", locale = Locale.UK), offlineUs
        )
        assertEquals(listOf("us-local", "uk-local"), offlineEnglishVoices(voices).map { it.name })
    }

    @Test fun `offline route never requests the online recording and waits for engine initialization`() = runTest {
        val engine = FakeEngine(listOf(offlineUs), initDelay = 900)
        var onlineCalls = 0
        val controller = WordAudioController(backgroundScope, { false }, { false }, { onlineCalls++; true }, { listOf(engineInfo) }, { engine })
        controller.play("attract")
        runCurrent()
        assertEquals(0, onlineCalls)
        assertTrue(engine.spoken.isEmpty())
        advanceTimeBy(900); runCurrent()
        assertEquals(listOf("attract"), engine.spoken)
        assertTrue(controller.report.value.success)
        controller.stop()
    }

    @Test fun `local only setting and local preview bypass the online recording even with network`() = runTest {
        for ((preference, preview) in listOf(true to false, false to true)) {
            val engine = FakeEngine(listOf(offlineUs))
            var requests = 0
            val controller = WordAudioController(backgroundScope, { true }, { preference }, { requests++; true }, { listOf(engineInfo) }, { engine })
            controller.play("hello", localOnly = preview)
            runCurrent()
            assertEquals(0, requests)
            assertEquals(listOf("hello"), engine.spoken)
            controller.stop()
        }
    }

    @Test fun `online timeout falls back to local speech within a bounded wait`() = runTest {
        val engine = FakeEngine(listOf(offlineUs))
        var released = false
        val controller = WordAudioController(backgroundScope, { true }, { false }, {
            try { awaitCancellation() } finally { released = true }
        }, { listOf(engineInfo) }, { engine })
        controller.play("hello")
        runCurrent(); advanceTimeBy(6_000); runCurrent()
        assertTrue(released)
        assertEquals(listOf("hello"), engine.spoken)
        assertTrue(controller.report.value.success)
        controller.stop()
    }

    @Test fun `successful online recording does not initialize a local engine`() = runTest {
        var created = 0
        val controller = WordAudioController(backgroundScope, { true }, { false }, { true }, { listOf(engineInfo) }, {
            created++; FakeEngine(listOf(offlineUs))
        })
        controller.play("hello"); runCurrent()
        assertEquals(0, created)
        assertEquals("在线播放完成", controller.report.value.message)
        controller.stop()
    }

    @Test fun `initialization failure and cloud only engine fall back to installed local engine`() = runTest {
        val infos = listOf(SpeechEngineInfo("broken", "故障引擎"), SpeechEngineInfo("cloud", "云端引擎"), engineInfo)
        val broken = FakeEngine(emptyList(), initSuccess = false)
        val cloud = FakeEngine(listOf(offlineUs.copy(networkRequired = true)))
        val local = FakeEngine(listOf(offlineUs))
        val engines = mapOf("broken" to broken, "cloud" to cloud, engineInfo.packageName to local)
        var report = SpeechReport()
        val speech = OfflineSpeech({ infos }, { engines.getValue(it.packageName) }, { report = it })
        speech.speak("word")
        assertTrue(broken.closed)
        assertTrue(cloud.spoken.isEmpty())
        assertEquals(listOf("word"), local.spoken)
        assertTrue(report.success)
        assertEquals(engineInfo.packageName, report.enginePackage)
        speech.close()
    }

    @Test fun `asynchronous synthesis error tries another local voice and success needs completion`() = runTest {
        val engine = FakeEngine(listOf(offlineUs, offlineUs.copy(name = "uk", locale = Locale.UK)), speechDelay = 150,
            results = mutableListOf(-9, 0))
        var report = SpeechReport()
        val speech = OfflineSpeech({ listOf(engineInfo) }, { engine }, { report = it })
        val task = launch { speech.speak("word") }
        runCurrent()
        assertFalse(report.success)
        advanceTimeBy(150); runCurrent()
        assertFalse(report.success)
        advanceTimeBy(150); runCurrent()
        task.join()
        assertEquals(listOf("us-local", "uk"), engine.selected)
        assertTrue(report.success)
        speech.close()
    }

    @Test fun `missing local voices and failed legacy language selection never pretend readiness`() = runTest {
        for (voices in listOf(emptyList(), listOf(offlineUs.copy(installed = false)))) {
            val engine = FakeEngine(voices, legacy = false)
            var report = SpeechReport()
            OfflineSpeech({ listOf(engineInfo) }, { engine }, { report = it }).speak("word")
            assertFalse(report.success)
            assertTrue(engine.spoken.isEmpty())
            assertTrue(report.message.contains("未找到已安装的离线英语音色"))
        }
    }

    @Test fun `legacy engine without voice inventory can play using English compatibility mode`() = runTest {
        val engine = FakeEngine(emptyList(), legacy = true)
        var report = SpeechReport()
        val speech = OfflineSpeech({ listOf(engineInfo) }, { engine }, { report = it })
        speech.speak("word")
        assertTrue(report.success)
        assertEquals("英语兼容模式", report.voiceName)
        speech.close()
    }

    @Test fun `rapid new request cancels initialization and never speaks the old word`() = runTest {
        val created = mutableListOf<FakeEngine>()
        val controller = WordAudioController(backgroundScope, { false }, { false }, { error("Unexpected network") }, { listOf(engineInfo) }, {
            FakeEngine(listOf(offlineUs), initDelay = 500).also { created += it }
        })
        controller.play("old"); runCurrent()
        controller.play("new"); runCurrent()
        advanceTimeBy(500); runCurrent()
        assertTrue(created.first().closed)
        assertEquals(listOf("new"), created.flatMap { it.spoken })
        controller.stop()
    }

    @Test fun `leaving the screen cancels slow initialization and reports no false success`() = runTest {
        val engine = FakeEngine(listOf(offlineUs), initDelay = 4000)
        val controller = WordAudioController(backgroundScope, { false }, { false }, { false }, { listOf(engineInfo) }, { engine })
        controller.play("word"); runCurrent(); controller.stop()
        advanceTimeBy(6000); runCurrent()
        assertTrue(engine.closed)
        assertTrue(engine.spoken.isEmpty())
        assertFalse(controller.report.value.success)
    }

    @Test fun `hung engine initialization times out and the next engine is tried`() = runTest {
        val broken = FakeEngine(listOf(offlineUs), initDelay = 60_000)
        val good = FakeEngine(listOf(offlineUs))
        var report = SpeechReport()
        val speech = OfflineSpeech({ listOf(SpeechEngineInfo("hung", "无响应引擎"), engineInfo) },
            { if (it.packageName == "hung") broken else good }, { report = it })
        speech.speak("word")
        assertTrue(broken.closed)
        assertEquals(listOf("word"), good.spoken)
        assertTrue(report.success)
        speech.close()
    }

    private class FakeEngine(
        private val advertised: List<LocalVoice>, private val initSuccess: Boolean = true,
        private val initDelay: Long = 0, private val legacy: Boolean = false,
        private val speechDelay: Long = 0, private val results: MutableList<Int> = mutableListOf()
    ) : LocalSpeechEngine {
        val spoken = mutableListOf<String>()
        val selected = mutableListOf<String>()
        var closed = false
        override suspend fun initialize(): Boolean { delay(initDelay); return initSuccess }
        override fun voices() = advertised
        override fun selectVoice(voice: LocalVoice): Boolean { selected += voice.name; return true }
        override fun selectLegacyEnglish() = legacy
        override suspend fun speak(text: String): Int { spoken += text; delay(speechDelay); return if (results.isEmpty()) 0 else results.removeAt(0) }
        override fun close() { closed = true }
    }
}
