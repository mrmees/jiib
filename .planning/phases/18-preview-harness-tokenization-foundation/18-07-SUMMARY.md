---
phase: 18-preview-harness-tokenization-foundation
plan: 07
subsystem: ui + preview-harness + convention-docs
tags: [preview, multipreview, tokenization, strings, icons, accessibility, coil, camerax, local-inspection-mode, convention-doc, exemplar, phase-22-deferral, crash-recovery]

# Dependency graph
requires:
  - phase: 18-02
    provides: PreviewBox + 6 theme seeds + fsLargeSeed + Nexus7Previews + SampleFixtures.spoolList + PreviewPlaceholderBox
  - phase: 18-03
    provides: DinghyIcons registry + DinghyIconView (sizeDp/a11y) + strings.xml master vocabulary + <area>_<element>/cd_* convention
  - phase: 18-05
    provides: the D-01/D-02 ANCHOR exemplar (PrintStatusPreviews) — the state-hoist + minimized-matrix template
  - phase: 18-06
    provides: exemplar #2 (FineTunePreviews) — the capability/busy archetype; shares strings.xml + DinghyIcons.kt (this plan sequenced after it)
provides:
  - "SpoolPreviews.kt — SpoolSelectionProvider (no-selection / selected) driving the MINIMIZED matrix (selection×one-theme + 6-theme×selected + fs=L + RTL), all PreviewBox/SampleFixtures.spoolList, @Nexus7Previews — the D-01/D-02/D-05 third archetype (preview-safe image/dense-data)"
  - "Stateless SpoolScreen(state=) overload + shared SpoolContent seam (no holder/dispatcher/Moonraker) — the container-free preview seam"
  - "Tokenized SpoolScreen: 20 stringResource(spool_*/cd_spool_*/common_back) sites + 7 DinghyIconView(DinghyIcons.*) glyph sites + cd_* a11y; spoolWeightText/tempText tokenized"
  - "D-05 camera branch: ScanSurface.kt CameraX PreviewView branched on LocalInspectionMode -> PreviewPlaceholderBox (Spool's real preview-unsafe View surface; NOT a Coil thumb — see deviation)"
  - "New DinghyIcons entries: Inventory, Palette, CalendarAddOn, CheckCircle, Archive (unique alternates)"
  - "New strings.xml spool_* labels/badges/weight/temp + cd_spool_* a11y keys"
  - "docs/ui_design/PREVIEW_AND_TOKENS.md — the authoritative preview-first/tokenized-first convention LAW doc Phases 19-21 follow (SC-2)"
  - "CLAUDE.md UI Design System LAW block pointer to PREVIEW_AND_TOKENS.md (SC-2 enforcement hook)"
  - "18-VALIDATION.md finalized per-task verification map (status=final, wave_0_complete=true); Phase-22 backfill deferral recorded (SC-5)"
affects: [phase-19-output-controls, phase-20-sysinfo, phase-21-webrtc, phase-22-tokenization-backfill]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Exemplar #3 = the preview-SAFE image + dense-data archetype: selection variant (no-selection/selected) = @PreviewParameter; theme = explicit PreviewBox(seed) wrappers; fs=L via fsLargeSeed (NOT @Preview(fontScale=)); ONE RTL spot-check; dense SampleFixtures.spoolList"
    - "D-05 branch target is the REAL preview-unsafe surface per screen: PrintStatus/FineTune branch Coil AsyncImage; Spool has NO Coil thumb (its QR is painterResource, preview-safe) — its preview-unsafe surface is the CameraX PreviewView in ScanSurface.kt, branched on LocalInspectionMode -> PreviewPlaceholderBox"
    - "Stateless SpoolScreen(state=) overload + shared SpoolContent — same state-hoist seam as the 18-05/18-06 anchors (no Moonraker under @Preview)"
    - "PREVIEW_AND_TOKENS.md is the enforcement hook: turns the 3 exemplars into a documented LAW (PreviewBox idiom, device/locale-only multipreview note, minimized-matrix shape, fs=L-via-fsLargeSeed gotcha, <area>_<element>/cd_* convention, DinghyIcon registration, RTL start/end rule, ≥48dp+cd_* rider, D-03 EXCLUSION + shared-component/backfill boundary)"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
    - docs/ui_design/PREVIEW_AND_TOKENS.md
  modified:
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/res/values/strings.xml
    - CLAUDE.md
    - .planning/phases/18-preview-harness-tokenization-foundation/18-VALIDATION.md

