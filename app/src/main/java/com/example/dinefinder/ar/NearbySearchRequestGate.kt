package com.example.dinefinder.ar

import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.data.RestaurantDiscovery

/** Controls Places refreshes independently of camera frames and orientation events. */
internal class NearbySearchRequestGate {
    enum class State { IDLE, LOADING, READY, RETRY_WAIT, BLOCKED }

    var state = State.IDLE
        private set
    private var activeTicket: Long? = null
    private var nextTicket = 0L
    private var lastAttemptAtMillis: Long? = null
    private var lastSuccessAtMillis: Long? = null
    private var lastSuccessLocation: DiscoveryLocation? = null
    private var retryAfterMillis = 0L

    fun tryStart(location: DiscoveryLocation, nowMillis: Long): Long? {
        if (!location.isValid || activeTicket != null || state == State.BLOCKED) return null
        if (nowMillis < retryAfterMillis) return null
        if (lastAttemptAtMillis?.let { nowMillis - it < MIN_REQUEST_INTERVAL_MILLIS } == true) return null
        val successAt = lastSuccessAtMillis
        val successLocation = lastSuccessLocation
        if (successAt != null && successLocation != null) {
            val movedMeters = RestaurantDiscovery.distanceMeters(successLocation, location) ?: return null
            if (nowMillis - successAt < MAX_RESULT_AGE_MILLIS && movedMeters < REFRESH_MOVEMENT_METERS) return null
        }
        val ticket = ++nextTicket
        activeTicket = ticket
        lastAttemptAtMillis = nowMillis
        state = State.LOADING
        return ticket
    }

    fun succeeded(ticket: Long, location: DiscoveryLocation, nowMillis: Long): Boolean {
        if (activeTicket != ticket) return false
        activeTicket = null
        lastSuccessAtMillis = nowMillis
        lastSuccessLocation = location
        retryAfterMillis = 0L
        state = State.READY
        return true
    }

    fun failed(ticket: Long, nowMillis: Long, retryDelayMillis: Long = 30_000L, blockConfiguration: Boolean = false): Boolean {
        if (activeTicket != ticket) return false
        activeTicket = null
        retryAfterMillis = nowMillis + retryDelayMillis
        state = if (blockConfiguration) State.BLOCKED else State.RETRY_WAIT
        return true
    }

    fun cancelInFlight() {
        if (activeTicket == null) return
        activeTicket = null
        state = if (lastSuccessAtMillis != null) State.READY else State.IDLE
    }

    fun blockConfiguration() {
        activeTicket = null
        state = State.BLOCKED
    }

    /** Call only after a key change or the user's explicit return from Settings. */
    fun reset() {
        activeTicket = null
        lastAttemptAtMillis = null
        lastSuccessAtMillis = null
        lastSuccessLocation = null
        retryAfterMillis = 0L
        state = State.IDLE
    }

    companion object {
        const val MIN_REQUEST_INTERVAL_MILLIS = 30_000L
        const val MAX_RESULT_AGE_MILLIS = 120_000L
        const val REFRESH_MOVEMENT_METERS = 100.0
    }
}
