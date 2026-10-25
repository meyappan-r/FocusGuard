# Codebase Walkthrough

A source-level tour in the order things execute. Architecture and rationale live in
[ARCHITECTURE.md](ARCHITECTURE.md); each platform concept is explained once in
[ANDROID_CONCEPTS.md](ANDROID_CONCEPTS.md) and linked from here. Bugs are referenced by ID and
described only in [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md). Evidence tags:
[index](README.md#evidence-tags).

All paths are under `app/src/main/java/com/focusguard/` unless stated.

---

## Part A - User journeys

### A1. First launch and permissions

1. The system starts `MainActivity` (`MainActivity.kt`). `onCreate` builds the object graph by
   hand and calls `setContent { FocusGuardApp(...) }` ([ARCHITECTURE section 5](ARCHITECTURE.md#5-startup-and-object-wiring)).
2. The user opens **Settings**. `SettingsViewModel.checkPermissions()` runs in `init` and again on
   every `ON_RESUME` (`SettingsScreen.kt`, a `DisposableEffect` adding a `LifecycleEventObserver`).
3. Two permissions are needed and are granted *outside the app*:
   - **Usage access** - `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, ...)` returns
     `MODE_ALLOWED` (API 29+; `AppOpsManagerCompat.noteOpNoThrow` below that).
     `requestUsageStatsPermission` opens `ACTION_USAGE_ACCESS_SETTINGS`.
   - **Accessibility service** - the string `"<pkg>/com.focusguard.core.services.BlockerAccessibilityService"`
     must appear in `Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES` and
     `ACCESSIBILITY_ENABLED == 1`. `requestAccessibilityPermission` opens `ACTION_ACCESSIBILITY_SETTINGS`.
4. When the user enables the service, Android binds it and calls
   `BlockerAccessibilityService.onServiceConnected()`, which starts the three collectors described in A3.

Notes: `POST_NOTIFICATIONS` is declared but never requested at runtime ([B-015](BUGS_AND_TECHNICAL_DEBT.md#b-015)).
Concepts: [permissions](ANDROID_CONCEPTS.md#permissions-and-special-access), [AccessibilityService](ANDROID_CONCEPTS.md#accessibilityservice).

### A2. Creating a session

`SessionsScreen` -> `SessionsMainScreen` -> FAB "New Session" -> `CreateSessionScreen` ->
(optionally `AppPickerScreen`) -> Save.

1. `viewModel.blankSession()` returns a `FocusSession` with a random UUID, `fromHour` = current hour,
   `toHour = (hour + 2) % 24`, one-time date = today, no apps. Alternatively a *suggested session*
   (`PREDEFINED_SESSIONS`) becomes a `FocusSession` via `newSessionFromSuggested` (repeat Mon-Fri,
   **no apps selected**).
2. `CreateSessionScreen` keeps every field in its own `remember { mutableStateOf(...) }`
   (so a rotation discards the draft - [B-018](BUGS_AND_TECHNICAL_DEBT.md#b-018)). The blocked apps
   come back from `AppPickerScreen` through the parent (`SessionNav.AppPicker` -> `copy(blockedPackages = chosen)`).
3. **Validation lives only in the composable**:
   `endBeforeStartErr = toMins <= fromMins`, `pastStartErr` (one-time session today whose start has
   passed), `noAppsErr`, `nameErr`. Save is enabled only when all pass. Consequence: the UI **cannot
   create a session that crosses midnight** ([B-004](BUGS_AND_TECHNICAL_DEBT.md#b-004)), although the
   model, scheduler and service all try to support it.
4. `SessionsViewModel.saveSession` (in `viewModelScope`): copy the current list from state, replace-or-append
   by `id`, `SessionDataStore.saveSessions(list)` (serialises the **whole list** to JSON), then
   `SessionScheduler.scheduleSession(session)`.
5. Persisting emits on `sessionsFlow`, which updates `SessionsState.sessions` *and* (if the service is
   running) the service's `activeSessionsCache` - this is how the service learns about the session
   immediately, without a broadcast.

There is no UI path to delete or disable a session: `SessionsViewModel.deleteSession` has no callers
([B-023](BUGS_AND_TECHNICAL_DEBT.md#b-023)).

### A3. A block actually happening

1. User opens app X. Android sends `TYPE_WINDOW_STATE_CHANGED` to the service
   (`res/xml/accessibility_service_config.xml` subscribes only to that type and sets
   `canRetrieveWindowContent="false"`, so the service sees package names, not screen text).
2. `onAccessibilityEvent` (main thread) -> `shouldBlockSynchronously(pkg)`:
   - skip own package, `NEVER_BLOCKABLE`, `ALWAYS_HIDDEN`;
   - if `now < activeBreakExpiryCache` -> no session block;
   - for each cached session: is *today* an active day (`repeatDays` contains weekday index 0=Mon,
     or `oneTimeDate == today`)? is `now` in `[from, to)` (wrapping if `to <= from`)? is `pkg` in
     `blockedPackages`? -> return the session name.
3. If no session matched, check `pkg in limitReachedCache` (filled by the 10-second poll,
   `updateLimitReachedCache`, which compares `queryAndAggregateUsageStats(startOfDay, now)[pkg].totalTimeInForeground`
   with the stored limit).
4. A hit calls `launchBlockActivity`, which starts `BlockActivity` with
   `FLAG_ACTIVITY_NEW_TASK | CLEAR_TASK | EXCLUDE_FROM_RECENTS` and extras `blocked_package`,
   `block_source` ("session" or "limit"), optional `session_name`.
5. `BlockActivity.onCreate` (classic View UI, `res/layout/block_overlay.xml`, `AppCompatActivity`):
   resolves app label/icon through `PackageManager`; sets the title; sets the subtitle by source.
   - **Exit** -> `goHomeAndFinish()` (starts the HOME intent). Back does the same (`onBackPressed`).
   - **Unblock manually** (uncommitted working-tree version) -> writes `now + 5 min` into DataStore
     from a fire-and-forget `CoroutineScope(Dispatchers.IO)`, starts `MainActivity` with
     `navigate_to=blocks`, `finish()`. The previous committed version offered 2/5/10 minutes in an
     `AlertDialog`.
   - If the required extras are missing, `onCreate` `return`s early after `setContentView`, leaving a
     dimmed screen with no click listeners ([B-010](BUGS_AND_TECHNICAL_DEBT.md#b-010)).

Threading: the callback runs on the main thread, the caches are `@Volatile` and written from
`Dispatchers.IO` collectors. See [Volatile and visibility](ANDROID_CONCEPTS.md#kotlin-coroutines-and-flow).

### A4. Setting a daily limit

`BlocksScreen` loads apps in a `LaunchedEffect(Unit)` -> `BlocksViewModel.loadApps()`:
`GetDailyUsageUseCase.execute()` (IO dispatcher) + `LimitManager.getAllLimits()` -> `BlocksState`.
Tapping the edit/toggle opens `EditLimitDialog`; saving calls `setLimit(pkg, minutes*60*1000)`
which writes SharedPreferences (`apply()`, async) and calls `loadApps()` again. Only apps that
**have usage today** appear (the list is today's usage), so an app you have not opened yet cannot be limited.
The service picks the new limit up on its next 10-second poll.

### A5. Home screen

`HomeScreen` -> `LaunchedEffect(Unit) { viewModel.loadUsage() }` (re-runs each time the tab is
re-entered, not on app resume). `loadUsage` computes ring segments (top 4 apps + "Other", sweep angles
out of 300 degrees minus 2 degrees per gap), the **peak distraction** (largest app) and the list,
which *drops the peak app* (`sorted.drop(1)`) because it has its own card. The weekly tab calls
`repository.getWeeklyUsage()` once (guarded by `weeklyData.isEmpty()`, [B-021](BUGS_AND_TECHNICAL_DEBT.md#b-021)).
Tapping a bar maps `index` -> `dayOffset = 6 - index` and loads that day's per-app list.

### A6. Reboot

`BootReceiver.onReceive` (`BOOT_COMPLETED` / `QUICKBOOT_POWERON`): reads sessions with
`runBlocking { flow.firstOrNull() }`, re-schedules each, and if the accessibility service is not
enabled posts a high-importance notification (channel `focusguard_system_channel`, id 1001) that opens
accessibility settings. On Android 13+ the post is dropped unless the user granted
notification permission ([B-015](BUGS_AND_TECHNICAL_DEBT.md#b-015)).

---

## Part B - Package reference

### `MainActivity.kt`

- `sealed class Screen(route, title, icon)` - four `object`s: Home, Blocks, Sessions, Settings.
- `MainActivity : ComponentActivity` - `onCreate` (wiring), `onNewIntent` (`setIntent` only; the route
  extra is read once in `onCreate`, so a `navigate_to` delivered to a live instance is not applied.
  `BlockActivity` starts it with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`; because
  `MainActivity` uses the default launch mode, `CLEAR_TOP` finishes and recreates the existing
  instance, so `onCreate` runs again. **[I]** - verify on a device).
- `FocusGuardApp(...)` - resolves dark mode from `SettingsState.themeMode`; builds a Material 3
  `ColorScheme` from `FGColors`; sets status/navigation-bar colours in a `SideEffect`; hosts
  `Scaffold` + `NavigationBar` + `NavHost`. Tab clicks use the standard
  `popUpTo(startDestination){saveState=true}; launchSingleTop; restoreState` recipe.
  [Compose state and effects](ANDROID_CONCEPTS.md#jetpack-compose-state-and-effects).

### `core/services/BlockerAccessibilityService.kt`

| Member | What it does |
|---|---|
| `activeSessionsCache`, `activeBreakExpiryCache`, `limitReachedCache` | `@Volatile` snapshots read on the main thread |
| `serviceScope` | `CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)`; **never cancelled** ([B-008](BUGS_AND_TECHNICAL_DEBT.md#b-008)) |
| `onServiceConnected` | creates `LimitManager`, `SessionDataStore`; launches 2 collectors + the 10 s `while(true)` poll |
| `onAccessibilityEvent` | filter event type, call the two checks, launch `BlockActivity`; everything in `try/catch` |
| `shouldBlockSynchronously` | session predicate (see A3) |
| `updateLimitReachedCache` | limit poll via `queryAndAggregateUsageStats` |
| `onInterrupt` | empty |

Debug tip: logs use tag `BlockerService`; only errors are logged.

### `core/services/BlockActivity.kt` and `res/layout/block_overlay.xml`

See A3. Manifest: `noHistory`, `excludeFromRecents`, theme `Theme.Material3.DayNight.NoActionBar`,
not exported. The layout is a translucent frame (`#99000000`) with a bottom sheet.

### `core/scheduling/SessionScheduler.kt`

- `scheduleSession(session)`: next start and end via `calculateNextTrigger`, then `setExactAlarm`
  with request codes `id.hashCode()` (start) and `id.hashCode() + 1` (end) ([B-012](BUGS_AND_TECHNICAL_DEBT.md#b-012)).
- `setExactAlarm`: API >= 31 and `canScheduleExactAlarms()` -> `setExactAndAllowWhileIdle`; otherwise
  `setAndAllowWhileIdle` (inexact); `SecurityException` falls back the same way.
  `PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE`.
- `calculateNextTrigger`: one-time -> parse date, return null if in the past; repeating -> start
  from today at the target time (+1 day if already passed) then scan up to 8 days for an allowed
  weekday. `repeatDays` is 0=Mon..6=Sun, `DayOfWeek.value - 1` converts.
- `cancelSession` rebuilds equal `PendingIntent`s (extras are ignored by `filterEquals`) and cancels.

### `core/receivers/`

- `SessionAlarmReceiver`: extra `extra_session_id` -> find session -> `scheduleSession`. `runBlocking` on
  the main thread ([B-008](BUGS_AND_TECHNICAL_DEBT.md#b-008)).
- `BootReceiver`: see A6. Its `isAccessibilityServiceEnabled` compares `ComponentName.className`
  (more robust than the Settings-screen string compare).

### `data/local/SessionDataStore.kt`

- `data class FocusSession` - `id, name, emoji, tag, fromHour, fromMinute, toHour, toMinute,
  repeatEnabled, repeatDays (0=Mon), oneTimeDate "YYYY-MM-DD", breakMinutes, blockedPackages, description`.
  `durationMillis()` handles wrap-around (unused outside the model). `toJson()` / `fromJson()` use
  `org.json`; `fromJson` returns `null` for malformed entries via `runCatching`.
- `private val Context.dataStore by preferencesDataStore("focus_sessions_datastore")` - a
  file-level delegate guaranteeing one `DataStore` instance per process
  ([DataStore](ANDROID_CONCEPTS.md#datastore-and-sharedpreferences)).
- `sessionsFlow`, `breakExpiryFlow`, `saveSessions`, `setBreakExpiry`, `clearBreak`.
  `breakMinutes` is stored but not used for enforcement ([B-010](BUGS_AND_TECHNICAL_DEBT.md#b-010)).

### `data/local/LimitManager.kt`

SharedPreferences `limit_preferences`: package name -> limit ms; `getLimit` returns -1 when absent;
`getAllLimits` filters to `Long` values.

### `data/datasource/UsageStatsDataSource.kt` and `data/repository/UsageRepositoryImpl.kt`

State machine: [ARCHITECTURE section 8](ARCHITECTURE.md#8-usage-measurement). `UsageRepositoryImpl`:
`getDailyUsage()` (today since local midnight), `getWeeklyUsage()` (7 days, today clamped to now),
`getDayAppUsage(dayOffset)`, `getHiddenPackages()` (three-pass filter, cached in a plain `var`
without synchronisation or invalidation - [B-022](BUGS_AND_TECHNICAL_DEBT.md#b-022)).
`isBlockable = packageName !in NEVER_BLOCKABLE`.

### `domain/`

Data classes only (`AppUsage`, `WeeklyDayUsage`, `ScreenTimeSummary`, `PeakDistraction`) plus
`GetDailyUsageUseCase.execute()` = `withContext(Dispatchers.IO) { repository.getDailyUsage() }`.
`ScreenTimeSummary.focusTime` is never set.

### `ui/`

| File | Contents |
|---|---|
| `home/HomeViewModel.kt`, `HomeState.kt` | state: `HomeState` (usage list, ring segments, weekly data, selections); `HomeEvent` is an empty class |
| `home/HomeScreen.kt` (794 lines) | `HomeScreen`, `TabToggle`, `SegmentedRing` (Canvas arcs), `PeakDistractionCard`, `AppUsageRow`, `WeeklyBarChart`, `rememberAppIcon` (`produceState` + IO), `formatTime`. Declares its own private colour constants duplicating `ui/theme` |
| `blocks/` | `BlocksViewModel`, `BlocksScreen` (`BlockAppRow`, `EditLimitDialog`) |
| `sessions/SessionsViewModel.kt` | state, CRUD, templates, 1 Hz active-session evaluator, installed-app loader (non-system apps only - [B-011](BUGS_AND_TECHNICAL_DEBT.md#b-011)) |
| `sessions/SessionsScreen.kt` (1169 lines) | `SessionNav`, week strip, active card (with "End Break Early"), template/saved cards, form, app picker, time/date/tag/break pickers |
| `settings/` | `SettingsViewModel` (+ factory), `SettingsScreen`, `PrivacyScreen`. The "Check for Updates" row is a 1.5 s `delay` followed by a hard-coded "up to date" ([B-017](BUGS_AND_TECHNICAL_DEBT.md#b-017)) |
| `theme/FocusGuardColors.kt` | tonal-green palette + `FGColors` helpers that animate light/dark transitions |
| `extensions/ModifierExt.kt` | `Modifier.clickableScale` (press animation via `composed`) |

---

## Part C - How to trace things yourself

- *"Why was app X blocked?"* -> filter logcat for tag `BlockerService`; then check the caches by
  reasoning from [A3](#a3-a-block-actually-happening). See
  [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md#debugging-recipes).
- *"Why does Home show a different number than Digital Wellbeing?"* -> read
  `UsageStatsDataSource.getSotByPackage` and compare against `adb shell dumpsys usagestats`.
- *"Is the alarm armed?"* -> `adb shell dumpsys alarm | findstr com.focusguard`.
