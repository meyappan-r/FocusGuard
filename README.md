# FocusGuard

> **Reclaim your attention. Block distractions. Own your time.**

An Android app that blocks distracting apps on a schedule you set in advance, and caps daily time per app. Blocking is enforced by the system, not by willpower: when you open a blocked app, a full-screen block screen covers it.

Everything stays on the device: no account, no analytics, no internet permission.

---

## Features

- **Focus sessions** — a name, a time window, repeat days (or one date) and the apps to block. Sessions are active by the clock; there is nothing to start manually.
- **Per-app daily limits** — set a time cap per app; once it is reached the app is blocked until the next day.
- **Block screen** — shows which session or limit is blocking the app. *Exit* goes Home; *Unblock manually* grants a short break.
- **Screen-time insights** — today's usage ring, the biggest distraction, and a 7-day chart where tapping a bar shows that day's per-app breakdown. Usage is rebuilt from the system event log rather than the aggregate `totalTimeInForeground`, which over-counts.
- **Settings** — permission status with shortcuts, an *Enable App Blocking* switch that pauses all blocking, and Light / Dark / System theme.
- **Sessions can be edited and deleted** from the Sessions tab.

## How it works

1. You save a session; it is stored on-device (DataStore, JSON) and its next start/end alarms are registered with `AlarmManager` (inexact alarms; no exact-alarm permission is needed).
2. `BlockerAccessibilityService` receives a window-change event whenever an app comes to the foreground.
3. It checks the package against in-memory caches of sessions, the current break and the apps over their limit, using the current time. No disk access happens on this path.
4. On a match it starts `BlockActivity` on top of the app.

The alarms only re-arm the next occurrence (and are restored after a reboot); whether a session is active is always decided by the clock. Limits are refreshed by a 10-second usage poll, so a limit can trip a few seconds late.

## Tech stack

| | |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3, Navigation Compose |
| State | `ViewModel` + `StateFlow` + coroutines, manual dependency injection in `MainActivity` |
| Storage | Preferences DataStore (sessions, break), SharedPreferences (limits, settings) |
| Platform APIs | `AccessibilityService`, `UsageStatsManager`, `AlarmManager` |
| SDK | `minSdk 21` (with `java.time` core-library desugaring), `targetSdk 36`, `compileSdk 36` |
| Build | AGP 9.0.1, Gradle 9.1, Kotlin 2.0.0 |

Source layout (`app/src/main/java/com/focusguard/`): `core/` (service, block screen, receivers, scheduler), `data/` (usage data source, repository, DataStore, preferences), `domain/` (models, use case, the shared session rule), `ui/` (Compose screens and ViewModels).

## Build and run

```bash
git clone https://github.com/meyappan-r/FocusGuard.git
# open the folder in Android Studio, or from a terminal:
./gradlew assembleDebug        # build
./gradlew bundleRelease        # Android App Bundle for Google Play (needs the signing file below)
./gradlew testDebugUnitTest    # unit tests
./gradlew lintDebug            # lint report: app/build/reports/lint-results-debug.html
```

### Release build

`./gradlew assembleRelease` signs the APK only if a `keystore.properties` file exists in the project root (it is git-ignored, and so are `*.jks` / `*.keystore`):

```properties
storeFile=C:/path/to/your-upload-key.jks
storePassword=...
keyAlias=...
keyPassword=...
```

Without that file the release APK is built unsigned. Never commit a keystore or its passwords.

Use a physical device when you can: usage statistics and OEM background restrictions are not realistic on emulators.

After installing, open **Settings** in the app and grant two things:

1. **Usage access** — system Settings → Special app access → Usage access → FocusGuard
2. **Accessibility service** — system Settings → Accessibility → FocusGuard App Blocker → enable

## Permissions

| Permission | Why |
|---|---|
| Usage access (`PACKAGE_USAGE_STATS`) | Read the system event log to compute per-app screen time and enforce limits |
| Accessibility service | Receive window-change events (package names only; `canRetrieveWindowContent` is off) |
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms after a reboot |
| Package visibility (`<queries>` for launchable apps) | List launchable apps for the app picker and show app names and icons |
| `POST_NOTIFICATIONS` | Warn after a reboot if the accessibility service is off (not yet requested at runtime on Android 13+, see the bug list) |

## Privacy

Full policy: [PRIVACY_POLICY.md](PRIVACY_POLICY.md).

- No internet permission is declared; nothing leaves the device.
- No analytics, crash reporting or ads.
- The accessibility service reads only the package name of the foreground window — not text, passwords or the clipboard.
- Data lives in the app's private storage. Note that Android cloud backup is currently enabled with the default rules.

## Known limitations

- **OEM battery managers** (Xiaomi, OnePlus, some Samsung) may stop the accessibility service; blocking then silently stops until the user re-enables it.
- **Midnight-crossing sessions** are not supported by the editor: the end time must be after the start time.
- **A break does not pause limit-based blocks**, and the session form's break length setting is not used yet.
- Only one API 36 emulator has been used to test at runtime; behaviour on physical devices and on Android 5–7 is unverified.

## Roadmap

- [ ] Optional foreground-service fallback for more reliable blocking
- [ ] Session enable/disable switch and midnight-crossing sessions
- [ ] More tests (scheduler, usage algorithm, UI)
- [ ] Import/export sessions as JSON
- [ ] Allow-list focus mode (block everything except chosen apps)
- [ ] Home-screen widget for the active session

## Contributing

This is a learning project; issues and pull requests are welcome.

## License

MIT License — see [LICENSE](LICENSE).