key-decisions:
  - "D-05 branch applied to the CameraX PreviewView (ScanSurface.kt), NOT a Coil thumb. The plan text said 'Coil filament-thumb branch', but SpoolScreen has NO Coil AsyncImage — the QR at SpoolScreen.kt:379 is painterResource (preview-safe, no branch needed). The genuinely preview-unsafe surface in the Spool flow is the CameraX scan PreviewView (RESEARCH Q5), so the LocalInspectionMode -> PreviewPlaceholderBox branch was correctly applied there. This still satisfies D-05's intent (branch the preview-unsafe surface) and the must-have truth (the preview-unsafe path is inspection-branched)."
  - "Stateless SpoolScreen(state=) overload + shared SpoolContent: the live path keeps its holder/dispatcher/Moonraker wiring and delegates to SpoolContent; the preview path drives SpoolContent from SpoolSelectionProvider fixtures with no live socket — the documented anchor seam (behaviour-neutral)."
  - "Tokenization boundary (Codex MEDIUM-6) honoured: tokenized literals in SpoolScreen.kt + its directly-edited children only; shared components reused across unrelated screens keep their literals for the Phase-22 backfill (detekt baseline accepts them)."
  - "D-03 respected: zero @Stable/@Immutable/ImmutableList added (Phase-22 scope)."
  - "PREVIEW_AND_TOKENS.md authored as the closing-plan enforcement hook (SC-2): without it Phases 19-21 would not mechanically follow the pattern. CLAUDE.md UI Design System LAW block gets a one-line pointer."
  - "nyquist_compliant HELD false (Codex LOW-9): all CI rows ✅, but the Studio-eyeball + flox rows (incl. the 18-04 start_dest gate) are owner-DEFERRED ⬜ — the Phase-17-UAT posture. Flip only once those are recorded ✅. Phase-verification/completion is the orchestrator's call, NOT this plan's."

patterns-established:
  - "The 3-exemplar foundation (PrintStatus = multi-state, FineTune = capability/busy, Spool = preview-safe-image/dense-data) is now a documented LAW (PREVIEW_AND_TOKENS.md). Phases 19-21 copy the nearest archetype mechanically."

requirements-completed: [SC-1, SC-2, SC-3, SC-5, D-01, D-02, D-05]

# Metrics
duration: ~4min (original execution, 19:46–19:50); close-out after crash recovery
completed: 2026-06-06
---

# Phase 18 Plan 07: Spool Exemplar + PREVIEW_AND_TOKENS.md Convention Summary

> ⚠ **Crash-recovery close-out:** This SUMMARY was written by a recovery/close-out pass. The
> original 18-07 executor committed all production + docs work (the 3 commits below) but **crashed
> before writing this SUMMARY and updating STATE.md/ROADMAP.md**. No work was re-implemented; the
> close-out verified the committed work against the plan, re-ran the authoritative build gate on the
> merged state (green), and wrote this SUMMARY + the tracking updates.

**Applied the D-02 template to `SpoolScreen` — the preview-SAFE image + dense-data third archetype — and
then wrote the convention doc that turns the whole 3-exemplar foundation into enforceable LAW. Added
`SpoolPreviews.kt` (a `SpoolSelectionProvider` over no-selection / selected driving the minimized
`@Preview` matrix on `SampleFixtures.spoolList`, `@Nexus7Previews`), state-hoisted Spool into a stateless
`SpoolScreen(state=)` overload + shared `SpoolContent` so previews render with NO live Moonraker, tokenized
the Spool literals to `stringResource` + routed glyphs through `DinghyIconView`/`DinghyIcons`, branched the
preview-unsafe CameraX scan surface (`ScanSurface.kt`) on `LocalInspectionMode`, wrote
`docs/ui_design/PREVIEW_AND_TOKENS.md` + a CLAUDE.md pointer (SC-2), and finalized `18-VALIDATION.md` +
recorded the Phase-22 backfill deferral / no-regression (SC-5).**

## Performance
- **Duration:** ~4 min original execution (commits 19:46–19:50, 2026-06-06); close-out pass after terminal crash.
- **Tasks:** 3 (all executed + committed before the crash)
- **Files:** 8 (2 created, 6 modified) across the 3 implementation/docs commits

## Task Commits
1. **Task 1: Spool @Preview matrix (dense fixtures + D-05 camera branch) + tokenize SpoolScreen** — `97b65a2` (feat)
2. **Task 2: Migrate Spool matchers (NO-OP) + write PREVIEW_AND_TOKENS.md + CLAUDE.md pointer** — `c614aae` (docs)
3. **Task 3: Record Phase-22 deferral + no-regression + finalize VALIDATION map** — `8397891` (docs)

## What Was Built

