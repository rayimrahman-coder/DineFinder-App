package com.example.dinefinder.ui

import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28],qualifiers="w393dp-h851dp")
class RestaurantReviewBrowserTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val controller = ReviewBrowserController()
    private val url = "https://www.google.com/maps/place/Campus+Cafe/data=!9m1!1b1"
    private fun screen(close: () -> Unit = {}) {
        compose.setContent { MaterialTheme { RestaurantReviewBrowser("Campus Cafe",url,controller,close) } }
    }
    private fun request(main: Boolean = true) = object : WebResourceRequest {
        override fun getUrl(): Uri = Uri.parse(this@RestaurantReviewBrowserTest.url)
        override fun isForMainFrame() = main
        override fun isRedirect() = false
        override fun hasGesture() = true
        override fun getMethod() = "GET"
        override fun getRequestHeaders() = emptyMap<String,String>()
    }
    @Test fun exactStoredUrlLoadsInsideWebViewWithoutAppKeysOrExternalIntent() {
        screen()
        compose.onNodeWithTag("review_web_content").assertIsDisplayed()
        compose.runOnIdle {
            val view = controller.webView!!
            assertEquals(url,shadowOf(view).lastLoadedUrl)
            assertTrue(shadowOf(view).lastAdditionalHttpHeaders.isNullOrEmpty())
            assertTrue(view.settings.javaScriptEnabled)
            assertTrue(view.settings.domStorageEnabled)
            assertFalse(view.settings.allowFileAccess)
            assertFalse(view.settings.allowContentAccess)
            assertEquals(WebSettings.MIXED_CONTENT_NEVER_ALLOW,view.settings.mixedContentMode)
            assertNull(shadowOf(compose.activity).nextStartedActivity)
        }
    }
    @Test fun backUsesWebHistoryBeforeClosingAndCloseAlwaysReturnsToProfile() {
        var closed = false
        screen { closed = true }
        compose.runOnIdle { shadowOf(controller.webView!!).setCanGoBack(true) }
        compose.onNodeWithContentDescription("Back from reviews").performClick()
        compose.runOnIdle { assertEquals(1,shadowOf(controller.webView!!).goBackInvocations); assertFalse(closed) }
        compose.onNodeWithContentDescription("Close reviews").performClick()
        compose.runOnIdle { assertTrue(closed) }
        compose.runOnIdle { closed=false; shadowOf(controller.webView!!).setCanGoBack(false) }
        compose.onNodeWithContentDescription("Back from reviews").performClick()
        compose.runOnIdle { assertTrue(closed) }
    }
    @Suppress("DEPRECATION")
    @Test fun appLaunchAndUnsafeSchemesAreBlockedWithoutLaunchingAnything() {
        screen()
        compose.runOnIdle {
            val view=controller.webView!!; val client=shadowOf(view).webViewClient
            for (link in listOf("intent://maps/#Intent;scheme=https;end", "geo:5.28,115.17", "javascript:alert(1)", "file:///data/private"))
                assertTrue(client.shouldOverrideUrlLoading(view,link))
            assertFalse(client.shouldOverrideUrlLoading(view,"https://www.google.com/maps?review=next"))
            assertNull(shadowOf(compose.activity).nextStartedActivity)
        }
        compose.onNodeWithText("This action isn't available in the review viewer.").assertIsDisplayed()
    }
    @Test fun mainPageFailureHasRetryAndSubresourceFailureDoesNotReplaceReviews() {
        screen()
        compose.runOnIdle {
            val view=controller.webView!!
            shadowOf(view).webViewClient.onReceivedHttpError(view,request(false),WebResourceResponse("text/html","UTF-8",503,"Unavailable",emptyMap(),null))
        }
        compose.onNodeWithText("Retry reviews").assertDoesNotExist()
        val old=controller.webView!!
        compose.runOnIdle { shadowOf(old).webViewClient.onReceivedHttpError(old,request(),WebResourceResponse("text/html","UTF-8",503,"Unavailable",emptyMap(),null)) }
        compose.onNodeWithText("Retry reviews").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertTrue(shadowOf(old).wasDestroyCalled())
            assertNotSame(old,controller.webView)
            assertEquals(url,shadowOf(controller.webView!!).lastLoadedUrl)
        }
    }
    @Test fun lifecyclePausesWebViewAndDisposalReleasesIt() {
        var visible by mutableStateOf(true)
        compose.setContent { MaterialTheme { if(visible) RestaurantReviewBrowser("Campus Cafe",url,controller,{}) } }
        val view=controller.webView!!
        compose.activityRule.scenario.moveToState(Lifecycle.State.STARTED)
        compose.runOnIdle { assertTrue(shadowOf(view).wasOnPauseCalled()) }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.runOnIdle { assertTrue(shadowOf(view).wasOnResumeCalled()); visible=false }
        compose.runOnIdle { assertTrue(shadowOf(view).wasDestroyCalled()); assertNull(controller.webView) }
    }
    @Test fun invalidReviewUrlsAreRejected() {
        for (link in listOf("http://www.google.com/maps", "https://user:password@www.google.com/maps", "javascript:alert(1)", "file:///data/local"))
            assertFalse(isInAppReviewUrl(link))
        assertTrue(isInAppReviewUrl(url))
    }
}
