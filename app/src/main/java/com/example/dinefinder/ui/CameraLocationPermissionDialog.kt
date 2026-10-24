package com.example.dinefinder.ui

import android.view.WindowManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider

private val PermissionGreen = Color(0xFF075548)
private val PermissionInk = Color(0xFF101820)
private val PermissionMuted = Color(0xFF606870)

/** Explanation only. The existing ActivityResult launcher owns runtime permissions. */
@Composable
internal fun CameraLocationPermissionDialog(onDismiss: () -> Unit, onContinue: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            window?.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(.55f)
        }
        var entered by remember { mutableStateOf(false) }
        // Compose's animation clock respects the system animator duration scale.
        val entrance by animateFloatAsState(if (entered) 1f else 0f,
            animationSpec = tween(180, easing = FastOutSlowInEasing), label = "Permission dialog entrance")
        LaunchedEffect(Unit) { entered = true }
        BoxWithConstraints(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(vertical = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            val dialogWidth = (maxWidth * .88f).coerceAtMost(440.dp)
            Surface(
                modifier = Modifier.width(dialogWidth).heightIn(max = maxHeight)
                    .graphicsLayer { alpha = entrance; scaleX = .96f + .04f * entrance; scaleY = scaleX }
                    .testTag("camera_location_permission_dialog"),
                color = Color.White, shape = RoundedCornerShape(28.dp),
                shadowElevation = 8.dp, tonalElevation = 0.dp,
            ) {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PermissionIllustration()
                    Spacer(Modifier.height(20.dp))
                    Text("Allow camera & location", color = PermissionInk,
                        fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.W700,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().semantics { heading() })
                    Spacer(Modifier.height(16.dp))
                    Text("To find nearby restaurants and guide you with AR navigation, we need access to your camera and location.",
                        color = PermissionMuted, fontSize = 16.sp, lineHeight = 24.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(28.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = CircleShape, border = BorderStroke(1.dp, Color(0xFFE0E4E7)),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFF7F8F9), contentColor = PermissionGreen),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        ) { Text("Not now", fontSize = 16.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Medium) }
                        Button(
                            onClick = onContinue,
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = PermissionGreen, contentColor = Color.White),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 12.dp),
                        ) { Text("Continue", fontSize = 16.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionIllustration() {
    // Both symbols repeat the title, so exclude the decoration from TalkBack.
    Box(Modifier.size(width = 200.dp, height = 116.dp).clearAndSetSemantics { }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(112.dp).background(Color(0xFFF3F8F7), CircleShape))
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 3.dp.toPx()
            drawLine(Color(0xFF83DCC7), Offset(size.width * .02f, size.height * .64f), Offset(size.width * .09f, size.height * .64f), stroke, StrokeCap.Round)
            drawLine(Color(0xFF83DCC7), Offset(size.width * .06f, size.height * .78f), Offset(size.width * .10f, size.height * .72f), stroke, StrokeCap.Round)
            drawLine(Color(0xFFB0D6F3), Offset(size.width * .88f, size.height * .20f), Offset(size.width * .92f, size.height * .13f), stroke, StrokeCap.Round)
            drawLine(Color(0xFFB0D6F3), Offset(size.width * .93f, size.height * .30f), Offset(size.width * .99f, size.height * .27f), stroke, StrokeCap.Round)
        }
        Box(Modifier.offset(x = (-32).dp).size(88.dp).background(Color(0xFFD9F4EC), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, tint = PermissionGreen, modifier = Modifier.size(44.dp))
        }
        Box(Modifier.offset(x = 36.dp).size(80.dp).background(Color(0xFFE1EEFC).copy(alpha = .94f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color(0xFF2E6BAB), modifier = Modifier.size(46.dp))
        }
    }
}
