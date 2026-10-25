package com.focusguard.ui.home

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import com.focusguard.ui.theme.fgAccent
import com.focusguard.ui.extensions.clickableScale
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.focusguard.domain.model.AppUsage
import com.focusguard.domain.model.WeeklyDayUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

// ─────────────────────────────────────────────────────────
//  Palette
// ─────────────────────────────────────────────────────────
private val BgLight       = Color(0xFFF4F6F4)
private val BgDark        = Color(0xFF0F1A14)
private val HeaderTint    = Color(0xFFE8F3EC)
private val HeaderTintDark= Color(0xFF162419)
private val TextPri       = Color(0xFF0D2B1E)
private val TextPriDark   = Color(0xFFD8EEE0)
private val TextSec       = Color(0xFF5A7A68)
private val TextSecDark   = Color(0xFF6E9B80)
private val CardBgLight   = Color(0xFFFFFFFF)
private val CardBgDark    = Color(0xFF1A2E22)
private val Seg0          = Color(0xFF0D3B2E)
private val Seg1          = Color(0xFF1A5C45)
private val Seg2          = Color(0xFF2E7D5A)
private val Seg3          = Color(0xFF4A9B72)
private val SegOther      = Color(0xFFADD4BC)
private val Track         = Color(0xFFD8EDE0)
private val TrackDark     = Color(0xFF243A2C)
private val BarActive     = Color(0xFF1A5C45)
private val BarNormal     = Color(0xFFADD4BC)

private val segColors = listOf(Seg0, Seg1, Seg2, Seg3, SegOther)

