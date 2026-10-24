package com.example.dinefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.example.dinefinder.core.*
import kotlin.math.roundToInt

/** Packs measured cards above the sheet. Unfitted cards remain available in the list. */
internal fun packDiscoveryMarkers(
    width: Int, height: Int, sizes: List<IntSize>, horizontal: List<Float>,
    top: Int, bottom: Int, margin: Int, gap: Int,
): Map<Int, IntOffset> {
    val available = (height - top - bottom).coerceAtLeast(0)
    val accepted = mutableListOf<Int>()
    var used = 0
    sizes.forEachIndexed { i, size ->
        val needed = size.height + if (accepted.isEmpty()) 0 else gap
        if (size.width <= width - margin * 2 && used + needed <= available) {
            accepted += i; used += needed
        }
    }
    val freeGap = (available - used) / (accepted.size + 1)
    var y = top + freeGap
    return accepted.associateWith { i ->
        val x = margin + ((width - margin * 2 - sizes[i].width) * horizontal[i].coerceIn(0f, 1f)).roundToInt()
        IntOffset(x, y).also { y += sizes[i].height + gap + freeGap }
    }
}

@Composable
internal fun DiscoveryMarkers(
    position: GeoPoint, heading: Double, restaurants: List<Restaurant>,
    selectedId: String?, availableHeight: Dp, busy: Boolean, onSelect: (Restaurant) -> Unit,
) {
    val visible = restaurants.map { it to projectMarker(bearingDegrees(position, it.location), heading) }
        .filter { it.second.isVisible }.take(6)
    SubcomposeLayout(Modifier.fillMaxWidth().height(availableHeight).clipToBounds().testTag("ar_restaurant_markers")) { constraints ->
        val width = minOf(180.dp.roundToPx(), constraints.maxWidth - 32.dp.roundToPx()).coerceAtLeast(1)
        val cards = visible.map { (restaurant, _) ->
            subcompose(restaurant.id) {
                DiscoveryMarker(restaurant, distanceMeters(position, restaurant.location), restaurant.id == selectedId, busy, onSelect)
            }.single().measure(Constraints(minWidth = width, maxWidth = width))
        }
        val placements = packDiscoveryMarkers(constraints.maxWidth, constraints.maxHeight,
            cards.map { IntSize(it.width, it.height) }, visible.map { ((it.second.horizontal + 1) / 2).toFloat() },
            64.dp.roundToPx(), 58.dp.roundToPx(), 16.dp.roundToPx(), 14.dp.roundToPx())
        layout(constraints.maxWidth, constraints.maxHeight) {
            placements.forEach { (index, offset) -> cards[index].place(offset.x, offset.y) }
        }
    }
}

@Composable
private fun DiscoveryMarker(restaurant: Restaurant, distance: Double, selected: Boolean, busy: Boolean, onSelect: (Restaurant) -> Unit) {
    Column {
        Surface(onClick = { onSelect(restaurant) }, enabled = !busy,
            color = Color.White.copy(alpha = .98f), contentColor = DiscoveryInk, shape = RoundedCornerShape(20.dp), shadowElevation = 3.dp,
            border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) DiscoveryGreen else Color.White)) {
            Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(DiscoveryMint, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Restaurant, null, tint = DiscoveryGreen, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(restaurant.name, color = DiscoveryInk, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(scanDistance(distance) + if (restaurant.halalStatus == "Halal") " · Halal" else "", color = DiscoveryMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
        }
        Canvas(Modifier.fillMaxWidth().height(24.dp)) {
            val x = size.width / 2
            drawLine(DiscoveryGreen, Offset(x, 0f), Offset(x, size.height - 6.dp.toPx()), 2.dp.toPx())
            drawCircle(Color.White, 6.dp.toPx(), Offset(x, size.height - 6.dp.toPx()))
            drawCircle(DiscoveryGreen, 4.dp.toPx(), Offset(x, size.height - 6.dp.toPx()))
        }
    }
}
