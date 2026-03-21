package com.focusguard.ui.sessions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.focusguard.data.local.FocusSession
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.UUID

// ─────────────────────────────────────────────────────────────────
//  Screens enum for in-page navigation
// ─────────────────────────────────────────────────────────────────
private sealed class SessionNav {
    object Main : SessionNav()
    data class CreateEdit(val session: FocusSession, val isNew: Boolean) : SessionNav()
    data class AppPicker(val session: FocusSession, val isNew: Boolean) : SessionNav()
}

// ─────────────────────────────────────────────────────────────────
//  Root: handles in-page navigation stack
// ─────────────────────────────────────────────────────────────────
@Composable
fun SessionsScreen(viewModel: SessionsViewModel) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    var nav by remember { mutableStateOf<SessionNav>(SessionNav.Main) }

    LaunchedEffect(Unit) { viewModel.loadInstalledApps(context) }

    when (val current = nav) {
        is SessionNav.Main -> SessionsMainScreen(
            state    = state,
            viewModel = viewModel,
            onCreateNew = {
                nav = SessionNav.CreateEdit(viewModel.blankSession(), isNew = true)
            },
            onEditSession = { s ->
                nav = SessionNav.CreateEdit(s, isNew = false)
            },
            onStartSession = { s ->
                viewModel.startSession(s)
            }
        )
        is SessionNav.CreateEdit -> CreateSessionScreen(
            initial    = current.session,
            isNew      = current.isNew,
            onSave     = { saved ->
                viewModel.saveSession(saved)
                nav = SessionNav.Main
            },
            onPickApps = { draft ->
                nav = SessionNav.AppPicker(draft, current.isNew)
            },
            onDiscard  = { nav = SessionNav.Main }
        )
        is SessionNav.AppPicker -> AppPickerScreen(
            allApps         = state.installedApps,
            selectedPackages = current.session.blockedPackages,
            onDone          = { chosen ->
                val updated = current.session.copy(blockedPackages = chosen)
                nav = SessionNav.CreateEdit(updated, current.isNew)
            },
            onBack          = {
                nav = SessionNav.CreateEdit(current.session, current.isNew)
            }
        )
    }
}

