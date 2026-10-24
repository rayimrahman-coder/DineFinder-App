package com.example.dinefinder

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import com.example.dinefinder.core.*
import com.example.dinefinder.core.Restaurant as CoreRestaurant
import com.example.dinefinder.data.*
import com.example.dinefinder.device.DeviceSensors
import com.example.dinefinder.device.SensorSnapshot

internal data class AppState(
    val guest: Boolean = false,
    val account: Account? = null,
    val guestName: String = "Guest",
    val profile: Boolean = false,
    val demo: Boolean = false,
    val scan: Boolean = false,
    val consent: Boolean = false,
    val cameraAllowed: Boolean = false,
    val authBusy: Boolean = false,
    val loading: Boolean = false,
    val routing: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val sensors: SensorSnapshot = SensorSnapshot(),
    val restaurants: List<CoreRestaurant> = emptyList(),
    val lastSearchCenter: GeoPoint? = null,
    val lastSearchCount: Int? = null,
    val selected: CoreRestaurant? = null,
    val destination: CoreRestaurant? = null,
    val route: RoutePlan? = null,
    val demoHeading: Double = 35.0,
    val demoProgress: Double = 0.0,
) {
    val signedIn get() = guest || account != null
    val displayName get() = account?.displayName ?: guestName
    val position: GeoPoint? get() = if (demo) {
        route?.let { DemoData.pointOnRoute(it, demoProgress) } ?: DemoData.origin
    } else sensors.position
    val heading: Double? get() = if (demo) demoHeading else sensors.heading
}

