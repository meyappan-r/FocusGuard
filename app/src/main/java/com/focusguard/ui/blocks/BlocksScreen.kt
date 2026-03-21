package com.focusguard.ui.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.ui.home.formatTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(viewModel: BlocksViewModel) {
    val state by viewModel.state.collectAsState()
    var selectedAppForEdit by remember { mutableStateOf<BlockAppState?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("App Limits", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Manage your daily application limits. Apps will be blocked when the limit is exceeded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.apps) { appState ->
                    BlockAppRow(
                        appState = appState,
                        onEditClick = { selectedAppForEdit = appState },
                        onToggleBlock = {
                            if (appState.limitMillis > 0) {
                                viewModel.setLimit(appState.appUsage.packageName, -1L) // Remove limit
                            } else {
                                selectedAppForEdit = appState // Open dialog to set new limit
                            }
                        }
                    )
                }
            }
        }
    }

    selectedAppForEdit?.let { app ->
        EditLimitDialog(
            appState = app,
            onDismiss = { selectedAppForEdit = null },
            onSave = { limitMinutes ->
                viewModel.setLimit(app.appUsage.packageName, limitMinutes * 60 * 1000L)
                selectedAppForEdit = null
            }
        )
    }
}

@Composable
fun BlockAppRow(
    appState: BlockAppState,
    onEditClick: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val isBlockable = appState.appUsage.isBlockable
    val isLimited = appState.limitMillis > 0
    val usageTime = appState.appUsage.usageTimeMillis
    val percentage = if (isLimited && appState.limitMillis > 0) {
        (usageTime.toFloat() / appState.limitMillis).coerceIn(0f, 1f)
    } else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // App Icon Placeholder
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = appState.appUsage.appName.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appState.appUsage.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (!isBlockable) {
                            Spacer(modifier = Modifier.width(6.dp))
                            // Shield badge — emergency/essential app
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shield,
                                        contentDescription = "Protected",
                                        tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "Protected",
                                        fontSize = 9.sp,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Used: ${formatTime(usageTime)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!isBlockable) {
                        Text(
                            text = "Cannot be blocked — emergency access",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                }

                Switch(
                    checked = isLimited,
                    onCheckedChange = { if (isBlockable) onToggleBlock() },
                    enabled = isBlockable
                )
            }

            if (isLimited && isBlockable) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Daily Limit",
                                style = MaterialTheme.typography.labelMedium
                            )
                            Text(
                                text = formatTime(appState.limitMillis),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { percentage },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = if (percentage >= 1f) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit Limit",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun EditLimitDialog(
    appState: BlockAppState,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var limitInput by remember { 
        val initialMinutes = if (appState.limitMillis > 0) appState.limitMillis / 60000 else 30L
        mutableStateOf(initialMinutes.toString()) 
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Limit for ${appState.appUsage.appName}") },
        text = {
            Column {
                Text("Enter daily time limit in minutes:")
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = limitInput,
                    onValueChange = { if (it.all { char -> char.isDigit() }) limitInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    suffix = { Text("min") }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val minutes = limitInput.toLongOrNull() ?: 0L
                    onSave(minutes)
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