### Task 1 — Spool preview matrix + state-hoist + D-05 camera branch (`97b65a2`)
- `preview/SpoolPreviews.kt` (new, 142 lines): `SpoolSelectionProvider` (no-selection / selected) driving the
  MINIMIZED matrix per RESEARCH Q8 — selection×one-theme + a 6-theme×selected sweep + fs=L (via `fsLargeSeed`)
  + an RTL spot-check, all wrapping `SpoolContent` in `PreviewBox(seed)` over the dense `SampleFixtures.spoolList`,
  all `@Nexus7Previews` (portrait+landscape). `PreviewBox(` ×9, `SampleFixtures.spoolList` ×5 (grep-confirmed).
- State-hoist: added a stateless `SpoolScreen(state=)` overload + a shared `SpoolContent` seam (no holder/
  dispatcher/Moonraker) — the documented anchor pattern; the live path delegates to `SpoolContent`.
- **Tokenization:** 20 `stringResource(spool_*/cd_spool_*/common_back)` sites (9 lines carry the `spool_` prefix,
  the remainder are `cd_spool_*`/`common_back`); 7 `DinghyIconView(DinghyIcons.*)` glyph sites
  (Inventory/Storefront/Palette/Scale/Edit/CalendarAddOn/Nozzle/HeatBed/CheckCircle/Archive);
  `spoolWeightText`/`tempText` tokenized.
- **D-05 camera branch:** the CameraX `PreviewView` in `ScanSurface.kt` branched on `LocalInspectionMode`
  → `PreviewPlaceholderBox` (2 `LocalInspectionMode` references, grep-confirmed).
- **Icons:** +Inventory/Palette/CalendarAddOn/CheckCircle/Archive registered in `DinghyIcons` (unique alternates).
- **strings.xml:** +29 lines — `spool_*` labels/badges/weight/temp + `cd_spool_*` a11y keys.
- **D-03 respected:** zero `@Stable`/`@Immutable`/`ImmutableList` (grep = 0).

### Task 2 — PREVIEW_AND_TOKENS.md + CLAUDE.md pointer (`c614aae`)
- `docs/ui_design/PREVIEW_AND_TOKENS.md` (new, 229 lines): the authoritative preview-first/tokenized-first LAW
  doc Phases 19-21 follow — the `PreviewBox` idiom, the device/locale-ONLY multipreview note (themes are explicit
  `PreviewBox(seed)` wrappers, NOT the annotation), the minimized-matrix shape, the stateless no-Moonraker seam,
  the ⚠ `fs=L`-via-`fsLargeSeed` gotcha (`@Preview(fontScale=)` is a NO-OP), the `<area>_<element>`/`cd_*` key
  convention + format-arg + plurals example, `DinghyIcon`/`DinghyIconView` registration, the RTL `start`/`end`
  rule + hardware-spatial exceptions, the ≥48dp + `cd_*` rider, the D-04/D-05 placeholder idiom, and the D-03
  EXCLUSION (`@Stable`/`ImmutableList` = Phase 22) + the shared-component/exhaustive-backfill boundary.
- `CLAUDE.md`: one-line enforcement pointer in the UI Design System LAW block (`PREVIEW_AND_TOKENS` present, grep-confirmed).
- **Spool instrumented matcher migration: NO-OP** — there is no `SpoolScreenTest`, and no androidTest asserts the
  Spool literals tokenized in Task 1 (ShellPresenceTest targets App-Drawer tiles, not Spool detail). Recorded as a no-op.

### Task 3 — Phase-22 deferral + no-regression + VALIDATION finalize (`8397891`)
- `18-VALIDATION.md`: per-task verification map filled for all 18-02..18-07 tasks (tier-labeled CI / Studio / flox);
  `status=final`, `wave_0_complete=true`.
- `nyquist_compliant` HELD `false` (Codex LOW-9): every CI row ✅ (3 exemplars compile/tokenize/full unit suite
  green, no regression) but the Studio-eyeball + flox rows are owner-DEFERRED ⬜ pending (Phase-17-UAT posture) —
  flip only once those are recorded ✅, not merely planned.
- Phase-22 deferral confirmed already recorded in ROADMAP §Phase 22 (the exhaustive preview/string/icon backfill +
  light final conformance sweep); the foundation changes are additive (the 3 host D-05 branches are preview-only;
  the 3 exemplars preserve layout/tokens — no existing screen regresses).

## Deviations from Plan

