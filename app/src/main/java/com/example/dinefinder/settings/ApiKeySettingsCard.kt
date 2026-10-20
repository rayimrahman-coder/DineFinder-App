package com.example.dinefinder.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.dinefinder.R

@Composable
internal fun ApiKeySettingsCard(
    apiKey: String,
    onSave: (String) -> String?,
    onRemove: () -> String?,
    onHelp: () -> Unit
) {
    // Keep draft credentials out of the saved-instance-state Bundle.
    var draftKey by remember(apiKey) { mutableStateOf(apiKey) }
    var keyVisible by remember { mutableStateOf(false) }
    var keyEdited by remember { mutableStateOf(false) }
    var storageError by remember { mutableStateOf<String?>(null) }
    val inputError = apiKeyValidationError(draftKey)
    val shownError = storageError ?: inputError.takeIf { keyEdited }
    val canSave = inputError == null && draftKey.trim() != apiKey
    val focusManager = LocalFocusManager.current
    val saveKey: () -> Unit = {
        if (canSave) {
            storageError = onSave(draftKey)
            if (storageError == null) {
                keyVisible = false
                keyEdited = false
                focusManager.clearFocus()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(R.string.settings_google_maps),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(R.string.settings_api_key_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = draftKey,
                    onValueChange = {
                        draftKey = it
                        keyEdited = true
                        storageError = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.settings_api_key)) },
                    singleLine = true,
                    isError = shownError != null,
                    supportingText = {
                        Text(shownError ?: stringResource(
                            if (apiKey.isBlank()) R.string.settings_api_key_not_saved
                            else R.string.settings_api_key_saved_hint
                        ))
                    },
                    visualTransformation = if (keyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { saveKey() }),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                if (keyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (keyVisible) R.string.settings_hide_api_key else R.string.settings_show_api_key
                                )
                            )
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                )
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = saveKey, enabled = canSave, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_save_api_key))
                    }
                    TextButton(
                        enabled = apiKey.isNotBlank(),
                        onClick = {
                            storageError = onRemove()
                            if (storageError == null) {
                                draftKey = ""
                                keyVisible = false
                                keyEdited = false
                                focusManager.clearFocus()
                            }
                        }
                    ) {
                        Text(stringResource(R.string.settings_remove_api_key))
                    }
                }
                TextButton(onClick = onHelp) {
                    Text(stringResource(R.string.settings_api_key_tutorial))
                }
            }
        }
    }
}
