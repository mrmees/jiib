---
phase: 27-motion-calibration
reviewed: 2026-06-12T13:16:21Z
depth: standard
files_reviewed: 24
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/preview/CalibrationPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/MovePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt
  - app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/calibration/CalibrationHubScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/calibration/ProbeCalibrateScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt
  - app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt
  - app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/ShellNavState.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/shell/ShellNavStateTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/FootGunDestsTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/HomeActionTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/NavDestRoundTripTest.kt
  - app/src/test/java/works/mees/dinghy/ui/route/PopToRootTest.kt
  - tools/verify_ligatures.py
findings:
  critical: 1
  warning: 5
  info: 6
  total: 12
status: issues_found
---

# Phase 27: Code Review Report

**Reviewed:** 2026-06-12T13:16:21Z
**Depth:** standard
**Files Reviewed:** 24
**Status:** issues_found

## Summary

Phase 27 migrated MoveScreen + 5 calibration screens to the jiib kit and converted the
calibration sub-nav to 6 real NavDest routes. The route plumbing is solid: dispatch-key
literals in the screens match `CommandRegistry` exactly (`probe_calibrate`,
`z_endstop_calibrate`, `testz`, `jog_X/Y/Z`, `home_xy`, `home_Z`, `z_tilt_adjust`,
`quad_gantry_level` — all verified against `CommandRegistry.kt:433-586`),
`isValidProfileName` guards the BedMesh save path including the dotted default name
(`PROFILE_NAME_ALLOWLIST` = `[A-Za-z0-9_.-]+` accepts `yy.MM.dd_HH.mm`),
`CommandDispatcher.dispatch` internally de-dupes in-flight keys (CommandDispatcher.kt:127),
`FOOT_GUN_DESTS` covers all 8 members with explicit host tests, and the swipe-suppress set
includes all six calibration routes. The 4 prior strike-classes were checked: no
`rememberCoroutineScope()` persistence writes, no stale `pointerInput` closures (the AppShell
detector is re-keyed on `navBackStackEntry`), the BedMeshHeatmapHost D-12 update-only token
guard is preserved (factory stays inside the host), and the optimistic-target class doesn't
apply (no clamped optimistic UI here).

One Critical was found in the Back-dispatch ordering that Phase 27's probe BackHandler
gating (Codex WARNING-6) relies on — the assumed priority is inverted relative to how
`OnBackPressedDispatcher` actually orders callbacks, which re-opens the D-09
abandon-live-probe hole through the prompt/scan overlay path.

## Narrative Findings (AI reviewer)

## Critical Issues