### [Rule 1/clarification — D-05 target] Camera surface branched, not a Coil thumb
- **Found during:** Task 1.
- **Issue:** The plan's `<action>` and key_links named a "Coil filament-thumb `AsyncImage`" as the D-05
  `LocalInspectionMode` branch target. SpoolScreen has **no** Coil `AsyncImage` — its only image is the QR at
  `SpoolScreen.kt:379`, which is `painterResource` (preview-safe; no branch needed, as the plan itself noted).
- **Fix:** The genuinely preview-unsafe surface in the Spool flow is the CameraX scan `PreviewView`
  (RESEARCH Q5), so the `LocalInspectionMode` → `PreviewPlaceholderBox` branch was applied to `ScanSurface.kt`.
  This satisfies D-05's intent (branch the preview-unsafe surface) and the must-have truth.
- **Files:** `ScanSurface.kt`. **Commit:** `97b65a2`.

### [Process — crash recovery] SUMMARY + tracking written post-crash
- **Found during:** close-out pass. The original executor crashed after the 3 commits but before writing this
  SUMMARY / advancing STATE.md / flipping ROADMAP. The close-out re-verified the committed work against the plan,
  re-ran the authoritative build gate on the merged state (green), and wrote this SUMMARY + tracking. **No work re-implemented.**

Everything else executed as written.

## Phase-22 Backfill Notes
- The exhaustive every-screen backfill — all remaining `@Previews`, the ~240 string-literal extraction, full icon
  call-site migration, a11y/RTL/`@Stable` riders, and the `compose-preview-screenshot` golden-image net — is
  DEFERRED to the Phase-22 conformance sweep as ONE co-sequenced per-screen pass (SC-5, D-03), confirmed against
  ROADMAP §Phase 22.
- Shared components reused across unrelated screens keep their literals (tokenization boundary, Codex MEDIUM-6).

## Known Stubs
None. The stateless `SpoolScreen(state=)` overload is the intended preview seam (the live overload renders
identically on-device), not a stub. The `LocalInspectionMode` camera placeholder is the intended D-05 idiom.

## Threat Flags
None — behaviour-neutral state-hoist + tokenization + host-rendered previews + a preview-only camera branch + docs;
no new trust-boundary surface (matches the plan's accepted T-18-07-01).

## Verification Summary
- **Authoritative gate (close-out, merged state):** `:app:assembleDebug` + full `:app:testDebugUnitTest` +
  `:app:compileDebugAndroidTestKotlin` — **BUILD SUCCESSFUL, exit 0** (62 tasks; the committed state was
  already UP-TO-DATE, confirming no regression on the merged tree). `:app:lintDebug` NOT a gate (known
  AGP-8.7/JDK-21 crash, neutralized via `abortOnError=false`).
- **Grep (CI-assertable, re-confirmed in close-out):** `SpoolPreviews.kt` `PreviewBox(`×9 + `SampleFixtures.spoolList`×5;
  `SpoolScreen.kt` `stringResource(R.string.spool_`×9 + `DinghyIconView(DinghyIcons.`×7; `LocalInspectionMode`×2 in
  `ScanSurface.kt`; `@Stable`/`@Immutable`/`ImmutableList` = 0 in SpoolScreen; `PREVIEW_AND_TOKENS` in CLAUDE.md;
  `PreviewBox` keyword present in PREVIEW_AND_TOKENS.md.
- **Studio (phase gate, ⬜ owner-DEFERRED):** Spool no-selection/selected × 6 combos + fs=L + RTL render; dense list +
  camera placeholder labeled — human-eyeball, deferred (Phase-17-UAT posture).
- **flox (⬜ owner-DEFERRED):** `:app:connectedDebugAndroidTest` Spool smoke + the 18-04 `start_dest` gate — on-device, deferred.

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/preview/SpoolPreviews.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt (tokenized + state-hoisted)
- FOUND: app/src/main/java/works/mees/dinghy/ui/spool/scan/ScanSurface.kt (LocalInspectionMode branch)
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt (new entries)
- FOUND: app/src/main/res/values/strings.xml (new keys)
- FOUND: docs/ui_design/PREVIEW_AND_TOKENS.md (229 lines)
- FOUND: CLAUDE.md (PREVIEW_AND_TOKENS pointer)
- FOUND: .planning/phases/18-preview-harness-tokenization-foundation/18-VALIDATION.md (finalized)
- FOUND commit: 97b65a2 (Task 1)
- FOUND commit: c614aae (Task 2)
- FOUND commit: 8397891 (Task 3)
- BUILD GATE: assembleDebug + testDebugUnitTest + compileDebugAndroidTestKotlin exit 0 (merged state, no regression)

---
*Phase: 18-preview-harness-tokenization-foundation*
*Completed: 2026-06-06 (close-out after crash recovery)*
