package com.example.dinefinder.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinefinder.core.*
import com.example.dinefinder.device.CameraPreview
import com.example.dinefinder.ar.ArNavigationPreview
import java.util.Locale
import kotlinx.coroutines.launch

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
    onShowSearchDetails: () -> Unit = {},
    searchCompleted: Boolean = true,
    onCameraCheckChange: (Boolean, String?, Boolean) -> Unit = { _, _, _ -> },
) {
    var focusedId by rememberSaveable { mutableStateOf<String?>(null) }
    val orderedRestaurants = remember(restaurants, position) {
        if (position == null) restaurants else restaurants.sortedBy { distanceMeters(position, it.location) }
    }
    val selectedId = focusedId?.takeIf { id -> orderedRestaurants.any { it.id == id } } ?: orderedRestaurants.firstOrNull()?.id
    val selectRestaurant: (Restaurant) -> Unit = { focusedId = it.id; onSelect(it) }
    var directionsVisible by remember(route) { mutableStateOf(false) }
    var cameraReady by remember(isDemo) { mutableStateOf(false) }
    var cameraError by remember(isDemo) { mutableStateOf<String?>(null) }
    var arStatus by remember(route) { mutableStateOf<String?>(null) }
    var arEnabled by remember(route) { mutableStateOf(!isDemo && route != null) }
    var cameraAttempt by remember { mutableIntStateOf(0) }
    val cameraActive = !(arEnabled && route != null)
    LaunchedEffect(cameraReady, cameraError, cameraActive) {
        onCameraCheckChange(cameraReady, cameraError, cameraActive)
    }
    val validHeading = heading?.takeIf { it.isFinite() }
    val progress = if (position != null && route != null) restaurantRouteProgress(position, route, destination?.location) else null
    val pathEndsNearby = position != null && route != null && progress?.hasArrived == false && nearestRouteProgress(position, route).hasArrived
    LaunchedEffect(progress?.hasArrived) { if (progress?.hasArrived == true) directionsVisible = false }
    val retry = { cameraAttempt++; cameraError = null; onRetry() }
    val problems = listOfNotNull(error, locationError, headingError, cameraError).distinct()

    Column(Modifier.fillMaxSize().background(Color.White)) {
        DiscoveryHeader(if (route == null) "Discover nearby" else "Walking navigation",
            if (route == null) "Restaurants within 1 km" else destination?.name.orEmpty(), onClose, onShowSearchDetails)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DiscoveryGreen)
        if (progress?.hasArrived == true) {
            ArrivalScreen(destination, isDemo, onClose)
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            val fontScale = density.fontScale.coerceAtLeast(1f)
            val heightPx = with(density) { maxHeight.toPx() }
            val collapsedHeight = minOf(72.dp * fontScale, maxHeight * .55f)
            val halfHeight = minOf(maxHeight - 16.dp,
                maxOf(collapsedHeight + 32.dp, minOf(280.dp * fontScale, maxHeight * .75f)))
            val halfTop = with(density) { (maxHeight - halfHeight).toPx() }.coerceAtLeast(0f)
            val collapsedTop = with(density) { (maxHeight - collapsedHeight).toPx() }.coerceAtLeast(halfTop + 1f)
            val sheet = rememberSaveable(saver = AnchoredDraggableState.Saver<DiscoverySheetPosition>()) {
                AnchoredDraggableState(DiscoverySheetPosition.Collapsed)
            }
            val anchors = remember(heightPx, halfTop, collapsedTop) { DraggableAnchors {
                DiscoverySheetPosition.Expanded at 0f
                DiscoverySheetPosition.Half at halfTop
                DiscoverySheetPosition.Collapsed at collapsedTop
            } }
            SideEffect { sheet.updateAnchors(anchors, sheet.targetValue) }
            val scope = rememberCoroutineScope()
            val sheetTop = (sheet.offset.takeIf { it.isFinite() } ?: collapsedTop).coerceIn(0f, collapsedTop)
            val cameraHeight = if (route == null) with(density) { sheetTop.toDp() } else maxHeight
            BackHandler(enabled = route == null && sheet.currentValue == DiscoverySheetPosition.Expanded) {
                scope.launch { sheet.animateTo(DiscoverySheetPosition.Half, tween(240)) }
            }
            ScanCameraScene(position, validHeading, orderedRestaurants, route, progress, isDemo, busy,
                cameraAllowed, cameraAttempt, { cameraError = it }, { cameraReady = it }, selectRestaurant,
                arEnabled, pathEndsNearby, error, searchCompleted, { arStatus = it },
                { arEnabled = false; arStatus = "Local 3D AR is unavailable. Using the camera compass view." },
                cameraHeight, selectedId, cameraReady, cameraError, Modifier.fillMaxSize())
            if (route == null) {
                RestaurantDiscoverySheet(sheet, sheetTop, with(density) { (heightPx - sheetTop).toDp() },
                    cameraHeight >= maxHeight - collapsedHeight - 2.dp,
                    orderedRestaurants, position, busy, searchCompleted, problems, error != null,
                    selectRestaurant, retry, onShowSearchDetails)
            } else Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        Text(problems.joinToString("\n"), color = Color(0xFF77552D), fontSize = 12.sp)
                        TextButton(onClick = retry, enabled = !busy) { Text("Retry sensors and search") }
                    }
                }
                if (isDemo) Surface(color = Color(0xFFFBECCF), shape = RoundedCornerShape(14.dp)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("SIMULATION · fictional route", fontSize = 11.sp)
                        Slider(value = (validHeading ?: 0.0).toFloat(), onValueChange = { onHeadingChange(it.toDouble()) }, valueRange = 0f..359f,
                            modifier = Modifier.semantics { contentDescription = "Demo compass heading" })
                        Slider(value = demoProgress.toFloat(), onValueChange = { onWalkChange(it.toDouble()) },
                            modifier = Modifier.semantics { contentDescription = "Demo walking progress" })
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
    onCameraReady: (Boolean) -> Unit,
    onSelect: (Restaurant) -> Unit,
    arEnabled: Boolean,
    pathEndsNearby: Boolean,
    searchError: String?,
    searchCompleted: Boolean,
    onArStatus: (String) -> Unit,
    onArUnavailable: () -> Unit,
    markerAreaHeight: androidx.compose.ui.unit.Dp,
    selectedId: String?,
    cameraReady: Boolean,
    cameraError: String?,
    modifier: Modifier,
) {
    Box(
        modifier.background(Color(0xFF18332E)),
    ) {
        when {
            isDemo -> DemoCameraBackground(Modifier.fillMaxSize())
            cameraAllowed && arEnabled && route != null -> ArNavigationPreview(
                if (position != null && heading != null && progress != null && !progress.isOffRoute && !progress.hasArrived && !pathEndsNearby)
                    headingDelta(bearingDegrees(position, progress.targetPoint), heading) else null,
                Modifier.fillMaxSize(), onArStatus, onArUnavailable,
            )
            cameraAllowed -> key(cameraAttempt) { CameraPreview(Modifier.fillMaxSize(), onCameraError, onCameraReady) }
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .08f), Color.Transparent, Color.Black.copy(alpha = .22f)))))
        Box(Modifier.fillMaxWidth().height(markerAreaHeight).clipToBounds()) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = Color.White.copy(alpha = .94f), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Box(Modifier.size(7.dp).background(if (cameraReady) DiscoveryGreen else DiscoveryMuted, CircleShape))
                        Text(when { isDemo -> "Demo camera"; arEnabled && route != null -> "3D AR"; cameraError != null -> "Camera unavailable"; !cameraAllowed -> "Camera off"; cameraReady -> "Camera live"; else -> "Camera starting" },
                            color = DiscoveryInk, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.weight(1f))
                Surface(color = Color.White.copy(alpha = .92f), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Default.Navigation, null, tint = Color(0xFFD6554B), modifier = Modifier.size(17.dp).graphicsLayer { rotationZ = -(heading ?: 0.0).toFloat() })
                        Text(heading?.let { "${it.toInt()}°" } ?: "—", color = DiscoveryInk, fontSize = 12.sp)
                    }
                }
            }
            if (position != null && heading != null && route == null) DiscoveryMarkers(position, heading, restaurants, selectedId, markerAreaHeight, busy, onSelect)
        }
        val hasVisibleMarker = position != null && heading != null && restaurants.any { projectMarker(bearingDegrees(position, it.location), heading).isVisible }
        val guidance = when {
            searchError != null && route == null && restaurants.isEmpty() -> "Restaurant search could not be completed. Expand the panel for details."
            position == null -> if (busy) "Getting your GPS location…" else "A current GPS fix is needed to place restaurant markers."
            !isDemo && !cameraAllowed -> "Camera access is unavailable. You can still use the restaurant list."
            progress?.hasArrived == true -> "You’ve arrived"
            pathEndsNearby -> "The mapped path ends nearby. Check the restaurant entrance."
            progress?.isOffRoute == true -> "Off route · recalculate your walking route"
            heading == null -> "A compass heading isn’t available. Use the restaurant list and written walking directions."
            route == null && restaurants.isEmpty() -> if (busy || !searchCompleted) "Finding nearby restaurants…" else "No restaurants found within 1 km"
            route == null && !hasVisibleMarker -> "Turn slowly to find nearby restaurant markers"
            else -> null
        }
        if (guidance != null && markerAreaHeight > 150.dp) {
            Box(Modifier.fillMaxWidth().height(markerAreaHeight).padding(horizontal = 24.dp, vertical = 60.dp), contentAlignment = Alignment.Center) {
                Surface(color = Color.Black.copy(alpha = .62f), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(if (position == null) Icons.Default.LocationSearching else Icons.Default.Explore,
                            null, tint = Color.White, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(8.dp))
                        Text(guidance, color = Color.White, fontSize = 14.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
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
        if (route == null && guidance == null && markerAreaHeight > 170.dp) {
            Box(Modifier.fillMaxWidth().height(markerAreaHeight).padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.BottomCenter) {
                Surface(color = Color(0xFF101820).copy(alpha = .65f), shape = CircleShape) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ScreenRotation, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Text("Move your phone slowly to explore", color = Color.White, fontSize = 12.sp)
                    }
                }
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

internal fun scanDistance(metres: Double): String = if (metres >= 1000.0)
    String.format(Locale.getDefault(), "%.1f km", metres / 1000.0) else "${metres.toInt()} m"
