package com.focusguard.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    // Observe permission changes when resuming the screen
    DisposableEffect(context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkPermissions()
            }
        }
        val lifecycle = (context as androidx.activity.ComponentActivity).lifecycle
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                SettingsSectionTitle("Permissions")
            }

            item {
                PermissionItem(
                    title = "Usage Access",
                    description = "Required to track screen time accurately.",
                    isGranted = state.hasUsageStatsPermission,
                    onClick = { viewModel.requestUsageStatsPermission(context) }
                )
            }

            item {
                PermissionItem(
                    title = "Accessibility Service",
                    description = "Required to enforce app limits locally.",
                    isGranted = state.hasAccessibilityPermission,
                    onClick = { viewModel.requestAccessibilityPermission(context) }
                )
            }

            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle("Preferences")
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.Block,
                    title = "Enable App Blocking",
                    description = "Toggle to temporarily pause all limits.",
                    isChecked = state.isBlockingEnabled,
                    onCheckedChange = { viewModel.toggleBlocking(it) }
                )
            }

            item {
                SettingsSwitchItem(
                    icon = Icons.Default.DarkMode,
                    title = "Dark Mode",
                    description = "Change the application theme.",
                    isChecked = state.isDarkMode,
                    onCheckedChange = { viewModel.toggleDarkMode(it) }
                )
            }
            
            item {
                Divider(modifier = Modifier.padding(vertical = 8.dp))
                SettingsSectionTitle("About")
                
                SettingsActionItem(
                    icon = Icons.Default.Info,
                    title = "Version",
                    subtitle = "1.0.0 (MVP)"
                ) {}
            }
        }
    }
}

@Composable
fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)
    )
}

@Composable
fun PermissionItem(
    title: String,
    description: String,
    isGranted: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable(!isGranted) { onClick() },
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        },
        trailingContent = {
            if (!isGranted) {
                TextButton(onClick = onClick) {
                    Text("Grant")
                }
            } else {
                Text("Granted", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

@Composable
fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    description: String,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = {
            Switch(
                checked = isChecked,
                onCheckedChange = onCheckedChange
            )
        }
    )
}

@Composable
fun SettingsActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) }
    )
}
