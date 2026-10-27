package com.example.dinefinder.ui

import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class ReviewWebNavigationTest {
    private fun intent(fallback: String) = "intent://maps/#Intent;scheme=https;package=com.google.android.apps.maps;S.browser_fallback_url="+Uri.encode(fallback)+";end"
    @Test fun explicitMapsHttpsFallbackKeepsItsFullReviewParameters() {
        val fallback="https://www.google.com/maps/place/Cafe/data=!9m1!1b1?hl=en&gl=MY"
        assertEquals(fallback,googleMapsBrowserFallback(intent(fallback)))
        assertEquals(ReviewWebDecision.LoadFallback(fallback),ReviewWebNavigation().decide(intent(fallback),true))
    }
    @Test fun httpsMapsIntentDataWorksWithoutAnExplicitFallbackExtra() {
        val link="intent://maps.google.com/maps?q=Cafe#Intent;scheme=https;package=com.google.android.apps.maps;end"
        assertEquals("https://maps.google.com/maps?q=Cafe",googleMapsBrowserFallback(link))
    }
    @Test fun unsafeOrNonMapsFallbacksCannotExecuteOrLaunchAnything() {
        for (fallback in listOf("http://www.google.com/maps", "javascript:alert(1)", "file:///data/private", "https://evil.example/maps", "https://www.google.com/maps.evil", "https://user:password@www.google.com/maps", "https://www.google.com/url?q=https://evil.example")) {
            assertNull(googleMapsBrowserFallback(intent(fallback)))
            assertEquals(ReviewWebDecision.ShowUnavailable,ReviewWebNavigation().decide(intent(fallback),true))
        }
    }
    @Test fun normalWebAndChildBlankFramesContinueWithoutFalseBlockedBanner() {
        val navigation=ReviewWebNavigation()
        assertEquals(ReviewWebDecision.StayInWebView,navigation.decide("https://www.google.com/maps",true))
        assertEquals(ReviewWebDecision.StayInWebView,navigation.decide("about:blank",false))
        assertEquals(ReviewWebDecision.BlockSubframe,navigation.decide("intent://maps/#Intent;scheme=https;end",false))
    }
    @Test fun missingMalformedAndRepeatedFallbacksStopWithRecovery() {
        val navigation=ReviewWebNavigation()
        val link=intent("https://www.google.com/maps/place/Cafe")
        assertTrue(navigation.decide(link,true) is ReviewWebDecision.LoadFallback)
        assertEquals(ReviewWebDecision.ShowUnavailable,navigation.decide(link,true))
        assertEquals(ReviewWebDecision.ShowUnavailable,navigation.decide("intent:not-an-intent",true))
        assertEquals(ReviewWebDecision.ShowUnavailable,navigation.decide("geo:5.28,115.17",true))
    }
    @Test fun fallbackAttemptsHaveAFiniteBudgetAndRetryGetsANewBudget() {
        val navigation=ReviewWebNavigation()
        for (n in 1..3) assertTrue(navigation.decide(intent("https://www.google.com/maps?retry=$n"),true) is ReviewWebDecision.LoadFallback)
        assertEquals(ReviewWebDecision.ShowUnavailable,navigation.decide(intent("https://www.google.com/maps?retry=4"),true))
        assertTrue(ReviewWebNavigation().decide(intent("https://www.google.com/maps?retry=4"),true) is ReviewWebDecision.LoadFallback)
    }
}
