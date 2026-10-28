package com.example.dinefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.dinefinder.core.RestaurantProfileDetails

@Composable
internal fun RestaurantProfilePhoto(name: String, url: String?, imageLoader: ImageLoader) {
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    var attempt by remember(url) { mutableIntStateOf(0) }
    val context = LocalContext.current
    if (url == null) {
        Surface(color = Color.White, shape = RoundedCornerShape(18.dp), border = BorderStroke(1.dp, DiscoveryBorder)) {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Photo, null, tint = DiscoveryMuted)
                Text("Photo not available", color = DiscoveryMuted, fontSize = 14.sp)
            }
        }
    } else Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(24.dp)).testTag("restaurant_photo"),
        contentAlignment = Alignment.Center) {
        if (failed) {
            Surface(color = DiscoveryMint.copy(alpha = .4f), modifier = Modifier.fillMaxSize()) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Default.Photo, null, tint = DiscoveryGreen)
                    Text("Photo couldn't be loaded", color = DiscoveryMuted)
                    TextButton(onClick = { attempt++; failed = false; loading = true }) { Text("Retry photo", color = DiscoveryGreen) }
                }
            }
        } else key(url, attempt) {
            AsyncImage(model = ImageRequest.Builder(context).data(url).crossfade(true).build(), imageLoader = imageLoader,
                contentDescription = "Photo of $name", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
                onLoading = { loading = true }, onSuccess = { loading = false }, onError = { failed = true; loading = false })
        }
        if (loading && !failed) CircularProgressIndicator(Modifier.size(28.dp), color = DiscoveryGreen, strokeWidth = 2.dp)
    }
}

@Composable
internal fun RestaurantReviewSection(details: RestaurantProfileDetails?) {
    ProfileInfoCard("Reviews", Icons.Default.Star) {
        Text(when (val count = details?.reviewCount) {
            null -> "Review count not available"
            0 -> "No reviews recorded"
            1 -> "1 review"
            else -> "$count reviews"
        }, color = DiscoveryGreen, fontSize = 16.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold)
        val scores = details?.scoreCounts.orEmpty()
        val total = scores.values.sumOf { it.toLong() }
        if (total > 0) Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Rating breakdown", color = DiscoveryMuted, fontSize = 13.sp)
            scores.toSortedMap(reverseOrder()).forEach { (stars, count) ->
                Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "$stars stars: $count reviews" },
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("$stars", color = DiscoveryMuted, fontSize = 12.sp)
                    Icon(Icons.Default.Star, null, tint = DiscoveryGreen, modifier = Modifier.size(14.dp))
                    LinearProgressIndicator(progress = { (count.toDouble() / total).toFloat() }, modifier = Modifier.weight(1f).height(6.dp),
                        color = DiscoveryGreen, trackColor = DiscoveryMint)
                    Text("$count", color = DiscoveryMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
