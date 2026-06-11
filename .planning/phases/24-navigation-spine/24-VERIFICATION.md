---
phase: 24-navigation-spine
verified: 2026-06-10T08:00:00Z
status: passed
score: 5/5 must-haves verified
overrides_applied: 0
---

# Phase 24: Navigation Spine — Verification Report

**Phase Goal:** Build the redesign's skeleton. Turn `PrintStatusScreen` into the morphing waterfall ROOT — one Focus/Field surface (no gutter) that morphs idle/printing/terminal. Adopt Navigation-Compose for the drill-down back-stack. Remove the gutter from the morphing root. Add the floating emergency-stop shown on every screen while printing. Create the System page shell that rehomes Power + device/system settings off the printer waterfall.

**Verified:** 2026-06-10T08:00:00Z
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Home root is one surface that morphs idle/printing/terminal; Focus = state hero/progress/stats, Field = state-filtered action list, foot = state buttons; owner-approved on flox both orientations | VERIFIED | `PrintStatusScreen.kt:473` wraps `when(mode)` in `Crossfade(tween(150), label = "PrintStatusMorph")`; `PrintStatusStandbyField` uses `ListBlock`/`ListRow` over `buildIdleActions`; `FootButtonBar` with Preheat + System foot. UAT SC-1 + SC-2 PASS on flox (Adreno 320). |
| 2 | Navigation-Compose drives a working drill-down back-stack; old hub-and-spoke `when(screen)` holder retired; App Drawer may remain | VERIFIED | `AppShell.kt` contains `NavHost` with 18 `composable<NavDest.*>` entries; zero live `when(dest)` routing in the shell host (only 2 comments referencing the old pattern); `ShellNavState` no longer has `navigateTo`/`dest`/`backStack`/`goBack`. UAT SC-4 drill-in + Back-out confirmed. |
| 3 | Gutter is gone from the morphing root; its actions rehomed to foot-of-list bars | VERIFIED | `PrintStatusScreen.kt:498`: `gutter = null` for the Standby mode. Preheat + System actions moved to `FootButtonBar` in `PrintStatusStandbyField`. The printing/paused/terminal modes retain their Phase-16 gutters — this is explicit scope (24-CONTEXT, 24-04 plan, and UAT out-of-scope note). UAT SC-1 confirmed. |
| 4 | Floating e-stop appears on every screen ONLY when printing (drill-downs included), opens the full-screen Stop Confirm guard | VERIFIED | `AppShell.kt:1051-1070`: `FloatingEStop` is a `Box` sibling after the `NavHost`, visible when `printState == Printing || Paused`; `showEstopGuard` state raises `ConfirmGuard`. E-stop glyph font-scale-stable fix applied (`OutlinedControl.kt`: `sizeSp = 50f / fontScale`) and 64dp floor on `FloatingEStop.kt`. UAT SC-3 PASS (function + styling defect fixed in-phase). |
| 5 | System entry rehomes device/system settings; PrintStatus-as-root introduces no print-monitoring regression | VERIFIED | `PrintStatusField.kt:134-141`: `OutlinedControl` "System" foot button (`DinghyIcons.FootSystem`, `Intent.Neutral`) calls `onOpenDrawer()` → `AppDrawer` (D-10 interim hub). Power tile remains inert in drawer (D-11). UAT SC-4 PASS: System foot opens drawer, live temps + progress confirmed updating, back-stack smoke green. |

