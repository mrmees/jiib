# PrintStatus — collapse all printer states to one home skeleton

**Date:** 2026-06-15
**Status:** design approved, pre-plan
**Owner:** Matthew

## Problem

The home screen (`PrintStatusScreen.kt` and friends in `ui/printstatus/`) renders **four
hand-built per-state variations** routed by `classifyPrintStatus` → `PrintStatusMode`
(Standby / Printing / Paused / Terminal[Complete|Cancelled|Error]). Each variation has its
own Focus composable, its own Field content, and its own foot-bar button set, plus
per-state filtering in `PrintStatusUiModel.uiModel()` and `derivePrintStatusControls()`.

Almost all of that work predates the project's current UI structure (the jiib lists-first
redesign, the Focus/Field-no-gutter grammar, the FocusFrame header + docked-e-stop law, the
named-role type system). Retrofitting each bespoke variation to current standards is more
work than deleting them and rebuilding from a single clean baseline.

## Goal

Collapse **every Moonraker-reported printer state** to **one** home screen — the current
Standby treatment, with a reworked Focus frame — as a clean skeleton that future per-state
customization will build up from. Do the housekeeping (delete the bespoke variations) and a
small amount of layout work on the Focus frame.

## Scope: what's in the bucket

The codebase has **two distinct state axes** (Codex review, 2026-06-15):

- `PrinterState.printState` (`print_stats.state`): Standby / Printing / Paused / Complete /
  Cancelled / Error — the print-job lifecycle. **All of these already reach PrintStatus.**
- `PrinterState.klippyState` (`KlippyState`: Disconnected / Startup / Ready / Error / Shutdown)
  — the Klipper *host* lifecycle, a separate axis (`PrinterState.kt:349`).

Top-level routing lives in `TopRoute.derive()` (`TopRoute.kt:49-54`) and currently sends
**any `klippyState != Ready` to `Splash`** (line 51) — so a Klipper shutdown/error never
reaches PrintStatus today, even with Moonraker connected.

**In (collapse to the skeleton):**
- Every `printState` (Standby/Printing/Paused/Complete/Cancelled/`Error`) — these already
  route to PrintStatus; they share the one skeleton.
- **Klipper `Error` and `Shutdown`** while Moonraker is connected — **re-routed** into the
  skeleton (owner decision, 2026-06-15). This is the scope increase beyond `printstatus/`:
  `TopRoute.derive()` must let these reach `Shell` instead of `Splash`.

**Out (untouched — stays at Splash):**
- **Moonraker connection faults** — `connection !is ConnectionState.Connected` (websocket
  down / host unreachable). The genuine "different bucket."
- **Klipper `Disconnected` / `Startup`** — the host isn't really up yet (klipper service down
  or firmware still booting); the recovery/initializing Splash remains the right surface.

So the refined `TopRoute.derive()` intent:

```
!cfgPresent                                   -> Connect
connection !is Connected                      -> Splash   // real connection fault
klippyState == Disconnected || Startup        -> Splash   // host not up yet
else (Ready, Error, Shutdown)                 -> Shell     // skeleton home
```

## Non-goals

- No per-state build-up. Re-introducing a progress ring, terminal result hero, pause/cancel
  controls, babystep row, etc. is explicitly **future work** built from this baseline.
- No per-state icons. The title icon stays the current standby glyph for every state; per-state
  glyphs are a build-up decision (and require an owner icon ruling — never auto-pick).
- No change to the **connection-fault** arm of routing (`connection !is Connected → Splash`)
  or to the `Disconnected`/`Startup` Splash arms. The only routing edit is admitting Klipper
  `Error`/`Shutdown` to the skeleton.
- No change to capability gating (see below). The e-stop *mechanism* is preserved, not
  redesigned — but it must be explicitly re-wired into the universal focus (see Part A).

## Design

### Part A — collapse the rendering

`PrintStatusContent`'s `when(mode)` collapses to a **single rendering path**: the reworked
universal Focus + the idle-action Field list + the Standby foot bar, for all printer states.

**Delete:**
- `PrintStatusFocus` (progress ring) and `TerminalFocus`.
- `PrintStatusActiveField`, `PrintStatusTerminalField`, `ShortcutRow`, `BabystepRow`, the
  StatGrid, and the terminal stats / error-line composables.

**Gut:**
- `PrintStatusUiModel` per-state logic (`launcherDests`, `foot`, `activeRow`,
  `showErrorLines`).
- `derivePrintStatusControls` and the pause / resume / cancel pending-action machinery.

**Keep:**
- `StandbyFocus` → becomes the **universal Focus**, reworked per Part B.
- `PrintStatusStandbyField` → becomes the **universal Field**.
- `buildIdleActions` / `HomeAction` (the idle-action list).
- A **minimal `state → label`** helper for the title (two-axis: Klipper fault precedence,
  else `printState` — see Part B). The richer `PrintStatusMode` rendering/model divergence is
  removed; modes return during build-up.

**Routing change (outside `printstatus/`):** `TopRoute.derive()` (`TopRoute.kt:49-54`) is
edited so Klipper `Error`/`Shutdown` reach `Shell` while a real connection fault and Klipper
`Disconnected`/`Startup` still go to `Splash` (see Scope). This is the one deliberate
out-of-package edit.

**Two distinctions deliberately preserved:**
- **Capability gates stay.** Hiding Spool / Outputs / Webcam rows when those capabilities are
  absent is *capability* filtering, not *print-state* filtering — correct in every state.
