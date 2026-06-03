# Phase 9: Calibration & Maintenance - Research

**Researched:** 2026-06-02
**Domain:** Klipper calibration/leveling G-code routines + Moonraker live objects, parsed/displayed natively on Android (Kotlin, Compose+Views hybrid, ADR-0001 headless spine)
**Confidence:** HIGH (every load-bearing response/object shape pinned VERBATIM against Klipper master source, not docs prose; in-repo extension points read directly)

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions

- **D-01:** Full interactive `PROBE_CALIBRATE` IS in scope (ZCAL-01). Manual-probe Z-calibrate page: Start (`PROBE_CALIBRATE`) → fine Z-jog via `TESTZ Z=±step` with step presets → `ACCEPT` → then `SAVE_CONFIG`, plus `ABORT` to close the manual-probe session. The one routine with a real interactive prompt/session. No mockup. Research to pin `manual_probe` object state + `// Z position:` parse + whether to surface `Z_ENDSTOP_CALIBRATE` for probe-less printers (gate by probe-present).
- **D-02:** `QUAD_GANTRY_LEVEL` built blind, gated off the `quad_gantry_level` object. Same automatic run-and-show-convergence flow as `Z_TILT_ADJUST`. Unverifiable on Matthew's hardware → `Z_TILT_ADJUST` is the on-device verification proxy (same code path, real dual-Z).
- **D-03:** Screws-tilt guided one-screw-at-a-time loop (per locked mockup, NOT a flat table): after a probe, show the single WORST out-of-tolerance screw with its exact clock-face turn (e.g. `CW 00:25`); user turns by hand; re-probe; advance to next worst until all in tol. "X of N in tolerance" is the Focus headline.
- **D-04:** Generic 3/4/N-screw support. Screw count + coordinates + names come from `[screws_tilt_adjust]` config (`screwN: X,Y`, `screwN_name`). Do NOT hardcode 4 screws.
- **D-05:** `SCREWS_TILT_CALCULATE` is one-shot, stateless, no interactive prompt. Gutter `Initiate` and `Adjust` BOTH just (re-)run the same command. Nothing to Accept/Abort, no prompt to close → mockup's Accept/Cancel collapse to a single `Back` (navigation only; screws-tilt changes no config).
- **D-06:** DESIRED enhancement — draw the bed to scale + place screw indicators at real coordinates. Graceful fallback: labeled list / abstract corner layout if coords unavailable. Planner sizes the cost; the loop (D-03) is the must-have.
- **D-07:** Info-rich-but-simple overhead 2D heatmap. Focus shows CURRENT mesh by default (empty-state indicator if none). Classic red=high / blue=low. Source from the live `bed_mesh` object (`mesh_matrix`/`probed_matrix`, `mesh_min`/`mesh_max`, `profile_name`, `profiles`) — NOT console parsing.
- **D-08:** Faint, density-scaled probe-point dots. Grids 3×3 → 50×50 (Klipper min is 3 per axis). Dots shrink with density.
- **D-09:** User-adjustable color scale. Re-scale the red/blue clamp (min/max) via `ScrubberPage`. Pure view-layer, no re-probe.
- **D-10:** Full profile management. Calibrate + save + load/remove + persist. Save auto-generates timestamp name `YY.MM.DD_HH.MM` (`BED_MESH_PROFILE SAVE=` requires a name; no nameless save; sidesteps no-keyboard law). Load/remove pick from `bed_mesh.profiles`. Persist via `SAVE_CONFIG` (D-12).
- **D-11:** In-progress = live response feed. Stream the routine's `notify_gcode_response` lines into a compact feed, reusing the Phase-8 console parse, tapped independent of the console display filter. No abort for automatic routines (Z_TILT/QGL/BED_MESH) — Klipper has no clean cancel; disable Back/re-run while running; rely on global Stop→ConfirmGuard→`emergency_stop`. The manual-probe Z-calibrate page is the exception (real `ABORT`, D-01).
- **D-12:** SAVE_CONFIG reuses the Phase-5 G2 firmware-restart re-handshake. Warn first via `ConfirmGuard` ("saves config + restarts the printer"), send it, expect klippy `shutdown→ready`, re-run the existing reconnect/resubscribe handshake.
- **D-13:** App pre-flights before running. Gate the run/Initiate button on homed state (offer "Home" if not homed). For BED_MESH offer an optional bed pre-heat. Reuse live homed/temperature state in `PrinterState`.
- **D-14:** Single "Calibration" hub tile. One App-Drawer tile → a Calibration hub listing ONLY routines THIS printer supports (gated off the live matrix). Empty/unsupported states live in the hub.
- **D-15 (folded todo `files-delete-gating-too-broad`):** Fix `FilesScreen.deleteEnabled`: gate on `selected.path == active print_stats.filename` instead of GLOBAL `printingActive`. Verify filename path-form against `docs/moonraker-capabilities.md`. Record the UI-SPEC rule change in `docs/ui_design/`. Add the delete-during-print-scoping check to `09-UAT.md`.

### Claude's Discretion

- Exact `manual_probe`/`PROBE_CALIBRATE` state parse + whether `Z_ENDSTOP_CALIBRATE` surfaces for probe-less printers (D-01) — research to confirm; planner designs the page.
- Heatmap cell-interpolation, dot sizing curve, color-scale control granularity (D-07/08/09).
- TESTZ step-preset values for the Z-calibrate jog (D-01) — sensible fine steps (e.g. 1 / 0.1 / 0.05 / 0.025 mm) consistent with the Move scrubber grammar.
- Precise Focus/Field/Gutter composition of each non-mockup page (bed-mesh, Z-tilt/QGL, Z-calibrate, the hub) — subject to design law; firm in `/gsd-ui-phase` if run. Screws-tilt is locked by mockup.
- Cost/feasibility call on the to-scale bed drawing (D-06) vs the list fallback.

### Deferred Ideas (OUT OF SCOPE)

- Input shaper calibration (SHAPER-01) — its own later phase.
- Calibration config-section editing (probe offsets, mesh params) — app RUNS routines + manages mesh profiles; does NOT edit arbitrary `[section]` config beyond what `SAVE_CONFIG` persists.
- `Z_ENDSTOP_CALIBRATE` for probe-less printers — confirm in research; include ONLY if it falls out naturally from the same manual-probe helper.
- Shipping static per-printer capability data — runtime gating stays live (Phase-6 D-07).
</user_constraints>

<phase_requirements>
## Phase Requirements

The roadmap carries three coarse umbrella REQ-IDs (BEDM-01, BEDL-01, ZCAL-01); the finer **CALIB-\*** family is to be coined by the planner. Mapping the umbrella reqs to research support:

