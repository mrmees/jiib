---
phase: 26-adjustment-screens
fixed_at: 2026-06-11T00:13:35Z
review_path: .planning/phases/26-adjustment-screens/26-REVIEW.md
iteration: 1
findings_in_scope: 15
fixed: 14
skipped: 1
status: partial
---

# Phase 26: Code Review Fix Report

**Fixed at:** 2026-06-11T00:13:35Z
**Source review:** .planning/phases/26-adjustment-screens/26-REVIEW.md
**Iteration:** 1

**Summary:**
- Findings in scope: 15 (4 Critical + 11 Warning; fix_scope = critical_warning)
- Fixed: 14
- Skipped: 1 (WR-08 — requires owner glyph decisions, hard icon law)

**Verification:** All fixes applied in an isolated worktree, committed atomically, fast-forwarded
to `master`, then gated GREEN with `:app:testDebugUnitTest` (Windows-side gw.bat, BUILD SUCCESSFUL,
full host suite incl. the new `FmtValueTest`). Compose interaction fixes (CR-01/02/04, WR-03/05/06/07)
are compile- and test-verified but their on-device behavior should be confirmed at the next flox UAT.

## Fixed Issues

### CR-01: Temperature adjuster reads a frozen SensorReadout

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`
**Commit:** e088610
**Applied fix:** Selection state now stores the sensor NAME (`selectedName`); the live
`SensorReadout` is resolved from `legend` on every composition. Repeated ± taps now step from the
live target, the hero value tracks updates, and a sensor vanishing on reconnect falls back to the
graph automatically. Requires human (on-device) verification of the interactive feel.

### CR-02: Off heater cannot be turned on from the Temperature adjuster

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`
**Commit:** 5bfa00d
**Applied fix:** `AdjusterPanel` now receives `value = currentValue` (target, else live temperature
when > 0), and both nudge closures step from `currentValue` — an idle heater steps up from its
current temperature through `clampHeaterTarget`. Dead `baselineTarget` local deleted.

### CR-03: `fmtValue` corrupts 0-decimal displays ending in zero

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneShared.kt`,
`app/src/test/java/works/mees/dinghy/ui/finetune/FmtValueTest.kt` (new)
**Commit:** d298993
**Applied fix:** Trailing-zero trim now applies ONLY when the formatted string contains a decimal
point. Regression tests added and passing: `fmtValue(1499.5, 0) == "1500"`,
`fmtValue(2999.9999, 0) == "3000"`, plus decimal-trim and whole-number cases.

### CR-04: Outputs Focus scrubber stale `pointerInput` closure (command misdirection + frozen preview)

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/outputs/OutputsScreen.kt`,
`app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt`,
`app/src/main/java/works/mees/dinghy/ui/outputs/OutputFocusControl.kt`
**Commit:** 0a29f6b
**Applied fix:** Three-part hardening:
1. `OutputsContent` wraps `OutputFocusControl` in `key(selectedRow.descriptor.objectKey)` — keyed on
   output IDENTITY (cannot change mid-drag; not the forbidden `key(value)` rebuild), so switching
   same-family outputs tears down the previous control's gesture handler and dispatch closures.
2. `ScrubberControl`: `working` moved to ONE stable `MutableFloatState` re-seeded IN PLACE on
   value/range change (the running gesture handler never writes a dead state object), and
   `actions`/`onValueChange` routed through `rememberUpdatedState` so settle always dispatches via
   current lambdas.
3. Same stable-state + `rememberUpdatedState` hardening applied to `LedBrightnessControl`.
Requires human (on-device) verification: drag fan A → switch to fan B → drag again; repeat drags
after echoes.

### WR-01: Heater nudge in-flight guard never matches

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`
**Commit:** 717a980
**Applied fix:** Guard now compares the dispatch key `"set_heater_$sensorName"` (the same key
passed into `SetHeaterArgs`) against `inFlight`, instead of the clamped value's string.

### WR-02: Extrude dispatch failures silently dropped

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`
**Commit:** 44f34b6
**Applied fix:** Live overload now owns `failureText` + a `LaunchedEffect(dispatcher)` collector on
`dispatcher.events` (mirrors FineTuneScreen/TemperatureScreen) with the 4s auto-clear; the value is
plumbed into `ExtrudeContent` as a parameter and the dead `onDispatchFailure` parameter was deleted
from both overloads.

### WR-03: Extrude numeric IME display diverges from clamped state

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`
**Commit:** 1b116db
**Applied fix:** `onDone` now ALWAYS resyncs the display text from the clamped state
(`distanceText = fmtDist(distance)` / `speedText = speed.toString()`), not only when parsing fails —
typing 500 against a 50 mm ceiling snaps back to 50 on Done.

### WR-04: Retraction speeds — markPending rounds half-up, wire truncates

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt`
**Commit:** 1348df3
**Applied fix:** `dispatchForTuner` now converts `retractSpeed`/`unretractSpeed` with
`.roundToInt()` (half-up), matching `FineTuneHolder.markPending`'s `roundToWirePrecision` exactly —
a fractional live speed no longer arms an unreachable flip (8s transient dim closed).

