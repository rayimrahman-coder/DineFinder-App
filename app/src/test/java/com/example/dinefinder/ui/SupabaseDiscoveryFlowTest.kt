package com.example.dinefinder.ui

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.dinefinder.MainActivity
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import androidx.lifecycle.ViewModelProvider
import com.example.dinefinder.AppViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SupabaseDiscoveryFlowTest {
    private val compose = createAndroidComposeRule<MainActivity>()
    private val reset = object : ExternalResource() {
        override fun before() {
            val context = RuntimeEnvironment.getApplication()
            context.getSharedPreferences("dinefinder_profile", Context.MODE_PRIVATE)
                .edit().clear().commit()
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(reset).around(compose)

    @Test fun homeScanRequestsSupabaseConsentWithoutLaunchingLegacyPlacesActivity() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").assertIsDisplayed().performClick()
        compose.onNodeWithText("Find restaurants around you").assertIsDisplayed()
        compose.onNodeWithText("Allow location and camera access to find restaurants stored in Supabase within 1 km. Camera frames stay on your device.").assertIsDisplayed()
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state
            assertTrue(state.consent)
            assertFalse(state.demo)
            assertTrue(state.restaurants.isEmpty())
        }
        assertNull(shadowOf(compose.activity).nextStartedActivity)
    }

    @Test fun settingsDoesNotRequestGooglePlacesKey() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("Google Maps API").assertDoesNotExist()
    }
}
