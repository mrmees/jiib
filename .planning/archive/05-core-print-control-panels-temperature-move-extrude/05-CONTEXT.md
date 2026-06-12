# Phase 5: Core Print-Control Panels — Temperature, Move, Extrude - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

<domain>
## Phase Boundary

The first real interactive **printer-control panels** — manual control of Temperature, Move/jog,
and Extrude. Delivers 12 requirements: TEMP-01..04, MOVE-01..04, EXTR-01..04. These deliberately
precede the print-loop panels (Files/Job Status) because they are simpler surfaces that prove, in
anger on real hardware, the **capability-gating model**, the **confirm-action policy**, the **shared
command-dispatch primitive (PRIM-05)**, and the **first sustained high-rate render** — the
Temperature graph EXTENDS the Phase-3/4 line-graph render primitive into a full history series.

**In scope:** the Temperature panel (live current/target per heater+sensor, target set via presets +
the single-setting scrubber, preheat/cooldown, history graph backfilled from `server.temperature_store`);
the Move panel (3×3 XY jog pad, Z, distance presets, home all/per-axis, disable steppers behind a
confirm, live position, un-homed gating); the Extrude panel (extrude/retract distance+speed, load/unload
macros, multi-tool select, cold-extrude guard).

**Out of scope (own phases / v2):** Files/Print (Phase 6), Job Status (Phase 7), Macros/Console (Phase 8),
fan speed control surface, fine-tune, camera, user-editable presets. New capabilities are NOT added here.
</domain>

<decisions>
## Implementation Decisions

### Temperature target entry & presets (TEMP-01..03)
- **D-01:** Exact heater-target entry uses the **Phase-3 `ScrubberPage`** (tap-to-set jumps near, ± steppers
  nail the exact value) — the single-setting page pattern (design README #7). There is **NO 0-9 numeric
  keypad**: the design system has no such component (inventory = `.fillbar` scrubber + `.step` increment),
  and the UI LAW forbids any keyboard in printer controls. "exact keypad entry" in TEMP-02 maps to the
  **numpad-style scrubber+steppers**, NOT a telephone pad. User explicitly chose to follow the locked design
  system over adding a keypad deviation. Tapping a live temperature value opens its single-setting page.
- **D-02:** Preheat presets (TEMP-03) = a **built-in, FIXED material set** — PLA / PETG / ABS / TPU as
  nozzle+bed pairs — plus **Cooldown** (all heaters off). Hardcoded for v1, **no in-app editor** (editing is
  deferred). Per mockup #9 the **Presets** and **Cooldown** actions live in the temperature-graph gutter.
  Sensible default temps are Claude's discretion (researcher confirms reasonable values, e.g. PLA 200/60,
  PETG 240/80, ABS 245/100, TPU 220/50 — verify against common Ender-5-Plus practice).

### Move / jog (MOVE-01..04) — LOCKED BY MOCKUP, not re-litigated
- **D-03:** The Move panel is **LAW** per `docs/ui_design/images/04-move.png` + README #4. Capture verbatim:
  **Focus** = 3×3 XY jog pad (edge arrows; **center = XY home**; corner cells show live **X / Y / Z** as
  value-on-glyph, label colored **green=homed / amber=unhomed**; one corner is an **amber Override** to jog
  while unhomed). **Field** = a **Z row** (∧ / Z / ∨, glyphs only at 3 cols) + a **6-up distance selector**
  (0.1–100 mm, **square** buttons). **Gutter** = **Home** (accent) · **Disable** (amber) · **Back** (red).
  - Disable-steppers (MOVE-03) routes through the **ConfirmGuard** (amber proceed-at-peril).
  - Live toolhead position (MOVE-04) is the value-on-glyph in the jog-pad corners (no separate readout cell).
  - Un-homed axes are visibly gated: amber axis labels + the Override cell is the only way to move unhomed.

### Temperature history graph (TEMP-04) — LOCKED BY MOCKUP
- **D-04:** The Temperature graph is **LAW** per `docs/ui_design/images/09-temperature-graph.png` + README #9.
  **Focus** = live current/setpoint value(s) (single-sensor = value-on-glyph; multi-sensor = small legend
  list). **Field** = time-series line graph: a **single trace with a dashed setpoint line**, OR **multiple
  sensors overlaid on ONE axis** with a legend (**nozzle = heat/amber, bed = accent/blue, chamber = violet**).
  **Gutter** = **Back** (red) · **Presets** (neutral) · **Cooldown** (amber).
- **D-05:** The graph **EXTENDS the existing `GraphView`/`GraphViewHost` primitive — do NOT fork it.** This is
  where the **Phase-4 sparkline gap G-1 is fixed properly**: (a) **backfill** the series from
  **`server.temperature_store`** on connect so the graph is full immediately (steady or not, survives app
  restart), and (b) use a **sensible/stable Y-range** instead of the noise-amplifying window-min/max
  auto-range. Multi-trace = several series overlaid on one shared axis (extend the single-`Path` GraphView to
  N pre-allocated paths/paints, one per sensor; keep the allocation-free onDraw discipline).
