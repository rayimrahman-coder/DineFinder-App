package com.example.dinefinder.camera

/** Gates asynchronous captures so a stopped or replaced camera cannot revive an overlay. */
internal class CameraFrameDelivery {
    var generation: Long = 0
        private set
    var active: Boolean = false
        private set
    private var lastCaptureTimestamp = 0L

    fun start(): Long {
        generation++
        active = true
        lastCaptureTimestamp = 0
        return generation
    }

    fun stop() {
        generation++
        active = false
        lastCaptureTimestamp = 0
    }

    fun canDeliver(
        expectedGeneration: Long,
        captureTimestamp: Long,
        observedAtNanos: Long,
        nowNanos: Long,
        cameraPermission: Boolean,
        resumed: Boolean
    ): Boolean {
        if (!active || expectedGeneration != generation || !cameraPermission || !resumed ||
            captureTimestamp <= 0 || captureTimestamp <= lastCaptureTimestamp ||
            nowNanos - observedAtNanos !in 0..1_000_000_000L) return false
        lastCaptureTimestamp = captureTimestamp
        return true
    }
}