**Score:** 5/5 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt` | Type-safe `@Serializable sealed interface NavDest` with 17 destinations + `FOOT_GUN_DESTS` + `shouldPopToRoot` | VERIFIED | 19 `@Serializable` annotations (sealed interface + 17 data objects); `knownNavDests` list; `FOOT_GUN_DESTS = {Move, Extrude, Calibration}`; pure predicate with no NavHost dependency |
| `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt` | Typed idle action list model + `buildIdleActions()` | VERIFIED | Sealed `HomeAction` (`Destination` + `OpenDrawer`); `buildIdleActions` in D-06 order with D-08 capability gates; `LauncherWebcam` wired (PLACEHOLDER resolved); all icons use registered `DinghyIcons` tokens |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | NavHost-based shell with hoisted holders + sibling overlays + app-level `FloatingEStop` + pop-to-root | VERIFIED | `NavHost` present; 18 `composable<NavDest.*>` entries; 4 `DisposableEffect { onDispose { *.cancel() } }` blocks preserved; `FloatingEStop` + `ConfirmGuard` as `Box` siblings after `NavHost`; `shouldPopToRoot` wired in `LaunchedEffect(printState)` |
| `app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt` | Slimmed nav state (in-screen sub-nav only) | VERIFIED | `navigateTo`/`dest`/`backStack`/`goBack` removed; `applyEntryReset` promoted to `internal`; `startDest: NavDest?` stored as a `val` |
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` | Crossfade morph + no in-screen `FloatingEStop` | VERIFIED | `Crossfade` wraps `when(mode)` in `PrintStatusContent`; `showEstopGuard` state + in-screen `ConfirmGuard` removed (FIX-1); `gutter = null` for Standby |
| `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt` | Data-driven idle `HomeAction` list + idle `FootButtonBar` (Preheat + System) | VERIFIED | `PrintStatusStandbyField` uses `ListBlock`/`ListRow` over `buildIdleActions`; `FootButtonBar` with `Intent.Neutral` Preheat + System buttons |
| `app/src/main/java/works/mees/dinghy/designsystem/components/FloatingEStop.kt` | Font-scale-stable e-stop button with 64dp floor | VERIFIED | `modifier.size((uDp * 0.7f).coerceAtLeast(64.dp))`; `DinghyIcons.StatusStop`; `Intent.Danger` |
| `app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt` | `LauncherWebcam`, `FootPreheat`, `FootSystem` tokens registered | VERIFIED | All three present at lines 54, 68-69; all three in `DinghyIcons.all` drift-guard list (lines 176-177) |
| `gradle/libs.versions.toml` + `app/build.gradle.kts` | navigation-compose 2.8.9 pinned; no navigation-testing | VERIFIED | `navigation = "2.8.9"` at line 77; `implementation(libs.androidx.navigation.compose)` at line 169; no `navigation-testing` dep (FIX-8 preserved) |
| `app/src/test/java/works/mees/dinghy/ui/route/PopToRootTest.kt` | Pure JVM predicate test, no `TestNavController` | VERIFIED | 12 test functions; comment at line 17 confirms no `navigation.testing` imports; grep found no actual `TestNavController` import |
| `app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt` | D-06 order + D-08 HIDE-rule coverage | VERIFIED | 12 tests per SUMMARY; GREEN per build evidence |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `buildIdleActions` | `NavDest.*` | `HomeAction.Destination(dest = NavDest.*)` | VERIFIED | All 8 rows in `HomeAction.kt` use `NavDest.*` destinations |
| `PrintStatusStandbyField` idle list | `navController` via `onNavigate` | `onNavigate(action.dest)` in `PrintStatusField.kt:98` | VERIFIED | `onClick = { onNavigate(action.dest) }` wired |
| `AppShell.printState` | `navController.popBackStack<WaterfallHome>` | `LaunchedEffect(printState)` calling `shouldPopToRoot` | VERIFIED | `AppShell.kt:911-926`; pure predicate `shouldPopToRoot` from `NavDest.kt` |
| `AppShell-level FloatingEStop overlay` | `ConfirmGuard` | `Box` sibling after `NavHost`; `onClick = { showEstopGuard = true }` | VERIFIED | `AppShell.kt:1051-1070`; `ConfirmGuard` dispatches `CommandRegistry.emergencyStop` |
| `System foot button` | App Drawer | `onOpenDrawer()` → `drawerOpen = true` | VERIFIED | `PrintStatusField.kt:139`: `onClick = onOpenDrawer`; `AppShell.kt:1024-1034`: `if (drawerOpen) AppDrawer(...)` |
| `macroBookmarks`/`macroRevealHidden` | `AppContainer.stateScope` | Process-lifetime `stateIn` | VERIFIED | `AppContainer.kt:235-245`: `stateIn(stateScope, SharingStarted.Eagerly, ...)` (WR-02 fix, commit `c5fa886`) |
| Spool "Home" / Devices "onSwitched" | `NavDest.WaterfallHome` (existing root entry) | `popBackStack<WaterfallHome>(inclusive = false)` | VERIFIED | `AppShell.kt:782,879`; uses `popBackStack` not `navigate` (WR-03 fix, commit `976dc73`) |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|--------------|--------|--------------------|--------|
| `PrintStatusStandbyField` | `idleActions: List<HomeAction>` | `buildIdleActions(spoolmanPresent, bookmarksExist, outputsPresent, webcamEnabled)` in `PrintStatusScreen.kt:162-171` | YES — capability flags from live `StateFlow`s collected in `PrintStatusScreen` from `AppContainer` | FLOWING |
| `AppShell FloatingEStop` | `printerState.printState` | `printerStateFlow.collectAsStateWithLifecycle()` in `AppShell.kt:199` | YES — live Moonraker state | FLOWING |
| `buildIdleActions` capability gates | `spoolmanPresent`, `outputsPresent`, `webcamEnabled` | `container.spoolmanPresent`, `container.outputsPresent`, `container.webcamTileEnabled` (live `StateFlow`s) collected in `PrintStatusScreen` | YES | FLOWING |

