package com.example.dinefinder.ar

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.dinefinder.R
import com.example.dinefinder.camera.CameraArrowView
import com.example.dinefinder.camera.CameraPreviewController
import com.example.dinefinder.camera.CameraPreviewFrame
import com.example.dinefinder.camera.NavigationCameraFrame
import com.example.dinefinder.camera.NavigationCameraSource
import com.example.dinefinder.camera.NavigationOrientationTracker
import com.example.dinefinder.settings.ApiKeyStore
import com.example.dinefinder.settings.GoogleMapsAppIdentity
import com.example.dinefinder.settings.UserPreferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ARNavigationActivity : AppCompatActivity() {
    private lateinit var cameraPreview: CameraPreviewController
    private lateinit var cameraArrow: CameraArrowView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var instructionTextView: TextView
    private lateinit var directionTextView: TextView
    private lateinit var upcomingTurnTextView: TextView
    private lateinit var maneuverPreviewCaption: TextView
    private lateinit var maneuverPreviewContainer: View
    private lateinit var distanceTextView: TextView
    private lateinit var routeStatusTextView: TextView
    private lateinit var mapsAttributionTextView: TextView
    private lateinit var retryRouteButton: Button
    private lateinit var arrivalOverlay: FrameLayout
    private lateinit var navigationDestination: NavigationDestination

    private var isNavigationInitialized = false
    private var isForeground = false
    private var isLocationUpdatesRequested = false
    private var hasRequestedPermissions = false
    private var locationRequestFailed = false
    private var cameraFailed = false
    private var hasArrived = false
    private var returningFromSettings = false
    private var latestLocation: Location? = null
    private var lastCameraCaptureTimestamp = 0L
    private var lastCameraObservedAtNanos = 0L
    private var hardwareGeneration = 0

    private var pathPoints = emptyList<RouteCoordinate>()
    private var routeSteps = emptyList<WalkingRouteStep>()
    private var currentStepIndex = 0
    private var currentTargetIndex = 0
    private var currentGuidance: WalkingStepGuidance? = null
    private val instructionTracker = RouteInstructionTracker()
    private var routeRequestGate = RouteRequestGate()
    private var routeApiKey = ""
    private var routeConfigurationGeneration = 0
    private var lastRouteFailure: WalkingRouteFailure? = null
    private var routeRequestJob: Job? = null
    private val apiKeyStore by lazy { ApiKeyStore(this) }
    private val routingIdentity by lazy { GoogleMapsAppIdentity.from(this) }
    private val walkingRouteClient by lazy {
        WalkingRouteClient(Retrofit.Builder().baseUrl("https://routes.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create()).build().create(RoutesService::class.java))
    }
    private val orientationTracker by lazy { NavigationOrientationTracker(this) }
    private val watchdogHandler = Handler(Looper.getMainLooper())
    private val watchdog = object : Runnable {
        override fun run() {
            if (!isForeground || !isNavigationInitialized || hasArrived) return
            if (!hasNavigationPermissions() && isLocationUpdatesRequested) stopHardware()
            renderNavigationState()
            watchdogHandler.postDelayed(this, 250L)
        }
    }
    private val destinationCoordinate get() = RouteCoordinate(navigationDestination.latitude, navigationDestination.longitude)

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            if (!isForeground || !isLocationUpdatesRequested || !hasNavigationPermissions() || hasArrived) return
            result.locations.forEach { fix ->
                latestLocation = fix
                locationRequestFailed = false
                usableNavigationLocation(fix, SystemClock.elapsedRealtimeNanos())?.let { location ->
                    updateGuidance(location)
                    if (!hasArrived) fetchPedestrianPath(location)
                }
            }
            renderNavigationState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = preferredNightMode()
        super.onCreate(savedInstanceState)
        returningFromSettings = savedInstanceState?.getBoolean(RETURNING_FROM_SETTINGS) == true
        val destination = NavigationDestination.create(
            intent?.getStringExtra("restaurant_name"),
            intent?.takeIf { it.hasExtra("restaurant_lat") }?.getDoubleExtra("restaurant_lat", Double.NaN),
            intent?.takeIf { it.hasExtra("restaurant_lng") }?.getDoubleExtra("restaurant_lng", Double.NaN)
        )
        if (destination == null) {
            Toast.makeText(this, R.string.navigation_invalid_destination, Toast.LENGTH_LONG).show()
            finish()
            return
        }
        navigationDestination = destination
        enableEdgeToEdge()
        setContentView(R.layout.activity_ar_navigation)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.navigationRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        instructionTextView = findViewById(R.id.instructionText)
        directionTextView = findViewById(R.id.directionText)
        upcomingTurnTextView = findViewById(R.id.upcomingTurnText)
        maneuverPreviewCaption = findViewById(R.id.maneuverPreviewCaption)
        maneuverPreviewContainer = findViewById(R.id.maneuverPreviewContainer)
        distanceTextView = findViewById(R.id.distanceValue)
        routeStatusTextView = findViewById(R.id.routeStatus)
        mapsAttributionTextView = findViewById(R.id.googleMapsAttribution)
        retryRouteButton = findViewById(R.id.retryRouteButton)
        arrivalOverlay = findViewById(R.id.arrivalOverlay)
        instructionTextView.text = getString(R.string.navigation_to_restaurant, destination.name)
        findViewById<TextView>(R.id.arrivalRestaurantName).text = destination.name
        findViewById<View>(R.id.viewRestaurantButton).setOnClickListener { finish() }
        findViewById<View>(R.id.finishNavigationButton).setOnClickListener {
            startActivity(Intent(this, com.example.dinefinder.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }
        retryRouteButton.setOnClickListener { retryNavigation() }
        findViewById<View>(R.id.navigationBackButton).setOnClickListener { finish() }
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        cameraPreview = CameraPreviewController(this, findViewById(R.id.sceneViewContainer), ::onCameraFrame) {
            if (isForeground && !hasArrived) {
                cameraFailed = true
                stopHardware()
                renderNavigationState()
            }
        }
        cameraArrow = CameraArrowView(this).apply { visibility = View.INVISIBLE }
        findViewById<FrameLayout>(R.id.maneuverArrowSlot).addView(cameraArrow,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        orientationTracker.onOrientationChanged = { updateArrow() }
        isNavigationInitialized = true
        refreshRouteConfiguration()
        renderNavigationState()
    }

    private fun preferredNightMode() = if (UserPreferences(this).isDarkMode()) {
        AppCompatDelegate.MODE_NIGHT_YES
    } else AppCompatDelegate.MODE_NIGHT_NO

    private fun hasPermission(permission: String) = ActivityCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    private fun hasNavigationPermissions() = hasPermission(Manifest.permission.CAMERA) && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun locationEnabled() = (getSystemService(LOCATION_SERVICE) as LocationManager).isLocationEnabled

    private fun startNavigation(requestPermission: Boolean = false) {
        if (!isForeground || hasArrived || isLocationUpdatesRequested) return
        if (!hasNavigationPermissions()) {
            renderNavigationState()
            if (requestPermission && !hasRequestedPermissions) {
                hasRequestedPermissions = true
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA,
                    Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_REQUEST)
            }
            return
        }
        cameraFailed = false
        locationRequestFailed = false
        isLocationUpdatesRequested = true
        val generation = ++hardwareGeneration
        cameraPreview.start()
        orientationTracker.start()
        if (!isLocationUpdatesRequested || !hasNavigationPermissions()) return
        try {
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2_000)
                .setMinUpdateIntervalMillis(1_000).build()
            fusedLocationClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
                .addOnFailureListener {
                    if (isForeground && generation == hardwareGeneration) {
                        locationRequestFailed = true
                        stopHardware()
                        renderNavigationState()
                    }
                }
        } catch (_: SecurityException) {
            locationRequestFailed = true
            stopHardware()
        }
        renderNavigationState()
    }

    private fun retryNavigation() {
        if (!hasNavigationPermissions()) {
            val denied = listOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION).filter { !hasPermission(it) }
            if (hasRequestedPermissions && denied.any { !ActivityCompat.shouldShowRequestPermissionRationale(this, it) }) {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply { data = Uri.fromParts("package", packageName, null) })
            } else {
                hasRequestedPermissions = false
                startNavigation(requestPermission = true)
            }
        } else if (routeApiKey.isBlank() || lastRouteFailure?.needsSettings == true) {
            returningFromSettings = true
            startActivity(Intent(this, com.example.dinefinder.MainActivity::class.java).apply {
                putExtra("open_settings", true)
                putExtra("return_to_navigation", true)
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            })
        } else if (!locationEnabled()) {
            startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
        } else if (!isLocationUpdatesRequested) {
            startNavigation()
        } else {
            val fix = usableNavigationLocation(latestLocation, SystemClock.elapsedRealtimeNanos()) ?: return
            if (routeRequestGate.state == RouteRequestGate.State.READY && currentGuidance?.onRoute != true) resetRoute()
            fetchPedestrianPath(fix, forceRetry = true)
        }
    }

    private fun resetRoute() {
        routeConfigurationGeneration++
        routeRequestJob?.cancel()
        routeRequestJob = null
        routeRequestGate = RouteRequestGate()
        lastRouteFailure = null
        pathPoints = emptyList()
        routeSteps = emptyList()
        currentStepIndex = 0
        currentTargetIndex = 0
        currentGuidance = null
        instructionTracker.reset()
        removeArrow()
    }

    private fun refreshRouteConfiguration(allowSameKeyRetry: Boolean = false) {
        val savedKey = apiKeyStore.apiKey()
        if (!shouldRefreshRouteConfiguration(savedKey, routeApiKey, lastRouteFailure, allowSameKeyRetry)) return
        routeApiKey = savedKey
        resetRoute()
    }

    private fun fetchPedestrianPath(currentLocation: RouteCoordinate, forceRetry: Boolean = false) {
        if (routeApiKey.isBlank()) {
            lastRouteFailure = WalkingRouteFailure.MISSING_KEY
            removeArrow()
            return
        }
        if (lastRouteFailure?.needsSettings == true || hasArrived || !isForeground ||
            !routeRequestGate.tryStart(SystemClock.elapsedRealtime(), forceRetry)) return
        val gate = routeRequestGate
        val generation = routeConfigurationGeneration
        val key = routeApiKey
        lastRouteFailure = null
        removeArrow()
        renderNavigationState()
        routeRequestJob = lifecycleScope.launch {
            try {
                val result = walkingRouteClient.fetch(key, routingIdentity, currentLocation, destinationCoordinate)
                if (generation != routeConfigurationGeneration || !isForeground || !isLocationUpdatesRequested) return@launch
                when (result) {
                    is WalkingRouteResult.Success -> {
                        pathPoints = result.points
                        routeSteps = result.steps
                        currentStepIndex = 0
                        currentTargetIndex = 0
                        instructionTracker.reset()
                        gate.succeeded()
                    }
                    is WalkingRouteResult.Failure -> {
                        lastRouteFailure = result.reason
                        gate.failed(SystemClock.elapsedRealtime())
                    }
                }
                usableNavigationLocation(latestLocation, SystemClock.elapsedRealtimeNanos())?.let(::updateGuidance)
                renderNavigationState()
            } catch (error: CancellationException) {
                if (generation == routeConfigurationGeneration) gate.cancelled()
                throw error
            } finally {
                if (generation == routeConfigurationGeneration) routeRequestJob = null
            }
        }
    }

    private fun updateGuidance(location: RouteCoordinate) {
        if (hasArrived) return
        val distance = routeDistanceMeters(location, destinationCoordinate)
        distanceTextView.text = getString(R.string.navigation_distance_meters, distance.toInt())
        val routed = routeRequestGate.state == RouteRequestGate.State.READY
        if (routed) {
            currentGuidance = walkingStepGuidance(routeSteps, pathPoints, location, currentStepIndex, currentTargetIndex)
            currentGuidance?.let {
                currentStepIndex = it.stepIndex
                currentTargetIndex = it.targetIndex
            }
        } else {
            currentGuidance = null
        }
        val arrivalAllowed = routed && currentGuidance?.arrived == true
        if (arrivalAllowed && distance <= 10 && latestLocation?.accuracy?.let { it <= 10f } == true) {
            hasArrived = true
            directionTextView.setText(R.string.direction_arrive)
            arrivalOverlay.visibility = View.VISIBLE
            stopHardware()
            retryRouteButton.visibility = View.GONE
        }
    }

    private fun onCameraFrame(frame: CameraPreviewFrame) {
        if (!isForeground || !hasNavigationPermissions() || hasArrived) return
        orientationTracker.acceptFrame(
            NavigationCameraFrame(
                frame = frame,
                cameraToWorld = null,
                position = null,
                tracking = false,
                source = NavigationCameraSource.SENSORS
            )
        )
        val wasFresh = cameraIsFresh(frame.observedAtNanos)
        if (frame.captureTimestampNanos > lastCameraCaptureTimestamp) {
            lastCameraCaptureTimestamp = frame.captureTimestampNanos
            lastCameraObservedAtNanos = frame.observedAtNanos
        }
        // GPS and the watchdog update guidance. Camera frames only need to
        // refresh it when a live preview becomes available again.
        if (!wasFresh) updateArrow()
    }

    private fun currentManeuver(location: RouteCoordinate?) = instructionTracker.guidance(
        routeRequestGate.state, pathPoints, routeSteps, currentGuidance, location
    )

    private fun maneuverInstruction(maneuver: RouteManeuverGuidance): String = when {
        maneuver.instruction != null && maneuver.isApproachingTurn ->
            getString(R.string.navigation_junction_maneuver, maneuver.instruction)
        maneuver.instruction != null -> getString(R.string.navigation_current_maneuver, maneuver.instruction)
        maneuver.isStraightPath && maneuver.remainingMeters > 0 ->
            getString(R.string.navigation_continue_straight_distance, maneuver.remainingMeters)
        maneuver.isStraightPath -> getString(R.string.navigation_continue_straight)
        else -> getString(R.string.navigation_continue_path)
    }

    private fun updateArrow() {
        renderNavigationState()
    }

    private fun renderArrow(maneuver: RouteManeuverGuidance?, location: RouteCoordinate?, now: Long) {
        val staticAngle = maneuver?.directionDegrees
        if (!isForeground || hasArrived || !hasNavigationPermissions() || !locationEnabled() || location == null ||
            cameraFailed || locationRequestFailed || !cameraIsFresh(now) || maneuver == null || staticAngle == null || routeApiKey.isBlank() || lastRouteFailure != null) {
            removeArrow()
            return
        }

        val targetCoord = if (currentGuidance?.onRoute == true) currentGuidance!!.target else destinationCoordinate
        @Suppress("DEPRECATION")
        val displayRotationDegrees = when (windowManager.defaultDisplay?.rotation) {
            android.view.Surface.ROTATION_90 -> 90
            android.view.Surface.ROTATION_180 -> 180
            android.view.Surface.ROTATION_270 -> 270
            else -> 0
        }
        val fix = latestLocation ?: Location("gps").apply {
            latitude = location.latitude
            longitude = location.longitude
            elapsedRealtimeNanos = now
        }
        val cameraToTrueEnu = orientationTracker.pose(fix, displayRotationDegrees, now)?.cameraToTrueEnu
            ?: orientationTracker.cameraToTrueEnu(fix, displayRotationDegrees, now)

        val angle = if (cameraToTrueEnu != null) {
            val dxMeters = routeLongitudeDelta(location.longitude, targetCoord.longitude) *
                6_371_000.0 * Math.toRadians(1.0) * kotlin.math.cos(Math.toRadians(location.latitude))
            val dyMeters = (targetCoord.latitude - location.latitude) *
                6_371_000.0 * Math.toRadians(1.0)
            val x = cameraToTrueEnu[0] * dxMeters + cameraToTrueEnu[3] * dyMeters
            val z = cameraToTrueEnu[2] * dxMeters + cameraToTrueEnu[5] * dyMeters
            Math.toDegrees(kotlin.math.atan2(x, -z)).toFloat()
        } else {
            staticAngle
        }

        val instruction = maneuverInstruction(maneuver)
        maneuverPreviewContainer.visibility = View.VISIBLE
        cameraArrow.visibility = View.VISIBLE
        cameraArrow.setManeuver(angle, instruction)
        val caption = getString(R.string.navigation_current_step_caption, instruction)
        if (maneuverPreviewCaption.text.toString() != caption) maneuverPreviewCaption.text = caption
        maneuverPreviewCaption.visibility = View.VISIBLE
    }

    private fun cameraIsFresh(nowNanos: Long) = lastCameraCaptureTimestamp > 0 &&
        nowNanos - lastCameraObservedAtNanos in 0..1_500_000_000L

    private fun removeArrow() {
        cameraArrow.clearDirection()
        cameraArrow.visibility = View.INVISIBLE
        maneuverPreviewContainer.visibility = View.GONE
        maneuverPreviewCaption.text = ""
        maneuverPreviewCaption.visibility = View.GONE
    }

    private fun renderNavigationState() {
        if (!isNavigationInitialized || hasArrived) return
        val cameraPermission = hasPermission(Manifest.permission.CAMERA)
        val finePermission = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val now = SystemClock.elapsedRealtimeNanos()
        val location = usableNavigationLocation(latestLocation, now)
        val ready = routeRequestGate.state == RouteRequestGate.State.READY && pathPoints.isNotEmpty()
        val needsSettings = routeApiKey.isBlank() || lastRouteFailure?.needsSettings == true
        val guidance = currentGuidance
        val maneuver = currentManeuver(location)
        directionTextView.text = when {
            !cameraPermission -> getString(R.string.navigation_camera_permission_needed)
            !finePermission -> getString(R.string.navigation_permission_needed)
            location == null -> getString(R.string.navigation_waiting_location)
            ready && guidance?.onRoute == false -> getString(R.string.navigation_off_route)
            ready && guidance?.arrived == true -> getString(R.string.navigation_route_end)
            ready && guidance == null -> getString(R.string.navigation_route_required)
            ready -> maneuver?.let(::maneuverInstruction) ?: getString(R.string.navigation_continue_path)
            else -> getString(R.string.navigation_direction_to_restaurant)
        }
        // Text and model use one tracker snapshot, including camera-only
        // refreshes. A future turn stays on the separate "Then" line until
        // the walker reaches its junction window.
        upcomingTurnTextView.text = if (isForeground && cameraPermission && finePermission && locationEnabled() &&
            location != null && ready && maneuver != null) {
            if (maneuver.upcomingInstruction != null && maneuver.metersToManeuver != null) {
                if (maneuver.metersToManeuver > 0) getString(R.string.navigation_then_turn,
                    maneuver.metersToManeuver, maneuver.upcomingInstruction)
                else getString(R.string.navigation_then_at_junction, maneuver.upcomingInstruction)
            } else if (routeSteps.isEmpty()) getString(R.string.navigation_remaining_path, maneuver.remainingMeters)
            else getString(R.string.navigation_remaining_step, maneuver.remainingMeters)
        } else ""
        upcomingTurnTextView.visibility = if (upcomingTurnTextView.text.isEmpty()) View.GONE else View.VISIBLE
        routeStatusTextView.setText(when {
            !cameraPermission -> R.string.navigation_camera_permission_hint
            !finePermission -> R.string.navigation_permission_hint
            cameraFailed -> R.string.navigation_camera_unavailable
            !locationEnabled() -> R.string.navigation_location_off
            locationRequestFailed -> R.string.navigation_location_unavailable
            routeApiKey.isBlank() -> R.string.navigation_api_key_required
            lastRouteFailure?.needsSettings == true -> lastRouteFailure!!.messageResource()
            location == null && latestLocation != null -> R.string.navigation_precise_fix_needed
            location == null -> R.string.navigation_waiting_location_hint
            ready && guidance?.onRoute == false -> R.string.navigation_off_route_hint
            ready && guidance?.arrived == true -> R.string.navigation_route_end_hint
            ready && guidance == null -> R.string.navigation_route_geometry_unavailable
            ready && !cameraIsFresh(now) -> R.string.navigation_waiting_camera
            ready -> R.string.navigation_route_loaded
            routeRequestGate.state == RouteRequestGate.State.FAILED -> lastRouteFailure?.messageResource() ?: R.string.navigation_route_unavailable
            else -> R.string.navigation_loading_route
        })
        if (location == null) distanceTextView.setText(R.string.navigation_unknown_distance)
        mapsAttributionTextView.visibility = if (ready) View.VISIBLE else View.GONE
        retryRouteButton.setText(when {
            !cameraPermission || !finePermission -> R.string.navigation_enable_permissions
            needsSettings -> R.string.navigation_open_settings
            !locationEnabled() -> R.string.navigation_enable_location
            cameraFailed -> R.string.navigation_retry_camera
            locationRequestFailed -> R.string.navigation_retry_location
            location != null && ready && guidance?.onRoute != true -> R.string.navigation_reroute
            else -> R.string.navigation_retry_route
        })
        retryRouteButton.visibility = if (!cameraPermission || !finePermission || needsSettings || !locationEnabled() ||
            cameraFailed || locationRequestFailed || (location != null && ready && guidance?.onRoute != true) ||
            (location != null && routeRequestGate.state == RouteRequestGate.State.FAILED)) View.VISIBLE else View.GONE
        renderArrow(maneuver, location, now)
    }

    override fun onResume() {
        val preferredMode = preferredNightMode()
        val themeChanged = delegate.localNightMode != preferredMode
        if (themeChanged) delegate.localNightMode = preferredMode
        super.onResume()
        if (themeChanged || !isNavigationInitialized) return
        isForeground = true
        refreshRouteConfiguration(allowSameKeyRetry = returningFromSettings)
        returningFromSettings = false
        startNavigation(requestPermission = true)
        watchdogHandler.removeCallbacks(watchdog)
        watchdogHandler.post(watchdog)
    }

    private fun stopHardware() {
        if (!isNavigationInitialized) return
        hardwareGeneration++
        cameraPreview.stop()
        orientationTracker.stop()
        fusedLocationClient.removeLocationUpdates(locationCallback)
        isLocationUpdatesRequested = false
        latestLocation = null
        lastCameraCaptureTimestamp = 0L
        lastCameraObservedAtNanos = 0L
        routeConfigurationGeneration++
        routeRequestJob?.cancel()
        routeRequestJob = null
        routeRequestGate.cancelled()
        removeArrow()
    }

    override fun onPause() {
        isForeground = false
        watchdogHandler.removeCallbacks(watchdog)
        stopHardware()
        super.onPause()
    }

    override fun onDestroy() {
        watchdogHandler.removeCallbacks(watchdog)
        if (isNavigationInitialized) { stopHardware(); cameraPreview.close() }
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(RETURNING_FROM_SETTINGS, returningFromSettings)
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (!isNavigationInitialized || requestCode != PERMISSION_REQUEST) return
        if (hasNavigationPermissions()) startNavigation() else { stopHardware(); renderNavigationState() }
    }

    companion object {
        private const val PERMISSION_REQUEST = 100
        private const val RETURNING_FROM_SETTINGS = "navigation_returning_from_settings"
    }
}

internal fun shouldRefreshRouteConfiguration(savedKey: String, currentKey: String, failure: WalkingRouteFailure?, returnedFromSettings: Boolean) =
    savedKey != currentKey || (returnedFromSettings && failure?.needsSettings == true)

internal fun WalkingRouteFailure.messageResource(): Int = when (this) {
    WalkingRouteFailure.MISSING_KEY -> R.string.navigation_api_key_required
    WalkingRouteFailure.MISSING_APP_IDENTITY -> R.string.navigation_api_identity_unavailable
    WalkingRouteFailure.INVALID_REQUEST -> R.string.navigation_api_request_rejected
    WalkingRouteFailure.KEY_REJECTED -> R.string.navigation_api_key_rejected
    WalkingRouteFailure.QUOTA_EXCEEDED -> R.string.navigation_route_quota_exceeded
    WalkingRouteFailure.NO_ROUTE -> R.string.navigation_no_walking_route
    WalkingRouteFailure.NETWORK -> R.string.navigation_route_network_error
    WalkingRouteFailure.UNAVAILABLE, WalkingRouteFailure.INVALID_RESPONSE -> R.string.navigation_route_unavailable
}
