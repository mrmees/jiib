# Whole-App UI Review — jiib (Dinghy Display)

**Audited:** 2026-06-08
**Baseline:** `docs/ui_design/` UI LAW (CLAUDE.md philosophy, LAYOUT.md Focus/Field/Gutter grammar, THEMING.md token + intent law, the 10 hi-fi mockups in `docs/ui_design/images/`). Abstract 6-pillar standards applied where LAW is silent.
**Method:** Code-only audit (native Android app — no web dev server; no on-device screenshots captured this pass). Visual judgment is reconstructed from source + the canonical mockups.
**Scope:** WHOLE app — every screen under `app/src/main/java/works/mees/dinghy/ui/` + `designsystem/` + `render/`. Builds on `.planning/codebase/CONVENTIONS.md` ("Inconsistencies and Conformance Gaps") and `.planning/codebase/CONCERNS.md` (perf — NOT re-derived here).
**Downstream consumers:** Phase 23 (Interaction Coherence & Page Overhauls) reads the Per-Page Verdict Table + Interaction-Inconsistency Catalog; Phase 24 (Touch-Target / Scaling / Rotation Sweep) reads the Conformance-Gap List.

---

## Pillar Scores (whole-app)

| Pillar | Score | Key Finding |
|--------|-------|-------------|
| 1. Copywriting | 4/4 | Empty/error states are specific and human across the app; one stray "coming soon" placeholder; i18n only half-externalized (cosmetic, not user-visible). |
| 2. Visuals | 3/4 | Strong outline-led language and value-on-glyph hierarchy, but the icon system is split — 11 screens still use raw `MaterialSymbol` vs 9 on `DinghyIconView`. |
| 3. Color | 3/4 | Token discipline is near-total (THEME-01 essentially clean; carve-outs all sanctioned), but a concrete intent-vs-mockup divergence exists (Move "Disable" red where the mockup is amber) plus 4 duplicated `intentColor()` maps inviting drift. |
| 4. Typography | 4/4 | `fsSp(base, t.fs)` is used universally — ZERO hardcoded `.sp` font sizes app-wide; Geist/GeistMono split honored; metadata 15sp floor respected. |
| 5. Spacing | 3/4 | Ratio-only region sizing via `ScreenScaffold` is disciplined; fixed dp is confined to decorative spacers + the sanctioned 64dp floor. The App Drawer's fixed 4-column grid is the one structural spacing regression. |
| 6. Experience Design | 3/4 | Loading/error/empty/disabled/confirm states are thorough; the single full-screen `ConfirmGuard` is a model safety gate. Dragged down by the App-Drawer density regression and the deferred Files-delete-during-print bug. |

**Overall: 20/24**

> Adversarial note: this is a genuinely well-built app whose 6-pillar floor is high *because the design-system primitives (`ScreenScaffold`, `OutlinedControl`, `ScrubberPage`, `ConfirmGuard`, `DinghyTheme`) are excellent and widely reused*. The 20/24 reflects real cross-cutting **inconsistency** debt (split icon system, bespoke buttons, intent divergences, drawer density) — not broken screens. Phase 23/24 are coherence sweeps, not rescues. Scores were NOT averaged up: Pillars 2/3/5/6 each sit at 3 on the strength of specific named defects below.

---

## Top Priority Fixes (ranked by user-visible impact)