### WR-05: Duplicate FloatingEStop (shell-level + per-screen, stacked in the same corner)

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
**Commit:** 0e4b9a0
**Applied fix:** Took the review's second option: the shell-level e-stop is now suppressed on
destinations that render their OWN FloatingEStop + guard (`NavDest.FineTune`, `NavDest.Temperature`,
`NavDest.Spool`). Per-screen placement is those screens' documented design intent (Box-sibling
pattern asserted by their preview matrices), so this preserves it while guaranteeing ONE owner per
destination. Requires human (on-device) verification while printing.

### WR-06: Swipe-up drawer suppress set missing the Phase-26 screens

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`
**Commit:** b0d7827
**Applied fix:** Added `NavDest.Temperature`, `NavDest.FineTune`, and `NavDest.Extrude` to the
suppress predicate with a comment tying them to the set's own criteria (scrollable ListBlock Fields
/ IME content).

### WR-07: Busy-locked AdjusterPanel gives no disabled affordance

**Files modified:** `app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt`
**Commit:** ec7538a
**Applied fix:** Applied the 25-03 convention (`alpha(0.38f)` + `semantics { disabled() }`) to the
"−"/"+" stepper tiles AND the Reset button when `enabled == false || value == null`; Reset's
onClick is now also gated on the same condition (previously it dispatched even during busy-lock).

### WR-09: Macro numeric clamp bypassed by Execute-without-Done; filter admits NaN/Infinity/exponent

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`
**Commit:** a41d37e
**Applied fix:** `execute()` now clamps every numeric param on the dispatch path itself
(parse → `coerceIn(MACRO_NUMERIC_RANGE)` → `formatNumeric`), so skipping ImeAction.Done can never
send an unclamped number; non-finite/unparseable text falls through to `buildTyped`'s
`rejectNonNumeric` (toast) rather than being silently coerced. The keystroke filter's
`toDoubleOrNull()` branch was dropped — the digits-only regex is the sole gate, blocking "NaN",
"Infinity", and "1e5".

### WR-10: `measureSpool` (remote Spoolman write) on `rememberCoroutineScope`

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt`,
`app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt`
**Commit:** 2880316
**Applied fix:** Added `SpoolHolder.measureSpoolAsync(spool, grams)` launching on the holder's own
`holderScope` (shell-lifetime, mirrors its existing collector); `SpoolScreen.onApplyMeasure` now
calls it instead of `scope.launch { ... }` — same-frame navigation can no longer cancel the HTTP
write ([[dinghy-compose-write-scope-cancellation]] applied to a network write).

### WR-11: Hardcoded user-facing strings on the new surfaces

**Files modified:** `app/src/main/res/values/strings.xml`,
`app/src/main/java/works/mees/dinghy/designsystem/components/AdjusterPanel.kt`,
`app/src/main/java/works/mees/dinghy/designsystem/ScrubberPage.kt`,
`app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneScreen.kt`,
`app/src/main/java/works/mees/dinghy/ui/temperature/TemperatureScreen.kt`,
`app/src/main/java/works/mees/dinghy/ui/extrude/ExtrudeScreen.kt`
**Commit:** bc4a864
**Applied fix:** Added 10 string resources (`common_apply`, `adjuster_reset`, `adjuster_was`,
`finetune_reset_all`, `temp_presets`, `temp_cooldown`, `temp_preheat_preset`,
`extrude_spoolman_coming_soon`, `extrude_no_load_macro`, `extrude_no_unload_macro`) and wired every
flagged call site through `stringResource` (reusing existing `common_done`, `common_back`,
`common_cancel`, `output_off` where present). Extrude toast copy is resolved at composition and
captured by the onClick lambdas. **Documented exemption:** the "−"/"+" stepper labels stay literal
by explicit decision (locale-independent math glyphs, comment at the AdjusterPanel stepper row);
the "was" baseline span is now `adjuster_was` with the two-space inline gap kept code-side.

## Skipped Issues

### WR-08: Duplicate glyphs co-rendered on the Fine-Tune flat list

**File:** `app/src/main/java/works/mees/dinghy/ui/finetune/FineTuneParams.kt:119/201, 161/234, 171/213`
**Reason:** Requires owner decision — the fix is four NEW glyph assignments for the FW-retraction
rows (Retract Length, Retract Speed, Unretract Extra, Unretract Speed), and the hard owner law
([[dinghy-never-pick-icons-ask]] / D-24 registry-only) forbids choosing glyphs unilaterally. The
review itself says "Ask the owner... do NOT pick replacements unilaterally". Until Matthew assigns
them, this remains a known icon-law violation visible only on fw-retraction printers
(`hasFwRetraction == true`), where `OutputCircle`, `MaxVelocity`, and `MaxAccel` each appear twice
on the flat list.
**Original issue:** The flat list shows all params on one screen; three glyphs are each used by two
co-rendered rows, violating the "never the same glyph twice on one screen" registry law.

---

_Fixed: 2026-06-11T00:13:35Z_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
