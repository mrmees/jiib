# Phase 9: Calibration & Maintenance - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning

<domain>
## Phase Boundary

Touch pages for the high-use, semi-regular Klipper calibration/leveling routines — each
**capability-gated off the Phase-6 live matrix**, running the gcode and parsing/displaying its
result without a browser. All pages hang off a **single "Calibration" hub** (see D-13).

**In scope (the routines):**
- **`SCREWS_TILT_CALCULATE`** — manual bed leveling; guided per-screw adjustment loop. Hi-fi mockup
  `docs/ui_design/images/05-screws-tilt.png` is **locked UI law**. Must support **3-screw AND 4-screw**
  beds (and N>4) generically.
- **`Z_TILT_ADJUST`** — automatic multi-Z lead-screw leveling. The Ender 5 Plus has dual `stepper_z`
  → this is the **on-device-verifiable** automatic routine.
- **`QUAD_GANTRY_LEVEL`** — built **blind, capability-gated off** (neither test printer has a gantry;
  Z_TILT is the on-device proxy — D-02). Same automatic run-and-show-convergence flow as Z_TILT.
- **`BED_MESH_CALIBRATE`** — probes a grid → 2D height mesh; overhead heatmap + full profile
  management. KAMP-equipped E5+ → verifiable.
- **`PROBE_CALIBRATE` (interactive manual-probe Z-calibrate)** — the ZCAL-01 workflow, **IN scope**
  (D-01): Start → fine Z-jog (`TESTZ`) → `ACCEPT`/`ABORT` → `SAVE_CONFIG`. The probe-less sibling
  `Z_ENDSTOP_CALIBRATE` is the same manual-probe helper, gated by probe-present (research to confirm).

**Folded in:** the Phase-7 **Files-delete-gating** defect fix + its UAT check (D-14).

**Out of scope (this phase):**
- Input shaper calibration (SHAPER-01) — its own later effort.
- Any calibration *config-section editing* (probe offsets, mesh params) — the app RUNS routines and
  manages mesh profiles; it does not edit `[section]` config beyond what `SAVE_CONFIG` persists.
- Shipping static per-printer capability data — runtime gating stays live (Phase-6 D-07).
</domain>

<decisions>
## Implementation Decisions

### Routine scope
- **D-01: Full interactive `PROBE_CALIBRATE` IS in scope (ZCAL-01).** A manual-probe Z-calibrate page:
  Start (`PROBE_CALIBRATE`) → fine Z-jog via `TESTZ Z=±step` with step presets → `ACCEPT` → then
  `SAVE_CONFIG`, plus `ABORT` to close the manual-probe session. This is the one routine with a real
  interactive prompt/session (unlike screws-tilt). No mockup — designed fresh in the Focus/Field/Gutter
  grammar; behaves like a specialized fine Z-move page. Research to pin the `manual_probe` object state
  + `// Z position:` parse, and whether to also surface `Z_ENDSTOP_CALIBRATE` for probe-less printers
  (gate by probe-present predicate).
- **D-02: `QUAD_GANTRY_LEVEL` built blind, gated off.** Implement as the same automatic
  run-and-show-convergence flow as `Z_TILT_ADJUST`, gated on the `quad_gantry_level` object so it only
  appears for users who have it. **Unverifiable on Matthew's hardware → `Z_TILT_ADJUST` is the on-device
  verification proxy** (same code path, real dual-Z hardware).

### Screws-tilt (locked mockup behavior)
- **D-03: Guided one-screw-at-a-time loop** (per the locked mockup, NOT a flat table): after a probe,
  show the single **worst** out-of-tolerance screw with its exact clock-face turn (e.g. `CW 00:25`),
  user turns it by hand, re-probe, advance to the next worst until all "in tol." "X of N in tolerance"
  is the Focus headline.
- **D-04: Generic 3/4/N-screw support.** Screw count + coordinates + names come from the
  `[screws_tilt_adjust]` config (`screwN: X,Y`, `screwN_name`). Do **not** hardcode 4 screws.
- **D-05: `SCREWS_TILT_CALCULATE` is one-shot, stateless, no interactive prompt** (confirmed against
  Klipper `Manual_Level.html`). Gutter `Initiate` and `Adjust` BOTH just (re-)run the same command —
  `Initiate` = first probe, `Adjust` = re-probe after turning to show the new measurement. There is
  **nothing to Accept/Abort and no prompt to close** → the mockup's Accept/Cancel **collapse to a single
  `Back`** (navigation only; screws-tilt changes no config).
