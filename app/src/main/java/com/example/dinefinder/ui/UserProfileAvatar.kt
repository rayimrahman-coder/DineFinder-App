package com.example.dinefinder.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File

@Composable
internal fun UserProfileAvatar(photoPath: String?, size: Dp = 80.dp) {
    var failed by remember(photoPath) { mutableStateOf(false) }
    Box(Modifier.size(size).clip(CircleShape).background(DiscoveryMint), contentAlignment = Alignment.Center) {
        if (photoPath != null && !failed) AsyncImage(File(photoPath), "Your profile picture",
            modifier = Modifier.size(size), contentScale = ContentScale.Crop, onError = { failed = true })
        else Icon(Icons.Default.Person, "Default profile picture", tint = DiscoveryGreen, modifier = Modifier.size(size * .5f))
    }
}
