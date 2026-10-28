package com.example.dinefinder.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WelcomeScreen(
    busy: Boolean,
    message: String?,
    onAuthenticate: (String, String, Boolean) -> Unit,
    onGuest: () -> Unit,
) {
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords remain in transient composition state, never saved to a Bundle.
    var password by remember { mutableStateOf("") }
    var createAccount by rememberSaveable { mutableStateOf(false) }
    var showPassword by remember { mutableStateOf(false) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var showPrivacy by remember { mutableStateOf(false) }
    val focus = LocalFocusManager.current

    fun submit() {
        if (busy) return
        emailError = if (EMAIL_PATTERN.matches(email.trim())) null else "Enter a valid email address."
        passwordError = when {
            password.isEmpty() -> "Enter your password."
            createAccount && password.length < 8 -> "Use at least 8 characters."
            else -> null
        }
        if (emailError == null && passwordError == null) {
            focus.clearFocus()
            onAuthenticate(email.trim(), password, createAccount)
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Color.White) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(12.dp))
                Column(
                    modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .background(DineMint, RoundedCornerShape(22.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Restaurant, null, tint = DineGreen, modifier = Modifier.size(35.dp))
                    }
                    Spacer(Modifier.height(20.dp))
                    Text("DineFinder", style = MaterialTheme.typography.headlineLarge, color = DineInk)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        if (createAccount) "Create your account to get started."
                        else "Create an account or sign in to continue.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DineMuted,
                    )
                    Spacer(Modifier.height(30.dp))
                    val fieldColors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFFEEF1EA),
                        unfocusedContainerColor = Color(0xFFEEF1EA),
                        disabledContainerColor = Color(0xFFEEF1EA),
                        unfocusedBorderColor = Color(0xFFCACFC8),
                        focusedBorderColor = DineGreen,
                        errorContainerColor = Color(0xFFEEF1EA),
                    )
                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; emailError = null },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Email address", color = DineMuted) },
                        leadingIcon = { Icon(Icons.Outlined.Email, null, tint = DineMuted) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        isError = emailError != null,
                        supportingText = if (emailError != null) { { Text(emailError.orEmpty()) } } else null,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; passwordError = null },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        singleLine = true,
                        shape = RoundedCornerShape(18.dp),
                        colors = fieldColors,
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text("Password", color = DineMuted) },
                        leadingIcon = { Icon(Icons.Default.Lock, null, tint = DineMuted) },
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }, enabled = !busy) {
                                Icon(
                                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    if (showPassword) "Hide password" else "Show password",
                                    tint = DineMuted,
                                )
                            }
                        },
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }),
                        isError = passwordError != null,
                        supportingText = if (passwordError != null) { { Text(passwordError.orEmpty()) } } else null,
                    )
                    if (!message.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            message,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            style = MaterialTheme.typography.bodyMedium,
                            color = DineGreen,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = { submit() },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DineGreen),
                    ) {
                        if (busy) CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        ) else Text(if (createAccount) "Create account" else "Continue", fontWeight = FontWeight.SemiBold)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCDD1CB))
                        Text("OR", modifier = Modifier.padding(horizontal = 10.dp), color = DineMuted, fontSize = 12.sp)
                        HorizontalDivider(modifier = Modifier.weight(1f), color = Color(0xFFCDD1CB))
                    }
                    OutlinedButton(
                        onClick = onGuest,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                        enabled = !busy,
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Color(0xFFCACFC8)),
                    ) {
                        Text("Continue as guest", color = DineMuted, fontWeight = FontWeight.SemiBold)
                    }
                    TextButton(
                        onClick = {
                            createAccount = !createAccount
                            emailError = null
                            passwordError = null
                        },
                        enabled = !busy,
                    ) {
                        Text(
                            if (createAccount) "Already have an account? Sign in" else "New here? Create an account",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = DineGreen,
                        )
                    }
                }
                TextButton(onClick = { showPrivacy = true }, modifier = Modifier.padding(top = 22.dp)) {
                    Text("Privacy & data", color = DineMuted, fontSize = 12.sp)
                }
            }
        }
    }
    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            title = { Text("Privacy & data") },
            text = {
                Text(
                    "Camera images stay on your device. Your location is sent to Supabase " +
                        "when you search for nearby restaurants, and to the FOSSGIS walking-route " +
                        "service when you select Navigate.\n\n" +
                        "Demo mode uses fictional restaurants and simulated movement. " +
                        "You can change location and camera permissions in Android settings.",
                )
            },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Close") } },
        )
    }
}