// ─────────────────────────────────────────────────────────
//  Root
// ─────────────────────────────────────────────────────────
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) { viewModel.loadUsage() }

    val bg     = if (isDarkMode) BgDark     else BgLight
    val cardBg = if (isDarkMode) CardBgDark else CardBgLight
    val txtPri = if (isDarkMode) TextPriDark else TextPri
    val txtSec = if (isDarkMode) TextSecDark else TextSec
    val track  = if (isDarkMode) TrackDark   else Track
    val hdrBg  = if (isDarkMode) HeaderTintDark else HeaderTint

    val displayedApps = if (state.showAll) state.appUsage else state.appUsage.take(3)
    val totalTime = state.screenTimeSummary.totalScreenTime

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {

        // ── Header ──────────────────────────────────────────────
        item {
            FocusGuardHeader(
                isDarkMode    = isDarkMode,
                onThemeToggle = onThemeToggle,
                bgColor       = hdrBg,
                textColor     = txtPri
            )
        }

        // ── Usage Overview label ─────────────────────────────────
        item {
            Text(
                text     = "USAGE OVERVIEW",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color    = txtSec,
                letterSpacing = 1.6.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp, bottom = 12.dp)
                    .wrapContentWidth(Alignment.CenterHorizontally)
            )
        }

        // ── Tab toggle ───────────────────────────────────────────
        item {
            TabToggle(
                selected    = state.selectedTab,
                onSelect    = { viewModel.selectTab(it) },
                bgColor     = cardBg,
                activeColor = Seg1,
                textColor   = txtSec
            )
            Spacer(Modifier.height(28.dp))
        }

        // ── Today / Weekly content ───────────────────────────────
        item {
            AnimatedContent(
                targetState    = state.selectedTab,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
                label = "tab_content"
            ) { tab ->
                when (tab) {
                    HomeTab.TODAY -> TodayContent(
                        state        = state,
                        totalTime    = totalTime,
                        track        = track,
                        txtPri       = txtPri,
                        txtSec       = txtSec,
                        cardBg       = cardBg,
                        isDarkMode   = isDarkMode,
                        displayedApps = displayedApps,
                        onToggleAll  = { viewModel.toggleShowAll() }
                    )
                    HomeTab.WEEKLY -> WeeklyContent(
                        weeklyData    = state.weeklyData,
                        weeklyAverage = state.weeklyAverage,
                        selectedDayIndex = state.selectedDayIndex,
                        selectedDayApps  = state.selectedDayApps,
                        totalTime        = totalTime,
                        txtPri        = txtPri,
                        txtSec        = txtSec,
                        cardBg        = cardBg,
                        track         = track,
                        onDayTap      = { viewModel.selectWeeklyDay(it) }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Header
// ─────────────────────────────────────────────────────────
@Composable
fun FocusGuardHeader(
    isDarkMode: Boolean,
    onThemeToggle: () -> Unit,
    bgColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(bgColor)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector         = Icons.Default.Shield,
            contentDescription  = "FocusGuard",
            tint                = Seg1,
            modifier            = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text       = "FocusGuard",
            fontWeight = FontWeight.Bold,
            fontSize   = 18.sp,
            color      = textColor
        )
    }
}

// ─────────────────────────────────────────────────────────
//  Tab Toggle
// ─────────────────────────────────────────────────────────
@Composable
fun TabToggle(
    selected: HomeTab,
    onSelect: (HomeTab) -> Unit,
    bgColor: Color,
    activeColor: Color,
    textColor: Color
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 24.dp)
            .clip(RoundedCornerShape(50))
            .background(bgColor)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        HomeTab.entries.forEach { tab ->
            val isActive = selected == tab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isActive) activeColor else Color.Transparent)
                    .clickableScale { onSelect(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text       = tab.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                    fontSize   = 14.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (isActive) Color.White else textColor
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  TODAY content
// ─────────────────────────────────────────────────────────
@Composable
private fun TodayContent(
    state: HomeState,
    totalTime: Long,
    track: Color,
    txtPri: Color,
    txtSec: Color,
    cardBg: Color,
    isDarkMode: Boolean,
    displayedApps: List<com.focusguard.domain.model.AppUsage>,
    onToggleAll: () -> Unit
) {
    Column {
        // Ring
        SegmentedRing(
            segments  = state.ringSegments,
            totalTime = totalTime,
            trackColor = track,
            txtPri    = txtPri,
            txtSec    = txtSec
        )

        Spacer(Modifier.height(20.dp))

        // Peak Distraction
        state.peakDistraction?.let { peak ->
            PeakDistractionCard(
                appName    = peak.appName,
                packageName = state.appUsage.firstOrNull { it.appName == peak.appName }?.packageName ?: "",
                usageMillis = peak.usageTime,
                cardBg     = cardBg,
                txtPri     = txtPri,
                txtSec     = txtSec
            )
        }

        Spacer(Modifier.height(20.dp))

        // Most Used header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text       = "MOST USED",
                fontSize   = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color      = txtSec,
                letterSpacing = 1.4.sp
            )
            Text(
                text       = if (state.showAll) "Show Less" else "View All",
                fontSize   = 13.sp,
                fontWeight = FontWeight.Medium,
                color      = fgAccent(),
                modifier   = Modifier.clickableScale { onToggleAll() }
            )
        }
        Spacer(Modifier.height(10.dp))

        displayedApps.forEach { app ->
            val pct = if (totalTime > 0) app.usageTimeMillis.toFloat() / totalTime else 0f
            AppUsageRow(
                packageName  = app.packageName,
                appName      = app.appName,
                usageMillis  = app.usageTimeMillis,
                percentage   = pct,
                track        = track,
                txtPri       = txtPri,
                txtSec       = txtSec,
                cardBg       = cardBg
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Segmented ring
// ─────────────────────────────────────────────────────────
@Composable
fun SegmentedRing(
    segments: List<RingSegment>,
    totalTime: Long,
    trackColor: Color,
    txtPri: Color,
    txtSec: Color
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
    ) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = Stroke(width = 26.dp.toPx(), cap = StrokeCap.Round)
            val inset  = 13.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            // Track
            drawArc(
                color      = trackColor,
                startAngle = 120f,
                sweepAngle = 300f,
                useCenter  = false,
                topLeft    = topLeft,
                size       = arcSize,
                style      = stroke
            )

            if (segments.isNotEmpty()) {
                var cursor = 120f
                segments.forEach { seg ->
                    val color = segColors.getOrElse(seg.colorIndex) { SegOther }
                    drawArc(
                        color      = color,
                        startAngle = cursor,
                        sweepAngle = seg.sweepAngle,
                        useCenter  = false,
                        topLeft    = topLeft,
                        size       = arcSize,
                        style      = stroke
                    )
                    cursor += seg.sweepAngle + 2f   // 2° gap
                }
            }
        }

        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text       = "TOTAL TODAY",
                fontSize   = 10.sp,
                fontWeight = FontWeight.Medium,
                color      = txtSec,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text       = formatTime(totalTime),
                fontSize   = 32.sp,
                fontWeight = FontWeight.Bold,
                color      = txtPri
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Peak Distraction card
// ─────────────────────────────────────────────────────────
@Composable
fun PeakDistractionCard(
    appName: String,
    packageName: String,
    usageMillis: Long,
    cardBg: Color,
    txtPri: Color,
    txtSec: Color
) {
    val icon = rememberAppIcon(packageName)

    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape     = RoundedCornerShape(20.dp),
        colors    = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = "Peak Distraction",
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 15.sp,
                    color      = txtPri
                )
                Surface(
                    color = Seg0.copy(alpha = 0.10f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text       = "INSIGHT",
                        fontSize   = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color      = fgAccent(),
                        modifier   = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        letterSpacing = 0.8.sp
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Track),
                    contentAlignment = Alignment.Center
                ) {
                    if (icon != null) {
                        Image(
                            bitmap             = icon.toBitmap(72, 72).asImageBitmap(),
                            contentDescription = appName,
                            modifier           = Modifier.size(36.dp)
                        )
                    } else {
                        Text(
                            appName.take(1).uppercase(),
                            fontWeight = FontWeight.Bold,
                            color      = Seg1,
                            fontSize   = 16.sp
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        text       = appName,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                        color      = txtPri
                    )
                    Text(
                        text     = "${formatTime(usageMillis)} today",
                        fontSize = 12.sp,
                        color    = txtSec
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text       = formatTime(usageMillis),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 18.sp,
                    color      = txtPri
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  App usage row
// ─────────────────────────────────────────────────────────
@Composable
fun AppUsageRow(
    packageName: String,
    appName: String,
    usageMillis: Long,
    percentage: Float,
    track: Color,
    txtPri: Color,
    txtSec: Color,
    cardBg: Color
) {
    val icon = rememberAppIcon(packageName)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cardBg),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap             = icon.toBitmap(72, 72).asImageBitmap(),
                    contentDescription = appName,
                    modifier           = Modifier.size(36.dp)
                )
            } else {
                Text(
                    text       = appName.take(1).uppercase(),
                    fontWeight = FontWeight.Bold,
                    fontSize   = 16.sp,
                    color      = Seg1
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text       = appName,
                    fontWeight = FontWeight.Medium,
                    fontSize   = 14.sp,
                    color      = txtPri,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                    modifier   = Modifier.weight(1f)
                )
                Text(
                    text       = formatTime(usageMillis),
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 13.sp,
                    color      = txtPri
                )
            }
            Spacer(Modifier.height(5.dp))
            LinearProgressIndicator(
                progress     = { percentage.coerceIn(0f, 1f) },
                modifier     = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color        = Seg2,
                trackColor   = track
            )
        }
    }
}

// ─────────────────────────────────────────────────────────
//  WEEKLY content
// ─────────────────────────────────────────────────────────
@Composable
private fun WeeklyContent(
    weeklyData: List<WeeklyDayUsage>,
    weeklyAverage: Long,
    selectedDayIndex: Int?,
    selectedDayApps: List<AppUsage>,
    totalTime: Long,
    txtPri: Color,
    txtSec: Color,
    cardBg: Color,
    track: Color,
    onDayTap: (Int) -> Unit
) {
    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
        // Average
        Text(
            text       = "WEEKLY AVERAGE",
            fontSize   = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color      = txtSec,
            letterSpacing = 1.4.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text       = formatTime(weeklyAverage),
            fontSize   = 36.sp,
            fontWeight = FontWeight.Bold,
            color      = txtPri
        )
        Text(
            text     = "per day",
            fontSize = 13.sp,
            color    = txtSec
        )

        Spacer(Modifier.height(28.dp))

        if (weeklyData.isNotEmpty()) {
            WeeklyBarChart(
                days             = weeklyData,
                selectedDayIndex = selectedDayIndex,
                txtSec           = txtSec,
                onDayTap         = onDayTap
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Seg2, strokeWidth = 2.dp)
            }
        }

        // ── Day breakdown list ──────────────────────────────
        if (selectedDayIndex != null) {
            Spacer(Modifier.height(24.dp))

            val dayName = weeklyData.getOrNull(selectedDayIndex)?.dayLabel ?: ""
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dayName.uppercase() + " — APP USAGE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = txtSec,
                    letterSpacing = 1.3.sp
                )
                Text(
                    text = "✕",
                    fontSize = 14.sp,
                    color = txtSec,
                    modifier = Modifier.clickableScale { onDayTap(selectedDayIndex) }
                )
            }

            Spacer(Modifier.height(10.dp))

            if (selectedDayApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(60.dp),
                    contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Seg2, strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp))
                }
            } else {
                val dayTotal = selectedDayApps.sumOf { it.usageTimeMillis }
                selectedDayApps.forEach { app ->
                    val pct = if (dayTotal > 0) app.usageTimeMillis.toFloat() / dayTotal else 0f
                    AppUsageRow(
                        packageName = app.packageName,
                        appName     = app.appName,
                        usageMillis = app.usageTimeMillis,
                        percentage  = pct,
                        track       = track,
                        txtPri      = txtPri,
                        txtSec      = txtSec,
                        cardBg      = cardBg
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Weekly bar chart — rounded pill bars
// ─────────────────────────────────────────────────────────
@Composable
fun WeeklyBarChart(
    days: List<WeeklyDayUsage>,
    selectedDayIndex: Int? = null,
    txtSec: Color,
    onDayTap: (Int) -> Unit = {}
) {
    val maxMs = days.maxOfOrNull { it.totalMillis } ?: 1L
    val chartHeight = 160.dp

    Row(
        modifier            = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment   = Alignment.Bottom
    ) {
        days.forEachIndexed { index, day ->
            val isSelected = index == selectedDayIndex
            val barColor = when {
                isSelected  -> Seg0
                day.isToday -> BarActive
                else        -> BarNormal
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier            = Modifier
                    .weight(1f)
                    .clickableScale { onDayTap(index) }
            ) {
                // Label above bar (selected or today)
                if ((isSelected || day.isToday) && day.totalMillis > 0) {
                    Surface(
                        color = if (isSelected) Seg0 else Seg1,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = formatTime(day.totalMillis),
                            fontSize = 9.sp,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                        )
                    }
                } else {
                    Spacer(Modifier.height(20.dp))
                }
                Spacer(Modifier.height(4.dp))

                val fraction = if (maxMs > 0) (day.totalMillis.toFloat() / maxMs).coerceIn(0.05f, 1f) else 0.05f

                Box(
                    modifier = Modifier
                        .width(22.dp)
                        .height(chartHeight * fraction)
                        .clip(RoundedCornerShape(50))
                        .background(barColor)
                )

                Spacer(Modifier.height(8.dp))
                Text(
                    text = day.dayLabel.take(3),
                    fontSize = 11.sp,
                    color = if (isSelected) Seg0 else if (day.isToday) Seg1 else txtSec,
                    fontWeight = if (isSelected || day.isToday) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────
//  Async icon loader
// ─────────────────────────────────────────────────────────
@Composable
fun rememberAppIcon(packageName: String): Drawable? {
    val context = LocalContext.current
    val icon by produceState<Drawable?>(initialValue = null, key1 = packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()
        }
    }
    return icon
}

// ─────────────────────────────────────────────────────────
//  Time formatter
// ─────────────────────────────────────────────────────────
fun formatTime(millis: Long): String {
    val s = millis / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    return when {
        h > 0 && m > 0 -> "${h}h ${m}m"
        h > 0           -> "${h}h"
        m > 0           -> "${m}m"
        else            -> "< 1m"
    }
}