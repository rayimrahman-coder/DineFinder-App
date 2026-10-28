package com.example.dinefinder.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dinefinder.ui.UserProfileAvatar
import androidx.compose.material3.TextButton
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import com.example.dinefinder.R
import com.example.dinefinder.ui.theme.DineFinderTheme

@Composable
internal fun SettingsScreen(
    userName: String,
    darkMode: Boolean,
    onBack: () -> Unit,
    onSaveUserName: (String) -> String?,
    onDarkModeChange: (Boolean) -> Unit,
    apiKey: String = "",
    onSaveApiKey: (String) -> String? = { null },
    onClearApiKey: () -> String? = { null },
    onApiKeyHelp: () -> Unit = {},
    showApiKeySettings: Boolean = true,
    backLabel: String? = null,
    photoPath: String? = null,
    photoBusy: Boolean = false,
    photoError: String? = null,
    onChangePhoto: () -> Unit = {},
    onRemovePhoto: () -> Unit = {},
) {
    var editedName by rememberSaveable(userName) { mutableStateOf(userName) }
    var nameEdited by rememberSaveable { mutableStateOf(false) }
    var saveError by rememberSaveable { mutableStateOf<String?>(null) }
    val validationError = userNameValidationError(editedName)
    val shownError = saveError ?: validationError.takeIf { nameEdited }
    val canSave = validationError == null && editedName.trim() != userName
    val focusManager = LocalFocusManager.current
    val saveName: () -> Unit = {
        if (canSave) {
            saveError = onSaveUserName(editedName)
            if (saveError == null) focusManager.clearFocus()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = backLabel ?: stringResource(R.string.settings_back)
                        )
                    }
                    Text(
                        stringResource(R.string.settings_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_list")
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item {
                Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Profile picture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        UserProfileAvatar(photoPath)
                        Text("Choose a photo to personalise your profile. Saved on this phone.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = onChangePhoto, enabled = !photoBusy, modifier = Modifier.fillMaxWidth()) {
                            Text(if (photoBusy) "Saving photo…" else "Change photo")
                        }
                        if (photoPath != null) TextButton(onClick = onRemovePhoto, enabled = !photoBusy) { Text("Remove photo") }
                        photoError?.let { Text(it, color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_profile),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                stringResource(R.string.settings_username_description),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            OutlinedTextField(
                                value = editedName,
                                onValueChange = {
                                    editedName = it
                                    nameEdited = true
                                    saveError = null
                                },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.settings_username)) },
                                singleLine = true,
                                isError = shownError != null,
                                supportingText = {
                                    Text(shownError ?: stringResource(R.string.settings_username_limit, USER_NAME_MAX_LENGTH))
                                },
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(onDone = { saveName() })
                            )
                            Button(
                                onClick = saveName,
                                enabled = canSave,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.settings_save_username))
                            }
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        stringResource(R.string.settings_appearance),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = darkMode,
                                    role = Role.Switch,
                                    onValueChange = onDarkModeChange
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    stringResource(R.string.settings_dark_mode),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.settings_dark_mode_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = darkMode, onCheckedChange = null)
                        }
                    }
                }
            }
            if (showApiKeySettings) item {
                ApiKeySettingsCard(
                    apiKey = apiKey,
                    onSave = onSaveApiKey,
                    onRemove = onClearApiKey,
                    onHelp = onApiKeyHelp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsLightPreview() {
    DineFinderTheme {
        SettingsScreen("User", false, {}, { null }, {})
    }
}

@Preview(showBackground = true)
@Composable
private fun SettingsDarkPreview() {
    DineFinderTheme(darkTheme = true) {
        SettingsScreen("User", true, {}, { null }, {})
    }
}
