package com.example.dinefinder.ar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteConfigurationRefreshTest {
    @Test
    fun ordinaryResumeKeepsRejectedConfigurationBlocked() {
        assertFalse(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.KEY_REJECTED, false))
        assertFalse(shouldRefreshRouteConfiguration("", "", WalkingRouteFailure.MISSING_KEY, false))
    }

    @Test
    fun settingsReturnAllowsOneSameKeyRetryForCloudConfigurationFix() {
        assertTrue(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.KEY_REJECTED, true))
        assertTrue(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.INVALID_REQUEST, true))
        assertFalse(shouldRefreshRouteConfiguration("same-key", "same-key", null, true))
        assertFalse(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.KEY_REJECTED, false))
    }

    @Test
    fun settingsReturnDoesNotBypassTransientFailureCooldown() {
        assertFalse(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.NETWORK, true))
        assertFalse(shouldRefreshRouteConfiguration("same-key", "same-key", WalkingRouteFailure.QUOTA_EXCEEDED, true))
    }

    @Test
    fun changingOrRemovingKeyAlwaysDiscardsOldRoute() {
        assertTrue(shouldRefreshRouteConfiguration("new-key", "old-key", null, false))
        assertTrue(shouldRefreshRouteConfiguration("", "old-key", null, false))
        assertTrue(shouldRefreshRouteConfiguration("new-key", "old-key", WalkingRouteFailure.KEY_REJECTED, false))
    }
}
