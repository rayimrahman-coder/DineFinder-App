package com.example.dinefinder

import android.content.Intent
import android.graphics.Color
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge

/** Bundled, silent animation. Failure or Skip always leads to the existing app. */
class BrandSplashActivity : ComponentActivity() {
    private lateinit var video: VideoView
    private val handler = Handler(Looper.getMainLooper())
    private var completed = false
    private var resumed = false
    private var prepared = false
    private var position = 0
    private val timeout = Runnable { openApp() }

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_DineFinder)
        super.onCreate(savedInstanceState)
        position = savedInstanceState?.getInt("splash_position") ?: 0
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT))
        val root = FrameLayout(this).apply { setBackgroundColor(getColor(R.color.brand_splash_background)) }
        val poster = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            addView(ImageView(this@BrandSplashActivity).apply { setImageResource(R.drawable.dinefinder_logo) },
                LinearLayout.LayoutParams(dp(120), dp(140)))
            addView(TextView(this@BrandSplashActivity).apply {
                text = "DineFinder"; textSize = 30f; setTextColor(Color.rgb(7,85,72)); gravity = Gravity.CENTER
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            })
        }
        root.addView(poster, FrameLayout.LayoutParams(-1,-1))
        video = VideoView(this).apply {
            id = R.id.brand_splash_video
            setAudioFocusRequest(AudioManager.AUDIOFOCUS_NONE)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setOnPreparedListener { player ->
                prepared = true
                player.setVolume(0f,0f)
                player.isLooping = false
                seekTo(position)
                if (resumed) start()
            }
            setOnInfoListener { _, what, _ ->
                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) poster.visibility = View.GONE
                false
            }
            setOnCompletionListener { openApp() }
            setOnErrorListener { _, _, _ -> openApp(); true }
        }
        // VideoView preserves the landscape aspect ratio inside these bounds.
        root.addView(video, FrameLayout.LayoutParams(-1,-1,Gravity.CENTER))
        // Keep the static logo visible until actual frames arrive (no black loading rectangle).
        root.bringChildToFront(poster)
        val skip = Button(this).apply {
            id = R.id.brand_splash_skip
            text = getString(R.string.splash_skip)
            contentDescription = getString(R.string.splash_skip_description)
            isAllCaps = false
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(7,85,72))
            minHeight = dp(48)
            setPadding(dp(24),dp(8),dp(24),dp(8))
            setOnClickListener { openApp() }
        }
        root.addView(skip, FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM or Gravity.END).apply {
            setMargins(dp(24),dp(24),dp(24),dp(24))
        })
        root.setOnApplyWindowInsetsListener { _, insets ->
            (skip.layoutParams as FrameLayout.LayoutParams).apply {
                rightMargin = dp(24) + insets.systemWindowInsetRight
                bottomMargin = dp(24) + insets.systemWindowInsetBottom
            }.also { skip.layoutParams = it }
            insets
        }
        setContentView(root)
        runCatching { video.setVideoURI(Uri.parse("android.resource://$packageName/${R.raw.dinefinder_splash}")) }
            .onFailure { openApp() }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        if (!completed) {
            if (prepared) { video.seekTo(position); video.start() }
            // Prevent a broken decoder from blocking access to the app.
            handler.postDelayed(timeout, 12_000)
        }
    }
    override fun onPause() {
        resumed = false
        handler.removeCallbacks(timeout)
        if (::video.isInitialized) { position = video.currentPosition.coerceAtLeast(0); video.pause() }
        super.onPause()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("splash_position", if (::video.isInitialized) video.currentPosition else position)
        super.onSaveInstanceState(outState)
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::video.isInitialized) video.stopPlayback()
        super.onDestroy()
    }
    internal fun openApp() {
        if (completed || isFinishing || isDestroyed) return
        completed = true
        handler.removeCallbacks(timeout)
        if (::video.isInitialized) video.stopPlayback()
        startActivity(Intent(this,MainActivity::class.java))
        finish()
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}