| ID | Description | Research Support |
|----|-------------|------------------|
| BEDM-01 | Bed mesh view/calibrate/profiles | `bed_mesh` live object fields pinned (§ Code Examples 4); `BED_MESH_CALIBRATE`/`BED_MESH_PROFILE SAVE/LOAD/REMOVE` surface + name-required + no-default-autoload pinned; heatmap is a NEW Views-Canvas surface modeled on GraphView discipline (§ Architecture Patterns); `SAVE_CONFIG` persistence via G2 re-handshake (§ Code Examples 6) |
| BEDL-01 | Bed level / screws-tilt + Z-tilt | `screws_tilt_adjust.results` structured object (per-screw `z`/`sign`/`adjust`/`is_base`) pinned VERBATIM from source — the guided-loop data source (§ Code Examples 1); `[screws_tilt_adjust]` config shape + N-screw generic support; `z_tilt.applied` + `Retries:` convergence line pinned (§ Code Examples 3) |
| ZCAL-01 | Z calibrate workflow (→ full PROBE_CALIBRATE per D-01) | `manual_probe` object (`is_active`/`z_position`/`z_position_lower`/`z_position_upper`) + the exact `"Z position: %s --> %.3f <-- %s"` and `"Starting manual Z probe..."` output pinned (§ Code Examples 5); `TESTZ`/`ACCEPT`/`ABORT` semantics; `Z_ENDSTOP_CALIBRATE` confirmed same helper |

**Planner action:** coin CALIB-* IDs (suggest one per routine + one for the hub + one for the D-15 defect), e.g. CALIB-01 hub/gating, CALIB-02 screws-tilt, CALIB-03 Z-tilt/QGL, CALIB-04 bed-mesh, CALIB-05 PROBE_CALIBRATE, CALIB-06 files-delete-scoping. Add them to `.planning/REQUIREMENTS.md` traceability.
</phase_requirements>

## Summary

Phase 9 adds dedicated touch pages for the four leveling routines plus interactive Z-calibrate, all hanging off a single capability-gated "Calibration" hub. The phase is almost entirely **plumbing onto proven seams** — the command registry (Phase 6), the `hasObject()` gating predicate (Phase 6), the `notify_gcode_response` stream + console parse (Phase 8), the G4 120s gcode timeout (Phase 5), the G2 firmware-restart re-handshake (Phase 5), and the GraphView Views-in-Compose Canvas discipline (Phases 3/5). The genuinely NEW work is: registering ~7 calibration commands, surfacing 5 new live objects through the reducer (`screws_tilt_adjust`, `z_tilt`, `quad_gantry_level`, `bed_mesh`, `manual_probe`), a NEW bed-mesh heatmap Canvas, a NEW Calibration hub route, the interactive manual-probe page (the only one with a real session), and the one-line D-15 delete-gating fix.

**The single most important finding** — and the one that de-risks the "RESEARCH: DEEPER" flag: **screws-tilt does NOT require console text parsing.** Klipper exposes a structured `screws_tilt_adjust` live object whose `results` dict gives, per screw, `{z, sign:"CW"|"CCW", adjust:"HH:MM", is_base:bool}` keyed `screw1`/`screw2`/… (1-based index), plus top-level `error:bool` and `max_deviation:float`. This is the clean source for the D-03 guided loop — far more robust than regexing `notify_gcode_response`. The same lesson holds for every routine: prefer the **structured live object** for the result display (`bed_mesh.mesh_matrix`, `screws_tilt_adjust.results`, `z_tilt.applied`), and use `notify_gcode_response` only for the live IN-PROGRESS feed (D-11). This is the architecture that kills the mock-vs-reality bug class that bit Phases 2 and 5.

**Primary recommendation:** Build every result-display off the structured live object (subscribe `screws_tilt_adjust`/`z_tilt`/`quad_gantry_level`/`bed_mesh`/`manual_probe` via the existing subscribe path); use `notify_gcode_response` only for the progress feed. Detect "done" from the object (`z_tilt.applied==true`, a populated `screws_tilt_adjust.results`, a non-empty `bed_mesh.profile_name`/`mesh_matrix`, `manual_probe.is_active==false`); detect "failed" from the `RpcError` the dispatcher already catches (G1). Re-probe **all live-object reads must be probed against the real Ender 5 Plus before parsers are finalized** (capture into test fixtures, exactly as Phase 8 did).

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| Run a calibration G-code | Spine — `CommandRegistry`/`CommandDispatcher` | — | All printer actions route through the Phase-6 registry; gcode.script gets the G4 120s timeout |
| Gate which routines appear | Spine — `Capabilities.hasObject()` | — | Phase-6 live matrix; re-derived every reconnect; no static per-printer data (D-14, Phase-6 D-07) |
| Result display (screws/mesh/tilt) | Spine — `PrinterState` reducer (new objects) → headless holder StateFlow | UI — Compose/Views render | Structured live object is the source-of-truth; ADR-0001 keeps holders toolkit-agnostic |
| In-progress live feed (D-11) | Spine — existing `notify_gcode_response` flow → console parse | UI — compact feed | Raw stream tapped upstream of the console display filter (Phase-8 D-04) |
| Bed-mesh heatmap render | UI — NEW classic-Views custom `Canvas` (`ThemeableView`) | — | High-fill 2D grid → Views per ADR-0001; modeled on GraphView discipline, NOT a GraphView extension |
| Result PARSING (pure) | Spine — pure functions mirroring `PrinterCommands`/`deriveCapabilities` | — | Host-testable off-hardware; the Nyquist quick-run target |
| SAVE_CONFIG lifecycle | Spine — existing G2 re-handshake (`SessionControl`/`MoonrakerSession`) | UI — ConfirmGuard warn | SAVE_CONFIG restarts the host → klippy shutdown→ready → proven G2 path |
| Calibration hub routing | UI — `AppDrawer`/`RootController`/`AppShell` | — | Single tile → sub-routes (D-14) |
| Files delete scoping (D-15) | UI — `FilesScreen` predicate | Spine — `print_stats.filename` already in `PrinterState.printFilename` | One-line predicate change against an already-surfaced field |

## Standard Stack

No new libraries. Every dependency this phase needs is already pinned in `gradle/libs.versions.toml` and proven on-device. This is a "compose existing seams" phase.

### Core (already present — verified in repo)
| Library | Version | Purpose | Why Standard |
|---------|---------|---------|--------------|
| Kotlin | 2.1.x | Language | Project standard (CLAUDE.md) |
| OkHttp + kotlinx.serialization | 4.12.x / 1.7.x | WS + JSON-RPC parse of new live objects | Already the spine transport/parse path |
| Coroutines/Flow | 1.9.x | Holder StateFlows for calibration view-models | ADR-0001 headless-spine pattern |
| Jetpack Compose + classic Views | BOM 2026.05 / Views | Hub + most pages (Compose); heatmap (Views Canvas) | ADR-0001 hybrid; high-churn/high-fill → Views |
| DataStore | 1.1.x | (none required) | Mesh profiles live on the PRINTER (`SAVE_CONFIG`), not in-app — no local persistence needed for calibration |

### Alternatives Considered
| Instead of | Could Use | Tradeoff |
|------------|-----------|----------|
| Structured live-object result display | Regex `notify_gcode_response` | Console parse is FRAGILE (the exact bug class that bit Phases 2/5). Use the live object; reserve console parse for the in-progress feed only (D-11). VERDICT: live object. |
| New heatmap Views-Canvas | Extend `GraphView` | GraphView is a 1D line-graph primitive (traces on a shared X/Y); a 2D mesh heatmap is a fundamentally different draw. Reuse its DISCIPLINE (allocation-free onDraw, `ThemeableView` token-push, sparse redraw via `invalidate()`), not its code. VERDICT: new sibling Canvas View. |
| New heatmap Views-Canvas | Compose `Canvas` | A 50×50 interpolated heatmap is fill-rate heavy on Adreno 320 — exactly what ADR-0001 routes to Views (~2× lower p95). VERDICT: Views Canvas. Re-measure on flox. |