/** Owns an in-memory account and foreground scan. No passwords or tokens are persisted. */
class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val config = SupabaseConfig(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_PUBLISHABLE_KEY)
    private val api = DineFinderApi(config)
    internal var state by mutableStateOf(AppState(demo = false))
        private set
    private var foreground = false
    private var queryJob: Job? = null
    private var routeJob: Job? = null
    private var authJob: Job? = null
    private var lastQuery: GeoPoint? = null
    private var lastQueryAt = 0L
    private var generation = 0
    private val device = DeviceSensors(application, ::sensorUpdate)

    internal fun enterGuest() {
        if (state.authBusy) return
        state = state.copy(guest = true, message = null, profile = false)
    }

    internal fun authenticate(email: String, password: String, create: Boolean) {
        if (state.authBusy) return
        if (!config.isConfigured) {
            state = state.copy(message = "Account sign-in needs a Supabase connection. You can continue as a guest.")
            return
        }
        state = state.copy(authBusy = true, message = null)
        authJob = viewModelScope.launch {
            try {
                val account = if (create) api.signUp(email.trim(), password) else api.signIn(email.trim(), password)
                state = if (account == null) state.copy(
                    message = "Check your email to confirm your account, then sign in.", authBusy = false,
                ) else state.copy(account = account, guest = false, profile = false, authBusy = false, message = null)
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                state = state.copy(authBusy = false, message = readable(error, "Couldn't connect to your account. Please try again."))
            }
        }
    }

    internal fun logout() {
        val account = state.account
        generation++
        queryJob?.cancel(); routeJob?.cancel(); authJob?.cancel()
        lastQuery = null
        state = AppState(demo = false)
        device.stop()
        if (account != null) viewModelScope.launch {
            // Clearing the local session must work even when the network is offline.
            try { api.signOut(account) } catch (cancelled: CancellationException) { throw cancelled
            } catch (_: Exception) { /* The local, non-persisted session is already gone. */ }
        }
    }

    internal fun showProfile(profile: Boolean) { state = state.copy(profile = profile) }
    internal fun renameGuest(name: String) {
        val trimmed = name.trim().take(32)
        if (state.account == null && trimmed.isNotEmpty()) state = state.copy(guestName = trimmed)
    }
    internal fun setDemo(demo: Boolean) {
        if (!demo && !config.isConfigured) {
            state = state.copy(error = "Live discovery needs your Supabase project URL and public publishable key.")
            return
        }
        closeScan()
        state = state.copy(demo = demo)
    }
    internal fun dismissError() { state = state.copy(error = null) }

    internal fun requestScan() {
        if (!state.signedIn) return
        if (!config.isConfigured) {
            state = state.copy(error = "The Supabase connection is not configured. Restaurant data is available only from Supabase.")
            return
        }
        if (state.demo) {
            state = state.copy(scan = true, profile = false, error = null,
                restaurants = DemoData.nearby(DemoData.origin))
        } else {
            val application = getApplication<Application>()
            fun granted(permission: String) = ContextCompat.checkSelfPermission(application, permission) == PackageManager.PERMISSION_GRANTED
            val location = granted(Manifest.permission.ACCESS_FINE_LOCATION) || granted(Manifest.permission.ACCESS_COARSE_LOCATION)
            val camera = granted(Manifest.permission.CAMERA)
            if (location && camera) scanWithPermissions(location = true, camera = true)
            else state = state.copy(consent = true)
        }
    }
    internal fun dismissConsent() { state = state.copy(consent = false) }

    internal fun scanWithPermissions(location: Boolean, camera: Boolean) {
        if (!state.signedIn || state.demo) return
        state = state.copy(consent = false, scan = true, profile = false,
            cameraAllowed = camera, error = if (!location) "Allow location access and enable GPS to find restaurants around you." else null)
        if (location && foreground) {
            device.start()
            if (freshFix()) reload()
        }
    }
    internal fun closeScan() {
        generation++
        queryJob?.cancel(); routeJob?.cancel()
        lastQuery = null
        state = state.copy(scan = false, profile = false, consent = false,
            loading = false, routing = false, restaurants = emptyList(), selected = null,
            route = null, destination = null, demoProgress = 0.0, error = null,
            sensors = SensorSnapshot(), lastSearchCenter = null, lastSearchCount = null)
        device.stop()
    }

    internal fun setForeground(value: Boolean) {
        foreground = value
        if (!value) {
            device.stop()
            state = state.copy(sensors = state.sensors.copy(heading = null))
            return
        }
        val application = getApplication<Application>()
        val locate = ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(application, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val camera = ContextCompat.checkSelfPermission(application, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        state = state.copy(cameraAllowed = camera)
        if (state.scan && !state.demo && locate) device.start()
    }

    private fun freshFix(): Boolean = state.sensors.position != null &&
        state.sensors.lastFixElapsedMs?.let { SystemClock.elapsedRealtime() - it in 0..45_000 } == true

    private fun sensorUpdate(snapshot: SensorSnapshot) {
        if (!state.scan || state.demo || !foreground) return
        state = state.copy(sensors = snapshot)
        val position = snapshot.position ?: return
        if (!freshFix() || state.loading) return
        val old = lastQuery
        val now = SystemClock.elapsedRealtime()
        if (old == null || (distanceMeters(old, position) >= 60 && now - lastQueryAt >= 15_000)) reload()
    }

    internal fun retry() {
        if (state.demo) {
            state = state.copy(restaurants = DemoData.nearby(state.position ?: DemoData.origin), error = null)
        } else if (freshFix()) reload() else requestScan()
    }

    private fun reload() {
        if (!state.scan || !freshFix()) return
        val point = state.position ?: return
        val current = generation
        queryJob?.cancel()
        lastQuery = point
        lastQueryAt = SystemClock.elapsedRealtime()
        state = state.copy(loading = true, error = null, lastSearchCenter = point, lastSearchCount = null)
        queryJob = viewModelScope.launch {
            try {
                val places = api.nearby(point)
                if (generation == current && state.scan && !state.demo) {
                    state = state.copy(restaurants = places.filter { distanceMeters(point, it.location) <= 1000.0 }, loading = false, lastSearchCount = places.size)
                }
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (generation == current) state = state.copy(loading = false,
                    error = readable(error, "Couldn't load nearby restaurants. Check your connection and retry."))
            }
        }
    }

    internal fun select(restaurant: CoreRestaurant?) { state = state.copy(selected = restaurant) }
    internal fun navigate(restaurant: CoreRestaurant) {
        if (state.routing) return
        val origin = state.position ?: return
        if (!state.demo && !freshFix()) {
            device.refreshLocation()
            state = state.copy(error = "Waiting for a fresh GPS location. Try Navigate again after the GPS updates.")
            return
        }
        val current = generation
        val demo = state.demo
        state = state.copy(selected = null, routing = true, error = null)
        routeJob?.cancel()
        routeJob = viewModelScope.launch {
            try {
                val route = if (demo) DemoData.route(origin, restaurant.location) else api.walkingRoute(origin, restaurant.location)
                if (generation == current && state.scan) state = state.copy(
                    route = route, destination = restaurant, demoProgress = 0.0, routing = false,
                    demoHeading = if (demo) bearingDegrees(route.points[0], route.points[1]) else state.demoHeading,
                )
            } catch (cancelled: CancellationException) { throw cancelled
            } catch (error: Exception) {
                if (generation == current) state = state.copy(routing = false,
                    error = readable(error, "Walking directions are unavailable. Please try again."))
            }
        }
    }
    internal fun recalculate() { state.destination?.let(::navigate) }
    internal fun setHeading(value: Double) { state = state.copy(demoHeading = normalizeAngle(value)) }
    internal fun setWalk(value: Double) { state = state.copy(demoProgress = value.coerceIn(0.0, 1.0)) }

    override fun onCleared() { device.close(); super.onCleared() }
    private fun readable(error: Exception, fallback: String): String = if (error is ApiException) error.message ?: fallback else fallback
}
