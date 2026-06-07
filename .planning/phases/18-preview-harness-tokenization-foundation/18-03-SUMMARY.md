---
phase: 18-preview-harness-tokenization-foundation
plan: 03
subsystem: ui
tags: [design-system, icons, strings, tokenization, accessibility, kotlin, compose]

# Dependency graph
requires:
  - phase: 18-01
    provides: DinghyIconsTest compile scaffold + the ic_* exemplar drawables (lock/status/babystep) + detekt baseline tolerating untokenized literals
  - phase: 18-02
    provides: preview/fixture harness (SampleFixtures + PreviewPlaceholderBox) the icon tokens render inside
provides:
  - "IconRef sealed interface (Ligature|Drawable) + DinghyIcon(primary, alternate) data class — D-07 remap handle, D-08 label-free"
  - "DinghyIcons registry object (exemplar icons only) + DinghyIcons.all list (Phase-22 readiness + subset-symbols source)"
  - "DinghyIconView render primitive: Ligature -> MaterialSymbol, Drawable -> painterResource; sizeDp one-unit API; a11y semantics; dpToSp helper"
  - "res/values/strings.xml master vocabulary + <area>_<element>/cd_* key convention + format-arg + plurals proofs"
affects: [18-05, 18-06, 18-07, phase-22-tokenization-backfill, tools/subset-symbols]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Semantic icon token = sealed one-of-N source (IconRef) + canonical alternate remap handle; one render primitive delegating to the two icon sources"
    - "strings.xml <area>_<element> + cd_* key convention; IN=app vocabulary / OUT=pass-through data"
    - "dpToSp() single-point dp->sp derivation documenting the DinghyTheme fontScale=1f invariant"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcon.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt
    - app/src/main/res/values/strings.xml
  modified:
    - app/src/test/java/works/mees/dinghy/designsystem/icons/DinghyIconsTest.kt

key-decisions:
  - "Adopted RESEARCH Q6 sealed-interface icon shape WITH 3 owner+Codex amendments (verdict: adopt-with-amendments)"
  - "Amendment 1 (a11y): ligature branch owns semantics so TalkBack never speaks the raw ligature name"
  - "Amendment 2 (dp->sp): named dpToSp() helper documents the fontScale=1f pin assumption"
  - "Amendment 3 (Phase-22 readiness): DinghyIcons.all hand-rolled list; uniqueness test iterates it (no reflection)"

patterns-established:
  - "DinghyIconView(icon, modifier, tint, sizeDp, contentDescription) — the app's one icon render entry point"
  - "Every DinghyIcons entry carries a non-blank UNIQUE alternate; the registry is the subset-symbols source going forward"

requirements-completed: []

# Metrics
duration: ~20min
completed: 2026-06-06
---

# Phase 18 Plan 03: Tokenization Substrates (icon registry + strings.xml) Summary

**Semantic icon-token registry (sealed `IconRef` {Ligature|Drawable} + `DinghyIcon(primary, alternate)` + `DinghyIconView` delegating to MaterialSymbol/painterResource) plus the master `strings.xml` vocabulary with `<area>_<element>`/`cd_*` conventions, a format-arg and a plurals proof — the two tokenization substrates the exemplar plans extend.**

## Performance

- **Duration:** ~20 min
- **Tasks:** 2 executed (Task 1 was the decision checkpoint, resolved by owner before this run)
- **Files modified:** 5 (4 created, 1 converted)

## Accomplishments
- `IconRef`/`DinghyIcon`/`DinghyIcons`/`DinghyIconView` icon-token system unifying the app's two icon sources behind one swap point with the D-07 alternate remap handle, kept label-free per D-08.
- `DinghyIconsTest` converted from the 18-01 compile scaffold to live assertions — non-blank alternate + resolvable IconRef per entry, AND `alternate` uniqueness over `DinghyIcons.all`. GREEN.
- `res/values/strings.xml` established as the master English vocabulary with the key convention + a format-arg (`printstatus_layer_progress`) + a `<plurals>` (`spool_count`) proof.

## Task Commits

1. **Task 2: Icon-token registry + DinghyIconsTest GREEN** - `1ab1c22` (feat)
2. **Task 3: strings.xml master file + key convention** - `08e4db2` (feat)

**Plan metadata:** (final docs commit)

## Files Created/Modified
- `designsystem/icons/DinghyIcon.kt` - `sealed interface IconRef` (`@JvmInline` Ligature/Drawable) + `data class DinghyIcon(primary, alternate)`, no label (D-08).
- `designsystem/icons/DinghyIcons.kt` - `object DinghyIcons`, 18 exemplar entries (11 ligature + 7 drawable) + `val all: List<DinghyIcon>`.
- `designsystem/icons/DinghyIconView.kt` - render primitive; `when(icon.primary)` → MaterialSymbol / Icon(painterResource); `sizeDp` API; a11y semantics; `dpToSp()` helper.
- `app/src/main/res/values/strings.xml` - master vocabulary + conventions + format-arg/plurals proofs.
- `designsystem/icons/DinghyIconsTest.kt` - converted scaffold → 2 live tests (contract + uniqueness over `DinghyIcons.all`).

