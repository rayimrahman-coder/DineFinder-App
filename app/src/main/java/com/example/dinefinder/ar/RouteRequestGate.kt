package com.example.dinefinder.ar

/** Serializes route requests and backs off after failures using a monotonic clock. */
internal class RouteRequestGate(private val retryDelayMillis: Long = 30_000L) {
    enum class State { WAITING, LOADING, READY, FAILED }

    var state = State.WAITING
        private set
    private var retryAfterMillis = 0L

    fun tryStart(nowMillis: Long, forceRetry: Boolean = false): Boolean {
        if (state == State.LOADING || state == State.READY) return false
        if (state == State.FAILED && !forceRetry && nowMillis < retryAfterMillis) return false
        state = State.LOADING
        return true
    }

    fun succeeded() {
        state = State.READY
    }

    fun failed(nowMillis: Long) {
        state = State.FAILED
        retryAfterMillis = nowMillis + retryDelayMillis
    }

    fun cancelled() {
        if (state == State.LOADING) state = State.WAITING
    }
}
