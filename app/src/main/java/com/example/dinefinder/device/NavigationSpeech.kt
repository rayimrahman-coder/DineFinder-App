package com.example.dinefinder.device

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dinefinder.core.NavigationVoiceCue
import com.example.dinefinder.core.NavigationVoiceHistory
import com.example.dinefinder.core.RoutePlan
import java.util.Locale

internal enum class NavigationSpeechStatus { Starting, Ready, Unavailable }

/** Owns only this navigation session's speech engine and audio focus. */
internal class NavigationSpeech(context: Context) {
    var status by mutableStateOf(NavigationSpeechStatus.Starting)
        private set
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private var closed = false
    private var engine: TextToSpeech? = null
    private var utterance = 0L
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attributes).setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }.build()
    init {
        try {
            engine = TextToSpeech(context.applicationContext) { result ->
                // Post so engine assignment completes even if an engine invokes its callback immediately.
                main.post {
                    if (!closed) {
                        val tts = engine
                        val ready = runCatching {
                            result == TextToSpeech.SUCCESS && tts != null && tts.setLanguage(Locale.ENGLISH) >= TextToSpeech.LANG_AVAILABLE
                        }.getOrDefault(false)
                        status = if (ready) NavigationSpeechStatus.Ready else NavigationSpeechStatus.Unavailable
                        if (ready) {
                            tts?.setAudioAttributes(attributes)
                            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                                override fun onStart(id: String?) = Unit
                                override fun onDone(id: String?) { main.post { if (id == utterance.toString()) abandonFocus() } }
                                @Deprecated("Android callback")
                                override fun onError(id: String?) { failed(id) }
                                override fun onError(id: String?, errorCode: Int) { failed(id) }
                            })
                        }
                    }
                }
            }
        } catch (_: Exception) { status = NavigationSpeechStatus.Unavailable }
    }
    private fun failed(id: String?) { main.post {
        if (!closed && id == utterance.toString()) { status = NavigationSpeechStatus.Unavailable; abandonFocus() }
    } }
    fun speak(text: String): Boolean {
        if (closed || status != NavigationSpeechStatus.Ready) return false
        return runCatching {
            if (audio?.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) return false
            utterance++
            val result = engine?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance.toString()) == TextToSpeech.SUCCESS
            if (!result) { status = NavigationSpeechStatus.Unavailable; abandonFocus() }
            result
        }.getOrElse { status = NavigationSpeechStatus.Unavailable; abandonFocus(); false }
    }
    private fun abandonFocus() { runCatching { audio?.abandonAudioFocusRequest(focus) } }
    fun stop() { runCatching { engine?.stop() }; abandonFocus() }
    fun close() { closed = true; stop(); runCatching { engine?.shutdown() }; engine = null }
}

internal data class NavigationVoiceControls(val status: NavigationSpeechStatus, val active: Boolean, val repeat: () -> Unit)

/** Speech is foreground-only; disposal, muting and backgrounding stop the current utterance. */
@Composable
internal fun rememberNavigationVoice(route: RoutePlan?, enabled: Boolean, cue: NavigationVoiceCue, paused: Boolean): NavigationVoiceControls {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val speech = remember(route, context, owner) { if (route == null) null else NavigationSpeech(context) }
    val history = remember(route) { NavigationVoiceHistory() }
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(speech, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) foreground = true
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                foreground = false
                speech?.stop()
                history.reset()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); speech?.close() }
    }
    val ready = speech?.status == NavigationSpeechStatus.Ready
    LaunchedEffect(enabled, foreground, paused, ready, cue.key, speech) {
        if (!enabled || !foreground || paused) {
            speech?.stop()
            history.reset()
        } else if (ready && history.shouldSpeak(cue) && speech?.speak(cue.text) == true) history.accepted(cue)
    }
    return NavigationVoiceControls(speech?.status ?: NavigationSpeechStatus.Starting,
        enabled && ready && foreground && !paused) {
        if (enabled && ready && foreground && !paused && speech?.speak(cue.text) == true) history.accepted(cue)
    }
}
