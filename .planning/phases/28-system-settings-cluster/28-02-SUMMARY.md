---
phase: 28-system-settings-cluster
plan: "02"
subsystem: navigation,ui-screen,preview
tags: [navdest, system-page, brand-identity, preview-matrix, wave-2]
dependency_graph:
  requires:
    - "28-01 (DinghyIcons SystemRow* tokens + dense ListRow/TokenTextField flags)"
  provides:
    - NavDest.System route (serialization-safe, in knownNavDests, absent from FOOT_GUN_DESTS)
    - SystemPageScreen (thin VM wrapper)
    - SystemPageContent stateless seam
    - SystemPagePreviews.kt (6-combo + fsL + RTL + pseudolocale + landscape matrix)
    - SampleFixtures.systemPageActivePrinter / systemPageVersion constants
  affects:
    - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/FootGunDestsTest.kt
    - app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/SystemPagePreviews.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
tech_stack:
  added: []
  patterns:
    - BoxWithConstraints orientation detection + focusGrow/fieldGrow ratio cap (D-02: 20% portrait / 40% landscape)
    - Stateless content seam (WARNING-5 preview-first convention)
    - SystemNavRow data class for D-03 ordered nav row list
    - Power stub via plain Row (no ListRow, no onClick) with stop.copy(alpha=0.38f)
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt
    - app/src/main/java/works/mees/dinghy/preview/SystemPagePreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
    - app/src/test/java/works/mees/dinghy/ui/route/FootGunDestsTest.kt
    - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
decisions:
  - "NavDest.System routes to NavDest.Devices (Printers row) — correct: Printers screen is the Devices dest"
  - "SystemNavRow data class keeps D-03 order as a statically-typed list, not an enum"
  - "PowerStubRow is a plain Row (not ListRow) with no onClick — enforces inert-no-tap contract without disabling a ListRow"
  - "activeName StateFlow<String?> handled with ?: empty string in VM wrapper — stateless seam always receives non-null String"
metrics:
  duration: ~18 minutes
  completed: "2026-06-12"
  tasks_completed: 3
  tasks_total: 3
  files_modified: 6
---

# Phase 28 Plan 02: NavDest.System Route + SystemPageScreen Summary

**One-liner:** NavDest.System route registered (round-trip tested, foot-gun safe) and SystemPageScreen built with a static ratio-capped brand Focus + D-03 direct-tap dense rows + inert Power stub; full preview matrix ships.

## Tasks Completed

| Task | Name | Commit | Key files |
|------|------|--------|-----------|
| 1 | Add NavDest.System route + extend the round-trip test | 2807775 | NavDest.kt, NavDestRoundTripTest.kt, FootGunDestsTest.kt |
| 2 | Build SystemPageScreen | 4adf38c | SystemPageScreen.kt |
| 3 | SystemPage preview matrix + SampleFixtures | 7c51dda | SystemPagePreviews.kt, SampleFixtures.kt |

## What Was Built

**Task 1 — NavDest.System route:**
- `@Serializable data object System : NavDest` added after `NavDest.About` in the sealed interface
- `knownNavDests` updated to include `NavDest.System` (count rises 22→23)
- `FOOT_GUN_DESTS` docstring updated with comment: System cluster intentionally absent (D-06, mid-print reachable); `else -> null` fallback in `shouldPopToRoot` covers it
- `NavDestRoundTripTest`: added `system_roundTrips`, `system_isInKnownNavDests`, `system_isNotInFootGunDests`
- `FootGunDestsTest.midPrintDests_notInFootGunDests`: added `NavDest.System` to valid mid-print list

**Task 2 — SystemPageScreen:**
- `SystemPageScreen(container, onNavigate, onBack)`: thin wrapper collecting `container.activeName` (StateFlow<String?> → null-coalesced to empty string for stateless seam)
- `SystemPageContent(activePrinterName, versionName, onNavigate, onBack)`: stateless seam
- Focus (D-02 ratio cap): `focusGrow`/`fieldGrow` computed from `BoxWithConstraints` orientation — `0.2f/0.8f` portrait, `0.4f/0.6f` landscape
- Focus content: jiib wordmark via `brandTint(t.accent, t.bg, t.text)` + `BuildConfig.VERSION_NAME` (Geist Mono 15sp) + active printer name (Geist SemiBold 17sp)
- Field rows in D-03 order via `systemNavRows()` list: Printers→`NavDest.Devices`, Settings→`NavDest.Settings`, Theme→`NavDest.Theme`, System Info→`NavDest.SystemInfo`, About→`NavDest.About`
- All 5 rows: `ListRow(dense=true, selected=false, onClick={onNavigate(dest)}, ...)` — direct-tap nav, no picker
- Power stub (D-08): plain `Row` with no onClick, `t.stop.copy(alpha=0.38f)` icon+label, `t.text2.copy(alpha=0.38f)` "Coming soon" sublabel
- `ScreenScaffold(gutter=null)` — LAW; `FootButtonBar` Back-only (Intent.Neutral) in the field lambda
- No `FloatingEStop` rendered; no `rememberCoroutineScope`; shell-level e-stop applies (D-06)

