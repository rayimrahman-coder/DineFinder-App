package com.example.dinefinder.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.example.dinefinder.core.GeoPoint
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SearchFailureScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedRequestShowsFailureInsteadOfAFalseEmptyResult() {
        compose.setContent {
            ScanScreen(
                position = GeoPoint(5.280285448386224, 115.17640486697412),
                heading = 0.0, restaurants = emptyList(), route = null, destination = null,
                isDemo = false, busy = false, error = "Unable to connect to Supabase.",
                locationError = null, headingError = null, cameraAllowed = false,
                onSelect = {}, onClose = {}, onRetry = {}, onRecalculate = {},
                onHeadingChange = {}, onWalkChange = {}, demoProgress = 0.0,
                searchCompleted = false,
            )
        }
        compose.onNodeWithText("No restaurants found within 1 km").assertDoesNotExist()
        compose.onNodeWithText("Search unavailable").assertIsDisplayed()
        compose.onNodeWithContentDescription("Expand restaurants").performClick()
        compose.onNodeWithText("Unable to connect to Supabase.").assertIsDisplayed()
        compose.onNodeWithText("No restaurants recorded within 1 km").assertDoesNotExist()
    }
}
