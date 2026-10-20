package com.example.dinefinder.camera

import android.Manifest
import android.content.pm.PackageManager
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.annotation.MainThread
import androidx.core.content.ContextCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.UnavailableUserDeclinedInstallationException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

enum class NavigationCameraSource { ARCORE, SENSORS }

data class NavigationCameraFrame(
    val frame: CameraPreviewFrame,
    /** Display-oriented camera-to-AR-world rotation, row-major 3 by 3. */
    val cameraToWorld: DoubleArray? = null,
    /** ARCore's local metric translation; it is not a latitude or longitude. */
    val position: DoubleArray? = null,
    val tracking: Boolean = false,
    val source: NavigationCameraSource = NavigationCameraSource.SENSORS
)

/**
 * ARCore owns both the live camera and pose when available. CameraX is the sole camera owner
 * on other devices. Every callback runs on the main thread and belongs to the current start.
 */
class NavigationCameraController(
    private val activity: ComponentActivity,
    private val container: FrameLayout,
    private val onFrame: (NavigationCameraFrame) -> Unit,
    private val onError: (String) -> Unit
) : DefaultLifecycleObserver {
    private val handler = Handler(Looper.getMainLooper())
    private val delivery = NavigationFrameDelivery()
    private val pendingFrame = AtomicReference<PendingFrame?>(null)
    private val callbackPosted = AtomicBoolean(false)
    private val closeExecutor = Executors.newSingleThreadExecutor()
    private var closed = false
    private var fallback: CameraPreviewController? = null
    private var session: Session? = null
    private var surface: GLSurfaceView? = null
    private var renderer: NavigationRenderer? = null
    private var installRequested = false
    private var arcoreDisabled = false
    private var availabilityAttempts = 0
    private var availabilityRetry: Runnable? = null
    private var lastDeliveredFrame: NavigationCameraFrame? = null

    private data class PendingFrame(val generation: Long, val frame: NavigationCameraFrame)

    init {
        activity.lifecycle.addObserver(this)
    }

    @MainThread
    fun start() {
        if (closed || delivery.active) return
        if (!cameraPermission()) {
            onError("Allow camera access to use walking navigation.")
            return
        }
        val generation = delivery.start()
        availabilityAttempts = 0
        lastDeliveredFrame = null
        if (resumed()) startBackend(generation)
    }

    @MainThread
    fun stop() {
        delivery.stop()
        availabilityRetry?.let(handler::removeCallbacks)
        availabilityRetry = null
        pendingFrame.set(null)
        lastDeliveredFrame = null
        fallback?.close()
        fallback = null
        releaseARCore()
    }

    @MainThread
    fun close() {
        if (closed) return
        stop()
        closed = true
        activity.lifecycle.removeObserver(this)
        closeExecutor.shutdown()
    }

    override fun onResume(owner: LifecycleOwner) {
        if (delivery.active && session == null && fallback == null) startBackend(delivery.generation)
    }

    override fun onPause(owner: LifecycleOwner) = stop()
    override fun onDestroy(owner: LifecycleOwner) = close()

    private fun startBackend(generation: Long) {
        if (!isCurrent(generation) || !resumed() || session != null || fallback != null) return
        if (!cameraPermission()) {
            fail(generation, "Allow camera access to use walking navigation.")
            return
        }
        if (arcoreDisabled) {
            startFallback(generation)
            return
        }
        try {
            val apk = ArCoreApk.getInstance()
            val availability = apk.checkAvailability(activity)
            when {
                availability.isTransient -> {
                    if (++availabilityAttempts <= AVAILABILITY_RETRIES) {
                        availabilityRetry?.let(handler::removeCallbacks)
                        availabilityRetry = Runnable {
                            availabilityRetry = null
                            startBackend(generation)
                        }.also { handler.postDelayed(it, AVAILABILITY_RETRY_MS) }
                    } else {
                        Log.i(TAG, "ARCore availability check timed out; using sensor camera")
                        startFallback(generation)
                    }
                }
                !availability.isSupported -> startFallback(generation)
                else -> {
                    // An optional install prompt is initiated only once by opening navigation.
                    // A subsequent resume checks the result without repeatedly prompting.
                    val userRequestedInstall = !installRequested
                    installRequested = true
                    val status = apk.requestInstall(activity, userRequestedInstall,
                        ArCoreApk.InstallBehavior.OPTIONAL, ArCoreApk.UserMessageType.FEATURE)
                    if (status == ArCoreApk.InstallStatus.INSTALLED) startARCore(generation)
                }
            }
        } catch (_: UnavailableUserDeclinedInstallationException) {
            arcoreDisabled = true
            Log.i(TAG, "Optional ARCore installation declined; using sensor camera")
            startFallback(generation)
        } catch (error: Exception) {
            fallbackAfterError(generation, "ARCore initialization", error)
        } catch (error: LinkageError) {
            fallbackAfterError(generation, "ARCore native runtime", error)
        }
    }

    private fun startARCore(generation: Long) {
        if (!isCurrent(generation)) return
        val nextSession = Session(activity)
        // Keep this reference immediately so a configuration/resume failure also closes it.
        session = nextSession
        nextSession.configure(Config(nextSession).apply {
            focusMode = Config.FocusMode.AUTO
            planeFindingMode = Config.PlaneFindingMode.DISABLED
            lightEstimationMode = Config.LightEstimationMode.DISABLED
            depthMode = Config.DepthMode.DISABLED
            updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        })
        val nextRenderer = NavigationRenderer(generation, nextSession)
        renderer = nextRenderer
        val nextSurface = GLSurfaceView(activity).apply {
            setEGLContextClientVersion(2)
            preserveEGLContextOnPause = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            setRenderer(nextRenderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        surface = nextSurface
        container.addView(nextSurface, 0, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        nextSession.resume()
        nextRenderer.enabled = true
        nextSurface.onResume()
        Log.i(TAG, "ARCore camera started")
    }

    private fun startFallback(generation: Long) {
        if (!isCurrent(generation) || fallback != null) return
        invalidateTrackedPose(generation)
        // A consumer may stop or close navigation while receiving the invalidation.
        if (!isCurrent(generation) || fallback != null) return
        // ARCore is paused and its GL loop stopped before CameraX is allowed to bind.
        if (!releaseARCore()) {
            fail(generation, "Camera unavailable. Try opening navigation again.")
            return
        }
        pendingFrame.set(null)
        // The fallback camera may use a different capture timestamp epoch. Replacing the
        // generation both resets that clock and rejects callbacks from the old AR session.
        val fallbackGeneration = delivery.start()
        lastDeliveredFrame = null
        val nextFallback = CameraPreviewController(activity, container, { frame ->
            deliver(PendingFrame(fallbackGeneration, NavigationCameraFrame(frame)))
        }, { message -> fail(fallbackGeneration, message) })
        fallback = nextFallback
        nextFallback.start()
        Log.i(TAG, "Sensor camera started")
    }

    private fun invalidateTrackedPose(generation: Long) {
        if (!isCurrent(generation) || !resumed() || !cameraPermission()) return
        val tracked = lastDeliveredFrame?.takeIf {
            it.source == NavigationCameraSource.ARCORE && it.tracking
        } ?: return
        // This is a tracking-state change, not a new camera image. Retaining both capture
        // clocks prevents fallback initialization from extending the old image's liveness.
        val invalidated = tracked.copy(cameraToWorld = null, position = null, tracking = false)
        lastDeliveredFrame = invalidated
        onFrame(invalidated)
    }

    private fun fallbackAfterError(generation: Long, operation: String, error: Throwable) {
        if (!isCurrent(generation)) return
        // Exception class is enough for diagnostics without logging locations or API keys.
        Log.w(TAG, "$operation failed (${error.javaClass.simpleName}); using sensor camera")
        arcoreDisabled = true
        startFallback(generation)
    }

    private fun releaseARCore(): Boolean {
        renderer?.enabled = false
        // Never hold a lock shared with the GL thread here. It only posts main-thread work.
        // Waiting for the GL loop to pause prevents Session.update() racing Session.pause().
        surface?.onPause()
        val oldSurface = surface
        surface = null
        renderer = null
        val oldSession = session
        session = null
        var paused = true
        if (oldSession != null) {
            try {
                oldSession.pause()
            } catch (error: Exception) {
                paused = false
                Log.w(TAG, "ARCore pause failed (${error.javaClass.simpleName})")
            }
        }
        oldSurface?.let(container::removeView)
        if (oldSession == null) return true
        if (!paused) {
            // A failed pause gives no camera-release guarantee. Finish teardown before
            // binding CameraX, even though this exceptional path can take longer.
            return closeSession(oldSession)
        }
        // Native teardown can take time; its camera has already been released by pause().
        closeExecutor.execute { closeSession(oldSession) }
        return true
    }

    private fun closeSession(oldSession: Session): Boolean = try {
        oldSession.close()
        true
    } catch (error: Exception) {
        Log.w(TAG, "ARCore close failed (${error.javaClass.simpleName})")
        false
    }

    /** Coalesces GL frames so a busy UI thread can never accumulate a render-sized backlog. */
    private fun postFrame(generation: Long, frame: NavigationCameraFrame) {
        pendingFrame.set(PendingFrame(generation, frame))
        scheduleDelivery()
    }

    private fun scheduleDelivery() {
        if (!callbackPosted.compareAndSet(false, true)) return
        handler.post {
            val next = pendingFrame.getAndSet(null)
            callbackPosted.set(false)
            if (next != null) deliver(next)
            if (pendingFrame.get() != null) scheduleDelivery()
        }
    }

    private fun deliver(pending: PendingFrame) {
        if (!isCurrent(pending.generation)) return
        if (!cameraPermission()) {
            fail(pending.generation, "Allow camera access to use walking navigation.")
            return
        }
        if (delivery.canDeliver(pending.generation, pending.frame, SystemClock.elapsedRealtimeNanos(),
                cameraPermission = true, resumed = resumed())) {
            lastDeliveredFrame = pending.frame
            onFrame(pending.frame)
        }
    }

    private fun cameraPermission() = ContextCompat.checkSelfPermission(
        activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun resumed() = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)

    private fun isCurrent(generation: Long) = !closed && delivery.active &&
        generation == delivery.generation

    private fun fail(generation: Long, message: String) {
        if (!isCurrent(generation)) return
        stop()
        onError(message)
    }

    private inner class NavigationRenderer(
        private val generation: Long,
        private val activeSession: Session
    ) : GLSurfaceView.Renderer {
        @Volatile var enabled = false
        private val background = ARCoreBackgroundRenderer()
        private var textureBound = false
        private var width = 0
        private var height = 0
        private var previousRotation = -1
        private var geometryChanged = true
        private var previousTracking: Boolean? = null
        private var previousTimestamp = 0L
        private var previousObservedAt = 0L
        private var failed = false

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            try {
                GLES20.glClearColor(0f, 0f, 0f, 1f)
                background.create()
                textureBound = false
            } catch (error: Exception) {
                renderFailed(error)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            this.width = width
            this.height = height
            GLES20.glViewport(0, 0, width, height)
            geometryChanged = true
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
            if (!enabled || failed || width <= 0 || height <= 0) return
            try {
                if (!textureBound) {
                    activeSession.setCameraTextureName(background.textureId)
                    textureBound = true
                }
                val rotation = surface?.display?.rotation ?: Surface.ROTATION_0
                if (geometryChanged || rotation != previousRotation) {
                    activeSession.setDisplayGeometry(rotation, width, height)
                    previousRotation = rotation
                    geometryChanged = false
                }
                val frame = activeSession.update()
                background.draw(frame)
                if (frame.timestamp <= 0) return
                if (frame.timestamp != previousTimestamp) {
                    previousTimestamp = frame.timestamp
                    previousObservedAt = SystemClock.elapsedRealtimeNanos()
                }
                val camera = frame.camera
                val tracking = camera.trackingState == TrackingState.TRACKING
                if (tracking != previousTracking) {
                    previousTracking = tracking
                    Log.i(TAG, if (tracking) "ARCore tracking active" else "ARCore tracking paused")
                }
                val projection = FloatArray(16)
                camera.getProjectionMatrix(projection, 0, 0.1f, 1_000f)
                val pose = if (tracking) camera.displayOrientedPose else null
                val matrix = pose?.let { FloatArray(16).also { values -> it.toMatrix(values, 0) } }
                val rotationMatrix = matrix?.let { values ->
                    DoubleArray(9) { index -> values[(index % 3) * 4 + index / 3].toDouble() }
                }
                val translation = pose?.translation?.let { values ->
                    DoubleArray(3) { values[it].toDouble() }
                }
                postFrame(generation, NavigationCameraFrame(
                    CameraPreviewFrame(DoubleArray(16) { projection[it].toDouble() },
                        frame.timestamp, previousObservedAt),
                    rotationMatrix, translation, tracking, NavigationCameraSource.ARCORE))
            } catch (error: Exception) {
                renderFailed(error)
            } catch (error: LinkageError) {
                renderFailed(error)
            }
        }

        private fun renderFailed(error: Throwable) {
            if (failed) return
            failed = true
            enabled = false
            handler.post { fallbackAfterError(generation, "ARCore frame", error) }
        }
    }

    private companion object {
        const val TAG = "DineFinderAR"
        const val AVAILABILITY_RETRIES = 10
        const val AVAILABILITY_RETRY_MS = 250L
    }
}
