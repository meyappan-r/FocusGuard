package com.focusguard.ui.theme

import androidx.compose.ui.graphics.Color

// ── Primary tonal greens ──────────────────────────────────────────
val FG_Seg0        = Color(0xFF0D3B2E)   // darkest — primary action / deep heading
val FG_Seg1        = Color(0xFF1A5C45)   // dark — icons, accent text, FAB
val FG_Seg2        = Color(0xFF2E7D5A)   // mid — progress bars, active rings
val FG_Seg3        = Color(0xFF4A9B72)   // light-mid — secondary highlights
val FG_SegOther    = Color(0xFFADD4BC)   // lightest segment / "Other"
val FG_Track       = Color(0xFFD8EDE0)   // progress track (light)
val FG_TrackDark   = Color(0xFF243A2C)   // progress track (dark)

// ── Backgrounds ───────────────────────────────────────────────────
val FG_BgLight     = Color(0xFFF4F6F4)
val FG_BgDark      = Color(0xFF0F1A14)
val FG_HeaderLight = Color(0xFFE8F3EC)
val FG_HeaderDark  = Color(0xFF162419)
val FG_CardLight   = Color(0xFFFFFFFF)
val FG_CardDark    = Color(0xFF1A2E22)
val FG_SurfLight   = Color(0xFFEEF4F0)   // row backgrounds, outlined cards
val FG_SurfDark    = Color(0xFF223328)

// ── Text ──────────────────────────────────────────────────────────
val FG_TxtPriLight = Color(0xFF0D2B1E)
val FG_TxtPriDark  = Color(0xFFD8EEE0)
val FG_TxtSecLight = Color(0xFF5A7A68)
val FG_TxtSecDark  = Color(0xFF6E9B80)

// ── Semantic ──────────────────────────────────────────────────────
val FG_Error       = Color(0xFFB85450)
val FG_ErrorLight  = Color(0xFFFFEDED)

// ── Helper: resolve light/dark pair ──────────────────────────────
object FGColors {
    fun bg(dark: Boolean)     = if (dark) FG_BgDark     else FG_BgLight
    fun header(dark: Boolean) = if (dark) FG_HeaderDark else FG_HeaderLight
    fun card(dark: Boolean)   = if (dark) FG_CardDark   else FG_CardLight
    fun surf(dark: Boolean)   = if (dark) FG_SurfDark   else FG_SurfLight
    fun txtPri(dark: Boolean) = if (dark) FG_TxtPriDark else FG_TxtPriLight
    fun txtSec(dark: Boolean) = if (dark) FG_TxtSecDark else FG_TxtSecLight
    fun track(dark: Boolean)  = if (dark) FG_TrackDark  else FG_Track
}