**Installation:** none — no new packages.

## Package Legitimacy Audit

> Not applicable — this phase installs **zero** external packages. All stack is pre-pinned in `gradle/libs.versions.toml` (PKG-02, Phase 1) and proven on-device through Phase 8. slopcheck/registry verification is moot; the planner adds no install tasks and no `checkpoint:human-verify` for package legitimacy.

## Architecture Patterns

### System Architecture Diagram

```
                ┌──────────────────────────────────────────────────────────┐
   AppDrawer    │  Calibration Hub (D-14)                                    │
   "Calibration"│  lists ONLY routines where Capabilities.hasObject(X)==true │
   tile  ───────▶  (screws_tilt_adjust / z_tilt / quad_gantry_level /        │
                │   bed_mesh / probe→manual_probe)                           │
                └───────┬────────────┬─────────────┬───────────┬────────────┘
                        │            │             │           │
              ┌─────────▼──┐  ┌──────▼─────┐  ┌────▼──────┐  ┌─▼──────────────┐
              │ ScrewsTilt │  │ ZTilt/QGL  │  │ BedMesh   │  │ ProbeCalibrate │
              │  page      │  │  page      │  │  page     │  │  page (D-01)   │
              │ (mockup    │  │ (auto run, │  │ (heatmap, │  │ INTERACTIVE    │
              │  locked)   │  │  no abort) │  │  profiles)│  │  session       │
              └─────┬──────┘  └─────┬──────┘  └────┬──────┘  └──────┬─────────┘
                    │               │              │                │
       ┌────────────┴───────────────┴──────────────┴────────────────┴──────────┐
       │  D-13 PRE-FLIGHT: gate run on homedAxes; offer Home; BED_MESH offer     │
       │  optional pre-heat (reuse live PrinterState.homedAxes + heaters)        │
       └────────────┬───────────────┬──────────────┬────────────────┬──────────┘
                    │               │              │                │
            dispatch via CommandDispatcher (PRIM-05; gcode.script → G4 120s timeout)
                    │               │              │                │
       ┌────────────▼───────────────▼──────────────▼────────────────▼──────────┐
       │  Moonraker  printer.gcode.script  { SCREWS_TILT_CALCULATE | Z_TILT_ADJUST│
       │             | QUAD_GANTRY_LEVEL | BED_MESH_CALIBRATE | PROBE_CALIBRATE   │
       │             | TESTZ | ACCEPT | ABORT | BED_MESH_PROFILE | SAVE_CONFIG }  │
       └───────┬───────────────────────────────────────────────────┬───────────┘
               │ RESULT (structured)                                 │ PROGRESS (text)
       ┌───────▼────────────────────────────┐          ┌─────────────▼──────────────┐
       │ notify_status_update → reducer →    │          │ notify_gcode_response →     │
       │ PrinterState NEW fields:            │          │ existing gcode flow → console│
       │  screws_tilt_adjust.results{}       │          │ parse (Phase-8) → IN-PROGRESS│
       │  z_tilt.applied / qgl.applied       │          │ live feed (D-11), raw stream │
       │  bed_mesh.mesh_matrix/probed_matrix │          │ INDEPENDENT of console filter│
       │  manual_probe.is_active/z_position  │          └──────────────────────────────┘
       └───────┬─────────────────────────────┘
               │                              ┌─────────────────────────────────────┐
       ┌───────▼──────┐   ┌──────────────┐    │ FAILURE: gcode.error → RpcError →     │
       │ Holder       │   │ Heatmap      │    │ CommandDispatcher G1 catch → Failure  │
       │ StateFlow    │──▶│ Views Canvas │    │ toast (NOT a crash). Done detection   │
       │ (headless)   │   │ (red/blue)   │    │ from the live object, never bare ack. │
       └──────────────┘   └──────────────┘    └─────────────────────────────────────┘

   SAVE_CONFIG (bed-mesh persist / Z-calibrate accept) → host restart →
   klippy shutdown→ready → existing G2 re-handshake (SessionControl) recovers spine (D-12)
```

### Recommended Project Structure (additive — follows existing layout)
```
app/src/main/java/works/mees/dinghy/
├── command/        # ADD calibration CommandSpecs to CommandRegistry.kt (+ new gcode builders in PrinterCommands.kt)
├── state/          # EXTEND PrinterState.kt (+5 nullable object models), PrinterStateReducer.kt (walk new objects),
│                   #   DeriveCapabilities.kt (add new objects to the subscribe superset)
├── calibration/    # NEW: pure result parsers (ScrewsTiltResult, BedMeshModel, TiltResult, ManualProbeState),
│                   #   headless holders (ScrewsTiltHolder, BedMeshHolder, TiltHolder, ProbeCalibrateHolder)
├── render/         # NEW: BedMeshHeatmapView.kt (classic Views Canvas, ThemeableView) + its Host
├── ui/calibration/ # NEW: CalibrationHubScreen, ScrewsTiltScreen, TiltScreen, BedMeshScreen, ProbeCalibrateScreen
├── ui/shell/       # EXTEND AppDrawer.kt (Calibration tile), RootController/AppShell (Dest.Calibration + sub-routes)
└── ui/files/       # FIX FilesScreen.kt deleteEnabled predicate (D-15)
```

### Pattern 1: Subscribe-and-reduce the new live objects (NOT console parse)
**What:** Add the five calibration objects to the subscribe superset; walk them in the reducer into new `PrinterState` fields; consume them in headless holders.
**When to use:** Every result DISPLAY. (Console parse is for the in-progress feed only.)
**Example:**
```kotlin
// DeriveCapabilities.kt — add to V1_SUBSCRIBE_CORE the calibration objects, intersected with detected:
// (subscribe only when present — A3 discipline already enforced by deriveSubscribeSet)
"screws_tilt_adjust", "z_tilt", "quad_gantry_level", "bed_mesh", "manual_probe", "probe"
// Source: in-repo DeriveCapabilities.kt:50 (V1_SUBSCRIBE_CORE) — extend, don't fork.
```

### Pattern 2: Done/failed detection from object + RpcError, never a bare ack
**What:** A long routine's gcode.script reply (after G4 120s) is NOT the success signal — Klipper can reply OK and still have failed a retry, or the routine's real "done" is a state change. Detect completion from the live object; detect failure from the `gcode.error` → `RpcError` the dispatcher already surfaces (G1).
**When to use:** All automatic routines (Z_TILT/QGL/BED_MESH) and screws-tilt.
```
done(Z_TILT)        := z_tilt.applied == true
done(QGL)           := quad_gantry_level.applied == true
done(BED_MESH)      := bed_mesh.mesh_matrix non-empty AND profile_name set (mesh active after calibrate)
done(SCREWS_TILT)   := screws_tilt_adjust.results non-empty AND error == false   (all in tol)
done(PROBE_CALIB)   := manual_probe.is_active == false (after ACCEPT/ABORT)
failed(any)         := CommandDispatcher.events emits Failure (RpcError text, e.g.
                       "Too many retries" / "bed level exceeds configured limits (...)mm!")
```

