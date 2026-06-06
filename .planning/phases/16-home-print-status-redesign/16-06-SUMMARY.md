---
phase: 16-home-print-status-redesign
plan: 06
subsystem: ui-printstatus
tags: [four-state-home, print-status-mode, ui-model, launcher, babystep, preheat, terminal, focus-field-gutter, behavior-change]
requires:
  - "16-02: classifyPrintStatus + PrintStatusMode + selectPreheatPath + per-mode gutter sets"
  - "16-03: babystepZ + dismissPrint specs + BABYSTEP_STEPS"
  - "16-04: PrinterState.gcodeZOffset readback + selectGlanceSensor + temperatureSensors map"
  - "16-05: AppContainer.babystepEnabled/babystepLayers flows"
provides:
  - "PrintStatusUiModel (pure mode->layout/control derivation) + uiModel() + PrintStatusUiModelTest"
  - "Four-state PrintStatusScreen routed off classifyPrintStatus (Standby/Printing/Paused/Terminal)"
  - "AppShell launcher nav (onNavigate(Dest)/onOpenDrawer) + bounded <=3 ERROR-line projection"
  - "TemperatureScreen.PresetSelector promoted to internal (shared Preheat fallback)"
  - "ic_babystep_compress.xml / ic_babystep_expand.xml distinct glyphs"
affects:
  - "16-07 (docs merge: README four-state + LAYOUT flexible-tile law)"
  - "16-08 (on-device SC-5 babystep sign-verify + SC-3 perf + SC-4 gates)"
tech-stack:
  added: []
  patterns:
    - "Pure host-testable PrintStatusUiModel seam BEFORE the big Compose rewrite (ADR-0001 toolkit-agnostic gate)"
    - "Screen renders FROM the model (launcher order/gutter/active-row/error-flag are decided in pure Kotlin)"
    - "AppShell projects a bounded error-line list; the screen renders plain text (no store reach-in, no markup exec)"
    - "Per-heater capability gate at the call site (capabilities.hasObject) before each Preheat setHeater dispatch"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModel.kt
    - app/src/test/java/works/mees/dinghy/ui/printstatus/PrintStatusUiModelTest.kt
    - app/src/main/res/drawable/ic_babystep_compress.xml
    - app/src/main/res/drawable/ic_babystep_expand.xml
  modified:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt
decisions:
  - "Spoolman print line shows ACTIVE-SPOOL REMAINING only (informational/neutral) — the live PrintMetadata carries no per-job filament weight, so the required-vs-available 'accent-when-short' comparison has no data source on this surface in P16; deferred (no fabricated required value)"
  - "Removed the dead idle LastJobCard/LastJobEmpty Field surfaces (+ fmtFinished/parseHexColor helpers, unused LastJob import) — the four-state rework replaced that branch with the Standby launcher grid"
  - "Removed the now-unreachable restart ConfirmGuard — Terminal Reprint dispatches printStart DIRECTLY (no guard, no SDCARD_RESET_FILE-first, D-05); ConfirmGuard copy updated to the UI-SPEC Copywriting contract"
  - "Babystep glyphs are chevron-pairs over a bed baseline (Compress=down-toward / Expand=up-away) — distinct silhouettes, white-stroke-tinted-at-call-site"
metrics:
  duration: ~50 min
  completed: 2026-06-06
---

# Phase 16 Plan 06: Four-State Print-Status Home Summary

Reworked `PrintStatusScreen` into its definitive four-state form — routed off
`classifyPrintStatus(state)` instead of the coarse `printing` boolean — by first extracting a PURE,
host-tested `PrintStatusUiModel` (the seam Codex asked for) and then recomposing the harvested
primitives into Standby / Printing / Paused / Terminal surfaces. The three blockers Codex caught are
all wired concrete: AppShell threads `onNavigate(Dest)`/`onOpenDrawer` + a bounded ≤3 error-line
projection, `PresetSelector` is promoted to `internal`, and per-temp Preheat is capability-gated.

## What Was Built

