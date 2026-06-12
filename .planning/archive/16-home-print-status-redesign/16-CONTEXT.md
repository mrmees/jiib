# Phase 16: Home / Print-Status Redesign - Context

**Gathered:** 2026-06-06
**Status:** Ready for planning

<domain>
## Phase Boundary

Rework the home / Print-Status surface into its **definitive, state-driven** form — the visual
FOUNDATION the remaining feature phases (17 Fine-Tune, 18 Output, 19 SysInfo, 20 WebRTC) slot into
without forcing a later redesign. Every state renders through the existing **Focus / Field / Gutter**
grammar (portrait + landscape, sacred aspect ratios, ratio-only sizing).

**The core move:** introduce ONE classifier — `PrintStatusMode` — derived from **Moonraker state
only**, and render composables from that mode instead of scattering raw `printState` checks. Modes:

- `Standby`
- `Printing`
- `Paused`
- `Terminal(kind = Complete | Cancelled | Error)`

Classification rules (Moonraker-derived, no app-remembered state):
`printing→Printing` · `paused→Paused` · `complete→Terminal(Complete)` · `cancelled→Terminal(Cancelled)`
· `error→Terminal(Error)` · `standby→Standby` even if stale filename/job data exists. Klippy
shutdown/error does **not** create a terminal print-result mode — app-level recovery routing owns it.

**Also in this phase:** the conditional **Z-babystep** control (moved here from Phase 17), shown on
Print-Status only during the early first-layer window (~first N layers), session-only.

**The precursor staging note is the primary spec** (see canonical refs) — it locks the four-state
model, every state's Focus/Field/Gutter layout, the interactive-grid flexible-tile rule, babystep
mechanics, and terminal behavior. This CONTEXT captures only the decisions the staging note left open.

**Explicitly NOT this phase (no scope creep):** new functional REQ-IDs/backend (UX rework only);
building the forward features themselves (17–20 — only greyed stubs here); saving Z-offset to config
(stays a Calibration action); mid-print object exclusion (v2). No regression to the proven
Views-based render/throttle primitives (GraphView, status) or the core monitor loop.

</domain>

<decisions>
## Implementation Decisions

These are the gray areas the staging note did NOT resolve. Everything else flows from the staging
note verbatim (canonical ref below) — do not re-litigate those.

### Standby — Preheat action (gutter)
- **D-01:** Preheat is **spool-aware**:
  - **If Spoolman is available AND the active spool exposes filament temps** → Preheat fires
    `applyPreset` **directly** to the active spool's filament temps (no chooser). Source the temps
    from `SpoolmanFilament.settingsExtruderTemp` / `settingsBedTemp` (already in our model;
    `PrintStatusScreen` already resolves `activeSpool → spoolDetail: SpoolmanSpool`).
  - **Otherwise** (no Spoolman, or those temps are null) → Preheat **opens the preset selection
    page** — reuse the Phase-5 `PresetSelector` (fixed PLA/PETG/ABS/TPU), keyboard-free.
  - Guard each temp independently — fire whichever of nozzle/bed the spool actually provides; if the
    spool exists but BOTH temps are null, fall through to the selector (don't fire a half/zero preset).

### Forward entry points / capability-gated stubs (ROADMAP SC1)
- **D-02:** Forward stubs for not-yet-built features live in the **App Drawer only**, using the
  **existing greyed-tile pattern** (`DrawerTileSpec.dest = null` → rendered disabled, like Devices/
  Power were; cf. Webcam's beta/runtime gating). Add greyed **"Output"** (P18) and **"System Info"**
  (P19) tiles **now**. The standby launcher grid stays the curated high-use list from the staging
  note — it does NOT carry forward stubs.
- **D-03:** The active-print row's **Tune** tile remains the P17 Fine-Tune stub the staging note
  already calls for. WebRTC (P20) is the existing Webcam tile (already runtime-gated) — no new stub.
- **D-04:** Standby gutter **Power** is design/layout-only & nonfunctional in P16 (per staging note);
  render it consistent with the drawer's red Power tile (stop-intent outline, inert). Future: opens a
  host/system power dialog.

### Terminal — surfacing behavior
- **D-05:** Terminal is **passive** — it is a mode of the home screen and does NOT auto-yank the user
  from another screen when a print finishes. It's seen next time they navigate home. Honors the
  existing shell rule "shell does NOT reset the user to Home" (G-A1, 13-05). Terminal stays visible
  until dismissed, reprint starts, or Moonraker reports another state.

### Babystep — app setting placement
- **D-06:** The app-level babystep setting (enable toggle + positive-integer layer-count, default
  **enabled / first 5 layers**) lives under the **"Settings" tile** of the Phase-15.2 four-tile IA
  (Printers · Theme · Settings · About) as a feature-toggle-class item. It is an **overall app
  preference**, not per-printer for P16. Layer-count uses the **standard numeric keyboard** — allowed
  because it's in Settings (the no-alphanumeric-keyboard LAW applies to printer *controls*, not the
  keyboard-allowed Settings screen). See [[numeric-keyboard-for-numeric-fields]].

### Claude's Discretion (delegated to planner/researcher)
- **Standby glance metric "MCU/host temp OR host load, whichever is available/useful"** — the staging
  note deliberately hedges. Pick the rule in research/planning (prefer a real temp when a usable
  MCU/host sensor exists; fall back to host load otherwise). Keep the glance list minimal/glanceable.
- **Rework strategy** — whether to refactor the existing 942-line `PrintStatusScreen` in place or
  restructure around the `PrintStatusMode` classifier is a planning/implementation call. The classifier
  extraction itself is locked; the mechanics are not a user decision.
- **Babystep step-cycle & icon mechanics** are locked by the staging note (cycle `.02/.05/.10/.15/.20`,
  Compress=closer / Expand=farther, no text labels) — but the `SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1` ↔
  `gcode_move.homing_origin[2]` wiring details carry from 17-CONTEXT; verify against live first layer.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The primary spec for this phase
- `/mnt/e/claude/personal/github/parallel_dinghy/phase-16-home-status-redesign-staging.md` — **THE
  primary design spec.** Locks the four-state `PrintStatusMode` model + classification rules, every
  state's Focus/Field/Gutter layout (Standby launcher grid + glance metrics; Printing cockpit + stats
  rows + shortcut-combination matrix; Paused; Terminal hero + stats-frame reuse + error lines), the
  interactive-grid **flexible-tile rule** (Standby→Drawer, active→Tune grow; babystep row exempt),
  babystep mechanics, and gutter action semantics. Read this FIRST; this CONTEXT only fills its gaps.
  ⚠ This file lives OUTSIDE the repo checkout (staging note) — copy the relevant content into the
  repo docs as part of the Documentation Merge Direction below.

