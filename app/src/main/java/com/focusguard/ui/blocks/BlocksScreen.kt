package com.focusguard.ui.blocks

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focusguard.ui.home.formatTime
import com.focusguard.ui.home.rememberAppIcon
import com.focusguard.ui.theme.FGColors
import com.focusguard.ui.theme.FG_Error
import com.focusguard.ui.theme.FG_ErrorLight
import com.focusguard.ui.theme.FG_HeaderLight
import com.focusguard.ui.theme.FG_Seg0
import com.focusguard.ui.theme.FG_Seg1
import com.focusguard.ui.theme.FG_Seg2
import com.focusguard.ui.theme.FG_Seg3
import com.focusguard.ui.theme.FG_SegOther
import com.focusguard.ui.theme.FG_SurfLight
import com.focusguard.ui.theme.FG_Track
import com.focusguard.ui.theme.FG_TxtPriLight
import com.focusguard.ui.theme.FG_TxtSecLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocksScreen(viewModel: BlocksViewModel, isDarkMode: Boolean = false) {
    val state    by viewModel.state.collectAsState()
    val bg       = FGColors.bg(isDarkMode)
    val hdrBg    = FGColors.header(isDarkMode)
    val cardBg   = FGColors.card(isDarkMode)
    val surfBg   = FGColors.surf(isDarkMode)
    val txtPri   = FGColors.txtPri(isDarkMode)
    val txtSec   = FGColors.txtSec(isDarkMode)
    val track    = FGColors.track(isDarkMode)

    var selectedAppForEdit by remember { mutableStateOf<BlockAppState?>(null) }

    LaunchedEffect(Unit) { viewModel.loadApps() }

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Shield, contentDescription = null,
                            tint = FG_Seg1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("FocusGuard", fontWeight = FontWeight.Bold,
                            fontSize = 18.sp, color = txtPri)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = hdrBg)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Set daily time limits. Apps are blocked when the limit is reached.",
                fontSize = 13.sp,
                color = txtSec,
                modifier = Modifier.padding(bottom = 14.dp)
            )

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.apps) { appState ->
                    BlockAppRow(
                        appState  = appState,
                        isDarkMode = isDarkMode,
                        cardBg    = cardBg,
                        surfBg    = surfBg,
                        txtPri    = txtPri,
                        txtSec    = txtSec,
                        track     = track,
                        onEditClick   = { selectedAppForEdit = appState },
                        onToggleBlock = {
                            if (appState.limitMillis > 0)
                                viewModel.setLimit(appState.appUsage.packageName, -1L)
                            else
                                selectedAppForEdit = appState
                        }
                    )
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }

    selectedAppForEdit?.let { app ->
        EditLimitDialog(
            appState  = app,
            txtPri    = FGColors.txtPri(isDarkMode),
            onDismiss = { selectedAppForEdit = null },
            onSave    = { limitMinutes ->
                viewModel.setLimit(app.appUsage.packageName, limitMinutes * 60 * 1000L)
                selectedAppForEdit = null
            }
        )
    }
}

@Composable
fun BlockAppRow(
    appState: BlockAppState,
    isDarkMode: Boolean,
    cardBg: Color,
    surfBg: Color,
    txtPri: Color,
    txtSec: Color,
    track: Color,
    onEditClick: () -> Unit,
    onToggleBlock: () -> Unit
) {
    val isBlockable = appState.appUsage.isBlockable
    val isLimited   = appState.limitMillis > 0
    val usageTime   = appState.appUsage.usageTimeMillis
    val percentage  = if (isLimited) (usageTime.toFloat() / appState.limitMillis).coerceIn(0f, 1f) else 0f
    val icon        = rememberAppIcon(appState.appUsage.packageName)
    val barColor    = if (percentage >= 1f) FG_Error else FG_Seg2

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surfBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon.toBitmap(72, 72).asImageBitmap(),
                            contentDescription = appState.appUsage.appName,
                            modifier = Modifier.size(38.dp)
                        )
                    } else {
                        Text(appState.appUsage.appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold, color = FG_Seg1, fontSize = 16.sp)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = appState.appUsage.appName,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = txtPri
                        )
                        if (!isBlockable) {
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                color = FG_Seg0.copy(alpha = 0.10f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    "Protected",
                                    fontSize = 9.sp,
                                    color = FG_Seg0,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text("Used: ${formatTime(usageTime)}", fontSize = 12.sp, color = txtSec)
                }

                Switch(
                    checked  = isLimited,
                    onCheckedChange = { if (isBlockable) onToggleBlock() },
                    enabled  = isBlockable,
                    colors   = SwitchDefaults.colors(
                        checkedThumbColor  = FG_Seg1,
                        checkedTrackColor  = FG_Seg3.copy(alpha = 0.4f)
                    )
                )
            }

            if (isLimited && isBlockable) {
                Spacer(Modifier.height(12.dp))
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
                            Text("Daily Limit", fontSize = 12.sp, color = txtSec)
                            Text(formatTime(appState.limitMillis),
                                fontSize = 12.sp, fontWeight = FontWeight.Bold, color = txtPri)
                        }
                        Spacer(Modifier.height(5.dp))
                        LinearProgressIndicator(
                            progress  = { percentage },
                            modifier  = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                            color     = barColor,
                            trackColor = track
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    IconButton(onClick = onEditClick) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit",
                            tint = FG_Seg1, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EditLimitDialog(
    appState: BlockAppState,
    txtPri: Color,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit
) {
    var limitInput by remember {
        val init = if (appState.limitMillis > 0) appState.limitMillis / 60000 else 30L
        mutableStateOf(init.toString())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title   = { Text("Set Limit — ${appState.appUsage.appName}",
            fontWeight = FontWeight.Bold, color = txtPri) },
        text    = {
            Column {
                Text("Enter daily time limit in minutes:", color = txtPri)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value         = limitInput,
                    onValueChange = { if (it.all { c -> c.isDigit() }) limitInput = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine    = true,
                    suffix        = { Text("min") },
                    colors        = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = FG_Seg1,
                        unfocusedBorderColor = FG_Seg3.copy(alpha = 0.5f)
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(limitInput.toLongOrNull() ?: 0L) }) {
                Text("Save", color = FG_Seg1, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = txtPri) }
        }
    )
}
