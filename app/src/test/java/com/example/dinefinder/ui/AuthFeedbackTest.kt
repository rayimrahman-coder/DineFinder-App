package com.example.dinefinder.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
@org.robolectric.annotation.Config(sdk = [28])
class AuthFeedbackTest {
    @get:Rule val compose = createComposeRule()

    @Test fun delayedLoginFailureIsVisibleAndPendingRequestDisablesActions() {
        val busy = mutableStateOf(false)
        val message = mutableStateOf<String?>(null)
        compose.setContent {
            WelcomeScreen(busy.value, message.value,
                onAuthenticate = { _, _, _ -> busy.value = true }, onGuest = {})
        }
        compose.onNodeWithText("Email address").performTextInput("person@example.test")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onNodeWithText("Continue").performScrollTo().performClick()
        compose.onNodeWithText("Continue as guest").assertIsNotEnabled()
        compose.runOnIdle {
            message.value = "The email or password is incorrect."
            busy.value = false
        }
        compose.onNodeWithText("The email or password is incorrect.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Continue").assertIsDisplayed()
    }

    @Test fun createAccountSendsSignupInsteadOfSignin() {
        var signup = false
        compose.setContent {
            WelcomeScreen(false, null,
                onAuthenticate = { _, _, create -> signup = create }, onGuest = {})
        }
        compose.onNodeWithText("New here? Create an account").performScrollTo().performClick()
        compose.onNodeWithText("Email address").performTextInput("person@example.test")
        compose.onNodeWithText("Password").performTextInput("test-password")
        compose.onNodeWithText("Create account").performScrollTo().performClick()
        compose.runOnIdle { assertTrue(signup) }
    }
}
