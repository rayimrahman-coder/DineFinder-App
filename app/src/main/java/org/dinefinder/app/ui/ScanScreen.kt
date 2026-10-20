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
import java.util.Locale
import kotlin.math.ceil

/** A GPS/compass overlay. It does not estimate a world-anchored AR pose. */
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
    var allDirections by remember(route) { mutableStateOf(false) }
    var cameraError by remember(isDemo) { mutableStateOf<String?>(null) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    val validHeading = heading?.takeIf { it.isFinite() }
    val progress = if (position != null && route != null) nearestRouteProgress(position, route) else null
    val retry = { cameraAttempt++; cameraError = null; onRetry() }
    val problems = listOfNotNull(error, locationError, headingError, cameraError).distinct()

    Column(Modifier.fillMaxSize().background(DinePaper)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 6.dp, end = 12.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Home")
            }
            Column(Modifier.weight(1f)) {
                Text(if (route == null) "Scan for Restaurant" else "Your walking route", style = MaterialTheme.typography.titleMedium)
                Text("Within 1 km of your location", color = DineMuted, fontSize = 11.sp)
            }
            TextButton(onClick = { listVisible = !listVisible }) {
                Icon(if (listVisible) Icons.Default.CameraAlt else Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(5.dp))
                Text(if (listVisible) "Camera" else "List")
            }
        }
        if (busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isDemo) item {
                ScanNotice("Demo · fictional restaurants and simulated movement", Icons.Default.Science)
            }
            if (problems.isNotEmpty()) item {
                Surface(color = Color(0xFFFFF0D5), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(problems.joinToString("\n"), color = Color(0xFF725B32), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = retry, enabled = !busy) { Text(if (busy) "Connecting…" else "Retry sensors and search") }
                    }
                }
            }
            if (!listVisible) item {
                ScanCameraScene(
                    position, validHeading, restaurants, route, progress, isDemo, busy,
                    cameraAllowed, cameraAttempt, { cameraError = it }, onSelect,
                )
            }
            if (route != null) item {
                val fraction = if (route.distanceMeters > 0.0 && progress != null)
                    (progress.remainingMeters / route.distanceMeters).coerceIn(0.0, 1.0) else 1.0
                val minutes = ceil(route.durationSeconds * fraction / 60.0).toInt().coerceAtLeast(1)
                Surface(color = Color.White, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(destination?.name ?: "Your destination", style = MaterialTheme.typography.titleLarge)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.DirectionsWalk, contentDescription = null, tint = DineGreen)
                            Spacer(Modifier.width(10.dp))
                            Text(
                                when {
                                    progress == null -> "Waiting for a current GPS fix"
                                    progress.hasArrived -> "You’ve arrived"
                                    else -> "$minutes min · ${scanDistance(progress.remainingMeters)} remaining"
                                },
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (route.isDemo) Text("Simulation only. This path does not represent streets or walkable access.", color = DineMuted, fontSize = 12.sp)
                        if (progress?.isOffRoute == true && !progress.hasArrived) {
                            Text("You’re away from the walking route. Recalculate before following the next direction.", color = DineRed, fontSize = 13.sp)
                            Button(onClick = onRecalculate, enabled = !busy) { Text(if (busy) "Calculating…" else "Recalculate route") }
                        }
                        progress?.instruction?.let { instruction ->
                            Text(instruction.text, style = MaterialTheme.typography.titleMedium)
                        }
                        val activeStep = progress?.instruction?.let { route.instructions.indexOf(it) }?.coerceAtLeast(0) ?: 0
                        val steps = route.instructions.drop(activeStep)
                        steps.take(if (allDirections) Int.MAX_VALUE else 3).forEachIndexed { index, instruction ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                                Text("${activeStep + index + 1}.", color = DineMuted, modifier = Modifier.width(24.dp))
                                Text(instruction.text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(10.dp))
                                Text(scanDistance(instruction.distanceMeters), color = DineMuted, fontSize = 11.sp)
                            }
                        }
                        if (steps.size > 3) TextButton(onClick = { allDirections = !allDirections }) {
                            Text(if (allDirections) "Show fewer directions" else "Show all directions")
                        }
                        Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                            Text(if (progress?.hasArrived == true) "Finish journey" else "End navigation")
                        }
                    }
                }
            }
            if (isDemo) item {
                Surface(color = Color(0xFFFFF0D5), shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Try the camera view", fontWeight = FontWeight.SemiBold)
                        Text("Simulated heading: ${(validHeading ?: 0.0).toInt()}°", fontSize = 12.sp, color = DineMuted)
                        Slider(
                            value = (validHeading ?: 0.0).coerceIn(0.0, 359.0).toFloat(),
                            onValueChange = { onHeadingChange(it.toDouble()) }, valueRange = 0f..359f,
                            modifier = Modifier.semantics { contentDescription = "Demo compass heading" },
                        )
                        if (route != null) {
                            Text("Simulate walking: ${(demoProgress.coerceIn(0.0, 1.0) * 100).toInt()}%", color = DineMuted, fontSize = 12.sp)
                            Slider(
                                value = demoProgress.coerceIn(0.0, 1.0).toFloat(),
                                onValueChange = { onWalkChange(it.toDouble()) },
                                modifier = Modifier.semantics { contentDescription = "Demo walking progress" },
                            )
                        }
                    }
                }
            }
            if (!listVisible && route == null) item {
                OutlinedButton(onClick = { listVisible = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("View ${restaurants.size} nearby restaurants")
                }
            }
            if (listVisible) {
                item {
                    Text("${restaurants.size} restaurants within 1 km", style = MaterialTheme.typography.titleMedium)
                }
                if (restaurants.isEmpty()) item {
                    Surface(color = Color.White, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(if (position == null) "Waiting for your location" else if (busy) "Finding restaurants…" else "No restaurants found within 1 km", fontWeight = FontWeight.SemiBold)
                            Text(if (position == null) "Allow location access and wait for a GPS fix." else "Only restaurants with a recorded location inside the search radius appear here.", color = DineMuted, fontSize = 13.sp)
                            TextButton(onClick = retry, enabled = !busy) { Text("Search again") }
                        }
                    }
                }
                items(restaurants, key = { it.id }) { restaurant ->
                    ScanRestaurantCard(restaurant, position?.let { distanceMeters(it, restaurant.location) }, busy, onSelect)
                }
            }
            item {
                Text(
                    "GPS + compass guidance is approximate. Stay aware of traffic and use the written directions; the camera does not detect obstacles or safe crossings.",
                    color = DineMuted, fontSize = 11.sp, lineHeight = 17.sp,
                )
                if (!isDemo) Text("Walking routes: FOSSGIS · © OpenStreetMap contributors · ODbL", color = DineMuted, fontSize = 10.sp)
            }
        }
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
) {
    BoxWithConstraints(
        Modifier.fillMaxWidth().height(420.dp).clip(RoundedCornerShape(24.dp)).background(Color(0xFF18332E)),
    ) {
        when {
            isDemo -> DemoCameraBackground(Modifier.fillMaxSize())
            cameraAllowed -> key(cameraAttempt) { CameraPreview(Modifier.fillMaxSize(), onCameraError) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .45f), Color.Transparent, Color.Black.copy(alpha = .75f)))))
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(color = Color.Black.copy(alpha = .4f), shape = RoundedCornerShape(20.dp)) {
                Text(if (isDemo) "SIMULATED CAMERA" else "LIVE CAMERA", Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    color = if (isDemo) Color(0xFFFFD58B) else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            Text(heading?.let { "${it.toInt()}°" } ?: "NO HEADING", color = Color.White, fontSize = 11.sp)
        }
        val visibleMarkers = if (position != null && heading != null && route == null) restaurants.map { restaurant ->
            restaurant to projectMarker(bearingDegrees(position, restaurant.location), heading)
        }.filter { it.second.isVisible }.take(6) else emptyList()
        if (position != null) visibleMarkers.forEachIndexed { index, (restaurant, projection) ->
            val markerWidth = minOf(150.dp, maxWidth - 24.dp)
            val x = 12.dp + (maxWidth - markerWidth - 24.dp) * ((projection.horizontal + 1.0) / 2.0).toFloat()
            Surface(
                onClick = { onSelect(restaurant) }, enabled = !busy, color = Color.White.copy(alpha = .96f), shape = RoundedCornerShape(15.dp), shadowElevation = 4.dp,
                modifier = Modifier.width(markerWidth).offset(x = x, y = (90 + (index % 3) * 72).dp),
            ) {
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(restaurant.emoji, fontSize = 22.sp)
                    Spacer(Modifier.width(7.dp))
                    Column(Modifier.weight(1f)) {
                        Text(restaurant.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, color = DineInk)
                        Text(scanDistance(distanceMeters(position, restaurant.location)), color = DineMuted, fontSize = 10.sp)
                    }
                }
            }
        }
        val guidance = when {
            position == null -> if (busy) "Getting your GPS location…" else "A current GPS fix is needed to place restaurant markers."
            !isDemo && !cameraAllowed -> "Camera access is unavailable. You can still use the restaurant list."
            progress?.hasArrived == true -> "You’ve arrived"
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
        } else if (position != null && heading != null && progress != null && !progress.isOffRoute && !progress.hasArrived) {
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
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Canvas(Modifier.widthIn(max = 280.dp).fillMaxWidth().height(18.dp)) {
                for (index in 0..20) {
                    val x = size.width * index / 20
                    drawLine(Color.White.copy(alpha = .5f), Offset(x, 0f), Offset(x, if (index % 5 == 0) 12f else 6f), strokeWidth = 1.2f)
                }
                drawCircle(Color(0xFFFFD58B), radius = 3f, center = Offset(size.width / 2, 16f))
            }
            Spacer(Modifier.height(8.dp))
            Text("GPS + compass · approximate alignment", color = Color.White.copy(alpha = .8f), fontSize = 10.sp)
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