---

### Behavioral Spot-Checks

Skipped — no runnable server entry point can be exercised from WSL bash without a live printer connection. The on-device UAT (24-UAT.md) with a live E3 printer is the authoritative behavioral gate.

---

### Probe Execution

No phase-declared probes. Conventional `scripts/*/tests/probe-*.sh` — not a migration/tooling phase; skipped.

---

### Requirements Coverage

No formal REQUIREMENTS.md IDs for this phase (maps to redesigned `docs/ui_design/` LAW — nav architecture). All five ROADMAP Success Criteria directly verified above.

---

### Anti-Patterns Found

| File | Pattern | Severity | Impact |
|------|---------|----------|--------|
| `NavDest.kt:107` | `shouldPopToRoot` ignores `printActive` parameter | INFO | Pop fires on any `printState` change including completion/cancel, not just print start. Acknowledged as by-design in REVIEW (WR-01): the conservative behavior is not dangerous (user pops to root they'd land on anyway). No blocker. |
| `PrintStatusField.kt:93` | `filterIsInstance<HomeAction.Destination>()` silently drops non-Destination variants | INFO | `HomeAction.OpenDrawer` is unreachable via `buildIdleActions` currently; drop is harmless. Documented in REVIEW IN-01. Not a blocker. |

No `TBD`, `FIXME`, or `XXX` markers found in any phase-modified file. The `PLACEHOLDER(24-04)` comment in `HomeAction.kt` was confirmed resolved (Webcam row now uses `DinghyIcons.LauncherWebcam`).

---

### Code Review Disposition

REVIEW.md findings (deep, 18 files):

| Finding | Severity | Status |
|---------|----------|--------|
| WR-01: `shouldPopToRoot` ignores `printActive` — pops on any state change, not just print start | WARNING | Acknowledged by-design (`283c2f2`). KDoc documents the deliberate conservative behavior. Not a correctness regression. |
| WR-02: `stateIn(compositionScope)` kills bookmark updates after recovery Splash | WARNING | Fixed (`c5fa886`) — moved to `AppContainer.stateScope` |
| WR-03: Spool "Home" and Devices "onSwitched" pushed duplicate `WaterfallHome` | WARNING | Fixed (`976dc73`) — changed to `popBackStack<WaterfallHome>(inclusive = false)` |
| IN-01: `HomeAction.OpenDrawer` never produced by `buildIdleActions`; renderer silently drops it | INFO | Open, optional. Non-blocking. |
| IN-02: `PopToRootTest` does not cover `printActive=false` returning `true` | INFO | Open, optional. Related to WR-01 by-design choice. Non-blocking. |

Both mandatory fixes (WR-02, WR-03) are committed and on HEAD.

---

### Human Verification Required

None — the on-device UAT (24-UAT.md) was owner-approved on real hardware (flox / Nexus 7 2013 / Adreno 320 / LineageOS 18.1 API 30) with all five SC gates PASS. The sign-off was by Matthew on 2026-06-10. No additional human verification required.

---

### Gaps Summary

No gaps. All five ROADMAP Success Criteria are verified against the codebase with Level 1 (exists), Level 2 (substantive), Level 3 (wired), and Level 4 (data-flowing) evidence. The two mandatory code-review findings (WR-02, WR-03) were fixed in-phase and are on HEAD. The styling defect found at UAT (e-stop glyph overflow at large font scale) was fixed in-phase (`e07263c`) and the fixed APK reinstalled on flox. The acknowledged by-design WR-01 behavior and the two info-level items are non-blocking.

---

_Verified: 2026-06-10T08:00:00Z_
_Verifier: Claude (gsd-verifier)_
