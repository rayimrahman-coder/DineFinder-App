package com.example.dinefinder

import android.Manifest
import android.content.pm.PackageManager
import android.content.Intent
import android.content.Context
import android.net.Uri
import android.provider.Settings
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.dinefinder.data.SupabaseConfig
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dinefinder.core.Restaurant
import com.example.dinefinder.core.distanceMeters
import com.example.dinefinder.ui.ScanScreen
import com.example.dinefinder.ui.CameraLocationPermissionDialog
import java.util.Locale

/** Live discovery/profile data comes exclusively from DineFinderApi's Supabase RPC. */
@Composable
internal fun SupabaseDiscoveryFlow(model: AppViewModel) {
    val state = model.state
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var showSearchDetails by remember { mutableStateOf(false) }
    var cameraReady by remember { mutableStateOf(false) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraActive by remember { mutableStateOf(true) }
    var checkTime by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(showSearchDetails, state.scan) {
        while (showSearchDetails && state.scan) {
            checkTime = SystemClock.elapsedRealtime()
            delay(1000)
        }
    }
    val clipboard = LocalClipboardManager.current
    val activity = LocalActivity.current
    val permissionHistory = remember(context) { context.getSharedPreferences("dinefinder_permissions", Context.MODE_PRIVATE) }
    var showPermissionHelp by remember { mutableStateOf(false) }
    var permissionBlocked by remember { mutableStateOf(false) }
    fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    fun hasLocation() = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
    fun blocked(permission: String) = activity != null &&
        permissionHistory.getBoolean("asked:$permission", false) &&
        !ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
    fun hasBlockedPermission() =
        (!granted(Manifest.permission.CAMERA) && blocked(Manifest.permission.CAMERA)) ||
        (!hasLocation() && blocked(Manifest.permission.ACCESS_FINE_LOCATION) && blocked(Manifest.permission.ACCESS_COARSE_LOCATION))
    val settings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // Settings has no permission-result payload; re-read actual grants on return.
        model.scanWithPermissions(hasLocation(), granted(Manifest.permission.CAMERA))
    }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        // A cancelled system sheet returns an empty map, not a permanent denial.
        if (result.isNotEmpty()) permissionHistory.edit().apply {
            result.keys.forEach { putBoolean("asked:$it", true) }
        }.apply()
        val location = hasLocation()
        val camera = granted(Manifest.permission.CAMERA)
        model.scanWithPermissions(location, camera)
        if (result.isNotEmpty() && (!location || !camera)) {
            permissionBlocked = hasBlockedPermission()
            showPermissionHelp = true
        }
    }
    fun requestPermissions() {
        model.dismissConsent()
        val location = hasLocation()
        val camera = granted(Manifest.permission.CAMERA)
        if (location && camera) model.scanWithPermissions(location = true, camera = true)
        else if (hasBlockedPermission()) {
            permissionBlocked = true
            showPermissionHelp = true
        } else permissions.launch(buildList {
            if (!location) {
                add(Manifest.permission.ACCESS_COARSE_LOCATION)
                add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
            if (!camera) add(Manifest.permission.CAMERA)
        }.toTypedArray())
    }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> {
                    model.setForeground(true)
                    if (model.state.consent && hasLocation() && granted(Manifest.permission.CAMERA)) {
                        model.scanWithPermissions(location = true, camera = true)
                    }
                }
                Lifecycle.Event.ON_STOP -> model.setForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        model.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.setForeground(false) }
    }
    BackHandler(enabled = state.scan) {
        if (state.selected != null) model.select(null) else model.closeScan()
    }
    if (state.scan) Surface(Modifier.fillMaxSize().background(Color.White).windowInsetsPadding(WindowInsets.safeDrawing), color = Color.White) {
        ScanScreen(
            position = state.position, heading = state.heading,
            restaurants = state.restaurants.filter { restaurant ->
                state.position?.let { distanceMeters(it, restaurant.location) <= 1000.0 } == true
            },
            route = state.route, destination = state.destination, isDemo = false,
            busy = state.loading || state.routing, error = state.error,
            locationError = state.sensors.locationError, headingError = state.sensors.headingError,
            cameraAllowed = state.cameraAllowed, onSelect = model::select, onClose = model::closeScan,
            onRetry = model::retry, onRecalculate = model::recalculate,
            onHeadingChange = model::setHeading, onWalkChange = model::setWalk,
            demoProgress = state.demoProgress,
            onShowSearchDetails = { showSearchDetails = true },
            searchCompleted = state.lastSearchCount != null,
            onCameraCheckChange = { ready, error, active ->
                cameraReady = ready; cameraError = error; cameraActive = active
            },
        )
    }
    if (state.scan && showSearchDetails) {
        fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        val precise = allowed(Manifest.permission.ACCESS_FINE_LOCATION)
        val locate = precise || allowed(Manifest.permission.ACCESS_COARSE_LOCATION)
        val locationEnabled = runCatching { context.getSystemService(LocationManager::class.java)?.isLocationEnabled }.getOrNull()
        val networkValidated = runCatching {
            val manager = context.getSystemService(ConnectivityManager::class.java)
            manager?.getNetworkCapabilities(manager.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }.getOrNull()
        val nowForChecks = maxOf(checkTime, SystemClock.elapsedRealtime())
        val rawDetails = searchDetailsText(state, precise, nowForChecks)
        val checks = scanChecklist(state, locate, precise, locationEnabled, networkValidated,
            SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY).isConfigured,
            cameraReady, cameraError, cameraActive, nowForChecks)
        val details = checks.joinToString("\n") { "${it.status}: ${it.title} — ${it.detail}" } + "\n\n" + rawDetails
        AlertDialog(
            onDismissRequest = { showSearchDetails = false },
            title = { Text("Scan checklist") },
            text = {
                Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val failures = checks.count { it.status == CheckStatus.FAIL }
                    val attention = checks.count { it.status == CheckStatus.CHECK }
                    Text("$failures failed · $attention need attention. Checks update automatically.")
                    checks.forEach { check ->
                        val color = when (check.status) {
                            CheckStatus.PASS -> MaterialTheme.colorScheme.primary
                            CheckStatus.FAIL -> MaterialTheme.colorScheme.error
                            CheckStatus.CHECK -> MaterialTheme.colorScheme.tertiary
                            CheckStatus.WAIT -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(when (check.status) {
                                CheckStatus.PASS -> Icons.Default.CheckCircle
                                CheckStatus.FAIL -> Icons.Default.Error
                                CheckStatus.CHECK -> Icons.Default.Warning
                                CheckStatus.WAIT -> Icons.Default.HourglassEmpty
                            }, contentDescription = check.status.name, tint = color)
                            Column(Modifier.weight(1f)) {
                                Text(check.title, style = MaterialTheme.typography.titleSmall)
                                Text("${check.status}: ${check.detail}", color = color, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    HorizontalDivider()
                    Text(rawDetails, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { clipboard.setText(AnnotatedString(details)) }) { Text("Copy checks") } },
            dismissButton = {
                Row {
                    TextButton(onClick = { showSearchDetails = false; model.retry() }) { Text("Retry") }
                    TextButton(onClick = { showSearchDetails = false }) { Text("Close") }
                }
            },
        )
    }
    if (state.consent) CameraLocationPermissionDialog(
        onDismiss = model::dismissConsent,
        onContinue = ::requestPermissions,
    )
    if (showPermissionHelp) AlertDialog(
        onDismissRequest = { showPermissionHelp = false },
        title = { Text(if (permissionBlocked) "Allow access in Settings" else "Permissions not granted") },
        text = { Text(if (permissionBlocked)
            "Camera or location access is blocked. Open app settings to allow access, then return to DineFinder."
            else "Location helps find nearby restaurants, and the camera enables AR guidance. You can try again or continue without granting access.") },
        dismissButton = { TextButton(onClick = { showPermissionHelp = false }) { Text("Not now") } },
        confirmButton = {
            TextButton(onClick = {
                showPermissionHelp = false
                if (permissionBlocked) settings.launch(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)))
                else requestPermissions()
            }) { Text(if (permissionBlocked) "Open settings" else "Try again") }
        },
    )
    if (!state.scan && state.error != null) AlertDialog(
        onDismissRequest = model::dismissError, title = { Text("DineFinder") },
        text = { Text(state.error) },
        confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } },
    )
    state.selected?.let { restaurant ->
        SupabaseRestaurantProfile(restaurant, state.position?.let { distanceMeters(it, restaurant.location) },
            state.routing, { model.select(null) }, { model.navigate(restaurant) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SupabaseRestaurantProfile(restaurant: Restaurant, distance: Double?, busy: Boolean, onClose: () -> Unit, onNavigate: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("${restaurant.emoji} ${restaurant.name}", style = MaterialTheme.typography.headlineSmall)
            val rating = if (restaurant.rating > 0) String.format(Locale.getDefault(), "★ %.1f", restaurant.rating) else "Not rated"
            Text("Rating: $rating")
            Text("Cuisine type: ${restaurant.cuisine}")
            Text("Halal status: ${restaurant.halalStatus}")
            Text("Price range: Not provided")
            Text("Operating hours: ${restaurant.openingHours}")
            Text("Address: ${restaurant.address.ifBlank { "Not provided" }}")
            distance?.let { Text("${it.toInt()} m from your location", color = MaterialTheme.colorScheme.primary) }
            if (restaurant.description.isNotBlank()) Text(restaurant.description)
            if (restaurant.tags.isNotEmpty()) Text(restaurant.tags.joinToString(" · "))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy, onClick = onNavigate, modifier = Modifier.weight(1f)) { Text("Navigate") }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("CANCEL") }
            }
        }
    }
}