- **E-stop must be explicitly preserved (correction, Codex 2026-06-15).** The kept
  `StandbyFocus` does **not** currently pass `isPrinting` / `onEmergencyStop` into
  `FocusFrame` (`PrintStatusFocus.kt:214-219`) — it never needed to, being standby-only. The
  e-stop mechanism for PrintStatus is also ambiguous between two documented designs that
  must be reconciled against live code during planning: the **AppShell-overlay FloatingEStop**
  (per `PrintStatusScreen.kt:148,316` comments) vs the **FocusFrame docked-header e-stop**
  (per the 2026-06-13 Focus Frame law / `screenOwnsEstop` gate). The plan must confirm which
  is live for PrintStatus and ensure the reworked universal focus wires e-stop so it stays
  reachable in every state — including the re-routed Klipper Error/Shutdown, where e-stop
  matters most. Per-state foot actions (Pause/Cancel/Resume) return in the build-up; the
  interim foot bar is Preheat + System.

**Interim behavior note:** during this skeleton phase, while a print runs the foot bar shows
Preheat + System (no Pause/Cancel) and the Focus shows the glance block (no progress). This
is an accepted interim state; richer per-state content is the next effort.

### Part B — universal Focus frame layout

Reworking the kept `StandbyFocus`:

- **Title.** Single saved printer profile → just the **state label**. **More than one** saved
  printer profile (regardless of which is active) → `"<printer name> · <state>"` (middle-dot
  separator). Title icon stays the current standby glyph for all states.
  - **State label derivation (two-axis).** Because Klipper `Error`/`Shutdown` now reach this
    screen, the label is **not** purely `printState`. A Klipper fault takes precedence:
    `klippyState == Shutdown` → `Shutdown`, `klippyState == Error` → `Error`; otherwise the
    `printState` label (`Ready` / `Printing` / `Paused` / `Complete` / `Cancelled` / `Error`).
    This is the single `state → label` helper noted in Part A.
- **Brand mark.** Shrink the jiib mark to **30% of `min(width, height)`**, aligned
  **bottom-end**, keeping the current faint accent2 tint + alpha.
- **Glance text.** Aligned **top-start**. Same four rows as today: Nozzle · Bed ·
  [glance sensor, when a real reading exists] · [spool remaining, when Spoolman present + a
  spool is loaded].
- **Sizing.** Label **and** value both at `focusHero` (~30sp). Value keeps its color
  (series color / `text`); label in dim `text2`.
- **Spacing.** Tightened — minimal line spacing, no excess gaps; label and value sit on one
  line per row.

Net result: text reads top-start, the watermark sits small at bottom-end — the inverse of
today's centered-glance-over-full-frame-watermark.

## Affected files (indicative)

- `ui/printstatus/PrintStatusScreen.kt` — collapse `PrintStatusContent` to one path; thread
  printer-name + profile-count for the title.
- `ui/printstatus/PrintStatusFocus.kt` — rework `StandbyFocus` (Part B); delete
  `PrintStatusFocus`, `TerminalFocus`.
- `ui/printstatus/PrintStatusField.kt` — keep/rework `PrintStatusStandbyField`; delete the
  active/terminal field composables and their helper rows.
- `ui/printstatus/PrintStatusUiModel.kt`, `PrintStatusControlModel.kt`, `PrintStatusMode.kt`
  — gut per-state model/control logic; retain a minimal state-label mapping.
- `ui/route/HomeAction.kt` — unchanged (idle-action list + capability gates retained).
- `ui/route/TopRoute.kt` — `derive()` re-routes Klipper `Error`/`Shutdown` to `Shell`
  (the one out-of-package edit).

## Open verification items (for planning, not blockers)

- **Connection-fault path is confirmed outside `PrintStatusContent`** (Codex 2026-06-15):
  `TopRoute.derive()` routes non-connected/non-ready states to `Splash` before AppShell
  composes, so collapsing the `when(mode)` cannot affect it. ✓
- **E-stop mechanism** — confirm which design is live for PrintStatus (AppShell-overlay
  FloatingEStop vs FocusFrame docked header) and wire it into the universal focus; verify it
  works in the re-routed Klipper Error/Shutdown case. `StandbyFocus` does not wire it today.
- Confirm how profile count + active printer name are obtained from `AppContainer` for the
  title (multi-printer detection = more than one saved profile).
- **Tests/preview fixtures depend heavily on `PrintStatusMode`, `uiModel()`, and
  `derivePrintStatusControls`** (Codex 2026-06-15) — deletion requires pruning or rewriting
  them, plus any `TopRoute.derive()` tests for the new Klipper-state routing arms.

## Success criteria

1. Every `printState` and Klipper `Error`/`Shutdown` (Moonraker connected) renders the same
   reworked Standby home; genuine connection faults and Klipper `Disconnected`/`Startup` stay
   at Splash.
2. The bespoke Printing/Paused/Terminal Focus, Field, and control code is deleted (not just
   bypassed); dependent tests/preview fixtures are pruned or rewritten.
3. The Focus frame matches Part B (two-axis title rule, 30% bottom-end watermark, top-start
   glance, uniform focusHero sizing, tightened spacing).
4. Capability gates and e-stop still work — e-stop verified reachable in the re-routed
   Klipper Error/Shutdown case.
5. `TopRoute.derive()` routes the Klipper-state arms as specified (with tests).
6. Build + test suite green.