### Pattern 3: The interactive manual-probe session (the ONLY stateful page)
**What:** Unlike the one-shot routines, PROBE_CALIBRATE opens a session: `is_active` flips true; `TESTZ Z=±step` nudges; each nudge emits a `Z position:` line AND updates `manual_probe.z_position`; `ACCEPT`/`ABORT` closes it; then `SAVE_CONFIG` persists. Drive the page's enabled/disabled state off `manual_probe.is_active`.
**When to use:** D-01 page only.

### Anti-Patterns to Avoid
- **Regexing `notify_gcode_response` for screw turns / mesh values.** The structured object exists; use it. Console parse only for the live progress feed.
- **Treating the gcode.script ack as "done".** Detect from the object/state (Pattern 2). This is the Phase-5 G2/G3 lesson.
- **Forking GraphView for the heatmap.** Reuse its discipline; build a sibling Canvas View.
- **Hardcoding 4 screws or a fixed bed size.** Read `[screws_tilt_adjust].screwN` and `toolhead.axis_minimum/axis_maximum` (D-04/D-06; bed extents already noted in capabilities doc).
- **An "abort" button on automatic routines.** Klipper has no clean cancel; only `emergency_stop` interrupts (D-11). The manual-probe page is the sole exception (`ABORT`).
- **Subscribing an object the printer lacks.** `deriveSubscribeSet` already intersects with detected objects — add to the superset, never request unconditionally (A3).

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Per-screw turn math (CW/CCW clock-minute) | Reproduce Klipper's `diff / threads_factor` + `trunc`/`minutes` math | Read `screws_tilt_adjust.results[*].adjust`/`.sign` | Klipper already computed it; the object hands you `"00:25"`/`"CW"` ready-formatted |
| Mesh interpolation / matrix | Recompute lagrange/bicubic mesh | Read `bed_mesh.mesh_matrix` (interpolated) + `probed_matrix` (raw points) | Klipper interpolates; you only colour-map + dot the points |
| Tilt convergence detection | Parse `Retries: N/M ...` text | Read `z_tilt.applied` / `quad_gantry_level.applied`; failure = RpcError | The boolean is the truth; the text is only for the live feed |
| Long-gcode timeout | A new timeout path | `CommandDispatcher` GCODE_TIMEOUT_MS=120s (G4) | gcode.script already gets the long timeout; multi-minute probes covered |
| Firmware-restart recovery after SAVE_CONFIG | A bespoke reconnect | The G2 `notify_klippy_ready` re-handshake | SAVE_CONFIG restarts the host → klippy shutdown→ready → G2 already re-objects/subscribes |
| Gcode-rejection crash safety | try/catch in the holder | `CommandDispatcher` G1 RpcError catch → Failure toast | Already non-fatal; "Too many retries" surfaces as a toast, not a crash |
| Macro/command availability gating | Static per-printer table | `Capabilities.hasObject()` (live) | Re-derived every reconnect; works on any user's printer (Phase-6 D-07) |

**Key insight:** Klipper already did the hard math and exposes it structured. This phase is a presentation layer over `get_status()` objects + the existing dispatch/recover/gate spine. The temptation to console-parse is the trap.

## Runtime State Inventory

> This is NOT a rename/refactor phase, but D-12/D-10 touch persistent printer state. Documenting the cross-system state explicitly.

| Category | Items Found | Action Required |
|----------|-------------|------------------|
| Stored data | Bed-mesh profiles persist in **`printer.cfg` on the PRINTER host** (via `SAVE_CONFIG`), keyed by profile name; surfaced live in `bed_mesh.profiles`. No app-side datastore. | None app-side. App reads `bed_mesh.profiles`, writes via `BED_MESH_PROFILE SAVE=` + `SAVE_CONFIG`. |
| Live service config | `[screws_tilt_adjust]`, `[z_tilt]`, `[quad_gantry_level]`, `[bed_mesh]`, `[probe]` sections live in `printer.cfg` (and `configfile.settings`). Read-only to this app (config-editing is OUT of scope). | Read `[screws_tilt_adjust]` screw coords/names from `configfile.settings` for D-04/D-06. No writes except SAVE_CONFIG's mesh/z_offset persist. |
| OS-registered state | None — no OS-level registrations involved. | None — verified (Android app, no host-OS coupling per Out-of-Scope). |
| Secrets/env vars | None — calibration uses no new secrets; existing API-key path unchanged. | None. |
| Build artifacts | None — additive Kotlin source; no package rename. | None. |

**Critical cross-system note:** `SAVE_CONFIG` mutates the printer's `printer.cfg` and **restarts the Klipper host process** (verbatim: *"This command will overwrite the main printer config file and restart the host software."*). The spine MUST treat this exactly like the Phase-5 G2 FIRMWARE_RESTART path: warn (ConfirmGuard), send, expect klippy `shutdown→ready`, re-handshake (D-12). After restart, the newly-saved mesh profile appears in `bed_mesh.profiles` and the persisted Z-offset is live.

## Common Pitfalls

### Pitfall 1: `screws_tilt_adjust.results` is keyed by 1-based index, NOT the screw name
**What goes wrong:** You assume `results["front left screw"]` keyed by `screwN_name`; it's actually `results["screw1"]`, `results["screw2"]`, … (1-based loop index).
**Why it happens:** The config has `screwN_name` (a label), but the live object keys on `"screw%d" % (i+1)`.
**How to avoid:** Join `results["screwN"]` to the config's `screwN`/`screwN_name` by index N to get coordinates + label. The label for display comes from config; the turn data from the object. **[VERIFIED: klippy master `screws_tilt_adjust.py:98,113`]**
**Warning signs:** Empty screw names, or a KeyError-equivalent null lookup in the holder.

### Pitfall 2: `z_tilt.applied` is FALSE during a run AND after a failure
**What goes wrong:** You poll `applied` and treat `false` as "still running" or "in progress" — but it's also `false` immediately after a failed retry (`self.applied = False` at run start; only set `True` on success). A failure raises `gcode.error`, which surfaces as `RpcError`.
**Why it happens:** `applied` is a success flag, not a tri-state.
**How to avoid:** State machine = {Idle → Running (dispatched, in-flight) → Done (`applied==true`) | Failed (RpcError received)}. Never infer "failed" from `applied==false` alone. **[VERIFIED: klippy master `z_tilt.py:71,76,79`]**

### Pitfall 3: A long routine's gcode.script reply is the ack of COMPLETION, but not of SUCCESS
**What goes wrong:** Treating the 120s-bounded gcode.script reply as "succeeded". Klipper replies when the script ends — but a converged-vs-failed routine both "end". Worse, a retry-exhausted routine raises an error (→ RpcError).
**How to avoid:** Success = the live-object signal (Pattern 2). Failure = the dispatcher's `DispatchEvent.Failure`. The reply just means "stopped running." This is the exact Phase-5 lesson re-applied. **[VERIFIED: in-repo `CommandDispatcher.kt:120-176` G4 + G1]**