- **`PrintStatusUiModel.kt` (new, pure — no Compose).** `uiModel(mode, …)` maps a `PrintStatusMode`
  (+ spoolmanPresent / hasBookmarkedMacros / babystepVisible) to a host-testable surface description:
  the ordered Standby `launcherDests` (Files · Temperature · Move · Extrude · Calibration ·
  Spool[if present] · Macros[if bookmarked] · Console · **Drawer always-last/flexible**), the per-mode
  `gutter` (REUSING the 16-02 `derivePrintStatusControls` builders — never re-derived), the active
  `activeRow` (Shortcut vs Babystep), and `showErrorLines` (Terminal(Error) only). A pure `LauncherDest`
  enum keeps the model UI-import-free; the screen maps each to its `Dest`.
- **`PrintStatusUiModelTest.kt` (new, GREEN).** Covers all four modes' gutter sets, the curated launcher
  order with the spool/macros conditionals + always-last flexible Drawer, the shortcut↔babystep row pick,
  and the Terminal-error flag (Error true; Complete/Cancelled false).
- **`PrintStatusScreen.kt` (reworked).** Top-level `when (classifyPrintStatus(state))` rendering from
  `uiModel`:
  - **Standby** — app-icon Focus (`ic_launcher_foreground`, faint) + a minimal centered glance overlay
    (Nozzle/Bed = `seriesColor(0/1)`, the `selectGlanceSensor` glance temp **omitted when null** — no
    host-load fallback in P16, active-spool remaining only when Spoolman present, **no connection line**);
    Field = the adaptive 2-col `LauncherGrid` where **every tile dispatches a real `onNavigate(Dest.*)`**
    and the always-last **Drawer** tile is the flexible/growing tile → `onOpenDrawer()` (NO no-op tile);
    Gutter = Preheat (accent) + inert Power (red, no-op, D-04), **no E-Stop**.
  - **Printing** — preserved `03-print-status` composition (`PrintStatusFocus`); Field = ONE framed
    `StatGrid` (now incl. the **Applied-Z-offset row** from `state.gcodeZOffset`, shown non-zero OR
    in-window) + the optional Spoolman line + the **shortcut row** (Tune flexible, per the combination
    matrix) — nozzle/bed in `seriesColor(0/1)` (NOT amber), no animated ring, no second throttle;
    Gutter = Pause · Cancel · E-Stop.
  - **Babystep row** (replaces the shortcut row in the early-layer window via
    `babystepVisible(babystepEnabled, currentLayer, babystepLayers)`): `[Compress][step][Expand]`,
    accent-outline icon-only, distinct glyphs, verbatim contentDescriptions; Compress dispatches
    `babystepZ(-step)`, Expand `+step`, center tap cycles via `nextBabystepStep`.
  - **Spool-aware Preheat (D-01)** — calls the pure `selectPreheatPath(spoolmanPresent, nozTemp, bedTemp)`
    on the resolved spool's `settingsExtruderTemp`/`settingsBedTemp`; on `DirectTemps` dispatches a
    per-temp `setHeater` for each non-null temp **EACH capability-gated** (nozzle on `extruder`, bed on
    `heater_bed`, never 0); on `OpenSelector` opens the now-`internal` `PresetSelector`.
  - **Paused** — the Printing focus DIMMED (alpha) + a static `pause_circle` overlay
    (contentDescription "Print paused"); same Field/toolset; Gutter = Resume · Cancel, **no E-Stop**.
  - **Terminal** — clean `TerminalFocus` hero (thumbnail or app-icon fallback, **no ring/dim/result-icon**)
    + the stats frame with live-only fields → em-dash; Gutter = Dismiss (`dismissPrint`) · Reprint
    (`printStart` DIRECT, **no ConfirmGuard, no SDCARD_RESET_FILE-first**). Terminal(Error) appends the
    AppShell-projected `errorLines` (≤3, plain text, hidden if empty).
- **`AppShell.kt` (wired).** The `Dest.PrintStatus` call passes `onNavigate = { navigateTo(it) }`,
  `onOpenDrawer = { drawerOpen = true }`, `onScanSpool`, and `errorLines` = a bounded ≤3 projection of
  `consoleHolder.state` filtered to `ConsoleSeverity.ERROR`, `takeLast(3)`, mapped to `rawMessage` (FIX 6).
