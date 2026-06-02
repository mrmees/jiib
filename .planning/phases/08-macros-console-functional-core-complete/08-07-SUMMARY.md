---
phase: 08-macros-console-functional-core-complete
plan: 07
subsystem: ui-shell
tags: [macros, console, navigation, datastore, MACRO-01, MACRO-02, MACRO-03, CONS-02, B1, on-device-uat, perf-gate]

requires:
  - phase: "08-03"
    provides: "MacroPrefs(injected DataStore) — bookmarks/revealHidden prefs class (B1 needed a real store)"
  - phase: "08-05"
    provides: "ConsoleHolder + ConsoleScreen (read-only severity-colored scrollback, gcode_store backfill)"
  - phase: "08-06"
    provides: "MacroHolder + BookmarkedMacrosScreen/SystemMacrosScreen + MacroExecutionPopup (callbacks wired here)"
provides:
  - "macros.preferences_pb process-scoped DataStore (DinghyApp → AppContainer.macroPrefs → MacroHolder) — closes B1"
  - "Dest.Macros + Dest.Console route enum entries; live Macros drawer tile + new Console tile (terminal glyph)"
  - "AppShell when(dest) branches building session-owned ConsoleHolder/MacroHolder; bookmark/reveal callbacks wired to MacroPrefs"
  - "drawer swipe-up suppression extended to Console + System macro list (D-05)"
  - "ON-DEVICE PROOF: functional core complete on real flox + live Ender 5 Plus (7/7 UAT checks PASS)"
affects: ["phase-9", "any future panel: copy this DataStore-into-AppContainer + when(dest) + swipe-suppress pattern"]

tech-stack:
  added: []
  patterns:
    - "Third process-scoped DataStore (macros.preferences_pb) mirrors theme/connection: PreferenceDataStoreFactory.create(appScope) → AppContainer ctor → val macroPrefs (NOT SpineHandle — survives reconnects)"
    - "Screens expose bookmark/reveal MUTATION + nav as caller lambdas; the shell when(dest) wires them to MacroPrefs suspend fns + back-stack (holder stays pure/host-testable)"
    - "drawer-swipe suppress set: if (dest !in setOf(Files, Console, Macros)) — finger-scrollable Fields opt out, explicit green Back is the exit"

key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/DinghyApp.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - docs/moonraker-capabilities.md

key-decisions:
  - "macros.preferences_pb is its OWN file (not shared with theme/connection) — separate-file discipline, independent lifecycle; macros carry no secrets but keep the boundary"
  - "macroPrefs is process-scoped on AppContainer (like ThemePrefs/ConnectionStore), NOT session-scoped on SpineHandle — bookmarks/revealHidden survive reconnects (MACRO-03)"
  - "Console tile uses the 'terminal' glyph (unused elsewhere in DRAWER_TILES) per the icon-no-repeat law"
  - "perf measured via gfxinfo (system-of-record on API 30) not FrameTimingMetric; continuous-scroll-only run after confirming the overflow bucket was idle-attribution"

patterns-established:
  - "Pattern: new panel = (optional) DataStore in DinghyApp→AppContainer, Dest enum entry, drawer tile, when(dest) branch building a session-owned holder, swipe-suppress opt-out if it scrolls"
  - "Pattern: on-device UAT + gfxinfo perf gate on flox is the non-negotiable backstop for any live-data screen (two prior phases shipped green suites that hid live bugs)"

requirements-completed: [MACRO-01, MACRO-02, MACRO-03, CONS-02]

duration: ~40min
completed: 2026-06-02
---

# Phase 8 Plan 07: Nav Wiring + Functional-Core On-Device Gate Summary

**Wired the four Macros/Console screens into the shell (route enum + live drawer tiles + when(dest) branches), created the missing `macros.preferences_pb` process-scoped DataStore to make macro bookmarks actually persist (B1), and PROVED the whole functional core on real flox + live Ender 5 Plus — 7/7 UAT checks PASS, console scroll p95 9ms / 0 frozen frames on the Adreno-320 floor.**

## Performance

- **Duration:** ~40 min (incl. on-device UAT round-trip)
- **Started:** 2026-06-02 (Task 1)
- **Completed:** 2026-06-02
- **Tasks:** 3 (2 auto + 1 human-verify checkpoint)
- **Files modified:** 5 production + 2 test + 1 doc

## Accomplishments

