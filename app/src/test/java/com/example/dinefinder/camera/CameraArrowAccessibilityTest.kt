package com.example.dinefinder.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CameraArrowAccessibilityTest {
    @Test
    fun maneuverAnnouncementRetainsDistanceAndAdvancesWithInstruction() {
        val view = CameraArrowView(RuntimeEnvironment.getApplication())
        view.setManeuver(0f, "Continue straight for 63 m")
        assertEquals("Continue straight for 63 m", view.contentDescription)
        view.setManeuver(90f, "Now: Turn right")
        assertEquals("Now: Turn right", view.contentDescription)
        view.clearDirection()
        assertNull(view.contentDescription)
    }

    @Test
    fun invalidOrMissingInstructionRemovesPreviousAnnouncement() {
        val view = CameraArrowView(RuntimeEnvironment.getApplication())
        for ((angle, text) in listOf(Float.NaN to "In 58 m: Turn right", 90f to "  ", Float.POSITIVE_INFINITY to "Turn right")) {
            view.setManeuver(0f, "Continue straight for 63 m")
            view.setManeuver(angle, text)
            assertNull(view.contentDescription)
        }
    }
}
