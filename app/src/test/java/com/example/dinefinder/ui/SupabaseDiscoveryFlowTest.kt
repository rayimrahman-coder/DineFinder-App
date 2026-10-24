package com.example.dinefinder.ui

import android.content.Context
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
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
            context.getSharedPreferences("dinefinder_permissions", Context.MODE_PRIVATE)
                .edit().clear().commit()
            shadowOf(context).denyPermissions(Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
    @get:Rule val rules: RuleChain = RuleChain.outerRule(reset).around(compose)

    @Test fun homeScanShowsPermissionExplanationWithoutLaunchingLegacyPlacesActivity() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").assertIsDisplayed().performClick()
        compose.onNodeWithText("Allow camera & location").assertIsDisplayed()
        compose.onNodeWithText("To find nearby restaurants and guide you with AR navigation, we need access to your camera and location.").assertIsDisplayed()
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state
            assertTrue(state.consent)
            assertFalse(state.demo)
            assertTrue(state.restaurants.isEmpty())
        }
        assertNull(shadowOf(compose.activity).nextStartedActivity)
    }

    @Test fun notNowDismissesWithoutRequestingRuntimePermissions() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Not now").performScrollTo().performClick()
        compose.onNodeWithText("Allow camera & location").assertDoesNotExist()
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state
            assertFalse(state.consent)
            assertFalse(state.scan)
        }
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test fun grantedPermissionsSkipExplanationAndStartScan() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Allow camera & location").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(ViewModelProvider(compose.activity)[AppViewModel::class.java].state.scan)
        }
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test fun continueUsesExistingRuntimePermissionLauncher() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        val requested = shadowOf(compose.activity).lastRequestedPermission.requestedPermissions.toSet()
        assertTrue(requested.containsAll(setOf(Manifest.permission.CAMERA,
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)))
    }

    @Test fun ordinaryDenialOffersRetryAndKeepsLocationOnlyScanAvailable() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(compose.activity.packageManager)
            .setShouldShowRequestPermissionRationale(Manifest.permission.CAMERA, true)
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        val request = shadowOf(compose.activity).lastRequestedPermission
        compose.runOnIdle {
            compose.activity.onRequestPermissionsResult(request.requestCode,
                arrayOf(Manifest.permission.CAMERA), intArrayOf(PackageManager.PERMISSION_DENIED))
        }
        compose.onNodeWithText("Permissions not granted").assertIsDisplayed()
        compose.onNodeWithText("Try again").assertIsDisplayed()
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state
            assertTrue(state.scan)
            assertFalse(state.cameraAllowed)
        }
    }

    @Test fun cameraAndApproximateLocationDoNotRepeatedlyPrompt() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(
            Manifest.permission.CAMERA, Manifest.permission.ACCESS_COARSE_LOCATION)
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Allow camera & location").assertDoesNotExist()
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
    }

    @Test fun permanentlyBlockedPermissionOffersApplicationSettings() {
        RuntimeEnvironment.getApplication().getSharedPreferences("dinefinder_permissions", Context.MODE_PRIVATE)
            .edit().putBoolean("asked:${Manifest.permission.CAMERA}", true).commit()
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Scan for Restaurant").performClick()
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Allow access in Settings").assertIsDisplayed()
        compose.onNodeWithText("Open settings").performClick()
        val settings = shadowOf(compose.activity).nextStartedActivityForResult
        org.junit.Assert.assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, settings.intent.action)
        org.junit.Assert.assertEquals("package:${compose.activity.packageName}", settings.intent.dataString)
        assertNull(shadowOf(compose.activity).lastRequestedPermission)
        compose.runOnIdle {
            shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            compose.activity.activityResultRegistry.dispatchResult(settings.requestCode, Activity.RESULT_CANCELED, null)
        }
        compose.runOnIdle {
            val state = ViewModelProvider(compose.activity)[AppViewModel::class.java].state
            assertTrue(state.scan)
            assertTrue(state.cameraAllowed)
        }
        compose.onNodeWithText("Allow access in Settings").assertDoesNotExist()
    }

    @Test fun scanChecksOpensLiveChecklistAndCopyAction() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.runOnIdle {
            ViewModelProvider(compose.activity)[AppViewModel::class.java]
                .scanWithPermissions(location = false, camera = false)
        }
        compose.onNodeWithContentDescription("Scan checklist").assertIsDisplayed().performClick()
        compose.onNode(hasText("Scan checklist") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithText("Location permission").assertIsDisplayed()
        compose.onNodeWithText("Copy checks").assertIsDisplayed()
    }

    @Test fun settingsDoesNotRequestGooglePlacesKey() {
        compose.onNodeWithText("Continue as guest").performScrollTo().performClick()
        compose.onNodeWithText("Profile").performClick()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Username").assertIsDisplayed()
        compose.onNodeWithText("Google Maps API").assertDoesNotExist()
    }
}
