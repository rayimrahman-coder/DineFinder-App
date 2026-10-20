package com.example.dinefinder.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.example.dinefinder.ui.theme.DineFinderTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ApiKeySettingsTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun savesAndRemovesKeyThroughTheSettingsCard() {
        var savedKey = ""
        composeRule.setContent {
            var key by remember { mutableStateOf("") }
            DineFinderTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApiKeySettingsCard(
                        apiKey = key,
                        onSave = {
                            savedKey = it.trim()
                            key = savedKey
                            null
                        },
                        onRemove = { savedKey = ""; key = ""; null },
                        onHelp = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("Save API key").assertIsNotEnabled()
        composeRule.onNodeWithText("API key").performTextReplacement(" fake_test_key_123 ")
        composeRule.onNodeWithText("Save API key").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("fake_test_key_123", savedKey) }
        composeRule.onNodeWithContentDescription("Show API key").assertIsDisplayed().performClick()
        composeRule.onNodeWithContentDescription("Hide API key").assertIsDisplayed()
        composeRule.onNodeWithText("Remove").performScrollTo().performClick()
        composeRule.runOnIdle { assertEquals("", savedKey) }
        composeRule.onNodeWithText("Save API key").assertIsNotEnabled()
    }

    @Test
    fun rejectsUrlAndShowsSaveFailureWithoutDiscardingInput() {
        var submittedKey: String? = null
        composeRule.setContent {
            DineFinderTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApiKeySettingsCard(
                        apiKey = "",
                        onSave = { submittedKey = it; "Could not save. Try again." },
                        onRemove = { null },
                        onHelp = {}
                    )
                }
            }
        }
        composeRule.onNodeWithText("API key").performTextReplacement("https://example.com/key")
        composeRule.onNodeWithText("Save API key").assertIsNotEnabled()
        composeRule.runOnIdle { assertNull(submittedKey) }
        composeRule.onNodeWithText("API key").performTextReplacement("fake_test_key_456")
        composeRule.onNodeWithText("Save API key").performScrollTo().performClick()
        composeRule.onNodeWithText("Could not save. Try again.").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals("fake_test_key_456", submittedKey) }
    }
}
