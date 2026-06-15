# Control / Button Baseline Audit — session handoff (2026-06-15)

**Branch:** `control-baseline-audit` (pushed to `origin` — 8 commits). NOT merged to master.
**Flow:** superpowers brainstorm → writing-plans → subagent-driven-development (GSD OFF).

## The three governing docs (read these first to resume)
1. `.planning/notes/2026-06-14-control-baseline-audit-design.md` — the approved SPEC (scope, ControlSpec
   architecture, state/a11y schema, 1U + U-relative spacing, **bottom-dock law**, reset/revert).
2. `.planning/notes/2026-06-14-control-baseline-audit-plan.md` — the PLAN (reduction methodology + Phase 0
   + the Phase 1–9 implementation roadmap).
3. `.planning/notes/2026-06-14-control-master-list.md` — **the owner-LOCKED master list** (44 named
   controls + icon/label/intent mapping; **Part 3 §(a)–(f) = the owner rulings**, incl. §(f) icons/intents
   and the Move scope, and the **Focus-body BOTTOM-DOCK law** in Part 1).

## DONE (committed on the branch, in order)
- `7b9ed04` docs: spec + plan + master list.
- **Phase 0** — master list (175 sites → 44 named controls; 2 Codex passes; owner gate cleared & locked).
- **Phase 1** `d26109d` — U-fraction spacing tokens `gapS`/`gapM`/`padFloat` (`designsystem/layout/Spacing.kt`); R13 reconciled in THEMING/COMPONENTS.
- **Phase 2** `81c8cc8` — **ControlSpec catalog** (`control/ControlSpecs.kt`, presentation-only, composes R.string+DinghyIcons+CommandRegistry; 12 seed entries) + `OutlinedControl(spec=…)` overload + `ControlCatalogDriftTest` + 3 owner-picked icons (`DinghyIcons.SortAsc`=arrow_drop_up, `SortDesc`=arrow_drop_down, `Close`=close; added to `tools/verify_ligatures.py`).
- `cd4fd57` docs: **bottom-dock law** (general focus button groups dock to bottom; SPECIALIZED controls — bed-area, Z slider, XY scrubbers, ColorWheel — exempt; Move: Microstep+Bookmark+Add-bookmark IN, TouchMove/XY/Z OUT).
- **Phase 3** `bdb2985` + `02553cb` — **SelectorRow primitive**; SortRow/FilterRow/IncrementPicker are thin presets; sort-direction caret → SortAsc/SortDesc, sized `uDp*0.5` (owner: was a dot). Owner UAT ✓.
- **Phase 4** `4915408` — **StepperRow class**; AdjusterPanel + Scrubber ± rows delegate to it (no visual change); Move Microstep step-size cycler + jog ± (→ **Go** intent) via StepperRow, axis-select → `AxisSelectorRow`, Microstep + Add-bookmark groups **bottom-docked**. Owner UAT ✓. `OldMoveScreen` NOT yet deleted (Phase 9).
- **Height uniformity** `6b6b1b18` — **THE single `Modifier.controlHeight(uDp)` rule** (`Spacing.kt`) = exact `height(maxOf(uDp,64dp))`; FootButtonBar/SelectorRow/StepperRow all route through it. FocusFrame now **provides LocalUnitDp** → standalone focus buttons are true 1U. Stripped the `fillMaxHeight` SelectorRow wrapper (the ~7px-taller offender). Measured on-device: sort tile & foot button both 150px, pixel-aligned. Exception: Output On/Off toggle opts out (grows past 1U).