1. **App Drawer is a fixed 4-column grid in BOTH orientations** (`ui/shell/AppDrawer.kt:110` `GridCells.Fixed(4)`) — the mockup `02-app-drawer.png` is 2-col portrait / 3-col landscape with large tiles; the tile count has grown ~8 → ~19, so in portrait the user gets 4 columns of tiny tiles. This is the app's ONE navigation surface and it diverges hardest from its own LAW mockup. Fix: orientation-aware column count (`GridCells.Adaptive(minSize)` or `BoxWithConstraints` → 2/3/4 by width) so tiles keep mockup-scale touch targets.
2. **Move "Disable" gutter button is red `Intent.Danger`, but the mockup + this file's own KDoc say amber `Intent.Warn`** (`ui/move/MoveScreen.kt:220` code vs `:92` doc vs `04-move.png`). The KDoc and the code contradict each other. Fix: pick one — the mockup and THEMING worked-example both say amber proceed-at-peril (M84 is reversible by re-homing, not destructive). Reconcile to `Intent.Warn`.
3. **Icon system is split app-wide** — 11 screens render raw `MaterialSymbol(name, …)` with literal ligature strings; 9 use the `DinghyIconView`/`DinghyIcons` registry. Pre-18.1 screens (Move, Console, Extrude, Calibration cluster, Files, Macros, AppDrawer, Spool sub-pages) never migrated (D-03 "Phase-22 backfill"). User-visible risk: a glyph drift or theme/registry change won't propagate to half the app. Fix: complete the D-03 backfill onto `DinghyIconView`.
4. **Four duplicated `intentColor()` maps** re-implement `OutlinedControl`'s private `Intent.outlineColor` (`CalibrationHubScreen.kt:202`, `FilesScreen.kt:524`, `MacroExecutionPopup.kt:352`, `OutputsScreen.kt:230`) — these exist only because those screens hand-roll `Box+border` buttons instead of `OutlinedControl`. Drift hazard: a future intent-color change must be made in 5 places. Fix: expose `Intent.outlineColor` as `internal`, delete the copies, migrate the bespoke buttons to `OutlinedControl`.
5. **Calibration cluster bypasses `OutlinedControl` entirely** (`CalibrationHubScreen`, `ProbeCalibrate`, `BedMesh`, `ScrewsTilt`, `Tilt`) with local `HubActionControl`/`CalibActionControl` clones. They honor the 64dp floor + tokens but are a parallel button implementation — the largest cohesion gap of any single feature area.
6. **`stringResource` externalization is half-done** — 14 screen files use string resources, ~18 still inline hardcoded English (`text = "Disable"`, `"Console is quiet"`, etc.). Not user-visible today (single-locale), but an inconsistent convention that complicates any future i18n and is a coherence smell. Lower priority than the above.

---

## PER-PAGE VERDICT TABLE (the Phase-23 page list)

Verdict legend: **OVERHAUL** = major rework needed (structural layout / interaction-model divergence). **POLISH** = minor conformance fixes (intent color, icon migration, string externalization). **PASS** = conformant, leave alone.

