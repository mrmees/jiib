# Conformance Matrix — Visual Normalization Sweep, Step 2

**Scored:** 2026-06-12 against the reconciled law (`54259f3`, R1–R16) via 6 parallel audit agents
(per-screen file reads + global greps). Line numbers are evidence pointers — executors re-verify.
**This is the doc Matthew rules from: one verdict (REBUILD / POLISH / LEAVE) per screen.**

## 1. The verdict table (owner rules per row)

| Screen | Structure (U/region/rows) | Intent repaint | Type flags | Other flags | Agent rec | My rec |
|---|---|---|---|---|---|---|
| **PrintStatus** | Standby = redesigned ✓; **Printing/Paused/Terminal still live-gutter** (R1 migration); Focus all-custom | gutter buttons (Pause→caution, Dismiss→accent), launcher tiles hair→accent | 4× 17-18sp | 15 raw strings (control labels); 14dp spacing | mixed | **REBUILD** (the 3 gutter modes → foot pattern; keep Standby + Focus) |
| Temperature | clean ✓ | Presets cond.→accent; Back→accent | 2× 18sp | — | POLISH | **POLISH** |
| Extrude | PresetRow = local row (C-F2); BigCommand raw symbols | Back→accent ×2; **Load accent→caution** | 18sp preset | 3 raw glyph sites (nozzle painterResource sanctioned) | REBUILD | **REBUILD-lite** (PresetRow→ListRow + registry icons + repaint) |
| FineTune | clean ✓ | Back→accent | 18sp labels | — | POLISH | **POLISH** |
| Outputs | clean except **LedBrightnessControl = live fill-bar scrubber** (C-B1) | Back→accent ×3; Off naming Danger/Stop | 18sp labels | scrubber migration to 004 | REBUILD | **REBUILD-lite** (scrubber swap + repaint) |
| Files | FileStatRow local (detail-card rows) | Back→accent; **Print accent→GO** | ok | — | POLISH | **POLISH** |
| Console | Views scrollback sanctioned (§8) ✓ | Back→accent + **Back is LAST → FIRST** | ok | — | REBUILD | **POLISH** (one bar reorder + intent) |
| Macros | clean ✓; Back FIRST ✓ | Back→accent ×3; **Execute accent→GO** | ok | — | POLISH | **POLISH** |
| Webcam | cam-picker = deliberate card pattern (PASS) | Back→accent | 16sp cam names→20 | — | POLISH | **POLISH** |
| Spool (north star) | exemplary ✓ (17 local helpers = conformant slot-fillers) | **Load accent→GO, Unload neutral→GO** | ok | 1 "close" symbol TODO (owner icon ask) | POLISH | **POLISH** |
| Move | spatial jog grid (sanctioned); **Back 3rd → FIRST** | Home All→? (Q-B); Disable ✓ stop; Back→accent | **13sp FAIL**, 14sp ×2 | 3 raw symbols; 1 fixed 40dp icon | REBUILD | **POLISH+** (reorder + repaint + sizes; grid itself stays) |
| BedMesh | clean ✓ | Back→accent ×3 + **Back LAST → FIRST ×3**; Save→GO ×2 | ok (14sp trailing) | 1 raw "expand" symbol; 4/16dp spacing | POLISH | **POLISH** |
| CalibrationHub | clean ✓ | Back→accent; **Open accent→GO** | ok | — | POLISH | **POLISH** |
| ProbeCalibrate | ProbeIconButton local (sanctioned 18.1-03 pattern, consolidation candidate) | Back→accent ×2 + position; SAVE_CONFIG Warn ✓(=caution) | ok | 4/16dp spacing | POLISH | **POLISH** |
| ScrewsTilt | clean ✓ | Back→accent + position; Run-again→accent | **14sp trailing → 15+** | — | POLISH | **POLISH** |
| Tilt | clean ✓ | Back→accent + position; Run-again→accent | ok | 6dp spacing | POLISH | **POLISH** |
| Settings | C6 dense, All-1U ✓ | Back→accent | 17sp labels→20 | — | LEAVE | **POLISH-lite** |
| SystemPage | PowerStubRow local | Back→accent | 17sp→20 | fixed 22dp icons → fsSp(22) | POLISH | **POLISH** |
| SystemInfo | clean ✓ | Back→accent | 17sp→20 | — | POLISH | **POLISH** |
| About | **InfoRow no 1U floor; DevEnableRow+InfoRow local rows, anatomy broken** | Back→accent | 17sp→20 | — | REBUILD | **REBUILD-lite** (rows → ListRow) |
| Printers | SecureToggleRow+DiscoveredPrinterRow local; **Back LAST → FIRST** | intents mostly ✓; Back→accent | 17sp→20 | one 14dp pad ✓(=padFloat) | REBUILD | **REBUILD-lite** (rows + bar reorder) |
| Splash | structure sound (hard-override) ✓ | intents ✓ as-is (Edit connection→accent) | 16sp reason text is LEGAL (≥15) | **13 raw string literals** | REBUILD | **POLISH** (string extraction) |
| Theme | wrapper only | — | — | — | LEAVE | **LEAVE** |
| ThemeEditor | FFG/C6-exempt surface (documented) | Clear Warn→**stop** ×2 | 18sp segment labels | 4 raw symbols; `Color(0xFF101010/F5F5F5)` ink → tokens; 16dp pads | POLISH | **POLISH** |