- **`TemperatureScreen.kt`.** `PresetSelector` `private` → `internal` (signature/behavior unchanged) so
  the Preheat OpenSelector fallback reuses the one chooser.
- **`ic_babystep_compress.xml` / `ic_babystep_expand.xml` (new).** Distinct chevron-pair silhouettes over
  a bed baseline (Compress points down-toward, Expand up-away), white-stroke-tinted-at-call-site.

## Verification

- **Task 1:** `:app:testDebugUnitTest --tests PrintStatusUiModelTest` → BUILD SUCCESSFUL (GREEN, all four
  modes' gutters + launcher order + babystep-row + terminal-error flag). `PrintStatusUiModel.kt`
  grep-clean of `androidx.compose`. AppShell call site greps confirm onNavigate/onOpenDrawer + the
  bounded `ConsoleSeverity.ERROR` `takeLast(3)` errorLines; screen signature declares
  `onNavigate/onOpenDrawer/errorLines`. `:app:assembleDebug` SUCCESSFUL.
- **Task 2:** `:app:assembleDebug` SUCCESSFUL. Greps: top-level `when (classifyPrintStatus(state))` +
  `uiModel(...)`; every Standby launcher tile dispatches a real `onNavigate(Dest.*)`/`onOpenDrawer()`
  (no bare `{}` onClick); `selectGlanceSensor(state.temperatureSensors)` + `glance?.let` (omitted when
  null); Standby gutter Preheat + inert Power, no E-Stop; Applied-Z-offset row sourced from
  `state.gcodeZOffset`; nozzle/bed = `seriesColor(0/1)`; no animate/throttle/infiniteRepeatable.
- **Task 3:** `:app:assembleDebug` SUCCESSFUL. `internal fun PresetSelector` + `ui.temperature.PresetSelector`
  reuse; Preheat handler CALLS `selectPreheatPath` + per-temp `setHeater` gated on
  `hasObject("extruder")`/`hasObject("heater_bed")` (no inline if re-deriving; no 0); Compress
  `BabystepArgs(-babystepStep)` / Expand `BabystepArgs(babystepStep)`; center cycles `nextBabystepStep`;
  two distinct drawables with verbatim contentDescriptions.
- **Task 4:** `:app:testDebugUnitTest --tests 'ui.printstatus.*'` (explicit classes) → BUILD SUCCESSFUL.
  Paused gutter Resume+Cancel no E-Stop + "Print paused" overlay; Dismiss → `dismissPrint`; Reprint →
  `printStart` no guard; Terminal(Error) renders `errorLines` (hidden if empty); em-dash placeholders.
- **Full host suite:** `:app:testDebugUnitTest` (whole) → BUILD SUCCESSFUL (no regression).
  `:app:assembleDebug` → BUILD SUCCESSFUL.

## Commits

- `d1c0513` feat(16-06): pure PrintStatusUiModel + AppShell nav/error-line wiring (Task 1)
- `7c63b1f` feat(16-06): PresetSelector private->internal + distinct babystep glyphs (Task 3 artifacts)
- `04661ec` feat(16-06): route Print-Status off classifyPrintStatus into four definitive modes (Tasks 2-4)
- `c6a18af` refactor(16-06): drop the now-dead idle LastJobCard/Empty surfaces + stale Inc-3 docs

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Dead code] Removed the idle LastJobCard/LastJobEmpty Field surfaces**
- **Found during:** Task 2 (the four-state rework).
- **Issue:** The old `printing → StatGrid / idle+history → LastJobCard / idle+no-history → LastJobEmpty`
  Field branch is fully replaced by the Standby launcher grid. `LastJobCard`/`LastJobStatRow`/
  `LastJobScrollRow`/`LastJobEmpty` (and their `fmtFinished`/`parseHexColor` helpers + the unused
  `LastJob` import) became dead code with stale `TODO(nav)` KDoc.
- **Fix:** Removed all four composables + the two orphaned formatters + the unused import; rewrote the
  top-level KDoc from the stale Inc-1/2/3 narrative to the four-state model.
- **Files modified:** PrintStatusScreen.kt
- **Commit:** c6a18af