- **B1 closed:** `MacroPrefs` (from 08-03) finally has a real production DataStore. A third `macros.preferences_pb` store is created in `DinghyApp.onCreate` (own file, app-scoped), threaded into the `AppContainer` ctor, and exposed as the process-scoped `val macroPrefs` — so macro bookmarks/reveal-hidden survive reconnects AND app restarts.
- **Both escape hatches reachable:** `Dest.Macros`/`Dest.Console` added to the route enum; the greyed Macros drawer tile flipped live (`dest = Dest.Macros`) and a new Console tile added (`terminal` glyph, icon-no-repeat law). `AppShell.when(dest)` now builds the session-owned `ConsoleHolder`/`MacroHolder` and renders the Bookmarked launcher / System manager / Execution popup, with bookmark+reveal callbacks wired to `MacroPrefs`.
- **Drawer-swipe suppression** extended from Files-only to `{Files, Console, Macros}` so the swipe-up gesture doesn't fight the finger-scrollable Console scrollback and System macro list (D-05); explicit green Back stays the exit.
- **Functional core PROVEN on real hardware** (the gate): on flox + live Ender 5 Plus, all 7 UAT checks pass — console backfills from `gcode_store` and recovers after reconnect (SC #3), a real param macro executes and its bookmarks persist across restart (B1 on-device), and string-param injection (newline/`;`/M112) is rejected with NO estop (B2/T-08-07-T).

## Task Commits

1. **Task 1 [B1]: macros.preferences_pb DataStore wiring (DinghyApp → AppContainer → MacroPrefs)** - `8cd685b` (feat)
2. **Task 2: Nav wiring — Dest enum, drawer tiles, AppShell branches + drawer-swipe suppression (D-05/D-07/D-11)** - `c6b14c3` (feat)
3. **Task 3: On-device UAT + console-scroll perf gate on flox + live Ender 5 Plus** - checkpoint (human-verify; PASSED, results recorded in `docs/moonraker-capabilities.md` via `376b8ad`)

**Plan metadata:** (this SUMMARY + STATE/ROADMAP/REQUIREMENTS commit follows)

## Files Created/Modified

- `app/src/main/java/works/mees/dinghy/DinghyApp.kt` - adds the third `macroDataStore` (`macros.preferences_pb`), passes it into `AppContainer(...)`
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` - `macroDataStore` ctor param + process-scoped `val macroPrefs = MacroPrefs(macroDataStore)`; session holder wiring
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` - `Dest` enum += `Macros, Console`
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` - Macros tile live (`dest = Dest.Macros`) + new Console tile (`symbol = "terminal"`)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` - `when(dest)` branches for Macros/Console, holders built from the session store, MacroPrefs callback wiring, swipe-suppress set extended
- `docs/moonraker-capabilities.md` - dated "Phase 8 — on-device UAT + console-scroll perf" note with the verbatim gfxinfo numbers
- (test) `AppContainerTest.kt`, `ShellPresenceTest.kt` - updated for the new ctor param / nav surface

## On-Device UAT + Perf Results

Run on **flox** (Nexus 7 2013, LineageOS 18.1 / API 30, Adreno 320 — the perf FLOOR) against the **live Ender 5 Plus** (`192.168.1.120:7125`). Matthew confirmed checks 1–6 by hand; the orchestrator measured check 7. **7/7 PASS.**

1. **Console backfill + live** — populates from `server.gcode_store`, new lines append live. PASS
2. **Severity color** — `!!` red / `// ` amber / normal default (color + text). PASS
3. **Filters (D-03/D-04)** — toggle ON hides, OFF re-reveals (raw buffer survives). PASS
4. **Reconnect backfill (SC #3)** — disconnect-window lines recovered, not dropped. PASS
5. **Macros incl. bookmark-persist (B1)** — System list, hidden-helpers gate, param macro executes, **bookmarks persist across app restart**. PASS
6. **Security injection-reject (B2/T-08-07-T)** — newline/`;`/M112 rejected, **no estop / no extra command** on the live printer. PASS
7. **Console-scroll perf (gfxinfo, system-of-record on API 30)** — `adb shell dumpsys gfxinfo works.mees.dinghy`, reset → ~15s continuous scroll → dump. **Continuous-scroll-only, 991 frames:** p50=7ms, p90=8ms, **p95=9ms**, p99=11ms; janky 1 (0.10%); **frozen (≥700ms) = 0**; missed vsync 0; slow UI thread 0. Beats the Files gate (07-06 p95 ~15ms). A first pass's 32-frame 4950ms overflow bucket was confirmed **idle-attribution**; re-measured clean scroll-only → 0 frozen. PASS

## Decisions Made

- `macros.preferences_pb` is a **separate file** from theme/connection (independent lifecycle, separate-file discipline) and lives **process-scoped on AppContainer** (not session-scoped on SpineHandle) so bookmarks survive reconnects — the correct read of MACRO-03.
- Console tile uses the **`terminal`** glyph (unused on the drawer) per the icon-no-repeat law.
- Perf measured via **gfxinfo** (FrameTimingMetric unreliable on this floor); the overflow bucket on the first pass was investigated and dismissed as idle-attribution rather than fabricating a clean number.

## Deviations from Plan

None - plan executed exactly as written. The "System macro list reached via Manage" and the MacroExecutionPopup overlay were rendered as the plan's `when(dest)`/sub-state described; the bookmark/reveal callbacks (left as caller lambdas by 08-06's documented contract adaptation) were wired to `MacroPrefs` here exactly as that plan anticipated.

## Issues Encountered

None during the planned auto tasks. The on-device UAT's first perf pass showed an alarming 4950ms overflow bucket (32 frames) — investigated and confirmed to be gfxinfo charging idle wall-time to the histogram, not real frozen frames; a continuous-scroll-only re-measure returned 0 frozen frames. This is documented in the docs note so future readers don't re-chase the same ghost.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- The Macros & Console functional core is wired and **proven on real hardware** — the phase's connect → console-backfill/recover → run-a-param-macro → reject-injection → smooth-scroll loop works on the Adreno-320 floor.
- Phase-level verification and the overall phase-complete mark are owned by the orchestrator (this plan deliberately does not run them).
- Established pattern for any future panel: DataStore (if needed) in DinghyApp→AppContainer, Dest enum entry, drawer tile, `when(dest)` branch with a session-owned holder, swipe-suppress opt-out if it scrolls.

## Self-Check: PASSED

- SUMMARY file FOUND: `.planning/phases/08-macros-console-functional-core-complete/08-07-SUMMARY.md`
- Task commits all FOUND: `8cd685b` (Task 1), `c6b14c3` (Task 2), `376b8ad` (docs UAT note).

---
*Phase: 08-macros-console-functional-core-complete*
*Completed: 2026-06-02*
