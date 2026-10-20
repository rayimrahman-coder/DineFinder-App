package org.dinefinder.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.dinefinder.app.core.*
import org.dinefinder.app.device.CameraPreview
import org.dinefinder.app.ar.ArNavigationPreview
import java.util.Locale
import kotlin.math.ceil

/** A GPS/compass overlay. It does not estimate a world-anchored AR pose. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    position: GeoPoint?,
    heading: Double?,
    restaurants: List<Restaurant>,
    route: RoutePlan?,
    destination: Restaurant?,
    isDemo: Boolean,
    busy: Boolean,
    error: String?,
    locationError: String?,
    headingError: String?,
    cameraAllowed: Boolean,
    onSelect: (Restaurant) -> Unit,
    onClose: () -> Unit,
    onRetry: () -> Unit,
    onRecalculate: () -> Unit,
    onHeadingChange: (Double) -> Unit,
    onWalkChange: (Double) -> Unit,
    demoProgress: Double,
) {
    var listVisible by rememberSaveable { mutableStateOf(false) }
    var directionsVisible by remember(route) { mutableStateOf(false) }
    var cameraError by remember(isDemo) { mutableStateOf<String?>(null) }
    var arStatus by remember(route) { mutableStateOf<String?>(null) }
    var arEnabled by remember(route) { mutableStateOf(!isDemo && route != null) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    val validHeading = heading?.takeIf { it.isFinite() }
    val progress = if (position != null && route != null) restaurantRouteProgress(position, route, destination?.location) else null
    val pathEndsNearby = position != null && route != null && progress?.hasArrived == false && nearestRouteProgress(position, route).hasArrived
    LaunchedEffect(progress?.hasArrived) { if (progress?.hasArrived == true) directionsVisible = false }
    val retry = { cameraAttempt++; cameraError = null; onRetry() }
    val problems = listOfNotNull(error, locationError, headingError, cameraError).distinct()

    Column(Modifier.fillMaxSize().background(DinePaper)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Home") }
            Column(Modifier.weight(1f)) {
                Text(if (route == null) "Discover restaurants" else "Walking navigation", style = MaterialTheme.typography.titleMedium)
                Text(if (route == null) "Within 1 km · tap a dining marker" else destination?.name.orEmpty(), color = DineMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = { listVisible = !listVisible }) { Text(if (listVisible) "Camera" else "List") }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (progress?.hasArrived == true) {
            ArrivalScreen(destination, isDemo, onClose)
            return@Column
        }
        if (listVisible) {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Text("${restaurants.size} restaurants within 1 km", style = MaterialTheme.typography.titleMedium) }
                if (isDemo) item { ScanNotice("Demo · fictional restaurants", Icons.Default.Science) }
                if (problems.isNotEmpty()) item { Text(problems.joinToString("\n"), color = DineRed) }
                if (restaurants.isEmpty()) item {
                    Text(if (position == null) "Waiting for a fresh GPS fix" else if (busy) "Searching…" else "No restaurants recorded within 1 km")
                }
                items(restaurants, key = { it.id }) { restaurant ->
                    ScanRestaurantCard(restaurant, position?.let { distanceMeters(it, restaurant.location) }, busy, onSelect)
                }
                item { OutlinedButton(onClick = retry, enabled = !busy) { Text("Refresh location and search") } }
            }
        } else {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                ScanCameraScene(position, validHeading, restaurants, route, progress, isDemo, busy,
                    cameraAllowed, cameraAttempt, { cameraError = it }, onSelect,
                    arEnabled, pathEndsNearby, { arStatus = it }, { arEnabled = false; arStatus = "Local 3D AR is unavailable. Using the camera compass view." },
                    Modifier.fillMaxSize())
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (route != null) Surface(color = Color.Black.copy(alpha = .75f), shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(progress?.instruction?.text ?: "Waiting for your location", color = Color.White, fontWeight = FontWeight.SemiBold)
                            if (progress?.isOffRoute == true) {
                                Text("Off route · recalculate before continuing", color = Color(0xFFFFD58B))
                                Button(onClick = onRecalculate, enabled = !busy) { Text("Recalculate") }
                            }
                            if (!isDemo) {
                                Text(arStatus ?: "GPS + compass guidance", color = Color.White.copy(alpha = .8f), fontSize = 11.sp)
                                TextButton(onClick = { arEnabled = !arEnabled; arStatus = null }) {
                                    Text(if (arEnabled) "Use compass camera" else "Try local 3D AR", color = Color(0xFF7CF4B2))
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { directionsVisible = true }, Modifier.weight(1f)) { Text("Directions", color = Color.White) }
                                OutlinedButton(onClick = onClose, Modifier.weight(1f)) { Text("End", color = Color.White) }
                            }
                        }
                    }
                    if (problems.isNotEmpty()) Surface(color = Color(0xFFFBECCF), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(10.dp)) {
                            Text(problems.joinToString("\n"), color = Color(0xFF725B32), fontSize = 11.sp, maxLines = 4, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = retry, enabled = !busy) { Text("Retry sensors and search") }
                        }
                    }
                    if (isDemo) Surface(color = Color(0xFFFBECCF), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                            Text("SIMULATION · fictional places and paths", color = DineInk, fontSize = 11.sp)
                            Text("Heading ${(validHeading ?: 0.0).toInt()}°", color = DineMuted, fontSize = 10.sp)
                            Slider(value = (validHeading ?: 0.0).coerceIn(0.0, 359.0).toFloat(), onValueChange = { onHeadingChange(it.toDouble()) }, valueRange = 0f..359f,
                                modifier = Modifier.height(30.dp).semantics { contentDescription = "Demo compass heading" })
                            if (route != null) {
                                Text("Walk ${(demoProgress * 100).toInt()}%", color = DineMuted, fontSize = 10.sp)
                                Slider(value = demoProgress.toFloat().coerceIn(0f, 1f), onValueChange = { onWalkChange(it.toDouble()) },
                                    modifier = Modifier.height(30.dp).semantics { contentDescription = "Demo walking progress" })
                            }
                        }
                    }
                    if (route == null) FilledTonalButton(onClick = { listVisible = true }, Modifier.fillMaxWidth()) {
                        Text("${restaurants.size} nearby restaurants · View list")
                    }
                }
            }
        }
    }
    if (directionsVisible && route != null) ModalBottomSheet(onDismissRequest = { directionsVisible = false }) {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Walking directions", style = MaterialTheme.typography.headlineSmall) }
            items(route.instructions) { instruction -> Text("${instruction.text} · ${scanDistance(instruction.distanceMeters)}") }
            item { Text("Routing: FOSSGIS · © OpenStreetMap contributors · ODbL", style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun ArrivalScreen(destination: Restaurant?, demo: Boolean, onFinish: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Surface(color = DineMint, shape = CircleShape) {
            Icon(Icons.Default.CheckCircle, null, tint = DineGreen, modifier = Modifier.padding(28.dp).size(72.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Arrive at the restaurant", style = MaterialTheme.typography.headlineMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(destination?.name.orEmpty(), style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(if (demo) "Demo arrival · simulated walking" else "GPS indicates you are near the destination. Check the restaurant entrance around you.", color = DineMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Button(onClick = onFinish, modifier = Modifier.fillMaxWidth()) { Text("Back to Home") }
    }
}

@Composable
private fun ScanCameraScene(
    position: GeoPoint?,
    heading: Double?,
    restaurants: List<Restaurant>,
    route: RoutePlan?,
    progress: RouteProgress?,
    isDemo: Boolean,
    busy: Boolean,
    cameraAllowed: Boolean,
    cameraAttempt: Int,
    onCameraError: (String?) -> Unit,
    onSelect: (Restaurant) -> Unit,
    arEnabled: Boolean,
    pathEndsNearby: Boolean,
    onArStatus: (String) -> Unit,
    onArUnavailable: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier.background(Color(0xFF18332E)),
    ) {
        when {
            isDemo -> DemoCameraBackground(Modifier.fillMaxSize())
            cameraAllowed && arEnabled && route != null -> ArNavigationPreview(
                if (position != null && heading != null && progress != null && !progress.isOffRoute && !progress.hasArrived && !pathEndsNearby)
                    headingDelta(bearingDegrees(position, progress.targetPoint), heading) else null,
                Modifier.fillMaxSize(), onArStatus, onArUnavailable,
            )
            cameraAllowed -> key(cameraAttempt) { CameraPreview(Modifier.fillMaxSize(), onCameraError) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent, Color.Black.copy(alpha = .75f)))))
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.Black.copy(alpha = .4f), shape = RoundedCornerShape(20.dp)) {
                Text(if (isDemo) "SIMULATED CAMERA" else if (arEnabled && route != null) "LOCAL 3D AR" else "LIVE CAMERA", Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    color = if (isDemo) Color(0xFFFFD58B) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(heading?.let { "${it.toInt()}°" } ?: "NO HEADING", color = Color.White, fontSize = 11.sp)
        }
        val visibleMarkers = if (position != null && heading != null && route == null) restaurants.map { restaurant ->
            restaurant to projectMarker(bearingDegrees(position, restaurant.location), heading)
        }.filter { it.second.isVisible }.take(6) else emptyList()
        if (position != null) visibleMarkers.forEachIndexed { index, (restaurant, projection) ->
            val markerWidth = minOf(124.dp, maxWidth - 24.dp)
            val x = 12.dp + (maxWidth - markerWidth - 24.dp) * ((projection.horizontal + 1.0) / 2.0).toFloat()
            Surface(
                onClick = { onSelect(restaurant) }, enabled = !busy, color = Color.White.copy(alpha = .96f), shape = RoundedCornerShape(32.dp), shadowElevation = 4.dp,
                modifier = Modifier.width(markerWidth).offset(x = x, y = (90 + (index % 3) * 72).dp),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = DineGreen, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(restaurant.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = DineInk)
                        Text(scanDistance(distanceMeters(position, restaurant.location)) + if (restaurant.halalStatus == "Halal") " · Halal" else "", color = DineMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        val guidance = when {
            position == null -> if (busy) "Getting your GPS location…" else "A current GPS fix is needed to place restaurant markers."
            !isDemo && !cameraAllowed -> "Camera access is unavailable. You can still use the restaurant list."
            progress?.hasArrived == true -> "You’ve arrived"
            pathEndsNearby -> "The mapped path ends nearby. Check the restaurant entrance."
            progress?.isOffRoute == true -> "Off route · recalculate your walking route"
            heading == null -> "A compass heading isn’t available. Use the restaurant list and written walking directions."
            route == null && restaurants.isEmpty() -> if (busy) "Finding nearby restaurants…" else "No restaurants found within 1 km"
            route == null && visibleMarkers.isEmpty() -> "Turn slowly to find nearby restaurant markers"
            else -> null
        }
        if (guidance != null) {
            Column(Modifier.align(Alignment.Center).padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    when {
                        progress?.hasArrived == true -> Icons.Default.CheckCircle
                        position == null -> Icons.Default.LocationSearching
                        else -> Icons.Default.Explore
                    },
                    contentDescription = null, tint = Color(0xFFFFD58B), modifier = Modifier.size(40.dp),
                )
                Spacer(Modifier.height(12.dp))
                Text(guidance, color = Color.White, style = MaterialTheme.typography.bodyMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        } else if (!arEnabled && position != null && heading != null && progress != null && !progress.isOffRoute && !progress.hasArrived) {
            Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Navigation, contentDescription = "Bearing toward the next walking route point",
                    tint = Color(0xFFFFD58B), modifier = Modifier.size(90.dp).graphicsLayer {
                        rotationZ = headingDelta(bearingDegrees(position, progress.targetPoint), heading).toFloat()
                    },
                )
                Spacer(Modifier.height(16.dp))
                Text(progress.instruction?.text ?: "Follow the walking route", color = Color.White,
                    fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        if (progress != null) Surface(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 62.dp),
            color = Color.Black.copy(alpha = .6f), shape = RoundedCornerShape(20.dp),
        ) {
            Text("${scanDistance(progress.remainingMeters)} remaining", Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                color = Color.White, style = MaterialTheme.typography.titleMedium)
        }

    }
}

@Composable
private fun ScanRestaurantCard(restaurant: Restaurant, distance: Double?, busy: Boolean, onSelect: (Restaurant) -> Unit) {
    Surface(onClick = { onSelect(restaurant) }, enabled = !busy, color = Color.White, shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(52.dp).background(DineMint, RoundedCornerShape(15.dp)), contentAlignment = Alignment.Center) {
                Text(restaurant.emoji, fontSize = 25.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(restaurant.name, fontWeight = FontWeight.Bold)
                Text(restaurant.cuisine, color = DineMuted, fontSize = 12.sp)
                Text(if (restaurant.rating > 0.0) String.format(Locale.getDefault(), "★ %.1f", restaurant.rating) else "Not rated", color = DineMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.width(8.dp))
            Text(distance?.let(::scanDistance) ?: "", color = DineGreen, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ScanNotice(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(Modifier.fillMaxWidth().background(DineMint, RoundedCornerShape(14.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = DineGreen, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(9.dp))
        Text(text, color = DineGreen, fontSize = 12.sp)
    }
}

@Composable
private fun DemoCameraBackground(modifier: Modifier) {
    Canvas(modifier) {
        drawRect(Brush.verticalGradient(listOf(Color(0xFF548C89), Color(0xFFC4D2BA), Color(0xFFE1CB9F))))
        drawCircle(Color(0x88FFE1A5), radius = size.width * .065f, center = Offset(size.width * .76f, size.height * .2f))
        for (index in 0..7) {
            val width = size.width * .16f
            val height = size.height * (.18f + (index % 3) * .04f)
            drawRect(if (index % 2 == 0) Color(0xFF6E9085) else Color(0xFF7D9C8D), Offset(index * size.width / 7 - width / 2, size.height * .49f - height), androidx.compose.ui.geometry.Size(width, height))
        }
        val road = Path().apply {
            moveTo(size.width * .46f, size.height * .49f)
            lineTo(size.width * .55f, size.height * .49f)
            lineTo(size.width * 1.12f, size.height)
            lineTo(-size.width * .12f, size.height)
            close()
        }
        drawPath(road, Color(0xFF536E66))
        for (index in 0..4) {
            val y = size.height * (.54f + index * .1f)
            drawLine(Color(0x99EAE1BF), Offset(size.width * .5f, y), Offset(size.width * .5f, y + 8 + index * 5), strokeWidth = 1f + index)
        }
    }
}

private fun scanDistance(metres: Double): String = if (metres >= 1000.0)
    String.format(Locale.getDefault(), "%.1f km", metres / 1000.0) else "${metres.toInt()} m"
