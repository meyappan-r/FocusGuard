package com.focusguard.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Color

// ── Primary tonal greens ──────────────────────────────────────────
val FG_Seg0        = Color(0xFF0D3B2E)   // darkest — primary action / deep heading
val FG_Seg1        = Color(0xFF1A5C45)   // dark — icons, accent text, FAB
val FG_Seg2        = Color(0xFF2E7D5A)   // mid — progress bars, active rings
val FG_Seg3        = Color(0xFF4A9B72)   // light-mid — secondary highlights
val FG_SegOther    = Color(0xFFADD4BC)   // lightest segment / "Other"
val FG_Track       = Color(0xFFD8EDE0)   // progress track (light)
val FG_TrackDark   = Color(0xFF1E3025)   // progress track (dark)

// ── Backgrounds ───────────────────────────────────────────────────
val FG_BgLight     = Color(0xFFF4F6F4)
val FG_BgDark      = Color(0xFF000000)   // AMOLED pure black
val FG_HeaderLight = Color(0xFFE8F3EC)
val FG_HeaderDark  = Color(0xFF0A120E)   // very dark green tint
val FG_CardLight   = Color(0xFFFFFFFF)
val FG_CardDark    = Color(0xFF121E17)   // elevated above pure black
val FG_SurfLight   = Color(0xFFEEF4F0)   // row backgrounds, outlined cards
val FG_SurfDark    = Color(0xFF1A2A20)   // higher elevation

// ── Text ──────────────────────────────────────────────────────────
val FG_TxtPriLight = Color(0xFF0D2B1E)
val FG_TxtPriDark  = Color(0xFFE8F3EC)   // high contrast white-green
val FG_TxtSecLight = Color(0xFF5A7A68)
val FG_TxtSecDark  = Color(0xFF8BAE99)   // readable secondary

// ── Semantic ──────────────────────────────────────────────────────
val FG_Error       = Color(0xFFB85450)
val FG_ErrorLight  = Color(0xFFFFEDED)

// ── Helper: resolve light/dark pair with smooth transition ────────
object FGColors {
    @Composable fun bg(dark: Boolean)     = animateColorAsState(if (dark) FG_BgDark     else FG_BgLight, tween(250)).value
    @Composable fun header(dark: Boolean) = animateColorAsState(if (dark) FG_HeaderDark else FG_HeaderLight, tween(250)).value
    @Composable fun card(dark: Boolean)   = animateColorAsState(if (dark) FG_CardDark   else FG_CardLight, tween(250)).value
    @Composable fun surf(dark: Boolean)   = animateColorAsState(if (dark) FG_SurfDark   else FG_SurfLight, tween(250)).value
    @Composable fun txtPri(dark: Boolean) = animateColorAsState(if (dark) FG_TxtPriDark else FG_TxtPriLight, tween(250)).value
    @Composable fun txtSec(dark: Boolean) = animateColorAsState(if (dark) FG_TxtSecDark else FG_TxtSecLight, tween(250)).value
    @Composable fun track(dark: Boolean)  = animateColorAsState(if (dark) FG_TrackDark  else FG_Track, tween(250)).value
}

/** One switch style for the whole app (Settings, Blocks, Sessions form and app picker). */
@Composable
fun fgSwitchColors(): SwitchColors = SwitchDefaults.colors(
    checkedThumbColor = FG_Seg1,
    checkedTrackColor = FG_Seg3.copy(alpha = 0.4f)
)

/** Accent for text and icons: the deep green on light backgrounds, a lighter green on dark ones. */
@Composable
fun fgAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) FG_Seg3 else FG_Seg1
