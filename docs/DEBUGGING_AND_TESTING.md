# Setup, Debugging and Testing

Everything needed to build, run, verify and diagnose FocusGuard. Architecture is in
[ARCHITECTURE.md](ARCHITECTURE.md); known defects in [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md).
Commands marked **(ran)** were executed while writing this document, with the results recorded in
[Verification log](#verification-log); commands marked **(not run)** are standard but unverified here.

---

## Setup

| Item | Value (from the repo) |
|---|---|
| Android Gradle Plugin | 9.0.1 (`gradle/libs.versions.toml`) |
| Gradle wrapper | 9.1.0 |
| Kotlin | 2.0.0 + Compose compiler plugin 2.0.0 |
| JDK used for the verified build | Temurin 21 |
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 34 / 21 |
| App id / version | `com.focusguard` / `0.5` (code 4) |
| Compose BOM | 2024.06.00 |

1. Install Android Studio (a version supporting AGP 9) and the Android SDK; create `local.properties` with `sdk.dir=...`
   (git-ignored; Android Studio does this for you).
2. Use a **physical device** for anything involving usage stats or blocking - emulators have little real usage
   history, and OEM behaviour (battery killers) cannot be reproduced there.
3. On the device, enable *Developer options -> USB debugging*.

### Build commands

```bash
./gradlew.bat assembleDebug            # (not run) debug APK -> app/build/outputs/apk/debug
./gradlew.bat compileDebugKotlin       # (ran) fastest compile check
./gradlew.bat testDebugUnitTest        # (ran) JVM unit tests
./gradlew.bat lintDebug                # (ran) Android lint; report in app/build/reports/lint-results-debug.html
./gradlew.bat installDebug             # (not run) build + install on the connected device
```

(Use `./gradlew` on macOS/Linux.) `assembleRelease` needs the signing keystore and secrets - see
[B-007](BUGS_AND_TECHNICAL_DEBT.md#b-007); do not commit the keystore.

Note: `lint { abortOnError = false }` means lint problems never fail the build - read the report yourself.

---

## First-run checklist on a device

1. Install, open the app.
2. Settings -> grant **Usage Access**, then **Accessibility Service** (enable "FocusGuard App Blocker").
3. Home should now show non-zero usage after you have used other apps.
4. Sessions -> New Session -> pick a one-time session starting in the next minutes, choose a harmless app (e.g. a calculator), Save.
5. When the window starts, open that app -> `BlockActivity` should cover it.

---

## Debugging recipes

| Question | Command / action |
|---|---|
| Is the service enabled? | `adb shell settings get secure enabled_accessibility_services` should list `com.focusguard/com.focusguard.core.services.BlockerAccessibilityService` |
| What does the service log? | `adb logcat -s BlockerService` (only errors are logged - add `Log.d` temporarily to trace decisions) |
| Is usage access granted? | `adb shell appops get com.focusguard GET_USAGE_STATS` |
| Grant usage access without UI | `adb shell appops set com.focusguard GET_USAGE_STATS allow` (debug builds on a test device) |
| What does Android think the usage is? | `adb shell dumpsys usagestats` (compare with the Home number - see [B-006](BUGS_AND_TECHNICAL_DEBT.md#b-006)) |
| Are alarms armed? | `adb shell dumpsys alarm` and look for `com.focusguard` / `SessionAlarmReceiver` |
| Simulate reboot receiver | `adb shell am broadcast -a android.intent.action.BOOT_COMPLETED -n com.focusguard/.core.receivers.BootReceiver` |
| Simulate Doze | `adb shell dumpsys deviceidle force-idle` then `unforce` |
| Inspect stored sessions (debug build) | `adb shell run-as com.focusguard cat files/datastore/focus_sessions_datastore.preferences_pb` (binary protobuf; the JSON is readable inside) |
| Inspect limits | `adb shell run-as com.focusguard cat shared_prefs/limit_preferences.xml` |
| Is the process leaking after rotation? | Android Studio Profiler -> Memory -> force GC -> heap dump -> filter `SessionsViewModel`, `MainActivity` |
| Why was an app blocked / not blocked? | Walk the checklist in [CODEBASE_WALKTHROUGH A3](CODEBASE_WALKTHROUGH.md#a3-a-block-actually-happening): service enabled? session day matches? time in window? package in `blockedPackages`? break active? limit reached? |

Common symptoms:

- *Nothing is ever blocked* -> service disabled/killed by OEM; blocking toggle is irrelevant ([B-003](BUGS_AND_TECHNICAL_DEBT.md#b-003)); app is on an exemption list.
- *Home screen empty* -> usage access not granted (queries return empty, no exception).
- *Sessions "forget" the draft on rotation* -> [B-018](BUGS_AND_TECHNICAL_DEBT.md#b-018).
- *Limit block repeats after "Unblock manually"* -> [B-005](BUGS_AND_TECHNICAL_DEBT.md#b-005).

---

## Testing strategy

**Current state:** only the two template tests exist ([B-013](BUGS_AND_TECHNICAL_DEBT.md#b-013)); nothing in the
business logic is verified by automation.

**Recommended order (highest value, lowest cost first):**

1. **Pure-logic unit tests (JVM, no device).** Extract and test:
   - the session-active predicate (weekday/date + wrap-around, boundaries `from` inclusive / `to` exclusive, equal times);
   - `SessionScheduler.calculateNextTrigger` (inject a `Clock`/`now` so it is deterministic; weekday scan; past one-time dates);
   - `FocusSession.toJson`/`fromJson` round trip and malformed input;
   - the usage state machine (feed a fake event list: normal switch, pause w/o resume, screen-off, end-of-range);
   - `HomeViewModel` ring math (sweep angles sum to <= 300 degrees; zero usage -> empty).
   `java.time` works in JVM tests, so these run today even though [B-001](BUGS_AND_TECHNICAL_DEBT.md#b-001) exists.
2. **Characterization tests first.** Write tests that pin *current* behaviour (including the midnight quirk in
   [B-004](BUGS_AND_TECHNICAL_DEBT.md#b-004)), then change behaviour deliberately and update the test. This turns "I think it is a bug" into evidence.
3. **Robolectric or instrumented tests** for `SessionDataStore` persistence and receivers.
4. **Compose UI tests** for the session form validation.
5. **Manual device matrix:** API 24 (desugaring), 33 (notifications), 34; one aggressive-OEM device.

Dependencies already present: JUnit 4 (`testImplementation(libs.junit)`). Adding coroutine-test or MockK would be an explicit,
justified change.

### Manual regression script (until automation exists)

| # | Steps | Expected |
|---|---|---|
| 1 | Create one-time session starting in 2 min, block app A | App A blocked after start, not before |
| 2 | During the session tap "Unblock manually" | 5-minute break; app A usable; "End Break Early" visible on Sessions tab |
| 3 | Set a 1-minute limit on app B, use it for 1 min | App B blocked within ~10 s |
| 4 | Toggle "Enable App Blocking" off | (Currently has no effect - [B-003](BUGS_AND_TECHNICAL_DEBT.md#b-003)) |
| 5 | Rotate device on Sessions tab | (Currently resets - [B-002](BUGS_AND_TECHNICAL_DEBT.md#b-002)/[B-018](BUGS_AND_TECHNICAL_DEBT.md#b-018)) |
| 6 | Reboot with the service disabled | Notification appears (API 33+: only if notification permission granted - [B-015](BUGS_AND_TECHNICAL_DEBT.md#b-015)) |

---

## Verification log

Facts recorded while producing this documentation (Windows, Git Bash, JDK 21):

| Command | Result |
|---|---|
| `./gradlew.bat testDebugUnitTest compileDebugKotlin --console=plain` | BUILD SUCCESSFUL in 38 s (template test only) |
| `./gradlew.bat lintDebug --console=plain` | BUILD SUCCESSFUL in 1 m 42 s; "Lint found 93 errors, 40 warnings (and 23 warnings filtered by baseline)". Errors: 92 `NewApi` (`java.time` API 26 / `AlarmManager` API 23) + 1 `MissingSuperCall` (`BlockActivity.onBackPressed`). Notable warnings: `StaticFieldLeak` (`SettingsViewModel`), `SetTextI18n`, `HardcodedText`, `GradleDependency`, `UnusedResources`. The baseline file lists 24 entries that no longer match |
| Mermaid syntax check (`mermaid.parse`, v10.9.1 in the built-in browser) of the 5 diagrams in `ARCHITECTURE.md` | all 5 parse without error (syntax only; rendering on GitHub not checked) |
| Doc link/anchor check (throwaway Python script, not committed) | all relative links and anchors in `README.md` and `docs/*.md` resolve, except the 4 pre-existing `screenshots/*.png` links in the root README (the images do not exist; "coming soon") |
| Run on a device / emulator | **Not done** - no device was used; every runtime claim in these docs is therefore `[O]`/`[I]`, not `[V]` |
