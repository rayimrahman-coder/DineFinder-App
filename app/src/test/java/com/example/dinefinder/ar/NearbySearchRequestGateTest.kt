package com.example.dinefinder.ar

import com.example.dinefinder.data.DiscoveryLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbySearchRequestGateTest {
    private val origin = DiscoveryLocation(0.0, 0.0)
    private val moved = DiscoveryLocation(0.002, 0.0)

    @Test
    fun frequentLocationAndCameraUpdatesCannotOverlapRequests() {
        val gate = NearbySearchRequestGate()
        assertNotNull(gate.tryStart(origin, 0))
        repeat(100) { assertNull(gate.tryStart(moved, 1_000L + it)) }
        assertEquals(NearbySearchRequestGate.State.LOADING, gate.state)
    }

    @Test
    fun stillLocationWaitsTwoMinutesAfterSuccessIncludingNoResults() {
        val gate = NearbySearchRequestGate()
        val ticket = gate.tryStart(origin, 0)!!
        assertTrue(gate.succeeded(ticket, origin, 1_000))
        assertNull(gate.tryStart(origin, 120_999))
        assertNotNull(gate.tryStart(origin, 121_000))
    }

    @Test
    fun meaningfulMovementRefreshesOnlyAfterTheMinimumRequestInterval() {
        val gate = NearbySearchRequestGate()
        val ticket = gate.tryStart(origin, 0)!!
        gate.succeeded(ticket, origin, 1_000)
        assertNull(gate.tryStart(moved, 29_999))
        assertNotNull(gate.tryStart(moved, 30_000))
    }

    @Test
    fun smallGpsMovementDoesNotCauseRepeatedSearches() {
        val gate = NearbySearchRequestGate()
        val ticket = gate.tryStart(origin, 0)!!
        gate.succeeded(ticket, origin, 1_000)
        assertNull(gate.tryStart(DiscoveryLocation(0.0001, 0.0), 60_000))
    }

    @Test
    fun failuresBackOffAndRejectedConfigurationStaysBlocked() {
        val gate = NearbySearchRequestGate()
        var ticket = gate.tryStart(origin, 0)!!
        assertTrue(gate.failed(ticket, 1_000, retryDelayMillis = 60_000))
        assertNull(gate.tryStart(origin, 60_999))
        ticket = gate.tryStart(origin, 61_000)!!
        gate.failed(ticket, 62_000, blockConfiguration = true)
        assertNull(gate.tryStart(moved, 1_000_000))
        gate.reset()
        assertNotNull(gate.tryStart(moved, 1_000_001))
    }

    @Test
    fun canceledOrOldKeyResponsesCannotReplaceTheCurrentSearchState() {
        val gate = NearbySearchRequestGate()
        val oldTicket = gate.tryStart(origin, 0)!!
        gate.cancelInFlight()
        assertFalse(gate.succeeded(oldTicket, origin, 1_000))
        assertNull(gate.tryStart(origin, 2_000))
        gate.reset()
        val newTicket = gate.tryStart(moved, 3_000)!!
        assertFalse(gate.failed(oldTicket, 4_000, blockConfiguration = true))
        assertTrue(gate.succeeded(newTicket, moved, 4_000))
        assertEquals(NearbySearchRequestGate.State.READY, gate.state)
    }

    @Test
    fun restoredRejectionRequiresAnExplicitReset() {
        val gate = NearbySearchRequestGate()
        gate.blockConfiguration()
        assertNull(gate.tryStart(origin, 0))
        gate.reset()
        assertNotNull(gate.tryStart(origin, 1))
    }

    @Test
    fun invalidCoordinatesNeverStartANetworkRequest() {
        val gate = NearbySearchRequestGate()
        assertNull(gate.tryStart(DiscoveryLocation(Double.NaN, 0.0), 0))
        assertNull(gate.tryStart(DiscoveryLocation(91.0, 0.0), 0))
        assertEquals(NearbySearchRequestGate.State.IDLE, gate.state)
    }
}