### UI design LAW (governs every screen)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables.
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar; orientation rules; **must gain** the
  interactive-grid flexible-tile rule as hard law (scope: grids of user-interaction surfaces ONLY —
  not stat grids, lists, graphs, info frames). Also holds criteria C1–C7 from Phase 15.2.
- `docs/ui_design/THEMING.md` — semantic tokens; `fsSp` text scale. Avoid editing unless a color/
  shape-status rule changes (staging note: don't touch THEMING for P16).
- `docs/ui_design/README.md` — has a **stale Print Status section** to update to the four-state model.
- `docs/ui_design/images/*.png` — hi-fi mockups; Print-Status artboards updated as part of this work.

### Phase sequencing / babystep origin
- `.planning/phases/17-fine-tune-live-adjust-panel/17-CONTEXT.md` §Deferred — babystep mechanics
  carried forward to P16 (`SET_GCODE_OFFSET Z_ADJUST`, step 0.01 fine / 0.05 coarse, nozzle
  closer/away labels, live applied-offset readout, session-only).
- `.planning/ROADMAP.md` — Phase 16 goal + Success Criteria (1–5, incl. the babystep on-device proof).

### Recurring lessons (read before coding)
- `[[dinghy-font-sizes-too-small]]` — use the `fsSp(baseSp, t.fs)` scale; do NOT undersize fonts.
- `[[dinghy-compose-write-scope-cancellation]]` — babystep + settings persistence writes must route
  through the process-lifetime `AppContainer.writeScope`, never `rememberCoroutineScope()`.
- `[[dinghy-display-mock-vs-reality]]` — green host suites repeatedly missed real device bugs;
  on-device flox + live-printer UAT is mandatory (esp. babystep on a real first layer, SC-5).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ui/printstatus/PrintStatusScreen.kt` (942 lines) — current home/root. Has `PrintStatusFocus`,
  `StatGrid`, `LastJobCard`, `IconValueCell`/`IconTwoRowCell`, `PrintStatusControlTile`, `StopButton`
  (tap=confirm / hold=immediate — matches the EStop semantics in the staging note), `DisabledTile`,
  and a `statusLabel(PrintState)` for all 6 states. Today it only has a coarse `printing` flag — NO
  rich Standby/Terminal modes. This is what gets reworked around `PrintStatusMode`.
- `ui/printstatus/PrintStatusControlModel.kt` — already has `activeControls`/`terminalControls`/
  `standbyControls` derivation + `PrintStatusPendingAction`. The classifier formalizes/extends this.
- `ui/printstatus/PrintStatusHolder.kt` — `PrintStatusGrid` / `HeaterCell` / `ProgressCell` /
  `InfoCell` cell model + bounded ring; toolkit-agnostic StateFlow holder. Reuse for stats frames.
- `ui/temperature/TemperatureScreen.kt` — `PresetSelector` (fixed PLA/PETG/ABS/TPU) + `applyPreset`
  (`ApplyPresetArgs(nozzle, bed, key)`) + `MATERIAL_PRESETS` + Cooldown. **Reuse for D-01 Preheat.**
- `spool/SpoolmanModels.kt` — `SpoolmanFilament.settingsExtruderTemp` / `settingsBedTemp` (nullable,
  never fabricated) — the data source for spool-aware Preheat (D-01). `PrintStatusScreen` already
  fetches `container.activeSpool` → `spoolDetail: SpoolmanSpool`.
- `ui/shell/AppDrawer.kt` — `DrawerTileSpec` + `DrawerTile`: established greyed (`dest=null`),
  `danger` (red Power), and `beta`/runtime-gated (Webcam/Spool) patterns. **Add Output + System Info
  greyed tiles here (D-02).** Tile set mirrors `docs/ui_design/images/02-app-drawer.png`.
- `theme/StatusSlot.kt` + the Phase-15.1 shape-coded status layer (octagon/triangle glyphs) — for any
  status indication; remember status = shape, not just color.

### Established Patterns
- `ui/shell/AppShell.kt` + `ShellNavState.kt` — single `var dest` rendered by `when(dest)`;
  `Dest.PrintStatus` is the home/root (navigating home CLEARS the back stack; Back at root no-ops).
  Drawer is swipe-up full-screen (`AppDrawer`). The standby launcher grid is a tap-alternative that
  COEXISTS with the swipe-up drawer (staging note) — Drawer is the standby flexible/growing tile.
- Classic-Views-in-Compose for high-churn surfaces (GraphView, status) per ADR-0001 — do NOT regress
  these; the Adreno-320 perf floor (SC-3) is measured against the current home.

### Integration Points
- `PrintStatusMode` classifier consumes the live `PrinterStateStore` / `PrinterState.printState`
  (Moonraker-derived). Composables route off the mode.
- Babystep reads layer data from `PrinterState` (hidden if layer count unavailable — no time-based
  fallback) and the app-setting toggle/threshold from DataStore (via `writeScope` intent helpers).
- Preheat (D-01), Pause/Resume/Cancel, EStop, Dismiss (`SDCARD_RESET_FILE`), Reprint (existing
  print-start command on Moonraker's current/last file path) all dispatch through the shared command
  primitive / `CommandRegistry`.

</code_context>

<specifics>
## Specific Ideas

- **Spool-aware Preheat (D-01)** is the one notable embellishment beyond the staging note — Matthew
  wants Preheat to "just work" off the loaded spool's recommended temps when Spoolman knows them, and
  only fall back to manual material selection when it doesn't.
- The staging note's **Documentation Merge Direction** is part of this phase: fold the four-state
  model into `docs/ui_design/README.md` (replace the stale Print Status section), promote the
  interactive-grid flexible-tile rule into `docs/ui_design/LAYOUT.md` as hard law (scoped to
  interactive grids only), and update the Print-Status artboards. Docs need not become strict
  declarative law, but must explain the state layouts + merge direction. Avoid `THEMING.md`.

</specifics>

<deferred>
## Deferred Ideas

- **Per-printer Standby Focus image** (user-defined icon/image from printer settings) — staging note
  marks it future; P16 uses the app icon. Avoid implementation choices that prevent it later.
- **User-customizable / reorderable standby launcher grid** — future release; P16 ships the fixed
  curated order but must not bake in choices preventing reorder/selection later.
- **Power → full host/system power dialog** — P16 Power is inert/design-only (D-04); the real dialog
  is future (ties to the eventual host-power command, a destructive/elevation path).
- **Mid-print object exclusion (`EXCLUDE_OBJECT`)** — v2 wishlist (needs object picker + confirm
  guard; from 17-CONTEXT).
- **Saving Z-offset to config** (`Z_OFFSET_APPLY_*` + `SAVE_CONFIG`) — stays a Calibration action,
  never mid-print; P16 babystep is session-only.

None of the above is in P16 scope — listed so they're not lost.

</deferred>

---

*Phase: 16-home-print-status-redesign*
*Context gathered: 2026-06-06*
