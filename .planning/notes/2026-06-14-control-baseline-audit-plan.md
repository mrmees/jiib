# Control / Button Baseline Audit — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to
> execute Phase 0 task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Collapse every in-scope interactive control to a small set of defined component CLASSES
(1U height, U-relative spacing, one baseline per state) and a single `ControlSpec` catalog of named
identities — verified style-resolution + owner-reviewed icon/label/intent mapping.

**Architecture:** Build ONTO the existing `OutlinedControl` primitive; add a presentation-only
`ControlSpec` catalog that composes the three existing registries (`R.string` / `DinghyIcons` /
`CommandRegistry`); unify the selector/stepper/toggle/swatch families onto shared classes; express
inner spacing as U-fractions under collapsed named tokens (`gapS`/`gapM`).

**Tech Stack:** Kotlin, Jetpack Compose, `kotlinx.collections.immutable`, host JUnit tests; build
Windows-side via `E:\Android\gw.bat`; UAT on flox (`0a64b42e`) + moto (`ZY22LBDRM9`).

**Spec:** `.planning/notes/2026-06-14-control-baseline-audit-design.md` (owner-approved 2026-06-14).

---

## Execution shape — the human gate

```
Phase 0  Consolidation pass → PARED-DOWN MASTER LIST   ← detailed below (executable now)
   │
   ▼  ◄──────────────  OWNER REVIEW GATE  (owner redlines icons/labels/intents)
   │
Phase 1..9  Implementation (roadmap below; full TDD task-plans authored AFTER the gate)
```

Detailed per-phase TDD task plans for Phases 1–9 are authored **after** the owner redlines the
master list (via a fresh writing-plans pass per phase), because the catalog DATA — exact icon
tokens, label resources, and intent assignments — is an OUTPUT of the gate, not an input.

---

## The reduction methodology (Phase 0 applies this SILENTLY)

The master list owner reviews is the *result* of reduction, not the raw inventory. Apply in order:

1. **Dedupe by semantic ROLE.** Collapse ~175 call sites to the minimal set of distinct NAMED
   controls (Back, Home, Save, Done, Print, Delete, Load, Unload, Cooldown, Decrease, Increase,
   Sort:name/date/size, Filter:material/vendor/color, e-stop, …). One catalog row per role.
2. **Absorb micro-variance into class tokens — do NOT surface it.** Any spacing/padding/size that
   differs only trivially (the `8`-vs-`10`-vs-`12`dp spread, a `0.02U` gap difference) normalizes to
   `gapS`/`gapM` (§6 of spec) automatically. The owner NEVER sees a "this gap was 10 not 8" row.
3. **Discard rogue/redundant styles → just "migrate to X".** MUI `Checkbox`, custom pills, inline
   `Box+border+clickable`, literal `"−"/"+"` text → mapped onto the canonical class. Not decisions.
4. **Surface ONLY genuine decisions:** (a) unassigned icons → **ASK** (sourced against
   `img/material-icon-bucket.json`, Claude picks none); (b) the SAME role wearing DIFFERENT intent
   across screens (needs a ruling); (c) ambiguous/inconsistent label wording; (d) a control whose
   CLASS is genuinely ambiguous.

---

## Phase 0 — Consolidation pass → pared-down master list

**Deliverable:** `.planning/notes/2026-06-14-control-master-list.md` — three parts:
- **Part 1 — Consolidated classes + baseline** (small table, quick sign-off): final class set, their
  1U + `gapS`/`gapM` tokens, the state-style baseline.
- **Part 2 — Named-control mapping** (the meat, owner redlines): one row per DISTINCT named control —
  `key` · role · screens it appears on · current status · proposed **icon** (ASSIGNED-from-`<src>` /
  **ASK**) · proposed **label** (`R.string` ref or wording) · **intent** · **type**.
- **Part 3 — Open decisions** (short): the genuine asks/conflicts from methodology step 4.