### CR-01: NavHost's internal back handler out-prioritizes the scan/prompt overlay BackHandlers — the D-09 probe gate assumption is inverted

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:506-526` (handlers), `:599` (NavHost), `:715-720` (probe gate)

**Issue:** `OnBackPressedDispatcher` fires the **most recently registered enabled** callback.
Compose `BackHandler`s register in composition order, and Navigation-Compose 2.8.x's
`NavHost` registers its own internal `PredictiveBackHandler` (enabled whenever back-stack
depth > 1) **inside** the `NavHost` call at line 599 — which is composed **after** the
shell-level scan (line 514) and prompt (line 520) handlers. The comment block at lines
491-505 asserts the opposite ("An active overlay (drawer/scan/prompt) is enabled → its
handler fires FIRST") — enabled-ness does not reorder priority; registration order does.

Verified facts: `ScanSurface.kt` and `PromptDialog.kt` contain **no** `Dialog(` window and
**no** internal `BackHandler` (grep: zero matches) — they depend entirely on the AppShell
handlers. (The drawer at line 506 is exempt: `AppDrawer` is hosted in a window-backed
`Dialog`, whose own window consumes Back via `onDismissRequest` — making the line-506
handler dead code in practice, but harmless.)

Concrete failure modes whenever the back stack is deeper than root:
1. **Scan overlay:** Spool screen (depth 2) → "Scan" → system Back ⇒ NavHost pops Spool
   *underneath the live camera overlay*; the scan stays open over the wrong screen instead
   of closing.
2. **Macro prompt:** prompt visible over any drilled-down screen → Back ⇒ NavHost pops the
   destination; `action:prompt_end` is never dispatched, prompt stays open.
3. **D-09 re-opened (the Phase-27 regression):** the probe-route BackHandler at lines
   715-720 gates itself on `!nav.scanActive && !promptView.visible` *expecting* the shell
   overlay handlers to consume Back. Since NavHost actually wins, a macro prompt visible
   during an active probe session (klicky/probe macros use the prompt protocol mid-routine)
   disables the probe gate AND lets NavHost pop `CalibrationProbe` — abandoning a live
   nozzle-descent screen via a single Back press, exactly what D-09 / T-27-04-01 forbids.

**Fix:** Register the overlay Back interception *after* the NavHost in composition so it
out-prioritizes NavHost's handler. Cleanest: move the `BackHandler`s into the overlay
composables themselves (they are composed as Box siblings AFTER the NavHost):

```kotlin
// inside the `if (nav.scanActive) { ... }` block, before ScanSurface(...):
BackHandler { nav.scanActive = false }
// inside the `if (promptView.visible) { ... }` block, before PromptDialog(...):
BackHandler {
    dispatcher?.dispatch(promptEngine.closeKey, JsonRpcMethods.GCODE_SCRIPT,
        promptEngine.scriptParamsFor(promptEngine.closeGcode))
}
```
Then delete the now-dead handlers at lines 514-526 and correct the 491-505 comment. The
probe gate at 715-720 can keep its overlay conditions (they become correct once overlays
genuinely consume Back). Verify on-device: Spool→Scan→Back, prompt-over-Move→Back, and
prompt-during-active-probe→Back.

## Warnings

### WR-01: Pop-to-root fires on EVERY printState transition and never on mid-print entry — `printActive` is dead weight

**File:** `app/src/main/java/works/mees/dinghy/ui/route/NavDest.kt:151-152`, `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:846-868`

**Issue:** `shouldPopToRoot` ignores its `printActive` parameter, and the AppShell
`LaunchedEffect(printerState.printState)` fires on *any* transition. Two consequences:
(a) a user idling in Move/Calibration after a finished print is yanked to WaterfallHome on
*safe* transitions (Printing→Complete, Complete→Standby — e.g. another client clears the
print, or Printing↔Paused churn); (b) the inverse hole: the App Drawer's Move/Calibration
tiles are always LIVE (`AppDrawer.kt:200,210` — no print-state gate), so a user can freely
*enter* a foot-gun screen mid-print and is never popped (no transition occurs), defeating
D-04's safety intent for the most direct path to danger (jogging the head mid-print). The
KDoc itself flags the "only pop when ENTERING printing" tightening as future work — Phase 27
expanded the set to 8 members without closing either edge.

**Fix:** Use the parameter: `current != null && current in FOOT_GUN_DESTS && printActive`
(pop only on transitions into Printing/Paused), and update `PopToRootTest`'s
`printActive = false ⇒ true` assertions accordingly. Separately gate (grey) the drawer's
Move/Extrude/Calibration tiles while printing, mirroring the webcam/spool grey pattern.

### WR-02: BedMesh profile REMOVE never offers SAVE_CONFIG — removed profiles resurrect after Klipper restart; `onShowSaveConfigGuard` is a dead parameter

**File:** `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt:142-150` (wrapper), `:213-238` (dead param)

**Issue:** `BED_MESH_PROFILE REMOVE` only mutates Klipper's runtime state; without a
follow-up `SAVE_CONFIG` the profile reappears on the next firmware restart. The Save path
raises the amber guard (`onSaveNameConfirm` sets `showSaveConfigGuard = true`), but
`onRemoveConfirm` does not — and the `onShowSaveConfigGuard` parameter threaded into
`BedMeshContent` is **never invoked anywhere in the composable body** (grep the Field/foot
branches: only `onSaveConfigConfirm/Cancel` appear). The persist affordance for removal was
lost in the D-11 dialog→Field migration.

**Fix:** In `onRemoveConfirm`, after dispatching the remove, set `showSaveConfigGuard = true`
(mirroring the save path); or drop the dead `onShowSaveConfigGuard` parameter if a different
persist UX is intended.

### WR-03: Hardcoded user-facing English literals across the rebuilt screens — violates the stringResource law; the phase's own pseudolocale previews cannot catch them

**File:** `app/src/main/java/works/mees/dinghy/ui/calibration/TiltScreen.kt:128-135, 282-301`; `ScrewsTiltScreen.kt:251-253, 308`; `BedMeshScreen.kt:550, 559`; `ProbeCalibrateScreen.kt:242, 257, 272, 281, 441-457, 474`

**Issue:** The PREVIEW_AND_TOKENS.md convention (every new screen ships `stringResource`
strings from day one) is broken in four of the six rebuilt screens:
- TiltScreen: titles "Z-Tilt Adjust"/"Quad Gantry Level", "$runLabel Ready to Run",
  "Home Axis First", "Running…", "Probing and adjusting…", "Failed",
  "The printer rejected the routine.", "Leveled", "Gantry leveled.",
  "Z adjustments applied:" — the *entire* screen body is raw literals.
- ScrewsTiltScreen: "base" turn label, "Bed screw map unavailable",
  "Run to probe the bed screws".
- BedMeshScreen: "No active mesh", "Activate a bed mesh, or load a saved profile.".
- ProbeCalibrateScreen: contentDescriptions "Raise nozzle (TESTZ +)",
  "Lower nozzle (TESTZ -)", "Larger step", "Smaller step", "Probe", "Z offset", "Nozzle",
  and the "saved …" reference label.
The en-XA pseudolocale previews added in `CalibrationPreviews.kt` for exactly these screens
silently pass because raw literals never pseudo-expand — the sweep is asserting nothing for
the worst offenders.

**Fix:** Externalize to `strings.xml` (the `calibration_*`/`mesh_*` blocks already exist as
the obvious home) and re-check the en-XA previews actually expand.

### WR-04: Raw, un-registered ligature glyphs in the rebuilt screens bypass both the DinghyIcons registry and the verify_ligatures.py gate — guaranteed tofu when the planned font subset lands

**File:** `app/src/main/java/works/mees/dinghy/ui/move/MoveScreen.kt:635, 703`; `ScrewsTiltScreen.kt:363-369`; `BedMeshScreen.kt:548, 605`; `ProbeCalibrateScreen.kt:440-451, 596`; `tools/verify_ligatures.py:63-124`

**Issue:** The phase registered the 5 hub Routine* tokens, but the rebuilt screens still
draw raw `MaterialSymbol(name=...)` / inline `DinghyIcon(IconRef.Ligature(...))` glyphs that
appear in **neither** `DinghyIcons.all` (the declared subset source of truth,
DinghyIcons.kt:10-12) **nor** the `NEEDED` set in `tools/verify_ligatures.py`:
- MoveScreen: `arrow_forward`, `in_home_mode`, `wifi_home` (the X+ jog cell and BOTH home
  glyph states of the XY home cell);
- ScrewsTiltScreen: `point_scan`, `anchor`, `commit`, `rotate_left`, `rotate_right` (every
  Focus bed-map state glyph);
- BedMeshScreen: `grid_off` (empty-state Focus).
Nothing today proves these resolve in the bundled ttf, and the planned `tools/subset-symbols`
(which iterates the registry) will strip them — silent tofu on the Move jog pad and the
entire ScrewsTilt visualization. Per the phase's own Icon Registration Gate and the
icon-law review rule, raw call sites surviving in *rebuilt* screens are defects.
(`expand`/`compress`/`arrow_upward`/`arrow_downward`/`add`/`remove`/`detector`/`lock`/
`lock_open_right`/`warning` are also raw but at least covered by `NEEDED`.)

**Fix:** Minimum: add the 9 missing names to `NEEDED` in verify_ligatures.py and run the
gate. Correct: register owner-blessed tokens (per [[dinghy-never-pick-icons-ask]], ASK before
naming any token — these glyphs are already in shipped use, so promotion verbatim mirrors
the 27-01 "bless as-is" precedent) and route the call sites through `DinghyIconView`.

### WR-05: BedMesh "Save" is offered with no active mesh, and the SAVE_CONFIG guard is raised before the save is known to succeed

**File:** `app/src/main/java/works/mees/dinghy/ui/calibration/BedMeshScreen.kt:131-136, 380-386`

**Issue:** The homed/no-selection foot branch shows Calibrate + **Save** regardless of
`vm.isEmpty`. With no active mesh, `BED_MESH_PROFILE SAVE` errors in Klipper (toast), yet
`onSaveNameConfirm` has already unconditionally set `showSaveConfigGuard = true` — the user
is invited to confirm an amber **Klipper restart** that persists nothing (and aborts any
queued state for no reason). The guard fires on dispatch, not on success.

**Fix:** Gate the Save foot button on `!vm.isEmpty` (the empty-Focus state already tells the
user to calibrate first), and/or raise the SAVE_CONFIG guard only after the save dispatch
resolves without a Failure event.

## Info

### IN-01: Dead code — `LauncherGrid` has zero callers

**File:** `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt:432-473`
**Issue:** The Phase-24 Standby redesign replaced the tile grid with
`PrintStatusStandbyField`; `LauncherGrid` (and its Drawer flexible-tile layout logic) is no
longer referenced anywhere in main or test sources. `LauncherTile`/`launcherDestTarget`/
`launcherIcon`/`launcherLabelRes` remain live via `ShortcutRow`.
**Fix:** Delete `LauncherGrid`.

### IN-02: Unused string resources

**File:** `app/src/main/res/values/strings.xml:437, 443, 451, 452, 490`
**Issue:** `cd_move_jog_pad`, `cd_routine_probe_calibrate`, `cd_calibration_hub`,
`calibration_hub_title`, `cd_mesh_heatmap` are defined but referenced nowhere in code
(grep: zero hits).
**Fix:** Wire them (e.g. a contentDescription on the jog pad / heatmap) or remove them.

### IN-03: SampleFixtures — unused `variant` parameter and orphaned KDoc

**File:** `app/src/main/java/works/mees/dinghy/preview/SampleFixtures.kt:236-247, 388-392`
**Issue:** `tiltContent(variant, ...)`'s `variant` parameter is never used in the body (the
KDoc claims it "drives the title string" but `TiltVm` carries no title — the previews pass
variant separately to `TiltContent`). Also the `probeVm` KDoc block at lines 236-244 was
orphaned from its function when the BedMesh fixtures were inserted between them.
**Fix:** Drop the parameter (update preview call sites) and move the KDoc back onto `probeVm`.

### IN-04: Locale-dependent number formatting in the ScrewsTilt bed map

**File:** `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt:398`
**Issue:** `"%.3f".format(turn.z)` uses the default locale (renders `0,025` on
comma-decimal locales), inconsistent with `Locale.US` used everywhere else in this package
(`fmtZ`, `TiltFieldBody:315`). Also `shortScrewName` (line 413) only trims a lowercase
`"screw"` suffix.
**Fix:** `String.format(Locale.US, "%.3f", turn.z)`; case-insensitive suffix trim if desired.

### IN-05: Permanently-invisible FloatingEStop rendered on all 5 calibration screens + Move-adjacent hubs

**File:** `CalibrationHubScreen.kt:128-133`, `ProbeCalibrateScreen.kt:216-221`, `BedMeshScreen.kt:466-471`, `ScrewsTiltScreen.kt:231-236`, `TiltScreen.kt:216-221`
**Issue:** Each screen composes `FloatingEStop(visible = false, onClick = {})` — dead UI by
construction (the comments call it a "structural contract reservation"). The AppShell-level
e-stop already renders over these destinations when printing, so if the mid-print entry
hole (WR-01b) is ever exercised, the local stub contributes nothing; it is pure noise plus
a no-op lambda allocation per recomposition.
**Fix:** Remove the five stubs, or wire `visible` to the real print state and add these
routes to AppShell's `screenOwnsEstop` suppression set — half-measures satisfy neither
contract.

### IN-06: ScrewsTilt direction glyph tints reuse the safety-intent tokens (CW=`t.go` green, CCW=`t.stop` red)

**File:** `app/src/main/java/works/mees/dinghy/ui/calibration/ScrewsTiltScreen.kt:371-378`
**Issue:** The button-intent color law reserves red = stop/cancel and green = accept; using
them for *rotation direction* on the bed map gives "turn this screw clockwise" the same
visual weight as "go/safe" and CCW the same as "danger", with no matching color in the Field
list rows. Carried over from the prior implementation, but this was a restyle pass — worth
an owner decision (e.g. both directions in accent, direction carried by the glyph alone).
**Fix:** Confirm with the owner; if changed, tint both `rotate_left`/`rotate_right` with
`t.accent2`.

---

_Reviewed: 2026-06-12T13:16:21Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