## 2. Sweep-wide repaint roll-ups (apply across all screens)

1. **Back: → `Intent.Accent` + FIRST position, app-wide.** Every screen audited has Back=Neutral;
   position violations: Console, Move, BedMesh×3, ProbeCalibrate, ScrewsTilt, Tilt, Printers.
2. **Expected action → GO:** Print (Files), Execute (Macros), Load+Unload (Spool), Open (CalHub),
   Save/Save-confirm (BedMesh), Done (pickers, already go ✓). Owner confirms per-screen during fix.
3. **Type ramp:** list/button labels 17–18sp → **20sp** on ~12 screens (the single biggest visual
   change of the sweep). Sub-15 FAILs to fix: Move 13sp `home` label + 14sp `mm`; ScrewsTilt 14sp
   turn instruction; TokenTextField 13sp hint; SortFilterControlRow 14sp icon-size const (verify);
   SpoolScreen 11sp (DEBUG-only — exempt if release-stripped).
4. **Icon registry:** 16 raw `MaterialSymbol(` sites → DinghyIconView/registry (Move ×3, Extrude ×2,
   BedMesh ×1, ThemeEditor ×4 [status-shape-on-swatch — possible carve-out, owner call],
   ActiveSpoolCard ×4, Scan ×4). ⚠ icon law: any NEW glyph needed = ASK Matthew. 1 fixed
   `40.dp` icon (Move:490) → tier idiom. SystemPage fixed 22dp → fsSp(22).
5. **Scrubber:** LedBrightnessControl (Outputs LED) = the only live fill-bar → migrate to 004
   ringed-thumb. Delete `ScrubberPage.kt` (zero production call-sites — confirmed).
6. **Strings (R14):** Splash 13 literals + PrintStatus 15 control labels = the bulk; rest of app
   nearly clean. Extract via stringResource.
7. **Raw colors:** SpoolPicker preset wheel hexes + ThemeEditor ink pair → tokens (or documented
   carve-out). Bench/preview hexes exempt.
