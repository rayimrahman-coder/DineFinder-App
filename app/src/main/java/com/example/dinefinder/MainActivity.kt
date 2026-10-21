package com.example.dinefinder

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dinefinder.ar.ARDiscoveryActivity
import com.example.dinefinder.settings.SettingsScreen
import com.example.dinefinder.settings.ApiKeyStore
import com.example.dinefinder.settings.HelpScreen
import com.example.dinefinder.settings.UserPreferences
import com.example.dinefinder.ui.theme.DineFinderTheme
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private enum class AppScreen { SIGN_IN, HOME, PROFILE, SETTINGS, HELP }

class MainActivity : ComponentActivity() {
    private val preferences by lazy { UserPreferences(this) }
    private val apiKeyStore by lazy { ApiKeyStore(this) }
    private var settingsRequested by mutableStateOf(false)
    private var returnToNavigation by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settingsRequested = savedInstanceState?.getBoolean("settings_requested")
            ?: intent.getBooleanExtra("open_settings", false)
        returnToNavigation = savedInstanceState?.getBoolean("return_to_navigation")
            ?: intent.getBooleanExtra("return_to_navigation", false)
        intent.removeExtra("open_settings")
        intent.removeExtra("return_to_navigation")
        enableEdgeToEdge()

        setContent {
            var email by remember { mutableStateOf(preferences.currentEmail()) }
            var name by remember { mutableStateOf(preferences.userName(email)) }
            var darkMode by remember { mutableStateOf(preferences.isDarkMode()) }
            var apiKey by remember { mutableStateOf(apiKeyStore.apiKey()) }
            SideEffect {
                val barStyle = if (darkMode) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = barStyle, navigationBarStyle = barStyle)
            }
            DineFinderTheme(darkTheme = darkMode) {
                var screen by rememberSaveable {
                    mutableStateOf(
                        if (email.isBlank()) AppScreen.SIGN_IN else AppScreen.HOME
                    )
                }
                var helpReturnScreen by rememberSaveable { mutableStateOf(AppScreen.PROFILE) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            val savedEmail = preferences.currentEmail()
                            if (email != savedEmail) {
                                screen = if (savedEmail.isBlank()) AppScreen.SIGN_IN else AppScreen.HOME
                            }
                            email = savedEmail
                            name = preferences.userName(savedEmail)
                            darkMode = preferences.isDarkMode()
                            apiKey = apiKeyStore.apiKey()
                        }
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                LaunchedEffect(settingsRequested, email) {
                    if (settingsRequested && email.isNotBlank()) {
                        screen = AppScreen.SETTINGS
                        settingsRequested = false
                    }
                }
                BackHandler(enabled = screen in listOf(AppScreen.PROFILE, AppScreen.SETTINGS, AppScreen.HELP)) {
                    when (screen) {
                        AppScreen.HELP -> screen = helpReturnScreen
                        AppScreen.SETTINGS -> if (returnToNavigation) finish() else screen = AppScreen.PROFILE
                        else -> screen = AppScreen.HOME
                    }
                }

                val appViewModel: AppViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
                LaunchedEffect(appViewModel.state.account) {
                    if (appViewModel.state.account != null) {
                        val authEmail = appViewModel.state.account!!.email
                        preferences.signIn(authEmail)
                        email = authEmail
                        name = preferences.userName(authEmail)
                        screen = AppScreen.HOME
                    }
                }

                when (screen) {
                    AppScreen.SIGN_IN -> SignInScreen(
                        onSignIn = { enteredEmail, password ->
                            when {
                                !android.util.Patterns.EMAIL_ADDRESS.matcher(enteredEmail).matches() ->
                                    "Enter a valid email address."
                                password.length < 6 ->
                                    "Password must contain at least 6 characters."
                                else -> {
                                    email = enteredEmail.trim()
                                    appViewModel.authenticate(email, password, create = false)
                                    // Error messages are handled via view model state, but for UI feedback we can return message
                                    appViewModel.state.message
                                }
                            }
                        },
                        onGuest = {
                            email = "guest@dinefinder.local"
                            preferences.signIn(email)
                            name = preferences.userName(email)
                            screen = AppScreen.HOME
                        }
                    )

                    AppScreen.HOME -> MainScaffold(
                        selectedTab = AppScreen.HOME,
                        onHome = { screen = AppScreen.HOME },
                        onProfile = { screen = AppScreen.PROFILE }
                    ) { paddingValues ->
                        HomeScreen(
                            name = name,
                            onScan = { startActivity(Intent(this, ARDiscoveryActivity::class.java)) },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    AppScreen.PROFILE -> MainScaffold(
                        selectedTab = AppScreen.PROFILE,
                        onHome = { screen = AppScreen.HOME },
                        onProfile = { screen = AppScreen.PROFILE }
                    ) { paddingValues ->
                        ProfileScreen(
                            email = email,
                            name = name,
                            onSettings = { screen = AppScreen.SETTINGS },
                            onHelp = {
                                helpReturnScreen = AppScreen.PROFILE
                                screen = AppScreen.HELP
                            },
                            onLogout = {
                                preferences.signOut()
                                email = ""
                                name = preferences.userName(email)
                                screen = AppScreen.SIGN_IN
                            },
                            modifier = Modifier.padding(paddingValues)
                        )
                    }

                    AppScreen.SETTINGS -> SettingsScreen(
                        userName = name,
                        darkMode = darkMode,
                        onBack = { if (returnToNavigation) finish() else screen = AppScreen.PROFILE },
                        onSaveUserName = { enteredName ->
                            val error = preferences.saveUserName(email, enteredName)
                            if (error == null) {
                                name = preferences.userName(email)
                                Toast.makeText(this, R.string.settings_username_saved, Toast.LENGTH_SHORT).show()
                            }
                            error
                        },
                        onDarkModeChange = { enabled ->
                            preferences.setDarkMode(enabled)
                            darkMode = enabled
                        },
                        apiKey = apiKey,
                        onSaveApiKey = { enteredKey ->
                            val error = apiKeyStore.saveApiKey(enteredKey)
                            if (error == null) {
                                apiKey = apiKeyStore.apiKey()
                                Toast.makeText(this, R.string.settings_api_key_saved, Toast.LENGTH_SHORT).show()
                            }
                            error
                        },
                        onClearApiKey = {
                            val error = apiKeyStore.clearApiKey()
                            if (error == null) {
                                apiKey = ""
                                Toast.makeText(this, R.string.settings_api_key_removed, Toast.LENGTH_SHORT).show()
                            }
                            error
                        },
                        onApiKeyHelp = {
                            helpReturnScreen = AppScreen.SETTINGS
                            screen = AppScreen.HELP
                        },
                        backLabel = if (returnToNavigation) getString(R.string.settings_back_to_navigation) else null
                    )

                    AppScreen.HELP -> HelpScreen(
                        onBack = { screen = helpReturnScreen },
                        onOpenSettings = { screen = AppScreen.SETTINGS }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("open_settings", false)) {
            settingsRequested = true
            returnToNavigation = intent.getBooleanExtra("return_to_navigation", false)
            intent.removeExtra("open_settings")
            intent.removeExtra("return_to_navigation")
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("settings_requested", settingsRequested)
        outState.putBoolean("return_to_navigation", returnToNavigation)
        super.onSaveInstanceState(outState)
    }
}

@Composable
private fun SignInScreen(
    onSignIn: (String, String) -> String?,
    onGuest: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Restaurant,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(29.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "DineFinder",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Create an account or sign in to continue.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f)
        )
        Spacer(Modifier.height(30.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it; errorMessage = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Email address") },
            leadingIcon = {
                Icon(Icons.Default.MailOutline, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                autoCorrectEnabled = false
            ),
            colors = signInFieldColors()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it; errorMessage = null },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Password") },
            leadingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null)
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            colors = signInFieldColors()
        )
        if (errorMessage != null) {
            Text(
                text = errorMessage.orEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { errorMessage = onSignIn(email.trim(), password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(13.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text("Continue", fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(15.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Text(
                "  OR  ",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
        }
        Spacer(Modifier.height(14.dp))
        OutlinedButton(
            onClick = onGuest,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(13.dp)
        ) {
            Text("Continue as guest", fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = "By continuing, you agree to DineFinder's\nTerms of Service and Privacy Policy.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 22.dp),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.52f)
        )
    }
}

@Composable
private fun signInFieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedBorderColor = MaterialTheme.colorScheme.primary
)

@Composable
private fun MainScaffold(
    selectedTab: AppScreen,
    onHome: () -> Unit,
    onProfile: () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .navigationBarsPadding()
                    .padding(top = 10.dp, bottom = 9.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                BottomTab(
                    label = "Home",
                    icon = Icons.Default.Home,
                    selected = selectedTab == AppScreen.HOME,
                    onClick = onHome
                )
                BottomTab(
                    label = "Profile",
                    icon = Icons.Default.Person,
                    selected = selectedTab == AppScreen.PROFILE,
                    onClick = onProfile
                )
            }
        },
        content = content
    )
}

@Composable
private fun BottomTab(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(21.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HomeScreen(
    name: String,
    onScan: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 22.dp, end = 22.dp, top = 28.dp, bottom = 18.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Hello, $name",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Let's find nearby restaurants using AR navigation.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.67f)
            )
        }

        Spacer(Modifier.weight(1f))
        RadarGraphic(
            modifier = Modifier
                .fillMaxWidth()
                .height(290.dp)
        )
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onScan,
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF090A09), contentColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = null,
                    tint = Color(0xFF20C775),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(3.dp))
                Text("Scan for Restaurant", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.weight(0.5f))
    }
}

@Composable
private fun RadarGraphic(modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.onSurface
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val maxRadius = size.minDimension * .47f
            for (ring in 1..4) {
                drawCircle(
                    color = Color(0xFF18B96B).copy(alpha = if (ring == 4) .12f else .065f),
                    radius = maxRadius * ring / 4f,
                    center = center,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
                )
            }
            drawCircle(
                color = Color(0xFF19BD70),
                radius = 5.dp.toPx(),
                center = Offset(center.x - maxRadius * .58f, center.y + maxRadius * .38f)
            )
            drawCircle(
                color = dotColor,
                radius = 4.dp.toPx(),
                center = Offset(center.x + maxRadius * .57f, center.y - maxRadius * .56f)
            )
            drawCircle(
                color = Color(0xFFC7D1CA),
                radius = 3.dp.toPx(),
                center = Offset(center.x + maxRadius * .77f, center.y + maxRadius * .05f)
            )
        }
        Surface(
            modifier = Modifier.size(84.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = "Searching nearby",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    email: String,
    name: String,
    onSettings: () -> Unit,
    onHelp: () -> Unit,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .padding(14.dp)
                                .size(30.dp)
                        )
                    }
                    Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        text = if (email == "guest@dinefinder.local") "Guest profile" else email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .65f)
                    )
                }
            }
        }

        item {
            ProfileAction(
                icon = Icons.Default.Settings,
                label = "Settings",
                onClick = onSettings
            )
        }
        item {
            ProfileAction(
                icon = Icons.AutoMirrored.Filled.HelpOutline,
                label = "Help",
                onClick = onHelp
            )
        }
        item {
            ProfileAction(
                icon = Icons.AutoMirrored.Filled.Logout,
                label = "Logout",
                destructive = true,
                onClick = onLogout
            )
        }
    }
}

@Composable
private fun ProfileAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    supportingText: String? = null,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 13.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp).size(18.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                )
                if (supportingText != null) {
                    Text(
                        text = supportingText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f)
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    DineFinderTheme {
        HomeScreen(name = "User", onScan = {})
    }
}
