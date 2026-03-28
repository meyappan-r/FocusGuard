package com.focusguard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focusguard.ui.theme.FGColors
import com.focusguard.ui.theme.FG_Error
import com.focusguard.ui.theme.FG_Seg0
import com.focusguard.ui.theme.FG_Seg1
import com.focusguard.ui.theme.FG_Seg2
import com.focusguard.ui.theme.FG_Seg3
import com.focusguard.ui.extensions.clickableScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel, 
    isDarkMode: Boolean = false,
    onNavigateToPrivacy: () -> Unit
) {
    val state   by viewModel.state.collectAsState()
    val context  = LocalContext.current

    val bg      = FGColors.bg(isDarkMode)
    val hdrBg   = FGColors.header(isDarkMode)
    val cardBg  = FGColors.card(isDarkMode)
    val surfBg  = FGColors.surf(isDarkMode)
    val txtPri  = FGColors.txtPri(isDarkMode)
    val txtSec  = FGColors.txtSec(isDarkMode)

    DisposableEffect(context) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME)
                viewModel.checkPermissions()
        }
        val lifecycle = (context as androidx.activity.ComponentActivity).lifecycle
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Permissions ─────────────────────────────────────
            item {
                SectionLabel("PERMISSIONS", txtSec)
            }
            item {
                SettingsCard(cardBg = cardBg) {
                    PermissionRow(
                        icon      = if (state.hasUsageStatsPermission) Icons.Default.CheckCircle else Icons.Default.Lock,
                        iconTint  = if (state.hasUsageStatsPermission) FG_Seg1 else FG_Error,
                        title     = "Usage Access",
                        subtitle  = "Required to track screen time.",
                        isGranted = state.hasUsageStatsPermission,
                        txtPri    = txtPri,
                        txtSec    = txtSec,
                        onClick   = { viewModel.requestUsageStatsPermission(context) }
                    )
                    HorizontalDivider(color = FGColors.track(isDarkMode), thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    PermissionRow(
                        icon      = if (state.hasAccessibilityPermission) Icons.Default.CheckCircle else Icons.Default.Lock,
                        iconTint  = if (state.hasAccessibilityPermission) FG_Seg1 else FG_Error,
                        title     = "Accessibility Service",
                        subtitle  = "Required to enforce app limits.",
                        isGranted = state.hasAccessibilityPermission,
                        txtPri    = txtPri,
                        txtSec    = txtSec,
                        onClick   = { viewModel.requestAccessibilityPermission(context) }
                    )
                }
            }

            // ── Preferences ──────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionLabel("PREFERENCES", txtSec) }
            item {
                SettingsCard(cardBg = cardBg) {
                    ToggleRow(
                        icon     = Icons.Default.Block,
                        title    = "Enable App Blocking",
                        subtitle = "Pause all limits temporarily.",
                        checked  = state.isBlockingEnabled,
                        txtPri   = txtPri,
                        txtSec   = txtSec,
                        onToggle = { viewModel.toggleBlocking(it) }
                    )
                }
            }

            // ── Appearance ───────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionLabel("APPEARANCE", txtSec) }
            item {
                SegmentedThemeSelector(
                    selectedMode = state.themeMode,
                    onModeSelected = { viewModel.setThemeMode(it) },
                    cardBg = cardBg,
                    txtPri = txtPri,
                    isDarkMode = isDarkMode
                )
            }

            // ── About ─────────────────────────────────────────────
            item { Spacer(Modifier.height(20.dp)) }
            item { SectionLabel("ABOUT", txtSec) }
            item {
                SettingsCard(cardBg = cardBg) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null,
                            tint = FG_Seg1, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Version", fontWeight = FontWeight.Medium,
                                fontSize = 14.sp, color = txtPri)
                            Text("1.0.0 (MVP)", fontSize = 12.sp, color = txtSec)
                        }
                    }
                    HorizontalDivider(color = FGColors.track(isDarkMode), thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    ActionRow(
                        icon     = Icons.Default.SystemUpdate,
                        title    = "Check for Updates",
                        subtitle = "You are up to date.",
                        txtPri   = txtPri,
                        txtSec   = txtSec,
                        onClick  = { /* Handle update check */ }
                    )
                    HorizontalDivider(color = FGColors.track(isDarkMode), thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 16.dp))
                    ActionRow(
                        icon     = Icons.Default.PrivacyTip,
                        title    = "Privacy Statement",
                        subtitle = "Read how we protect your data.",
                        txtPri   = txtPri,
                        txtSec   = txtSec,
                        onClick  = onNavigateToPrivacy
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Shared sub-composables ────────────────────────────────────────

@Composable
private fun SectionLabel(text: String, color: Color) {
    Text(
        text          = text,
        fontSize      = 11.sp,
        fontWeight    = FontWeight.SemiBold,
        color         = color,
        letterSpacing = 1.4.sp,
        modifier      = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
private fun SettingsCard(cardBg: Color, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Column(content = content)
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    isGranted: Boolean,
    txtPri: Color,
    txtSec: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale(enabled = !isGranted) { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = iconTint,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = txtPri)
            Text(subtitle, fontSize = 12.sp, color = txtSec)
        }
        if (!isGranted) {
            TextButton(onClick = onClick,
                colors = ButtonDefaults.textButtonColors(contentColor = FG_Seg1)) {
                Text("Grant", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            }
        } else {
            Text("Granted", fontSize = 12.sp,
                color = FG_Seg1, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SegmentedThemeSelector(
    selectedMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    cardBg: Color,
    txtPri: Color,
    isDarkMode: Boolean
) {
    val options = listOf(
        ThemeMode.LIGHT  to Pair("Light",  Icons.Default.LightMode),
        ThemeMode.SYSTEM to Pair("System", Icons.Default.Settings),
        ThemeMode.DARK   to Pair("Dark",   Icons.Default.DarkMode)
    )
    val selectedIndex = options.indexOfFirst { it.first == selectedMode }.coerceAtLeast(0)
    val trackBg  = FGColors.track(isDarkMode)
    val txtSec   = FGColors.txtSec(isDarkMode)

    // One single float animation drives the sliding pill
    val pillOffset by androidx.compose.animation.core.animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness    = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "pillOffset"
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackBg)
            .padding(4.dp)
    ) {
        val pillWidth = maxWidth / options.size

        // Sliding pill background
        Box(
            modifier = Modifier
                .offset(x = pillWidth * pillOffset)
                .width(pillWidth)
                .fillMaxHeight()
                .clip(RoundedCornerShape(7.dp))
                .background(cardBg)
        )

        // Labels row on top
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                val mode  = option.first
                val label = option.second.first
                val icon  = option.second.second
                val isSelected = selectedIndex == index

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector        = icon,
                        contentDescription = label,
                        modifier           = Modifier.size(15.dp),
                        tint               = if (isSelected) FG_Seg1 else txtSec
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text       = label,
                        fontSize   = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color      = if (isSelected) txtPri else txtSec
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    txtPri: Color,
    txtSec: Color,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FG_Seg1,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = txtPri)
            Text(subtitle, fontSize = 12.sp, color = txtSec)
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(
                checkedThumbColor  = FG_Seg1,
                checkedTrackColor  = FG_Seg3.copy(alpha = 0.4f)
            )
        )
    }
}

@Composable
private fun ActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    txtPri: Color,
    txtSec: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableScale { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = FG_Seg1,
            modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium,
                fontSize = 14.sp, color = txtPri)
            Text(subtitle, fontSize = 12.sp, color = txtSec)
        }
    }
}