**2. [Rule 1 - Dead code] Removed the now-unreachable restart ConfirmGuard**
- **Found during:** Task 4.
- **Issue:** The legacy `showRestartGuard` ConfirmGuard was only reachable from the old idle Files
  restart path. Terminal Reprint (D-05) dispatches `printStart` DIRECTLY with no guard, so the guard +
  its `showRestartGuard` state were dead.
- **Fix:** Removed the state + the guard block; the Reprint `RestartPrint` action dispatches `printStart`
  directly. Also updated the Cancel/E-Stop ConfirmGuard copy to the UI-SPEC Copywriting contract.
- **Files modified:** PrintStatusScreen.kt
- **Commit:** 04661ec

### Spec interpretation

**Spoolman print line = active-spool remaining only (informational/neutral).** The UI-SPEC describes an
"available < required → accent" attention cue, but the LIVE `PrintMetadata` on this surface carries no
per-job filament weight (`filamentWeightTotal` lives on `FilePreviewMetadata`, not the printing
metadata). Rather than fabricate a required value, the line shows the active spool's remaining weight
only, neutral. The accent-when-short comparison is deferred until a required-weight source is wired to
the print surface. (Documented as a Known Stub below.)

## Threat Model Compliance

- **T-16-06-01** (V5, babystep dispatch): the step comes only from the fixed-cycle `nextBabystepStep`;
  the sign is code-fixed (Compress=−, Expand=+); dispatched via the V5-validated `babystepZ` spec; gated
  to the early-layer window via `babystepVisible`; session-only. ✅
- **T-16-06-02** (V5, Preheat/Reprint/Dismiss): Preheat path decided by the unit-gated `selectPreheatPath`;
  temps come from typed `Int?` spool fields (per-temp `setHeater`, never 0), EACH capability-gated (no
  bed temp to an absent `heater_bed`); Reprint uses the Moonraker-provided `restartFilename` through the
  existing `printStart` spec; Dismiss is the fixed `dismissPrint`/`SDCARD_RESET_FILE` const — no
  free-text concatenation. ✅
- **T-16-06-03** (physical motion): E-Stop + Cancel route through the full-screen ConfirmGuard (E-Stop
  tap=confirm / hold=immediate); babystep `MOVE=1` is the small in-window Z jog, session-only, no
  SAVE_CONFIG. ✅
- **T-16-06-04** (injection, terminal error lines): the lines are AppShell-projected (bounded ≤3,
  `ConsoleSeverity.ERROR`) and rendered as PLAIN TEXT (no markup execution); never interpolated into a
  command. ✅
- **T-16-SC** (package installs): none this phase. ✅

## Known Stubs

- **Spoolman print line — required-vs-available comparison deferred.** Shows active-spool remaining only
  (no per-job required-weight source on the live print surface in P16). Intentional, documented above —
  not goal-blocking (the line is explicitly "informational/optional" in the staging note). A future phase
  wiring per-job filament weight onto the print surface will restore the accent-when-short cue.
- **Tune shortcut tile** = the disabled P17 Fine-Tune stub (D-03), and the inert **Power** tile (D-04) —
  both intentional, plan-documented (rendered, no-op).

## SC-5 note (on-device, deferred to 16-08)

The babystep `+`/`−` SIGN convention (Compress = nozzle closer = `babystepZ(-step)`) is implemented per
the plan but is **DEVICE-VERIFIED on a live first layer at the 16-08 gate (SC-5)** — not guess-flipped
here. The Applied-Z-offset readout pairs with it for the on-device round-trip check.

## Self-Check: PASSED

- Files exist: PrintStatusUiModel.kt, PrintStatusUiModelTest.kt, ic_babystep_compress.xml,
  ic_babystep_expand.xml (created); PrintStatusScreen.kt, AppShell.kt, TemperatureScreen.kt (modified). ✅
- Commits exist: d1c0513, 7c63b1f, 04661ec, c6a18af. ✅
- `classifyPrintStatus` + `uiModel` drive the screen; `selectPreheatPath` + per-heater capability gate in
  the Preheat handler; `PresetSelector` is `internal`; `errorLines` from the AppShell projection; full
  host suite + assembleDebug GREEN. ✅
