# Control / Button Master List — Phase 0 deliverable (OWNER REVIEW GATE)

**Date:** 2026-06-14
**Status:** PARED-DOWN MASTER LIST — awaiting owner redlines (icons / labels / intents)
**Spec:** `.planning/notes/2026-06-14-control-baseline-audit-design.md`
**Plan:** `.planning/notes/2026-06-14-control-baseline-audit-plan.md`

> **How to read this.** This is the *result* of reduction, not the raw inventory. ~175 in-scope call
> sites across 28 screens collapsed to **44 distinct named controls** (Part 2). Micro-spacing
> variance was absorbed into class tokens silently — you will not see a "this gap was 10 not 8" row.
> Rogue/inline styles are noted only as "migrate to `<class>`" in the *current status* column, not as
> separate decisions. The only things that need YOUR call are in **Part 3 — Open decisions**
> (unassigned icons → ASK, intent conflicts, label wording, ambiguous class).
>
> **Icon law honored:** every `proposed icon` is either `ASSIGNED: DinghyIcons.<X>` (already chosen,
> in the registry / as-built screen / `img/`) or **`ASK`** (nothing assigned — Claude picks NONE;
> candidates from `img/material-icon-bucket.json` are listed in Part 3 *for you to pick from*).

---

## Part 1 — Consolidated classes + baseline (quick sign-off)

The final control class set. Each holds **1U height** by default (where `LocalUnitDp` is provided)
and derives inner spacing from two U-fraction tokens.

| Class | 1U height | `gapS` (≈U×0.125 ≈ 8dp) | `gapM` (≈U×0.1875 ≈ 12dp) | What it is |
|---|---|---|---|---|
| **ActionButton** | `heightIn(min=uDp)` (≥64dp) | — | — | Foot/focus-foot `OutlinedControl` (Back, Save, Print, Delete, Off, Execute…) |
| **SelectorRow** (+ presets) | tile = 1U | intra-tile gap | inter-row rhythm | One primitive; `SortRow` / `FilterRow` / `IncrementPicker` become named presets (locked SortFilter anatomy preserved) |
| **StepperRow** | row = 1U | between `[−][value][+]` | — | One `[−][value][+]` class; `Decrease`/`Increase` icons (kills literal `−`/`+`/`Z−`) |
| **Toggle** | 1U | — | — | On/off · this/that; inactive half = `Intent.Neutral` (R18). In-scope toggles become SelectorRow presets |
| **Scrubber** | track+thumb ≤1U | — | — | 004 ringed-thumb (already centralized; verify U-relative only) |
| **ColorSwatch** | tile (grid) | swatch gap | — | Tappable color tile (Temperature trace pool + Spool filter pool) |
| **DenseValueCell** | 1U | — | — | Value over background glyph (`IconValueCell` — no change) |
| **DockedEStop** | 0.7U (min 64) | — | `padFloat`=14dp (shell fallback only) | FocusFrame-header morph + shell fallback (cataloged only — not restyled) |

**State-style baseline (one per class):**

| State | Treatment |
|---|---|
| **default** | filled `t.surface`, 2dp intent border, 1U height |
| **pressed** | one-shot Compose ripple; Scrubber `accentSoft` halo — NO continuous animation (Adreno-320) |
| **selected / active** | `fill = accentSoft` + `accentLine` border (ListRow-selected convention; already in IncrementPicker) |
| **disabled** | `enabled = false` (R10 true disablement — no click installed) + alpha 0.38 + `semantics { disabled() }` |
| **pending / busy** | alpha 0.38 but **stays clickable** — taps accumulate (AdjusterPanel `busy` convention) |

**Spacing tokens** (R13 supersession — names survive, now resolve to U-fractions): `gapS ≈ U×0.125`
(absorbs the stray 4/10dp), `gapM ≈ U×0.1875`, `padFloat = 14dp` (floating-overlay edge case ONLY).

**Focus-body layout law — GENERAL button groups BOTTOM-DOCK (owner, 2026-06-14).** A **general
button / sort / filter / selector / stepper / toggle group** rendered inside a Focus is **pinned to
the BOTTOM** of the Focus content area (weighted body/value zone above takes the slack via
`weight(1f)`; the group sits at the bottom via `Arrangement.Bottom` / `SpaceBetween` / a trailing
`Spacer`). It NEVER floats centered or top-aligned. This **generalizes** `LAYOUT.md §"Focus with a
docked action region"` (was adjuster-only) to every in-scope Focus that holds a GENERAL control group.

> ⚠ **DOES NOT APPLY to SPECIALIZED domain controls** — they keep their own layout, untouched:
> bed-area representations (`BedMapView`/`BedMeshHeatmapView`/jog-pad-over-bed), **height/position-
> relative sliders** (the Move **Z scrubber** — its vertical position IS the data), the **XY
> scrubbers**, `ColorWheel`, and anything where vertical placement encodes meaning. Do NOT bottom-dock
> these. Extrude excluded entirely.