- **D-06: DESIRED enhancement — draw the bed to scale + place screw indicators at real coordinates**
  (highlight the active screw on an overhead bed outline, using `[screws_tilt_adjust]` coords + bed
  extents from config). **Graceful fallback:** if coordinates are unavailable, fall back to a labeled
  list / abstract corner layout. Planner sizes the cost; the loop (D-03) is the must-have, the
  to-scale drawing is the slick-if-affordable layer.

### Bed mesh
- **D-07: Info-rich-but-simple overhead 2D heatmap.** Focus shows the **current** mesh by default
  (empty-state indicator if none loaded). Classic **red=high / blue=low** heatmap, overhead (2D) view.
  Source the mesh from the **live `bed_mesh` printer object** (`mesh_matrix`/`probed_matrix`,
  `mesh_min`/`mesh_max`, `profile_name`, `profiles`) — not console parsing.
- **D-08: Faint, density-scaled probe-point dots.** Grids range **3×3 → 50×50** (Klipper min is 3 per
  axis, not 2). Dots stay subtle / shrink with density so a 50×50 doesn't turn into noise.
- **D-09: User-adjustable color scale.** The heatmap is mostly static, but the user can re-scale the
  red/blue clamp (min/max) — reuse `ScrubberPage` — to pinpoint high/low spots. Pure view-layer, no
  re-probe.
- **D-10: Full profile management.** Calibrate + save + load/remove + persist. **Save auto-generates a
  timestamp profile name `YY.MM.DD_HH.MM`** (`BED_MESH_PROFILE SAVE=` requires a name — there is no
  nameless save; this also sidesteps the no-keyboard-in-controls law, no typing needed). Load/remove
  pick from the existing `bed_mesh.profiles` list. Note: Klipper no longer auto-loads "default" at
  startup. Persisting a profile uses `SAVE_CONFIG` → see D-12.

### Run lifecycle (cross-cutting)
- **D-11: In-progress = live response feed.** Stream the routine's `notify_gcode_response` lines
  (probe-at-X,Y / Z=… / `Retries: 1/5`) into a compact feed, **reusing the Phase-8 console parse**
  (tap the raw stream **independent of** the console display filter — Phase-8 D-04). Works for every
  routine even when point counts are unknown. **No abort for automatic routines** (Z_TILT/QGL/BED_MESH):
  Klipper has no clean cancel — disable Back/re-run while running and rely on the global
  **Stop→ConfirmGuard→`emergency_stop`** as the only hard interrupt. (The manual-probe Z-calibrate page
  is the exception — it DOES get a real `ABORT`, D-01.)
- **D-12: SAVE_CONFIG reuses the Phase-5 G2 firmware-restart re-handshake.** `SAVE_CONFIG` (bed-mesh
  persist + Z-calibrate accept) writes `printer.cfg` and restarts klippy. Warn first via `ConfirmGuard`
  ("saves config + restarts the printer"), send it, expect klippy `shutdown→ready`, and re-run the
  existing reconnect/resubscribe handshake (the proven G2 path) so the spine recovers cleanly.
- **D-13: App pre-flights before running.** Gate the run/Initiate button on **homed state** (offer
  "Home" if not homed — prevents the #1 "Must home axis first" failure), and for BED_MESH offer an
  **optional bed pre-heat** (accuracy). Reuse live homed/temperature state already in `PrinterState`.

### Navigation
- **D-14: Single "Calibration" hub tile.** One App-Drawer tile opens a Calibration hub that lists ONLY
  the routines THIS printer supports (gated off the live matrix). Keeps the drawer uncluttered and
  scales as later phases add features. Empty/unsupported states live in the hub.

### Claude's Discretion
- Exact `manual_probe`/`PROBE_CALIBRATE` state parse + whether `Z_ENDSTOP_CALIBRATE` surfaces for
  probe-less printers (D-01) — research to confirm; planner to design the page.
- Heatmap cell-interpolation, dot sizing curve, and color-scale control granularity (D-07/08/09).
- TESTZ step-preset values for the Z-calibrate jog (D-01) — pick sensible fine steps (e.g. 1 / 0.1 /
  0.05 / 0.025 mm) consistent with the Move panel's scrubber grammar.
- Precise Focus/Field/Gutter composition of each non-mockup page (bed-mesh, Z-tilt/QGL, Z-calibrate,
  the hub) — subject to the design law; firm in `/gsd-ui-phase` if run. Screws-tilt is locked by mockup.
- Cost/feasibility call on the to-scale bed drawing (D-06) vs the list fallback.

### Folded Todos
- **`files-delete-gating-too-broad`** (D-15) — fix `FilesScreen.deleteEnabled`: gate on
  `selected.path == active print_stats.filename` instead of the GLOBAL `printingActive`, so during a
  print only the **currently-printing** file is undeletable; every other idle file stays deletable.
  Verify the filename path-form against `docs/moonraker-capabilities.md` (relative vs leading `gcodes/`).
  **Record the UI-SPEC rule change** (relaxes the original "Delete is idle-only" rule) in
  `docs/ui_design/`. Add the delete-during-print-scoping check to `09-UAT.md`.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### UI design law (screws-tilt is LOCKED)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables (Focus/Field/Gutter, intent-by-safety
  colors, no-keyboard-in-controls, content-image Fit, scroll-Field⇒no-swipe-drawer).
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar + ⚠ non-negotiables (shared grid, sacred
  aspect ratios, ratio-only sizing).
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent-by-safety doctrine, `--fs`.
- `docs/ui_design/images/05-screws-tilt.png` — **LOCKED** screws-tilt layout (portrait + landscape).
  Note: behavior reconciled by D-03/D-05 (gutter collapses to Back; loop, not table).

