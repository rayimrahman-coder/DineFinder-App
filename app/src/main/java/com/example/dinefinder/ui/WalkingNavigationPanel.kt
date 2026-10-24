package com.example.dinefinder.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinefinder.core.RouteProgress
import com.example.dinefinder.device.NavigationSpeechStatus
import com.example.dinefinder.device.NavigationVoiceControls

@Composable
internal fun WalkingNavigationPanel(
    cameraEnabled: Boolean, onCameraToggle: () -> Unit,
    voiceEnabled: Boolean, onVoiceToggle: () -> Unit, voice: NavigationVoiceControls,
    progress: RouteProgress?, busy: Boolean, locationError: String?,
    arEnabled: Boolean, arStatus: String?, onArToggle: () -> Unit, isDemo: Boolean,
    onRecalculate: () -> Unit, onDirections: () -> Unit, onEnd: () -> Unit,
    problems: List<String>, onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    demoControls: @Composable () -> Unit = {},
) {
    val context = LocalContext.current
    Column(modifier.fillMaxSize().testTag("walking_navigation_panel")
        .verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(onClick = onCameraToggle, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DiscoveryMint, contentColor = DiscoveryGreen)) {
                        Icon(if (cameraEnabled) Icons.Default.VideocamOff else Icons.Default.Videocam, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (cameraEnabled) "Turn camera off" else "Turn camera on")
                    }
                    Button(onClick = onVoiceToggle, modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen, contentColor = Color.White)) {
                        Icon(if (voiceEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (voiceEnabled) "Mute voice" else "Enable voice")
                    }
                }
                Text(when {
                    !voiceEnabled -> "Voice guidance is muted"
                    voice.status == NavigationSpeechStatus.Unavailable -> "Voice unavailable. Enable an English voice in Android text-to-speech settings."
                    voice.status == NavigationSpeechStatus.Starting -> "Preparing voice guidance…"
                    busy -> "Updating route…"
                    !voice.active -> "Voice guidance is paused"
                    else -> "Voice guidance is on · adjust your media volume"
                }, color = DiscoveryMuted, fontSize = 13.sp, lineHeight = 19.sp)
                TextButton(onClick = voice.repeat, enabled = voice.active, colors = ButtonDefaults.textButtonColors(contentColor = DiscoveryGreen)) { Text("Repeat instruction") }
                if (voice.status == NavigationSpeechStatus.Unavailable) TextButton(onClick = {
                    val intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (runCatching { context.startActivity(intent) }.isFailure)
                        runCatching { context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("Open speech settings", color = DiscoveryGreen) }
            }
        }
        if (cameraEnabled) Spacer(Modifier.height(160.dp))
        else {
            Surface(color = DiscoveryMint.copy(alpha = .45f), shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(72.dp).background(Color.White, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.DirectionsWalk, null, Modifier.size(40.dp), tint = DiscoveryGreen)
                    }
                    Text("Camera off · GPS guidance", color = DiscoveryGreen, fontWeight = FontWeight.Bold, fontSize = 20.sp, lineHeight = 26.sp)
                    Text("Keep DineFinder open for spoken walking directions.", color = DiscoveryMuted, fontSize = 14.sp, lineHeight = 21.sp)
                }
            }
        }
        Surface(color = Color.White, contentColor = DiscoveryInk, shape = RoundedCornerShape(24.dp), shadowElevation = 4.dp) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Current instruction", color = DiscoveryMuted, fontSize = 13.sp)
                Text(if (locationError != null || progress == null) "Waiting for a fresh GPS location"
                    else if (progress.isOffRoute) "Off route · recalculate before continuing"
                    else progress.instruction?.text ?: "Follow the walking route",
                    color = DiscoveryInk, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
                if (progress != null && locationError == null) Text("${scanDistance(progress.remainingMeters)} remaining", color = DiscoveryGreen, fontSize = 16.sp)
                if (progress?.isOffRoute == true) Button(onClick = onRecalculate, enabled = !busy,
                    colors = ButtonDefaults.buttonColors(containerColor = DiscoveryGreen)) { Text("Recalculate") }
                if (cameraEnabled && !isDemo) {
                    Text(arStatus ?: "GPS + compass guidance", color = DiscoveryMuted, fontSize = 12.sp)
                    TextButton(onClick = onArToggle) { Text(if (arEnabled) "Use compass camera" else "Try local 3D AR", color = DiscoveryGreen) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(onClick = onDirections, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, DiscoveryGreen)) { Text("Directions", color = DiscoveryGreen) }
                    OutlinedButton(onClick = onEnd, modifier = Modifier.weight(1f), border = BorderStroke(1.dp, DiscoveryBorder)) { Text("End", color = DiscoveryGreen) }
                }
                Text("Pause to check your surroundings before following a direction.", color = DiscoveryMuted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        demoControls()
        if (problems.isNotEmpty()) Surface(color = Color(0xFFFBECCF), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(12.dp)) {
                Text(problems.joinToString("\n"), color = Color(0xFF77552D), fontSize = 13.sp)
                TextButton(onClick = onRetry, enabled = !busy) { Text("Retry sensors and search", color = DiscoveryGreen) }
            }
        }
    }
}
