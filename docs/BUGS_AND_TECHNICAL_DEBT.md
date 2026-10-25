# Bugs and Technical Debt

The single authoritative list of defects, risks and debt. Nothing here has been fixed yet; every
entry is `Open`. Entries come from reading the source and from running Android lint
(`./gradlew lintDebug`). **No entry was reproduced on a device**, so "Confirmed" below means
"proven from source or tooling output", not "seen failing at runtime".

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
| [B-002](#b-002) | High | Strongly suspected | ViewModels bypass the ViewModelStore; infinite loops never cancelled |
| [B-007](#b-007) | High | Confirmed | Signing passwords hardcoded in tracked `build.gradle.kts` |
| [B-003](#b-003) | Medium | Confirmed | "Enable App Blocking" setting is not enforced |
| [B-005](#b-005) | Medium | Strongly suspected | A break does not suppress *limit* blocks |
| [B-006](#b-006) | Medium | Needs verification | Home usage and limit enforcement use different algorithms |
| [B-008](#b-008) | Medium | Strongly suspected | `runBlocking` in receivers; service scope never cancelled |
| [B-009](#b-009) | Medium | Confirmed (lint) | `SettingsViewModel` retains an Activity |
| [B-010](#b-010) | Medium | Confirmed | Block screen: blank-screen path, ignores `breakMinutes`, `onBackPressed` lint error |
| [B-011](#b-011) | Medium | Strongly suspected | App picker hides pre-installed (FLAG_SYSTEM) apps |
| [B-013](#b-013) | Medium | Confirmed | No real automated tests |
| [B-015](#b-015) | Medium | Strongly suspected | `POST_NOTIFICATIONS` never requested |
| [B-023](#b-023) | Medium | Confirmed | Sessions cannot be deleted or disabled from the UI |
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

- **Severity / confidence:** High / Confirmed by lint (92 `NewApi` errors), not run on an API 21-25 device.
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

- **Severity / confidence:** High / Strongly suspected.
- **Where:** `MainActivity.onCreate` (plain constructors for Home/Blocks/Sessions); `SessionsViewModel.init`.
- **Evidence:** three ViewModels are built with `HomeViewModel(...)`, `BlocksViewModel(...)`, `SessionsViewModel(...)`; none uses
  `ViewModelProvider`/`viewModels`. `AndroidManifest` has no `configChanges`. `SessionsViewModel.init` launches two collectors
  and a `while (true) { ...; delay(1000L) }` loop in `viewModelScope`.
- **Impact:** **[I]** Rotation or theme change recreates the Activity: screen state resets and a new set of three
  coroutines starts while the old set keeps running (nothing calls `onCleared`). CPU/battery drain and leaked references grow with each recreation.
- **Repro (to run):** rotate the device 10x on the Sessions tab, then count `SessionsViewModel` instances in the Android Studio heap dump.
- **Remediation:** obtain ViewModels with `by viewModels { Factory }` (one factory per VM, as `SettingsViewModelFactory` already does), and make the loop `while (isActive)`.
- **Risks:** low; touches `MainActivity` wiring only. **Verify:** heap dump instance count stays 1; state survives rotation.
- **Status:** Open.

## <a id="b-003"></a>B-003 - "Enable App Blocking" has no effect

- **Severity / confidence:** Medium / Confirmed.
- **Where:** `SettingsScreen` ToggleRow ("Pause all limits temporarily."), `SettingsViewModel.toggleBlocking` writing `blocking_enabled`.
- **Evidence:** `grep -rn "blocking_enabled\|isBlockingEnabled" app/src/main` finds the value only in the Settings UI/ViewModel; neither
  `BlockerAccessibilityService` nor `BlockActivity` reads it.
- **Impact:** The user believes blocking is paused; it is not. Trust and safety (users trying to access an app urgently).
- **Remediation:** have the service read `settings_prefs.blocking_enabled` (or move it into DataStore and collect it into a cache like the others) and short-circuit `onAccessibilityEvent`.
- **Verify:** unit-test the decision function with the flag false; manual: toggle off, open a blocked app.
- **Status:** Open.

## <a id="b-004"></a>B-004 - Midnight-crossing sessions: duplicated predicate, wrong-day check, UI forbids creation

- **Severity / confidence:** Low (latent) / Strongly suspected.
- **Where:** `BlockerAccessibilityService.shouldBlockSynchronously`, `SessionsViewModel.checkSessionActiveStatus`, `SessionScheduler.calculateNextTrigger`, `CreateSessionScreen` validation.
- **Evidence:** the weekday/date check uses *today*, then the time window wraps. For a Monday 22:00-02:00 session, at Tuesday 01:00 "today" is Tuesday, which is not selected, so the window is judged inactive. Separately, `endBeforeStartErr = toMins <= fromMins` stops the UI from ever creating such a session.
- **Impact:** None today through the UI (cannot create), but the model/service claim to support it; any future relaxation of the UI check will ship a bug. README "Current Limitations" describes this incorrectly.
- **Remediation:** extract one pure function `isActive(session, now): ActiveState` handling the previous-day case, used by service + ViewModel, with table-driven unit tests. Decide product behaviour (allow midnight sessions or not) first.
- **Verify:** unit tests for 23:59, 00:00, 00:01, equal from/to, DST days.
- **Status:** Open.

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
- **Remediation:** pass `applicationContext` (or use `AndroidViewModel`).
- **Status:** Open.

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
- **Impact:** None of the logic (session predicate, trigger calculation, usage state machine, JSON round-trip) is protected against regression.
- **Remediation:** extract pure functions and add JUnit tests (see [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md#testing-strategy)).
- **Status:** Open.

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
- **Remediation:** operate inside `dataStore.edit {}` (read-modify-write in one transaction); log parse failures; consider Room.
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
- **Remediation:** add delete (with confirmation) to the edit screen, and ideally an enable/disable switch.
- **Status:** Open.

---

## Fix log

| Date | ID | Commit | Result |
|---|---|---|---|
| - | - | - | No fixes applied yet |
