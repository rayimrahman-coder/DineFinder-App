package com.example.dinefinder.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dinefinder.R
import com.example.dinefinder.ui.theme.DineFinderTheme

@Composable
internal fun HelpScreen(onBack: () -> Unit, onOpenSettings: () -> Unit) {
    var appRestrictionHelpExpanded by rememberSaveable { mutableStateOf(false) }
    val appRestrictionState = stringResource(
        if (appRestrictionHelpExpanded) R.string.help_details_expanded else R.string.help_details_collapsed
    )
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val linkError = stringResource(R.string.help_link_unavailable)
    val openLink: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
            .onFailure { Toast.makeText(context, linkError, Toast.LENGTH_SHORT).show() }
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
                            contentDescription = stringResource(R.string.help_back)
                        )
                    }
                    Text(
                        stringResource(R.string.help_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp).semantics { heading() }
                    )
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("help_list")
                .padding(padding)
                .consumeWindowInsets(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HelpHeading(stringResource(R.string.help_api_tutorial_title))
                    Text(
                        stringResource(R.string.help_api_tutorial_intro),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                HelpCard {
                    HelpHeading(stringResource(R.string.help_before_start_title))
                    HelpBody(stringResource(R.string.help_before_start_body))
                }
            }
            item {
                TutorialStep(1, stringResource(R.string.help_step_console_title), stringResource(R.string.help_step_console_body)) {
                    HelpLink(R.string.help_open_console) { openLink(CLOUD_CONSOLE_URL) }
                }
            }
            item {
                TutorialStep(2, stringResource(R.string.help_step_project_title), stringResource(R.string.help_step_project_body)) {
                    HelpLink(R.string.help_create_project) { openLink(CREATE_PROJECT_URL) }
                }
            }
            item {
                TutorialStep(3, stringResource(R.string.help_step_billing_title), stringResource(R.string.help_step_billing_body)) {
                    HelpLink(R.string.help_open_billing) { openLink(BILLING_CONSOLE_URL) }
                    HelpLink(R.string.help_billing_and_pricing) { openLink(ROUTES_BILLING_URL) }
                    HelpLink(R.string.help_places_billing_and_pricing) { openLink(PLACES_BILLING_URL) }
                }
            }
            item {
                TutorialStep(4, stringResource(R.string.help_step_enable_title), stringResource(R.string.help_step_enable_body)) {
                    HelpLink(R.string.help_enable_routes) { openLink(ROUTES_LIBRARY_URL) }
                    HelpLink(R.string.help_enable_places) { openLink(PLACES_LIBRARY_URL) }
                }
            }
            item {
                TutorialStep(5, stringResource(R.string.help_step_key_title), stringResource(R.string.help_step_key_body)) {
                    HelpLink(R.string.help_open_credentials) { openLink(CREDENTIALS_URL) }
                }
            }
            item {
                TutorialStep(6, stringResource(R.string.help_step_restrict_title), stringResource(R.string.help_step_restrict_body)) {
                    TextButton(
                        onClick = { appRestrictionHelpExpanded = !appRestrictionHelpExpanded },
                        modifier = Modifier.fillMaxWidth().semantics { stateDescription = appRestrictionState }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                stringResource(R.string.help_app_restriction_help_title),
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (appRestrictionHelpExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null
                            )
                        }
                    }
                    if (appRestrictionHelpExpanded) {
                        HelpBody(stringResource(R.string.help_app_restriction_help_body))
                    }
                    HelpLink(R.string.help_key_security) { openLink(KEY_SECURITY_URL) }
                }
            }
            item {
                TutorialStep(7, stringResource(R.string.help_step_save_title), stringResource(R.string.help_step_save_body)) {
                    Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.help_open_settings))
                    }
                }
            }
            item {
                TutorialStep(8, stringResource(R.string.help_step_test_title), stringResource(R.string.help_step_test_body))
            }
            item {
                HelpCard {
                    HelpHeading(stringResource(R.string.help_cost_title))
                    HelpBody(stringResource(R.string.help_cost_body))
                    HelpLink(R.string.help_budget_alerts) { openLink(BUDGETS_URL) }
                }
            }
            item {
                HelpCard {
                    HelpHeading(stringResource(R.string.help_troubleshooting_title))
                    HelpTip(R.string.help_error_key_title, R.string.help_error_key_body)
                    HelpTip(R.string.help_error_billing_title, R.string.help_error_billing_body)
                    HelpTip(R.string.help_error_quota_title, R.string.help_error_quota_body)
                    HelpTip(R.string.help_error_route_title, R.string.help_error_route_body)
                    HelpLink(R.string.help_official_setup) { openLink(ROUTES_SETUP_URL) }
                    HelpLink(R.string.help_official_places_setup) { openLink(PLACES_SETUP_URL) }
                }
            }
            item {
                HelpCard {
                    HelpHeading(stringResource(R.string.help_model_credits_title))
                    HelpBody(stringResource(R.string.help_direction_arrow_credit))
                    HelpLink(R.string.help_direction_arrow_author) { openLink(DIRECTION_ARROW_AUTHOR_URL) }
                    HelpLink(R.string.help_direction_arrow_source) { openLink(DIRECTION_ARROW_SOURCE_URL) }
                    HelpLink(R.string.help_direction_arrow_license) { openLink(DIRECTION_ARROW_LICENSE_URL) }
                }
            }
        }
    }
}

