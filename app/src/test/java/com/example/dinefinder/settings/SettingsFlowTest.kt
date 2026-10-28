package com.example.dinefinder.settings

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.core.view.WindowInsetsControllerCompat
import com.example.dinefinder.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SettingsFlowTest {
    private val composeRule = createAndroidComposeRule<MainActivity>()
    private val prepareProfile = object : ExternalResource() {
        override fun before() {
            val context = RuntimeEnvironment.getApplication()
            context.getSharedPreferences("dinefinder_profile", Context.MODE_PRIVATE).edit().clear().commit()
            UserPreferences(context).signIn("guest@dinefinder.local")
            UserPreferences(context).saveUserName("guest@dinefinder.local", "Rayim")
        }
    }

    @get:Rule
    val rules: RuleChain = RuleChain.outerRule(prepareProfile).around(composeRule)

    @Test
    fun savingUsernameUpdatesBothProfileAndHome() {
        openSettings()
        composeRule.onNodeWithText("Username").performTextReplacement(" Rayim Rahman ")
        composeRule.onNodeWithText("Save username").performScrollTo().performClick()
        composeRule.onNodeWithContentDescription("Back to Profile").performClick()
        composeRule.onNodeWithText("Rayim Rahman").assertIsDisplayed()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Hello, Rayim Rahman").assertIsDisplayed()
        assertEquals("Rayim Rahman", UserPreferences(composeRule.activity).userName("guest@dinefinder.local"))
    }

    @Test
    fun invalidUsernameCannotReplaceSavedName() {
        openSettings()
        composeRule.onNodeWithText("Username").performTextReplacement("   ")
        composeRule.onNodeWithText("Save username").assertIsNotEnabled()
        composeRule.onNodeWithText("Enter a user name.").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Back to Profile").performClick()
        composeRule.onNodeWithText("Rayim").assertIsDisplayed()
    }

    @Test
    fun darkModeUpdatesImmediatelyAndSurvivesActivityRecreation() {
        openSettings()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(isToggleable())
        composeRule.onNode(isToggleable()).performScrollTo().assertIsOff().performClick().assertIsOn()
        composeRule.runOnIdle {
            assertTrue(UserPreferences(composeRule.activity).isDarkMode())
            assertFalse(WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView).isAppearanceLightStatusBars)
        }
        composeRule.activityRule.scenario.recreate()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithTag("settings_list").performScrollToNode(isToggleable())
        composeRule.onNode(isToggleable()).performScrollTo().assertIsOn().performClick().assertIsOff()
        composeRule.runOnIdle {
            assertFalse(UserPreferences(composeRule.activity).isDarkMode())
            assertTrue(WindowInsetsControllerCompat(composeRule.activity.window, composeRule.activity.window.decorView).isAppearanceLightStatusBars)
        }
    }

    @Test
    fun helpOpensTutorialAndLinksToSettings() {
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithText("Help").performClick()
        composeRule.onNodeWithText("Help & support").assertIsDisplayed()
        composeRule.onNodeWithTag("help_list").performScrollToNode(hasText("Open Settings"))
        composeRule.onNodeWithText("Open Settings").performClick()
        composeRule.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
    }

    private fun openSettings() {
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithText("Username").performScrollTo().assertIsDisplayed()
    }
}