### Pitfall 4: `bed_mesh` reports `profiles` even when no mesh is loaded
**What goes wrong:** Empty-state mis-detection. `bed_mesh.profiles` (saved profiles) is independent of whether a mesh is currently ACTIVE.
**How to avoid:** Empty-state (D-07) = `mesh_matrix` empty / `profile_name == ""`. Profile LIST availability (D-10 load/remove) = `profiles` non-empty. They're different conditions. **[VERIFIED: klippy master `bed_mesh.py:229-250` — `profile_name` defaults `""`, `mesh_matrix` defaults `[[]]`, `profiles` is separate]**

### Pitfall 5: `BED_MESH_PROFILE SAVE=<name>` activates the mesh but does NOT persist it
**What goes wrong:** Save a profile, restart, profile gone. `SAVE=` writes the profile into the running config object; `SAVE_CONFIG` is what writes `printer.cfg`. Also: "default" is **no longer auto-loaded** at startup (behavior removed).
**How to avoid:** Profile save flow = `BED_MESH_PROFILE SAVE=<YY.MM.DD_HH.MM>` → ConfirmGuard → `SAVE_CONFIG` (D-10/D-12). Don't name a profile "default" expecting auto-load. **[CITED: klipper3d.org/Bed_Mesh.html — "After a profile has been saved ... SAVE_CONFIG ... to write the profile to printer.cfg"; "default ... behavior has been removed"]**

### Pitfall 6: The mock-vs-reality trap (THE recurring project bug class — 2 prior strikes)
**What goes wrong:** A lenient fake socket returns idealized object shapes; unit tests go green; the real printer returns a slightly different shape (extra wrapping, tuple-vs-array for `mesh_min`, null where you expected a number); on-device it breaks.
**Why it happens:** `mesh_min`/`mesh_max` are Python **tuples** `(x, y)` → serialize as JSON arrays `[x,y]`; `probed_matrix`/`mesh_matrix` are arrays-of-arrays; `screws_tilt_adjust.results` values are nested dicts. Subtle shapes.
**How to avoid:** **Probe the real Ender 5 Plus FIRST** (read-only `objects/query?screws_tilt_adjust&z_tilt&bed_mesh&manual_probe&probe` and a real `SCREWS_TILT_CALCULATE`/`BED_MESH_CALIBRATE` run), capture verbatim JSON into `app/src/test/resources/fixtures/` (exactly as Phase 8 did for `gcode_store_e5.json`), and write parsers against the captured fixtures. The on-device UAT is the backstop. **[ASSUMED: exact JSON serialization of tuples/matrices — verify by live probe before finalizing parsers]**

## Code Examples

Verified patterns — every shape below is quoted from Klipper master source or the in-repo substrate.

### 1. SCREWS_TILT_CALCULATE — structured result (the D-03 guided-loop source)
```
// Live object: printer.objects.query?screws_tilt_adjust
// get_status() returns:  [VERIFIED: klippy master screws_tilt_adjust.py:61-65]
{
  "error": false,            // bool — true if MAX_DEVIATION exceeded
  "max_deviation": null,     // float or null (the MAX_DEVIATION arg, if passed)
  "results": {
    "screw1": { "z": 2.48750, "sign": "CW",  "adjust": "00:00", "is_base": true  },
    "screw2": { "z": 2.36000, "sign": "CW",  "adjust": "01:15", "is_base": false },
    "screw3": { "z": 2.55000, "sign": "CCW", "adjust": "00:50", "is_base": false }
  }
}
// adjust is "HH:MM" = full turns : clock-minutes (e.g. "01:15" = 1 turn + 15 min).
// is_base==true → the reference screw (adjust "00:00"). Worst = max |minutes-equivalent of adjust|.
// Keyed screw1..screwN (1-based index) — join to config screwN/screwN_name by index (Pitfall 1).

// Console output (for the D-11 in-progress feed only — NOT the result parse):
// [VERIFIED: klippy master screws_tilt_adjust.py:87-119]
//   "// 01:20 means 1 full turn and 20 minutes, CW=clockwise, CCW=counter-clockwise"
//   "// front left screw (base) : x=-5.0, y=30.0, z=2.48750"
//   "// front right screw : x=155.0, y=30.0, z=2.36000 : adjust CW 01:15"
// MAX_DEVIATION breach raises: "bed level exceeds configured limits (Nmm)! Adjust screws and restart print."

// Config: [screws_tilt_adjust]  [CITED: klipper3d.org/Config_Reference.html]
//   screwN: X,Y        (>=2 screws; the count + coords for D-04/D-06)
//   screwN_name: label
//   screw_thread: CW-M3|CCW-M3|CW-M4|CCW-M4|CW-M5|CCW-M5  (default CW-M3)
//   horizontal_move_z (5), speed (50)
// Read from configfile.settings["screws_tilt_adjust"] (one-shot, like the Phase-8 macro bodies).
```

### 2. BED_MESH — calibrate, visualize, profiles
```
// Command surface  [CITED: klipper3d.org/Bed_Mesh.html + G-Codes.html]
//   BED_MESH_CALIBRATE [PROFILE=<name>] [METHOD=automatic|manual|scan|rapid_scan] [ADAPTIVE=0|1]
//   BED_MESH_PROFILE SAVE=<name>   (name REQUIRED — no nameless save → D-10 timestamp name)
//   BED_MESH_PROFILE LOAD=<name>
//   BED_MESH_PROFILE REMOVE=<name>
//   BED_MESH_OUTPUT [PGP=0|1]      (console dump — not needed; use the object)

// Live object: printer.objects.query?bed_mesh   [VERIFIED: klippy master bed_mesh.py:229-250]
{
  "profile_name": "",          // "" when no mesh active (empty-state, Pitfall 4)
  "mesh_min": [0.0, 0.0],      // tuple→JSON array [x,y]
  "mesh_max": [0.0, 0.0],
  "probed_matrix": [[]],       // raw probed Z grid (the D-08 dots)
  "mesh_matrix": [[]],         // interpolated Z grid (the D-07 red/blue heatmap)
  "profiles": { /* name -> {points, mesh_params} */ }   // saved profiles (D-10 load/remove list)
}
// [bed_mesh] config: probe_count default "3,3" (min 3/axis — D-08); mesh_min/mesh_max required;
//   algorithm lagrange|bicubic.  [CITED: Config_Reference.html]
```

### 3. Z_TILT_ADJUST / QUAD_GANTRY_LEVEL — automatic, convergence
```
// Live objects  [VERIFIED: klippy master z_tilt.py:80-81, quad_gantry_level get_status]
//   printer.objects.query?z_tilt            -> { "applied": false }   // true on SUCCESS only
//   printer.objects.query?quad_gantry_level -> { "applied": false }   // same shape
// applied resets false at run start; set true only on convergence (Pitfall 2).

// In-progress console (D-11 feed only)  [VERIFIED: klippy master z_tilt.py:113-114 (ZAdjustStatus.check_retry)]
//   "Making the following Z adjustments:\n stepper_z = -0.123000\n stepper_z1 = 0.045000"
//   "Retries: 1/5 Probed points range: 0.043000 tolerance: 0.025000"
//   (Z_TILT value_label = "Probed points range")
// Failure raises gcode.error -> RpcError -> dispatcher Failure toast:
//   "Too many retries"  /  "Retries aborting: Probed points range is increasing."
//   QGL adds: max_adjust breach -> abort.   [CITED: Config_Reference.html quad_gantry_level.max_adjust default 4]

// Config  [CITED: Config_Reference.html]
//   [z_tilt] z_positions (req), points (req), speed 50, horizontal_move_z 5, retries 0, retry_tolerance 0
//   [quad_gantry_level] gantry_corners (req), points (req, four), speed 50, horizontal_move_z 5,
//                       max_adjust 4, retries 0, retry_tolerance 0
```

