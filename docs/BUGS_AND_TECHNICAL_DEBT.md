# Bugs and Technical Debt

The single authoritative list of defects, risks and debt. Entries come from reading the source,
from running Android lint (`./gradlew lintDebug`) and from runs on an Android 16 (API 36) emulator.
Status is `Open` unless marked `Fixed`; see the [fix log](#fix-log). "Confirmed" means "proven from
source or tooling output"; only entries marked *Reproduced* were seen failing at runtime (B-002, B-003).

**Confidence scale:** *Confirmed* (the code or tool output proves it) · *Reproduced* (seen failing at
runtime - none yet) · *Strongly suspected* (follows from documented platform behaviour, not yet run) ·
*Needs verification*.
**Severity:** Critical / High / Medium / Low / Informational.

Concept background is in [ANDROID_CONCEPTS.md](ANDROID_CONCEPTS.md); component context in
[ARCHITECTURE.md](ARCHITECTURE.md).

## Summary

| ID | Sev | Confidence | Title |
|---|---|---|---|
| [B-001](#b-001) | High | Confirmed (lint) | `java.time` used on `minSdk 21` without desugaring |
| [B-002](#b-002) | High | **Reproduced** - *Fixed* | ViewModels bypass the ViewModelStore; infinite loops never cancelled |
| [B-007](#b-007) | High | Confirmed | Signing passwords hardcoded in tracked `build.gradle.kts` |
| [B-003](#b-003) | Medium | **Reproduced** - *Fixed* | "Enable App Blocking" setting is not enforced |
| [B-005](#b-005) | Medium | Strongly suspected | A break does not suppress *limit* blocks |
| [B-006](#b-006) | Medium | Needs verification | Home usage and limit enforcement use different algorithms |
| [B-008](#b-008) | Medium | Strongly suspected | `runBlocking` in receivers; service scope never cancelled |
| [B-009](#b-009) | Medium | Confirmed (lint) - *Fixed* | `SettingsViewModel` retains an Activity |
| [B-010](#b-010) | Medium | Confirmed | Block screen: blank-screen path, ignores `breakMinutes`, `onBackPressed` lint error |
| [B-011](#b-011) | Medium | Strongly suspected | App picker hides pre-installed (FLAG_SYSTEM) apps |
| [B-013](#b-013) | Medium | Confirmed | No real automated tests |
| [B-015](#b-015) | Medium | Strongly suspected | `POST_NOTIFICATIONS` never requested |
| [B-023](#b-023) | Medium | Confirmed - *Fixed (delete)* | Sessions cannot be deleted or disabled from the UI |
| [B-004](#b-004) | Low | Strongly suspected | Midnight-crossing sessions: wrong-day check + UI forbids them |
| [B-012](#b-012) | Low | Confirmed | Alarm subsystem does no enforcement; request-code collisions possible |
| [B-014](#b-014) | Low | Confirmed | Layering / duplication debt |
| [B-016](#b-016) | Low | Confirmed | Repository hygiene (tracked logs, untracked large files) |
| [B-017](#b-017) | Low | Confirmed | Placeholder UI and docs/README drift |
| [B-018](#b-018) | Low | Confirmed | Form and sub-navigation state not saved |
| [B-020](#b-020) | Low | Strongly suspected | Whole-list DataStore rewrite; silent loss on parse errors |
| [B-021](#b-021) | Low | Confirmed | Weekly chart never refreshes |
| [B-022](#b-022) | Low | Confirmed | Hidden-package cache never invalidated, unsynchronised |
| [B-019](#b-019) | Info | Confirmed | Release/policy posture |

---

## <a id="b-001"></a>B-001 - `java.time` on `minSdk 21` without core-library desugaring

- **Severity / confidence:** High / Confirmed by lint (85 `NewApi` errors after the session rule was de-duplicated; 92 before), not run on an API 21-25 device.
- **Where:** `SessionScheduler`, `BlockerAccessibilityService`, `SessionsViewModel`, `SessionsScreen`, `SessionDataStore`.
- **Evidence:** `./gradlew lintDebug` -> "Call requires API level 26, or core library desugaring (current min is 21)".
  `app/build.gradle.kts` has `minSdk = 21` and no `isCoreLibraryDesugaringEnabled`. The build passes only
  because `lint { abortOnError = false }`.
- **Impact:** **[I]** On API 21-25 the first use of `LocalDateTime` throws `NoClassDefFoundError`; the service catches
  `Exception` but not `Error`, so blocking would crash or fail. Also `setExactAndAllowWhileIdle` needs API 23.
- **Remediation options:** (a) enable desugaring + `coreLibraryDesugaring("com.android.tools:desugar_jdk_libs")`;
  (b) raise `minSdk` to 26 (simplest, drops ~1% of devices; README must change).
- **Risks:** (a) adds a dependency; (b) changes supported devices. **Verify:** `lintDebug` has 0 `NewApi`; run on an API 24 emulator.
- **Status:** Open.

## <a id="b-002"></a>B-002 - ViewModels created without a `ViewModelStore`; loops never cancelled

- **Severity / confidence:** High / Reproduced on an emulator.
- **Where:** `MainActivity.onCreate` (plain constructors for Home/Blocks/Sessions); `SessionsViewModel.init`.
- **Evidence:** three ViewModels are built with `HomeViewModel(...)`, `BlocksViewModel(...)`, `SessionsViewModel(...)`; none uses
  `ViewModelProvider`/`viewModels`. `AndroidManifest` has no `configChanges`. `SessionsViewModel.init` launches two collectors
  and a `while (true) { ...; delay(1000L) }` loop in `viewModelScope`.
- **Impact:** **[I]** Rotation or theme change recreates the Activity: screen state resets and a new set of three
  coroutines starts while the old set keeps running (nothing calls `onCleared`). CPU/battery drain and leaked references grow with each recreation.
- **Repro:** emulator (API 36), app open, rotate 12 times via `adb shell settings put system user_rotation`, force a GC (`run-as com.focusguard kill -10 <pid>`), then `adb shell dumpsys meminfo com.focusguard`: **Activities: 9** (3 after the first 6 rotations) - one extra leaked `MainActivity` per rotation.
- **Fix:** commit "fix: create ViewModels through the ViewModelStore and use application context". ViewModels are now obtained with `ViewModelProvider` + `viewModelFactory { initializer { } }`, and every collaborator is built from `applicationContext` (otherwise a retained ViewModel would hold the Activity through the repository/scheduler). The `delay()` in the 1 Hz loop is cancellable, so no extra change was needed there.
- **Verified after the fix:** same procedure, 12 rotations -> **Activities: 1**. State retention across rotation and process death were not separately tested.
- **Status:** Fixed.

## <a id="b-003"></a>B-003 - "Enable App Blocking" has no effect

- **Severity / confidence:** Medium / Reproduced on an emulator.
- **Where:** `SettingsScreen` ToggleRow ("Pause all limits temporarily."), `SettingsViewModel.toggleBlocking` writing `blocking_enabled`.
- **Evidence:** `grep -rn "blocking_enabled\|isBlockingEnabled" app/src/main` finds the value only in the Settings UI/ViewModel; neither
  `BlockerAccessibilityService` nor `BlockActivity` reads it.
- **Impact:** The user believes blocking is paused; it is not. Trust and safety (users trying to access an app urgently).
- **Repro:** emulator, accessibility service enabled, `limit_preferences.xml` giving the Clock app a 1 ms limit, `settings_prefs.xml` with `blocking_enabled=false`: opening Clock still showed `BlockActivity`.
- **Fix:** commit "fix: make the Enable App Blocking setting pause blocking". New `data/local/BlockingPreference` (file name, key, `isEnabled`) is used by both `SettingsViewModel` and `BlockerAccessibilityService`, which returns early from `onAccessibilityEvent` when the switch is off (this pauses sessions and limits). Stored values are unchanged.
- **Verified:** same setup, switch off -> Clock opens normally; switch on -> still blocked. Unit tests for the helper (`BlockingPreferenceTest`). Not tested: toggling while a block screen is already showing.
- **Status:** Fixed.

## <a id="b-004"></a>B-004 - Midnight-crossing sessions: duplicated predicate, wrong-day check, UI forbids creation

- **Severity / confidence:** Low (latent) / Strongly suspected.
- **Where:** `FocusSession.remainingMillisAt` (`domain/rules/SessionRules.kt`, formerly duplicated in `BlockerAccessibilityService.shouldBlockSynchronously` and `SessionsViewModel`), `SessionScheduler.calculateNextTrigger`, `CreateSessionScreen` validation.
- **Evidence:** the weekday/date check uses *today*, then the time window wraps. For a Monday 22:00-02:00 session, at Tuesday 01:00 "today" is Tuesday, which is not selected, so the window is judged inactive. Separately, `endBeforeStartErr = toMins <= fromMins` stops the UI from ever creating such a session.
- **Impact:** None today through the UI (cannot create), but the model/service claim to support it; any future relaxation of the UI check will ship a bug. README "Current Limitations" describes this incorrectly.
- **Progress:** the service and ViewModel copies were merged into `FocusSession.remainingMillisAt` (`domain/rules/SessionRules.kt`) and `SessionRulesTest` pins the current behaviour, including the two "QUIRK" cases. `SessionScheduler.calculateNextTrigger` is still separate.
- **Remediation:** decide the product behaviour (allow midnight sessions or not), then make `remainingMillisAt` also consider the previous day and update the quirk tests.
- **Status:** Open (rule centralised and characterised; behaviour unchanged).

## <a id="b-005"></a>B-005 - A break does not suppress limit blocks

- **Severity / confidence:** Medium / Strongly suspected.
- **Where:** `BlockerAccessibilityService.onAccessibilityEvent`, `BlockActivity` unblock handler.
- **Evidence:** the break check is inside `shouldBlockSynchronously` (sessions only); the `limitReachedCache` branch never consults `activeBreakExpiryCache`. `BlockActivity` offers "Unblock manually" for `block_source == "limit"` too.
- **Impact:** Tapping "Unblock manually" on a limit block sets a break, then the very next window event re-blocks the app: a loop that looks broken. (If limits are meant to be non-bypassable, the button should be hidden for them.)
- **Remediation:** decide the product rule, then either hide the button for `limit` or honour the break in both branches.
- **Status:** Open.

## <a id="b-006"></a>B-006 - Two different usage numbers

- **Severity / confidence:** Medium / Needs verification.
- **Where:** `UsageStatsDataSource.getSotByPackage` (UI) vs `BlockerAccessibilityService.updateLimitReachedCache` (`queryAndAggregateUsageStats().totalTimeInForeground`).
- **Impact:** Home may show 55 min while the limit (60 min) already triggers - or the reverse. The commit message for `8457d3f` and the README say the custom algorithm exists *because* `totalTimeInForeground` is inflated, yet enforcement uses it. The poll adds up to ~10 s latency.
- **Verify:** on a device compare both numbers for the same app (`adb shell dumpsys usagestats`).
- **Remediation:** one shared function used by both; consider evaluating limits on window-change events with a short cache.
- **Status:** Open.

## <a id="b-007"></a>B-007 - Signing credentials hardcoded in a tracked file

- **Severity / confidence:** High / Confirmed.
- **Where:** `app/build.gradle.kts` `signingConfigs.release` (store and key password literals); in git history since before `8457d3f`. `app/focusguard.keystore` is untracked and **not** in `.gitignore` (one `git add .` away from being committed). The remote is public GitHub.
- **Impact:** Anyone who obtains the keystore file can sign updates as this app; the passwords are in public history. The values are not repeated here on purpose.
- **Remediation (needs owner approval - history and key rotation are outward-facing):** read secrets from `local.properties`/environment; add `*.keystore`/`*.jks` to `.gitignore`; if the keystore was ever published or distributed, generate a new one (Play App Signing makes this practical). Do **not** rewrite history without explicit approval.
- **Status:** Open - awaiting decision.

## <a id="b-008"></a>B-008 - `runBlocking` in receivers; service scope never cancelled

- **Severity / confidence:** Medium / Strongly suspected.
- **Where:** `SessionAlarmReceiver.onReceive`, `BootReceiver.onReceive`; `BlockerAccessibilityService.serviceScope`.
- **Impact:** Main-thread blocking on a DataStore read inside a receiver (usually fast, but a cold DataStore on a busy device can approach the receiver time limit). `serviceScope` has no `onDestroy`/`onUnbind` cancel: if the system recreates the service, an old poll loop can keep running.
- **Remediation:** use `goAsync()` + a short-lived scope in receivers; cancel `serviceScope` in `onDestroy`.
- **Status:** Open.

## <a id="b-009"></a>B-009 - `SettingsViewModel` retains an Activity

- **Severity / confidence:** Medium / Confirmed by lint (`StaticFieldLeak`) and code.
- **Where:** `SettingsViewModelFactory(this)` in `MainActivity` passes the Activity; `SettingsViewModel(private val context: Context)`.
- **Impact:** **[I]** after rotation the retained ViewModel holds the destroyed Activity (memory leak, and stale-context bugs).
- **Fix:** commit "fix: keep only the application context in SettingsViewModel" (stores `context.applicationContext`; `@SuppressLint("StaticFieldLeak")` with a comment because lint cannot distinguish it). With the B-002 change the factory is also given the application context.
- **Verified:** `lintDebug` no longer reports `StaticFieldLeak`; the Activity count stays at 1 after rotation (see B-002).
- **Status:** Fixed.

## <a id="b-010"></a>B-010 - Block screen behaviour

- **Severity / confidence:** Medium / Confirmed (from source + lint).
- **Where:** `BlockActivity`.
- **Details:**
  1. `intent.getStringExtra(...) ?: return` runs *after* `setContentView`, leaving a dim overlay with dead buttons if extras are missing.
  2. "Unblock manually" (uncommitted edit) always grants a fixed 5-minute **global** break, ignoring `FocusSession.breakMinutes` (stored, edited in the form, never read by enforcement) and offering no friction. README still says 2/5/10 minutes.
  3. `onBackPressed()` override without `super` is a lint **error** (`MissingSuperCall`) and deprecated; use `OnBackPressedDispatcher`.
  4. The write to DataStore uses an unowned `CoroutineScope(Dispatchers.IO)`, then `finish()` - **[I]** the break may be written after the next accessibility event is evaluated.
- **Remediation:** validate extras first and `finish()`; use `breakMinutes` of the blocking session; decide on friction; fix back handling.
- **Status:** Open.

## <a id="b-011"></a>B-011 - App picker excludes pre-installed apps

- **Severity / confidence:** Medium / Strongly suspected.
- **Where:** `SessionsViewModel.loadInstalledApps` filters `flags and FLAG_SYSTEM == 0`.
- **Impact:** On many devices Chrome, YouTube, Gmail etc. are system (or updated-system) apps, so they cannot be added to a session - while the Blocks (limits) list *does* include them (it lists apps with a launcher icon). Inconsistent, and defeats the main use case on those devices.
- **Remediation:** reuse the repository's launcher-icon-based filter for both.
- **Verify:** on a device where YouTube is preinstalled.
- **Status:** Open.

## <a id="b-012"></a>B-012 - Alarm subsystem does not enforce anything

- **Severity / confidence:** Low / Confirmed.
- **Where:** `SessionAlarmReceiver` (re-calls `scheduleSession`, ignores `EXTRA_IS_START`); `SessionScheduler` request codes `id.hashCode()` and `+1`.
- **Impact:** Complexity and a permission surface (exact alarms, boot receiver) with no behavioural payoff beyond waking the process; README overstates it. Two different sessions can collide if `hashCode()` of one equals the other's plus one (astronomically unlikely with UUIDs, but possible). Editing a session to a past one-time date leaves the old alarms armed (harmless).
- **Remediation:** either remove the alarms (and `SCHEDULE/USE_EXACT_ALARM`, shrinking policy risk) or make them do useful work (notifications at start/end).
- **Status:** Open - product decision.

## <a id="b-013"></a>B-013 - No real tests

- **Severity / confidence:** Medium / Confirmed.
- **Evidence:** `app/src/test/.../ExampleUnitTest.kt` is the template `2 + 2`; `ExampleInstrumentedTest` is the template; both are in package `com.example.focusguard` while the app is `com.focusguard`.
- **Impact:** Most logic (trigger calculation, usage state machine) is still unprotected against regression.
- **Progress:** 24 JVM tests added (`SessionRulesTest` 16, `FocusSessionJsonTest` 5, `BlockingPreferenceTest` 3). Still untested: `SessionScheduler.calculateNextTrigger`, the usage state machine, ViewModels, persistence, any UI.
- **Remediation:** continue per [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md#testing-strategy).
- **Status:** Open (partly addressed).

## <a id="b-014"></a>B-014 - Layering and duplication debt

- **Severity / confidence:** Low / Confirmed.
- **Details:** `FocusSession` in `data.local`; use case depends on a concrete repository; service reads `UsageRepositoryImpl` companion; `HomeScreen` defines private colour constants duplicating `ui/theme`; `ui.blocks` imports `ui.home.formatTime`; `HomeEvent` and `ScreenTimeSummary.focusTime` are unused; `durationMillis()` is unused; version dependencies are split between the catalog and inline strings (the lint baseline file lists 18 `UseTomlInstead` entries); BlockActivity uses XML views with hard-coded colours.
- **Status:** Open (opportunistic).

## <a id="b-015"></a>B-015 - Notification permission never requested

- **Severity / confidence:** Medium / Strongly suspected.
- **Where:** `AndroidManifest.xml` declares `POST_NOTIFICATIONS`; no `requestPermissions`/`ActivityResultContracts` call exists in the source.
- **Impact:** On Android 13+ the post-boot "FocusGuard Disabled" notification is dropped, so the user is never told blocking is off after a reboot.
- **Remediation:** request the permission from Settings (with a rationale) and show a persistent in-app warning when the service is off.
- **Status:** Open.

## <a id="b-016"></a>B-016 - Repository hygiene

- **Severity / confidence:** Low / Confirmed.
- **Evidence:** tracked: `full_logcat.txt` (~700 KB), `logcat_fg.txt`, `crash_log*.txt`, `actual_crash.txt`, `anr_log.txt`, `build_errors_new.txt`, `output.txt`. Untracked clutter: `ktlint.exe` (66 MB), `-files`, `*.log`, `deps*.txt`, `release_notes.txt`, `.vscode/`.
- **Impact:** Logs may contain device/user data and bloat history; binaries risk accidental commit.
- **Remediation:** extend `.gitignore` (safe, reversible); `git rm --cached` the tracked logs only with owner approval.
- **Status:** Open. (The only `.gitignore` change made so far is ignoring the local-only interview guide.)

## <a id="b-017"></a>B-017 - Placeholder UI and documentation drift

- **Severity / confidence:** Low / Confirmed.
- **Details:** Settings shows version "1.0.0" while `versionName = "0.5"`; "Check for Updates" fakes a check with `delay(1500)`; README says no background polling loop (the service polls every 10 s and `SessionsViewModel` loops at 1 Hz), says AlarmManager "triggers sessions", lists a 2/5/10-minute break, and says the UI lacks a midnight warning (it blocks such sessions outright).
- **Status:** README corrected in this docs work; UI items Open.

## <a id="b-018"></a>B-018 - Form and sub-navigation state not saved

- **Severity / confidence:** Low / Confirmed.
- **Details:** `CreateSessionScreen` fields and `SessionNav` use `remember`, so a rotation discards a half-filled session and returns to the list.
- **Remediation:** `rememberSaveable` with savers, or hoist the draft into the ViewModel.
- **Status:** Open.

## <a id="b-019"></a>B-019 - Release and policy posture

- **Severity / confidence:** Informational / Confirmed.
- **Details:** `isMinifyEnabled = false` for release; `allowBackup="true"` with the unmodified sample backup rules (sessions/limits can be restored from cloud backup); `targetSdk 34` below `compileSdk 36` (lint `OldTargetApi`); `USE_EXACT_ALARM` and `QUERY_ALL_PACKAGES` are Play-policy sensitive (alarm-clock/calendar apps only; broad package visibility needs justification); `AccessibilityService` apps must complete Play's declaration. Debug config is not enabled in release (no `android:debuggable`).
- **Status:** Open (decide before any Play submission).

## <a id="b-020"></a>B-020 - Whole-list persistence

- **Severity / confidence:** Low / Strongly suspected.
- **Details:** `saveSession` copies `_state.value.sessions` (a UI snapshot) and rewrites the whole JSON. If the stored JSON is corrupt, `sessionsFlow` emits an empty list and the next save overwrites the data; individual malformed sessions are silently dropped by `runCatching`.
- **Observed on the emulator:** a deliberately malformed DataStore file makes `BootReceiver.onReceive` throw `CorruptionException` (uncaught, because the `runBlocking` read is not wrapped), crashing the process. A `ReplaceFileCorruptionHandler` would avoid this.
- **Remediation:** operate inside `dataStore.edit {}` (read-modify-write in one transaction); log parse failures; add a corruption handler; consider Room.
- **Status:** Open.

## <a id="b-021"></a>B-021 - Weekly data never refreshes

- **Severity / confidence:** Low / Confirmed.
- **Details:** `selectTab` loads weekly data only when `weeklyData.isEmpty()`; the ViewModel is long-lived (until Activity recreation), so after midnight the chart shows yesterday's "today" until the Activity restarts.
- **Status:** Open.

## <a id="b-022"></a>B-022 - Hidden-package cache

- **Severity / confidence:** Low / Confirmed.
- **Details:** `UsageRepositoryImpl.hiddenPackagesCache` is a plain `var`, computed once per repository instance, never invalidated on install/uninstall, and not synchronised (called from IO threads).
- **Status:** Open.

## <a id="b-023"></a>B-023 - No way to delete or disable a session

- **Severity / confidence:** Medium / Confirmed.
- **Evidence:** `grep -rn "deleteSession" app/src/main` finds only the definition in `SessionsViewModel`; no composable calls it.
- **Impact:** A mistaken or obsolete session (especially a repeating one) can be edited but never removed; with no break-friction either, users may be stuck with unwanted blocking.
- **Fix:** commit "fix: let users delete a saved session from its edit screen": `CreateSessionScreen(canDelete, onDelete)`; a red "Delete Schedule" button with a confirmation dialog, shown only if the session id is already in `SessionsState.sessions` (templates being added are not yet stored). `onDelete` calls the existing `deleteSession`, which also cancels the alarms.
- **Verified on the emulator:** dialog appears; Cancel keeps the form; Delete returns to the list without the session; the stored list is empty; the `dumpsys alarm` history shows the start and end alarms with `Reason=alarm_cancelled`; the previously blocked app opens. Not done: an enable/disable switch (still missing) and a Compose UI test.
- **Status:** Fixed (delete). An enable/disable switch remains a possible follow-up.

---

## Fix log

| Date | ID | Commit | Result |
|---|---|---|---|
| 2026-10-25 | refactor | `refactor: share the session-active rule between service and sessions screen` | `remainingMillisAt` is the single rule; behaviour unchanged |
| 2026-10-25 | tests | `test: characterize session-active rule and FocusSession JSON` | 21 tests pass |
| 2026-10-25 | B-009 | `fix: keep only the application context in SettingsViewModel` | Fixed; `StaticFieldLeak` gone from lint |
| 2026-10-25 | B-002 | `fix: create ViewModels through the ViewModelStore and use application context` | Fixed; emulator: 9 -> 1 Activities after 12 rotations |
| 2026-10-25 | B-003 | `fix: make the Enable App Blocking setting pause blocking` | Fixed; emulator: switch off -> app not blocked |
| 2026-10-25 | B-023 | `fix: let users delete a saved session from its edit screen` | Fixed (delete); emulator-verified |

After these changes: `testDebugUnitTest` runs 25 tests (24 new plus the template test), all passing; `lintDebug` reports 86 errors (85 `NewApi` + 1 `MissingSuperCall`), down from 93, because the duplicated `java.time` code was merged.