| Screen / surface | File | Verdict | One-line reason |
|---|---|---|---|
| **App Drawer** | `ui/shell/AppDrawer.kt` | **OVERHAUL** | Fixed 4-col grid both orientations + ~19 tiles vs mockup 2/3-col large tiles → tiny portrait targets; the app's only nav surface diverging hardest from its own LAW. |
| **Calibration Hub** | `ui/calibration/CalibrationHubScreen.kt` | **OVERHAUL** | Bespoke `HubActionControl` + duplicated `intentColor` + raw `MaterialSymbol`; whole cluster is a parallel button/icon implementation. |
| **Screws Tilt** | `ui/calibration/ScrewsTiltScreen.kt` | **OVERHAUL** | Bespoke buttons, raw `MaterialSymbol`, owner-authored spatial layout never reconciled to `OutlinedControl`/`DinghyIconView`. |
| **Bed Mesh** | `ui/calibration/BedMeshScreen.kt` | **OVERHAUL** | Same cluster debt (6 bespoke controls, raw symbols, per-call `SimpleDateFormat`); heatmap host is fine but the chrome diverges. |
| **Tilt (Z-tilt/QGL)** | `ui/calibration/TiltScreen.kt` | **OVERHAUL** | Same calibration-cluster bespoke-button/icon debt. |
| **Extrude** | `ui/extrude/ExtrudeScreen.kt` | **POLISH** | Conformant intents + numpad, but raw `MaterialSymbol` + bespoke `FieldButton` + the live "Spoolman integration coming soon" placeholder + inline strings. |
| **Move** | `ui/move/MoveScreen.kt` | **POLISH** | Excellent jog-pad/force-move/value-on-glyph, but Disable intent red-vs-amber divergence + raw `MaterialSymbol` + inline strings. |
| **Console** | `ui/console/ConsoleScreen.kt` | **POLISH** | Solid read-only list + filters, but a 4-cell gutter (3 filters + Back) crowds the row; bespoke `FilterToggle`/`BackControl`; raw `MaterialSymbol`; inline strings. |
| **Files** | `ui/files/FilesScreen.kt` | **POLISH** | Views-in-Compose list works; but duplicated `intentColor`, raw `MaterialSymbol`, and the deferred delete-during-print bug (CONCERNS.md) live here. |
| **Macros (System/Bookmarked)** | `ui/macros/SystemMacrosScreen.kt`, `BookmarkedMacrosScreen.kt` | **POLISH** | Functional list; raw `MaterialSymbol`, inline strings; verify ≥64dp on macro rows. |
| **Macro Execution Popup** | `ui/macros/MacroExecutionPopup.kt` | **POLISH** | Duplicated `intentColor`, raw `MaterialSymbol`; param-entry interaction differs from the app's numpad/scrubber idiom — confirm coherence. |
| **Spool** | `ui/spool/SpoolScreen.kt` | **POLISH** | Already on `DinghyIconView` + `SpoolGlyph`; sub-pages (`SpoolPicker`, `ScanConfirmCard`, `ScanSurface`, `ActiveSpoolCard`) still raw `MaterialSymbol`; verify discard-Back stays red (C7). |
| **Settings** | `ui/screen/SettingsScreen.kt` | **POLISH** | C6-exempt densified config surface, on `OutlinedControl`; inline strings; "Coming soon" forward-stubs are intentional. |
| **Theme Editor** | `ui/screen/ThemeEditorScreen.kt` | **POLISH** | Config surface (C6-exempt); raw `MaterialSymbol`, several fixed dp (swatch sizes — acceptable for a color tool), raw `Color()` (sanctioned data-color). Verify dense-on-one-page. |
| **Printers** | `ui/screen/PrintersScreen.kt` | **POLISH** | Config surface; raw `MaterialSymbol`; ConfirmGuard for remove. Mostly conformant. |
| **Output LED detail / RGB** | `ui/outputs/OutputLedDetail.kt` | **POLISH** | Owner already flagged RGB page aesthetics as non-blocking future polish; logic clean (channel-capability aware), inline `LedBrightnessControl` duplicates ScrubberPage's fill-bar — acceptable (double-scaffold fix) but a coherence note. |
| **Outputs list** | `ui/outputs/OutputsScreen.kt` | **POLISH** | On `DinghyIconView`; but carries a duplicated `intentColor` + bespoke `OutputRow`/`BackControl` instead of `OutlinedControl`. |
| **Output Pin / Scrubber detail** | `ui/outputs/OutputPinDetail.kt`, `OutputScrubberDetail.kt` | **PASS** | Built on the shared `ScrubberPage`/toggle primitives; conformant. |
| **Print Status (home)** | `ui/printstatus/PrintStatusScreen.kt` | **PASS** | Matches `03-print-status.png` closely; on `DinghyIconView`; correct Tune/Pause/Stop intent ladder + ConfirmGuard. (Perf-heavy per CONCERNS.md, but visually conformant — 1584-line god-file is a refactor target, not a UI defect.) |
| **Temperature** | `ui/temperature/TemperatureScreen.kt` | **PASS** | Matches `09-temperature-graph.png`; on `OutlinedControl` + `ScrubberPage` + `GraphViewHost`; correct C5 context-dependent accent (Presets↔Cooldown). |
| **Fine-Tune Hub + tiles** | `ui/finetune/FineTuneHubScreen.kt`, `FineTuneTile.kt` | **PASS** | On `DinghyIconView`; Phase-17 hardened; tile grid + ScrubberPage edit path conformant. |
| **Fine-Tune sub-pages** | `ui/finetune/ExtrusionScreen.kt`, `MotionScreen.kt`, `FwRetractionScreen.kt` | **PASS** | All route numeric edits through the shared `ScrubberPage`; clamp/markPending hardened in Phase 17. |
| **Probe Calibrate** | `ui/calibration/ProbeCalibrateScreen.kt` | **PASS** | The one calibration screen already migrated to `DinghyIconView`; ConfirmGuard SAVE_CONFIG amber gate correct. (Still has 1 local control but closest to conformant in the cluster.) |
| **System Information** | `ui/systeminfo/SystemInformationScreen.kt` | **PASS** | Newest screen — on `DinghyIconView`, `stringResource`, fsSp throughout, graceful "—" degrade. The conformance exemplar. One fixed 24dp decorative spacer (trivial). |
| **Webcam** | `ui/webcam/WebcamScreen.kt` | **PASS** | Conformant chrome; known rotation-blank limitation is a decoder issue (Phase 22), not a UI-LAW defect. |
| **Splash / Confirm guard / ScreenScaffold / AppShell** | `ui/screen/SplashScreen.kt`, `designsystem/ConfirmGuard.kt`, `designsystem/layout/ScreenScaffold.kt`, `ui/shell/AppShell.kt` | **PASS** | Shell + safety primitives are model-conformant; ConfirmGuard especially (opaque scrim, warn/destructive/go intent ladder, neutral safe-dismiss). |
| **Prompt (macro prompt protocol)** | `ui/prompt/*` | **PASS** | Author-hex carve-out correctly bounded to markup runs; chrome token-routed. |

