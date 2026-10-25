# Android and Kotlin Concepts Used by FocusGuard

This is the single place where each platform or language concept is explained. Other documents link
here. Only technologies the project actually uses are covered. Each section follows the same
shape: **what -> why -> how it works -> where it appears here -> failure modes -> practice**.
Project facts are tagged per the [evidence legend](README.md#evidence-tags).

Contents: [Kotlin language](#kotlin-language-features-in-this-codebase) ·
[Component lifecycle](#activities-viewmodels-and-lifecycle) ·
[AccessibilityService](#accessibilityservice) ·
[UsageStatsManager](#usagestatsmanager) ·
[AlarmManager, PendingIntent, receivers](#alarmmanager-pendingintent-and-broadcastreceivers) ·
[DataStore and SharedPreferences](#datastore-and-sharedpreferences) ·
[Coroutines and Flow](#kotlin-coroutines-and-flow) ·
[Compose state and effects](#jetpack-compose-state-and-effects) ·
[Navigation](#navigation-compose) ·
[Permissions](#permissions-and-special-access) ·
[java.time and API levels](#javatime-and-api-levels) ·
[Manual dependency injection](#manual-dependency-injection)

---

## Kotlin language features in this codebase

| Feature | Syntax you will see | Where | Why it matters |
|---|---|---|---|
| `data class` | `data class FocusSession(val id: String, ...)` | `SessionDataStore.kt`, `domain/model/*` | Generates `equals/hashCode/copy/toString`; `copy()` is how state is "mutated" immutably (`_state.value.copy(...)`) |
| `sealed class` | `sealed class Screen(...)`, `private sealed class SessionNav` | `MainActivity.kt`, `SessionsScreen.kt` | Closed hierarchy -> exhaustive `when` |
| `object` | `object Home : Screen(...)` | `MainActivity.kt` | Singleton |
| `companion object` | `companion object { val ALWAYS_HIDDEN ... }`, `const val EXTRA_SESSION_ID` | repository, receiver | Kotlin's "static"; the service reads `UsageRepositoryImpl.NEVER_BLOCKABLE` this way |
| Nullable types / safe call / Elvis | `intent.getStringExtra("x") ?: return` | `BlockActivity`, receivers | `?: return` exits early - and is the cause of [B-010](BUGS_AND_TECHNICAL_DEBT.md#b-010) |
| `lateinit` | `private lateinit var limitManager` | service | Promise of initialisation in `onServiceConnected`; guarded with `::limitManager.isInitialized` |
| Property delegate | `by lazy`-style `by preferencesDataStore(...)`, `by remember`, `by collectAsState()` | many | `getValue` operator generates the accessor |
| Extension property | `private val Context.dataStore ...` | `SessionDataStore.kt` | Adds a property to `Context` without subclassing |
| Lambdas, trailing lambda | `repeat(n) { ... }`, `edit { prefs -> ... }` | many | Higher-order functions |
| `runCatching { }.getOrNull()` | `FocusSession.fromJson` | data | Exceptions -> null; hides *why* parsing failed |
| Destructuring | `for ((pkg, limit) in limits)` | service | `Map.Entry` components |
| Scope functions | `.apply { putExtra(...) }`, `.let { ... }` | many | Builder style / null-conditional blocks |

**Practice:** rewrite `FocusSession.fromJson` so it reports which field failed instead of returning
`null`; write a unit test that proves malformed JSON no longer disappears silently.

---

## Activities, ViewModels and lifecycle

**What.** An `Activity` is a UI screen the system can destroy and recreate (rotation, theme/locale
change, memory pressure). A `ViewModel` outlives *configuration* recreation because it lives in a
`ViewModelStore` owned by the Activity; `viewModelScope` is cancelled in `onCleared()`, which is
called only when the store is cleared for good.

**In this project.** `HomeViewModel`, `BlocksViewModel` and `SessionsViewModel` are created with plain
constructors in `MainActivity.onCreate`, so they never enter the store; only `SettingsViewModel`
goes through `ViewModelProvider` + `SettingsViewModelFactory` **[O]**. Implications **[I]**: state
reset on rotation, `onCleared` never runs, and `SessionsViewModel.init` launches loops that run
forever ([B-002](BUGS_AND_TECHNICAL_DEBT.md#b-002)). Conversely `SettingsViewModel` survives
rotation but holds the first Activity as `Context` ([B-009](BUGS_AND_TECHNICAL_DEBT.md#b-009)).

**Failure modes.** Leaking an Activity via a long-lived reference; doing work in `init` that nobody cancels;
assuming state survives process death (ViewModel does not - that needs `SavedStateHandle` / persistence).

**Debug.** Rotate the device and watch which screens reset; Android Studio Profiler -> memory ->
count `MainActivity` instances after several rotations (expect > 1 if leaking).

**Practice.** Replace the plain constructors with `viewModels { factory }` and show with the
profiler that Activity instances stop accumulating.

---

## AccessibilityService

**What.** A privileged service for assistive technology. Once the user enables it in system settings,
Android delivers `AccessibilityEvent`s from every app to it.

**Why FocusGuard uses it.** It is the only API that tells a third-party app *immediately* when the
foreground window changes, without a foreground service and polling loop.

**How it is configured.** `res/xml/accessibility_service_config.xml`: only
`typeWindowStateChanged`, `canRetrieveWindowContent=false`, `notificationTimeout=0`, a description
string (shown in settings). The manifest entry is `exported=true` but protected by
`android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` so only the system can bind it.

**Lifecycle.** The system creates and binds the service; `onServiceConnected()` is the "start";
there is no `onCreate` contract for your caches. `onInterrupt()` signals feedback interruption.
The service can be killed or disabled by the OEM or the user - blocking then silently stops
(a documented limitation in the README).

**Threading.** `onAccessibilityEvent` runs on the **main thread**; keep it fast. This project reads only
`@Volatile` fields there.

**Starting activities from a service.** Background activity launches are restricted on Android 10+;
the code relies on the service being allowed to do this **[U - the repo does not document which platform
exemption permits it; verify per OEM and API level]**.

**Failure modes.** Event storms (hence the early `return` for non-window events); exceptions in the callback
(the project wraps the body in `try/catch`); leaking coroutines started in the service ([B-008](BUGS_AND_TECHNICAL_DEBT.md#b-008)).

**Debug.** `adb shell settings get secure enabled_accessibility_services`; `adb logcat -s BlockerService`.

**Practice.** Add a log line with the package name in `onAccessibilityEvent`, open five apps, and
confirm which events fire for overlays vs full-screen apps.

---

## UsageStatsManager

**What.** System API exposing per-app usage. Two shapes: aggregated (`queryUsageStats`,
`queryAndAggregateUsageStats`) and raw events (`queryEvents`).

**Why raw events here.** `totalTimeInForeground` can overstate time (overlapping activities,
screen-off periods). `UsageStatsDataSource` replays events with a single-foreground state machine
([diagram](ARCHITECTURE.md#8-usage-measurement)) to approximate Digital Wellbeing.

**Event types used:** `ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`, `ACTIVITY_STOPPED`, `SCREEN_INTERACTIVE`,
`SCREEN_NON_INTERACTIVE`, `DEVICE_SHUTDOWN`. (`ACTIVITY_RESUMED` is superseded by
`MOVE_TO_FOREGROUND` in newer API docs but still emitted **[I]**.)

**Requires** the special "Usage access" grant (`PACKAGE_USAGE_STATS`), not a runtime dialog.

**Failure modes.** Without the grant the queries return empty data (no exception); split-screen /
picture-in-picture violates the "one foreground app" assumption; events are only retained for a
limited window **[I]**, so old weekly days may look empty; limit enforcement uses a *different*
number ([B-006](BUGS_AND_TECHNICAL_DEBT.md#b-006)).

**Practice.** Write a pure function `foregroundDurations(events: List<FakeEvent>)` extracted from
`getSotByPackage` and unit-test: a pause without a resume, two resumes in a row, a screen-off in the middle.

---

## AlarmManager, PendingIntent and BroadcastReceivers

**AlarmManager.** Schedules work at wall-clock times even when the app is not running.
`setExactAndAllowWhileIdle` fires near-exactly and during Doze (rate-limited by the system);
`setAndAllowWhileIdle` is inexact. Since Android 12, exact alarms need `SCHEDULE_EXACT_ALARM`
(user-grantable) or `USE_EXACT_ALARM` (Play-restricted to alarm/calendar apps); the code checks
`canScheduleExactAlarms()` and falls back ([B-019](BUGS_AND_TECHNICAL_DEBT.md#b-019)).

**PendingIntent.** A token letting another process (the alarm service) fire your intent later.
Identity is `(requestCode, Intent.filterEquals)`: extras are *not* part of identity, which is why
`cancelSession` can rebuild an equal intent without the extras. `FLAG_IMMUTABLE` is mandatory on API 31+.
`FLAG_UPDATE_CURRENT` replaces extras of an existing token - this is how re-scheduling the same
session replaces the old alarm.

**BroadcastReceiver.** `onReceive` runs on the main thread with roughly 10 s before the system may
consider the app unresponsive; for async work use `goAsync()`. This project uses `runBlocking` on
a DataStore read instead ([B-008](BUGS_AND_TECHNICAL_DEBT.md#b-008)).

**BOOT_COMPLETED.** Alarms are wiped on reboot, so `BootReceiver` re-arms them. It is declared
`exported=true`; system broadcasts do not require this, but it is common practice **[I]**.

**In this project the alarms never start/stop a block** ([ARCHITECTURE section 7](ARCHITECTURE.md#7-the-alarm-subsystem-and-what-it-really-does)).

**Debug.** `adb shell dumpsys alarm | findstr com.focusguard`; to simulate Doze,
`adb shell dumpsys deviceidle force-idle`.

**Practice.** Change a session's end time and confirm via `dumpsys alarm` that the old end alarm is
replaced, not duplicated.

---

## DataStore and SharedPreferences

**SharedPreferences** - XML key-value file, synchronous reads, `apply()` writes asynchronously and
`commit()` synchronously; no change `Flow`; can ANR if you read a big file on the main thread.
Used for `limit_preferences` and `settings_prefs`.

**Preferences DataStore** - coroutine/Flow based replacement. `dataStore.data` is a cold `Flow<Preferences>` that
re-emits on every write; `edit { }` is transactional and suspending. Must have exactly one instance per
file per process, hence the file-level delegate `by preferencesDataStore(name = ...)`.

**In this project.** All sessions are one JSON string under `sessions_json_v2`; the break expiry is a `Long`.
The accessibility service collects `sessionsFlow` into a cache, which is why a saved session takes effect
immediately. Risks: whole-list rewrite, read-modify-write based on a possibly stale UI snapshot
([B-020](BUGS_AND_TECHNICAL_DEBT.md#b-020)); a corrupt blob yields an empty list, and the next save would persist that.

**Alternatives.** Room (queries, migrations, one row per session) or Proto DataStore (typed, versioned).

**Practice.** Write an instrumented test that saves two sessions, kills the process, and reads them back.

---

## Kotlin coroutines and Flow

**Definitions.** A `suspend` function can pause without blocking its thread. A `CoroutineScope` owns coroutines;
cancelling the scope cancels children (*structured concurrency*). A `Dispatcher` picks the thread
(`Main`, `IO`, `Default`). A `Flow` is a cold asynchronous stream; `StateFlow` is a hot, conflated holder of the latest value.

**In this project.**

| Place | Scope / dispatcher | Lifetime |
|---|---|---|
| ViewModels `loadUsage`, `saveSession`, ... | `viewModelScope` (Main.immediate) | until `onCleared()` - which is never called for three of the four VMs |
| `GetDailyUsageUseCase.execute` | `withContext(Dispatchers.IO)` | one call |
| `SessionsViewModel` 1 Hz loop | `viewModelScope.launch(Dispatchers.Default) { while (true) { ...; delay(1000) } }` | forever (see above) |
| Service collectors + poll | `CoroutineScope(SupervisorJob() + Dispatchers.IO + handler)` | never cancelled |
| `BlockActivity` unblock | `CoroutineScope(Dispatchers.IO).launch` (anonymous, unowned) | fire-and-forget |
| Receivers | `runBlocking` | blocks the main thread |

`SupervisorJob` means one failed child does not cancel siblings. `CoroutineExceptionHandler` catches uncaught exceptions
only for root coroutines launched in that scope. `@Volatile` makes writes from IO threads visible to the main-thread
reader; it does **not** make compound updates atomic (the caches are replaced wholesale, which is safe).

**`StateFlow` update styles:** `_state.value = _state.value.copy(...)` (read-modify-write, racy if two coroutines
update) vs `_state.update { it.copy(...) }` (atomic). `HomeViewModel` uses the first, `SessionsViewModel` the second.

**Failure modes.** Unscoped `CoroutineScope(...)` leaks; `while (true)` without `isActive`/cancellation; blocking calls
(`runBlocking`) in callbacks; swallowing `CancellationException` in a broad `catch (Exception)`.

**Debug.** Kotlin coroutine debugger in Android Studio; log `coroutineContext[Job]`; `-Dkotlinx.coroutines.debug`.

**Practice.** Add `onDestroy() { serviceScope.cancel() }` to the service and prove the 10 s poll stops after disabling the service.

---

## Jetpack Compose state and effects

**Model.** UI = function of state. Composables re-run (*recompose*) when `State` they read changes.

| API | Meaning | Use here |
|---|---|---|
| `remember { }` | cache across recompositions; lost on Activity recreation | form fields in `CreateSessionScreen` |
| `rememberSaveable` | also survives recreation (Bundle) | **not used** ([B-018](BUGS_AND_TECHNICAL_DEBT.md#b-018)) |
| `collectAsState()` | turns a `StateFlow` into Compose `State` | every screen: `viewModel.state.collectAsState()` |
| `LaunchedEffect(key)` | run a coroutine when entering composition / key changes | `viewModel.loadUsage()` per screen; `LaunchedEffect(startRoute)` in `FocusGuardApp` |
| `SideEffect` | non-suspending effect after every successful recomposition | system-bar colours |
| `DisposableEffect(key)` | register/unregister with `onDispose` | lifecycle observer in `SettingsScreen` |
| `produceState` | async value into State | `rememberAppIcon` (loads an icon on IO) |
| `animateColorAsState`/`animateFloatAsState` | animated value | `FGColors`, ring, selector |
| `Modifier.composed { }` | modifier with its own `remember` | `clickableScale` |

**State hoisting.** State lives in the ViewModel (`HomeState`) and flows down; events flow up as lambdas
(`onThemeToggle`, `onSave`). `HomeEvent` was meant for events but is empty.

**Gotchas seen here.** Loading in `LaunchedEffect(Unit)` ties data refresh to navigation, not to app
resume; colour constants duplicated per screen make theming inconsistent ([B-014](BUGS_AND_TECHNICAL_DEBT.md#b-014)).

**Practice.** Convert the `CreateSessionScreen` fields to `rememberSaveable` (hint: `Set<Int>` and `Set<String>` need a custom saver)
and verify the draft survives rotation.

---

## Navigation Compose

`NavHost(startDestination = "home")` with five string routes. The bottom bar uses the standard multi-back-stack recipe
(`popUpTo(start){saveState=true}`, `launchSingleTop`, `restoreState`). `currentBackStackEntryAsState()` +
`destination.hierarchy` drives the selected tab. The Sessions tab does its own sub-navigation with a sealed class in `remember`,
so Android Back inside the form is handled by a manual `BackHandler`.

---

## Permissions and special access

| Capability | Kind | Granted by | In this app |
|---|---|---|---|
| `PACKAGE_USAGE_STATS` | special app access | Settings -> Usage access | Settings screen status + deep link |
| Accessibility service | special app access | Settings -> Accessibility | status via `Settings.Secure`; deep link |
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | special / install-time | user or auto | declared; never requested interactively |
| `RECEIVE_BOOT_COMPLETED` | normal | install | declared |
| `POST_NOTIFICATIONS` | runtime (API 33+) | dialog | declared; **never requested** ([B-015](BUGS_AND_TECHNICAL_DEBT.md#b-015)) |
| `QUERY_ALL_PACKAGES` | normal but Play-restricted | install | needed to list installed apps (package visibility) |

---

## java.time and API levels

`java.time` (`LocalDate`, `LocalDateTime`, ...) is built into Android only from API 26. With `minSdk 21`, either
enable **core library desugaring** (`isCoreLibraryDesugaringEnabled = true` + `desugar_jdk_libs`) or avoid it.
This project does neither: lint reports 92 `NewApi` errors ([B-001](BUGS_AND_TECHNICAL_DEBT.md#b-001)) and the
build still succeeds because `abortOnError = false`. `AlarmManager.setExactAndAllowWhileIdle` is API 23.

---

## Manual dependency injection

"DI" means: a class receives its collaborators instead of creating them. Frameworks (Hilt, Koin) automate wiring;
doing it by hand in `MainActivity.onCreate` is fine for a small app but, as shown above, can bypass lifecycle
management. A middle path: write a `ViewModelProvider.Factory` per ViewModel (as `SettingsViewModelFactory` does) and
obtain them with `by viewModels { ... }`. The service and receivers cannot use `MainActivity`'s wiring, so they
construct `SessionDataStore`/`LimitManager` themselves - a second hidden "composition root".