// ─────────────────────────────────────────────────────────────────
//  Main sessions screen
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsMainScreen(
    state: SessionsState,
    viewModel: SessionsViewModel,
    onCreateNew: () -> Unit,
    onEditSession: (FocusSession) -> Unit,
    onStartSession: (FocusSession) -> Unit
) {
    val sessionsForDay = viewModel.sessionsForSelectedDate()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Focus Sessions", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNew,
                icon    = { Icon(Icons.Default.Add, contentDescription = "Create") },
                text    = { Text("New Session") }
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

            // ── Calendar strip ────────────────────────────────
            item { CalendarWeekStrip(state = state, viewModel = viewModel) }
            item { Spacer(modifier = Modifier.height(16.dp)) }

            // ── Active session card ───────────────────────────
            if (state.isSessionActive) {
                item {
                    ActiveSessionCard(
                        name           = state.activeSessionName,
                        emoji          = state.activeSessionEmoji,
                        remainingMillis = state.activeSessionRemainingMillis,
                        blockedCount   = state.activeSessionBlockedCount,
                        onStop         = { viewModel.stopSession() }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // ── Today's sessions ──────────────────────────────
            if (sessionsForDay.isNotEmpty()) {
                item {
                    Text(
                        "Today's Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(sessionsForDay) { s ->
                    SavedSessionCard(
                        session  = s,
                        isActive = state.isSessionActive && state.activeSessionName == s.name,
                        onEdit   = { onEditSession(s) },
                        onStart  = { onStartSession(s) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            // ── Suggested sessions ────────────────────────────
            item {
                Text(
                    "Suggested Sessions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            items(state.suggestedSessions) { s ->
                SuggestedSessionCard(
                    s     = s,
                    onAdd = {
                        val fs = viewModel.newSessionFromSuggested(s)
                        onEditSession(fs)  // open editor pre-filled
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            // ── All saved sessions ────────────────────────────
            if (state.sessions.any { it !in sessionsForDay }) {
                item {
                    Text(
                        "My Sessions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(state.sessions.filter { it !in sessionsForDay }) { s ->
                    SavedSessionCard(
                        session  = s,
                        isActive = state.isSessionActive && state.activeSessionName == s.name,
                        onEdit   = { onEditSession(s) },
                        onStart  = { onStartSession(s) }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) } // FAB clearance
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Calendar week strip
// ─────────────────────────────────────────────────────────────────
@Composable
fun CalendarWeekStrip(state: SessionsState, viewModel: SessionsViewModel) {
    val today       = LocalDate.now()
    val weekStart   = today.minusDays(today.dayOfWeek.value.toLong() - 1)
        .plusWeeks(state.weekOffset.toLong())

    Column {
        // Month header + nav
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = { viewModel.navigateWeek(-1) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Previous week")
            }
            Text(
                text = weekStart.format(DateTimeFormatter.ofPattern("MMMM yyyy")),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.navigateWeek(1) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Next week")
            }
        }

        // Day chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(7) { i ->
                val date      = weekStart.plusDays(i.toLong())
                val isToday   = date == today
                val isSelected = date == state.selectedDate
                val dayLabel  = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).take(1)

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { viewModel.selectDate(date) }
                ) {
                    Text(
                        text  = dayLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                when {
                                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                                    isToday    -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha=0.5f)
                                    else       -> Color.Transparent
                                }
                            )
                            .border(
                                width = if(isSelected) 2.dp else 0.dp,
                                color = if(isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text  = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                isToday    -> MaterialTheme.colorScheme.secondary
                                else       -> MaterialTheme.colorScheme.onSurface
                            },
                            fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Active session card
// ─────────────────────────────────────────────────────────────────
@Composable
fun ActiveSessionCard(
    name: String,
    emoji: String,
    remainingMillis: Long,
    blockedCount: Int,
    onStop: () -> Unit
) {
    val h  = (remainingMillis / 3_600_000).toInt()
    val m  = ((remainingMillis % 3_600_000) / 60_000).toInt()
    val s  = ((remainingMillis % 60_000) / 1_000).toInt()
    val timeStr = if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 6.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(emoji, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Active Session", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    Text(name, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 6.dp,
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text  = timeStr,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text  = "$blockedCount apps blocked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onStop,
                colors  = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape   = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stop Session", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Suggested session card
// ─────────────────────────────────────────────────────────────────
@Composable
fun SuggestedSessionCard(s: SuggestedSession, onAdd: () -> Unit) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(s.emoji, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccessTime, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "%02d:%02d – %02d:%02d".format(s.fromHour, s.fromMinute, s.toHour, s.toMinute),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Add", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Saved session card
// ─────────────────────────────────────────────────────────────────
@Composable
fun SavedSessionCard(
    session: FocusSession,
    isActive: Boolean,
    onEdit: () -> Unit,
    onStart: () -> Unit
) {
    val tagColor = DEFAULT_TAGS.firstOrNull { it.name == session.tag }?.colorHex ?: "#888888"
    val parsed   = runCatching { android.graphics.Color.parseColor(tagColor) }.getOrDefault(0xFF888888.toInt())
    val composeColor = Color(parsed)

    OutlinedCard(
        modifier = Modifier.fillMaxWidth().clickable { onEdit() },
        colors   = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border   = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape    = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(composeColor.copy(alpha=0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(session.emoji, fontSize = 24.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(session.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp).clip(CircleShape)
                            .background(composeColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(session.tag, style = MaterialTheme.typography.labelSmall, color = composeColor)
                }
                val timeRange = "%02d:%02d – %02d:%02d".format(
                    session.fromHour, session.fromMinute, session.toHour, session.toMinute
                )
                val daysStr = if (session.repeatEnabled) {
                    listOf("M","T","W","T","F","S","S")
                        .filterIndexed { i, _ -> i in session.repeatDays }
                        .joinToString(" ")
                } else session.oneTimeDate
                Text("$timeRange  •  $daysStr", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (session.blockedPackages.isNotEmpty()) {
                    Text("${session.blockedPackages.size} apps blocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                }
            }
            if (!isActive) {
                FilledIconButton(
                    onClick = onStart,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = "Start", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Create / Edit session full-screen form
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSessionScreen(
    initial:    FocusSession,
    isNew:      Boolean,
    onSave:     (FocusSession) -> Unit,
    onPickApps: (FocusSession) -> Unit,
    onDiscard:  () -> Unit
) {
    // Mutable form state (derived from initial)
    var name         by remember { mutableStateOf(initial.name) }
    var emoji        by remember { mutableStateOf(initial.emoji) }
    var tag          by remember { mutableStateOf(initial.tag) }
    var fromHour     by remember { mutableStateOf(initial.fromHour) }
    var fromMinute   by remember { mutableStateOf(initial.fromMinute) }
    var toHour       by remember { mutableStateOf(initial.toHour) }
    var toMinute     by remember { mutableStateOf(initial.toMinute) }
    var repeatEnabled by remember { mutableStateOf(initial.repeatEnabled) }
    var repeatDays   by remember { mutableStateOf(initial.repeatDays) }
    var oneTimeDate  by remember { mutableStateOf(
        if (initial.oneTimeDate.isEmpty())
            LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        else initial.oneTimeDate
    ) }
    var breakMinutes by remember { mutableStateOf(initial.breakMinutes) }
    var description  by remember { mutableStateOf(initial.description) }
    val blockedPkgs  = initial.blockedPackages  // passed back from AppPicker

    // Dialogs
    var showDiscard    by remember { mutableStateOf(false) }
    var showTagPicker  by remember { mutableStateOf(false) }
    var showFromPicker by remember { mutableStateOf(false) }
    var showToPicker   by remember { mutableStateOf(false) }
    var showBreakPicker by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    // Detect "dirty" form
    val isDirty = name != initial.name || tag != initial.tag ||
            fromHour != initial.fromHour || fromMinute != initial.fromMinute ||
            toHour != initial.toHour || toMinute != initial.toMinute ||
            repeatEnabled != initial.repeatEnabled || repeatDays != initial.repeatDays ||
            oneTimeDate != initial.oneTimeDate || breakMinutes != initial.breakMinutes ||
            description != initial.description || blockedPkgs != initial.blockedPackages

    fun buildSession() = initial.copy(
        name = name, emoji = emoji, tag = tag,
        fromHour = fromHour, fromMinute = fromMinute,
        toHour = toHour, toMinute = toMinute,
        repeatEnabled = repeatEnabled, repeatDays = repeatDays,
        oneTimeDate = oneTimeDate, breakMinutes = breakMinutes,
        blockedPackages = blockedPkgs, description = description
    )

    BackHandler {
        if (isDirty) showDiscard = true else onDiscard()
    }

    // ── Discard confirmation dialog ───────────────────────────
    if (showDiscard) {
        AlertDialog(
            onDismissRequest = { showDiscard = false },
            title = { Text("Not saving your changes?", fontWeight = FontWeight.Bold) },
            text  = { Text("Your focus schedule changes will not be saved.") },
            confirmButton = {
                TextButton(onClick = { showDiscard = false; onDiscard() }) {
                    Text("Discard", color = MaterialTheme.colorScheme.onSurface)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscard = false }) {
                    Text("Keep editing", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // ── Tag picker bottom sheet ───────────────────────────────
    if (showTagPicker) {
        TagPickerSheet(
            currentTag = tag,
            onSelect   = { tag = it; showTagPicker = false },
            onDismiss  = { showTagPicker = false }
        )
    }

    // ── From time picker ──────────────────────────────────────
    if (showFromPicker) {
        TimePickerDialog(
            initialHour   = fromHour,
            initialMinute = fromMinute,
            onConfirm     = { h, m -> fromHour = h; fromMinute = m; showFromPicker = false },
            onDismiss     = { showFromPicker = false }
        )
    }

    // ── To time picker ────────────────────────────────────────
    if (showToPicker) {
        TimePickerDialog(
            initialHour   = toHour,
            initialMinute = toMinute,
            onConfirm     = { h, m -> toHour = h; toMinute = m; showToPicker = false },
            onDismiss     = { showToPicker = false }
        )
    }

    // ── Break picker ──────────────────────────────────────────
    if (showBreakPicker) {
        BreakPickerSheet(
            current   = breakMinutes,
            onSelect  = { breakMinutes = it; showBreakPicker = false },
            onDismiss = { showBreakPicker = false }
        )
    }

    // ── Date picker (when repeat off) ─────────────────────────
    if (showDatePicker) {
        DatePickerDialog(
            current    = oneTimeDate,
            onConfirm  = { oneTimeDate = it; showDatePicker = false },
            onDismiss  = { showDatePicker = false }
        )
    }

    // ── Main form layout ──────────────────────────────────────
    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            CenterAlignedTopAppBar(
                title = { Text(if (isNew) "New Schedule" else "Edit Schedule", fontWeight = FontWeight.Bold) },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { if (isDirty) showDiscard = true else onDiscard() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Button(
                    onClick  = {
                        if (name.isNotBlank()) onSave(buildSession())
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape    = RoundedCornerShape(28.dp),
                    enabled  = name.isNotBlank()
                ) {
                    Text("Save", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            // ── Name + emoji row
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(emoji, fontSize = 26.sp, modifier = Modifier.padding(end = 8.dp))
                    TextField(
                        value         = name,
                        onValueChange = { name = it },
                        placeholder   = { Text("Enter name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                        singleLine    = true,
                        colors        = TextFieldDefaults.colors(
                            focusedContainerColor   = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor   = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ── Tag
            FormRow(label = "Tag", onClick = { showTagPicker = true }) {
                val tc = DEFAULT_TAGS.firstOrNull { it.name == tag }?.colorHex ?: "#888888"
                val parsed = runCatching { android.graphics.Color.parseColor(tc) }.getOrDefault(0xFF888888.toInt())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Color(parsed)))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(tag, style = MaterialTheme.typography.bodyMedium)
                    Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Schedule section header
            Text("Schedule", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape  = RoundedCornerShape(12.dp)
            ) {
                Column {
                    FormRow(label = "From", onClick = { showFromPicker = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d:%02d %s".format(
                                if (fromHour % 12 == 0) 12 else fromHour % 12, fromMinute,
                                if (fromHour < 12) "AM" else "PM"),
                                style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    FormRow(label = "To", onClick = { showToPicker = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("%02d:%02d %s".format(
                                if (toHour % 12 == 0) 12 else toHour % 12, toMinute,
                                if (toHour < 12) "AM" else "PM"),
                                style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))

                    // ── Date row (only when repeat is OFF)
                    if (!repeatEnabled) {
                        FormRow(label = "Date", onClick = { showDatePicker = true }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val parsed = runCatching {
                                    LocalDate.parse(oneTimeDate).format(DateTimeFormatter.ofPattern("MMM d"))
                                }.getOrDefault(oneTimeDate)
                                Text(parsed, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    // ── Repeat toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Repeat", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked  = repeatEnabled,
                            onCheckedChange = { repeatEnabled = it }
                        )
                    }

                    // ── Day chips (only when repeat is ON)
                    if (repeatEnabled) {
                        val dayLabels = listOf("M","T","W","T","F","S","S")
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            dayLabels.forEachIndexed { i, label ->
                                val selected = i in repeatDays
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (selected) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable {
                                            repeatDays = if (selected)
                                                repeatDays - i else repeatDays + i
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text  = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                    FormRow(label = "Break", onClick = { showBreakPicker = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$breakMinutes mins", style = MaterialTheme.typography.bodyMedium)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Block settings section
            Text("Block settings", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.clickable { onPickApps(buildSession()) },
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape    = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Blocked Apps", style = MaterialTheme.typography.bodyLarge)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (blockedPkgs.isEmpty()) {
                            Text("None", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text("${blockedPkgs.size} apps", style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Description
            Text("Description", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape  = RoundedCornerShape(12.dp)
            ) {
                TextField(
                    value         = description,
                    onValueChange = { description = it },
                    placeholder   = { Text("What do you plan on focusing? Add notes...",
                        color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    modifier      = Modifier.fillMaxWidth().heightIn(min = 80.dp),
                    colors        = TextFieldDefaults.colors(
                        focusedContainerColor   = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor   = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  App picker screen
// ─────────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(
    allApps: List<AppInfo>,
    selectedPackages: Set<String>,
    onDone: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    var chosen  by remember { mutableStateOf(selectedPackages) }
    var query   by remember { mutableStateOf("") }
    var snackMsg by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(snackMsg) {
        if (snackMsg != null) {
            snackbarHostState.showSnackbar(snackMsg!!, duration = SnackbarDuration.Short)
            snackMsg = null
        }
    }

    val filtered = if (query.isBlank()) allApps
    else allApps.filter { it.appName.contains(query, ignoreCase = true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Select Apps to Block", fontWeight = FontWeight.Bold) },
                navigationIcon = {},
                actions = {
                    IconButton(onClick = { onDone(chosen) }) {
                        Icon(Icons.Default.Close, contentDescription = "Done")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) { data ->
            Card(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                shape    = RoundedCornerShape(50.dp),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }}
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Search bar
            OutlinedTextField(
                value         = query,
                onValueChange = { query = it },
                placeholder   = { Text("Search apps") },
                leadingIcon   = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine    = true,
                shape         = RoundedCornerShape(14.dp),
                modifier      = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(filtered) { app ->
                    val isSelected = app.packageName in chosen
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosen = if (isSelected) chosen - app.packageName
                                else chosen + app.packageName
                                if (!isSelected) snackMsg = "Added to blocked apps"
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // App icon placeholder letter
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text  = app.appName.take(1).uppercase(),
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text     = app.appName,
                            modifier = Modifier.weight(1f),
                            style    = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Switch(
                            checked         = isSelected,
                            onCheckedChange = { on ->
                                chosen = if (on) chosen + app.packageName else chosen - app.packageName
                                if (on) snackMsg = "Added to blocked apps"
                            }
                        )
                    }
                    Divider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────
//  Helper composables
// ─────────────────────────────────────────────────────────────────

/** A single form row with a label on the left and custom content on the right */
@Composable
fun FormRow(
    label: String,
    onClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        content()
    }
}

/** Tag picker bottom sheet dialog */
@Composable
fun TagPickerSheet(
    currentTag: String,
    onSelect:  (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(20.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Select Tag", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, contentDescription = null) }
                }
                Divider()
                DEFAULT_TAGS.forEach { t ->
                    val parsed = runCatching { android.graphics.Color.parseColor(t.colorHex) }.getOrDefault(0xFF888888.toInt())
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(t.name) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = currentTag == t.name, onClick = { onSelect(t.name) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.size(24.dp).clip(CircleShape).background(Color(parsed)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(t.name, style = MaterialTheme.typography.bodyLarge,
                            color = Color(parsed), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                    }
                    Divider()
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

/** Simple time picker dialog */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(
    initialHour:   Int,
    initialMinute: Int,
    onConfirm:     (Int, Int) -> Unit,
    onDismiss:     () -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = false)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Select time", style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                Spacer(modifier = Modifier.height(16.dp))
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(modifier = Modifier.width(8.dp))
                    TextButton(onClick = { onConfirm(state.hour, state.minute) }) {
                        Text("OK", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/** Break duration picker */
@Composable
fun BreakPickerSheet(
    current:  Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(0, 5, 10, 15, 20, 30)
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(20.dp)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Break Duration", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
                options.forEach { mins ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { onSelect(mins) }.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = current == mins, onClick = { onSelect(mins) })
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (mins == 0) "No break" else "$mins mins",
                            style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

/** Simple date picker dialog using Material3 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialog(
    current:   String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val initialMs = runCatching {
        LocalDate.parse(current).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    }.getOrDefault(System.currentTimeMillis())

    val dpState = rememberDatePickerState(initialSelectedDateMillis = initialMs)

    androidx.compose.material3.DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val ms = dpState.selectedDateMillis ?: System.currentTimeMillis()
                val date = java.time.Instant.ofEpochMilli(ms)
                    .atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                onConfirm(date.format(DateTimeFormatter.ISO_LOCAL_DATE))
            }) { Text("OK", fontWeight = FontWeight.Bold) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    ) {
        DatePicker(state = dpState)
    }
}