private const val CLOUD_CONSOLE_URL = "https://console.cloud.google.com/"
private const val CREATE_PROJECT_URL = "https://console.cloud.google.com/projectcreate"
private const val BILLING_CONSOLE_URL = "https://console.cloud.google.com/billing"
private const val ROUTES_LIBRARY_URL = "https://console.cloud.google.com/apis/library/routes.googleapis.com"
private const val PLACES_LIBRARY_URL = "https://console.cloud.google.com/apis/library/places.googleapis.com"
private const val CREDENTIALS_URL = "https://console.cloud.google.com/apis/credentials"
private const val ROUTES_SETUP_URL = "https://developers.google.com/maps/documentation/routes/get-api-key"
private const val ROUTES_BILLING_URL = "https://developers.google.com/maps/documentation/routes/usage-and-billing"
private const val PLACES_BILLING_URL = "https://developers.google.com/maps/documentation/places/web-service/usage-and-billing"
private const val PLACES_SETUP_URL = "https://developers.google.com/maps/documentation/places/web-service/get-api-key"
private const val KEY_SECURITY_URL = "https://developers.google.com/maps/api-security-best-practices#secure-client-side-web-service-calls"
private const val BUDGETS_URL = "https://cloud.google.com/billing/docs/how-to/budgets"
private const val DIRECTION_ARROW_AUTHOR_URL = "https://sketchfab.com/Dare0"
private const val DIRECTION_ARROW_SOURCE_URL = "https://sketchfab.com/3d-models/direction-arrow-6ef46718c7b242e39fcad7f27ee858a5"
private const val DIRECTION_ARROW_LICENSE_URL = "https://creativecommons.org/licenses/by/4.0/"

@Composable
private fun TutorialStep(number: Int, title: String, body: String, actions: @Composable () -> Unit = {}) {
    HelpCard {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(32.dp).clearAndSetSemantics { },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Text(number.toString(), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                }
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.help_step_progress, number, 8),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    title,
                    modifier = Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        HelpBody(body)
        actions()
    }
}

@Composable
private fun HelpCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun HelpHeading(text: String) {
    Text(
        text,
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun HelpBody(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun HelpTip(title: Int, body: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        HelpBody(stringResource(body))
    }
}

@Composable
private fun HelpLink(label: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) {
        Text(stringResource(label))
    }
}

@Preview(showBackground = true)
@Composable
private fun HelpLightPreview() {
    DineFinderTheme { HelpScreen({}, {}) }
}

@Preview(showBackground = true)
@Composable
private fun HelpDarkPreview() {
    DineFinderTheme(darkTheme = true) { HelpScreen({}, {}) }
}
