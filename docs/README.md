# FocusGuard Documentation

A small, non-redundant documentation set. Each topic has exactly one home; other files link to it.

| Read this | When you want to... |
|---|---|
| [ARCHITECTURE.md](ARCHITECTURE.md) | understand how the app is structured, the blocking pipeline, storage, and design trade-offs |
| [CODEBASE_WALKTHROUGH.md](CODEBASE_WALKTHROUGH.md) | follow user journeys and each package through the real source |
| [ANDROID_CONCEPTS.md](ANDROID_CONCEPTS.md) | learn the Kotlin/Android concepts the code relies on |
| [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md) | build, run, test, diagnose; see which checks were actually run |
| [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md) | see every known defect/risk with severity and evidence (the only bug list) |

The project overview, feature list and install steps stay in the root [README.md](../README.md);
the product requirements are in `SRS.md` at the repository root when present (it was untracked in git at the time of writing).
A personal learning/interview guide is kept **locally and is git-ignored**; it is not part of the repository.

Suggested reading order: README -> ARCHITECTURE -> CODEBASE_WALKTHROUGH (keep ANDROID_CONCEPTS open) -> BUGS.

## Evidence tags

Every non-trivial statement about the app is one of:

- **[O] Observed** - read directly in source or configuration.
- **[V] Verified** - confirmed by a successful build/test/lint run or runtime execution (the exact commands and
  outcomes are in [DEBUGGING_AND_TESTING.md](DEBUGGING_AND_TESTING.md#verification-log)).
- **[I] Inferred** - a reasonable interpretation of the code or platform documentation, not run.
- **[U] Unverified** - needs a device or more evidence.

Statements without a tag are definitions of Android/Kotlin concepts, or are observed in the source. Runtime checks
were done only on one API 36 emulator (see the verification log); nothing was tested on a physical device.

## Baseline

The docs describe the code as of the Stage E commits (the session-rule refactor, new tests and the fixes for
B-002, B-003, B-009 and B-023; see the fix log in [BUGS_AND_TECHNICAL_DEBT.md](BUGS_AND_TECHNICAL_DEBT.md#fix-log)),
built on commit `6ff1a61`, **plus** the author's uncommitted working-tree edits: the new `BlockActivity`
"Unblock manually" flow (fixed 5-minute break + redirect to Blocks), a cast cleanup in `SessionDataStore.clearBreak`,
and the launcher-icon change. Where behaviour differs from the last commit the text says so.

## Keeping docs in sync

When code changes, update the single owning document: structure -> ARCHITECTURE, flow/symbol -> WALKTHROUGH,
concept -> ANDROID_CONCEPTS, command/result -> DEBUGGING_AND_TESTING, defect status/fix -> BUGS (also the fix log
at the bottom of that file). Git history is the changelog; there is no separate CHANGELOG file.
