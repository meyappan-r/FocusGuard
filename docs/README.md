# FocusGuard Documentation

Each topic has exactly one home; other files link to it instead of repeating it.

| Read this | When you want to... |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | understand the structure, the blocking pipeline, storage and design trade-offs |
| [CODEBASE_WALKTHROUGH.md](CODEBASE_WALKTHROUGH.md) | follow user journeys and each package through the real source |
| [ANDROID_CONCEPTS.md](ANDROID_CONCEPTS.md) | learn the Kotlin/Android concepts the code relies on |
| [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md) | build, run, test, diagnose; see what was actually run |
| [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md) | see every known defect and risk with evidence, plus the fix log (the only bug list) |

The project overview, features and install steps are in the root [README](../README.md).
A personal learning/interview guide is kept locally and is git-ignored; it is not part of the repository.

Suggested order: README → ARCHITECTURE → CODEBASE_WALKTHROUGH (with ANDROID_CONCEPTS open) → BUGS.

## Evidence tags

- **[O] Observed** — read directly in source or configuration.
- **[V] Verified** — confirmed by a successful build, test or lint run, or by runtime execution (commands and results are in the [verification log](DEBUGGING_AND_TESTING.md#verification-log)).
- **[I] Inferred** — a reasonable reading of the code or platform documentation, not run.
- **[U] Unverified** — needs a device or more evidence.

Untagged statements are concept definitions or plain observations of the source. Runtime checks were done only on one
Android 16 (API 36) emulator; nothing has been tested on a physical device.

## What the docs describe

The code on `master`. One caveat: some behaviour of `BlockActivity` ("Unblock manually" sets a fixed 5-minute break and
opens the Blocks tab) comes from working-tree edits that may not be committed yet; the text says so where it matters.
Fixed bugs stay in the bug list, marked *Fixed*, with their before/after evidence.

## Keeping docs in sync

Update the single owning document: structure → ARCHITECTURE, flows and symbols → WALKTHROUGH, concepts →
ANDROID_CONCEPTS, commands and results → DEBUGGING_AND_TESTING, defects and fixes → BUGS (including its fix log).
Git history is the changelog; there is no separate CHANGELOG.