### Klipper / Moonraker authoritative docs (catalog source)
- `https://www.klipper3d.org/Manual_Level.html` — **READ THIS.** Covers `SCREWS_TILT_CALCULATE`
  (`[screws_tilt_adjust]` config: per-screw coords + names; one-shot, no prompt; CW/CCW clock-minute
  output), `Z_TILT_ADJUST`, `QUAD_GANTRY_LEVEL`, and the manual-probe helper (`PROBE_CALIBRATE`,
  `TESTZ`, `ACCEPT`, `ABORT`, `Z_ENDSTOP_CALIBRATE`).
- `https://www.klipper3d.org/Bed_Mesh.html` — `BED_MESH_CALIBRATE`, `BED_MESH_PROFILE
  SAVE=/LOAD=/REMOVE=` (name REQUIRED; "default" no longer auto-loaded), `SAVE_CONFIG` persistence,
  min probe_count 3/axis.
- `https://www.klipper3d.org/Status_Reference.html` — live object fields for the gating predicates +
  result display: `bed_mesh`, `manual_probe`, `z_tilt`, `quad_gantry_level`, `screws_tilt_adjust`,
  `probe`, `toolhead.homed_axes`.
- `https://www.klipper3d.org/G-Codes.html` — exact gcode command/param surface.

### In-repo command/state substrate (Phase 6)
- `docs/commands/klipper-gcode.md`, `docs/commands/moonraker-api.md` — the Phase-6 catalog; register
  new calibration commands with stable catalog IDs + gating predicates here.
- `docs/moonraker-capabilities.md` — LIVE E5/E3 captures: **E5+ has dual `stepper_z` (→ `z_tilt`),
  KAMP (→ `bed_mesh`), Klicky/Klack probe; neither printer has QGL.** Verify predicates + the
  `print_stats.filename` path-form (D-15) against this.
- `.planning/phases/06-command-reference-capability-matrix/06-CONTEXT.md` — registry/matrix/gating
  decisions (D-04/06/08/09: `hasObject()` predicate, registry wraps typed builders verbatim).
- `.planning/phases/08-macros-console-functional-core-complete/08-CONTEXT.md` — the `notify_gcode_response`
  + `gcode_store` parse architecture (D-02/D-04) the live feed (D-11) reuses; raw stream stays
  independent of the console display filter.

### Architecture / requirements / roadmap
- `docs/adr/0001-ui-toolkit-decision.md` — toolkit-agnostic headless spine (StateFlow to Compose +
  Views); calibration view-models stay headless.
- `.planning/REQUIREMENTS.md` — BEDM-01 (bed mesh), BEDL-01 (screws-tilt / Z-tilt), ZCAL-01 (Z calibrate,
  now scoped to full PROBE_CALIBRATE per D-01).
- `.planning/ROADMAP.md` § "Phase 9: Calibration & Maintenance" — goal + the 4 named leveling routines.
- `.planning/todos/pending/files-delete-gating-too-broad.md` + `.planning/phases/07-files-print-control-core-print-loop-gate/07-UAT.md` — the folded defect (D-15).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `command/CommandRegistry.kt` + `CommandSpec.kt` + `CommandDispatcher.kt` + `CommandDispatchExtensions.kt`
  + `PrinterCommands.kt` — register every calibration command here (Phase-6 path); long routines reuse
  the typed-timeout/in-flight dispatch (the Phase-5 **G4** 120s-typed-timeout pattern is essential for
  multi-minute probes).
- `state/Capabilities.kt` + `state/DeriveCapabilities.kt` — `hasObject()` predicate gates each routine
  on `z_tilt` / `bed_mesh` / `quad_gantry_level` / `screws_tilt_adjust` / `probe` presence (live, every
  reconnect). Add predicates, not derive forks.
- `state/PrinterState.kt` + `PrinterStateReducer.kt` + `PrinterStateStore.kt` — extend to surface the
  `bed_mesh` object (mesh matrices/profiles), `manual_probe` state, `toolhead.homed_axes`, and feed the
  live response stream. Consume `notify_gcode_response` (already wired).