**OVERHAUL count: 5** (App Drawer + the 4-screen Calibration cluster).
**POLISH count: 12.** **PASS count: 10** (grouping shell primitives as one row).

---

## INTERACTION-INCONSISTENCY CATALOG (Phase 23)

Building on CONVENTIONS.md "Inconsistencies and Conformance Gaps" — extends with the interaction-model layer.

### A. Bespoke `Box+border` buttons vs `OutlinedControl` (the dominant inconsistency)
The house button primitive `OutlinedControl` (`designsystem/control/OutlinedControl.kt`) embeds the 64dp floor, intent-color, and label/symbol grammar. These screens re-implement it inline instead:
- `ui/calibration/CalibrationHubScreen.kt:174` — `HubActionControl`
- `ui/calibration/ScrewsTiltScreen.kt`, `BedMeshScreen.kt`, `TiltScreen.kt`, `ProbeCalibrateScreen.kt` — local `CalibActionControl`-class composables
- `ui/outputs/OutputsScreen.kt:133/:207` — `OutputRow`, `BackControl`
- `ui/console/ConsoleScreen.kt:139/:179` — `FilterToggle`, `BackControl`
- `ui/files/FilesScreen.kt` — inline button construction (+ its own `intentColor:524`)
- `ui/macros/MacroExecutionPopup.kt`, `SystemMacrosScreen.kt` — inline rows
- `ui/extrude/ExtrudeScreen.kt` — `FieldButton`
Impact: 5 copies of the intent→color map (see B), inconsistent press/touch behavior risk, and any change to the control language touches N files.

### B. Duplicated `intentColor()` map (drift hazard)
Four verbatim copies of `OutlinedControl`'s private `Intent.outlineColor`:
- `ui/calibration/CalibrationHubScreen.kt:202` (`internal`)
- `ui/files/FilesScreen.kt:524`
- `ui/macros/MacroExecutionPopup.kt:352`
- `ui/outputs/OutputsScreen.kt:230`
Fix once: promote `Intent.outlineColor` to `internal` in `OutlinedControl.kt`, delete the four.

### C. Button-intent color vs mockup/LAW divergence
- **Move "Disable" is `Intent.Danger` (red)** at `ui/move/MoveScreen.kt:220`, while the file's OWN KDoc (`:92`) says `Intent.Warn` amber and the mockup `04-move.png` renders it amber. Code, doc, and mockup disagree. Reconcile (THEMING calls M84/disable "reset/undo" = amber proceed-at-peril; the un-homing is recoverable by re-homing, not destructive).
- Cross-check needed in Phase 23: the THEMING "Back-position-consistency" rule (Back always right-aligned/last). Spot-check found Back consistently right/last on Move/Extrude/Console/Outputs/Settings — but the **app-wide Back sweep was explicitly DEFERRED** from Phase 15.1 (THEMING.md "HONEST DEFERRAL"). Phase 23 should close that inventory.

### D. Numeric-input model: scrubber vs stepper vs numpad (three idioms)
The app has THREE keyboard-free numeric entry surfaces and their usage is screen-dependent:
- **`ScrubberPage`** (full-screen fill-bar + ± steppers): Temperature setpoints, Fine-Tune group, Output detail. The canonical idiom.
- **`NumpadPage`** (full-screen keypad): Extrude Distance/Speed/Temp (`ExtrudeScreen.kt:298+`) — exact entry where a scrubber's range is too wide.
- **Inline `LedBrightnessControl`** (`OutputLedDetail.kt:321`): a hand-rolled fill-bar that re-implements ScrubberPage's gesture to avoid a double-scaffold. Functionally identical interaction, separate code.
- **Bespoke distance/preset selectors** (Move `DistanceSelector`, Extrude `DistanceSelector`/`SpeedSelector`): fixed-preset row, intentional per mockup (D-03), NOT a scrubber.
Phase 23 question: is the scrubber-vs-numpad split principled and predictable to the user, or does it feel arbitrary screen-to-screen? Document the rule.

