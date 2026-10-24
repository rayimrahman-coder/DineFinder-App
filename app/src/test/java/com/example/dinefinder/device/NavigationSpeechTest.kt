package com.example.dinefinder.device

import android.os.Looper
import android.speech.tts.TextToSpeech
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class NavigationSpeechTest {
    @Test fun readyEngineSpeaksAndReleasesResourcesOnClose() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val speaker = NavigationSpeech(RuntimeEnvironment.getApplication())
        val tts = ShadowTextToSpeech.getLastTextToSpeechInstance()
        val shadow = shadowOf(tts)
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavigationSpeechStatus.Ready, speaker.status)
        assertTrue(speaker.speak("Turn left"))
        assertEquals("Turn left", shadow.lastSpokenText)
        assertEquals(TextToSpeech.QUEUE_FLUSH, shadow.queueMode)
        speaker.stop(); assertTrue(shadow.isStopped)
        speaker.close(); assertTrue(shadow.isShutdown)
        assertFalse(speaker.speak("Do not speak"))
    }
    @Test fun initFailureProvidesUnavailableStatusRatherThanCrashing() {
        val speaker = NavigationSpeech(RuntimeEnvironment.getApplication())
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.ERROR)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavigationSpeechStatus.Unavailable, speaker.status)
        assertFalse(speaker.speak("Turn left"))
        speaker.close()
    }
    @Test fun missingEnglishVoiceProvidesUnavailableStatus() {
        val speaker = NavigationSpeech(RuntimeEnvironment.getApplication())
        shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavigationSpeechStatus.Unavailable, speaker.status)
        assertFalse(speaker.speak("Turn left"))
        speaker.close()
    }
    @Test fun runtimeSpeechFailureLeavesWrittenNavigationUsable() {
        ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
        val speaker = NavigationSpeech(RuntimeEnvironment.getApplication())
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(speaker.speak("Turn left"))
        shadow.utteranceProgressListener.onError("1", TextToSpeech.ERROR_NETWORK)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(NavigationSpeechStatus.Unavailable, speaker.status)
        speaker.close()
    }
    @Test fun lateInitializationCannotRestartDisposedSpeaker() {
        val speaker = NavigationSpeech(RuntimeEnvironment.getApplication())
        val shadow = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
        speaker.close()
        shadow.onInitListener.onInit(TextToSpeech.SUCCESS)
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(speaker.speak("Closed")); assertTrue(shadow.isShutdown)
    }
}