**Task 3 — Preview matrix:**
- `SystemPagePreviews.kt`: 13 `PreviewBox` panels
  - State axis: printer-present / no-printer (2 panels, colorfulDark)
  - 6 theme combos via `@Nexus7Previews` annotation (colorfulDark/Light, simpleDark/Light, highContrastDark/Light)
  - `fsLargeSeed` overflow checks: portrait + landscape (2 panels)
  - RTL spot check via `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`
  - Pseudolocale en-XA spot check (`locale = "en-XA"`)
  - Landscape spot panel proving 40%-width Focus column
- `SampleFixtures`: added `systemPageActivePrinter = "Ender 5 Plus"` and `systemPageVersion = "0.1.0-debug"` constants

## Verification Results

- `:app:testDebugUnitTest --tests *NavDestRoundTripTest --tests *FootGunDestsTest`: BUILD SUCCESSFUL (all pass)
- `:app:assembleDebug`: BUILD SUCCESSFUL (screen + previews compile)
- Grep gates:
  - `grep -c 'fun SystemPageContent' SystemPageScreen.kt` → 1
  - `grep -c 'gutter = null' SystemPageScreen.kt` → 1
  - `grep -c 'BuildConfig.VERSION_NAME' SystemPageScreen.kt` → 3 (1 in code, 2 in KDoc)
  - `grep -c 'FloatingEStop(' SystemPageScreen.kt` → 0 (KDoc mentions only)
  - `grep -c 'rememberCoroutineScope' SystemPageScreen.kt` → 0
  - Power stub uses `0.38f` alpha on icon, label, and sublabel

## Deviations from Plan

None. Plan executed exactly as written.

- The `activeName: StateFlow<String?>` nullability (returns `String?` not `String`) required `?: ""` in the wrapper — this is a one-line null-safe coerce, not an architectural deviation.
- `FloatingEStop` appears 2 times in the file but only in KDoc comments (documenting the D-06 decision); zero actual `FloatingEStop(...)` function calls. The plan's acceptance criterion intent is met.

## Known Stubs

The Power row is an intentional documented stub (D-08): `system_row_power_sub` = "Coming soon". This is correct per the plan — Power is a placeholder for a future phase. Owner judges "Coming soon" copy at UAT.

## Threat Flags

None. SystemPageScreen renders only:
1. Static brand identity (BuildConfig.VERSION_NAME, user-set displayName — no API key, no host secret)
2. Navigation rows (destinations only, no network surface)
3. Shell-level FloatingEStop not suppressed (SystemPageScreen NOT in screenOwnsEstop)

This is consistent with T-28-02-01 (accept: version + display label only) and T-28-02-02 (mitigate: e-stop NOT suppressed, verified by grep gate).

## Self-Check: PASSED

- SystemPageScreen.kt: FOUND at `app/src/main/java/works/mees/dinghy/ui/screen/SystemPageScreen.kt`
  - Contains `fun SystemPageContent`: YES
  - Contains `gutter = null`: YES
  - Contains `BuildConfig.VERSION_NAME`: YES (in code)
  - Contains `FloatingEStop(` call: NO (KDoc only — correct)
  - Contains `rememberCoroutineScope`: NO
- SystemPagePreviews.kt: FOUND at `app/src/main/java/works/mees/dinghy/preview/SystemPagePreviews.kt`
  - Contains 13 `PreviewBox` calls (>= 6 required)
  - Contains `fsLargeSeed`: YES
  - Contains `LayoutDirection` and `Rtl`: YES
- NavDest.kt: `data object System` present, in `knownNavDests`, absent from `FOOT_GUN_DESTS` setOf(...) body
- Commit 2807775: verified in git log (Task 1)
- Commit 4adf38c: verified in git log (Task 2)
- Commit 7c51dda: verified in git log (Task 3)
- Test run: BUILD SUCCESSFUL (NavDestRoundTripTest + FootGunDestsTest)
- assembleDebug: BUILD SUCCESSFUL