### 4. PROBE_CALIBRATE — the interactive manual-probe session (D-01)
```
// Command surface  [VERIFIED: klippy master manual_probe.py + G-Codes.html]
//   PROBE_CALIBRATE            -> auto-probe, then opens manual probe session
//   Z_ENDSTOP_CALIBRATE        -> SAME manual-probe helper, for a Z position_endstop (probe-less sibling).
//                                 Registered ONLY when [stepper_z] has position_endstop set
//                                 (manual_probe.py:46,66-70). Gate by probe-present:
//                                 probe present -> PROBE_CALIBRATE; else -> Z_ENDSTOP_CALIBRATE.
//   TESTZ Z=<v> | Z=+ | Z=- | Z=++ | Z=--   (nudge; +/- relative to previous attempts)
//   ACCEPT                     -> accept current Z, conclude session, compute offset
//   ABORT                      -> terminate session, no change

// Session start console:  [VERIFIED: klippy master manual_probe.py:184-185]
//   "Starting manual Z probe. Use TESTZ to adjust position.\nFinish with ACCEPT or ABORT command."
// After each TESTZ:        [VERIFIED: klippy master manual_probe.py:238-239]
//   "Z position: %s --> %.3f <-- %s"   e.g.  "Z position: 5.001 --> 4.901 <-- 4.800"
//                                      (lower-bracket --> current <-- upper-bracket)
// On ACCEPT result:        [VERIFIED: klippy master manual_probe.py:83]
//   "Z position is %.3f"

// Live object: printer.objects.query?manual_probe   [VERIFIED: klippy master manual_probe.py:85-90]
{ "is_active": false, "z_position": null, "z_position_lower": null, "z_position_upper": null }
// Drive page enable/disable off is_active; show z_position live; ACCEPT/ABORT flips is_active->false.
// Then: SAVE_CONFIG to persist the probe z_offset (D-01 -> D-12 restart re-handshake).
```

### 5. SAVE_CONFIG — persistence + restart (D-12)
```
// [VERIFIED: klipper3d.org/G-Codes.html — verbatim]
//   "This command will overwrite the main printer config file and restart the host software."
// => klippy goes shutdown -> (host restart) -> ready.  Reuse the Phase-5 G2 path EXACTLY:
//    1. ConfirmGuard ("saves config + restarts the printer")
//    2. dispatch SAVE_CONFIG via CommandDispatcher
//    3. notify_klippy_shutdown then notify_klippy_ready arrive on the still-open socket
//    4. existing runHandshake() re-runs objects/subscribe (in-repo: 05-10 G2 fix, SessionControl)
// No Moonraker-specific extra signalling — the klippy lifecycle notifications are the trigger.
```

### 6. Files delete-gating fix (D-15) — the one-line correctness change
```kotlin
// CURRENT (too broad) — in-repo FilesScreen.kt:77,94
val printingActive = printerState.printState == PrintState.Printing || printerState.printState == PrintState.Paused
deleteEnabled = selected != null && !printingActive   // blocks ALL files during any print

// FIX (D-15) — only the CURRENTLY-PRINTING file is undeletable:
// print_stats.filename is the RELATIVE gcode path, dir-prefixed, NO leading "gcodes/"
//   [VERIFIED: docs/moonraker-capabilities.md:122 — filename "miata/airbox-bracket.gcode"]
// PrinterState.printFilename already carries it (PrinterState.kt:59). Compare against the
// selected file's path in the SAME relative form (FilesScreen already uses path-relative selection).
val activePrint = printerState.printFilename                       // "" when idle
val isActiveFile = activePrint.isNotEmpty() && selected?.path == activePrint
deleteEnabled = selected != null && !isActiveFile
// Put the comparison in a PURE host-testable helper (mirror PrinterCommands discipline) so the
// path-form match is unit-tested (the parser is the Nyquist quick-run target).
// Then: record the relaxed rule in docs/ui_design/ + add the scoping check to 09-UAT.md.
```

## State of the Art

| Old Approach | Current Approach | When Changed | Impact |
|--------------|------------------|--------------|--------|
| `bed_mesh` auto-loads "default" profile at startup | No auto-load; must `BED_MESH_PROFILE LOAD=` explicitly | Klipper (removed) | D-10 must not name a profile "default" expecting auto-load |
| `probe.last_z_result` for last calibrate result | Deprecated (removal planned) | Klipper | Don't depend on `last_z_result`; use `manual_probe.z_position` / the ACCEPT console line |
| Console-parse calibration results | Structured `get_status()` objects | Klipper has long exposed these | Use objects for display; this is the key de-risking finding |

**Deprecated/outdated:**
- `probe.last_z_result` — deprecated, removal planned. Use `manual_probe` object instead.
- KlipperScreen's manual-probe panel pattern is a reference only (per project Out-of-Scope: no gtk4_klipperscreen coupling).

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | Exact JSON serialization of `mesh_min`/`mesh_max` (Python tuple → JSON array `[x,y]`) and `probed_matrix`/`mesh_matrix` (array-of-arrays) | Code Examples 2, Pitfall 6 | Parser shape mismatch → heatmap renders wrong/empty. MITIGATION: live-probe E5 + capture fixture before finalizing (mandatory, per Pitfall 6) |
| A2 | `screws_tilt_adjust.results` is populated continuously after a run (persists in the object until next run / restart) so the holder can read it post-completion | Code Examples 1, Pattern 2 | If it clears immediately, the guided loop must capture it on the completion edge. MITIGATION: verify by live probe + a second query after the run completes |
| A3 | `Z_ENDSTOP_CALIBRATE` is registered only when `[stepper_z]` has `position_endstop` (source-read), so probe-present gating = `hasObject("probe")` ? PROBE_CALIBRATE : Z_ENDSTOP_CALIBRATE | Code Examples 4 | A printer with both could show the wrong one. LOW risk — both test printers have a probe (klicky/Klack). Confirm the gate predicate on E3 (single Z, Klack) at UAT |
| A4 | QUAD_GANTRY_LEVEL `quad_gantry_level.get_status()` returns `{applied}` identically to z_tilt (not separately confirmed line-by-line from source — built blind per D-02) | Code Examples 3 | QGL done-detection wrong. LOW risk — same `ZAdjustStatus` helper; QGL is gated-OFF on both test printers anyway (D-02), Z_TILT is the on-device proxy |
| A5 | `bed_mesh.profiles` value shape is `{name -> {points, mesh_params}}` (from `pmgr.get_profiles()`) — the app only needs the KEYS (profile names) for the load/remove list | Code Examples 2, D-10 | If the app tried to read nested profile internals it could break; it only needs keys. LOW risk |

## Open Questions