8. **Spacing:** stray 4/6/16dp → gapS/gapM/padFloat on ~6 screens.
9. **Local-row consolidation:** PresetRow (Extrude), FileStatRow (Files), DevEnableRow+InfoRow
   (About), SecureToggleRow+DiscoveredPrinterRow (Printers), PowerStubRow (SystemPage) → ListRow
   or a shared detail-row primitive. (Spool's helpers + Webcam picker + Move grid = sanctioned.)
10. **PrintStatus gutter migration (R1):** Printing/Paused/Terminal modes → foot pattern +
    FloatingEStop; then delete `ScreenScaffold.gutter` slot + `PrintStatusGutter.kt` + stale KDoc.
11. **oklch caution clamp (R10)** — generator fix, independent of screens.

## 3. Open questions for the verdict session

- **Q-A — `Intent.Neutral` for INACTIVE TOGGLE STATES.** R5 retired neutral as an ACTION intent,
  but ~49 sites use Neutral correctly as the *inactive* half of toggle patterns (IncrementPicker
  steps, SortFilter option tiles, show-hidden, ConfirmGuard dismiss). Ruling needed: retain
  Neutral as the inactive-state style (recommended — it reads as "not selected"), or restyle.
- **Q-B — Motion-command intent class.** Jog arrows / Home All / Z moves: warning
  (hazard-in-process, per THEMING worked example) vs go (expected action of Move screen)?
  Agents split. One ruling covers Move + Probe + Extrude big-commands.
- **Q-C — ThemeEditor status-shape-on-swatch raw symbols:** carve-out (they decorate DATA colors)
  or route via registry?

## RULINGS — Verdict session (owner, 2026-06-12)

- **R17 — Verdicts accepted, REBUILDs DEFERRED.** The sweep is POLISH-ONLY for now: the 5
  structural items (PrintStatus gutter migration, Extrude PresetRow, Outputs LedBrightnessControl
  → 004 scrubber, About rows, Printers rows/bar) move to a LATER structural slate. Those 5
  screens still receive their NON-structural polish items (Back, type ramp, intents, spacing,
  strings) in the wide pass. ScrubberPage.kt deletion still fine (dead code, no structure).
- **R18 — `Intent.Neutral` SURVIVES as the inactive-toggle-state style ONLY** (the unselected
  half of step pickers / filter tiles / show-hidden). Retired for action buttons. One law
  sentence added to THEMING.
- **R19 — Motion = GO when motion is the screen's/state's purpose** (jog arrows, Home All, Z
  moves on Move; Home-All gates on calibration screens). Amber/warning reserves for genuinely
  hazardous motion-adjacent acts: force-move, load/heat filament, resets. REVISES C1 + the
  THEMING worked examples (which said jog = warning).
- **R20 — ThemeEditor swatch status-glyphs route via the registry** (DinghyIconView with
  StatusStop/Warning, tinted on the swatch) — no carve-out.
- **Pilot-first execution:** owner wants 1–2 pilot screens fully polished and reviewed on-device
  BEFORE the wide pass.
- **R25 (post-pilot, owner): pilot APPROVED → wide pass GO, but Move and Extrude are OMITTED
  ENTIRELY** — both get their own rework later (they were REBUILD-flagged anyway). Wide-pass
  scope = the list/button system + intents + type + icons + strings on the remaining screens.

## 4. Focus-treatment inventory (input to the future Focus-standardization pass)

| Candidate archetype | Screens using it |
|---|---|
| **DetailCard detail** (selected-item pane) | Spool, CalibrationHub, Printers, Temperature (adjuster-in-card), FineTune (adjuster-in-card) |
| **Spatial visualization** (custom canvas/diagram) | BedMesh heatmap, ScrewsTilt bed map, Tilt icon-status, Move jog pad |
| **Progress ring + preview fill** | PrintStatus Printing/Paused (dim+pause-glyph variant) |
| **Hero backdrop + glance list** | PrintStatus Standby |
| **Result thumbnail card** | PrintStatus Terminal |
| **Image-backed info card** | Files (future-print card) |
| **Native media surface** | Webcam |
| **Graph host** | Temperature (default mode) |
| **No Focus (Field-only)** | Console, Macros, Settings, SystemInfo, About, Splash(centered) |

Nine distinct treatments across 24 screens — the standardization pass has real material.

## EXECUTION STATUS (2026-06-12, commits `d6cf108`..`82beb1d`)

**DONE (pilot + wide pass):** ListRow-owned anatomy + ListRowLabel (20sp SemiBold) + ListRowIcon
(0.6U, R23) + OutlinedControl glyphs 0.6U via LocalUnitDp (R24) + FootButtonBar-owned gapS padding
(R21). Back = FIRST + accent on every audited screen; expected→go repaints (Print, Execute,
Load/Unload, Open, Save, Calibrate, Home-gates, Run, Start, Retry/Setup); Preheat→warn; type ramp
(labels→20sp, sub-15 fixed incl. TokenTextField); Splash 13 strings extracted; ThemeEditor Clear→stop
+ swatch glyphs registry-routed (R20) + pads→gapM; spool-card literal icons registry-routed
(+QrCodeScanner token); dead ScrubberPage composable + gallery demo deleted (ScrubberControl stays
live until the 004 migration); home-standby U hoisted to screen root (C-U1b).

**OMITTED (owner R25):** Move + Extrude — full rework later.
**REMAINING (structural slate / follow-ups):** PrintStatus gutter-mode migration (+ its 15
control-label strings — they live in PrintStatusControlModel, needs @StringRes restructure);
LedBrightnessControl + ScrubberControl → 004 ringed-thumb; About/Printers local-row consolidation;
dynamic symbol-param icon sites (ActiveSpoolCard ×2, ScanSurface, ScanConfirmCard); oklch
caution-reads-red verification/clamp (R10 — needs a focused session, color.js parity risk); Spool
"close" symbol TODO (NEEDS OWNER GLYPH CHOICE — icon law); Focus standardization pass (owner);
@Preview/golden backfill → Ship (R14).

## 5. Suggested execution order (post-verdicts)

1. Sweep-wide mechanical roll-ups (#1 Back, #3 type ramp, #8 spacing) — touch every screen once.
2. Intent repaints (#2, #4 icons) — per-screen, owner spot-checks colors on flox.
3. Structural items (#5 scrubber, #9 rows, #10 PrintStatus gutter) — biggest, last.
4. #6 strings + #7 colors + #11 oklch alongside.
5. Final: full flox walk against the checklist.