/** Includes only public connection metadata and location/search diagnostics, never keys. */
internal fun searchDetailsText(state: AppState, precise: Boolean, nowElapsedMs: Long? = null): String {
    fun point(point: com.example.dinefinder.core.GeoPoint?) = point?.let {
        String.format(Locale.US, "%.6f, %.6f", it.latitude, it.longitude)
    } ?: "Unavailable"
    val accuracy = state.sensors.accuracyMeters?.let { "±${it.toInt()} m" } ?: "Unavailable"
    val searchStatus = when {
        state.loading -> "Searching"
        state.lastSearchCount != null -> "Completed: ${state.lastSearchCount} results"
        state.error != null -> "Failed: ${state.error}"
        else -> "Not completed"
    }
    return buildString {
        appendLine("DineFinder ${BuildConfig.VERSION_NAME}")
        appendLine("Supabase project: ${java.net.URI(BuildConfig.SUPABASE_URL).host}")
        appendLine("Phone GPS: ${point(state.position)}")
        appendLine("Accuracy: $accuracy")
        val age = nowElapsedMs?.let { now -> state.sensors.lastFixElapsedMs?.let { now - it } }
        appendLine("GPS fix age: ${age?.takeIf { it >= 0 }?.let { "${it / 1000} s${if (it > 45_000) " (stale)" else ""}" } ?: "Unavailable"}")
        appendLine("Precise location: ${if (precise) "Allowed" else "Not allowed — enable Precise location in Android app settings"}")
        appendLine("Search centre: ${point(state.lastSearchCenter)}")
        appendLine("Radius: 1,000 m")
        appendLine("Search: $searchStatus")
        appendLine("Currently visible in list: ${state.restaurants.count { restaurant -> state.position?.let { distanceMeters(it, restaurant.location) <= 1000.0 } == true }}")
        if ((state.sensors.accuracyMeters ?: 0f) > 100f) appendLine("Location accuracy is low. Enable precise location and retry outdoors.")
        state.sensors.locationError?.let { appendLine(it) }
    }.trim()
}