- **D-06:** **MANDATE (carried from Phase 3):** the multi-trace Temperature graph **MUST re-measure** the
  combined surface against the Phase-3 **two-part Adreno-320 perf gate** (liveness: allocation-free / no anim
  loop / 0 frozen frames + sparse-redraw **p95 ≤ ~66 ms**) on the real `flox` device. Do NOT grandfather the
  Phase-3 isolated 50.1 ms number (see `03-PERF-RESULTS.md`). More traces = more fill/stroke = the risk.

### Extrude controls & safety (EXTR-01..04)
- **D-07:** **Cold-extrude guard = disable + explain.** Gate Extrude/Retract on the **live `can_extrude`
  boolean** (EXTR-04 says respect `can_extrude`). While `can_extrude == false`, the controls are visibly
  **disabled** (reduced opacity, the standard) with a short inline reason ("Heat nozzle to extrude", ideally
  with the real `min_extrude_temp` number). No failed taps.
- **D-08:** **Extrude panel = Move-style** (no mockup exists → follow the Move template within Focus/Field/
  Gutter): **Extrude / Retract** as the primary actions + a **6-up distance selector** + a **speed selector**.
  Keyboard-free (distance/speed via selector buttons or scrubber pages; speed defaults Claude's discretion).
- **D-09:** **Multi-extruder tool selection (EXTR-03)** = a **capability-gated tool selector** (T0 / T1 …)
  shown ONLY when the printer has >1 extruder; hidden entirely on single-extruder printers.
- **D-10:** **Load/Unload filament (EXTR-02)** = **always show** Load/Unload buttons; if the
  `LOAD_FILAMENT` / `UNLOAD_FILAMENT` gcode_macro is not configured, tapping shows an **informational popup**
  ("No LOAD_FILAMENT macro configured"). Uses the `SeverityToast` / a small popup — teaches the user the
  feature exists and why it's unavailable, per EXTR-02's "missing macro popup" wording.

### Panel structure & cross-cutting (carried from Phases 3–4, not re-decided)
- Three panels (Temperature, Move, Extrude) are **App Drawer destinations** (extend the `Dest` enum +
  `AppDrawer` tiles + `RootController` routing).
- **Every** move/heat/extrude/home/disable action call is wrapped by the **`CommandDispatcher`** (PRIM-05:
  explicit timeout, in-flight/disabled state, tap debounce). No raw `rpc.request` from a panel.
- **Capability-gating** (the Phase-4 holder pattern): hide controls the connected printer doesn't support
  rather than disabling-and-confusing.
- All layout via **`ScreenScaffold`** (Focus/Field/Gutter); all color via **`LocalTokens`** (zero raw Color);
  button intent = color (accent=physical command, amber=proceed-at-peril, red=stop/back, green=accept).

### Claude's Discretion
- Exact preset temperatures (PLA/PETG/ABS/TPU nozzle+bed pairs) and the Cooldown behavior.
- Extrude distance set + default speeds (mm/min); jog distance set is locked (0.1–100 mm by mockup).
- Graph time-window length (driven by `server.temperature_store` default, typically ~20 min @ 1 Hz).
- Whether the toolhead-position source for MOVE-04 is `toolhead.position` vs `gcode_move.gcode_position`
  (researcher picks the correct field; gcode_position is usually the user-facing one).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### UI design system (LAW — supersedes any generated UI-SPEC)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables.
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar; sacred aspect ratios; ratio-only sizing.
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent colors, sensor colors (heat/accent/violet), `--fs`.
- `docs/ui_design/README.md` §4 (Move), §7 (single-setting pages), §9 (Temperatures — graph) — the locked
  per-screen specs for THIS phase.
- `docs/ui_design/images/04-move.png` — Move panel (LAW for MOVE-01..04).
- `docs/ui_design/images/07-single-setting.png` — single-setting page (LAW for the heater-temp scrubber, TEMP-02).
- `docs/ui_design/images/09-temperature-graph.png` — Temperature graph (LAW for TEMP-01/04).
- `docs/ui_design/reference/hifi.css` — canonical token + component values (`.jogpad`/`.jbtn`, `.fillbar`,
  `.step`, `.graph`/`.plot` + `LineGraph`, `.setval`) — reproduce values, do not copy verbatim.

### Architecture / perf / prior-phase carryover
- `docs/adr/0001-ui-toolkit-decision.md` — HYBRID toolkit; the temp graph is a **classic-Views `GraphView`**
  (Views won the high-churn render), the rest Compose.
- `.planning/phases/03-design-system-theming-foundation/03-PERF-RESULTS.md` — the **two-part Adreno-320 perf
  gate** the multi-trace graph MUST re-measure (D-06); forbids grandfathering the isolated 50.1 ms result.
- `.planning/phases/04-service-shell-settings-print-status-home/04-HUMAN-UAT.md` — gap **G-1** (sparkline
  auto-range / no-history / lone-dot) that this phase's TEMP-04 graph closes via `temperature_store` backfill.
- `.planning/REQUIREMENTS.md` — TEMP-01..04, MOVE-01..04, EXTR-01..04 (traceability).

### Research-verify flags (for gsd-phase-researcher)
- Confirm Moonraker exposes **`can_extrude`** live on the `extruder` object (gate for EXTR-04) and the
  **`min_extrude_temp`** value via **`configfile.settings.extruder.min_extrude_temp`** (for the hint text).
- **`server.temperature_store`** backfill mechanics: endpoint shape, per-sensor `temperatures`/`targets`
  arrays, history length/cadence — for the TEMP-04 graph backfill (D-05).
- Correct live fields for toolhead **X/Y/Z position** and **per-axis homed** state (`toolhead.homed_axes`,
  `gcode_move.gcode_position` vs `toolhead.position`).
- Detecting `LOAD_FILAMENT`/`UNLOAD_FILAMENT` macro presence (from `printer.objects.list` / gcode_macro names).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets (consume, never fork — Phase-3/4 fixes propagate for free)
- `designsystem/ScrubberPage.kt` — single-setting numeric entry, **now with tap-to-set (G-3 fix)**. Reuse for
  heater target (TEMP-02), extrude distance/speed.
- `designsystem/ConfirmGuard.kt` — full-screen confirm, **now opaque scrim (G-4 fix)**. Use for Disable
  steppers (MOVE-03).
- `render/GraphView.kt` + `render/GraphViewHost.kt` — single-trace Views Canvas graph. **EXTEND to N traces**
  for TEMP-04 (D-05); keep the one-Path-per-series, pre-allocated-Paint, allocation-free `onDraw` discipline.
- `command/CommandDispatcher.kt` (PRIM-05) — wrap every action (timeout/in-flight/debounce); redacted failures.
- `designsystem/SeverityToast.kt` — missing-macro popup (D-10), dispatch failures.
- `designsystem/ScreenScaffold.kt` + `OutlinedControl` — Focus/Field/Gutter + intent-colored controls.
- `ui/printstatus/PrintStatusHolder.kt` — the toolkit-agnostic **holder pattern** (consumes the throttled
  `PrinterStateStore.printerState`, owns derived StateFlows, explicit capability fallback). **Mirror this**
  for each new panel (a TemperatureHolder / MoveHolder / ExtrudeHolder).
- `state/PrinterState.kt` + `state/Capabilities` (`deriveCapabilities`) — live heaters map, klippy state.

### Established Patterns
- **Klippy/capability gating**: `deriveCapabilities` from `printer.objects.list`; panels hide unsupported
  controls. Likely **needs extending** to surface: extruder count, `can_extrude`, per-axis homed, toolhead
  position, load/unload macro presence, `min_extrude_temp` (from configfile).
- **Throttled render seam**: `PrinterStateStore` already conflates the high-rate plane to ~250 ms; holders
  consume it with NO second throttle (the Phase-4 lesson). The graph adds `temperature_store` backfill on top.
- **App Drawer routing**: `ui/shell/RootController.kt` + `AppShell.kt` + `AppDrawer.kt` `Dest` enum — add the
  three new destinations + tiles (currently greyed "coming soon").

### Integration Points
- Extend the `Dest` enum + `AppDrawer` tiles + `RootController` to route Temperature / Move / Extrude.
- Extend `PrinterState` / `deriveCapabilities` for the new live fields (position, homed, can_extrude, tools,
  macro presence) — pure reducer additions, unit-tested (the Phase-2 discipline).
- New Moonraker call: `server.temperature_store` (REST or RPC) for graph backfill — first use of a history endpoint.
</code_context>

<specifics>
## Specific Ideas

- **"exact keypad entry" reconciled to the scrubber.** The user explicitly chose to honor the locked design
  system (no 0-9 keypad) over a literal keypad reading of TEMP-02. If a keypad is ever wanted it must first be
  added to the design language as a sanctioned component — out of scope here.
- The user asked, mid-discussion, whether Moonraker actually exposes `min_extrude_temp`. Answer captured in the
  research flags: gate on the live **`can_extrude`** boolean; the numeric threshold comes from
  **`configfile.settings.extruder.min_extrude_temp`**. Both flagged for the researcher to confirm field paths.
- Move and Temperature-graph layouts were NOT open-discussed — they are pixel-locked by the hi-fi mockups and
  captured verbatim (D-03, D-04). Only Extrude (no mockup) and the app-own presets were genuinely decided here.
</specifics>

<deferred>
## Deferred Ideas

- **User-editable preset temperatures** (editor UI + DataStore persistence, add/remove materials) — a future
  phase; v1 ships a fixed built-in set (D-02).
- **Fan speed single-setting page** (design README #7 lists fan as a single-setting example) — fans are not in
  Phase 5's 12 requirements; defer to a later control-surface phase unless it falls out trivially free.
- **A dedicated 0-9 numeric keypad component** — only if the design language is later extended to sanction it.

---

*Phase: 5-Core Print-Control Panels — Temperature, Move, Extrude*
*Context gathered: 2026-05-31*
