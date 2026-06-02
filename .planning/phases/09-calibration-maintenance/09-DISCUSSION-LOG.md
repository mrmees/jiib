# Phase 9: Calibration & Maintenance - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-02
**Phase:** 9-Calibration & Maintenance
**Areas discussed:** Routine scope + Z-calibrate, Screws-tilt guided flow, Bed mesh display + profiles, Run lifecycle + SAVE_CONFIG, Navigation

---

## Routine scope + Z-calibrate

### ZCAL-01 disposition
| Option | Description | Selected |
|--------|-------------|----------|
| Defer Z-calibrate | Keep phase to the 4 leveling routines; Z-offset its own later phase | |
| Live Z-offset adjust only | Babystep-style SET_GCODE_OFFSET + Z_OFFSET_APPLY_PROBE, no TESTZ loop | |
| Full PROBE_CALIBRATE workflow | Interactive manual-probe: TESTZ +/-, ACCEPT, ABORT, SAVE_CONFIG | ✓ |

**User's choice:** Full PROBE_CALIBRATE workflow.

### QGL handling
| Option | Description | Selected |
|--------|-------------|----------|
| Build blind, gated off | Same run-and-show-convergence as Z_TILT, gated on `quad_gantry_level`; verify via Z_TILT | ✓ |
| Defer QGL entirely | Ship only the 3 verifiable routines | |

**User's choice:** Build blind, gated off.
**Notes:** Neither printer has a gantry; Z_TILT (dual stepper_z on the E5+) is the on-device proxy. Claude added: treat `Z_ENDSTOP_CALIBRATE` as the probe-less sibling of the manual-probe page.

---

## Screws-tilt guided flow

### Flow shape
| Option | Description | Selected |
|--------|-------------|----------|
| Guided one-at-a-time loop | Worst screw highlighted with CW/CCW turn, re-probe, advance until in-tol | ✓ |
| Flat all-screws table | Run once, show all screws at once | |

**User's choice:** Guided loop — **plus** support 3-screw AND 4-screw beds, and (slick idea) draw the user's bed to scale and place screw indicators at their real config coordinates.
**Notes:** Coordinates live in the `[screws_tilt_adjust]` config object. Captured as desired enhancement D-06 with a labeled-list fallback.

### Gutter verbs (Initiate vs Adjust)
| Option | Description | Selected |
|--------|-------------|----------|
| Initiate=first probe, Adjust=re-probe | Both fire SCREWS_TILT_CALCULATE; Adjust re-measures after a turn | ✓ (clarified) |
| Initiate=run, Adjust=skip/next screw | Adjust = navigation only, no command | |
| You decide | Planner/UI picks | |

**User's choice:** "No real functional difference — one starts, one reruns the same thing and displays the new measurement. Check the docs for how this is expected to work. Don't really need Accept/Cancel, just a Back that closes the prompt."
**Notes:** Verified against Klipper `Manual_Level.html` — `SCREWS_TILT_CALCULATE` is one-shot with NO interactive prompt (no Accept/Abort, nothing to close). So Initiate/Adjust both just (re-)run it, and the gutter collapses to a single **Back** (navigation). The prompt-close Matthew recalled belongs to PROBE_CALIBRATE/manual-probe, not screws-tilt. Captured as D-05.

---

## Bed mesh display + profiles

### Visualization
| Option | Description | Selected |
|--------|-------------|----------|
| Colored heatmap grid + values | Canvas heatmap with overlaid Z values | ✓ (refined) |
| Plain numeric table | Matrix of Z values, no color | |
| Pseudo-3D wireframe | Tilted surface, heaviest render | |

**User's choice:** Info-rich-but-simple — Focus shows current mesh by default (indicator if none); overhead 2D classic red/blue heatmap (red=high/blue=low); faint probe-point dots that scale with density (3×3 → 50×50); mostly static; user can adjust the red/blue scale to pinpoint high/low spots.

### Profile management
| Option | Description | Selected |
|--------|-------------|----------|
| Full: calibrate + save/load/delete + SAVE_CONFIG | Complete profile lifecycle, handles firmware restart | ✓ |
| Calibrate + load/apply only | No new-profile creation | |
| Run + view only | No profile management | |

**User's choice:** Full — engaged on save-naming: "if no name specified bed_mesh_profile auto-saves to default — if I'm wrong, make the default name a timestamp eg 26.06.02_18.47."
**Notes:** Verified against `Bed_Mesh.html` — `SAVE=` REQUIRES a name (no nameless save); "default" is no longer auto-loaded at startup; min grid is 3×3 not 2×2. Resolution: auto-generate timestamp name `YY.MM.DD_HH.MM` (also sidesteps the no-keyboard law). Captured as D-10.

---

## Run lifecycle + SAVE_CONFIG

### In-progress display
| Option | Description | Selected |
|--------|-------------|----------|
| Live response feed | Stream gcode_response lines, reuse Phase-8 parse | ✓ |
| Parsed point-progress bar | Determinate k/N bar (brittle) | |
| Generic busy indicator | Indeterminate "Calibrating..." | |

**User's choice:** Live response feed.

### Abort
| Option | Description | Selected |
|--------|-------------|----------|
| No soft-cancel; estop is the interrupt | Disable Back/re-run during automatic routines; estop only | ✓ |
| Attempt a soft cancel | Misleading for automatic routines | |

**User's choice:** No soft-cancel; estop is the interrupt. (Manual-probe Z-calibrate page still gets a real ABORT.)

### Pre-flight
| Option | Description | Selected |
|--------|-------------|----------|
| App pre-flights | Gate on homed (offer Home); optional bed pre-heat for mesh | ✓ |
| Fire raw, surface errors | Just send; toast Klipper's error | |

**User's choice:** App pre-flights.

### SAVE_CONFIG handling
| Option | Description | Selected |
|--------|-------------|----------|
| Reuse Phase-5 G2 re-handshake | ConfirmGuard warn + restart-recovery reconnect/resubscribe | ✓ |
| You decide | Planner pins it | |

**User's choice:** Reuse Phase-5 G2 re-handshake.

---

## Navigation

| Option | Description | Selected |
|--------|-------------|----------|
| Single 'Calibration' hub tile | One drawer tile → hub listing supported routines | ✓ |
| Individual drawer tiles | One tile per routine | |
| You decide | UI phase picks | |

**User's choice:** Single 'Calibration' hub tile.

---

## Claude's Discretion

- `manual_probe`/`PROBE_CALIBRATE` state parse + whether `Z_ENDSTOP_CALIBRATE` surfaces for probe-less printers (research to confirm).
- Heatmap cell interpolation, dot-sizing curve, color-scale control granularity.
- TESTZ step-preset values for the Z-calibrate jog.
- Focus/Field/Gutter composition of each non-mockup page (screws-tilt is locked by mockup).
- Cost/feasibility call on the to-scale bed drawing vs the list fallback.

## Deferred Ideas

- Input shaper calibration (SHAPER-01) — its own later phase.
- Calibration config-section editing — out of scope.
- `Z_ENDSTOP_CALIBRATE` probe-less sibling — include only if it falls out of the same manual-probe helper.

## Folded Todo

- `files-delete-gating-too-broad` — fix `FilesScreen.deleteEnabled` to gate only the currently-printing file; add the delete-during-print-scoping check to 09-UAT; record the relaxed UI-SPEC rule.