@Composable
fun HomeScreen(displayName: String, isDemo: Boolean, onScan: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = DinePaper) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = maxHeight)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Hello, ${displayName.ifBlank { "Guest" }}",
                        style = MaterialTheme.typography.headlineLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Let's find nearby restaurants using AR navigation.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = DineMuted,
                    )
                    if (isDemo) {
                        Spacer(Modifier.height(8.dp))
                        Text("Demo mode · sample restaurants", color = DineMuted, fontSize = 12.sp)
                    }
                }
                DiscoveryIllustration(
                    Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(vertical = 24.dp),
                )
                Surface(
                    onClick = onScan,
                    modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF090B0A),
                    shadowElevation = 7.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(Icons.Default.CameraAlt, null, tint = Color(0xFF1FBE77), modifier = Modifier.size(29.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("Scan for Restaurant", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryIllustration(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.aspectRatio(1f).semantics {
            contentDescription = "Restaurant discovery illustration"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val outerRadius = size.minDimension * 0.48f
            listOf(1f, 0.76f, 0.52f).forEachIndexed { index, fraction ->
                drawCircle(
                    color = DineMint.copy(alpha = if (index == 0) 0.9f else 0.52f),
                    radius = outerRadius * fraction,
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
            drawCircle(DineInk, radius = 4.5.dp.toPx(), center = Offset(size.width * 0.77f, size.height * 0.23f))
            drawCircle(Color(0xFF1FBE77), radius = 5.5.dp.toPx(), center = Offset(size.width * 0.24f, size.height * 0.70f))
            drawCircle(Color(0xFFC6D3CA), radius = 3.5.dp.toPx(), center = Offset(size.width * 0.87f, size.height * 0.53f))
        }
        Surface(shape = CircleShape, color = Color.White, shadowElevation = 10.dp) {
            Box(modifier = Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier.size(66.dp).background(DineMint, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LocationOn, null, tint = DineGreen, modifier = Modifier.size(37.dp))
                }
            }
        }
    }
}

@Composable
fun ProfileScreen(
    displayName: String,
    email: String?,
    isGuest: Boolean,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onLogout: () -> Unit,
    photoPath: String? = null,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(26.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 26.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        UserProfileAvatar(photoPath)
                        Spacer(Modifier.height(13.dp))
                        Text(
                            displayName.ifBlank { "Guest" },
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (isGuest) "Guest profile" else (email ?: "DineFinder member"),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item { ProfileAction("Settings", Icons.Default.Settings, onSettings) }
            item { ProfileAction("Help", Icons.Outlined.HelpOutline, onHelp) }
            item { ProfileAction("Logout", Icons.AutoMirrored.Filled.ExitToApp, onLogout, destructive = true) }
        }
    }
}

@Composable
private fun ProfileAction(label: String, icon: ImageVector, onClick: () -> Unit, destructive: Boolean = false) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color(0xFFE5E8E1)),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.3.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(42.dp).background(
                    if (destructive) Color(0xFFF9DEDB) else Color(0xFFEEF1EA),
                    CircleShape,
                ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = if (destructive) DineRed else DineMuted, modifier = Modifier.size(23.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = if (destructive) DineRed else MaterialTheme.colorScheme.onSurface,
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color(0xFFA1AAA3), modifier = Modifier.size(25.dp))
        }
    }
}

private val EMAIL_PATTERN = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
