package com.example.dinefinder.ui

import android.speech.tts.TextToSpeech
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import org.robolectric.annotation.GraphicsMode
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import com.example.dinefinder.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowTextToSpeech
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], qualifiers = "w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class WalkingNavigationScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val origin = GeoPoint(5.280285, 115.176405)
    private val corner = GeoPoint(5.282, 115.176405)
    private val end = GeoPoint(5.282, 115.179)
    private val restaurant = Restaurant("voice-test", "Campus Cafe", "Cafe", "", "", end, 0.0, 1, "", 0L)
    private val route = RoutePlan(listOf(origin, corner, end), listOf(
        RouteInstruction(origin, "Walk north on Campus Road", 190.0),
        RouteInstruction(corner, "Turn right onto Dining Lane", 280.0),
        RouteInstruction(end, "Arrive at Campus Cafe", 0.0)), 470.0, 360.0, isDemo = true)
    private var position by mutableStateOf(origin)
    private var heading by mutableStateOf(0.0)
    private var cameraActive: Boolean? = null
    private fun screen() {
        compose.setContent { MaterialTheme {
            ScanScreen(position, heading, listOf(restaurant), route, restaurant, true, false, null, null, null, false,
                {}, {}, {}, {}, {}, {}, 0.0, onCameraCheckChange = { _, _, active -> cameraActive = active })
        } }
    }
    private fun speechReady() {
        compose.runOnIdle {
            ShadowTextToSpeech.addLanguageAvailability(Locale.ENGLISH)
            shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance()).onInitListener.onInit(TextToSpeech.SUCCESS)
        }
        compose.waitForIdle()
    }
    private fun spoken() = shadowOf(ShadowTextToSpeech.getLastTextToSpeechInstance())
    @Test fun cameraStartsClearAndControlsCanBeSwipedUpAndHiddenAgain() {
        screen(); speechReady()
        compose.onNodeWithText("Swipe up for navigation").assertIsDisplayed()
        compose.onNodeWithText("Current instruction").assertDoesNotExist()
        compose.onNodeWithText("Turn camera off").assertDoesNotExist()
        compose.runOnIdle { assertEquals(true, cameraActive) }
        compose.onNodeWithTag("navigation_sheet_handle").performTouchInput { swipeUp() }
        compose.onNodeWithText("Turn camera off").assertIsDisplayed()
        compose.onNodeWithText("Current instruction").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("navigation_sheet_handle").performTouchInput { swipeDown() }
        compose.onNodeWithText("Current instruction").assertDoesNotExist()
        compose.onNodeWithText("Swipe up for navigation").assertIsDisplayed()
        val spokenCount = spoken().spokenTextList.size
        compose.runOnIdle { position = corner }
        compose.runOnIdle {
            assertEquals(true, cameraActive)
            assertEquals(spokenCount + 1, spoken().spokenTextList.size)
            assertEquals("Turn right onto Dining Lane", spoken().lastSpokenText)
        }
        compose.onNodeWithContentDescription("Show navigation controls").performClick()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("Swipe up for navigation").assertIsDisplayed()
    }
    @Test fun cameraCanTurnOffAndOnWithoutChangingRouteOrLosingWrittenDirections() {
        screen()
        compose.runOnIdle { assertEquals(true, cameraActive) }
        compose.onNodeWithContentDescription("Show navigation controls").performClick()
        compose.onNodeWithText("Turn camera off").performScrollTo().performClick()
        compose.onNodeWithText("Camera off · GPS guidance").assertIsDisplayed()
        compose.runOnIdle { assertEquals(false, cameraActive) }
        compose.waitForIdle()
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val preview = File("build/reports/previews/camera-free-navigation.png")
        preview.parentFile?.mkdirs()
        preview.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        compose.onNodeWithText("Directions", substring = false).performScrollTo().performClick()
        compose.onNodeWithText("Walking directions").assertIsDisplayed()
        compose.activity.onBackPressedDispatcher.onBackPressed()
        compose.onNodeWithText("Turn camera on").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(true, cameraActive) }
        compose.onNodeWithText("Turn camera off").assertDoesNotExist()
        compose.onNodeWithContentDescription("Show navigation controls").performClick()
        compose.onNodeWithText("Turn camera off").assertIsDisplayed()
    }
    @Test fun voiceDeduplicatesGpsUpdatesSupportsMuteAndRepeatAndWorksWithCameraOff() {
        screen(); speechReady()
        compose.runOnIdle { assertEquals("Walk north on Campus Road", spoken().lastSpokenText) }
        val firstCount = spoken().spokenTextList.size
        compose.runOnIdle { heading = 40.0; position = GeoPoint(5.2804, 115.176405) }
        compose.runOnIdle { assertEquals(firstCount, spoken().spokenTextList.size) }
        compose.onNodeWithContentDescription("Show navigation controls").performClick()
        compose.onNodeWithText("Turn camera off").performScrollTo().performClick()
        compose.onNodeWithText("Repeat instruction").performClick()
        compose.runOnIdle { assertEquals(firstCount + 1, spoken().spokenTextList.size) }
        compose.onNodeWithText("Mute voice").performClick()
        compose.runOnIdle { assertTrue(spoken().isStopped); position = corner }
        compose.runOnIdle { assertEquals(firstCount + 1, spoken().spokenTextList.size) }
        compose.onNodeWithText("Enable voice").performClick()
        compose.runOnIdle { assertEquals("Turn right onto Dining Lane", spoken().lastSpokenText) }
    }
    @Test fun backgroundingStopsSpeechAndArrivalIsAnnouncedOnceAfterResume() {
        screen(); speechReady()
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.runOnIdle { assertTrue(spoken().isStopped) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.waitForIdle()
        compose.runOnIdle { position = end }
        compose.onNodeWithText("Arrive at the restaurant").assertIsDisplayed()
        compose.runOnIdle { assertTrue(spoken().lastSpokenText.contains("Campus Cafe")) }
        val count = spoken().spokenTextList.size
        compose.runOnIdle { heading = 90.0 }
        compose.runOnIdle { assertEquals(count, spoken().spokenTextList.size) }
    }
    @Test
    @Config(sdk = [28], qualifiers = "w320dp-h480dp", fontScale = 2f)
    fun cameraFreeNavigationControlsRemainReachableWithLargeText() {
        screen()
        compose.onNodeWithContentDescription("Show navigation controls").performClick()
        compose.onNodeWithText("Turn camera off").performScrollTo().performClick()
        compose.onNodeWithText("Mute voice").assertIsDisplayed()
        compose.onNodeWithText("Directions", substring = false).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("End", substring = false).performScrollTo().assertIsDisplayed()
    }
}
