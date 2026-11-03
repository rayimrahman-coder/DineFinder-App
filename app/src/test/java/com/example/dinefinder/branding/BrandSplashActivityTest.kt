package com.example.dinefinder.branding

import android.content.Intent
import android.media.MediaPlayer
import android.os.Looper
import android.widget.VideoView
import com.example.dinefinder.BrandSplashActivity
import com.example.dinefinder.MainActivity
import com.example.dinefinder.R
import java.time.Duration
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BrandSplashActivityTest {
    @Test fun launcherUsesBrandSplashAndBundlesSilentLocalVideo() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        val launcher=context.packageManager.getLaunchIntentForPackage(context.packageName)
        assertEquals(BrandSplashActivity::class.java.name,launcher?.component?.className)
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val video=activity.findViewById<VideoView>(R.id.brand_splash_video)
        assertEquals("android.resource://${activity.packageName}/${R.raw.dinefinder_splash}",shadowOf(video).videoURIString)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }
    @Test fun normalPlaybackHasNoSkipButtonOrUnnecessaryLoadingIndicator() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val video=activity.findViewById<VideoView>(R.id.brand_splash_video)
        val progress=activity.findViewById<android.view.View>(R.id.brand_splash_progress)
        assertEquals(android.view.View.GONE,progress.visibility)
        assertNull(activity.findViewById<android.view.View>(activity.resources.getIdentifier("brand_splash_skip","id",activity.packageName)))
        shadowOf(video).onPreparedListener.onPrepared(MediaPlayer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(android.view.View.GONE,progress.visibility)
        shadowOf(video).onCompletionListener.onCompletion(MediaPlayer())
        shadowOf(video).onCompletionListener.onCompletion(MediaPlayer())
        assertEquals(MainActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.pause().stop().destroy()
    }
    @Test fun indicatorOnlyAppearsForSlowPreparationAndHidesWhenReady() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val progress=activity.findViewById<android.view.View>(R.id.brand_splash_progress)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(100))
        assertEquals(android.view.View.GONE,progress.visibility)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        assertEquals(android.view.View.VISIBLE,progress.visibility)
        shadowOf(activity.findViewById<VideoView>(R.id.brand_splash_video)).onPreparedListener.onPrepared(MediaPlayer())
        assertEquals(android.view.View.GONE,progress.visibility)
        controller.pause().stop().destroy()
    }
    @Test fun portraitAndLandscapeWindowsAreCoveredWithoutDistortion() {
        val context=org.robolectric.RuntimeEnvironment.getApplication()
        for ((width,height) in listOf(393 to 851,851 to 393)) {
            val view=com.example.dinefinder.SplashVideoView(context)
            view.measure(android.view.View.MeasureSpec.makeMeasureSpec(width,android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(height,android.view.View.MeasureSpec.EXACTLY))
            assertTrue(view.measuredWidth>=width)
            assertTrue(view.measuredHeight>=height)
            assertEquals(720.0/1280.0,view.measuredWidth.toDouble()/view.measuredHeight,0.002)
        }
    }
    @Test fun videoCompletionAndPlaybackFailureBothOpenApp() {
        for (fail in listOf(false,true)) {
            val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
            val activity=controller.get()
            val video=shadowOf(activity.findViewById<VideoView>(R.id.brand_splash_video))
            if (fail) assertTrue(video.onErrorListener.onError(MediaPlayer(),1,0))
            else video.onCompletionListener.onCompletion(MediaPlayer())
            assertEquals(MainActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
            controller.pause().stop().destroy()
        }
    }
    @Test fun pausedAppDoesNotTimeoutButResumedAppHasBoundedFallback() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val video=activity.findViewById<VideoView>(R.id.brand_splash_video)
        controller.pause().stop()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(20))
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(org.robolectric.shadows.ShadowVideoView.PAUSE,shadowOf(video).currentVideoState)
        controller.restart().start().resume()
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(13))
        assertEquals(MainActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }
    @Test fun playbackDoesNotLeaveEarlyAndCompletionHandsOffImmediately() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val video=activity.findViewById<VideoView>(R.id.brand_splash_video)
        shadowOf(video).onPreparedListener.onPrepared(MediaPlayer())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(2999))
        assertNull(shadowOf(activity).nextStartedActivity)
        shadowOf(video).onCompletionListener.onCompletion(MediaPlayer())
        assertEquals(MainActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }
    @Test fun completionInBackgroundWaitsUntilResume() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        val video=activity.findViewById<VideoView>(R.id.brand_splash_video)
        controller.pause().stop()
        shadowOf(video).onCompletionListener.onCompletion(MediaPlayer())
        assertNull(shadowOf(activity).nextStartedActivity)
        controller.restart().start().resume()
        assertEquals(MainActivity::class.java.name,shadowOf(activity).nextStartedActivity.component?.className)
        controller.pause().stop().destroy()
    }
    @Test fun recreationPreservesPlaybackPosition() {
        val controller=Robolectric.buildActivity(BrandSplashActivity::class.java).setup()
        val activity=controller.get()
        activity.findViewById<VideoView>(R.id.brand_splash_video).seekTo(1200)
        val state=android.os.Bundle()
        controller.saveInstanceState(state).pause().stop().destroy()
        val restored=Robolectric.buildActivity(BrandSplashActivity::class.java).create(state).start().resume().visible()
        val video=restored.get().findViewById<VideoView>(R.id.brand_splash_video)
        shadowOf(video).onPreparedListener.onPrepared(MediaPlayer())
        assertEquals(1200,video.currentPosition)
        restored.pause().stop().destroy()
    }
}
