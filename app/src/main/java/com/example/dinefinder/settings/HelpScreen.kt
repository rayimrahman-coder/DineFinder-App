package com.example.dinefinder.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dinefinder.ui.DiscoveryGreen
import com.example.dinefinder.ui.DiscoveryMint

@Composable
internal fun HelpScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    var permissionError by remember { mutableStateOf(false) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.background(DiscoveryMint, CircleShape)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back from Help", tint = DiscoveryGreen)
                }
                Text("Help & support", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
            }
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).testTag("help_list"),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                HelpCard {
                    Icon(Icons.AutoMirrored.Filled.HelpOutline, null, Modifier.size(48.dp)
                        .background(DiscoveryMint, CircleShape).padding(10.dp), tint = DiscoveryGreen)
                    Text("A little help finding your next meal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("Discover nearby places and find your way with confidence.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item { HelpHeading("Get started") }
            item { HelpCard {
                HelpStep("1", "Find nearby restaurants", "Tap Scan for Restaurant on Home. Allow camera and precise location access, then wait for a fresh location.")
                HelpStep("2", "Explore your surroundings", "Turn slowly to find restaurant markers within 1 km. Swipe up from the bottom to browse the list. Tap a place to see its details, photos and available reviews.")
                HelpStep("3", "Start walking navigation", "Choose Start walking navigation. Follow the arrow and spoken directions. Swipe up to see controls and instructions; swipe down to clear the camera view.")
            } }
            item { HelpHeading("Common questions") }
            item { HelpQuestion("Why are no restaurants showing?", "Turn on location services, allow precise location, and check your internet connection. Move somewhere with a clear view of the sky and scan again. Only listed restaurants within 1 km appear. Tap the shield icon on the scan screen to check what needs attention.") }
            item { HelpQuestion("Can I navigate without the camera?", "Yes. During navigation, swipe up and choose Turn camera off. GPS directions and voice guidance continue. Choose Turn camera on to return to the camera view.") }
            item { HelpQuestion("Why can’t I hear directions?", "Swipe up during navigation and enable voice. Turn up your phone’s media volume and tap Repeat instruction. If voice is unavailable, use Open speech settings to install or enable an English voice. Keep DineFinder open; background and lock-screen guidance are not supported.") }
            item { HelpQuestion("How do I change my profile picture?", "Open Profile, then Settings and Change photo. Choose a picture using your phone’s photo picker. Your picture is saved for this user on this phone. Use Remove photo to restore the default avatar.") }
            item { HelpQuestion("Why are some photos or reviews missing?", "Not every restaurant has photos or written reviews available. Photos also need an internet connection and may become unavailable. Reopen the profile or use Retry photo when shown. A review count may include reviews whose comments have not been imported.") }
            item { HelpQuestion("What if a place is temporarily closed?", "Check the restaurant’s profile before setting off. Temporarily closed places are labelled. Opening hours and closure information can change, so confirm with the restaurant if you are unsure.") }
            item { HelpCard {
                Text("Need to change access?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Manage camera and location access in your phone’s app permissions.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = {
                    permissionError = runCatching { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))) }.isFailure
                }, modifier = Modifier.fillMaxWidth()) { Text("App permissions") }
                if (permissionError) Text("Open your phone’s Settings, then Apps → DineFinder → Permissions.", color = MaterialTheme.colorScheme.error)
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) { Text("Open Settings") }
            } }
            item { Text("Pause and check your surroundings before following a direction.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun HelpHeading(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.semantics { heading() })
}

@Composable
private fun HelpCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
    }
}

@Composable
private fun HelpStep(number: String, title: String, body: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(32.dp).background(DiscoveryMint, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, color = DiscoveryGreen, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HelpQuestion(question: String, answer: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Surface(onClick = { expanded = !expanded }, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    if (expanded) "Collapse answer" else "Expand answer", tint = MaterialTheme.colorScheme.primary)
            }
            if (expanded) Text(answer, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
