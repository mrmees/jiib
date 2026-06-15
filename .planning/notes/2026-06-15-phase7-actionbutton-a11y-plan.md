# Phase 7 — ActionButton catalog migration + a11y sweep (TDD task plan)

**Date:** 2026-06-15  **Branch:** `control-baseline-audit`  **Flow:** writing-plans → subagent-driven (GSD OFF)
**Spec:** `.planning/notes/2026-06-14-control-baseline-audit-design.md` §3 (ControlSpec), §5 (a11y law)
**Master list:** `.planning/notes/2026-06-14-control-master-list.md` (Part 2 ActionButton; §f#3 output.off=Warn)
**Owner ruling 2026-06-15:** *"a11y sweep + disciplined catalog."* — catalog ONLY clearly-shared
recurring controls (the spec's anti-over-catalog seed policy); leave per-screen one-offs inline;
**DEFER** the PrintStatus Box rogues (they already carry `contentDescription` — structural-only).

---

## Goal
Close the icon-only a11y gap (~10 controls, concentrated in Spool + Temperature foot bars) and add the
two genuinely-shared recurring controls to the `ControlSpecs` catalog — proving the catalog by routing
the matching call sites through the `OutlinedControl(spec = …)` overload. No over-cataloging; no
visual change except the one LOCKED §f#3 change (Outputs Off: Danger→Warn).

### Catalog discipline (do NOT exceed)
Add EXACTLY two specs: `calibration.home_all` (appears on 4 calibration screens) and `output.off`
(2 screens; Warn per §f#3). Everything else stays inline. The conflicted per-screen-variant controls
(`common.cancel*`/`common.done`/`common.save`, splash.*, printers.*, calibration.run/accept/abort) are
deliberately NOT catalogued — the seed policy says "they need per-screen handling; one-offs never get a
spec." Don't touch them beyond a11y (and they're mostly LABELED → already a11y-OK).

---

## Task 1 (TDD) — two new catalog entries + drift test stays green

**File:** `app/src/main/java/works/mees/dinghy/control/ControlSpecs.kt`

Add, alphabetized by key, into the object AND into the `all` list:
```kotlin
val calibrationHomeAll = ControlSpec(
    key = ControlKey("calibration.home_all"),
    labelRes = R.string.calibration_home_all,   // LABELED → no contentDescriptionRes required
    icon = null,                                 // label-only foot button (master list: no glyph)
    intent = Intent.Go,                          // R19: homing is the expected action
    type = ControlType.Button,
)
val outputOff = ControlSpec(
    key = ControlKey("output.off"),
    labelRes = R.string.output_off,
    icon = null,
    intent = Intent.Warn,                        // §f#3 — Warn app-wide (Outputs Danger→Warn)
    type = ControlType.Button,
)
```
- **RED first:** `ControlCatalogDriftTest` already asserts unique keys / icon∈DinghyIcons.all / commandCatalogId resolves / icon-only⇒contentDescriptionRes. These two entries are LABELED with `icon = null`, so they need NO contentDescriptionRes and reference no icon/command — confirm the drift test PASSES with them added (run it). If the drift test has an explicit count assertion, bump it 12→14. No new test logic needed unless the count is hard-coded.
- Verify `calibration_home_all` + `output_off` string resources exist (they do: strings.xml:495, :250).

## Task 2 — migrate `calibration.home_all` ×4 → `spec = ControlSpecs.calibrationHomeAll`

Sites (verify each at HEAD before editing; confirm current intent is `Intent.Go` — if any is NOT Go,
STOP and flag, do not silently change it):
- `ui/calibration/ProbeCalibrateScreen.kt` (the `!vm.homedGate` foot branch — "Home All").
- `ui/calibration/BedMeshScreen.kt` (`!vm.homed` branch — "Home All").
- `ui/calibration/TiltScreen.kt` (`!vm.homedGate` branch).
- `ui/calibration/ScrewsTiltScreen.kt` (`!vm.homedGate` branch).
Replace each `OutlinedControl(label = stringResource(R.string.calibration_home_all), onClick = onHome…,
modifier = Modifier.weight(1f), intent = Intent.Go [, enabled = …])` with
`OutlinedControl(spec = ControlSpecs.calibrationHomeAll, onClick = onHome…, modifier = Modifier.weight(1f) [, enabled = …])`.
Zero visual change (same label/intent). Keep any existing `enabled =` arg (BedMesh passes `dispatcherPresent`).

## Task 3 — migrate Outputs `output.off` ×2 → `spec = ControlSpecs.outputOff`  (LOCKED Danger→Warn)

**File:** `ui/outputs/OutputFocusControl.kt` — the two "Off" buttons at ~460-463 and ~562-565
(currently `label = stringResource(R.string.output_off), intent = Intent.Danger`). Replace with
`OutlinedControl(spec = ControlSpecs.outputOff, onClick = …, modifier = …)`. This intentionally changes
the Off button from RED→AMBER per §f#3 (flag for UAT).
> Do NOT touch `OutputToggleControl.kt:116` — that `output_off` is the On/**Off** TOGGLE option (a
> Toggle pair, not the "set to 0" action button). Out of this task.

## Task 4 — Spool foot bar a11y via `spec =` (icon-only specs — clean fix)

**File:** `ui/spool/SpoolScreen.kt` foot bar (~489-518), four icon-only `OutlinedControl`s with NO
`contentDescription`. Migrate each to its EXISTING icon-only spec (verify the current `intent` matches
the spec; if not, flag):
- Home (`DinghyIcons.Home`, ~489) → `spec = ControlSpecs.commonHome` (Accent, icon-only + cd).
- Scan (`DinghyIcons.QrCode`, ~496) → `spec = ControlSpecs.spoolScan` (Accent).
- Unload (`DinghyIcons.ExpandCircleDown`, ~505) → `spec = ControlSpecs.spoolUnload` (Go).
- Load (`DinghyIcons.ExpandCircleUp`, ~513) → `spec = ControlSpecs.spoolLoad` (Go).
Keep each call site's `onClick`, `modifier` (`weight(1f)`), and any `enabled`/conditional wrapping. The
spec overload supplies the `contentDescription` automatically (the a11y fix) with NO visual change.

## Task 5 — Temperature foot a11y (inline contentDescription — keep icon-only)

**File:** `ui/temperature/TemperatureScreen.kt`. These icon-only foot controls lack a
`contentDescription`. Do NOT migrate Back to the labeled `commonBack` spec (it would add a "Back" text
label in a tight foot bar). Add inline `contentDescription = stringResource(...)`:
- Back ×2 (`DinghyIcons.Back`, ~664 + ~745) → `contentDescription = stringResource(R.string.cd_back)` (exists).
- Settings (`DinghyIcons.TempSettings`, ~671) → new `R.string.cd_temp_settings`.
- Enter-Adjust toggle (`DinghyIcons.OutputHeater`, ~678) → new `R.string.cd_temp_enter_adjust`.
- Return-to-Monitor toggle (`DinghyIcons.MonitorMode`, ~706 and ~712) → new `R.string.cd_temp_enter_monitor`.
(Verify exact lines/icons at HEAD — the inventory's numbers are approximate.)

## Task 6 — new strings

**File:** `app/src/main/res/values/strings.xml` (near the other `cd_temp*`/temperature strings):
```xml
<string name="cd_temp_settings">Sensor display settings</string>
<string name="cd_temp_enter_adjust">Switch to adjust mode</string>
<string name="cd_temp_enter_monitor">Switch to monitoring mode</string>
```
(Wording is a starting point — owner may tweak at UAT. These are TalkBack labels, not visible text.)

## Out of scope (record, do not touch)
- **PrintStatus inline Box rogues** (ShortcutRow/Launcher/Babystep, `PrintStatusField.kt`): already
  carry `contentDescription` — DEFERRED (structural-only; owner 2026-06-15). Leave a `// TODO(control-audit)`
  note is NOT required — just leave them.
- All per-screen one-off foot buttons (splash.*, printers.*, calibration.run/accept/abort/save_config,
  common.cancel/done/save): LABELED → a11y-OK; stay inline; no spec.
- Settings/Theme menu, SecureToggleRow, Move focus-body, bed-area, Extrude.

## Verification (controller runs after the subagent)
- **Host tests:** `:app:testDebugUnitTest --tests *ControlCatalogDriftTest` GREEN (the 2 new entries pass).
- **Build:** `:app:assembleDebug` GREEN (Windows-side, `| tr -d '\r'`).
- **Diff review:** (a) exactly 2 catalog entries added (no over-catalog), (b) 4 calibration + 2 Outputs
  + 4 Spool sites migrated to `spec=`, (c) Temp inline cds added, (d) 3 new strings, (e) PrintStatus
  untouched, (f) explicit-path staging only.
- **UAT on BOTH** flox (`0a64b42e`) + moto (`ZY22LBDRM9`): Spool/Temp/calibration/Outputs render
  unchanged EXCEPT the Outputs **Off** button is now AMBER (Warn) not red (§f#3). TalkBack (optional
  spot-check) announces the previously-silent icon buttons. Push only after UAT ✓.

## Subagent rules
- TDD: confirm `ControlCatalogDriftTest` green with the new entries first. Stage **explicit paths**,
  never `git add -A`. Do NOT commit/push (controller handles it).
- **Never pick an icon** — the two new specs are LABEL-only (`icon = null`). If any site seems to need a
  new glyph, STOP + ASK. Verify every call site's CURRENT intent matches the spec before swapping; flag
  any mismatch instead of silently changing it (except the LOCKED Outputs Off Danger→Warn).
