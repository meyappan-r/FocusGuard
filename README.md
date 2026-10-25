# FocusGuard

> **Reclaim your attention. Block distractions. Own your time.**

A system-level Android application that enforces scheduled focus sessions and screen-time limits — automatically, without relying on user willpower.

---

## Problem Statement

The average smartphone user unlocks their device 96 times a day. Social media, gaming, and short-form content apps are engineered to capture attention and resist quitting. The result is broken deep-work sessions, chronic cognitive fatigue, and measurable drops in productivity.

Existing solutions rely on soft nudges — usage summaries, app timers you can bypass with a single tap. They inform but do not enforce. FocusGuard takes a different approach: **enforcement at the OS level, on a schedule you define in advance, with no easy escape hatch in the moment.**

---

## Solution Overview

FocusGuard uses two Android system privileges — **Accessibility Service** and **AlarmManager exact alarms** — to create a deterministic, automated blocking pipeline:

- Sessions are configured once, stored on-device, and their next occurrences are registered as system alarms.
- Whether a session is active is decided from the system clock each time an app comes to the foreground.
- The Accessibility Service detects every foreground app change in real time.
- Blocked apps are intercepted and replaced with a full-screen block screen.
- Blocking itself needs no persistent foreground notification. A lightweight 10-second poll refreshes per-app daily-limit status; the exact trade-offs are documented in [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## Key Features

- **Scheduled App Blocking** — Define sessions with a start time, end time, repeat days, and a specific list of apps to block.
- **Automatic Session Execution** — Sessions start and end by the clock; no manual start required. (Exact alarms re-arm the next occurrence and are restored after reboot.)
- **Real-Time Accessibility Enforcement** — Every app switch is evaluated against in-memory caches, so the decision itself does no disk I/O.
- **Break System** — Tap "Unblock manually" on the block screen to temporarily unlock for a short break.
- **Screen-Time Tracking** — Tracks true Screen-On Time via a custom event-stream parser, not the inflated `totalTimeInForeground` API.
- **Peak Distraction Detection** — Identifies and surfaces the single app consuming the most attention today.
- **Weekly Usage Chart** — Tap any bar in the 7-day chart to see a per-app breakdown for that day.
- **Per-App Daily Limits** — Set a daily time cap per app. When reached, the app is blocked for the rest of the day.
- **System-Native Dark / Light / System Theme** — Three-mode theme toggle persisted across restarts.
- **Offline-First, Privacy-Focused** — All data stays on-device. No accounts. No telemetry. No internet permission.

---

## How It Works

```
1. User creates a focus session
        └─ Picks a name, time range, repeat days, and apps to block

2. Session is persisted in DataStore (JSON)
        └─ SessionScheduler registers exact AlarmManager alarms for start/end times

3. Alarms fire at session start/end
        └─ AlarmManager → SessionAlarmReceiver → re-arms the next occurrence
        └─ (Enforcement does not depend on the alarm: it is decided by the clock)

4. User opens a blocked app
        └─ BlockerAccessibilityService detects TYPE_WINDOW_STATE_CHANGED event
        └─ In-memory cache lookup + current time: is this app blocked right now?
        └─ YES → launches BlockActivity (full-screen block overlay)

5. Block screen is displayed
        └─ Shows app name, icon, and which session (or limit) is blocking it
        └─ Options: Exit (go Home) | Unblock manually (temporary break)

6. Session end time is reached
        └─ The clock check no longer matches → no further blocks
```

See [docs/CODEBASE_WALKTHROUGH.md](docs/CODEBASE_WALKTHROUGH.md) for the real code paths.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────┐
│                      UI Layer                           │
│  HomeScreen · BlocksScreen · SessionsScreen · Settings  │
│          (Jetpack Compose + Navigation)                 │
└────────────────────────┬────────────────────────────────┘
                         │ observes StateFlow
┌────────────────────────▼────────────────────────────────┐
│                   ViewModel Layer                       │
│  HomeViewModel · BlocksViewModel · SessionsViewModel    │
│  SettingsViewModel  (Android ViewModel + coroutines)    │
└──────┬─────────────────┬──────────────────┬─────────────┘
       │                 │                  │
┌──────▼──────┐  ┌───────▼──────┐  ┌───────▼──────────────┐
│  UsageRepo  │  │ SessionData  │  │  LimitManager         │
│  (UsageStats│  │ Store        │  │  (SharedPreferences)  │
│  DataSource)│  │ (DataStore)  │  └──────────────────────┘
└─────────────┘  └──────────────┘
                         │ Flow<List<FocusSession>>
┌────────────────────────▼────────────────────────────────┐
│               System / Core Layer                       │
│  BlockerAccessibilityService  ·  SessionScheduler       │
│  SessionAlarmReceiver  ·  BootReceiver  ·  BlockActivity│
└─────────────────────────────────────────────────────────┘
```

---

## Folder Structure

```
app/src/main/java/com/focusguard/
│
├── MainActivity.kt             # App entry point; manual DI; nav graph host
│
├── core/
│   ├── services/
│   │   ├── BlockerAccessibilityService.kt  # Real-time app blocking engine
│   │   └── BlockActivity.kt               # Full-screen block overlay UI
│   ├── receivers/
│   │   ├── BootReceiver.kt               # Re-schedules alarms after reboot
│   │   └── SessionAlarmReceiver.kt       # Handles alarm fires; reschedules
│   └── scheduling/
│       └── SessionScheduler.kt           # Wraps AlarmManager exact alarm API
│
├── data/
│   ├── datasource/
│   │   └── UsageStatsDataSource.kt       # Raw event-stream reader (UsageStatsManager)
│   ├── local/
│   │   ├── SessionDataStore.kt           # DataStore<Preferences> for sessions + breaks
│   │   └── LimitManager.kt              # SharedPreferences for per-app daily limits
│   └── repository/
│       └── UsageRepositoryImpl.kt        # Filters, aggregates, and exposes usage data
│
├── domain/
│   ├── model/                            # Pure Kotlin data models (no Android deps)
│   │   ├── AppUsage.kt
│   │   ├── PeakDistraction.kt
│   │   ├── ScreenTimeSummary.kt
│   │   └── WeeklyDayUsage.kt
│   └── usecase/
│       └── GetDailyUsageUseCase.kt       # Wraps repository; enforces IO dispatcher
│
└── ui/
    ├── home/       # Today's usage ring chart, weekly bar chart, app list
    ├── blocks/     # Per-app daily limit setter
    ├── sessions/   # Session builder, calendar, active session card
    ├── settings/   # Theme, permissions, blocking toggle
    ├── theme/      # Custom color palette (FGColors, FG_Seg0–3)
    └── extensions/ # Modifier.clickableScale() — tactile press animation
```

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin |
| UI Framework | Jetpack Compose + Material 3 |
| Navigation | Navigation Compose |
| State Management | ViewModel + StateFlow + Coroutines |
| Storage (sessions) | DataStore (Preferences) |
| Storage (limits) | SharedPreferences |
| App Blocking | AccessibilityService API |
| Scheduling | AlarmManager (exact alarms) |
| Usage Tracking | UsageStatsManager (event stream) |
| Min SDK | API 21 (Android 5.0) |
| Target SDK | API 34 (Android 14) |

---

## Documentation

In-depth documentation (architecture, source walkthrough, Android concepts, debugging/testing, and the bug list) is in [docs/](docs/README.md).

---

## Installation

```bash
# 1. Clone the repository
git clone https://github.com/the-mey/FocusGuard.git

# 2. Open in Android Studio (Hedgehog or newer)
# File → Open → select the FocusGuard folder

# 3. Build & run on a physical device (API 21+)
# Emulators do not report PACKAGE_USAGE_STATS events accurately
```

After installation, grant two permissions from the **Settings** tab:

1. **Usage Access** → Settings → Apps → Special app access → Usage Access → FocusGuard
2. **Accessibility Service** → Settings → Accessibility → FocusGuard → Enable

---

## Permissions Explained

| Permission | Why It's Needed |
|---|---|
| `PACKAGE_USAGE_STATS` | Reads the system event log to compute true screen-on time per app |
| Accessibility Service | Receives `TYPE_WINDOW_STATE_CHANGED` events to detect foreground app changes and trigger blocks |
| `SCHEDULE_EXACT_ALARM` | Fires session start/end alarms at the precise scheduled minute |
| `RECEIVE_BOOT_COMPLETED` | Re-registers all session alarms after a device reboot |
| `POST_NOTIFICATIONS` | Sends a reminder notification if the Accessibility Service is found disabled after reboot |
| `QUERY_ALL_PACKAGES` | Enumerates installed apps to populate the app picker and filter system apps |

Neither permission transmits any data outside the device. Both are declared with `tools:ignore` lint suppressions to document intentional usage.

---

## Privacy Philosophy

- **100% offline.** No internet permission is declared in the manifest.
- **No analytics, no crash reporting, no ads.**
- All session data is stored in `DataStore` in the app's private data directory — inaccessible to other apps without root.
- Usage statistics are read on-device and never leave the device.
- The Accessibility Service reads **only** the package name of the foreground window. It does not read text content, passwords, or clipboard data (`canRetrieveWindowContent="false"`).

---

## Current Limitations

- **OEM battery killers** — Xiaomi/MIUI, OnePlus, and some Samsung devices aggressively kill Accessibility Services when the app is not in the foreground. The current mitigation is AlarmManager-based rescheduling and a post-boot notification, but perfect reliability on restricted OEMs is not guaranteed without the user manually whitelisting the app from battery optimization.
- **Accessibility dependency** — The blocking engine requires the Accessibility Service to be enabled. If the user disables it, blocking silently stops.
- **Midnight-crossing sessions** — The data model has wrap-around logic, but the session editor currently rejects an end time that is not after the start time, so such sessions cannot be created.
- **Known defects** — A prioritized, evidence-based list (e.g. `java.time` use with `minSdk 21`) lives in [docs/BUGS_AND_TECHNICAL_DEBT.md](docs/BUGS_AND_TECHNICAL_DEBT.md).
- **No widget / quick-tile** — Starting or pausing a session requires opening the app.

---

## Roadmap

- [ ] Improve blocking reliability via a persistent foreground service fallback
- [ ] Pomodoro-style timer integration in the active session card
- [ ] AI-based peak distraction insights using on-device ML
- [ ] Import/export sessions as JSON
- [ ] Optional encrypted cloud sync via a user-provided key
- [ ] Advanced focus modes (allow-list instead of block-list)
- [ ] Home screen widget for active session status

---

## Screenshots

| Home – Usage Ring | Sessions – Builder | Block Screen | Settings |
|---|---|---|---|
| ![Home Screen](screenshots/home.png) | ![Sessions Screen](screenshots/sessions.png) | ![Block Screen](screenshots/block.png) | ![Settings](screenshots/settings.png) |

> Screenshots coming soon. Run the app on a physical device for the full experience.

---

## Contributing

This is a learning project, but contributions, bug reports, and improvement suggestions are welcome. Open an issue or a pull request.

---

## License

```
MIT License — © 2026 the-mey
```