## Decisions Made

**Task-1 checkpoint resolution (Codex-reviewed, owner-decided): adopt the RESEARCH Q6 sealed-interface shape WITH 3 amendments (verdict: adopt-with-amendments).**

Base shape (RESEARCH Q6, unchanged): `sealed interface IconRef { Ligature(name) ; Drawable(resId) }`; `data class DinghyIcon(primary, alternate)` (alternate = canonical remap handle D-07, required unique; no fused label D-08); `DinghyIconView` `when(icon.primary)` delegating Ligature → the unchanged MaterialSymbol primitive, Drawable → `Icon(painterResource)`.

- **Amendment 1 (CRITICAL — a11y):** the MaterialSymbol glyph renders the ligature NAME as `Text`, which would leak that name to TalkBack. The ligature branch now owns semantics: non-null `contentDescription` → `Modifier.semantics { contentDescription = cd }`; null → `Modifier.clearAndSetSemantics {}` (decorative). The Drawable branch's cd is wired through `Icon`'s own parameter (correct decorative/labelled on null/non-null).
- **Amendment 2 (IMPORTANT — dp→sp):** introduced `internal fun dpToSp(sizeDp): Float` with KDoc documenting that the 1:1 mapping is pixel-correct ONLY because `DinghyTheme` pins `fontScale = 1f` (DinghyTheme.kt:48). Call sites pass ONE unit (`sizeDp`); the Drawable branch is never sized in raw sp.
- **Amendment 3 (Phase-22 readiness):** added `val all: List<DinghyIcon>` (hand-rolled) to `DinghyIcons`; the uniqueness test iterates `DinghyIcons.all` (NOT reflection) and asserts `alternate` is unique across all entries.

Icon-set selection: registered ONLY the icons the 3 exemplar screens (PrintStatus/FineTune/Spool) use — 18 entries (e.g. Back, Check, FineTune `instant_mix`, Layers, Scale, Storefront, the babystep/status/nozzle/heat_bed drawables). The ~50 other raw call sites stay un-tokenized until Phase 22 (D-03).

## Deviations from Plan

### Out-of-scope tool crash (NOT auto-fixed — documented per scope boundary)

**1. Pre-existing AGP-8.7 / JDK-21 lint detector crash on `:app:lintDebug`**
- **Found during:** Task 3 verification (the plan's literal `assembleDebug :app:lintDebug` command).
- **Issue:** `:app:lintDebug` crashes with `IncompatibleClassChangeError` (`Found class KaCallableMemberCall, but interface was expected`) inside the bundled `NonNullableMutableLiveDataDetector` while analyzing `MoonrakerAuth.kt` — a tool/JDK incompatibility, NOT a code defect, and entirely unrelated to the static `strings.xml` this task added.
- **Why not fixed:** This is a known pre-existing condition — `app/build.gradle.kts:84-91` already documents it and sets `lint { checkReleaseBuilds = false; abortOnError = false }`, so it does NOT gate real builds. It originates in `MoonrakerAuth.kt` (untouched here) → out of scope per the executor scope boundary.
- **Resolution:** Verified the REAL gates instead: `:app:assembleDebug` GREEN (resources merged/processed → strings.xml format-args + plurals are valid) and `DinghyIconsTest` GREEN. The plan's own acceptance note already states "lint MissingTranslation tolerated; the gate is the new-literal detekt rule." No `:app:detekt` Gradle task exists in this repo (the "detekt baseline" is a conceptual/separate mechanism, not a wired task) — so the authoritative substitute gate is `assembleDebug` + the unit test, both green.

---

**Total deviations:** 1 documented out-of-scope tool crash (not fixed, pre-existing & already neutralized in build config).
**Impact on plan:** None on deliverables. Both tasks' real gates pass; the lint crash is orthogonal infrastructure noise the repo already accepts.

## Issues Encountered
- The plan's verify line invokes `:app:lintDebug`, which surfaces the documented pre-existing lint detector crash. Worked around by verifying via `:app:assembleDebug` (the build gate `lint{abortOnError=false}` makes authoritative) + `DinghyIconsTest`.

## User Setup Required
None - no external service configuration required.

## Next Phase Readiness
- Both tokenization substrates are live: exemplar plans 18-05/06/07 ADD their per-screen string keys + register any new icons by extending `DinghyIcons` (+ `DinghyIcons.all`).
- `tools/subset-symbols` can now switch to iterating `DinghyIcons.all` for the ligature subset list (no existing artifact breaks — only exemplar icons registered).
- Phase-22 backfill (~240 literals, ~50 raw icon sites) remains deferred (D-03).

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcon.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIconView.kt
- FOUND: app/src/main/res/values/strings.xml
- FOUND commit: 1ab1c22 (Task 2)
- FOUND commit: 08e4db2 (Task 3)

---
*Phase: 18-preview-harness-tokenization-foundation*
*Completed: 2026-06-06*