1. **Does `screws_tilt_adjust.results` persist after the run, or only emit transiently?**
   - What we know: `get_status()` returns `self.results`, reset to `{}` at the START of each run (source line 67-68), populated during the run.
   - What's unclear: whether a `notify_status_update` carrying the populated `results` is reliably delivered, or if the holder must read it via a one-shot `objects/query` on the completion edge.
   - Recommendation: subscribe `screws_tilt_adjust` AND fire a one-shot `objects/query?screws_tilt_adjust` when the dispatch completes (belt-and-braces); verify the live behavior at the mandatory pre-build probe.

2. **Exact METHOD default for BED_MESH_CALIBRATE on these printers (KAMP-adaptive?).**
   - What we know: E5+ has KAMP (capabilities doc); `BED_MESH_CALIBRATE` accepts `METHOD=` and `ADAPTIVE=`.
   - What's unclear: whether to pass `ADAPTIVE=1` by default or leave printer config to decide.
   - Recommendation: dispatch bare `BED_MESH_CALIBRATE` (let printer config/KAMP defaults apply); do NOT inject METHOD/ADAPTIVE in v1 (config-editing is out of scope).

3. **`info.total_layer`-style slicer-dependence — any equivalent gotcha for calibration objects?**
   - What we know: print_stats had a slicer/state-dependent nullable field that bit Status.
   - Recommendation: treat every new field nullable/defensive (the reducer's existing `?.`/`orNull` discipline); never `!!`.

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| Ender 5 Plus (live, `192.168.1.120:7125`) | On-device UAT of screws-tilt, Z_TILT, BED_MESH, PROBE_CALIBRATE; fixture capture | ✓ (Phase 8 used it 2026-06-02) | Klipper v0.13.0-662 / Moonraker v0.10.0 / API 1.5.0 | none — UAT is the backstop |
| `z_tilt` object (dual stepper_z) | Z_TILT on-device verification (D-02 proxy) | ✓ E5+ has dual `stepper_z`/`z1` | — | none |
| `bed_mesh` / KAMP | BED_MESH on-device | ✓ E5+ KAMP-equipped | — | none |
| `probe` (klicky/Klack) | PROBE_CALIBRATE | ✓ E5+ klicky, E3 Klack | — | Z_ENDSTOP_CALIBRATE path (A3) |
| `quad_gantry_level` object | QGL verification | ✗ neither printer has a gantry (D-02) | — | Z_TILT is the proxy (D-02); QGL built blind, gated off |
| Android build env (`E:\Android\gw.bat`) | Build/install | ✓ (CLAUDE.md) | JDK 21 / SDK android-35 | none |
| flox (Nexus 7 2013, Adreno 320) | Heatmap perf gate, on-device UAT | ✓ (Phase 8 used it) | LineageOS 18.1 / API 30 | none |

**Missing dependencies with no fallback:** none blocking. QGL is unverifiable by design (D-02) — Z_TILT proxies it.
**Missing dependencies with fallback:** QGL hardware → Z_TILT on-device proxy (same code path).

## Validation Architecture

> nyquist_validation is enabled (config.json `workflow.nyquist_validation: true`). This section is consumed by the orchestrator to emit VALIDATION.md.

### Test Framework
| Property | Value |
|----------|-------|
| Framework | JUnit (kotlin.test) — `:app:testReleaseUnitTest` (the project standard; 615 tests green as of Phase 8) |
| Config file | `app/build.gradle.kts` (test deps) — no separate runner config |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests '*Calibration*' --tests '*ScrewsTilt*' --tests '*BedMesh*' --tests '*ManualProbe*' --tests '*FilesDelete*' --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| On-device proxy | `adb` install of release build on **flox** + **live Ender 5 Plus** (the mock-vs-reality backstop; gfxinfo for heatmap perf) |

### Phase Requirements → Test Map
| Req (proposed) | Behavior | Test Type | Automated Command | File Exists? |
|--------|----------|-----------|-------------------|-------------|
| CALIB-02 | Screws-tilt: structured `results` → worst-out-of-tolerance screw + "X of N in tol" (D-03) | unit (pure parser) | `gw.bat :app:testReleaseUnitTest --tests '*ScrewsTiltResultTest*'` | ❌ Wave 0 — feed the REAL E5 fixture |
| CALIB-02 | Screws-tilt: join `results["screwN"]` to config `screwN`/`screwN_name` by index (Pitfall 1) | unit | same | ❌ Wave 0 |
| CALIB-03 | Z_TILT/QGL: `applied==true` → done; RpcError → failed (Pitfall 2) | unit (pure state machine) | `--tests '*TiltResultTest*'` | ❌ Wave 0 |
| CALIB-04 | Bed-mesh: `mesh_matrix`→color, `probed_matrix`→dots, empty-state from `profile_name`/`mesh_matrix` (Pitfall 4); profile list from `profiles` keys | unit (pure model) | `--tests '*BedMeshModelTest*'` | ❌ Wave 0 |
| CALIB-04 | Bed-mesh heatmap Canvas: allocation-free onDraw + token recolor + Adreno-320 perf | on-device (gfxinfo) | manual — flox + gfxinfo two-part gate | ❌ device checkpoint |
| CALIB-05 | Manual-probe: `is_active` drives page state; `Z position: a --> b <-- c` parse → z_position | unit (pure parser) | `--tests '*ManualProbeStateTest*'` | ❌ Wave 0 |
| CALIB-05 | Z_ENDSTOP_CALIBRATE vs PROBE_CALIBRATE gate by probe-present (A3) | unit (pure predicate) | `--tests '*ProbePresentGateTest*'` | ❌ Wave 0 |
| CALIB-01 | Hub lists ONLY supported routines (`hasObject` gating; no dead buttons) | unit | `--tests '*CalibrationGateTest*'` | ❌ Wave 0 |
| CALIB-06 | Files delete: only the `print_stats.filename`-matching file is undeletable during print (D-15) | unit (pure predicate) | `--tests '*FilesDeleteGateTest*'` | ❌ Wave 0 |
| (cross) | SAVE_CONFIG → klippy shutdown→ready → re-handshake (D-12) | integration (existing G2 harness) | `--tests '*ReHandshake*'` (extend the existing 05-10 G2 test) | ✅ extend |
| (cross) | gcode.script gets G4 120s timeout (no false "could not be sent") | unit (existing dispatcher test) | `--tests '*CommandDispatcher*'` | ✅ exists |

### Off-hardware testability of each routine (the Nyquist core)
Every routine's RESULT-PARSE and GATING-PREDICATE is a **pure function** fed a captured-from-real-hardware JSON fixture — host-testable with NO device:
- **screws-tilt:** pure `parseScrewsTilt(results: JsonObject, config: ScrewConfig): GuidedLoopState` → worst screw, in-tol count.
- **bed-mesh:** pure `BedMeshModel.from(bedMeshObject)` → matrices, min/max, empty-state, profile names.
- **z-tilt/QGL:** pure state machine over {dispatched, `applied`, RpcError}.
- **manual-probe:** pure `parseZPosition(line)` + `is_active` page-state derivation.
- **files-delete:** pure `deleteAllowed(selectedPath, activePrintFilename, printState): Boolean`.

### On-device verification proxies (what the host tests CANNOT cover)
- **Heatmap render/perf** — Adreno-320 fill-rate of the 2D Canvas → gfxinfo on flox (the two-part liveness+latency gate from Phase 3/5).
- **QGL (D-02)** — no gantry hardware → **`Z_TILT_ADJUST` is the on-device proxy** (identical run-and-converge code path on real dual-Z). QGL itself stays gated-off and unverified-by-design.
- **Real object shapes (Pitfall 6)** — live-probe the E5 BEFORE writing parsers; capture fixtures; the on-device UAT (screws-tilt loop, mesh heatmap, Z-calibrate accept→SAVE_CONFIG recovery) is the mock-vs-reality backstop.

### Sampling Rate
- **Per task commit:** quick run (calibration/files-delete pure tests).
- **Per wave merge:** full `:app:testReleaseUnitTest`.
- **Phase gate:** full suite green + on-device UAT on flox + live Ender 5 Plus (incl. the D-15 delete-scoping check in `09-UAT.md`) before `/gsd-verify-work`.

### Wave 0 Gaps
- [ ] `app/src/test/resources/fixtures/screws_tilt_adjust_e5.json` — REAL `SCREWS_TILT_CALCULATE` run capture (covers CALIB-02)
- [ ] `app/src/test/resources/fixtures/bed_mesh_e5.json` — REAL post-`BED_MESH_CALIBRATE` `objects/query?bed_mesh` (covers CALIB-04)
- [ ] `app/src/test/resources/fixtures/z_tilt_e5.json` — REAL post-`Z_TILT_ADJUST` capture (covers CALIB-03)
- [ ] `app/src/test/resources/fixtures/manual_probe_e5.json` — REAL `manual_probe` mid-session capture (covers CALIB-05)
- [ ] `app/src/test/resources/fixtures/configfile_screws_e5.json` — `configfile.settings["screws_tilt_adjust"]` (covers D-04/D-06 screw coords/names)
- [ ] `*ScrewsTiltResultTest`, `*TiltResultTest`, `*BedMeshModelTest`, `*ManualProbeStateTest`, `*ProbePresentGateTest`, `*CalibrationGateTest`, `*FilesDeleteGateTest` — RED scaffolds
- [ ] Framework install: none — JUnit/kotlin.test already present

## Security Domain

> security_enforcement enabled, ASVS Level 1.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V2 Authentication | no | Unchanged — existing API-key/trusted-client path (CONN-02) reused as-is |
| V3 Session Management | no | No new session surface (the manual-probe "session" is a printer-side gcode session, not an auth session) |
| V4 Access Control | no | LAN-local printer; no new access boundary |
| V5 Input Validation | yes | All calibration commands are FIXED gcode strings (no free-text). `BED_MESH_PROFILE SAVE=<name>` uses an APP-GENERATED timestamp name `YY.MM.DD_HH.MM` (D-10) — no user text → no injection surface. `TESTZ Z=<v>` uses a CLAMPED scrubber value (mirror `PrinterCommands` clamp-before-format discipline). |
| V6 Cryptography | no | No crypto in scope |

### Known Threat Patterns for {Klipper gcode dispatch on Android}

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Gcode injection via a profile name or param | Tampering | App-generated timestamp profile names (no keyboard, D-10); fixed command strings; CLAMP `TESTZ` step like `PrinterCommands` (ASVS V5). Reuse the Phase-8 V5 sanitizer pattern only if any free-text ever appears (it should not). |
| Credential leak in a failure toast | Information Disclosure | Already mitigated — `CommandDispatcher` composes failure messages from non-secret `method`/`key` only, NEVER `e.message` for transport errors (T-05-11-01). Calibration RpcError text (e.g. "Too many retries") is gcode-rejection text, carries no credential. |
| Cold/unsafe motion (running a routine unhomed) | Tampering/safety | D-13 pre-flight gates run on `homedAxes`; offer Home. The amber-Override/force-move escapes are NOT exposed on calibration pages. |
| Destructive SAVE_CONFIG without warning | Tampering | ConfirmGuard before SAVE_CONFIG (D-12) — user is warned it rewrites config + restarts the printer. |

## Sources

### Primary (HIGH confidence)
- **Klipper master source (verbatim, the load-bearing pins):**
  - `klippy/extras/screws_tilt_adjust.py` — `results` dict shape (`z`/`sign`/`adjust`/`is_base`), 1-based `screwN` keying, `error`/`max_deviation`, console output format, MAX_DEVIATION error text
  - `klippy/extras/manual_probe.py` — `manual_probe` status (`is_active`/`z_position`/`z_position_lower`/`z_position_upper`), "Starting manual Z probe..." + "Z position: %s --> %.3f <-- %s" output, Z_ENDSTOP_CALIBRATE registration condition
  - `klippy/extras/bed_mesh.py` — `bed_mesh` status (`profile_name`/`mesh_min`/`mesh_max`/`probed_matrix`/`mesh_matrix`/`profiles`) defaults + shapes
  - `klippy/extras/z_tilt.py` — `z_tilt.applied` semantics (reset-false/set-true-on-success), `ZAdjustStatus.check_retry` "Retries: N/M ...: error tolerance: tol" output, value_label "Probed points range"
- klipper3d.org/Status_Reference.html — confirms the object field sets above
- klipper3d.org/Config_Reference.html — `[z_tilt]`/`[quad_gantry_level]`/`[screws_tilt_adjust]`/`[bed_mesh]` params + defaults + screw_thread allowed values
- klipper3d.org/Bed_Mesh.html — BED_MESH_PROFILE name-required, "default" no-longer-auto-loaded, SAVE_CONFIG persistence
- klipper3d.org/G-Codes.html — SAVE_CONFIG verbatim restart text, TESTZ/ACCEPT/ABORT, QGL params
- **In-repo substrate (read directly):** `state/Capabilities.kt`, `state/DeriveCapabilities.kt`, `state/PrinterState.kt`, `state/PrinterStateReducer.kt`, `command/CommandRegistry.kt`, `command/CommandDispatcher.kt` (G1/G4), `command/PrinterCommands.kt`, `render/GraphView.kt`, `ui/files/FilesScreen.kt`, `docs/moonraker-capabilities.md` (filename path-form, dual-Z/KAMP/probe presence)

### Secondary (MEDIUM confidence)
- klipper.discourse.group / sean-dearing gitbook — corroborated the "Z position: 5.001 -> 6.001" / "Starting manual Z probe" console strings (then VERIFIED against klippy source)

### Tertiary (LOW confidence)
- KlipperScreen Zcalibrate panel docs — reference only (no code coupling, per project Out-of-Scope); not relied upon

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — no new packages; all pre-pinned + on-device proven
- Live-object shapes (results/matrices/applied/manual_probe): HIGH — pinned verbatim from Klipper master source (not docs prose); JSON serialization of tuples/matrices flagged A1 for live-probe confirmation (Pitfall 6)
- Architecture/extension points: HIGH — read directly from the repo; all seams (registry, gating, reducer, dispatcher G1/G4, G2 re-handshake, GraphView discipline) exist
- Pitfalls: HIGH — derived from source semantics + two prior project mock-vs-reality strikes
- QGL specifics: MEDIUM-by-design — built blind (D-02), Z_TILT is the on-device proxy

**Research date:** 2026-06-02
**Valid until:** 2026-07-02 (Klipper object shapes are stable; re-verify if the printer's Klipper version jumps a minor). The mandatory pre-build live probe (Pitfall 6 / Wave 0 fixtures) is the freshness backstop regardless of this date.