### E. Icon-system divergence (raw `MaterialSymbol` vs `DinghyIconView`)
- **Raw `MaterialSymbol`** (11 screens): Move, Console, Extrude, BedMesh, CalibrationHub, ScrewsTilt, Files, MacroExecutionPopup, SystemMacros, Printers, ThemeEditor, AppDrawer, ActiveSpoolCard, ScanConfirmCard, ScanSurface, SpoolPicker.
- **`DinghyIconView`** (9 screens): ProbeCalibrate, FineTuneHub, FineTuneTile, Outputs, PrintStatus, Spool, SystemInfo.
Tracked as D-03 "Phase-22 backfill" in `DinghyIcons.kt`. The risk is real: half the app won't follow a registry/glyph change.

### F. Drawer interaction
- The swipe-up App Drawer is correctly suppressed on scroll-Field screens (Console, Files, Settings, SystemInfo, Outputs) with an explicit gutter Back — this is consistent and conformant.
- The drawer's `Dest.Spool` special-case (`AppDrawer.kt:321` `SpoolGlyph` instead of `MaterialSymbol`) is a sanctioned one-off, not a bug.
- The density problem (fixed 4-col) is structural (see Top Fix #1), not an interaction-grammar problem.

### G. Confirm-guard behavior — CONSISTENT (the good news)
All 9 `ConfirmGuard` call-sites (Move, PrintStatus ×2, Files ×2, ProbeCalibrate, BedMesh, Printers, ThemeEditor) route through the single full-screen `ConfirmGuard` primitive with the correct destructive(red)/warn(amber)/go(green) + neutral-dismiss ladder. This is a model of interaction coherence and needs no Phase-23 work.

---

## CONFORMANCE-GAP LIST (Phase 24: touch-target / fsSp scaling / rotation)

### G1. ≥64dp touch-target floor
- **PASS (embedded floor):** Every screen on `OutlinedControl` inherits `heightIn(min = 64.dp)`. Bespoke controls in Calibration/Console/Outputs/CalibrationHub also apply `heightIn(min = 64.dp)` explicitly — verified present.
- **GAP — App Drawer portrait** (`ui/shell/AppDrawer.kt:110`): `GridCells.Fixed(4)` aspect-ratio-1 tiles in a narrow portrait column produce sub-comfortable tile sizes vs the mockup's 2-col large tiles. The 64dp floor is not explicitly enforced on drawer tiles (they rely on grid-cell sizing). **Phase-24: enforce a minimum tile size / orientation-aware columns.**
- **VERIFY — Macro rows** (`SystemMacrosScreen.kt`, `BookmarkedMacrosScreen.kt`): only 1 `64.dp` occurrence each; confirm every tappable macro row clears the floor on-device.
- **EXEMPT (correct):** Settings, Theme Editor, Printers, About — config surfaces, C6-exempt from the 64dp floor and intentionally densified. Do NOT flag.

### G2. `fsSp` S/M/L text-scaling (the recurring "fonts too small" trap)
- **PASS — universal:** Grep found ZERO hardcoded `.sp` font sizes in `ui/` + `designsystem/`. Every `fontSize` routes through `fsSp(base, t.fs)`. This is the cleanest pillar in the app.
- **VERIFY at `fs = L`:** The fixed `.dp` glyph/swatch sizes that derive from `fsSp(...).dp` (e.g. `OutputsScreen.kt:154`, `OutputLedDetail.kt:233`) scale correctly. But **decorative fixed-dp spacers** (`Box(Modifier.height(24.dp))` in Settings/About/SystemInfo/ThemeEditor/Printers, `Spacer(Modifier.size(12.dp))` Files:631) do NOT scale — at `fs = L` with long-form content these may produce cramped rhythm. Low severity; verify on-device at L.
- **VERIFY — `@Preview(fontScale)` is a no-op** (DinghyTheme pins OS fontScale=1f). The ONLY way to catch L-overflow is the `fsLargeSeed` preview companion (`preview/PreviewTheming.kt`). Phase-24 must confirm every overhauled/polished screen ships an `fsLargeSeed` preview — the icon-migrated/bespoke screens predate the preview-first convention and likely lack them.
- **TextAutoSize edge case** (`TemperatureScreen.kt:379` `stepSize = 1.sp`): auto-size granularity, not content text — acceptable, do not flag.

### G3. Portrait / landscape rotation conformance
- **PASS — primitive level:** `ScreenScaffold` detects orientation via `BoxWithConstraints` (`maxWidth > maxHeight`), not `LocalConfiguration` — so every screen built on it adapts (portrait stacks Focus/Field/Gutter; landscape 50/50 + full-width gutter). `portraitFocusAspect` correctly protects sacred squares (Move jog pad).
- **GAP — App Drawer ignores orientation** (`AppDrawer.kt:110`): fixed 4 columns regardless of `maxWidth/maxHeight`. The mockup is explicitly 2-col portrait / 3-col landscape. This is BOTH a touch-target gap (G1) and a rotation gap. **Top Phase-24 item.**
- **KNOWN/DEFERRED — Webcam H.264 blanks on rotation** (`ui/webcam/Media3Feed.kt`, CONCERNS.md CR-01): SurfaceView/decoder renegotiation drops the surface on orientation change mid-play. Decoder issue, deferred to Phase 22 — note it, but it is not a layout-conformance fix.
- **VERIFY — scroll-Field screens in landscape:** Console/Files/Settings/SystemInfo/Outputs are Field-only scroll lists with a gutter Back. Confirm the gutter Back stays reachable (not pushed off-screen) in landscape at `fs = L` with the keyboard absent. Most likely fine (gutter is outside the scroll), but it's the highest-risk rotation interaction.
- **VERIFY — Console 4-cell gutter** (`ConsoleScreen.kt:99`): 3 filter toggles + Back in one Row. In portrait at `fs = L` the 2-line filter labels ("Hide temperatures") may crowd or clip. Check wrap/overflow.

---

## Files Audited (representative — full UI tree traversed)
- Design-system primitives: `designsystem/control/OutlinedControl.kt`, `designsystem/layout/ScreenScaffold.kt`, `designsystem/ConfirmGuard.kt`, `designsystem/ScrubberPage.kt`, `designsystem/NumpadPage.kt`, `designsystem/MaterialSymbol.kt`, `designsystem/icons/DinghyIcons.kt`/`DinghyIconView.kt`/`SpoolGlyph.kt`
- Shell: `ui/shell/AppShell.kt`, `ui/shell/AppDrawer.kt`, `ui/shell/ShellNavState.kt`
- Screens: `ui/printstatus/PrintStatusScreen.kt`, `ui/move/MoveScreen.kt`, `ui/extrude/ExtrudeScreen.kt`, `ui/temperature/TemperatureScreen.kt`, `ui/console/ConsoleScreen.kt`, `ui/files/FilesScreen.kt`, `ui/finetune/{FineTuneHubScreen,FineTuneTile,ExtrusionScreen,MotionScreen,FwRetractionScreen}.kt`, `ui/calibration/{CalibrationHubScreen,ProbeCalibrateScreen,BedMeshScreen,ScrewsTiltScreen,TiltScreen}.kt`, `ui/outputs/{OutputsScreen,OutputLedDetail,OutputPinDetail,OutputScrubberDetail}.kt`, `ui/spool/{SpoolScreen,SpoolPicker}.kt`, `ui/macros/{SystemMacrosScreen,BookmarkedMacrosScreen,MacroExecutionPopup}.kt`, `ui/systeminfo/SystemInformationScreen.kt`, `ui/screen/{SettingsScreen,ThemeEditorScreen,PrintersScreen,SplashScreen}.kt`, `ui/webcam/WebcamScreen.kt`
- Baseline: `docs/ui_design/{CLAUDE.md,LAYOUT.md,THEMING.md}` + mockups `03-print-status.png`, `04-move.png`, `05-screws-tilt.png`, `07-single-setting.png`, `02-app-drawer.png`
- Cross-referenced: `.planning/codebase/CONVENTIONS.md`, `.planning/codebase/CONCERNS.md`

*No screenshots captured (no dev server — native Android). Visual divergences reconstructed from source vs canonical mockups; on-device verification of fs=L overflow + drawer density + rotation is recommended before Phase 23/24 execution.*
