package com.example.dinefinder.ar

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextUtils
import android.text.method.LinkMovementMethod
import android.text.style.URLSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.Surface
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.dinefinder.MainActivity
import com.example.dinefinder.R
import com.example.dinefinder.Restaurant
import com.example.dinefinder.RestaurantProfileActivity
import com.example.dinefinder.camera.CameraPreviewController
import com.example.dinefinder.camera.CameraPreviewFrame
import com.example.dinefinder.data.CameraRestaurantMatch
import com.example.dinefinder.data.CameraScanPose
import com.example.dinefinder.data.DiscoveryLocation
import com.example.dinefinder.data.RestaurantCameraScan
import com.example.dinefinder.data.RestaurantDiscovery
import com.example.dinefinder.data.RestaurantRepository
import com.example.dinefinder.places.NearbyRestaurant
import com.example.dinefinder.places.NearbyRestaurantMerger
import com.example.dinefinder.places.PlaceSummary
import com.example.dinefinder.places.PlacesClient
import com.example.dinefinder.places.PlacesFailure
import com.example.dinefinder.places.PlacesSearchResult
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
import java.security.MessageDigest
import kotlin.math.roundToInt

class ARDiscoveryActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sceneViewContainer: FrameLayout
    private lateinit var markersContainer: FrameLayout
    private lateinit var scanControls: View
    private lateinit var overflowHintText: TextView
    private lateinit var hintText: TextView
    private lateinit var setupHintText: TextView
    private lateinit var attributionText: TextView
    private lateinit var providerAttributionText: TextView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager

    private var cameraController: CameraPreviewController? = null
    private var previewRunning = false
    private var cameraError = false
    private var calibrationUnavailable = false
    private var localRestaurants = emptyList<Restaurant>()
    private var googlePlaces = emptyList<PlaceSummary>()
    private var nearbyRestaurants = emptyList<NearbyRestaurant>()
    private var restaurants = emptyList<Restaurant>()
    private var visibleRestaurants = emptyList<CameraRestaurantMatch>()
    private var hasLoadedRestaurants = false
    private var restaurantLoadFailed = false
    private var restaurantObserver: Job? = null
    private var scanActive = false
    private var hasRequestedScanPermissions = false
    private var permissionRequestPending = false
    private var configuredKey = ""
    private var returningFromSettings = false
    private var placesFailure: PlacesFailure? = null
    private var placesJob: Job? = null
    private var placesGeneration = 0
    private val placesGate = NearbySearchRequestGate()
    private val placesClient by lazy { PlacesClient.create() }
    private val merger = NearbyRestaurantMerger()
    private val apiKeyStore by lazy { ApiKeyStore(this) }
    private var attributionSignature = ""

    private var lastFix: Location? = null
    private var magneticDeclinationDegrees: Double? = null
    private var locationUpdatesRequested = false
    private var activeLocationCallback: LocationCallback? = null
    private var locationGeneration = 0
    private var locationFailed = false
    private var locationWaitStartedAtNanos = 0L

    private var rotationSensor: Sensor? = null
    private var orientationRegistered = false
    private var hasOrientation = false
    private var orientationTimestampNanos = 0L
    private var orientationAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    private val deviceToWorld = FloatArray(9)
    private val captureFreshness = CameraCaptureFreshness()
    private var projection: DoubleArray? = null
    private var lastRefreshAtNanos = 0L

    private val scanHandler = Handler(Looper.getMainLooper())
    private val markerViews = mutableMapOf<Int, LinearLayout>()
    private val markerRows = mutableMapOf<Int, Int>()
    private var positioningMarkers = false
    private val restaurantRepository by lazy { RestaurantRepository.getInstance(this) }
    private val scanWatchdog = object : Runnable {
        override fun run() {
            if (!scanActive) return
            if (!hasScanPermissions() || !locationManager.isLocationEnabled) {
                if (locationUpdatesRequested || lastFix != null) stopLocationUpdates()
            } else {
                if (!locationUpdatesRequested && !locationFailed) {
                    startLocationUpdates()
                } else if (locationFailed) {
                    locationFailed = false
                    startLocationUpdates()
                }
                if (cameraError && hasCameraPermission()) {
                    startCameraPreview()
                }
            }
            refreshScan()
            scanHandler.postDelayed(this, SCAN_INTERVAL_MILLIS)
        }
    }
    // Places search is deliberately scheduled separately from camera/sensor rendering.
    private val placesPoll = object : Runnable {
        override fun run() {
            if (!scanActive) return
            maybeSearchPlaces()
            scanHandler.postDelayed(this, PLACES_POLL_INTERVAL_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = if (UserPreferences(this).isDarkMode()) {
            AppCompatDelegate.MODE_NIGHT_YES
        } else {
            AppCompatDelegate.MODE_NIGHT_NO
        }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar_discovery)
        sceneViewContainer = findViewById(R.id.discoverySceneContainer)
        markersContainer = findViewById(R.id.discoveryMarkers)
        scanControls = findViewById(R.id.discoveryControls)
        overflowHintText = findViewById(R.id.discoveryOverflowHint)
        hintText = findViewById(R.id.discoveryHint)
        setupHintText = findViewById(R.id.discoverySetupHint)
        attributionText = findViewById(R.id.discoveryAttribution)
        providerAttributionText = findViewById(R.id.discoveryProviderAttribution)
        providerAttributionText.movementMethod = LinkMovementMethod.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        hasRequestedScanPermissions = savedInstanceState?.getBoolean("scan_permissions_requested") ?: false
        returningFromSettings = savedInstanceState?.getBoolean("returning_from_settings") ?: false
        configuredKey = apiKeyStore.apiKey()
        if (configuredKey.isNotEmpty() && !returningFromSettings &&
            savedInstanceState?.getBoolean("places_configuration_blocked") == true &&
            savedInstanceState.getString("places_key_fingerprint") == keyFingerprint(configuredKey)) {
            placesGate.blockConfiguration()
            placesFailure = PlacesFailure.CONFIG
        }

        val root = findViewById<View>(R.id.discoveryRoot)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshScan(force = true) }
        markersContainer.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionMarkers() }
        scanControls.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> positionMarkers() }
        findViewById<View>(R.id.discoveryBack).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            positionMarkers()
        }
        ViewCompat.requestApplyInsets(root)
        findViewById<View>(R.id.discoveryBack).setOnClickListener {
            it.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                it.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                finish()
            }.start()
        }
        hintText.setOnClickListener { handleHintTap() }
        setupHintText.setOnClickListener { openApiSettings() }
        observeRestaurants()
        refreshScan(force = true)
        if (!hasScanPermissions() && !hasRequestedScanPermissions) requestScanPermissions()
    }

    private fun observeRestaurants() {
        restaurantObserver?.cancel()
        restaurantLoadFailed = false
        restaurantObserver = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    restaurantRepository.observeRestaurants().collect {
                        localRestaurants = it
                        hasLoadedRestaurants = true
                        restaurantLoadFailed = false
                        rebuildNearbyRestaurants()
                        refreshScan(force = true)
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    Log.e("RestaurantScanner", "Could not load saved restaurants", error)
                    restaurantLoadFailed = true
                    hasLoadedRestaurants = false
                    localRestaurants = emptyList()
                    rebuildNearbyRestaurants()
                    refreshScan(force = true)
                }
            }
        }
    }

    private fun rebuildNearbyRestaurants() {
        // Just use local restaurants since we are not using Google Places API
        nearbyRestaurants = merger.mergeNearbyRestaurants(localRestaurants, emptyList())
        restaurants = nearbyRestaurants.map { it.restaurant }
    }

    private fun hasCameraPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasPreciseLocationPermission() = ActivityCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

    private fun hasScanPermissions() = hasCameraPermission() && hasPreciseLocationPermission()

    private fun requestScanPermissions(openSettingsIfDenied: Boolean = false) {
        if (permissionRequestPending) return
        val missingPermissions = arrayOf(Manifest.permission.CAMERA, Manifest.permission.ACCESS_FINE_LOCATION)
            .filter { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (missingPermissions.isEmpty()) {
            startScanServices()
        } else if (openSettingsIfDenied && hasRequestedScanPermissions && missingPermissions.any {
                !ActivityCompat.shouldShowRequestPermissionRationale(this, it)
            }) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
            })
        } else {
            hasRequestedScanPermissions = true
            permissionRequestPending = true
            ActivityCompat.requestPermissions(this, arrayOf(
                Manifest.permission.CAMERA,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ), SCAN_PERMISSION_REQUEST)
        }
    }

    private fun handleHintTap() {
        when {
            !hasScanPermissions() -> requestScanPermissions(openSettingsIfDenied = true)
            !locationManager.isLocationEnabled -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            cameraError -> startCameraPreview()
            currentLocation(SystemClock.elapsedRealtimeNanos()) == null -> {
                stopLocationUpdates()
                locationFailed = false
                startLocationUpdates()
                refreshScan(force = true)
            }
            restaurantLoadFailed -> observeRestaurants()
        }
    }

    private fun openApiSettings() {
        returningFromSettings = true
        startActivity(Intent(this, MainActivity::class.java).apply {
            putExtra("open_settings", true)
            putExtra("return_to_navigation", true)
        })
    }

    private fun reloadConfiguredKey() {
        val savedKey = apiKeyStore.apiKey()
        if (savedKey != configuredKey) {
            cancelPlacesRequest()
            configuredKey = savedKey
            placesGate.reset()
            placesFailure = null
            googlePlaces = emptyList()
            providerAttributionText.text = ""
            attributionSignature = ""
            rebuildNearbyRestaurants()
        } else if (returningFromSettings && placesGate.state == NearbySearchRequestGate.State.BLOCKED) {
            cancelPlacesRequest()
            placesGate.reset()
            placesFailure = null
        }
        returningFromSettings = false
    }

    private fun cancelPlacesRequest() {
        placesGeneration++
        placesJob?.cancel()
        placesGate.cancelInFlight()
    }

    private fun maybeSearchPlaces() {
        // Disabled: No longer using Google Places API.
    }

    private fun startScanServices() {
        if (!scanActive) return
        if (hasCameraPermission()) {
            startCameraPreview()
        }
        if (hasScanPermissions()) {
            startOrientationUpdates()
            startLocationUpdates()
        }
        refreshScan(force = true)
    }

    private fun startCameraPreview() {
        if (!scanActive || !hasCameraPermission()) return
        previewRunning = true
        cameraError = false
        if (cameraController == null) {
            cameraController = CameraPreviewController(
                activity = this, container = sceneViewContainer,
                onFrame = { onCameraFrame(it) },
                onError = {
                    previewRunning = false
                    cameraError = true
                    captureFreshness.reset()
                    projection = null
                    refreshScan(force = true)
                }
            )
        }
        cameraController?.start()
    }

    private fun onCameraFrame(frame: CameraPreviewFrame) {
        if (!scanActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) || !hasCameraPermission()) return
        val hadProjection = projection != null
        captureFreshness.observe(frame.captureTimestampNanos, frame.observedAtNanos)
        calibrationUnavailable = !frame.hasCalibratedProjection
        projection = if (frame.hasCalibratedProjection) {
            frame.projection.copyOf()
        } else {
            val width = markersContainer.width.takeIf { it > 0 } ?: sceneViewContainer.width
            val height = markersContainer.height.takeIf { it > 0 } ?: sceneViewContainer.height
            com.example.dinefinder.camera.CameraProjection.defaultProjection(width, height)
        }
        cameraError = false
        refreshScan(force = !hadProjection)
    }

    private fun startOrientationUpdates() {
        if (orientationRegistered || !scanActive) return
        rotationSensor?.let {
            orientationRegistered = sensorManager.registerListener(
                this, it, SensorManager.SENSOR_DELAY_GAME, scanHandler
            )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!scanActive || !lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) ||
            !orientationRegistered || event.sensor.type != Sensor.TYPE_ROTATION_VECTOR) return
        val wasReliable = hasOrientation && orientationAccuracy > SensorManager.SENSOR_STATUS_UNRELIABLE
        SensorManager.getRotationMatrixFromVector(deviceToWorld, event.values)
        hasOrientation = deviceToWorld.all { it.isFinite() }
        orientationTimestampNanos = event.timestamp
        orientationAccuracy = event.accuracy
        refreshScan(force = wasReliable && (!hasOrientation || orientationAccuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (!scanActive || sensor?.type != Sensor.TYPE_ROTATION_VECTOR) return
        val wasReliable = orientationAccuracy > SensorManager.SENSOR_STATUS_UNRELIABLE
        orientationAccuracy = accuracy
        refreshScan(force = wasReliable && accuracy <= SensorManager.SENSOR_STATUS_UNRELIABLE)
    }

    private fun stopOrientationUpdates() {
        sensorManager.unregisterListener(this)
        orientationRegistered = false
        hasOrientation = false
        orientationTimestampNanos = 0L
        orientationAccuracy = SensorManager.SENSOR_STATUS_UNRELIABLE
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        if (!scanActive || !hasScanPermissions() || !locationManager.isLocationEnabled || locationUpdatesRequested) return
        val generation = ++locationGeneration
        locationUpdatesRequested = true
        locationFailed = false
        locationWaitStartedAtNanos = SystemClock.elapsedRealtimeNanos()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                if (generation == locationGeneration && scanActive &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    locationUpdatesRequested && hasScanPermissions()) {
                    result.lastLocation?.let(::acceptLocation)
                }
            }
        }
        activeLocationCallback = callback
        try {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (generation == locationGeneration && scanActive &&
                    lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                    locationUpdatesRequested && hasScanPermissions()) {
                    location?.let(::acceptLocation)
                }
            }
            val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000)
                .setMinUpdateIntervalMillis(1000)
                .build()
            fusedLocationClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
                .addOnSuccessListener {
                    if (generation != locationGeneration || !scanActive || !hasScanPermissions()) {
                        fusedLocationClient.removeLocationUpdates(callback)
                    }
                }
                .addOnFailureListener {
                    if (generation == locationGeneration && scanActive) {
                        stopLocationUpdates()
                        locationFailed = true
                        refreshScan(force = true)
                    }
                }
        } catch (_: SecurityException) {
            stopLocationUpdates()
            locationFailed = true
            refreshScan(force = true)
        }
    }

    private fun stopLocationUpdates() {
        locationGeneration++
        activeLocationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        activeLocationCallback = null
        locationUpdatesRequested = false
        lastFix = null
        magneticDeclinationDegrees = null
        locationWaitStartedAtNanos = 0L
    }

    private fun acceptLocation(location: Location) {
        lastFix = Location(location)
        locationFailed = false
        magneticDeclinationDegrees = if (usableCameraScanLocation(lastFix, SystemClock.elapsedRealtimeNanos()) != null) {
            GeomagneticField(
                location.latitude.toFloat(), location.longitude.toFloat(),
                if (location.hasAltitude() && location.altitude.isFinite()) location.altitude.toFloat() else 0f,
                location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
            ).declination.toDouble().takeIf { it.isFinite() }
        } else null
        refreshScan(force = true)
        maybeSearchPlaces()
    }

    private fun currentLocation(nowNanos: Long): DiscoveryLocation? =
        if (scanActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            hasScanPermissions() && locationManager.isLocationEnabled)
            usableCameraScanLocation(lastFix, nowNanos) else null

    private fun currentPose(location: DiscoveryLocation?, nowNanos: Long): CameraScanPose? {
        val currentProjection = projection ?: return null
        val declination = magneticDeclinationDegrees ?: return null
        if (location == null || cameraError || !hasOrientation || !orientationRegistered ||
            !captureFreshness.isFresh(nowNanos) ||
            !cameraScanOrientationUsable(orientationTimestampNanos, orientationAccuracy, nowNanos)) return null
        val rotation = when (sceneViewContainer.display?.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        return CameraScanPose(
            deviceToWorld = DoubleArray(9) { deviceToWorld[it].toDouble() },
            projection = currentProjection,
            displayRotationDegrees = rotation,
            declinationDegrees = declination
        )
    }

    private fun refreshScan(force: Boolean = false) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (!force && now - lastRefreshAtNanos < SCAN_INTERVAL_NANOS) return
        lastRefreshAtNanos = now
        val location = currentLocation(now)
        val pose = currentPose(location, now)
        val dataReady = hasLoadedRestaurants || googlePlaces.isNotEmpty() ||
            placesGate.state == NearbySearchRequestGate.State.READY
        visibleRestaurants = if (scanActive && lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            dataReady && markersContainer.width > 0 && markersContainer.height > 0
        ) RestaurantCameraScan.visibleRestaurants(restaurants, location, pose) else emptyList()
        updateAttribution()
        updateSetupHint()
        updateHint(location, pose, now)
        renderMarkers()
    }

    private fun renderMarkers() {
        val visibleIds = visibleRestaurants.mapTo(mutableSetOf()) { it.restaurant.id }
        val iterator = markerViews.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.key !in visibleIds) {
                markersContainer.removeView(entry.value)
                markerRows.remove(entry.key)
                iterator.remove()
            }
        }
        for (match in visibleRestaurants) {
            val marker = markerViews.getOrPut(match.restaurant.id) {
                createMarker(match.restaurant.id).also {
                    markersContainer.addView(it, FrameLayout.LayoutParams(
                        minOf(dp(200), markersContainer.width), FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.LEFT
                    ))
                }
            }
            val icon = marker.findViewById<ImageView>(R.id.restaurantScanMarkerIcon)
            val iconResource = if (match.restaurant.cuisine.contains("caf", ignoreCase = true))
                R.drawable.ic_cafe_marker else R.drawable.ic_restaurant_marker
            if (icon.tag != iconResource) {
                icon.tag = iconResource
                icon.setImageResource(iconResource)
            }
            val name = marker.findViewById<TextView>(R.id.restaurantScanMarkerName)
            val distance = marker.findViewById<TextView>(R.id.restaurantScanMarkerDistance)
            if (name.text.toString() != match.restaurant.name) name.text = match.restaurant.name
            val distanceLabel = getString(R.string.restaurant_distance_meters, match.distanceMeters.roundToInt())
            if (distance.text.toString() != distanceLabel) distance.text = distanceLabel
            val description = getString(R.string.restaurant_scan_marker,
                match.restaurant.name, match.distanceMeters.roundToInt())
            if (marker.contentDescription != description) marker.contentDescription = description
        }
        positionMarkers()
    }

    private fun positionMarkers() {
        if (positioningMarkers) return
        positioningMarkers = true
        try {
            val gap = dp(12)
            val back = findViewById<View>(R.id.discoveryBack)
            val top = if (back.isLaidOut) maxOf(gap, back.bottom - markersContainer.top + gap) else gap
            val bottom = if (scanControls.isLaidOut && scanControls.height > 0) {
                minOf(markersContainer.height - gap, scanControls.top - markersContainer.top - gap)
            } else markersContainer.height - gap
            val bounds = MarkerLayoutBounds(gap, top, markersContainer.width - gap, bottom)
            val width = minOf(dp(200), bounds.right - bounds.left)

            // Measure every card at its final width before placing any of them. Long names
            // and larger text must reserve their full height on the very first frame.
            val items = if (width > 0 && bounds.bottom > bounds.top) {
                visibleRestaurants.sortedWith(
                    compareBy<CameraRestaurantMatch> { it.distanceMeters }.thenBy { it.restaurant.id }
                ).mapNotNull { match ->
                    val marker = markerViews[match.restaurant.id] ?: return@mapNotNull null
                    marker.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                    MarkerLayoutItem(match.restaurant.id, match.screenX * markersContainer.width,
                        width, marker.measuredHeight)
                }
            } else emptyList()
            val placements = RestaurantMarkerLayout.place(items, bounds,
                (RestaurantCameraScan.MARKER_SCREEN_Y * markersContainer.height).roundToInt(), gap, markerRows)
            markerRows.clear()
            for ((id, marker) in markerViews) {
                val placement = placements[id]
                marker.visibility = if (placement == null) View.INVISIBLE else View.VISIBLE
                if (placement == null) continue
                markerRows[id] = placement.row
                val params = marker.layoutParams as FrameLayout.LayoutParams
                if (params.width != width || params.leftMargin != placement.left || params.topMargin != placement.top) {
                    params.width = width
                    params.leftMargin = placement.left
                    params.topMargin = placement.top
                    marker.layoutParams = params
                }
            }
            val overflow = markersContainer.width > 0 && markersContainer.height > 0 &&
                placements.size < visibleRestaurants.size
            if (overflow) {
                val text = getString(R.string.restaurant_scan_card_overflow, placements.size, visibleRestaurants.size)
                if (overflowHintText.text.toString() != text) overflowHintText.text = text
            }
            overflowHintText.visibility = if (overflow) View.VISIBLE else View.GONE
        } finally {
            positioningMarkers = false
        }
    }

    private fun createMarker(restaurantId: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(12), dp(10), dp(12), dp(10))
        setBackgroundResource(R.drawable.restaurant_scan_marker_background)
        elevation = dp(8).toFloat()
        isFocusable = true
        isClickable = true
        addView(FrameLayout(this@ARDiscoveryActivity).apply {
            setBackgroundResource(R.drawable.profile_icon_background)
            setPadding(dp(6), dp(6), dp(6), dp(6))
            addView(ImageView(this@ARDiscoveryActivity).apply {
                id = R.id.restaurantScanMarkerIcon
                setImageResource(R.drawable.ic_restaurant_marker)
                imageTintList = ColorStateList.valueOf(getColor(R.color.ar_marker_tint))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }, FrameLayout.LayoutParams(dp(24), dp(24), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(36), dp(36)).apply { marginEnd = dp(10) })
        addView(LinearLayout(this@ARDiscoveryActivity).apply {
            orientation = LinearLayout.VERTICAL
            addView(TextView(this@ARDiscoveryActivity).apply {
                id = R.id.restaurantScanMarkerName
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(getColor(R.color.ar_text))
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
            addView(TextView(this@ARDiscoveryActivity).apply {
                id = R.id.restaurantScanMarkerDistance
                textSize = 12f
                setTextColor(getColor(R.color.ar_text_secondary))
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            })
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        setOnClickListener {
            animate().scaleX(0.92f).scaleY(0.92f).setDuration(80).withEndAction {
                animate().scaleX(1f).scaleY(1f).setDuration(80).start()
                refreshScan(force = true)
                visibleRestaurants.firstOrNull { it.restaurant.id == restaurantId }?.let { match ->
                    val source = nearbyRestaurants.firstOrNull { it.restaurant.id == restaurantId } ?: return@let
                    startActivity(RestaurantProfileActivity.intent(this@ARDiscoveryActivity,
                        source.restaurant, match.distanceMeters, source.placeId, source.attributions))
                }
            }.start()
        }
        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                positionMarkers()
            }
        }
    }

    private fun updateAttribution() {
        val visibleGoogle = nearbyRestaurants.filter { item ->
            item.placeId != null && visibleRestaurants.any { it.restaurant.id == item.restaurant.id }
        }
        if (visibleGoogle.isEmpty()) {
            attributionText.visibility = View.GONE
            providerAttributionText.visibility = View.GONE
            return
        }
        val providers = visibleGoogle.flatMap { it.attributions }.filter { it.provider.isNotBlank() }
            .distinctBy { it.provider to it.providerUri }
        val signature = providers.joinToString("|") { "${it.provider}:${it.providerUri}" }
        if (attributionSignature != signature || providerAttributionText.text.isEmpty()) {
            val attribution = SpannableStringBuilder()
            fun appendLink(label: String, uri: String?) {
                if (attribution.isNotEmpty()) attribution.append(" · ")
                val start = attribution.length
                attribution.append(label)
                val link = uri?.let(Uri::parse)?.takeIf {
                    it.scheme in listOf("https", "http") && !it.host.isNullOrBlank()
                }
                if (link != null) attribution.setSpan(URLSpan(link.toString()), start, attribution.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            providers.forEach { appendLink(it.provider, it.providerUri) }
            providerAttributionText.text = attribution
            attributionSignature = signature
        }
        attributionText.visibility = View.VISIBLE
        providerAttributionText.visibility = if (providers.isNotEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateSetupHint() {
        setupHintText.setText(if (configuredKey.isBlank()) R.string.restaurant_scan_setup_key
            else R.string.restaurant_scan_key_rejected)
        setupHintText.visibility = if (configuredKey.isBlank() || placesFailure?.needsSettings == true)
            View.VISIBLE else View.GONE
    }

    private fun updateHint(location: DiscoveryLocation?, pose: CameraScanPose?, nowNanos: Long) {
        val hasNearby = location != null && restaurants.any {
            val distance = RestaurantDiscovery.distanceMeters(location, DiscoveryLocation(it.latitude, it.longitude))
            distance != null && distance <= RestaurantCameraScan.RADIUS_METERS
        }
        val message = when {
            !hasCameraPermission() && !hasPreciseLocationPermission() -> R.string.restaurant_scan_permissions
            !hasCameraPermission() -> R.string.restaurant_scan_camera_required
            !hasPreciseLocationPermission() -> R.string.restaurant_scan_location_required
            cameraError -> R.string.restaurant_scan_camera_unavailable
            !locationManager.isLocationEnabled || locationFailed -> R.string.restaurant_scan_location_unavailable
            location == null -> {
                val fix = lastFix
                when {
                    fix != null && nowNanos - fix.elapsedRealtimeNanos !in 0..MAX_SCAN_LOCATION_AGE_NANOS ->
                        R.string.restaurant_scan_location_unavailable
                    fix != null && (!fix.hasAccuracy() || !fix.accuracy.isFinite() || fix.accuracy > MAX_SCAN_ACCURACY_METERS) ->
                        R.string.restaurant_scan_location_inaccurate
                    locationWaitStartedAtNanos > 0 && nowNanos - locationWaitStartedAtNanos >= MAX_SCAN_LOCATION_AGE_NANOS ->
                        R.string.restaurant_scan_location_unavailable
                    else -> R.string.restaurant_scan_locating
                }
            }
            calibrationUnavailable && captureFreshness.isFresh(nowNanos) -> R.string.restaurant_scan_calibration_unavailable
            !captureFreshness.isFresh(nowNanos) -> R.string.restaurant_scan_camera_starting
            rotationSensor == null || !orientationRegistered -> R.string.restaurant_scan_orientation_unavailable
            !hasOrientation || !cameraScanOrientationUsable(orientationTimestampNanos, orientationAccuracy, nowNanos) ->
                R.string.restaurant_scan_orientation_uncertain
            pose == null -> R.string.restaurant_scan_camera_starting
            placesFailure == PlacesFailure.QUOTA -> R.string.restaurant_scan_quota
            placesFailure == PlacesFailure.NETWORK -> R.string.restaurant_scan_network
            placesGate.state == NearbySearchRequestGate.State.LOADING -> R.string.restaurant_scan_search_loading
            restaurantLoadFailed -> R.string.restaurant_load_error
            !hasLoadedRestaurants && googlePlaces.isEmpty() -> R.string.restaurant_loading_hint
            !hasNearby -> if (configuredKey.isBlank() || placesFailure?.needsSettings == true)
                R.string.restaurant_scan_no_local_nearby else R.string.restaurant_scan_no_nearby
            else -> R.string.restaurant_scan_searching
        }
        val hint = getString(message)
        if (hintText.text.toString() != hint) hintText.text = hint
        hintText.isClickable = !hasScanPermissions() || !locationManager.isLocationEnabled ||
            cameraError || location == null || restaurantLoadFailed
        val placesStatus = message in listOf(R.string.restaurant_scan_quota,
            R.string.restaurant_scan_network, R.string.restaurant_scan_search_loading)
        hintText.visibility = if (visibleRestaurants.isEmpty() || placesStatus) View.VISIBLE else View.GONE
    }

    override fun onResume() {
        super.onResume()
        val preferredMode = if (UserPreferences(this).isDarkMode()) AppCompatDelegate.MODE_NIGHT_YES
            else AppCompatDelegate.MODE_NIGHT_NO
        if (delegate.localNightMode != preferredMode) {
            pauseScanning()
            delegate.localNightMode = preferredMode
            return
        }
        reloadConfiguredKey()
        scanActive = true
        locationFailed = false
        startScanServices()
        scanHandler.removeCallbacks(scanWatchdog)
        scanHandler.removeCallbacks(placesPoll)
        scanHandler.post(scanWatchdog)
        scanHandler.post(placesPoll)
    }

    private fun pauseScanning() {
        scanActive = false
        scanHandler.removeCallbacks(scanWatchdog)
        scanHandler.removeCallbacks(placesPoll)
        cancelPlacesRequest()
        cameraController?.stop()
        previewRunning = false
        stopLocationUpdates()
        stopOrientationUpdates()
        captureFreshness.reset()
        projection = null
        calibrationUnavailable = false
        refreshScan(force = true)
    }

    override fun onPause() {
        pauseScanning()
        super.onPause()
    }

    override fun onStop() {
        pauseScanning()
        super.onStop()
    }

    override fun onDestroy() {
        cameraController?.close()
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("scan_permissions_requested", hasRequestedScanPermissions)
        outState.putBoolean("returning_from_settings", returningFromSettings)
        outState.putBoolean("places_configuration_blocked", placesGate.state == NearbySearchRequestGate.State.BLOCKED)
        outState.putString("places_key_fingerprint", keyFingerprint(configuredKey))
        super.onSaveInstanceState(outState)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == SCAN_PERMISSION_REQUEST) {
            permissionRequestPending = false
            startScanServices()
            refreshScan(force = true)
        }
    }

    private fun dp(value: Int) = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), resources.displayMetrics
    ).toInt()

    private fun keyFingerprint(key: String): String = if (key.isEmpty()) "" else MessageDigest
        .getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private const val SCAN_PERMISSION_REQUEST = 201
        private const val SCAN_INTERVAL_MILLIS = 100L
        private const val SCAN_INTERVAL_NANOS = 100_000_000L
        private const val PLACES_POLL_INTERVAL_MILLIS = 5_000L
    }
}

internal const val MAX_SCAN_LOCATION_AGE_NANOS = 30_000_000_000L
internal const val MAX_SCAN_ACCURACY_METERS = 50f
internal const val MAX_SCAN_ORIENTATION_AGE_NANOS = 2_000_000_000L

internal fun usableCameraScanLocation(fix: Location?, nowNanos: Long): DiscoveryLocation? {
    if (fix == null || !fix.hasAccuracy() || !fix.accuracy.isFinite() ||
        fix.accuracy !in 0f..MAX_SCAN_ACCURACY_METERS || fix.elapsedRealtimeNanos <= 0L ||
        nowNanos - fix.elapsedRealtimeNanos !in 0..MAX_SCAN_LOCATION_AGE_NANOS) return null
    return DiscoveryLocation(fix.latitude, fix.longitude).takeIf { it.isValid }
}

internal fun cameraScanOrientationUsable(timestampNanos: Long, accuracy: Int, nowNanos: Long) =
    accuracy > SensorManager.SENSOR_STATUS_UNRELIABLE && timestampNanos > 0L &&
        nowNanos - timestampNanos in 0..MAX_SCAN_ORIENTATION_AGE_NANOS

/** Render callbacks can repeat a camera capture; they must not keep a frozen image fresh. */
internal class CameraCaptureFreshness {
    private var lastCaptureTimestamp = 0L
    private var observedAtNanos = 0L

    fun observe(captureTimestamp: Long, nowNanos: Long) {
        if (captureTimestamp > 0L && captureTimestamp != lastCaptureTimestamp) {
            lastCaptureTimestamp = captureTimestamp
            observedAtNanos = nowNanos
        }
    }

    fun isFresh(nowNanos: Long) = lastCaptureTimestamp > 0L &&
        nowNanos - observedAtNanos in 0..1_000_000_000L

    fun reset() {
        lastCaptureTimestamp = 0L
        observedAtNanos = 0L
    }
}
