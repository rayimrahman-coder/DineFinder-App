package com.example.dinefinder.ar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteRequestGateTest {
    @Test
    fun locationUpdatesAndManualRetryCannotStartConcurrentRequests() {
        val gate = RouteRequestGate()
        assertTrue(gate.tryStart(0))
        assertFalse(gate.tryStart(2_000))
        assertFalse(gate.tryStart(3_000, forceRetry = true))
        assertEquals(RouteRequestGate.State.LOADING, gate.state)
    }

    @Test
    fun failedRequestWaitsThirtySecondsBeforeAutomaticRetry() {
        val gate = RouteRequestGate()
        gate.tryStart(0)
        gate.failed(1_000)
        assertFalse(gate.tryStart(3_000))
        assertFalse(gate.tryStart(30_999))
        assertTrue(gate.tryStart(31_000))
    }

    @Test
    fun manualRetryCanBypassFailureCooldown() {
        val gate = RouteRequestGate()
        gate.tryStart(0)
        gate.failed(1_000)
        assertTrue(gate.tryStart(2_000, forceRetry = true))
        assertFalse(gate.tryStart(2_001, forceRetry = true))
    }

    @Test
    fun loadedRouteIsNotFetchedAgainOnLocationUpdates() {
        val gate = RouteRequestGate()
        gate.tryStart(0)
        gate.succeeded()
        assertFalse(gate.tryStart(60_000))
        assertFalse(gate.tryStart(60_001, forceRetry = true))
        assertEquals(RouteRequestGate.State.READY, gate.state)
    }

    @Test
    fun cancelledRequestCanResumeWithoutWaitingForFailureCooldown() {
        val gate = RouteRequestGate()
        gate.tryStart(0)
        gate.cancelled()
        assertEquals(RouteRequestGate.State.WAITING, gate.state)
        assertTrue(gate.tryStart(1))
    }
}