- `state/ConsoleScrollback.kt` + `render/RingBuffer.kt` + `ui/console/ConsoleHolder.kt` — the Phase-8
  gcode-response parse/severity vocabulary to reuse for the in-progress live feed (D-11), tapped
  upstream of the console display filter.
- `render/GraphView.kt` / `GraphViewHost.kt` — the proven **Views-in-Compose Canvas** discipline
  (sparse redraw, Adreno-320 budget) to model the bed-mesh heatmap Canvas on. `render/ProgressRing.kt`
  available if a determinate indicator is wanted.
- `designsystem/ConfirmGuard.kt` — the SAVE_CONFIG-restart warning (D-12) and any destructive guard.
- `designsystem/ScrubberPage.kt` — the bed-mesh color-scale control (D-09) and TESTZ step entry (D-01).
  `designsystem/NumpadPage.kt` — numeric entry if needed. `designsystem/SeverityToast.kt` — error
  surfacing. `designsystem/MaterialSymbol.kt` + `designsystem/layout` + `designsystem/control` —
  Focus/Field/Gutter scaffolding.
- `ui/shell/AppDrawer.kt` + `AppShell.kt` + `RootController.kt` — wire the single "Calibration" hub tile
  (D-14) + sub-routing to each routine page.
- Firmware-restart recovery (the Phase-5 **G2** path): `di/SessionControl.kt`, `net/MoonrakerSession.kt`,
  `net/JsonRpcClient.kt` — the reconnect/resubscribe handshake to reuse for `SAVE_CONFIG` (D-12).
- `ui/files/FilesScreen.kt` — the `deleteEnabled` predicate to fix (D-15).

### Established Patterns
- **Capability gating live via `deriveCapabilities`** (Phase 6) — re-derived each reconnect; works on
  any user's printer. Calibration pages/buttons gate off `hasObject()` predicates.
- **Toolkit-agnostic holders exposing `StateFlow`** (ADR 0001) — calibration holders follow the
  Phase-5/7/8 holder pattern; no Compose annotations in the spine.
- **Purity discipline** — result parsers (mesh matrix, screw turns, manual-probe Z) should be pure +
  host-testable off-hardware, mirroring `PrinterCommands` / `deriveCapabilities`.
- **Raw stream independent of display filter** (Phase-8 D-04) — the live feed taps the raw
  `notify_gcode_response`, not the console's filtered view.

### Integration Points
- Routine commands → `CommandRegistry`/`CommandDispatcher`; results ← `notify_gcode_response` (feed) +
  the live `bed_mesh`/`manual_probe` objects (structured display).
- `SAVE_CONFIG` → the existing firmware-restart reconnect/resubscribe handshake (G2).
- The Calibration hub tile → `AppDrawer`/`RootController` routing.
- Files-delete fix → `FilesScreen` (compare against `print_stats.filename`).
</code_context>

<specifics>
## Specific Ideas

- Screws-tilt: **draw the user's bed to scale and place screw indicators at their real coordinates**
  (from `[screws_tilt_adjust]` config), highlighting the active screw — "pretty slick" if affordable;
  list fallback otherwise (D-06).
- Bed mesh: overhead 2D, classic **red/blue** heatmap, **faint density-scaled probe dots**, mostly
  static, **user-adjustable color scale** to pinpoint high/low (D-07/08/09).
- Bed-mesh save naming: timestamp `YY.MM.DD_HH.MM` so no keyboard is needed (D-10).
- `Z_TILT_ADJUST` is the on-device proxy for the blind QGL build (D-02).
</specifics>

<deferred>
## Deferred Ideas

- **Input shaper calibration (SHAPER-01)** — accelerometer/auto + manual; its own later phase, not here.
- **Calibration config-section editing** (probe offsets, mesh params, etc.) — out of scope; the app
  runs routines and manages mesh profiles, it does not edit arbitrary `[section]` config.
- **`Z_ENDSTOP_CALIBRATE` for probe-less printers** — likely a cheap sibling of the manual-probe page
  (D-01); confirm in research, include only if it falls out naturally from the same helper.

### Reviewed Todos (not folded)
- `benchmark-harness-fairness-fixes.md` — keyword-only match (bench harness); unrelated to calibration.
- `console-macro-page-ux-flow.md` — Phase-8 console/macro UX; unrelated.
- `status-progress-ring-dual-source-jump.md` — Phase-4 Status ring defect; unrelated.
- `macrobenchmark-module-wiring.md` — benchmarking infra; unrelated.

</deferred>

---

*Phase: 9-Calibration & Maintenance*
*Context gathered: 2026-06-02*