Compliant today: `AdjusterPanel` (Zone-2), Calibration Hub (docked Open). **NON-compliant general
groups (fix in this audit):** the **Move Microstep** focus (`MoveScreen.kt:491-493` top-aligns), the
**Move Bookmark / Add-bookmark** pages, and any in-Focus calibration ± stack that is a general button
group — verify + bottom-dock each as its phase lands.

---

## Part 2 — Named-control mapping (THE MEAT — owner redlines this)

One row per DISTINCT named control, sorted by **class** then **key**. `intent` per the R5 four-class
scheme. *current status* notes the dominant implementation + any rogue migration target.

> ⚠ **Part 3 §(e) folds in post-Codex corrections that OVERRIDE specific rows below:** Move
> focus-body ± / axis-chip are **OUT** (not in `stepper.*`/`move.axis_select`); `common.cancel`
> splits (discarding cancel → Danger); the Probe step-size ± reclassifies StepperRow→SelectorRow.
> Read §(e) for the authoritative deltas.

### Class: ActionButton

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `common.back` | Plain nav back (changes nothing) | ~all (CalibHub, Probe, ScrewsTilt, ZTilt, BedMesh, Console, Files, FineTune, Macros, Move, Outputs, Spool, Temp, ScanSurface, About, Printers, SystemPage, SysInfo, Webcam, Prompt-Close-adjacent) | clean OutlinedControl, `Intent.Accent`, FIRST position | ASSIGNED: `DinghyIcons.Back` | `R.string.common_back` | Accent | Button |
| `common.back.discard` | Back that DISCARDS pending input / REJECTS a pending result (C7) | ScanConfirmCard (reject scan), Spool measure-weight Field-takeover (discard measurement) | clean OutlinedControl, `Intent.Danger` (red Back) | ASSIGNED: `DinghyIcons.Back` | `R.string.common_back` (Spool: `spool_measure_back`) | Danger | Button |
| `common.cancel` | Cancel / dismiss a dialog-y action | BedMesh save-name, Move save-dialog, Prompt (style-dependent) | clean OutlinedControl, `Intent.Accent` (BedMesh save-name = `Intent.Danger`) | (none assigned; label-only) | `R.string.common_cancel` | Accent | Button |
| `common.done` | Close picker / accept-and-return | Spool filter, Temp preset/appearance/sensor pickers | clean OutlinedControl, `Intent.Go` (Spool filter) / `Intent.Accent` (Temp pickers) | ASSIGNED: `DinghyIcons.Check` (Spool); Temp pickers = label-only | `R.string.common_done` (Spool filter: `spool_filter_done`) | Go | Button |
| `common.home` | Navigate to PrintStatus | Spool foot | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.Home` | (icon-only) | Accent | Button |
| `common.save` | Save / commit a named artifact | Move save-dialog, Printers editor, BedMesh save-name | clean OutlinedControl, `Intent.Go` | ASSIGNED: `DinghyIcons.SaveLocation` (Move) / label-only (Printers, BedMesh) | `R.string.common_save` | Go | Button |
| `calibration.abort` | Abort active calibration routine | Probe (active) | clean OutlinedControl, `Intent.Danger` | (none assigned; label-only) | `R.string.calibration_abort` | Danger | Button |
| `calibration.accept` | Accept calibration result | Probe (active) | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | `R.string.calibration_accept` | Go | Button |
| `calibration.home_all` | Home all axes (precondition) | Probe, ScrewsTilt, ZTilt, BedMesh | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | `R.string.calibration_home_all` | Go | Button |
| `calibration.open` | Open selected routine | CalibrationHub | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | `R.string.calibration_open_routine` | Go | Button |
| `calibration.run` | Run / Run-again / Calibrate / Apply / Start routine | ScrewsTilt, ZTilt, BedMesh (`mesh_apply`/`mesh_calibrate`), Probe (`start`) | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | `R.string.calibration_run` (variants: `_run_again`, `_start`, `mesh_apply`, `mesh_calibrate`) | Go | Button |
| `calibration.save_config` | Persist calibration to printer config | Probe (accepted) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.calibration_save_config` | Warn | Button |
| `console.clear` | (NOT present — see Part 3 §d) | — | — | — | — | — | — |
| `files.delete` | Delete selected file | Files | clean OutlinedControl, `Intent.Danger`, alpha-gated | ASSIGNED: `DinghyIcons.Delete` | `R.string.files_foot_delete` | Danger | Button |
| `files.print` | Start print of selected file | Files | clean OutlinedControl, `Intent.Go`, alpha-gated | ASSIGNED: `DinghyIcons.Print` | `R.string.files_foot_print` | Go | Button |
| `files.spool_warning.print_anyway` | Proceed to print despite no-spool warning | Files (SpoolWarningGuard) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.files_spool_warning_print_anyway` | Warn | Button |
| `finetune.reset_all` | Reset all fine-tune params | FineTune | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.finetune_reset_all` | Warn | Button |
| `macros.execute` | Execute selected macro | Macros (launcher) | clean OutlinedControl, `Intent.Go` | ASSIGNED: `DinghyIcons.ExecuteMacro` | `R.string.macros_foot_execute` | Go | Button |
| `macros.manage` | Open macro manage mode | Macros (launcher) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.ManageMacros` | `R.string.macros_foot_manage` | Accent | Button |
| `move.disable_motors` | Disable steppers (loses homing) | Move foot | clean OutlinedControl, `Intent.Danger` | ASSIGNED: `DinghyIcons.MoveDisableMotors` | (icon-only) | Danger | Button |
| `move.home_all` | Home all axes | Move foot | clean OutlinedControl, `Intent.Go` | ASSIGNED: `DinghyIcons.MoveHomeAll` | (icon-only) | Go | Button |
| `move.bookmark.delete` | Delete a saved location | Move (Bookmark sub-mode) | clean OutlinedControl, `Intent.Danger` | (none assigned; label-only "Delete") | `R.string.common_delete` (verify) | Danger | Button |
| `move.bookmark.go` | Move to saved location | Move (Bookmark sub-mode) | clean OutlinedControl, `Intent.Go` | (none assigned; label-only "Move") | (verify resource) | Go | Button |
| `output.off` | Turn output / heater off (set 0) | Outputs (scrubber + LED surfaces), Temperature (heater popup) | clean OutlinedControl, `Intent.Danger` (Outputs) / `Intent.Warn` (Temp heater) | (none assigned; label-only) | `R.string.output_off` | **CONFLICT → Part 3 §b** | Button |
| `printers.add` | Add new printer profile | Printers | clean OutlinedControl, `Intent.Accent` | (none assigned; label-only) | `R.string.printers_add` | Accent | Button |
| `printers.arm_delete` | Arm delete-mode for printer rows | Printers | clean OutlinedControl, `Intent.Neutral`→`Intent.Danger` when armed | (none assigned; label-only) | `R.string.printers_delete` | Danger (armed) | Button |
| `printers.arm_edit` | Arm edit-mode for printer rows | Printers | clean OutlinedControl, `Intent.Accent` | (none assigned; label-only) | `R.string.printers_edit` | Accent | Button |
| `printers.clear_key` | Clear stored API key | Printers (editor) | clean OutlinedControl, `Intent.Danger` | (none assigned; label-only) | `R.string.printers_clear_key` | Danger | Button |
| `printers.scan_mdns` | Scan LAN for printers (mDNS) | Printers (editor) | clean OutlinedControl, `Intent.Accent` | (none assigned; label-only) | `R.string.printers_scan` (dynamic "Scanning…") | Accent | Button |
| `printstatus.preheat` | Preheat nozzle/bed (idle) | PrintStatus (standby foot) | clean OutlinedControl, `Intent.Warn` | ASSIGNED: `DinghyIcons.FootPreheat` | `R.string.home_foot_preheat` | Warn | Button |
| `printstatus.system` | Open System page (idle + mid-print shortcut) | PrintStatus | foot = clean OutlinedControl `Intent.Accent`; mid-print shortcut = **inline Box rogue → migrate to ActionButton/tile** | ASSIGNED: `DinghyIcons.FootSystem` | `R.string.home_foot_system` | Accent | Button |
| `print.cancel` | Cancel running print | PrintStatus (active foot) | clean OutlinedControl, `Intent.Danger`, busy-dim | (none assigned; label-only) | (via `control.labelRes`) | Danger | Button |
| `print.dismiss` | Dismiss finished-print card | PrintStatus (active foot) | clean OutlinedControl, `Intent.Accent` | (none assigned; label-only) | (via `control.labelRes`) | Accent | Button |
| `print.pause` | Pause running print | PrintStatus (active foot) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | (via `control.labelRes`) | Warn | Button |
| `print.resume` | Resume / Reprint | PrintStatus (active foot) | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | (via `control.labelRes`) | Go | Button |
| `prompt.close` | Close macro prompt dialog | PromptDialog | clean OutlinedControl, `Intent.Accent`, **raw `symbol = "close"` (NOT a registered token)** | **ASK** (see Part 3 §a) | `"Close"` (literal → should be `R.string`) | Accent | Button |
| `prompt.footer_button` | Macro-author-defined prompt button (PRIMARY/SECONDARY/DANGER) | PromptDialog | **inline Box+border+clickable rogue → migrate to ActionButton** (style→intent via `promptStyleColor`); author-hex carve-out preserved | (none — author-defined) | (author label) | maps style→intent | Button |
| `scan.use_picker` | Escape to manual picker (camera unavailable) | ScanSurface (degrade) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.ScanUsePicker` | (label "Use picker instead" → `R.string`) | Accent | Button |
| `spool.clear` | Clear active spool record | ActiveSpoolCard | clean OutlinedControl, `Intent.Danger`, conditional | ASSIGNED: `DinghyIcons.SpoolClear` | (label "Clear" → `R.string`) | Danger | Button |
| `spool.change` | Open spool picker (swap) | ActiveSpoolCard | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.SpoolChange` | (label "Change" → `R.string`) | Accent | Button |
| `spool.filter.clear` | Clear active filter facet | Spool (filter picker) | clean OutlinedControl, `Intent.Danger` | ASSIGNED: `DinghyIcons.DeleteSweep` | `R.string.spool_filter_clear` | Danger | Button |
| `spool.load` | Load selected spool | Spool foot | clean OutlinedControl, `Intent.Go`, conditional | ASSIGNED: `DinghyIcons.ExpandCircleUp` | (icon-only) | Go | Button |
| `spool.unload` | Unload active spool | Spool foot | clean OutlinedControl, `Intent.Go`, conditional | ASSIGNED: `DinghyIcons.ExpandCircleDown` | (icon-only) | Go | Button |
| `spool.measure.set` | Apply measured weight | Spool (measure Field-takeover) | clean OutlinedControl, `Intent.Go` | ASSIGNED: `DinghyIcons.Check` | `R.string.spool_measure_set` | Go | Button |
| `spool.scan` | Open QR scan | Spool foot, Files (SpoolWarning), ActiveSpoolCard (`QrCodeScanner`) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.QrCode` (Spool/Files); `DinghyIcons.QrCodeScanner` (ActiveSpoolCard) → **Part 3 §c (two glyphs, same role)** | `R.string.files_spool_warning_scan` / (icon-only) | Accent | Button |
| `spool.warning.pick_spool` | Open spool picker from no-spool guard | Files (SpoolWarningGuard) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.Inventory` | `R.string.files_spool_warning_pick_spool` | Accent | Button |
| `scan.camera_flip` | Toggle front/back camera | ScanSurface | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.ScanCameraFlip` | (dynamic "Front cam"/"Rear cam") | Accent | Toggle |
| `scan.confirm.set_active` | Confirm scan → set active spool | ScanConfirmCard | clean OutlinedControl, `Intent.Go` | ASSIGNED: `DinghyIcons.CheckCircle` | (label "Set active" → `R.string`) | Go | Button |
| `splash.edit_connection` | Edit / set up printer connection | Splash (Unreachable / FirstRun) | clean OutlinedControl, `Intent.Accent` (FirstRun = `Intent.Go`) | (none assigned; label-only) | `R.string.splash_edit_connection` / `splash_setup_printer` | Accent | Button |
| `splash.restart_firmware` | Restart MCU firmware | Splash (KlippyDown) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.splash_restart_firmware` | Warn | Button |
| `splash.restart_klipper` | Restart Klipper host | Splash (KlippyDown) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.splash_restart_klipper` | Warn | Button |
| `splash.retry` | Retry connection | Splash (KlippyDown, Unreachable) | clean OutlinedControl, `Intent.Go` | (none assigned; label-only) | `R.string.splash_retry` | Go | Button |
| `temp.cooldown` | Cooldown all heaters | Temperature (adjust foot) | clean OutlinedControl, `Intent.Warn` | (none assigned; label-only) | `R.string.temp_cooldown` | Warn | Button |
| `temp.presets` | Open temp preset picker | Temperature (adjust foot) | clean OutlinedControl, `Intent.Accent` | (none assigned; label-only) | `R.string.temp_presets` | Accent | Button |
| `temp.settings` | Open sensor-display picker | Temperature (monitoring foot) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.TempSettings` | (icon-only) | Accent | Button |

### Class: SelectorRow (+ presets: Sort, Filter, IncrementPicker, mode-select)

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `sort.<key>` (name/date/size/remaining) | Sort list by field (direction toggles) | Files (`Date`/`Size`), Spool (`name`/`date`/`remaining`) | `SortRow` preset (clean). Option glyphs ASSIGNED; **direction arrow rogue** | option ASSIGNED (`MatchCase`/`CalendarClock`/`Scale`/`LineWeight`); **direction arrow = ASK** (Part 3 §a) | (icon-only tiles) | selected=accent, inactive=Neutral | SelectorRow (sort preset) |
| `filter.<facet>` (type/color/mfg) | Open filter Field-takeover for facet | Spool | `FilterRow` preset (clean). Glyphs ASSIGNED | ASSIGNED (`Experiment`/`Palette`/`Storefront`) | (icon-only tiles) | active(≥1 set)=accent, else Neutral | SelectorRow (filter preset) |
| `stepper.increment_size` | Pick step increment magnitude | FineTune, (adjuster screens) | `IncrementPicker` preset (clean; selected=accentSoft fill) | (numeric tiles — no glyph) | (value tiles) | selected=accent, inactive=Neutral | SelectorRow (increment preset) |
| `move.mode` (touch/xy/z/microstep/bookmark) | Select Move sub-mode | Move (Field list) | `MoveRow` (custom ListRow wrapper) — **NOTE: ListRow-class, may be OUT (field list item)** → Part 3 §d | ASSIGNED (`MoveTouch`/`MoveXY`/`MoveZ`/`FineTune`/`SavedLocation`) | (label + icon) | accent | SelectorRow / ListRow (ambiguous) |
| `move.axis_select` (X/Y/Z) | Pick axis the ± pair drives | Move (Microstep sub-mode — IN: mode-select-ish) | **`AxisSelectChip` inline Box+border+clickable rogue → migrate to SelectorRow** | (text "X"/"Y"/"Z" — icon-never-twice text-label rule) | "X" / "Y" / "Z" | selected=accent, inactive=Neutral | SelectorRow |

### Class: StepperRow

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `stepper.decrease` | Decrement a value | FineTune (AdjusterPanel), Temperature (heater ±1), Move (microstep size + jog), Probe (step size), Babystep (PrintStatus) | AdjusterPanel/Temp/Probe-step = ASSIGNED `Decrease`; **Move = literal `"−"` text rogue (4 sites) → migrate**; Probe step uses raw `"remove"` ligature | ASSIGNED: `DinghyIcons.Decrease` | (icon-only) | **Accent**, but Probe step ± = Neutral → Part 3 §b | StepperRow |
| `stepper.increase` | Increment a value | (same as above) | AdjusterPanel/Temp/Probe-step = ASSIGNED `Increase`; **Move = literal `"+"` text rogue (4 sites) → migrate**; Probe step uses raw `"add"` ligature | ASSIGNED: `DinghyIcons.Increase` | (icon-only) | **Accent**, Probe step ± = Neutral → Part 3 §b | StepperRow |
| `babystep.cycle_step` | Cycle babystep step size | PrintStatus (BabystepRow) | **inline Box+border+clickable rogue → migrate to StepperRow value cell** | (value text) | (step value) | accentLine border | StepperRow (value cell) |

### Class: Toggle

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `console.filter.hide_temps` | Toggle hide temp messages | Console | clean OutlinedControl toggle (`selected` semantics) | ASSIGNED: `DinghyIcons.HideTemps` | (icon-only) | active=accent, inactive=Neutral | Toggle |
| `console.filter.hide_timelapse` | Toggle hide timelapse messages | Console | clean OutlinedControl toggle | ASSIGNED: `DinghyIcons.HideTimelapse` | (icon-only) | active=accent, inactive=Neutral | Toggle |
| `console.filter.hide_prompts` | Toggle hide prompt messages | Console | clean OutlinedControl toggle | ASSIGNED: `DinghyIcons.HidePrompts` | (icon-only) | active=accent, inactive=Neutral | Toggle |
| `output.on_off` | Digital output on/off pair | Outputs (OutputToggleControl) | clean OutlinedControl pair, `Intent.Accent`(active)/`Intent.Neutral` | (none — label "On"/"Off") | `R.string.output_on` / `output_off` | active=accent, inactive=Neutral | Toggle |
| `macros.show_hidden` | Toggle show hidden macros | Macros (manage mode) | clean OutlinedControl toggle | ASSIGNED: `DinghyIcons.Visibility` / `VisibilityOff` | `R.string.macros_foot_show_hidden` | active=accent, inactive=Neutral | Toggle |
| `temp.mode` (monitoring/adjust) | Toggle Monitoring ↔ Adjust | Temperature (foot) | clean OutlinedControl, `Intent.Accent` | ASSIGNED: `DinghyIcons.OutputHeater` (→Adjust) / `DinghyIcons.MonitorMode` (→Monitor) | (icon-only) | Accent | Toggle |
| `temp.trace.visibility` | Show/hide a temp trace on graph | Temperature (appearance popup) | clean OutlinedControl toggle | ASSIGNED: `DinghyIcons.Visibility` / `VisibilityOff` | (icon-only) | active=accent, inactive=Neutral | Toggle |
| `printers.secure` | Toggle wss/https (secure connection) | Printers (editor) | **`SecureToggleRow` custom pill rogue** → Part 3 §d (settings-class?) | (none — On/Off pill) | (label + On/Off) | accentLine when on | Toggle |
| `about.dev_widgets` | Enable developer widgets | About | **`DevEnableRow` custom pill rogue → migrate to Toggle** | (none — On/Off pill) | (label + On/Off) | accentLine when on | Toggle |
| `move.save.include_z` | Include Z in saved location | Move (save dialog) | **MUI `Checkbox` rogue → migrate to Toggle** | (none — checkbox) | (label "Include Z") | accent when checked | Toggle |

### Class: Scrubber

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `scrubber.output_value` | Drag-set output value (fan/servo/heater/PWM %, °, °C) | Outputs (FocusScrubberSurface), Temperature (heater coarse) | `Scrubber` (centralized 004 — no change; verify U-relative) | n/a | n/a | n/a (track=accent) | Scrubber |
| `scrubber.led_brightness` | Drag-set LED brightness | Outputs (FocusLedSurface) | `Scrubber` | n/a | n/a | n/a | Scrubber |

### Class: ColorSwatch

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `temp.trace.color` | Pick trace color (Colorful-8 pool) | Temperature (appearance popup) | **inline `Box`+background+border+clickable rogue (8 swatches) → migrate to ColorSwatch** | n/a (data-color carve-out) | n/a | selected=4dp border | ColorSwatch |
| `spool.filter.color` | Pick filament color filter | Spool (filter picker) | `ColorSwatchGrid` (in `SpoolPicker.kt`) — semi-clean; **consolidate with Temp onto one `ColorSwatch`** | n/a (data-color carve-out) | n/a | selected=border | ColorSwatch |

### Class: DockedEStop (cataloged only — not restyled)

| key | role | screens | current status | proposed icon | proposed label | intent | type |
|---|---|---|---|---|---|---|---|
| `printer.estop` | Emergency stop (tap→ConfirmGuard; long-press→panic) | All FocusFrame headers (21 routes) + shell fallback (Webcam, Theme) | centralized: `FocusFrame.kt` header morph + `FloatingEStop.kt` fallback. `Intent.Danger`, 0.7U | ASSIGNED: `DinghyIcons.StatusStop` | `R.string.cd_emergency_stop` | Danger | DockedEStop |

---

## Part 3 — Open decisions (the genuine asks)

### (a) Unassigned icons → **ASK** (Claude picks NONE; candidates are options for YOU)

1. **Sort-direction arrows** (`sort.<key>` ascending/descending indicator).
   Renders raw `arrow_upward` / `arrow_downward` via `MaterialSymbol` in `SortFilterControlRow.kt:160-161`
   — **NOT registered in `DinghyIcons`** (the code itself flags this as a TODO). Used by Files + Spool
   sort tiles. Needs a registered token pair.
   *Candidates from `img/material-icon-bucket.json` (owner picks — I am NOT choosing):*
   `arrow_upward` / `arrow_downward` (already in use), or `arrow_and_edge`. (The bucket also carries
   `clock_arrow_up`/`clock_arrow_down` but those read as date-specific, not generic direction.)

2. **Prompt Close glyph** (`prompt.close`).
   `PromptDialog.kt:170` uses raw `symbol = "close"` (a literal ligature string, not a registered
   token) and a literal `"Close"` label string. Needs a registered `DinghyIcons.Close` (or similar)
   + an `R.string`. This is the same "close" glyph the Spool-detail "close" question raised earlier
   ([[dinghy-visual-normalization-sweep]] — "Spool 'close' glyph (ASK OWNER)").
   *Candidate:* `close` (in use). Owner: confirm a token name + whether one shared close glyph covers
   both Prompt and any Spool detail-close.

> Everything else in Part 2 marked "(none assigned; label-only)" is a **labeled** ActionButton that
> needs NO icon (foot buttons may be text-only) — these are NOT asks. Only the two above genuinely
> lack an assigned glyph for a slot that needs one.

### (b) Same role, conflicting intent across screens (needs a ruling)

1. **`output.off`** — `Intent.Danger` (red) on the **Outputs** scrubber/LED surfaces, but `Intent.Warn`
   (amber) on the **Temperature** heater popup. Same semantic action (set output/heater to 0). Pick one:
   Danger reads "you're killing the output"; Warn reads "reversible but significant." My read: **Warn**
   is the better fit (turning a heater/fan off is reversible and routine), but you set the law.

2. **`stepper.decrease` / `stepper.increase` intent** — `Intent.Accent` everywhere (FineTune, Temp ±1,
   Move jog) **except** the **Probe step-size ±** (`ProbeCalibrateScreen.kt:276-292`) which is
   `Intent.Neutral`. The Probe step ± picks magnitude rather than acting on the printer, so Neutral is
   arguably correct (it's a selector, not a command). Ruling: keep steppers Accent app-wide, OR treat
   "step-size pickers" as `IncrementPicker`/SelectorRow (Neutral inactive) rather than StepperRow.
   (Leaning: the Probe step-size ± is really a **SelectorRow** increment, not a StepperRow — that
   resolves the conflict cleanly.)

### (c) Label / glyph wording inconsistencies

1. **`spool.scan` two glyphs for one role** — `DinghyIcons.QrCode` (Spool foot, Files SpoolWarning) vs
   `DinghyIcons.QrCodeScanner` (ActiveSpoolCard "Scan"). Same action (open QR scan). Pick one token for
   the role, or confirm the two are deliberately distinct (scanner-with-frame vs plain QR). Note the
   icon-never-twice-on-one-screen rule does NOT force a split here — they're on different screens.

2. **Literal label strings not yet in `R.string`** — several controls use inline literals instead of
   resources: Prompt `"Close"`, ActiveSpoolCard `"Scan"`/`"Change"`/`"Clear"`, ScanSurface
   `"Back"`/`"Use picker instead"`/`"Front cam"`/`"Rear cam"`, ScanConfirmCard `"Back"`/`"Set active"`,
   Move bookmark `"Move"`/`"Delete"`. Not a design ask — flagging for the implementation phase to route
   through `R.string` (a11y + i18n).

### (d) Genuinely ambiguous CLASS

1. **PrintersScreen — settings-menu or not?** The spec puts "the entire Settings menu" OUT, but
   PrintersScreen is **connection management** (Connect/Edit/Add printer, `SecureToggleRow` custom pill,
   Clear-key, Scan-mDNS). It is reached from the System page like Settings, and its toggle is a
   settings-style pill. **Recommendation: treat PrintersScreen as settings-class → OUT of this pass**
   (consistent with Settings/Theme being out), which also drops `printers.*` rows and `printers.secure`
   from scope. If you disagree, the `printers.*` ActionButtons are clean and only `SecureToggleRow`
   needs migration. **Your call.**

2. **`move.mode` rows (MoveRow)** — these are `MoveRow` (a custom ListRow wrapper) in the Move Field
   list. The spec excludes "field LIST ITEMS / ListRow" but INCLUDES "Move's mode selectors." A
   mode-select row that lives in the Field list straddles both. **Recommendation: the mode-select
   MoveRows are SelectorRow-class behavior wearing ListRow clothing** — treat them as in-scope
   selectors. But if you'd rather they stay as field list items (audited later), say so.

3. **`console.clear`, `console.execute` — DO NOT EXIST.** The plan's example key list mentioned them,
   but the as-built `ConsoleScreen.kt` has NO clear/execute buttons in scope — only Back + the three
   hide-filter toggles. (Console is a Views RecyclerView scrollback; gcode entry is elsewhere/absent.)
   Flagging so the absence is intentional, not an oversight.

### (e) Codex sanity-pass — vetted corrections (folded 2026-06-14)

A second-model pass flagged 7 items; after verifying each against source + the design law:

- **~~FOLDED — Move focus-body ± OUT.~~ SUPERSEDED by §(f)#7 (owner override 2026-06-14).** Codex's
  read (Move Microstep ± are protected focus-body) was REVERSED by the owner: the Microstep focus
  controls + the Move foot bar are **IN**; the **MoveRow mode-select list** is **OUT**. See §(f)#7.
- **FOLDED — `common.cancel` split.** A cancel that DISCARDS pending input is Danger (C7), not
  Accent. BedMesh save-name cancel (`BedMeshScreen.kt:501`, `Intent.Danger`) → new key
  `common.cancel.discard`. Plain dismiss (Prompt) stays `common.cancel`/Accent. Move save-dialog
  cancel: verify at impl whether it discards a pending save (→ discard/Danger) or is plain nav.
- **FOLDED — Probe step-size ± is a SelectorRow, not StepperRow.** It picks a magnitude (no motion)
  → reclassified to `calibration.probe.step_size` under SelectorRow (inactive = Neutral, valid per
  R18). This also resolves the §(b)#2 intent conflict.
- **FOLDED — missed in-scope tiles.** PrintStatus `ShortcutRow` (`PrintStatusField.kt:~702-714`)
  carries Tune + other shortcut tiles beyond System — add as in-scope field rogues to migrate
  (`printstatus.shortcut.<x>`; enumerate at the Phase-7 sweep).
- **REJECTED — selector inactive = Neutral is NOT a violation.** Codex read Neutral as toggle-only;
  THEMING **R18 explicitly sanctions** `Intent.Neutral` for "unselected step tiles, filter options."
  Sort/filter/increment inactive options keeping Neutral is LAW, not a bug. No change.
- **JUDGMENT (kept) — `printstatus.preheat` + `files…print_anyway` = Warn.** Codex argued
  Go-with-caution. Kept Warn: preheating/heating is R5's own listed Warn example ("heat filament");
  print-anyway overrides a no-spool guard ("proceed at peril"). Defensible; owner may override.
- **OWNER CALL (unchanged) — `output.off` intent (§b#1).** Codex agrees it's a conflict but argues
  Warn is wrong too (off is reversible → not destructive-in-process). The field is genuinely open:
  Danger / Warn / a routine-neutral treatment. Owner's call.
- **CONFIRMED — all three §(d) class calls** (Printers OUT, MoveRow IN, console.clear/execute absent).
- **Borderline (not cataloged):** Webcam tap-to-cycle (`WebcamScreen.kt:251`) + Spool measure-opener
  clickable `FillMeter` (`SpoolScreen.kt:873`) are media/content GESTURES, not button-class controls
  → treated as per-page affordances (like bed-area), not catalog entries. Flag if you want them IN.

### (f) Owner rulings — LOCKED 2026-06-14

Final. Override any conflicting row/section above (incl. §e where noted).

1. **Sort-direction arrows** → register a token pair: `arrow_drop_up` (asc) / `arrow_drop_down` (desc),
   owner-picked. Drives the SortRow direction indicator (replaces the raw `arrow_upward/_downward`).
2. **Close glyph** → register `DinghyIcons.Close = "close"`; ONE shared token covers Prompt close +
   any Spool detail-close. Replaces the raw `symbol = "close"` literal.
3. **`output.off` = `Intent.Warn`** app-wide (the Outputs `Intent.Danger` becomes Warn too). §(b)#1 closed.
4. **Probe step-size ±** → SelectorRow (`calibration.probe.step_size`), NOT StepperRow. §(b)#2 closed.
5. **`spool.scan`** → ONE token, `DinghyIcons.QrCode` (`qr_code`) only. Drop `QrCodeScanner` for this role.
6. **Printers `SecureToggleRow` pill → OUT** this pass (ignore it). Interpretation: the rest of
   PrintersScreen's ActionButtons (Connect/Edit/Add/Delete/Clear-key/Scan/Back) stay **IN** as
   standard ActionButtons — only the pill is deferred. (Supersedes §(d)#1's "whole screen OUT".)
7. **Move scope (owner-refined 2026-06-14; overrides §(e) Move bullet + §(d)#2). Only the pages with
   GENERAL button groups get the treatment — Microstep + Bookmark + Add-bookmark; everything else is a
   SPECIALIZED control left as-is.**
   - **IN (general button groups → normalize + bottom-dock):**
     - **Microstep** focus — **step-size ±** (→ SelectorRow-style increment w/ `Decrease`/`Increase`
       icons, kills literal `−`/`+`), **jog ±-pair** (→ **`Intent.Go`** per R19, motion is Move's
       purpose; kills literal `−`/`+`), **`AxisSelectChip`** (→ SelectorRow, X/Y/Z text labels).
     - **Bookmark** page — Move/Delete button group.
     - **Add-bookmark (SaveDialog)** — Cancel/Save button group + the include-Z toggle.
     - The Move **foot bar** (Back/Disable/HomeAll/Save).
   - **OUT (specialized — keep own layout, do NOT touch):** the **TouchMove bed map**, the **XY
     position scrubbers**, the **Z position scrubber** (height-relative — vertical position IS the
     data), and the **MoveRow mode-select list** ("the list itself does not" — field-list audit later).

Rogue / migration-target sites verified at HEAD:
- Move literal-`"−"`/`"+"` steppers: `ui/move/MoveScreen.kt:520, 534, 546, 554` (4 sites, `Intent.Accent`).
- Move `AxisSelectChip` (inline Box rogue): `ui/move/MoveScreen.kt:570-578` (def @934).
- Move save-dialog MUI `Checkbox`: `ui/move/MoveScreen.kt:671-679`.
- Macros numeric-param raw `BasicTextField`: `ui/macros/BookmarkedMacrosScreen.kt:587-627` (keyboard carve-out; not a button — note only).
- BedMesh `ScaleToggle` (inline Row+border+clickable): `ui/calibration/BedMeshScreen.kt:634-663` — bed-scale mode; **bed-area, likely OUT** (per-page rule).
- Probe step-size ± raw `"add"`/`"remove"`: `ui/calibration/ProbeCalibrateScreen.kt:276-292` (NON-bed, in scope).
- Probe Z-nudge `arrow_upward`/`arrow_downward`: `ui/calibration/ProbeCalibrateScreen.kt:246-269` — **bed-area toolhead jog, OUT**.
- Temp inline color swatches (8×): `ui/temperature/TemperatureScreen.kt:857-871`.
- Spool `ColorSwatchGrid`: `ui/spool/SpoolPicker.kt` (consumed `SpoolScreen.kt:574`).
- Sort-direction raw arrows: `designsystem/components/SortFilterControlRow.kt:156-163`.
- Prompt close raw symbol + footer-button rogue: `ui/prompt/PromptDialog.kt:170`; `ui/prompt/PromptContentItems.kt:175-200`.
- Printers `SecureToggleRow` custom pill: `ui/screen/PrintersScreen.kt:605-615` (render 741-796).
- About `DevEnableRow` custom pill: `ui/screen/AboutScreen.kt:213-257`.
- PrintStatus inline launcher/shortcut/babystep Boxes: `ui/printstatus/PrintStatusField.kt:566-597, 630-675, 741-770`.
- DockedEStop: `designsystem/components/FocusFrame.kt:236-245` (header morph) + `designsystem/components/FloatingEStop.kt:60-72` (fallback).
</content>
</invoke>