No production code in Phase 0 — it is analysis/synthesis over the inventory already gathered
(this plan's spec + the two explorer sweeps in the brainstorm transcript).

**Files:**
- Create: `.planning/notes/2026-06-14-control-master-list.md`

- [ ] **Step 1: Re-confirm the in-scope call-site inventory is complete.**

Dispatch a verification sweep (Explore subagent) to diff the brainstorm inventory against current
HEAD for any in-scope control site missed. Grep targets across `app/src/main/java/works/mees/dinghy/ui/`
(EXCLUDING `ui/extrude/**`, Move focus-body, Settings/Theme, bed-area views):
```
OutlinedControl(   SortRow(   FilterRow(   IncrementPicker(   AdjusterPanel(   Scrubber(
OutputToggleControl(   \.clickable   \.combinedClickable   Checkbox(   ColorSwatchGrid(
```
Expected: confirms or extends the 28-screen inventory in the spec. Record any deltas.

- [ ] **Step 2: Build the deduped named-control set (methodology steps 1 & 3).**

Group every in-scope site by semantic role into a `key` (`common.back`, `files.print`,
`spool.load`, `stepper.decrease`, `spool.sort.name`, `temp.cooldown`, `printer.estop`, …). Mark each
distinct role with: which screens it appears on, its current intent(s) across those screens, its
current icon/label, and current implementation (clean `OutlinedControl` / inline rogue / literal
text). Flag rogue implementations as "migrate to `<class>`".

- [ ] **Step 3: Resolve micro-variance to tokens (methodology step 2) — internal only.**

For every grouped role, compute the proposed class + 1U height + `gapS`/`gapM` spacing. Do NOT
emit per-site spacing deltas into the master list. Keep a private normalization note only if a value
genuinely cannot fold into `gapS`/`gapM` (that becomes an Open Decision, not a silent absorb).

- [ ] **Step 4: Map icons — ASSIGNED vs ASK (icon law).**

For each named control, determine the icon from existing sources ONLY: `DinghyIcons` registry, the
as-built screen, the hi-fi/sketch sources, or an `img/*.svg`. If a glyph is already selected →
`ASSIGNED-from-<src>`. If NONE is selected (e.g. sort-direction arrows; any new ControlSpec slot) →
**ASK** with the function described — Claude proposes NO glyph. Cross-reference candidate names
against `img/material-icon-bucket.json` so the owner has options to pick from, but do not choose.

- [ ] **Step 5: Map labels + intents; collect conflicts.**

For each named control, propose the `R.string` label (reuse existing resources; flag wording
inconsistencies) and the `Intent` under the R5 four-class scheme (red=destructive ·
amber=hazard-in-process · green=expected · accent=neutral/nav). Where the SAME role wears DIFFERENT
intent across screens, do NOT pick — list it in Part 3 (Open Decisions) for an owner ruling.

- [ ] **Step 6: Write the three-part master list document.**

Author `.planning/notes/2026-06-14-control-master-list.md` per the deliverable structure above.
Part 2 sorted by class then key. Keep it scannable — this is the owner's redline source.

- [ ] **Step 7: Codex sanity pass on the master list (non-reaping bg).**

Dispatch `codex:codex-rescue` (background) to sanity-check the consolidation for: roles collapsed too
aggressively (two genuinely-different controls sharing a key), missed rogue sites, and intent
assignments that violate the R5 scheme. Fold agreeable findings; surface borderline to owner.

- [ ] **Step 8: Present to owner — GATE.**

Present the pared-down master list. STOP. Do not proceed to Phase 1 until the owner redlines
icons/labels/intents and approves. Once approved, the corrected master list IS the catalog data
source for Phases 1–9.

---

## Implementation roadmap (Phases 1–9 — full TDD plans authored post-gate)

Listed in dependency order with goal · file map · test strategy. Catalog DATA (icons/labels/intents)
flows from the corrected master list.

### Phase 1 — Spacing tokens + doc reconciliation (infra, no UI risk)
- **Goal:** Add `gapS`/`gapM` as U-fraction helpers; record the R13 supersession.
- **Files:** new spacing helper in `designsystem/layout/` (e.g. `Spacing.kt` — `gapS(uDp)`, `gapM(uDp)`);
  edit `docs/ui_design/THEMING.md §7b` + `COMPONENTS.md §7b/§4` (R13 → U-fraction).
- **Tests:** host test asserting `gapS(64.dp)≈8dp`, `gapM(64.dp)≈12dp`, monotonic with U.

### Phase 2 — `ControlSpec` catalog + drift test (infra)
- **Goal:** Presentation-only catalog + `OutlinedControl(spec=…)` overload; populate from master list.
- **Files:** new `control/ControlSpec.kt` (+ `ControlType` enum), `control/ControlSpecs.kt`;
  overload in `designsystem/control/OutlinedControl.kt`; new `ControlCatalogDriftTest`.
- **Tests:** drift test (unique keys; every `icon` ∈ `DinghyIcons.all`; every `commandCatalogId`
  ∈ `CommandRegistry.all`; icon-only entries require `contentDescriptionRes`).

### Phase 3 — `SelectorRow` primitive + named presets (refactor)
- **Goal:** One `SelectorRow` base; `SortRow`/`FilterRow`/`IncrementPicker` become thin presets
  (locked SortFilter anatomy preserved). Spacing → `gapS`/`gapM`; tiles 1U.
- **Files:** new `designsystem/components/SelectorRow.kt`; refactor `SortFilterControlRow.kt`,
  `IncrementPicker.kt` to delegate.
- **Tests:** host tests for active-state style resolution (accentSoft fill + accentLine), tile-height
  = 1U, preset wiring; existing SortRow/FilterRow/IncrementPicker tests stay green.

### Phase 4 — `StepperRow` consolidation (refactor)
- **Goal:** One `[−][value][+]` class with `Decrease`/`Increase` icons; AdjusterPanel + Scrubber +
  the calibration NON-bed ± steppers delegate to it. Kills literal `"−"/"+"/"Z−"` text.
- **Files:** new `designsystem/components/StepperRow.kt`; refactor `AdjusterPanel.kt`, `Scrubber.kt`;
  edit `ui/calibration/{BedMesh,Tilt,ScrewsTilt,ProbeCalibrate}Screen.kt` (non-bed ± only).
- **Tests:** host test for ± icon path (not text), 1U fill via `LocalUnitDp`, settle-once contract
  unchanged.

### Phase 5 — `Toggle` (refactor)
- **Goal:** In-scope toggles (`OutputToggleControl`, Temperature visibility) become SelectorRow
  presets; remove rogue MUI `Checkbox` (Move save-dialog Z-include) + custom pill where in-scope.
- **Files:** edit `ui/outputs/OutputToggleControl.kt`, `ui/temperature/TemperatureScreen.kt`,
  `ui/move/MoveScreen.kt` (save-dialog toggle only — NOT focus-body).
- **Tests:** host test for inactive=`Intent.Neutral`, active=intent; toggle semantics.

### Phase 6 — `ColorSwatch` consolidation (refactor)
- **Goal:** Temperature + Spool inline swatch Boxes → one `ColorSwatch` class (ThemeEditor OUT).
- **Files:** new `designsystem/components/ColorSwatch.kt`; edit `ui/temperature/TemperatureScreen.kt`,
  `ui/spool/SpoolScreen.kt`.
- **Tests:** host test for selected-state styling + data-color carve-out (true hex, not brandTint).

### Phase 7 — ActionButton catalog migration + a11y sweep (sweep)
- **Goal:** Migrate named foot/focus-foot buttons to `OutlinedControl(spec=…)`; add the missing
  `contentDescription`s the inventory flagged. One-offs stay inline but gain a11y.
- **Files:** every in-scope screen's foot bars (per the corrected master list).
- **Tests:** instrumentation/host assertion that no icon-only in-scope control lacks a
  contentDescription (the new a11y conformance check).

### Phase 8 — Reset/revert standardization (sweep)
- **Goal:** Header trailing-revert becomes the app-wide safe "reset to default"; cull redundant foot
  Reset buttons where the revert is safe. Sequence-rearm Resets stay.
- **Files:** `ui/finetune/FineTuneScreen.kt` (drop "Reset all" where covered), adjuster screens;
  verify each calibration "Reset" per the safe-vs-sequence test before culling.
- **Tests:** host/instrumentation for revert-visibility-on-deviation; no regression of sequence Resets.

### Phase 9 — `OldMoveScreen` deletion (cleanup)
- **Goal:** Remove the legacy jog-button grid — Move Hub is committed.
- **Files:** delete `ui/move/OldMoveScreen.kt`; remove its debug-Gallery entry + any test refs.
- **Tests:** build green; grep confirms zero remaining references.

---

## Cross-phase verification
- Each phase: host tests for pure style-resolution; build Windows-side; **UAT on BOTH flox + moto**
  (push matching split-ABI slice). No-frozen-frames / responsiveness perf gate (ADR-0001 Add-2).
- **Codex (gpt-5.5/xhigh) reviews each final phase plan** before execute (non-reaping bg). Alert
  owner on criticals.
- Implementers **stage explicit paths**, never `git add -A`.
- **HARD RULE: never pick an icon — ASK.**

---

## Self-review (against the spec)
- Spec §2 scope (in/out + carve-outs) → Phase 0 grep targets + per-phase file maps honor it
  (Extrude, Move focus-body, bed-area, Settings/Theme, ColorWheel excluded). ✓
- Spec §3 ControlSpec → Phase 2 + Codex architecture. ✓
- Spec §4 classes → Phases 3–7. ✓
- Spec §5 state/a11y schema → Phases 2/7 (drift + a11y check). ✓
- Spec §6 1U + U-relative spacing → Phase 1 + applied in 3–6. ✓
- Spec §7 reset/revert → Phase 8. ✓
- Spec §8 master-list gate → Phase 0 (the deliverable + GATE). ✓
- Spec §2 OldMoveScreen deletion → Phase 9. ✓
- Reduce-first owner directive (2026-06-14) → the reduction methodology + Phase 0 silent-absorb. ✓
