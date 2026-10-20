package org.dinefinder.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import org.dinefinder.app.core.Restaurant
import org.dinefinder.app.core.distanceMeters
import org.dinefinder.app.ui.*
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { DineFinderTheme { DineFinderRoot() } }
    }
}

@Composable
private fun DineFinderRoot(model: AppViewModel = viewModel()) {
    val state = model.state
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var settings by rememberSaveable { mutableStateOf(false) }
    var help by rememberSaveable { mutableStateOf(false) }
    var rename by rememberSaveable { mutableStateOf(false) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        fun has(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        model.scanWithPermissions(has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION), has(Manifest.permission.CAMERA))
    }
    DisposableEffect(owner, model) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> model.setForeground(true)
                Lifecycle.Event.ON_STOP -> model.setForeground(false)
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        model.setForeground(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose { owner.lifecycle.removeObserver(observer); model.setForeground(false) }
    }
    LaunchedEffect(state.signedIn) {
        if (!state.signedIn) { settings = false; help = false; rename = false }
    }
    BackHandler(enabled = state.signedIn && (state.scan || state.profile)) {
        if (state.scan) model.closeScan() else model.showProfile(false)
    }
    if (!state.signedIn) {
        WelcomeScreen(state.authBusy, state.message, model::authenticate, model::enterGuest)
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            bottomBar = {
                if (!state.scan) NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = !state.profile, onClick = { model.showProfile(false) },
                        icon = { Icon(Icons.Default.Home, contentDescription = null) }, label = { Text("Home") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                    )
                    NavigationBarItem(
                        selected = state.profile, onClick = { model.showProfile(true) },
                        icon = { Icon(Icons.Default.Person, contentDescription = null) }, label = { Text("Profile") },
                        colors = NavigationBarItemDefaults.colors(indicatorColor = Color.Transparent),
                    )
                }
            },
        ) { insets ->
            Box(Modifier.fillMaxSize().padding(insets)) {
                when {
                    state.scan -> ScanScreen(
                        position = state.position, heading = state.heading,
                        restaurants = state.restaurants.filter { restaurant -> state.position?.let { distanceMeters(it, restaurant.location) <= 1000.0 } == true },
                        route = state.route, destination = state.destination, isDemo = state.demo,
                        busy = state.loading || state.routing, error = state.error,
                        locationError = state.sensors.locationError, headingError = state.sensors.headingError,
                        cameraAllowed = state.cameraAllowed, onSelect = { model.select(it) }, onClose = model::closeScan,
                        onRetry = model::retry, onRecalculate = model::recalculate,
                        onHeadingChange = model::setHeading, onWalkChange = model::setWalk, demoProgress = state.demoProgress,
                    )
                    state.profile -> ProfileScreen(state.displayName, state.account?.email, state.account == null,
                        onSettings = { settings = true }, onHelp = { help = true }, onLogout = model::logout)
                    else -> HomeScreen(state.displayName, state.demo, model::requestScan)
                }
                if (!state.scan && state.error != null) AlertDialog(
                    onDismissRequest = model::dismissError, title = { Text("DineFinder") },
                    text = { Text(state.error) }, confirmButton = { TextButton(onClick = model::dismissError) { Text("OK") } },
                )
            }
        }
    }
    if (state.consent) AlertDialog(
        onDismissRequest = model::dismissConsent,
        title = { Text("Find restaurants around you") },
        text = { Text("Allow foreground location and camera access to find restaurants within 1 km and show their directions. Camera frames stay on your device.") },
        dismissButton = { TextButton(onClick = model::dismissConsent) { Text("Later") } },
        confirmButton = { TextButton(onClick = {
            model.dismissConsent()
            permissions.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.CAMERA))
        }) { Text("Continue") } },
    )
    state.selected?.let { restaurant ->
        RestaurantDialog(restaurant, state.position?.let { distanceMeters(it, restaurant.location) },
            state.demo, state.routing, { model.select(null) }, { model.navigate(restaurant) })
    }
    if (settings && state.signedIn) AlertDialog(
        onDismissRequest = { settings = false }, title = { Text("Settings") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Search radius: 1 km around your GPS location")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Use demo mode", Modifier.weight(1f))
                    Switch(checked = state.demo, onCheckedChange = { settings = false; model.setDemo(it) })
                }
                if (state.account == null) TextButton(onClick = { settings = false; rename = true }) { Text("Change guest name") }
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
                }) { Text("Device permissions") }
                Text("Walking routes: FOSSGIS · © OpenStreetMap contributors · ODbL. No routing API key is required.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = { settings = false }) { Text("Done") } },
    )
    if (help && state.signedIn) AlertDialog(
        onDismissRequest = { help = false }, title = { Text("How DineFinder works") },
        text = { Text(
            "1. Tap Scan for Restaurant and allow location and camera access.\n\n" +
            "2. DineFinder searches Supabase for restaurant records within 1 km of your GPS location. Turn your phone to see markers, or open the list.\n\n" +
            "3. Tap a restaurant for its profile, then GUIDE ME for pedestrian directions and a camera arrow.\n\n" +
            "Camera guidance uses approximate GPS and compass bearings. It does not recognize restaurant signs or detect obstacles or safe crossings.\n\n" +
            "Camera frames remain on the device. Nearby coordinates go to Supabase; route endpoints go to FOSSGIS OpenStreetMap.\n\n" +
            "Demo mode uses fictional places and simulated routes. Guest names and account sessions last until this app process ends.",
            modifier = Modifier.verticalScroll(rememberScrollState()),
        ) },
        confirmButton = { TextButton(onClick = { help = false }) { Text("Got it") } },
    )
    if (rename && state.signedIn) {
        var name by remember { mutableStateOf(state.displayName) }
        AlertDialog(onDismissRequest = { rename = false }, title = { Text("Your guest name") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it.take(32) }, singleLine = true, label = { Text("Name") }) },
            dismissButton = { TextButton(onClick = { rename = false }) { Text("Cancel") } },
            confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { model.renameGuest(name); rename = false }) { Text("Save") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RestaurantDialog(restaurant: Restaurant, distance: Double?, demo: Boolean, busy: Boolean, onClose: () -> Unit, onNavigate: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, containerColor = Color.White) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (demo) Text("FICTIONAL DEMO RESTAURANT", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
            Text("${restaurant.emoji} ${restaurant.name}", style = MaterialTheme.typography.headlineSmall)
            val rating = if (restaurant.rating > 0) String.format(Locale.getDefault(), "★ %.1f", restaurant.rating) else "Not rated"
            Text("Rating: $rating")
            Text("Cuisine type: ${restaurant.cuisine}")
            Text("Halal status: ${restaurant.halalStatus}")
            Text("Price range: ${if (demo) restaurant.priceLabel else "Not provided"}")
            Text("Operating hours: ${restaurant.openingHours}")
            Text("Address: ${restaurant.address.ifBlank { "Not provided" }}")
            distance?.let { Text("${it.toInt()} m from your location", color = MaterialTheme.colorScheme.primary) }
            if (restaurant.description.isNotBlank()) Text(restaurant.description)
            if (restaurant.tags.isNotEmpty()) Text(restaurant.tags.joinToString(" · "))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(enabled = !busy, onClick = onNavigate, modifier = Modifier.weight(1f)) { Text("GUIDE ME") }
                OutlinedButton(onClick = onClose, modifier = Modifier.weight(1f)) { Text("CANCEL") }
            }
        }
    }
}