## LEFT TO DO (author each phase's TDD plan via writing-plans, then subagent-driven)
- **Phase 4b — calibration ± steppers.** `BedMesh/Tilt/ScrewsTilt/ProbeCalibrate` NON-bed ± steppers
  (literal `"−"/"+"/"Z−"` text) → `StepperRow`/`Decrease`/`Increase`. **Probe step-size ± → SelectorRow**
  (§f#4). Bed-area jog (`≺≻∧∨`, probe Z-nudge) stays per-page (OUT). Add a11y `contentDescription`.
- **Phase 5 — Toggle.** In-scope toggles → SelectorRow presets; kill rogue MUI `Checkbox` (Move
  save-dialog Z-include) + Temperature visibility. (Settings/Theme/Printers pill = OUT.)
- **Phase 6 — ColorSwatch.** Consolidate Temperature + Spool inline color-swatch `Box`es into one
  `ColorSwatch` (ThemeEditor swatches OUT — settings rework owns them).
- **Phase 7 — ActionButton catalog migration + a11y sweep.** Migrate named foot/focus-foot buttons to
  `OutlinedControl(spec = ControlSpecs.X)`; expand `ControlSpecs` from 12 seeds per the master list;
  add the dozens of missing `contentDescription`s the inventory flagged (the a11y backlog).
- **Phase 8 — reset/revert cull.** Make the FocusFrame header trailing-revert the app-wide "reset to
  default"; cull redundant foot Reset buttons where SAFE (FineTune "Reset all", adjusters). Calibration
  Resets that re-arm a SEQUENCE stay (verify per-screen).
- **Phase 9 — delete `OldMoveScreen.kt`** + its debug-Gallery entry + test refs (Move Hub is committed).
- **Doc/test debt:** broaden `LAYOUT.md` for the generalized bottom-dock law; `ControlCatalogDriftTest`
  grows as ControlSpecs grows.

## Operating conventions established this session (REUSE)
- **Workflow:** brainstorm/spec/plan/master-list are LOCKED; just execute remaining phases.
  subagent-driven-development — dispatch one capable implementer per phase with a thorough prompt that
  (a) points at the 3 governing docs, (b) gives exact deliverables/TDD steps, (c) demands explicit-path
  staging + no push. Controller verifies the diff + does on-device UAT.
- **Build:** Windows-side — `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat <args>"`, pipe `| tr -d '\r'`.
  **Use UNQUOTED `--tests *Glob` patterns** — escaped-quote form mangles through cmd.exe → false "No tests found".
- **Install/UAT:** split-ABI debug APK at `app/build/outputs/apk/debug/`; install matching slice to BOTH
  devices: flox (armeabi-v7a, `0a64b42e`) + moto (arm64-v8a, `ZY22LBDRM9`). `-r` install bounces the
  app to the launcher — the owner must reopen + navigate. Verify `assembleDebug UP-TO-DATE` on a clean
  tree to confirm the APK == HEAD before install (stale-APK trap).
- **On-device MEASUREMENT (this session's big lesson):** when the owner reports a sizing nit, do NOT
  eyeball — `adb -s <id> exec-out screencap -p > /tmp/x.png`, then measure with PIL (numpy). Detect the
  accent-yellow border bounding box for clean button extents; draw red reference lines and `SendUserFile`
  the annotated crop as proof. The owner is sharp on px — trust the report, measure to confirm/fix.
- **HARD RULES:** never pick an icon — ASK (owner picks glyphs). Stage explicit paths, never `git add -A`.
  Push uses **Windows git** (`/mnt/c/Program Files/Git/cmd/git.exe`) — Linux git fails chmod on /mnt/e.

## Key technical landmines learned
- `OutlinedControl` height = `(LocalUnitDp.current ?: 64dp).coerceAtLeast(64dp)`. A control is 1U ONLY
  where LocalUnitDp is provided; else flat 64dp. **`controlHeight(uDp)` is now the single rule** — use it.
- `fillMaxHeight` on a control measured ~7px TALLER than an intrinsic `heightIn(min)` sibling at the same
  uDp — prefer the intrinsic-min form (what `OutlinedControl` does internally).
- `verify_ligatures.py` only VALIDATES against the retained v2.944 font (no subsetting) — adding an icon
  = add the DinghyIcon token + the ligature name to `NEEDED` + run the gate (exit 0 / missing []).
- `DinghyIcons.kt` is CRLF in-repo ([[dinghy-crlf-commit-trap]]) — keep diffs to the added lines.
