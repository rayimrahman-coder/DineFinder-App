package com.example.dinefinder.ui

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.dinefinder.core.Restaurant

/** Restaurant information stays separate from the GPS coordinates used for routing. */
@Composable
internal fun RestaurantProfileScreen(
    restaurant: Restaurant,
    distance: Double?,
    busy: Boolean,
    onClose: () -> Unit,
    onNavigate: () -> Unit,
) {
    val context = LocalContext.current
    val preferences = remember(context) { context.getSharedPreferences("dinefinder_saved_restaurants", Context.MODE_PRIVATE) }
    var saved by remember(restaurant.id) { mutableStateOf(preferences.getBoolean(restaurant.id, false)) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().testTag("restaurant_profile"), color = Color.White, contentColor = DiscoveryInk) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    IconButton(onClick = onClose, modifier = Modifier.size(48.dp).background(DiscoveryMint, CircleShape)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to restaurants", tint = DiscoveryGreen)
                    }
                    Text("Restaurant details", Modifier.weight(1f), fontSize = 19.sp, lineHeight = 25.sp, fontWeight = FontWeight.Bold)
                    OutlinedIconToggleButton(checked = saved, onCheckedChange = {
                        saved = it
                        preferences.edit().putBoolean(restaurant.id, it).apply()
                    }, modifier = Modifier.size(48.dp), border = BorderStroke(1.dp, DiscoveryBorder)) {
                        Icon(if (saved) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            if (saved) "Remove from saved restaurants" else "Save restaurant", tint = DiscoveryGreen)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    // The current Supabase model has no photo URL. Never substitute another venue's photograph.
                    Box(Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(DiscoveryMint, Color(0xFFF0FAF5)))), contentAlignment = Alignment.Center) {
                        Box(Modifier.align(Alignment.TopEnd).offset(x = 28.dp, y = (-30).dp).size(155.dp)
                            .background(Color.White.copy(alpha = .45f), CircleShape))
                        Box(Modifier.align(Alignment.BottomStart).offset(x = (-25).dp, y = 48.dp).size(170.dp)
                            .background(DiscoveryGreen.copy(alpha = .06f), CircleShape))
                        Box(Modifier.size(104.dp).background(Color.White.copy(alpha = .85f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Restaurant, null, Modifier.size(52.dp), tint = DiscoveryGreen)
                        }
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (restaurant.cuisine.isNotBlank()) Text(restaurant.cuisine, color = DiscoveryMuted, fontSize = 13.sp, lineHeight = 18.sp)
                        Text(restaurant.name, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = DiscoveryInk)
                        if (restaurant.address.isNotBlank()) Text(restaurant.address, fontSize = 15.sp, lineHeight = 22.sp, color = DiscoveryMuted)
                        distance?.takeIf { it.isFinite() && it >= 0 }?.let {
                            Surface(color = DiscoveryMint, contentColor = DiscoveryGreen, shape = CircleShape) {
                                Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.DirectionsWalk, null, Modifier.size(20.dp))
                                    Text("${scanDistance(it)} away", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    if (restaurant.description.isNotBlank()) ProfileInfoCard("About this place") {
                        Text(restaurant.description, color = DiscoveryMuted, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    ProfileInfoCard("Location", Icons.Default.LocationOn) {
                        Text(restaurant.address.ifBlank { "Address not provided" }, color = DiscoveryMuted, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    if (restaurant.openingHours.isNotBlank()) ProfileInfoCard("Opening hours", Icons.Default.Schedule) {
                        Text(restaurant.openingHours, color = DiscoveryMuted, fontSize = 15.sp, lineHeight = 23.sp)
                    }
                    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, border = BorderStroke(1.dp, DiscoveryBorder)) {
                        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Explore, null, tint = DiscoveryGreen, modifier = Modifier.size(28.dp))
                            Text("Follow walking directions through your camera", fontSize = 14.sp, lineHeight = 21.sp, color = DiscoveryMuted)
                        }
                    }
                }
                Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onNavigate, enabled = !busy, shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen, contentColor = Color.White),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                            if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = DiscoveryGreen, strokeWidth = 2.dp)
                            else Icon(Icons.Default.NearMe, null, Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(if (busy) "Finding walking route…" else "Start walking navigation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                        Text("Routes follow available walking paths", fontSize = 12.sp, lineHeight = 18.sp, color = DiscoveryMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileInfoCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(20.dp), color = Color.White, contentColor = DiscoveryInk, border = BorderStroke(1.dp, DiscoveryBorder)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (icon != null) Icon(icon, null, tint = DiscoveryGreen, modifier = Modifier.size(22.dp))
                Text(title, fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}
