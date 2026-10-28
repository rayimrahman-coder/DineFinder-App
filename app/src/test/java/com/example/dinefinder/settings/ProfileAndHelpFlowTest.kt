package com.example.dinefinder.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.dinefinder.MainActivity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w393dp-h851dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ProfileAndHelpFlowTest {
    private val compose=createAndroidComposeRule<MainActivity>()
    private val prepare=object:ExternalResource(){ override fun before(){
        val context=RuntimeEnvironment.getApplication()
        context.getSharedPreferences("dinefinder_profile",Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("dinefinder_profile_photos",Context.MODE_PRIVATE).edit().clear().commit()
        UserPreferences(context).signIn("guest@dinefinder.local")
    } }
    @get:Rule val rules:RuleChain=RuleChain.outerRule(prepare).around(compose)
    private fun settings(){
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performClick()
    }
    @Test fun pickerResultUpdatesSettingsAndProfileAndRemovalPersists() {
        settings()
        compose.onNodeWithText("Change photo").performScrollTo().performClick()
        val launched=shadowOf(compose.activity).nextStartedActivityForResult
        assertNotNull(launched)
        val image=ByteArrayOutputStream()
        Bitmap.createBitmap(40,40,Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG,100,image)
        val uri=Uri.parse("content://test/photos/picked")
        org.robolectric.Shadows.shadowOf(compose.activity.contentResolver).registerInputStream(uri,ByteArrayInputStream(image.toByteArray()))
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(launched.intent,Activity.RESULT_OK,Intent().setData(uri)) }
        compose.waitUntil(10000){ProfilePhotoStore(compose.activity).photoPath("guest@dinefinder.local")!=null}
        compose.onNodeWithText("Remove photo").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("Back to Profile").performClick()
        compose.onNodeWithContentDescription("Your profile picture").assertExists()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("Your profile picture").assertExists()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Remove photo").performScrollTo().performClick()
        compose.waitUntil(10000){ProfilePhotoStore(compose.activity).photoPath("guest@dinefinder.local")==null}
        compose.onNodeWithContentDescription("Default profile picture").assertExists()
    }
    @Test fun cancellingPickerDoesNotChangePhotoOrShowAnError() {
        settings()
        compose.onNodeWithText("Change photo").performScrollTo().performClick()
        val launched=shadowOf(compose.activity).nextStartedActivityForResult
        compose.runOnIdle { shadowOf(compose.activity).receiveResult(launched.intent,Activity.RESULT_CANCELED,null) }
        compose.onNodeWithContentDescription("Default profile picture").assertExists()
        assertNull(ProfilePhotoStore(compose.activity).photoPath("guest@dinefinder.local"))
    }
    @Test
    @Config(sdk=[28],qualifiers="w320dp-h480dp",fontScale=2f)
    fun helpIsScrollableAndUsefulAndReturnsToSettings() {
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Help").performScrollTo().performClick()
        compose.onNodeWithText("Help & support").assertIsDisplayed()
        compose.onNodeWithTag("help_list").performScrollToNode(hasText("Why are no restaurants showing?"))
        compose.onNodeWithText("Why are no restaurants showing?").performClick()
        compose.onNodeWithText("Turn on location services",substring=true).assertExists()
        compose.onNodeWithTag("help_list").performScrollToNode(hasText("Open Settings"))
        compose.onNodeWithText("Open Settings").performScrollTo().performClick()
        compose.onNodeWithText("Settings").assertIsDisplayed()
        compose.onNodeWithTag("settings_list").performScrollToNode(hasText("Change photo"))
        compose.onNodeWithText("Change photo").performScrollTo().assertIsDisplayed()
    }
}
