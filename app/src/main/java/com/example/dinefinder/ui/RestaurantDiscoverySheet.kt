package com.example.dinefinder.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.dinefinder.core.GeoPoint
import com.example.dinefinder.core.Restaurant
import com.example.dinefinder.core.distanceMeters
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal val DiscoveryGreen = Color(0xFF075548)
internal val DiscoveryMint = Color(0xFFD9F4EC)
internal val DiscoveryInk = Color(0xFF101820)
internal val DiscoveryMuted = Color(0xFF606870)
internal val DiscoveryBorder = Color(0xFFE5E9E8)
internal enum class DiscoverySheetPosition { Collapsed, Half, Expanded }

@Composable
internal fun DiscoveryHeader(title: String, subtitle: String, onBack: () -> Unit, onChecks: () -> Unit) {
    Surface(color = Color.White, contentColor = DiscoveryInk) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(44.dp).background(DiscoveryMint.copy(alpha = .6f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Home", tint = DiscoveryGreen)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, color = DiscoveryInk, fontSize = 22.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = DiscoveryMuted, fontSize = 14.sp, lineHeight = 19.sp)
            }
            OutlinedIconButton(onClick = onChecks, modifier = Modifier.size(44.dp), border = BorderStroke(1.dp, DiscoveryBorder)) {
                Icon(Icons.Default.VerifiedUser, "Scan checklist", tint = DiscoveryGreen)
            }
        }
    }
}

/** Persistent, three-anchor panel. Dragging and list scrolling share the same state. */
@Composable
internal fun RestaurantDiscoverySheet(
    state: AnchoredDraggableState<DiscoverySheetPosition>,
    offset: Float,
    visibleHeight: Dp,
    collapsed: Boolean,
    restaurants: List<Restaurant>,
    position: GeoPoint?,
    busy: Boolean,
    searchCompleted: Boolean,
    problems: List<String>,
    searchFailed: Boolean,
    onSelect: (Restaurant) -> Unit,
    onRetry: () -> Unit,
    onChecks: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    fun moveTo(value: DiscoverySheetPosition) { scope.launch { state.animateTo(value, tween(240)) } }
    val expanded = state.currentValue == DiscoverySheetPosition.Expanded
    val toggle = { moveTo(when (state.currentValue) {
        DiscoverySheetPosition.Collapsed -> DiscoverySheetPosition.Half
        DiscoverySheetPosition.Half -> DiscoverySheetPosition.Expanded
        DiscoverySheetPosition.Expanded -> DiscoverySheetPosition.Collapsed
    }) }
    val nestedScroll = remember(state) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput && available.y < 0)
                    Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
                if (source == NestedScrollSource.UserInput) Offset(0f, state.dispatchRawDelta(available.y)) else Offset.Zero
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (available.y < 0 && state.requireOffset() > 0f) {
                    state.animateTo(DiscoverySheetPosition.Expanded, tween(240))
                    return Velocity(0f, available.y)
                }
                return Velocity.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                state.settle(tween(240))
                return Velocity(0f, available.y)
            }
        }
    }
    val count = when {
        searchFailed && restaurants.isEmpty() -> "Search unavailable"
        position == null && restaurants.isEmpty() -> "Locating you…"
        (busy || !searchCompleted) && restaurants.isEmpty() -> "Finding restaurants…"
        else -> "${restaurants.size} nearby restaurants"
    }
    Surface(
        Modifier.offset { IntOffset(0, offset.roundToInt()) }.fillMaxWidth().height(visibleHeight)
            .testTag("restaurant_discovery_sheet").semantics {
                stateDescription = state.currentValue.name
                customActions = listOf(
                    CustomAccessibilityAction("Expand restaurants") { moveTo(DiscoverySheetPosition.Expanded); true },
                    CustomAccessibilityAction("Collapse restaurants") { moveTo(DiscoverySheetPosition.Collapsed); true },
                )
            },
        color = Color.White, contentColor = DiscoveryInk, shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), shadowElevation = 8.dp,
    ) {
        Column {
            Column(Modifier.fillMaxWidth().anchoredDraggable(state, Orientation.Vertical,
                flingBehavior = AnchoredDraggableDefaults.flingBehavior(state)).testTag("restaurant_sheet_handle")) {
                Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 10.dp, bottom = 7.dp)
                    .size(34.dp, 4.dp).background(Color(0xFFA6AFB4), CircleShape))
                Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 18.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(count, color = DiscoveryInk, fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                        if (!collapsed) Text("Swipe up to explore · Swipe down to minimise", color = DiscoveryMuted, fontSize = 12.sp, lineHeight = 17.sp)
                    }
                    OutlinedIconButton(onClick = toggle, modifier = Modifier.size(40.dp), border = BorderStroke(1.dp, DiscoveryBorder)) {
                        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
                            if (expanded) "Collapse restaurants" else "Expand restaurants", tint = DiscoveryGreen)
                    }
                }
            }
            if (!collapsed) LazyColumn(
                Modifier.weight(1f).fillMaxWidth().nestedScroll(nestedScroll).testTag("nearby_restaurant_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 5.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (problems.isNotEmpty()) item {
                    Surface(color = Color(0xFFFFF5E5), shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(problems.joinToString("\n"), color = Color(0xFF77552D), fontSize = 13.sp)
                            TextButton(onClick = onChecks) { Text("Scan checklist", color = DiscoveryGreen) }
                        }
                    }
                }
                if (restaurants.isEmpty()) item {
                    Text(when {
                        searchFailed -> "Search failed. Check the error above and retry."
                        position == null -> "Waiting for a fresh GPS fix"
                        busy || !searchCompleted -> "Searching for nearby restaurants…"
                        else -> "No restaurants recorded within 1 km"
                    }, Modifier.padding(8.dp), color = DiscoveryMuted, fontSize = 14.sp)
                }
                items(restaurants, key = { it.id }) { restaurant ->
                    DiscoveryRestaurantRow(restaurant, position?.let { distanceMeters(it, restaurant.location) }, busy, onSelect)
                }
                if (restaurants.isNotEmpty()) item {
                    Text("Drag down for more camera space", Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        color = DiscoveryMuted, textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp, lineHeight = 16.sp)
                }
                item {
                    OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DiscoveryGreen)) { Text("Refresh location and search") }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryRestaurantRow(restaurant: Restaurant, distance: Double?, busy: Boolean, onSelect: (Restaurant) -> Unit) {
    Surface(onClick = { onSelect(restaurant) }, enabled = !busy, color = Color.White, contentColor = DiscoveryInk,
        shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, DiscoveryBorder)) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(DiscoveryMint, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Restaurant, null, tint = DiscoveryGreen, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(restaurant.name, color = DiscoveryInk, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 20.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(distance?.let { "${scanDistance(it)} away" } ?: "Distance unavailable", color = DiscoveryMuted, fontSize = 13.sp, lineHeight = 18.sp)
                    if (restaurant.halalStatus == "Halal") Surface(color = DiscoveryMint, shape = CircleShape) {
                        Text("Halal", Modifier.padding(horizontal = 8.dp, vertical = 2.dp), color = DiscoveryGreen, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = DiscoveryMuted, modifier = Modifier.size(20.dp))
        }
    }
}
