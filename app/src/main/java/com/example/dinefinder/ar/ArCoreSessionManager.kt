package com.example.dinefinder.ar

import android.content.Context
import android.util.Log
import com.google.ar.core.Anchor
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Earth
import com.google.ar.core.Frame
import com.google.ar.core.GeospatialPose
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException

/**
 * Manages ARCore sessions, 3D spatial pose tracking, and ARCore Geospatial API (VPS)
 * for anchoring virtual content to precise latitude, longitude, and altitude coordinates.
 * Safely falls back to sensor/camera projection if ARCore or Geospatial API is unavailable.
 */
class ArCoreSessionManager(private val context: Context) {
    private var session: Session? = null
    var isArCoreActive = false
        private set

    fun isArCoreSupported(): Boolean {
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            availability.isSupported
        } catch (_: Throwable) {
            false
        }
    }

    fun start(): Boolean {
        if (isArCoreActive) return true
        return try {
            val availability = ArCoreApk.getInstance().checkAvailability(context)
            if (!availability.isSupported) return false

            val newSession = Session(context)
            val config = Config(newSession).apply {
                updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                planeFindingMode = Config.PlaneFindingMode.HORIZONTAL_AND_VERTICAL
                focusMode = Config.FocusMode.AUTO
                geospatialMode = Config.GeospatialMode.ENABLED
            }
            newSession.configure(config)
            newSession.resume()
            session = newSession
            isArCoreActive = true
            true
        } catch (error: Throwable) {
            Log.w("ArCoreSessionManager", "ARCore Geospatial session initialization skipped; using fallback projection", error)
            stop()
            false
        }
    }

    fun updateFrame(): Frame? {
        val currentSession = session ?: return null
        if (!isArCoreActive) return null
        return try {
            val frame = currentSession.update()
            if (frame.camera.trackingState == TrackingState.TRACKING) frame else null
        } catch (_: CameraNotAvailableException) {
            null
        } catch (_: Throwable) {
            null
        }
    }

    fun cameraPose(): Pose? {
        return updateFrame()?.camera?.pose
    }

    fun earthState(): Earth.EarthState? {
        return try {
            session?.earth?.earthState
        } catch (_: Throwable) {
            null
        }
    }

    fun geospatialPose(): GeospatialPose? {
        val earth = session?.earth ?: return null
        if (earth.trackingState != TrackingState.TRACKING) return null
        return try {
            earth.cameraGeospatialPose
        } catch (_: Throwable) {
            null
        }
    }

    fun createGeospatialAnchor(latitude: Double, longitude: Double, altitude: Double = 0.0): Anchor? {
        val earth = session?.earth ?: return null
        if (earth.trackingState != TrackingState.TRACKING) return null
        return try {
            earth.createAnchor(latitude, longitude, altitude, 0f, 0f, 0f, 1f)
        } catch (_: Throwable) {
            null
        }
    }

    fun pause() {
        try {
            session?.pause()
        } catch (_: Throwable) {
        }
    }

    fun stop() {
        isArCoreActive = false
        try {
            session?.pause()
            session?.close()
        } catch (_: Throwable) {
        } finally {
            session = null
        }
    }
}
