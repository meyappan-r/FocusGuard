# LEARN.md — FocusGuard: Complete Educational Reference

> **Goal:** After reading this document you must be able to explain every line of code, every architectural decision, every library choice, and every system API — as if you built this app from scratch.

---

# TABLE OF CONTENTS

1. [Project Overview](#1-project-overview)
2. [Folder & File Architecture](#2-folder--file-architecture)
3. [Line-by-Line Code Explanation](#3-line-by-line-code-explanation)
4. [Kotlin Language Concepts](#4-kotlin-language-concepts)
5. [Jetpack Compose Deep Explanation](#5-jetpack-compose-deep-explanation)
6. [Android System APIs Used](#6-android-system-apis-used)
7. [Blocking System Architecture](#7-blocking-system-architecture)
8. [Usage Tracking Logic](#8-usage-tracking-logic)
9. [Scheduling Architecture](#9-scheduling-architecture)
10. [State Management Architecture](#10-state-management-architecture)
11. [Library-by-Library Explanation](#11-library-by-library-explanation)
12. [Performance & Memory Design](#12-performance--memory-design)
13. [Security & Privacy Architecture](#13-security--privacy-architecture)
14. [Failure & Edge Case Handling](#14-failure--edge-case-handling)
15. [Interview Defense Section](#15-interview-defense-section)
16. [Mental Model Summary](#16-mental-model-summary)

---

# 1. PROJECT OVERVIEW

## What Problem Does FocusGuard Solve?

Smartphones are designed to capture and hold attention. Apps like Instagram, YouTube Shorts, and mobile games use variable reward loops — the same psychological mechanism as slot machines — to keep users engaged beyond their intentions. The result is:

- **Broken focus**: Every notification or habitual app-check fragments deep work into shallow, interrupted sessions.
- **Willpower depletion**: "I'll just check for 2 minutes" fails because the decision to stop requires willpower, and willpower is finite.
- **Ineffective soft tools**: Android's built-in Digital Wellbeing shows you how much you used an app, and even lets you set a timer — but the timer can be dismissed with a single tap. Knowing you've used Instagram for 2 hours does not automatically stop you from using it for a third.

**FocusGuard's insight:** The solution is not information, it is **commitment**. A user sets rules in advance (when calm, motivated, and rational) and the system enforces those rules later (when tempted). This is the same principle behind locking junk food in the garage — remove the option from the moment of temptation.

## Why This Architecture Was Chosen

The app uses **MVVM (Model-View-ViewModel)** layered over a simplified **Clean Architecture**:

```
UI Layer (Compose screens)
    ↕ observes StateFlow
ViewModel Layer (business logic, state management)
    ↕ calls
Domain Layer (use cases — pure Kotlin, no Android)
    ↕ delegates to
Data Layer (DataStore, SharedPreferences, UsageStatsManager)
    ↕ interacts with
System Layer (AccessibilityService, AlarmManager, BroadcastReceivers)
```

**Why MVVM?**
- ViewModels survive configuration changes (screen rotation) automatically — the ViewModel is NOT destroyed when the screen rotates, only the UI is.
- StateFlow provides a reactive single-source-of-truth that Compose can observe reactively.
- Separation of concerns: the UI knows nothing about storage or system APIs; it only renders whatever state the ViewModel exposes.

**Why not MVI or Redux?**
- For this app's size, full MVI (Model-View-Intent) would add boilerplate without meaningful benefit. The current pattern is close to MVI where StateFlow acts as the single state container.

## High-Level System Design

```
┌─────────────────────────────────────────────────────┐
│  USER configures sessions in UI                     │
│  → saved to DataStore (disk)                        │
│  → scheduled via AlarmManager (OS clock)            │
└───────────────────────┬─────────────────────────────┘
                        │ at scheduled time
                        ▼
┌─────────────────────────────────────────────────────┐
│  AlarmManager fires SessionAlarmReceiver            │
│  → reschedules next occurrence                      │
└─────────────────────────────────────────────────────┘
                        │ always running independently
                        ▼
┌─────────────────────────────────────────────────────┐
│  BlockerAccessibilityService (system-bound service) │
│  → watches every app foreground event               │
│  → checks in-memory cache: is this app blocked now? │
│  → YES: launches BlockActivity immediately          │
└─────────────────────────────────────────────────────┘
```

The key insight: **the scheduler and the blocker are completely decoupled**. The scheduler just updates the DataStore and fires alarms. The blocker purely reads time mathematically — it never asks "is the session active now?"; instead it reads the session's start/end times and computes `currentTime in fromMs..toMs` itself. This makes the system deterministic and crash-resistant.

## How Android OS Interacts with FocusGuard

Android is a Linux-based OS where every app runs in its own sandboxed process. Apps cannot normally see what other apps are doing. FocusGuard uses three **privileged system channels** to break this isolation:

1. **`PACKAGE_USAGE_STATS`** — A protected permission that lets the app query the system's event log (which app was in the foreground and for how long). Requires the user to explicitly grant it via a special settings screen.
2. **AccessibilityService** — A special service category that lets the app receive real-time window-change events from the system. Originally designed for screen readers like TalkBack.
3. **`SCHEDULE_EXACT_ALARM`** — On Android 12+, exact alarms require explicit permission because they prevent Doze mode from batching them.

## Real-World Comparison: Digital Wellbeing

Google's Digital Wellbeing is built into Android and uses the same APIs. Key differences:

| Feature | Digital Wellbeing | FocusGuard |
|---|---|---|
| Block bypass | 1-tap override | Break requires deliberate timer selection |
| Scheduling | Manual daily limits | Time-based repeating sessions |
| Tracking accuracy | Uses system aggregated stats | Custom event-stream (Screen-On Time only) |
| Data privacy | Synced to Google account | 100% on-device |
| Open source | No | Yes |

---

# 2. FOLDER & FILE ARCHITECTURE

## Package Root: `com.focusguard`

Every file lives under `app/src/main/java/com/focusguard/`. This is the **application ID** — Android uses it as the globally unique identifier for the app on the device and in the Play Store. It maps directly to the folder structure.

## Why These Folders Exist

### `core/`
**Purpose:** Android system-level integrations that have no UI.

Everything in `core/` is coupled to the Android OS — it receives system events, registers system alarms, and interacts with privileged APIs. These components run **independently of whether the app UI is open**. Moving them here makes it immediately clear: "this folder contains things the OS controls, not the user."

- **`core/services/`** — Long-running system-bound processes. `AccessibilityService` is bound by the OS, not by the app.
- **`core/receivers/`** — One-shot components that wake up, do one thing, and die. `BroadcastReceiver` has a maximum of ~10 seconds of execution time.
- **`core/scheduling/`** — The pure logic for translating a `FocusSession` into AlarmManager API calls. Isolated so it can be called from anywhere (ViewModel, BootReceiver, etc.) without duplicating code.

### `data/`
**Purpose:** Everything related to reading, writing, and transforming persistent state.

This mirrors the **Data Layer** in Clean Architecture. Components here:
- Know where data is stored (DataStore, SharedPreferences, UsageStatsManager).
- Do NOT know about the UI or ViewModels.
- Are responsible for transforming raw OS data into domain objects.

- **`data/datasource/`** — Directly reads from Android's `UsageStatsManager`. This is the rawest data layer — it talks to the OS event log.
- **`data/local/`** — Reads/writes to local storage (`DataStore`, `SharedPreferences`).
- **`data/repository/`** — Combines data sources, filters them (remove system apps), and exposes clean lists. The ViewModel never calls `UsageStatsManager` directly — it always goes through the repository.

### `domain/`
**Purpose:** The business logic "heart" of the app — pure Kotlin with zero Android dependencies.

In Clean Architecture, the domain layer is the most stable. It changes least often and can be tested without an Android emulator.

- **`domain/model/`** — Data classes that represent what the app *cares about*: `AppUsage`, `PeakDistraction`, `ScreenTimeSummary`, `WeeklyDayUsage`. These are NOT Android classes. They do not have `Context`, `Intent`, or any Android import.
- **`domain/usecase/`** — Single-responsibility classes, each doing one thing. `GetDailyUsageUseCase.execute()` is the only way the ViewModel fetches today's usage. If the data source changes (e.g., from SharedPreferences to a database), only the repository changes; the ViewModel and UseCase are untouched.

### `ui/`
**Purpose:** Everything the user sees. Compose screens + ViewModels.

- **`ui/home/`** — Today's usage ring, weekly bar chart, app list.
- **`ui/blocks/`** — Per-app daily limit setter.
- **`ui/sessions/`** — Focus session creation, calendar, active session status.
- **`ui/settings/`** — Theme, permissions, blocking toggle, privacy policy.
- **`ui/theme/`** — The color system. One file (`FocusGuardColors.kt`) defines the entire palette.
- **`ui/extensions/`** — Custom Compose modifiers that are reused across multiple screens.

### `MainActivity.kt`

The **single Activity** entrypoint. FocusGuard uses a single-Activity architecture — there is one `ComponentActivity`, and all screens are Compose composables managed by Navigation Compose. This is now the strongly recommended Android architecture because:
- Compose composables are cheap to create/destroy (unlike Activities).
- Navigation Compose handles back-stack within one Activity.
- Only one lifecycle to manage, one theme context.

---

## File-by-File: What Breaks If You Remove It?

| File | What Breaks If Removed |
|---|---|
| `MainActivity.kt` | App won't launch. Entry point is gone. |
| `BlockerAccessibilityService.kt` | All app blocking stops. Sessions become decorative. |
| `BlockActivity.kt` | Block screen vanishes. Users can open any blocked app. |
| `BootReceiver.kt` | Session alarms are never rescheduled after reboot. Device reboot = blocking permanently stops. |
| `SessionAlarmReceiver.kt` | Alarms fire but sessions are never rescheduled. Each session fires only once. |
| `SessionScheduler.kt` | Sessions can be saved but never scheduled. Blocking only works while the service has the in-memory cache — nothing is triggered. |
| `SessionDataStore.kt` | Sessions cannot be saved or loaded. App loses all user configuration. |
| `LimitManager.kt` | Per-app daily limits cannot be saved. The Blocks screen becomes non-functional. |
| `UsageStatsDataSource.kt` | No usage data. Home screen shows nothing. |
| `UsageRepositoryImpl.kt` | Raw usage data is never filtered or converted to domain models. |
| `GetDailyUsageUseCase.kt` | ViewModels access the repository directly, coupling UI logic to data layer. |
| `FocusGuardColors.kt` | No custom colors. UI falls back to Material3 defaults, losing the green theme. |
| `ModifierExt.kt` | Press animation gone. Buttons still work but feel less tactile. |

---

# 3. LINE-BY-LINE CODE EXPLANATION

## `AndroidManifest.xml`

The manifest is the app's **contract with the Android OS**. The OS reads this file to know: what components exist, what permissions are needed, and how to launch the app.

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions"/>
```
- `PACKAGE_USAGE_STATS` — A **signature/privileged** permission. Normal apps cannot request it at runtime like camera permission. The user must manually navigate to Settings → Special App Access → Usage Access. The `tools:ignore` suppresses a lint warning that says "only system apps should request this" — we know; we're intentionally using it.

```xml
<uses-permission android:name="android.permission.QUERY_ALL_PACKAGES"/>
```
- Required on Android 11+ (API 30+) to list all installed packages. Without this, `PackageManager.getInstalledApplications()` returns an empty list. Used to populate the app picker in the session builder.

```xml
<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM"/>
<uses-permission android:name="android.permission.USE_EXACT_ALARM"/>
```
- `SCHEDULE_EXACT_ALARM` — Required on Android 12 (API 31–32). Allows `setExactAndAllowWhileIdle()` with user-granted permission.
- `USE_EXACT_ALARM` — Required on Android 13+ (API 33+). A stricter version; the user must grant it from Settings → Alarms & Reminders. Both are declared for compatibility across Android versions.

```xml
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED"/>
```
- Allows `BootReceiver` to be woken up when the device finishes booting. Without this, device reboot silently kills all scheduled alarms and they are never rescheduled.

```xml
<activity android:name=".core.services.BlockActivity"
    android:excludeFromRecents="true"
    android:noHistory="true"
    android:exported="false"/>
```
- `excludeFromRecents="true"` — The block screen does not appear in the Recents (app switcher). This is intentional: we don't want the user to navigate back to the block screen via the recents menu.
- `noHistory="true"` — Once the user leaves BlockActivity (goes home or takes a break), it is removed from the back stack. Pressing Back after a break won't return to the block screen.
- `exported="false"` — No other app can launch this Activity by sending an Intent. Only FocusGuard itself can show the block screen (via the AccessibilityService).

```xml
<service android:name=".core.services.BlockerAccessibilityService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
```
- `android:permission="BIND_ACCESSIBILITY_SERVICE"` — Only the Android OS itself can bind this service. No other app can bind or start it directly. This is a security measure: only the system can connect to an AccessibilityService.

```xml
<meta-data android:name="android.accessibilityservice"
    android:resource="@xml/accessibility_service_config"/>
```
- Points to the XML file that configures the service: which events to receive, feedback type, etc.

---

## `accessibility_service_config.xml`

```xml
android:accessibilityEventTypes="typeWindowStateChanged"
```
- The service only receives `TYPE_WINDOW_STATE_CHANGED` events — fired when the foreground window changes (i.e., when the user switches apps or a new activity comes to front). By subscribing to only this event type, the service avoids receiving hundreds of irrelevant events (scroll events, click events, text change events). This is a critical **performance decision** — AccessibilityServices that subscribe to all events are notorious performance killers.

```xml
android:canRetrieveWindowContent="false"
```
- The service cannot read the content of windows — no text, no passwords, no clipboard. This is explicitly set to `false` as a **privacy and security guarantee**. Most spyware-style accessibility services set this to `true`. Setting it to `false` also means the service runs in a lighter mode.

```xml
android:notificationTimeout="0"
```
- The minimum delay between event deliveries. `0` means deliver events as fast as possible — zero artificial latency. For a blocking app, any delay means a frame or two of the blocked app is visible before the block screen appears.

---

## `SessionDataStore.kt`

### The `FocusSession` Data Class

```kotlin
data class FocusSession(
    val id: String,
    ...
)
```
**`data class`** in Kotlin automatically generates:
- `equals()` — two `FocusSession` objects with identical fields are considered equal.
- `hashCode()` — consistent hash for use in Sets/Maps.
- `toString()` — human-readable string for logging.
- `copy()` — creates a modified copy without mutating the original.

Why `data class` here? Because `FocusSession` is a pure value object — it represents data, not behavior. You never subclass it. You often need to compare two sessions or create modified copies (`session.copy(name = "New Name")`).

```kotlin
val id: String
```
Why `String` UUID instead of an `Int`? UUIDs (`"3f4a2b1c-..."`) are globally unique even if sessions are created on multiple devices or re-imported. An auto-incrementing `Int` would conflict if the user ever exports and re-imports sessions.

```kotlin
val repeatDays: Set<Int>  // 0=Mon … 6=Sun
```
`Set<Int>` instead of `List<Int>` because:
- A session cannot repeat on Monday twice. Sets enforce uniqueness.
- Set lookup (`contains()`) is O(1) for `HashSet`, which is used in the blocking loop running on every app switch.

```kotlin
fun durationMillis(): Long {
    val fromMs = (fromHour * 60L + fromMinute) * 60_000L
    var toMs   = (toHour   * 60L + toMinute)   * 60_000L
    if (toMs <= fromMs) toMs += 24 * 60 * 60_000L // crosses midnight
    return toMs - fromMs
}
```
This converts hours and minutes to milliseconds-from-midnight. The midnight-crossing check (`if toMs <= fromMs`) handles sessions like 22:00 → 02:00 by adding 24 hours to `toMs`. Without this, the duration would be negative. The `60_000L` uses Kotlin's **underscore in numeric literals** for readability (`60_000` = 60000).

```kotlin
fun toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    ...
}
```
**`apply` scope function** — runs the lambda block on the object and returns the object itself. This is equivalent to building the `JSONObject` step by step but reads more fluently. `apply` is used when you're configuring an object — you call methods on it and don't need a return value from the block.

**Why JSON and not Protocol Buffers or Room?**
- DataStore with Preferences stores key-value pairs. The session list is serialized to a JSON string and stored under one key. This avoids adding the Room database dependency (adds ~1MB to APK). The tradeoff: no querying, sorting, or indexing — but for a handful of sessions, this is fine.

```kotlin
companion object {
    fun fromJson(obj: JSONObject): FocusSession? = runCatching { ... }.getOrNull()
}
```
**`companion object`** — The Kotlin equivalent of Java's `static` members. `fromJson` is a factory function: it creates a `FocusSession` from a `JSONObject`. It lives on the companion object because it creates instances — it can't live on an instance.

**`runCatching { }.getOrNull()`** — Wraps the block in a try-catch. If any field is missing in the JSON (e.g., old data from a previous app version), returns `null` instead of crashing. This is Kotlin's functional approach to safe parsing.

### The DataStore Extension

```kotlin
private val Context.dataStore: DataStore<Preferences>
    by preferencesDataStore(name = "focus_sessions_datastore")
```
This is a **Kotlin extension property** on `Context`. `by preferencesDataStore(...)` uses **property delegation** — the actual property logic (lazy initialization, singleton enforcement) is implemented by the `preferencesDataStore` delegate from the Jetpack DataStore library.

Why an extension on `Context`? Because `DataStore` must be a singleton per file (the library enforces this). The extension property ensures that no matter how many times you write `context.dataStore`, you always get the same instance. Creating multiple `DataStore` instances for the same file corrupts it.

```kotlin
val sessionsFlow: Flow<List<FocusSession>> = context.dataStore.data
    .map { preferences ->
        val raw = preferences[KEY_SESSIONS] ?: return@map emptyList()
        try {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { FocusSession.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            emptyList()
        }
    }
```
**`Flow<List<FocusSession>>`** — A cold, asynchronous stream. Every time the DataStore updates (a session is saved), this Flow automatically re-emits the new list. Any collector (the ViewModel, the AccessibilityService) instantly gets the fresh data.

`map { }` — A Flow operator. It transforms each emitted value (raw `Preferences` object) into a new type (`List<FocusSession>`). This runs on whatever coroutine dispatcher collects the Flow.

`return@map emptyList()` — `return@map` is a **labeled return** in Kotlin. Without the label, `return` inside a lambda is ambiguous (does it return from the lambda or from the enclosing function?). The label `map` targets the `map { }` lambda specifically.

```kotlin
suspend fun saveSessions(sessions: List<FocusSession>) {
    context.dataStore.edit { preferences ->
        preferences[KEY_SESSIONS] = JSONArray(sessions.map { it.toJson() }).toString()
    }
}
```
**`suspend`** — This function can only be called from a coroutine or another suspend function. The `suspend` keyword is Kotlin's way of marking a function that may pause (block) without blocking a thread. `DataStore.edit` is itself a suspend function — it performs disk I/O, which would block if done on the main thread.

---

## `BlockerAccessibilityService.kt`

```kotlin
class BlockerAccessibilityService : AccessibilityService()
```
Inheriting from `AccessibilityService` registers this class with the Android accessibility framework. The OS (not the app) creates this service instance and calls its lifecycle methods.

```kotlin
@Volatile
private var activeSessionsCache: List<FocusSession> = emptyList()
```
**`@Volatile`** — A JVM annotation that guarantees that writes to this field are immediately visible to all threads. Without `@Volatile`, the Java Memory Model (JMM) allows a CPU to cache the value in a register — another thread reading `activeSessionsCache` might see a stale value.

Why is this needed? The service runs on **multiple threads simultaneously**:
- The `serviceScope` coroutine (IO thread) updates `activeSessionsCache`.
- `onAccessibilityEvent()` (called on the main thread by the OS) reads `activeSessionsCache`.

`@Volatile` prevents the main thread from reading a stale cached value from a CPU register.

```kotlin
private val exceptionHandler = CoroutineExceptionHandler { _, exception ->
    Log.e(TAG, "Coroutine crash: ${exception.message}", exception)
}

private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)
```
**`CoroutineScope`** — Defines the lifetime of a group of coroutines. If the scope is cancelled, all coroutines in it are cancelled.

**`SupervisorJob()`** — A special `Job` where a child coroutine failure does NOT cancel the parent or siblings. Without `SupervisorJob`, if the DataStore collection coroutine crashes, it would cancel the limit-polling coroutine too. With `SupervisorJob`, each child is isolated.

**`Dispatchers.IO`** — A thread pool optimized for blocking I/O (disk reads, network). Using it here means DataStore operations and UsageStats queries don't block the main thread.

**`CoroutineExceptionHandler`** — A last-resort handler for uncaught exceptions in the coroutine. Without it, an unhandled crash in a coroutine on a non-main dispatcher would silently terminate the coroutine with no error.

```kotlin
override fun onServiceConnected() {
    super.onServiceConnected()
    limitManager = LimitManager(this)
    sessionDataStore = SessionDataStore(this)

    serviceScope.launch {
        sessionDataStore.sessionsFlow.collect { sessionsList ->
            activeSessionsCache = sessionsList
        }
    }
```
**`onServiceConnected()`** — Called once by the OS when the user enables the accessibility service. This is the service's "onCreate" equivalent. We start collecting the DataStore Flow here so `activeSessionsCache` is always up-to-date.

**`collect { }`** — Terminal Flow operator. It suspends the coroutine and calls the lambda every time the Flow emits a new value. This runs forever (until the scope is cancelled), acting as a live pipe from DataStore to the in-memory cache.

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    try {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
```
**`?.toString()`** — Safe call on a nullable `CharSequence?`. If `packageName` is null, the `?.` operator short-circuits and returns null. The `?: return` (Elvis-return) exits the function early if null — avoiding a NullPointerException.

This is called on the **main thread** — the most critical constraint in this codebase. Any slow operation here (disk I/O, network, heavy computation) freezes the UI. That's why all blocking decisions use the in-memory cache.

```kotlin
private fun shouldBlockSynchronously(packageName: String): String? {
    if (packageName == this.packageName) return null
    if (packageName in NEVER_BLOCKABLE) return null
    if (packageName in ALWAYS_HIDDEN) return null
```
**`in` operator** — Kotlin's `in` calls `contains()`. For a `Set<String>`, this is O(1) — a hash lookup. This is a key performance choice: the NEVER_BLOCKABLE and ALWAYS_HIDDEN sets are checked first to bail out early without touching the sessions list.

```kotlin
val now = LocalDateTime.now()
val currentMs = (now.hour * 60 + now.minute) * 60000L + now.second * 1000L
val dayOfWeek = now.dayOfWeek.value - 1
```
**`LocalDateTime`** — Java 8 date/time API (available on Android via desugaring or API 26+). `now()` captures the current local time. Converting to milliseconds-from-midnight (`currentMs`) allows simple numeric range checking.

`dayOfWeek.value - 1` — `DayOfWeek.MONDAY.value` is 1, but the app uses 0=Monday internally. Subtracting 1 normalizes to 0-based indexing.

```kotlin
if (System.currentTimeMillis() < activeBreakExpiryCache) {
    return null // Currently in a temporary break
}
```
The break system: if a break is active, the expiry timestamp (stored in DataStore, cached in `activeBreakExpiryCache`) is in the future. Returning `null` here means "do not block" — effectively putting all sessions on pause for the break duration.

```kotlin
val isActive = if (toMs > fromMs) {
    currentMs in fromMs until toMs
} else {
    // Crosses midnight
    currentMs >= fromMs || currentMs < toMs
}
```
The midnight-crossing logic is the subtlest part of the blocking engine. For a normal session (e.g., 9:00 → 17:00), `toMs > fromMs` and we check `currentMs in 9h..17h`. For midnight-crossing sessions (e.g., 22:00 → 02:00), `toMs < fromMs`. The check becomes: is it after 22:00 OR is it before 02:00? Both halves of the night are valid.

---

## `SessionScheduler.kt`

```kotlin
private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
```
`getSystemService()` returns an `Object` (Java type). The `as AlarmManager` is a **Kotlin type cast** — it tells the compiler "treat this Object as AlarmManager". If the cast fails at runtime, it throws a `ClassCastException`. This is safe here because `ALARM_SERVICE` always returns an `AlarmManager`.

```kotlin
fun scheduleSession(session: FocusSession) {
    val nextStart = calculateNextTrigger(session, isStart = true)
    val nextEnd   = calculateNextTrigger(session, isStart = false)
    if (nextStart != null) setExactAlarm(session.id.hashCode(), nextStart, session.id, true)
    if (nextEnd != null)   setExactAlarm(session.id.hashCode() + 1, nextEnd, session.id, false)
}
```
Each session gets **two alarms**: one for start, one for end. The alarm ID (`requestCode`) is derived from `session.id.hashCode()`. The end alarm uses `hashCode() + 1` to avoid collisions. `String.hashCode()` returns a deterministic 32-bit integer for the same string — so the same session ID always produces the same alarm IDs, allowing cancellation by ID without keeping state.

```kotlin
if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    if (alarmManager.canScheduleExactAlarms()) {
        alarmManager.setExactAndAllowWhileIdle(...)
    } else {
        alarmManager.setAndAllowWhileIdle(...)
    }
}
```
**Runtime version checking** — `Build.VERSION.SDK_INT` is the API level of the device running the app. `Build.VERSION_CODES.S` is API 31 (Android 12). On Android 12+, exact alarms require explicit user permission. `canScheduleExactAlarms()` checks if that permission is granted. The fallback (`setAndAllowWhileIdle`) is an inexact alarm — it fires within a time window rather than at the exact millisecond, but works without the permission.

```kotlin
private fun calculateNextTrigger(session: FocusSession, isStart: Boolean): Long? {
    ...
    var nextDateTime = LocalDateTime.of(now.toLocalDate(), targetTime)
    if (nextDateTime.isBefore(now)) {
        nextDateTime = nextDateTime.plusDays(1)
    }
    var checks = 0
    while (checks <= 7) {
        val dayIndex = nextDateTime.dayOfWeek.value - 1
        if (session.repeatDays.contains(dayIndex)) {
            return nextDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }
        nextDateTime = nextDateTime.plusDays(1)
        checks++
    }
    return null
}
```
This algorithm finds the next calendar occurrence of the session. It starts from today and walks forward day-by-day (max 7 days = one full week) until it finds a day that's in `session.repeatDays`. Then it converts that `LocalDateTime` to epoch milliseconds for the AlarmManager API. `ZoneId.systemDefault()` ensures the alarm fires at the correct local time even in different time zones.

---

## `BootReceiver.kt`

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || ...) {
```
`BroadcastReceiver` is a component that receives system-wide broadcast messages. When the Android OS finishes booting, it sends `ACTION_BOOT_COMPLETED` to all receivers that have registered for it. The receiver is woken up, executes `onReceive()`, and then dies. It has no persistent lifecycle.

```kotlin
val sessions = runBlocking {
    sessionDataStore.sessionsFlow.firstOrNull() ?: emptyList()
}
```
**`runBlocking`** — Bridges the coroutine world and the blocking world. `BroadcastReceiver.onReceive()` is a regular (non-suspend) function. `runBlocking` creates a new coroutine and blocks the current thread until it completes. **`firstOrNull()`** collects only the first emission from the Flow and returns it — we don't need to observe ongoing changes; we just need the current snapshot.

**Important:** `runBlocking` in a `BroadcastReceiver` is acceptable because BroadcastReceivers have a ~10-second deadline. DataStore reads are fast (milliseconds). However, using `runBlocking` on the main thread in a long-running context (Activity, Service) would cause ANR crashes.

---

# 4. KOTLIN LANGUAGE CONCEPTS

## `data class`

**What it is:** A class for holding data. The `data` modifier auto-generates `equals()`, `hashCode()`, `toString()`, and `copy()`.

**Why used here:** `FocusSession`, `AppUsage`, `HomeState`, `BlocksState`, `SettingsState` are all data classes. They represent snapshots of state — you never mutate them; you create new copies with `copy()`. This is the foundation of **immutable state** in the MVVM pattern.

**Alternative:** A regular `class` without `data`. Then you'd have to manually implement `equals()` — and Compose's recomposition system relies on `equals()` to detect state changes.

## `sealed class`

**What it is:** A class hierarchy where all subclasses are defined in the same file. The compiler knows all possible subclasses.

**Used here:** `Screen` in `MainActivity.kt` is a `sealed class` with `object` subclasses.

```kotlin
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home     : Screen("home",     "Home",     Icons.Filled.Home)
    object Blocks   : Screen("blocks",   "Blocks",   Icons.Filled.Block)
    object Sessions : Screen("sessions", "Sessions", Icons.Filled.Timer)
    object Settings : Screen("settings", "Settings", Icons.Filled.Settings)
}
```

**Why sealed?** When you `when(screen)` over a sealed class, the compiler enforces exhaustiveness — you must handle all cases. If you add a new screen later and forget to add it to the navigation, the compiler reminds you. With a regular class hierarchy, the compiler cannot enforce this.

## `object`

**What it is:** A Kotlin singleton. The `object` keyword creates a class with exactly one instance, created lazily on first access.

**Two usages in this codebase:**

1. `companion object` inside a class — static members (constants, factory functions).
2. Standalone `object` — true singleton (e.g., `FGColors`, `Screen.Home`).

```kotlin
object FGColors {
    @Composable fun bg(dark: Boolean) = animateColorAsState(...).value
}
```
`FGColors` is a singleton holding color-resolving functions. There's no state to store — it's purely functional. Using a singleton avoids instantiation overhead.

## `suspend` Functions

**What it is:** A function that can pause its execution without blocking a thread.

```kotlin
suspend fun saveSessions(sessions: List<FocusSession>) {
    context.dataStore.edit { ... }
}
```

When `dataStore.edit { }` is called, it suspends the coroutine until the disk write completes, then resumes. During suspension, the thread is freed to do other work. This is why `suspend` functions feel "synchronous" to read but are non-blocking in execution.

**Why not callbacks?** Callbacks lead to "callback hell" — nested lambdas that are hard to read, hard to test, and hard to handle errors in. `suspend` functions read linearly.

**Alternative:** `LiveData` (from old Android development). `StateFlow` + `suspend` is preferred because it's not Android-coupled, works in pure Kotlin code, and has a richer operator API.

## Coroutine Scopes

Three scopes are used in this codebase:

| Scope | Lifetime | Used In |
|---|---|---|
| `viewModelScope` | Lives as long as the ViewModel | HomeViewModel, SessionsViewModel, etc. |
| `serviceScope` (custom) | Lives as long as the AccessibilityService | BlockerAccessibilityService |
| `CoroutineScope(Dispatchers.IO)` (ad-hoc) | Completes and is GC'd | BlockActivity break save |

**`viewModelScope`** is built into `ViewModel` via the `lifecycle-viewmodel-ktx` library. When the user navigates away and the ViewModel is cleared, `viewModelScope` is cancelled — preventing memory leaks from orphaned coroutines.

## Higher-Order Functions & Lambdas

```kotlin
sessions.forEach { session -> scheduler.scheduleSession(session) }
```
`forEach` is a higher-order function — it takes a lambda as a parameter. The `{ session -> ... }` syntax defines the lambda inline. In Kotlin, if the last parameter is a lambda, it can be moved outside the parentheses (trailing lambda syntax).

## Extension Functions

```kotlin
fun Modifier.clickableScale(...): Modifier = composed { ... }
```
An extension function on `Modifier`. It lets you write `modifier.clickableScale { ... }` anywhere, as if `clickableScale` were a built-in method. Extension functions don't modify the original class — they're syntactic sugar compiled to a static function with the receiver as the first parameter.

## Null Safety

```kotlin
val packageName = event.packageName?.toString() ?: return
```
- `?.` — Safe call. If `packageName` is null, short-circuit and return null instead of calling `toString()`.
- `?:` — Elvis operator. If the left side is null, use the right side. Here, `?: return` returns from the enclosing function.

Kotlin's null safety is enforced at compile time. The type system distinguishes `String` (never null) from `String?` (possibly null). You cannot accidentally dereference a null pointer — the compiler forces you to handle nullability explicitly.

## `@Volatile` and Thread Safety

Covered in the service explanation above. The key mental model: in a multi-core CPU, each core has its own register cache. `@Volatile` bypasses the cache and forces reads/writes to go directly to RAM, ensuring cross-thread visibility.

---

# 5. JETPACK COMPOSE DEEP EXPLANATION

## What Is Compose?

Jetpack Compose is Android's modern UI toolkit. Instead of XML layouts that describe the initial state of views (which you then mutate with `view.text = "..."`, `view.visibility = VISIBLE`, etc.), Compose uses **declarative UI** — you describe *what the UI should look like given the current state*, and Compose figures out the minimal changes needed to transform the current UI to the new description.

**Mental model:**
```
UI = f(State)
```
Every Compose screen is a function of its state. When state changes, the function is re-called (recomposition), and the UI updates automatically.

## Recomposition Mechanics

```kotlin
@Composable
fun FocusGuardApp(...) {
    val settingsState by settingsViewModel.state.collectAsState()
    ...
}
```

`collectAsState()` converts a `StateFlow<T>` into Compose `State<T>`. When the `StateFlow` emits a new value:
1. The `State<T>` is updated.
2. Compose marks any composable that *read* this state as "dirty".
3. On the next frame, dirty composables are recomposed.

**Key insight:** Only composables that **read** the state are recomposed. If `HomeScreen` doesn't read `settingsState`, it won't recompose when the theme changes.

## `remember` vs `rememberSaveable` vs `State`

```kotlin
val navController = rememberNavController()
```

- **`remember { }`** — Survives recompositions but NOT configuration changes (rotation). Once the composable leaves the composition, remembered values are lost.
- **`rememberSaveable { }`** — Survives recompositions AND configuration changes (rotation, process death). Uses the `SavedStateHandle` under the hood. Use this for UI state the user would find annoying to re-enter (e.g., a selected tab, a scroll position).
- **`mutableStateOf()`** — The raw Compose state type. Wrapping a value in `mutableStateOf()` makes Compose track reads/writes and trigger recomposition.

`rememberNavController()` uses `remember` internally — the NavController is recreated if the process dies. But its back-stack state is managed separately.

## Side Effects

### `LaunchedEffect`

```kotlin
LaunchedEffect(startRoute) {
    if (startRoute != Screen.Home.route) {
        navController.navigate(startRoute)
    }
}
```

`LaunchedEffect` runs a suspend block as a side effect. It's "launched" once when the composable first enters the composition, and again whenever its **key** (`startRoute`) changes. The coroutine is cancelled and restarted if the key changes.

**Why not just call `navController.navigate()` directly in the composable body?** Because composable bodies can be called many times (recompositions). Running navigation on every recomposition would push new routes on every frame. `LaunchedEffect` with a stable key ensures navigation is only triggered once.

### `SideEffect`

```kotlin
SideEffect {
    val window = (view.context as Activity).window
    window.statusBarColor = bg.toArgb()
}
```

`SideEffect` runs after every successful recomposition. Unlike `LaunchedEffect`, it's not a coroutine – it runs synchronously. Used here to sync the status bar color with the current theme — every time the theme changes (recomposition), the status bar is updated.

### `DisposableEffect` (pattern used elsewhere)

Runs a setup block when a composable enters composition, and the returned `onDispose` block when it leaves. Used for registering/unregistering listeners, starting/stopping animations.

## Navigation Architecture

```kotlin
val navController = rememberNavController()
NavHost(navController, startDestination = Screen.Home.route) {
    composable(Screen.Home.route)     { HomeScreen(...) }
    composable(Screen.Blocks.route)   { BlocksScreen(...) }
    composable(Screen.Sessions.route) { SessionsScreen(...) }
    composable(Screen.Settings.route) { SettingsScreen(...) }
    composable("privacy")             { PrivacyScreen(...) }
}
```

`NavHost` creates a composable container for navigation. Each `composable(route)` block is a destination. The NavController manages the back stack — pressing Back pops the top destination.

```kotlin
navController.navigate(screen.route) {
    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true
    restoreState = true
}
```
- `popUpTo(startDestination) { saveState = true }` — When navigating between bottom-nav tabs, clear the back stack back to the start destination. This prevents a deep back stack when the user taps nav items multiple times. `saveState = true` caches the state of the popped composables.
- `launchSingleTop = true` — Don't create a new instance if this destination is already the top of the back stack.
- `restoreState = true` — Restore any previously saved state for this destination.

## Theme System

```kotlin
MaterialTheme(colorScheme = colorScheme) {
    Scaffold(...) { ... }
}
```

`MaterialTheme` is a `CompositionLocal` provider — it injects the color scheme, typography, and shapes into the entire sub-tree. Any composable inside can access `MaterialTheme.colorScheme.primary` without needing it passed as a parameter.

`CompositionLocal` is Compose's version of dependency injection — implicit passing of values down the composition tree. Use it for things that are "ambient" (like theme, locale, density) rather than explicit data.

## The Modifier Chain System

```kotlin
Modifier
    .fillMaxWidth()
    .padding(16.dp)
    .background(color)
    .clickable { ... }
```

Modifiers are applied in order — the sequence matters. `padding` before `background` means the background fills the padded area. `background` before `padding` means the background extends behind the padding.

Each `.modifier()` call returns a new `Modifier` instance — modifiers are immutable. The chain is compiled into an efficient linked list that Compose can traverse during layout.

---

---

# 6. ANDROID SYSTEM APIS USED

## UsageStatsManager

**What it is:** A system service (API 21+) that lets privileged apps query the system's app-usage event log — the same log used by Digital Wellbeing and parental controls.

**How it works internally:** Android continuously writes every foreground transition event to a private SQLite database maintained by the `UsageStatsService` system process. `UsageStatsManager` is the public interface to that database.

**Two functions used in this project:**

1. `queryEvents(start, end)` → raw stream of `ACTIVITY_RESUMED`, `ACTIVITY_PAUSED`, `SCREEN_INTERACTIVE`, `SCREEN_NON_INTERACTIVE`, `DEVICE_SHUTDOWN` events. Used in `UsageStatsDataSource.getSotByPackage()` for accurate Screen-On Time.

2. `queryAndAggregateUsageStats(start, end)` → returns a `Map<String, UsageStats>` with `totalTimeInForeground` pre-aggregated. Used in `BlockerAccessibilityService.updateLimitReachedCache()` for limit checking because it's faster than re-processing the raw event stream.

**Why `totalTimeInForeground` is inaccurate for tracking:** It counts time even when the screen is off. If you open Instagram and lock the screen, the API continues counting. FocusGuard's custom event-stream parser only counts time when `isScreenOn = true`.

**Permission check — why `AppOpsManager` instead of `ContextCompat.checkSelfPermission`:**
```kotlin
val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
val mode = appOps.unsafeCheckOpNoThrow(
    AppOpsManager.OPSTR_GET_USAGE_STATS,
    Process.myUid(), context.packageName
)
return mode == AppOpsManager.MODE_ALLOWED
```
`PACKAGE_USAGE_STATS` is a "signature/privileged" AppOps permission — not a runtime permission. `checkSelfPermission()` always returns DENIED even when granted. The correct check is via `AppOpsManager`. `unsafeCheckOpNoThrow` (API 29+) replaces the deprecated `checkOpNoThrow`.

**Security implication:** This permission exposes the user's full app-usage history. An abusive app could profile the user's daily routine. FocusGuard never transmits this data. Play Store requires a privacy disclosure for apps declaring this permission.

---

## AccessibilityService

**What it is:** An Android framework for apps that assist users with disabilities. Screen readers (TalkBack), switch access, and voice control all use it. FocusGuard repurposes it for foreground app detection.

**How it works:** When enabled, the OS registers the service as a system event listener. Every foreground window transition triggers a `TYPE_WINDOW_STATE_CHANGED` event dispatched by the window manager to the service on its process's main thread.

**Lifecycle:**
```
User enables in Settings → onServiceConnected() called → events flow
User disables in Settings OR OS kills process → onInterrupt() called → events stop → blocking stops
```

**Why `TYPE_WINDOW_STATE_CHANGED` only (not all events):**
Subscribing to all events generates thousands of calls per second (every scroll, every keystroke). This causes massive CPU/battery overhead and is why some accessibility services cause severe slowdowns. Subscribing only to window-state-changed limits firing to meaningful events: app switches, Activity launches.

**`canRetrieveWindowContent="false"` — the privacy and performance flag:**
When `true`, the OS builds the full view hierarchy (all text, passwords, buttons) for the service on every event. When `false`, the service only receives the package name and window title — which is all FocusGuard needs. This setting: (1) prevents reading passwords/messages, (2) reduces memory allocation per event, (3) demonstrates to Play Store reviewers the service is not a keylogger.

**`notificationTimeout="0"` — why zero?**
This is the minimum delay between event deliveries. Zero means deliver events as fast as the system can. For a blocking app, any delay allows the blocked app's screen to flash briefly before the block screen appears.

---

## AlarmManager

**What it is:** A system service for scheduling future code execution, surviving app process death.

**The alarm type hierarchy:**
```
set()                          — Inexact, Doze-deferred, deprecated for time-critical use
setExact()                     — Exact time, Doze can defer it
setExactAndAllowWhileIdle()    — Exact even during Doze — what FocusGuard uses
setAlarmClock()                — Highest priority, shows in the clock app, wakes screen
```

**Doze mode:** When device is still + unplugged + screen off, Android enters Doze. Standard alarms are batched into "maintenance windows" (every 15 min to several hours). `setExactAndAllowWhileIdle()` fires exactly at the scheduled time, bypassing Doze. Required for a session scheduler.

**Android 12+ permission change:** Exact alarms require the user to grant `SCHEDULE_EXACT_ALARM` in Settings → Alarms & Reminders. FocusGuard gracefully falls back to `setAndAllowWhileIdle()` (inexact) if permission is denied — sessions will start within a few minutes of the scheduled time rather than exactly on time.

**PendingIntent — what it is and why it's needed:**
```kotlin
PendingIntent.getBroadcast(context, requestCode, intent,
    FLAG_UPDATE_CURRENT or FLAG_IMMUTABLE)
```
A `PendingIntent` is a token. It says: "When this alarm fires, the OS should send this Intent as if it were sent by my app." The OS holds this token; when the alarm fires, the OS redeems the token and sends the Intent — even if the app process is dead. `FLAG_IMMUTABLE` (required on API 31+): the Intent's extras cannot be modified by the component that receives it, preventing Intent injection attacks.

---

## BroadcastReceiver

**What it is:** A component that wakes up to handle a system broadcast, executes in ~10 seconds, and dies. No persistent lifecycle.

**`BootReceiver`** receives `ACTION_BOOT_COMPLETED`. All AlarmManager alarms are erased on device reboot. The BootReceiver re-reads all sessions from DataStore and re-registers every alarm. Without this, a device reboot permanently disables all session blocking.

**`QUICKBOOT_POWERON`** — A Xiaomi/Qualcomm broadcast for fast-boot (the device resumes from deep sleep without a full reboot). FocusGuard listens to both to handle all restart scenarios.

**`runBlocking` in a BroadcastReceiver — is it safe?**
```kotlin
val sessions = runBlocking {
    sessionDataStore.sessionsFlow.firstOrNull() ?: emptyList()
}
```
`BroadcastReceiver.onReceive()` is not a suspend function. `runBlocking` bridges the divide by blocking the current (system) thread until the coroutine completes. Acceptable here because DataStore reads complete in milliseconds and the receiver's 10-second window is generous. NOT acceptable in Activities, ViewModels, or long-running services — it would block the main thread and cause ANR.

---

## Background Execution Limits (Android 8+)

Since Android 8.0, apps cannot: start background services arbitrarily, run background services without a foreground notification, or receive most implicit broadcasts while in the background.

FocusGuard is immune to these limits because:
- `AccessibilityService` is a **system-bound service**, permanently exempted.
- Alarms fire into `BroadcastReceiver`, which has its own exempted execution window.
- No background Service (beyond the OS-managed accessibility service) is ever started.

This means FocusGuard shows NO persistent notification — the "FocusGuard is running" notification common in other blockers is unnecessary here.

---

# 7. BLOCKING SYSTEM ARCHITECTURE

## Decoupled Detection and Enforcement

The blocking engine has two completely independent components:

**Component 1 — The Scheduler (passive):**
Sets alarms. When an alarm fires, reschedules the next one. Never activates or deactivates blocking state. Never writes "session is active: true" anywhere.

**Component 2 — The Blocker (deterministic):**
On every app switch: loads sessions from memory, checks `currentTime in session.fromMs..session.toMs` mathematically. If the current app is in a currently-active session's blocked list → block. Otherwise → pass. Always recomputes. Never reads a state flag.

**Why this matters:** If the blocker relied on a "session_active" flag set by the scheduler, then:
- Scheduler fails to fire → flag not set → blocking silently fails.
- Process restarts → flag resets → blocking fails until next alarm fires.
- Race condition: flag set at T=0, process killed at T=1, restarted at T=2 → blocking broken for the remainder of the session.

With the pure-math approach: process restarts → reloads sessions from DataStore → immediately knows what to block. No state corruption possible.

## Full Event Flow Diagram

```
Device clock reaches 09:00
  └─ AlarmManager fires intent → SessionAlarmReceiver.onReceive()
       └─ reads session from DataStore (runBlocking + firstOrNull)
       └─ calls SessionScheduler.scheduleSession(session)
            └─ calculateNextTrigger() → next Monday 09:00
            └─ setExactAlarm(nextMondayEpochMs)

Meanwhile, AccessibilityService is running continuously:
  User opens Instagram (com.instagram.android)
    └─ OS dispatches TYPE_WINDOW_STATE_CHANGED
    └─ onAccessibilityEvent(event)
         └─ shouldBlockSynchronously("com.instagram.android")
              └─ not self-package ✓
              └─ not NEVER_BLOCKABLE ✓  
              └─ not ALWAYS_HIDDEN ✓
              └─ break not active (currentTime > expiryCache is false) ✓
              └─ for each session in activeSessionsCache:
                   └─ isToday? dayOfWeek(Mon=0) in session.repeatDays? Yes
                   └─ currentMs(09:30) in [09:00, 11:00)? Yes
                   └─ "com.instagram.android" in session.blockedPackages? Yes
                   └─ return "Morning Study"
         └─ launchBlockActivity("com.instagram.android", "session", "Morning Study")
              └─ Intent with FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
              └─ BlockActivity appears instantly (< 1 frame delay)
```

## Block Screen (BlockActivity)

`BlockActivity` is a standard `AppCompatActivity` — not an overlay window. This is a deliberate design decision:

- **No `SYSTEM_ALERT_WINDOW` permission needed** — Overlay windows (`TYPE_APPLICATION_OVERLAY`) require a special permission users find alarming and Play Store scrutinizes heavily.
- **Full-screen enforcement** — An Activity covers the entire screen by definition. Overlay windows can be dismissed or drawn over by other apps in some OEM configurations.
- **FLAG_ACTIVITY_CLEAR_TASK** — The blocked app's task stack is cleared. Pressing Back on the block screen goes home, not back to Instagram.
- **`noHistory="true"` in manifest** — Once the block screen is dismissed, it cannot be navigated back to via system Back.
- **`excludeFromRecents="true"` in manifest** — The block screen does not appear in the Recents (app switcher) drawer.

## Break System Architecture

```
User taps "Take a Break" → AlertDialog with [2min, 5min, 10min]
→ User selects 5 minutes
→ expiry = System.currentTimeMillis() + (5 * 60 * 1000)
→ SessionDataStore.setBreakExpiry(expiry) [DataStore write on IO thread]
→ DataStore emits new breakExpiryFlow value
→ BlockerAccessibilityService.serviceScope.collect receives it
→ activeBreakExpiryCache = expiry
→ BlockActivity.finish() → user returns to Instagram
→ All subsequent app-switch checks: System.currentTimeMillis() < expiry → return null
→ At T+5min, expiry is in the past → blocking resumes automatically
```

The break requires NO background timer, NO WorkManager job, NO foreground service. It is purely timestamp-based — the same "is the current time within this range?" pattern used for sessions.

---

# 8. USAGE TRACKING LOGIC

## Why Custom Event-Stream Processing?

`UsageStats.totalTimeInForeground` is wrong for human-readable "screen time" because it counts time when the screen is off. FocusGuard implements Screen-On Time (SoT) — the union of: (app is in foreground) AND (screen is interactive).

## Event State Machine

The parser maintains three variables across the event stream:

```
isScreenOn: Boolean = true (assume screen on at start of day)
lastResumedPkg: String? = null (currently-foreground app)
lastResumedTime: Long = 0L (when it came to foreground)
```

**Event: ACTIVITY_RESUMED (App B opens while App A was open):**
```
if (isScreenOn):
    duration = event.timeStamp - lastResumedTime
    usageMap[lastResumedPkg] += duration   ← credit App A
    lastResumedPkg = App B
    lastResumedTime = event.timeStamp
```

**Event: ACTIVITY_PAUSED or ACTIVITY_STOPPED (App A pauses):**
```
if (lastResumedPkg == App A):
    duration = event.timeStamp - lastResumedTime
    usageMap[App A] += duration            ← credit App A
    lastResumedPkg = null
```

**Event: SCREEN_NON_INTERACTIVE (screen turned off):**
```
isScreenOn = false
duration = event.timeStamp - lastResumedTime
usageMap[lastResumedPkg] += duration       ← credit last app up to screen-off
lastResumedPkg = null                      ← no app accumulates time while screen is off
```

**After loop (app still in foreground):**
```
duration = min(endTime, now) - lastResumedTime
usageMap[lastResumedPkg] += duration       ← credit currently-open app
```

This processes the event stream in O(n) time where n is the number of events — typically a few hundred per day. Memory is O(apps) for `usageMap` — a few dozen entries.

## System App Filtering — Three-Pass Approach

Pass 1 — Static set (`ALWAYS_HIDDEN`): Known system packages hardcoded. Fast O(1) hash lookup.

Pass 2 — Dynamic launcher detection:
```kotlin
val homeIntent = Intent(ACTION_MAIN).addCategory(CATEGORY_HOME)
val homeInfos = pm.queryIntentActivities(homeIntent, MATCH_DEFAULT_ONLY)
```
Adds the OEM's current default launcher (MIUI Home, One UI, etc.) even if it's not in the hardcoded list.

Pass 3 — FLAG_SYSTEM without launcher icon:
```kotlin
val isSystem = (appInfo.flags and FLAG_SYSTEM) != 0
if (isSystem && !launcherPackages.contains(appInfo.packageName)) hidden.add(...)
```
System apps that have no launcher icon are background providers/services — they should never appear in usage lists.

**Caching:** `hiddenPackagesCache` is populated once on first call and reused. The `PackageManager` queries are slow (~50ms). Caching ensures they don't run on every refresh.

---

# 9. SCHEDULING ARCHITECTURE

## AlarmManager vs WorkManager — The Decision

| Criterion | AlarmManager | WorkManager |
|---|---|---|
| Timing precision | Exact to millisecond | ±15 minutes minimum |
| Doze behavior | Bypasses with `AllowWhileIdle` | Respects Doze; defers |
| Persistence across reboot | No (must re-register) | Yes (built-in) |
| Use case | Time-triggered tasks | Condition-triggered tasks |

A session scheduled for 09:00 AM must start at 09:00 AM, not 09:12 AM. WorkManager's "expedited tasks" come close but are still subject to system constraints. **AlarmManager is the only correct tool.**

## Idempotent Rescheduling

Every call to `scheduleSession(session)` is idempotent:
```kotlin
setExactAlarm(session.id.hashCode(), nextStart, session.id, isStart=true)
setExactAlarm(session.id.hashCode() + 1, nextEnd, session.id, isStart=false)
```
`PendingIntent.getBroadcast()` with `FLAG_UPDATE_CURRENT` and the same `requestCode` updates the existing alarm instead of adding a duplicate. Calling `scheduleSession()` twice on the same session just replaces both alarms with identical ones — no side effect. This idempotency is critical for the BootReceiver: it calls `scheduleSession()` for every session without checking whether alarms already exist.

## Request Code Collision Prevention

```kotlin
session.id.hashCode()     // start alarm
session.id.hashCode() + 1 // end alarm
```
`String.hashCode()` is deterministic and stable for a given string. Two different sessions could theoretically have the same `hashCode()` (hash collision). The probability is low with UUID-based IDs (~`1/(2^32)`) but non-zero. A production improvement would use a stable sequential ID instead of hash-derived codes.

## OEM Mitigation Strategy

Aggressive OEM power managers (Xiaomi MIUI, Samsung OneUI aggressive battery mode) can:
1. Kill the AccessibilityService process.
2. After killing, not restart it automatically.

Mitigations used:
- AlarmManager alarms survive process kills (stored in the OS kernel).
- BootReceiver re-registers alarms after any device restart.
- Post-boot notification if the service is found disabled.

Mitigations NOT used (yet):
- Foreground notification to prevent process kill.
- Device admin power management exemption.
- WorkManager as a watchdog to restart the service.

---

# 10. STATE MANAGEMENT ARCHITECTURE

## Unidirectional Data Flow

```
User action (click) → ViewModel function → state update → UI recomposition
```

The UI never directly modifies data. It calls ViewModel functions. ViewModels update `_state`. The updated state flows down to the UI automatically via `collectAsState()`.

This makes debugging trivial: log the state transitions and you have a complete history of what happened and why.

## ViewModel State Modeling

```kotlin
data class HomeState(
    val appUsage: List<AppUsage> = emptyList(),
    val screenTimeSummary: ScreenTimeSummary = ScreenTimeSummary(0L),
    val peakDistraction: PeakDistraction? = null,
    val selectedTab: HomeTab = HomeTab.TODAY,
    val ringSegments: List<RingSegment> = emptyList(),
    val weeklyData: List<WeeklyDayUsage> = emptyList(),
    val weeklyAverage: Long = 0L,
    val showAll: Boolean = false,
    val selectedDayIndex: Int? = null,
    val selectedDayApps: List<AppUsage> = emptyList()
)
```

All UI state for the Home screen lives in one `data class`. Benefits:
- Single `StateFlow<HomeState>` emission → one `collectAsState()` call in the composable.
- `copy()` makes partial updates easy: `_state.value = _state.value.copy(showAll = true)`.
- The default values make the "empty/loading" state implicit — no separate `isLoading: Boolean` needed.
- Nullable types (`peakDistraction: PeakDistraction?`) express "not yet loaded" cleanly.

## MutableStateFlow — Internal Mechanics

`MutableStateFlow(initial)` is a hot Flow that:
- Always holds the current/latest value (even if no collector is listening).
- Skips emissions if the new value equals the old value (`equals()` comparison using the data class).
- Thread-safe: multiple threads can read `.value` simultaneously.
- On Android, must emit new state on the main thread (or use `.update { }` which is thread-safe).

```kotlin
_state.update { it.copy(sessions = sessionsList) }
```
`.update { }` is an atomic update — it reads the current value, applies the lambda, and writes the result atomically. Prevents a race condition where two coroutines both read the same old state and overwrite each other.

## Manual DI in MainActivity

FocusGuard does not use a DI framework (Hilt/Koin):
```kotlin
val dataSource = UsageStatsDataSource(this)
val repository = UsageRepositoryImpl(this, dataSource)
val useCase = GetDailyUsageUseCase(repository)
val limitManager = LimitManager(this)
val homeViewModel = HomeViewModel(useCase, repository)
```

**Why no Hilt?** For this app's size, Hilt adds:
- A ~5MB compile-time annotation processor.
- Generated code that is hard to read for learners.
- A learning curve not justified by project complexity.

The manual DI is explicit: every dependency is visible, traceable, and understandable. For a larger app (multiple modules, many ViewModels), Hilt would be essential.

---

# 11. LIBRARY-BY-LIBRARY EXPLANATION

## `compose-bom:2024.06.00` (Bill of Materials)

**Problem it solves:** Compose is a family of 15+ independently versioned libraries. Mismatching a single version causes runtime crashes. The BOM declares a tested, compatible set of versions. You write `implementation("androidx.compose.ui:ui")` without a version — the BOM fills it in.

**Runtime cost:** The Compose runtime has a ~30–50ms initialization overhead on first composition. Subsequent compositions are fast (millisecond-range recompositions).

**APK impact:** +3–4MB before R8 shrinking. After R8: ~1–2MB (unused Compose APIs removed).

## `material3`

**What it solves:** Provides the full Material 3 (Material You) design system: `Scaffold`, `NavigationBar`, `Card`, `Button`, `TopAppBar`, `Text`, type scales, color roles, shape systems.

**Color role system:** Material3 defines semantic color roles: `primary`, `onPrimary`, `background`, `surface`, `error`, etc. Components use roles, not hardcoded colors. Set `primary = FG_Seg0` once; all FABs, selected indicators, and progress bars automatically use that color.

**Why `darkColorScheme` / `lightColorScheme` instead of dynamic color?**
Dynamic color (Monet) extracts a palette from the user's wallpaper (Android 12+). FocusGuard uses a curated green brand palette instead — brand consistency over user personalization.

## `navigation-compose:2.7.7`

**What it solves:** A declarative back-stack manager. Handles: composable routing, animating between screens, deep-links, back-stack save/restore, and `popBackStack()`.

**How the back stack works:**
```
Start: [Home]
Navigate to Sessions: [Home, Sessions]
Navigate to Settings: [Home, Sessions, Settings]
Press Back: [Home, Sessions]
```
With `popUpTo(startDestination) { saveState = true }` on bottom-nav tabs, the stack is pruned: navigating Home→Blocks→Sessions→Home results in `[Home]` not `[Home, Blocks, Sessions, Home]`.

**APK impact:** ~+500KB.

## `lifecycle-viewmodel-ktx:2.7.0`

**What it solves:** `viewModelScope` coroutine scope, automatically cancelled when `ViewModel.onCleared()` is called (navigating away, process death). Prevents memory leaks from orphaned coroutines.

Without `viewModelScope`, you'd need:
```kotlin
private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
override fun onCleared() { scope.cancel() }
```
`lifecycle-viewmodel-ktx` does this for you.

## `lifecycle-runtime-compose:2.7.0`

**What it solves:** Adds `collectAsStateWithLifecycle()`. Standard `collectAsState()` collects from a Flow even when the app is in the background (screen off, app backgrounded). `collectAsStateWithLifecycle()` pauses collection below `STARTED` lifecycle state — saving CPU when the UI is not visible.

**FocusGuard currently uses `collectAsState()`** for simplicity. Upgrading is a safe, non-breaking improvement.

## `kotlinx-coroutines-core` + `kotlinx-coroutines-android:1.7.3`

**`coroutines-core`:** The base library providing `launch`, `async`, `Flow`, `Channel`, `Mutex`, `SupervisorJob`, coroutine scopes.

**`coroutines-android`:** Adds `Dispatchers.Main` targeting Android's main (Looper) thread. Without this, `Dispatchers.Main` throws a `RuntimeException`.

**Dispatcher selection in this codebase:**
| Dispatcher | Used for |
|---|---|
| `Dispatchers.Main` | State updates to `MutableStateFlow` exposed to UI |
| `Dispatchers.IO` | DataStore reads/writes, UsageStats queries |
| `Dispatchers.Default` | `evaluateActiveSession()` math loop (runs every second) |

## `androidx.datastore:datastore-preferences`

**Problem it solves:** SharedPreferences is synchronous, not thread-safe (commits can block the main thread on `commit()`), and susceptible to file corruption on crash. DataStore is fully async, crash-safe (atomic writes), and Flow-reactive.

**Atomic write mechanism:** DataStore writes to a temp file, then atomically renames it to the main file. If the process crashes mid-write, the main file is untouched. SharedPreferences `commit()` is not crash-atomic.

## `material-icons-extended:1.6.8`

**What it is:** ~2000+ Material icons as `ImageVector` objects (vector paths, not image files).

**APK Impact Warning:** The unshrunken AAR is ~4MB. R8 removes unreferenced icons during minification. In debug builds (no R8), this adds ~4MB to the APK. In release builds with R8 enabled, only referenced icons remain. FocusGuard uses only 4 icons (`Home`, `Block`, `Timer`, `Settings`).

## `appcompat:1.7.0`

**Why still used if the app is Compose?** `BlockActivity` extends `AppCompatActivity` (not `ComponentActivity`) and inflates a traditional XML layout (`block_overlay.xml`). `AppCompatActivity` provides backward-compatible AlertDialog, themes, and the action bar system. Moving `BlockActivity` to `ComponentActivity` + Compose would allow removing this dependency.

---

---

# 12. PERFORMANCE & MEMORY DESIGN

## Recomposition Cost

The single most common performance mistake in Compose is triggering unnecessary recompositions. FocusGuard avoids this with:

**1. Single state object per screen:**
Each screen collects one `StateFlow<ScreenState>`. This means one `collectAsState()` per screen. Compose's recomposition is scoped — only composables that READ state are recomposed. If a composable deep in the tree doesn't read `appUsage`, it won't recompose when `appUsage` changes.

**2. `data class` equality checks:**
`MutableStateFlow` compares old and new values with `equals()`. Because all state is `data class`, `equals()` performs deep structural comparison. Emitting the same state twice causes ZERO recompositions — the StateFlow discards duplicates.

**3. Color animation overhead:**
```kotlin
@Composable fun bg(dark: Boolean) =
    animateColorAsState(if (dark) FG_BgDark else FG_BgLight, tween(250)).value
```
`animateColorAsState` creates an animation that interpolates between two colors over 250ms. Each running animation triggers a recomposition per frame (~60fps = 60 recompositions per second during the transition). This is expected and normal for smooth color changes. The animation only runs during theme switching — a rare user action.

**4. Ring segment pre-computation:**
The ring chart's sweep angles are computed ONCE in the ViewModel and stored in `ringSegments: List<RingSegment>`. The Compose canvas draws directly from pre-computed angles — no per-frame math in the UI layer.

## Background Service Cost (AccessibilityService)

**CPU impact of `onAccessibilityEvent`:**
The method runs on the main thread. Its entire execution path must be fast:
- `shouldBlockSynchronously` does: 3 set lookups + one `LocalDateTime.now()` + a list iteration over sessions (typically < 10 items). Total: ~0.1ms. Safe for the main thread.
- No I/O, no coroutine launches, no `suspend` calls in the hot path.

**Memory impact of DataStore collection:**
The `sessionsFlow.collect` coroutine holds a reference to `activeSessionsCache: List<FocusSession>`. Each `FocusSession` is ~500 bytes of data. For 20 sessions, the cache is ~10KB — negligible.

**The 10-second limit polling loop:**
```kotlin
while (true) {
    updateLimitReachedCache()
    delay(10000L)
}
```
Every 10 seconds, `queryAndAggregateUsageStats()` is called on the IO thread. This is a blocking call taking ~5–20ms on modern hardware. Running it every 10 seconds means 0.2% of IO-thread time is spent on usage polling — not measurable in real-world battery tests. The alternative (checking limits on every app switch) would add 5–20ms of blocking IO to the main thread on each event, causing UI jank.

**`@Volatile` vs `AtomicReference` — Why `@Volatile` is enough:**
`@Volatile` guarantees visibility (all threads see the latest write). It does NOT guarantee atomicity (read-modify-write operations are not atomic). For the cache variables, only the IO thread writes and the main thread reads — there is no concurrent read-modify-write. Therefore `@Volatile` alone is sufficient. `AtomicReference` would be needed if multiple threads both read AND wrote the same variable.

## Usage Stats Query Optimization

`queryEvents()` is I/O-bound: it queries the OS's internal SQLite database. The event log grows throughout the day (~1000 events/hour for active users). Querying a full day from midnight to now takes 5–50ms depending on device and day length.

FocusGuard calls this function:
- On every `HomeViewModel.loadUsage()` (triggered on screen open and pull-to-refresh).
- Once per day offset in `getWeeklyUsage()` (7 calls when weekly tab is first opened).
- Every 10 seconds in the accessibility service for limit checking (`queryAndAggregateUsageStats` — the pre-aggregated version, faster than `queryEvents`).

All calls are on `Dispatchers.IO` — never blocking the main thread.

**Memory model of `queryEvents`:** Returns a `UsageEvents` object backed by a cursor to the system's database. Events are streamed one-by-one via `getNextEvent(event)`. The single `UsageEvents.Event` object is reused across iterations (value-object pattern). Memory is O(1) regardless of the number of events.

---

# 13. SECURITY & PRIVACY ARCHITECTURE

## Permission Justification

Every permission in `AndroidManifest.xml` has a specific necessity:

| Permission | Minimum required for? | Could be removed if? |
|---|---|---|
| `PACKAGE_USAGE_STATS` | Reading screen-time events | App had no usage tracking feature |
| Accessibility Service | Real-time app blocking | App used polling instead (worse privacy) |
| `SCHEDULE_EXACT_ALARM` | Session start/end precision | ±15min accuracy was acceptable |
| `RECEIVE_BOOT_COMPLETED` | Post-reboot alarm re-registration | Sessions were only manual (no auto-start) |
| `POST_NOTIFICATIONS` | Accessibility-disabled warning | Notifications were undesirable |
| `QUERY_ALL_PACKAGES` | App picker in session builder | Session builder showed only pre-listed apps |

## Data Isolation Strategy

```
App private storage (inaccessible without root):
  /data/data/com.focusguard/files/datastore/focus_sessions_datastore.preferences_pb
  → All FocusSessions (JSON)
  → Break expiry timestamp

  Shared preferences:
  /data/data/com.focusguard/shared_prefs/limit_preferences.xml
  → Per-app daily limits (package → ms)

  /data/data/com.focusguard/shared_prefs/settings_prefs.xml
  → Theme mode, blocking toggle
```

All storage is in the app's private data directory. On Android, this directory is protected by the Linux DAC (Discretionary Access Control) — only the app's own UID can read/write. Even if another app requests this data, the kernel denies it.

**What's NOT stored:** No user identifiers, no timestamps of individual blocks, no names of blocked apps, no usage history beyond what the OS already stores in its system usage log.

## Accessibility Service Attack Surface

The AccessibilityService is the highest-privilege component. An adversarial analysis:

**What it CAN do:**
- Read the package name of every app that comes to the foreground.
- Launch `BlockActivity` as a new task.

**What it CANNOT do (because of `canRetrieveWindowContent="false"`):**
- Read text in text fields (passwords, messages).
- Read clipboard content.
- Simulate taps or gestures (`performGlobalAction` is not declared in the config).
- Read window content hierarchy.

**Code audit point:**
```kotlin
if (packageName in com.focusguard.data.repository.UsageRepositoryImpl.NEVER_BLOCKABLE) return null
```
Critical safety check: the phone dialer, messaging, and contacts apps can never be blocked. This prevents a scenario where a blocking session could prevent emergency calls.

## Play Store Policy Compliance

Google's policy (2023 update) requires AccessibilityService apps to:
1. Declare a core accessibility purpose — FocusGuard's purpose (focus/productivity assistance) is accepted.
2. Set `canRetrieveWindowContent="false"` if content reading is not needed — done.
3. Provide a clear in-app description of what the service does — done via `@string/accessibility_service_description`.
4. Not collect or transmit user data — FocusGuard has no internet permission (not even declared).

**No internet permission** is the strongest possible privacy statement — the OS literally cannot transmit any data from this app.

---

# 14. FAILURE & EDGE CASE HANDLING

## What Happens If the System Kills the App?

**Process killed, AccessibilityService was running:**
- AlarmManager alarms survive — they are stored in the kernel, not the app process.
- The next alarm fires → `SessionAlarmReceiver.onReceive()` → calls `scheduleSession()`.
- If the AccessibilityService is restarted by the OS (usual behavior when killed by the system, not by force-stop): it reconnects, calls `onServiceConnected()`, reloads sessions from DataStore into the cache, and resumes blocking.
- If the OS does NOT restart the service (OEM aggressive kill, force-stop): blocking is permanently suspended until the user opens the app or re-enables the service.

## What Happens After Reboot?

```
Device reboots
  └─ OS sends ACTION_BOOT_COMPLETED (20–60 seconds after device unlock)
  └─ BootReceiver.onReceive()
      └─ reads all sessions from DataStore (runBlocking)
      └─ for each session: SessionScheduler.scheduleSession(session)
          └─ calculateNextTrigger() → finds next alarm time
          └─ setExactAlarm()
      └─ checks if AccessibilityService is enabled
          └─ NO → sends notification "FocusGuard Disabled - Tap to re-enable"
```

**Gap during reboot:** If a session was scheduled for 09:00 AM and the phone was rebooted at 08:55 AM, the BootReceiver fires at ~09:15 AM. The alarm would be rescheduled for the NEXT occurrence of the session (tomorrow or next week), missing today's session. The blocking engine would not block during 09:00–boot completion even if the session is active in time.

**Mitigation (current):** When `calculateNextTrigger` runs at 09:15 AM for a session from-09:00 to-11:00, it finds `nextTrigger = tomorrow 09:00` (because 09:15 > 09:00 today). However, the AccessibilityService, once restarted, DOES block correctly — it recomputes mathematically. The session IS active at 09:15. The alarm just doesn't need to fire for the current window.

## Permission Revocation Handling

**Usage Stats permission revoked mid-session:**
- `UsageStatsDataSource.getSotByPackage()` returns an empty map.
- `UsageRepositoryImpl.getDailyUsage()` returns an empty list.
- `HomeViewModel` state shows 0 minutes for all apps.
- No crash — the `try/catch` in `SessionDataStore.sessionsFlow` and graceful empty-list defaults handle this.
- `LimitManager`'s limit checking in the service also fails gracefully: `queryAndAggregateUsageStats()` returns empty → `limitReachedCache = emptySet()` → no limits enforced.

**Accessibility Service disabled:**
- `onInterrupt()` is called (or the process is simply killed).
- Blocking stops immediately.
- No recovery without user action.
- BootReceiver detects this on next reboot and notifies the user.

## Session Overlap Handling

What if two sessions are active simultaneously and both block the same app?

```kotlin
for (session in activeSessionsCache) {
    ...
    if (isActive && packageName in session.blockedPackages) {
        return session.name   // returns on FIRST match
    }
}
```

The first matching session in the list "wins" — its name appears on the block screen. The block screen shows one session name. Both sessions are enforced (once block is triggered, it doesn't matter which session triggered it). The UI's `allActiveSessions` list in `SessionsViewModel` shows ALL currently active sessions to inform the user.

## JSON Deserialization Corruption Handling

```kotlin
companion object {
    fun fromJson(obj: JSONObject): FocusSession? = runCatching { ... }.getOrNull()
}
```

If a `FocusSession` JSON object is malformed (missing field, wrong type), `fromJson` returns `null`. The `mapNotNull` in `sessionsFlow` skips nulls:
```kotlin
(0 until arr.length()).mapNotNull { FocusSession.fromJson(arr.getJSONObject(it)) }
```
A single corrupted session entry does not crash the entire sessions list — only that entry is skipped. This is graceful degradation.

---

# 15. INTERVIEW DEFENSE SECTION

## Architecture Questions

**Q: Why MVVM over MVP or MVI?**
A: MVVM with StateFlow gives us the reactive state management of MVI without the boilerplate of explicit Intent/Action classes. The ViewModel's data binding to Compose StateFlow is tighter than MVP's manual View updates. For this app's scale, explicit MVI Actions would add ceremony without meaningful benefit. Scaling to MVI would make sense if the team grew and strict separation of side effects from state transitions became necessary.

**Q: Why not use Hilt for dependency injection?**
A: For a single-module app with 4 ViewModels and a clear dependency graph, manual DI in `MainActivity.kt` is explicit, readable, and has zero runtime overhead (no reflection). Hilt's code generation and learning curve are not justified here. The same dependency graph in Hilt would require `@Singleton`, `@ActivityScoped`, `@Provides`, and generated `HiltComponent` classes — not appropriate for a learning project.

**Q: Why is FocusSession serialized to JSON instead of using Room?**
A: The sessions collection is small (typically < 20 items), never queried by SQL predicates (no "give me sessions where blockedPackages contains X"), and accessed as a complete list. Room adds ~1MB to the APK and requires a schema, DAOs, entities, and migration strategies — over-engineering for this use case. DataStore with JSON serialization is simpler, sufficient, and involves fewer dependencies.

**Q: How does the app handle the database being larger than memory?**
A: All storage in this app (DataStore, SharedPreferences) stores only configuration data — sessions and settings — that is never more than a few KB. Usage statistics are not stored; they are queried from the OS on demand. The OS's usage event log has its own retention and size management handled by the system.

## System Design Questions

**Q: If you scaled FocusGuard to 10 million users, what would you change?**
A:
1. **BackEnd sync:** Add encrypted cloud sync for sessions — users switch devices. Use an API key–based scheme; never store data in plaintext.
2. **Room database:** Migrate from JSON/DataStore to Room to support richer queries (sessions by tag, usage analytics over weeks/months).
3. **WorkManager watchdog:** Add a WorkManager periodic task that checks if the AccessibilityService is running and sends a re-enable notification if not.
4. **Analytics (privacy-first):** On-device ML to suggest focus sessions based on detected peak distraction times, without sending raw data to a server.
5. **Widget + Quick Tile:** Allow session toggling from the home screen and notification shade.

**Q: Why does the AccessibilityService use an in-memory cache instead of reading from DataStore on every event?**
A: DataStore reads are asynchronous — they require a coroutine and take 1–5ms of IO time. `onAccessibilityEvent` runs on the main thread; any suspend call or blocking IO there would cause UI jank and risk ANR (Application Not Responding) warnings. The in-memory cache allows the check to complete in <0.1ms synchronously. The cache is kept fresh by a persistent `collect` coroutine on the IO thread — any DataStore update is reflected in the cache within milliseconds.

**Q: What is the blocking latency — how quickly does the block screen appear?**
A: The latency is bounded by:
1. Window manager event dispatch: ~1 frame (16ms at 60fps).
2. `onAccessibilityEvent` synchronous processing: ~0.1ms.
3. `startActivity(BlockActivity)`: ~30–100ms (Activity creation, layout inflation, draw).

Total: ~50–120ms. The blocked app's content may render for one or two frames before the block screen covers it. This is acceptable for a non-exploitable scenario — users cannot interact with the blocked app in that window.

## Kotlin Concept Questions

**Q: What is the difference between `val` and `var` in Kotlin?**
A: `val` declares an immutable reference — once assigned, the reference cannot be changed. `var` declares a mutable reference. In this codebase, almost everything is `val` — the state itself is replaced (via `copy()`), not mutated in place. Immutability prevents accidental state corruption and makes the flow of data traceable.

**Q: Explain `@Volatile` and when you need it.**
A: `@Volatile` annotates a JVM field to prevent CPU register caching. In a multi-core system, each core may cache field values in its own L1 cache. Without `@Volatile`, Thread A may write a new value but Thread B reads a stale cached value. `@Volatile` forces all writes and reads to go through main memory, guaranteeing cross-thread visibility. Needed here because the IO thread writes `activeSessionsCache` and the main thread reads it in every `onAccessibilityEvent` call.

**Q: What is the difference between `launch` and `async`?**
A: Both create coroutines. `launch` is fire-and-forget — it returns a `Job` that you can cancel or wait for, but has no result value. `async` returns a `Deferred<T>` — you call `.await()` to get the result. Use `launch` when you don't need a return value (saving to DataStore, updating state). Use `async` when you need to run tasks concurrently and collect their results.

**Q: What is a `Flow` and how is it different from `LiveData`?**
A: Both are observable data holders. Differences:
- `Flow` is pure Kotlin (no Android dependency). `LiveData` requires the Android lifecycle library.
- `Flow` is cold by default — it doesn't produce values until collected. `StateFlow` is hot — it always has a current value.
- `Flow` has a rich operator API: `map`, `filter`, `debounce`, `combine`, `flatMapLatest`, `zip`.
- `LiveData` is lifecycle-aware out of the box (stops delivering when UI is STOPPED). `Flow` requires `collectAsStateWithLifecycle()` to achieve the same.
- Coroutine cancellation works natively with Flow; LiveData has no concept of structured concurrency.

## Compose Internals Questions

**Q: What triggers recomposition?**
A: Reading a `State<T>` (or `StateFlow` via `collectAsState()`) inside a composable function. When that state value changes (via `equals()` comparison), Compose marks the composable reading it as "invalid" and schedules it for recomposition on the next frame. Only the reading composable and its children is recomposed — not the entire tree.

**Q: What is the difference between `remember` and `rememberSaveable`?**
A: `remember` survives recompositions but NOT process recreation (rotation, system-initiated process death). `rememberSaveable` additionally survives process recreation by saving to the `SavedStateRegistry` (similar to `onSaveInstanceState`). Use `rememberSaveable` for anything the user would find frustrating to re-enter after rotation — form fields, scroll position, selected tab. Use `remember` for derived state or objects that are cheap to recompute.

**Q: Why does FocusGuard compute the Material3 ColorScheme inside the composable instead of at the ViewModel level?**
A: Color scheme is UI state — it belongs in the composition tree, not in business logic. The ViewModel knows the `ThemeMode` (LIGHT/DARK/SYSTEM). The composable computes the `ColorScheme` from that mode and the system dark-mode value. This is correct separation of concerns: the ViewModel knows the *preference*, the UI knows how to *render* that preference.

## OS-Level Questions

**Q: What is Doze mode and how does FocusGuard handle it?**
A: Doze is Android's power-saving state triggered when the device is stationary, unplugged, and screen-off. In Doze, the OS defers: network access, background syncs, standard alarms. `setExactAndAllowWhileIdle()` is specifically exempt from Doze deferral — it fires at the exact scheduled time even in Doze. FocusGuard uses this for all session alarms.

**Q: Why does the AccessibilityService survive when the UI Activity is destroyed?**
A: The `Activity` and the `AccessibilityService` are different Android components running in the same process but with independent lifecycles. The `Activity` is destroyed when the user navigates away (back-pressed). The `AccessibilityService` lifecycle is controlled by the Accessibility framework — it lives as long as the user has it enabled in Settings. They share a process, but the service's lifecycle is not tied to any Activity.

**Q: How does Android prevent other apps from starting `BlockActivity`?**
A: `android:exported="false"` in the manifest. The Android OS enforces this at the Intent resolution level — when another app sends an Intent targeting `BlockActivity`, the `PackageManager` refuses to resolve it and throws `ActivityNotFoundException`. Only components within the same app (same package, same signing certificate) can start non-exported Activities.

---

# 16. MENTAL MODEL SUMMARY

## How to Think About This App as a System

Forget files and classes for a moment. Think about three independent machines running in parallel:

```
MACHINE 1: The Scheduler (passive)
  Input:  User-defined sessions (time + repeat rules + app list)
  Output: AlarmManager registrations
  Trigger: Session CRUD operations, device reboot
  Runs: Only when triggered (not continuously)

MACHINE 2: The Blocker (reactive)
  Input:  Every foreground app transition (OS event)
  Output: Block screen or nothing
  Trigger: Every app switch (user-initiated)
  Runs: Continuously while Accessibility Service is enabled

MACHINE 3: The Reporter (on-demand)
  Input:  OS usage event log
  Output: Screen-time summaries for the UI
  Trigger: User opens the app / pulls to refresh
  Runs: Only when the user is looking at the home screen
```

These three machines are connected only through **DataStore** as a shared data bus. The Scheduler writes sessions → DataStore → Blocker reads sessions. The Reporter reads from the OS, not DataStore.

This loose coupling is the core architectural choice. If Machine 2 (the Blocker) crashes, Machines 1 and 3 continue working correctly. If Machine 3 (the Reporter) crashes, blocking is unaffected. The system degrades gracefully.

## How to Think Like the System Designer

When someone asks "add feature X", run it through this mental model:

**What layer does X live in?**
- Pure calculation? → Domain layer (use case or model method).
- Stores or reads data? → Data layer (DataStore, SharedPreferences, new datasource).
- Runs in the background without the UI? → Core layer (receiver, service, scheduler).
- Shows UI? → UI layer (screen + viewmodel).

**Does X need a new permission?**
- Always minimize permissions. Each new permission is a user trust hurdle and a Play Store policy risk.

**Does X interact with the main thread?**
- If yes, it must be synchronous and fast (<1ms). Move any heavy work to IO/Default dispatcher.

**Does X need to survive reboots or process kills?**
- AlarmManager for time-based triggers.
- DataStore for persistent state.
- BootReceiver for post-reboot recovery.

## How to Extend to Production Scale

**Reliability improvements:**
1. Add a WorkManager "heartbeat" check every 15 minutes: if the AccessibilityService is not running, send a notification.
2. Consider a lightweight ForegroundService with an important-priority notification to prevent OEM kills — traded against user annoyance.

**Feature extensions:**
1. **Allow-list mode:** Instead of blocking all apps except listed ones, allow only certain apps during a session. Invert the `blockedPackages` check.
2. **Usage-triggered sessions:** "Start focus mode automatically if I've used Instagram for more than 30 minutes today." This adds a new Machine 4 to the system — a periodic checker that evaluates usage thresholds and activates sessions programmatically.
3. **Widgets & Quick Settings Tile:** Subscribe to session state changes via `DataStore` + update a `RemoteViews` widget. The `TileService` API provides a Quick Settings entry point.

**Testing strategy:**
- **Unit tests:** Domain models and use cases (no Android) — run on JVM.
- **Integration tests:** Repository + DataStore with an in-memory DataStore test implementation.
- **UI tests:** Compose semantics-based tests using `composeTestRule` from the `ui-test` library.
- **Manual tests:** Blocking behavior — must be tested on a physical device because emulators don't dispatch `TYPE_WINDOW_STATE_CHANGED` events accurately.

---

## Final Checklist: What You Should Now Be Able to Explain

After reading this document you should be able to:

- [ ] Draw the full data flow from "user opens a blocked app" to "block screen appears" without notes.
- [ ] Explain why `@Volatile` is needed on `activeSessionsCache` specifically.
- [ ] Explain the midnight-crossing session logic mathematically.
- [ ] Explain why AlarmManager alarms must be re-registered after reboot.
- [ ] Explain what `canRetrieveWindowContent="false"` prevents.
- [ ] Explain the difference between the two alarm types and when each is used.
- [ ] Explain why `runBlocking` is used in `BootReceiver` but would be an ANR bug in an Activity.
- [ ] Explain why all state is `data class` and how `copy()` fits in MVVM.
- [ ] Explain what `SupervisorJob` does differently from a regular `Job`.
- [ ] Explain how `DataStore` achieves crash-safety that `SharedPreferences` does not.
- [ ] Explain the three-pass system app filtering strategy.
- [ ] Defend every permission in the manifest with a one-sentence justification.
- [ ] Explain the break system without mentioning a timer — only timestamps.
- [ ] Describe two ways to improve blocking reliability on OEM-restricted devices.
- [ ] Describe what changes if you replaced manual DI with Hilt — pros and cons.

---

*LEARN.md — FocusGuard Educational Reference | Generated 2026-03-30*
