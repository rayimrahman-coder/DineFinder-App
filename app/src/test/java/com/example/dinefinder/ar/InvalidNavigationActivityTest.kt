package com.example.dinefinder.ar

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import com.example.dinefinder.R

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class InvalidNavigationActivityTest {
    @Test
    fun missingDestinationFinishesClearlyAndLifecycleDoesNotInitializeArOrGps() {
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, Intent()).create()
        val activity = controller.get()
        assertTrue(activity.isFinishing)
        assertEquals(activity.getString(R.string.navigation_invalid_destination), ShadowToast.getTextOfLatestToast())
        controller.start().resume().pause().stop().destroy()
    }

    @Test
    fun missingLatitudeIsRejectedEvenWhenLongitudeIsExplicitlyZero() {
        val intent = Intent()
            .putExtra("restaurant_name", "Example venue")
            .putExtra("restaurant_lng", 0.0)
        val controller = Robolectric.buildActivity(ARNavigationActivity::class.java, intent).create()
        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }
}
