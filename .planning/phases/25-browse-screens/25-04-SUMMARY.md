---
phase: 25-browse-screens
plan: "04"
subsystem: ui
tags: [console, views, recycler-view, footbuttonbar, filter-toggles, preview, spike-verdict]
dependency_graph:
  requires:
    - phase: 25-browse-screens
      plan: "01"
      provides: "Console=Views spike verdict (~8× Compose regression on Adreno 320)"
    - phase: 25-browse-screens
      plan: "02"
      provides: "Registered DinghyIcon tokens (HideTemps, HideTimelapse, HidePrompts, Back)"
  provides:
    - "Rebuilt ConsoleScreen on Phase-23 kit (field-only, gutter=null, FootButtonBar filter toggles)"
    - "Stateless ConsoleScreen(lines,...) preview seam"
    - "ConsolePreviews.kt @Preview matrix (6 theme combos + fs=L + landscape)"
    - "COMPONENTS.md §8 class-equivalent (Views) exception table"
  affects:
    - "AppShell NavDest.Console composable (unchanged — live overload signature preserved)"
    - "25-07 spike teardown (ConsoleListView.kt not deleted — teardown owns cleanup)"
tech_stack:
  added: []
  patterns:
    - "Toolkit: Views (binding spike verdict — ConsoleListView RecyclerView retained, not replaced)"
    - "Two-overload seam: ConsoleScreen(holder,...) + ConsoleScreen(lines,...)"
    - "D-04/D-15 invariant: ConsoleFilters.apply at render only off rawLines, NEVER in holder"
    - "ScreenScaffold(focus=null, gutter=null) — field-only per D-14"
    - "BoxWithConstraints + rememberUnitGrid + .height(maxHeight) pin (Files scroll lesson)"
    - "FootButtonBar with 3 OutlinedControl filter toggles (Intent.Accent active / Intent.Neutral inactive)"
    - "All icons via DinghyIcons.* registered tokens — no raw ligature strings"
    - "stringResource for all visible strings (no raw literals)"
key_files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/ConsolePreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
    - app/src/main/res/values/strings.xml
    - docs/ui_design/COMPONENTS.md
key_decisions:
  - "Console toolkit: Views (RecyclerView retained) — binding spike verdict from 25-01 (~8× p90 regression under live-churn on Adreno 320)"
  - "COMPONENTS.md §8 class-equivalent (Views) exception table added for ConsoleListView surface"
  - "Filter toggles moved from gutter to FootButtonBar (D-15: gutter=null on rebuilt screens)"
  - "ConsoleListView unchanged — visually conformed via token routing in ConsoleRowsAdapter (adapter already reads palette)"
  - "Stateless preview seam uses pre-filtered lines — caller is responsible for applying ConsoleFilters before passing"
requirements-completed: []
metrics:
  duration: "~20 min"
  completed: "2026-06-10"
  completed_tasks: 2
  total_tasks: 2
---

# Phase 25 Plan 04: Rebuild ConsoleScreen on the Phase-23 Kit — Summary

**One-liner:** ConsoleScreen rebuilt field-only with Views RecyclerView scrollback (spike verdict applied), render-time filter toggles in FootButtonBar using DinghyIcons tokens, raw-holder invariant preserved, and the full @Preview matrix shipped.

## Toolkit Verdict Applied

**Console: Views** — the 25-01 spike returned p90 73.35ms (vs Views baseline 9.26ms), ~8× regression under live-churn on Adreno 320. ADR-0001 gate FAIL. This plan retains `ConsoleListView` (RecyclerView, `stackFromEnd`, `isSingleAppend`/`isAppendEvict` incremental paths) and visually conforms it to the kit. A `COMPONENTS.md` §8 class-equivalent (Views) exception entry was added.

## What Was Built

### Task 1 — Rebuild ConsoleScreen (commit `55755c3`)

Rebuilt `ConsoleScreen.kt` on the Phase-23 component kit:

**Two-overload seam:**
- Live: `ConsoleScreen(holder, onBack, backfillFailed)` — collects `holder.state` (RAW lines), holds 3 local `mutableStateOf` filter flags, computes `ConsoleFilters.apply(rawLines, ...)` at render, delegates to `ConsoleContent`
- Stateless: `ConsoleScreen(lines, rawLineCount, backfillFailed, hideTemps, hideTimelapse, hidePrompt, onToggle*, onBack)` — no holder, no `collectAsStateWithLifecycle`, drives the @Preview matrix

**ConsoleContent layout:**
- `BoxWithConstraints` → `rememberUnitGrid(minOf(maxWidth, maxHeight))` → `ScreenScaffold(focus = null, gutter = null)` (D-14 field-only)
- Field = inner `BoxWithConstraints(weight(1f))` wrapping `ConsoleListView(Modifier.fillMaxWidth().height(maxHeight))` (pinned height — Files scroll lesson) + empty-state/backfill-failed overlays
- `FootButtonBar(uDp = grid.uDp)` containing 4 `OutlinedControl` items:
  - HideTemps toggle: `DinghyIcons.HideTemps` / `Intent.Accent` (active) or `Intent.Neutral` (inactive)
  - HideTimelapse toggle: `DinghyIcons.HideTimelapse` / `Intent.Accent` or `Intent.Neutral`
  - HidePrompts toggle: `DinghyIcons.HidePrompts` / `Intent.Accent` or `Intent.Neutral`
  - Back: `DinghyIcons.Back` / `Intent.Neutral` (plain nav — D-10)

**D-04/D-15 invariant preserved:** `ConsoleFilters.apply` is called in the live overload before passing `filtered` to `ConsoleContent`. `ConsoleHolder` is untouched — it stores RAW lines only.

**Strings:** `console_empty_title`, `console_empty_body`, `console_backfill_failed` added to `strings.xml` (append-only, distinct from 25-02/03 keys).

**COMPONENTS.md:** §8 "Class-equivalent (Views) exceptions" section added with ConsoleListView entry and measurement provenance.

**Acceptance criteria all met:**
- `ConsoleFiltersTest` green ✓
- `ConsoleFilters.apply` in ConsoleScreen: 2 ✓ (live + stateless path)
- `ConsoleFilters.apply` in ConsoleHolder: 0 ✓
- No `.id` ConsoleLine reference: 0 ✓
- `focus = null` count: 3 ✓
- `gutter = null` count: 4 ✓
- No raw `thermostat`/`videocam`/`chat_bubble` ligatures in active code: 0 ✓
- 3 `DinghyIcons.Hide*` tokens present: 3 ✓
- `:app:assembleDebug` GREEN ✓

### Task 2 — @Preview matrix + tokenized strings (commit `c51d434`)

**ConsolePreviews.kt:** 10 `@Nexus7Previews` + 1 explicit landscape `@Preview` = 21 total renders.

Matrix:
- Filter-state axis (2 variants × 2 orientations via `@Nexus7Previews`): all-filters-off / hideTemps active (shows Accent toggle state)
- 6-theme matrix on all-filters-off state: colorfulDark/Light, simpleDark/Light, highContrastDark/Light
- fs=L overflow shot (`fsLargeSeed` — NOT `@Preview(fontScale=)` which is a NO-OP)
- Explicit landscape spot-check: `@Preview(widthDp=800, heightDp=480)`

**Fake fixtures:** 12 `ConsoleLine` entries (realistic mix: normal gcode, temperature echo, timelapse frame, prompt command, error, warning — demonstrates all three filter categories + the none-filtered base case).

**Preview-safe:** Drives stateless `ConsoleScreen(lines=...)` overload. `ConsoleListView` (RecyclerView inside `AndroidView`) renders as an Android-layer placeholder in preview — correct D-05 behaviour for Views-in-Compose surfaces.

`:app:assembleDebug` GREEN (SC-4 @Preview compile gate).

## Deviations from Plan

### Design decisions made during implementation

1. **Stateless preview seam uses pre-filtered lines** — the live overload computes `ConsoleFilters.apply` internally, but the stateless overload receives pre-filtered lines. The preview for `hideTemps=true` applies the filter at the fixture level (Python-style `filterNot` in `ConsolePreviews.kt`). This is correct: the invariant is that `ConsoleFilters.apply` is called at render before display — the stateless seam shows the rendered (already-filtered) result, not the raw list.

2. **`ConsoleListView.kt` left unchanged** — the file visually conforms to the kit already via `ConsoleRowsAdapter`'s token-based `ConsoleRowPalette`. The adapter reads `t.surface`, `t.stop`, `t.heat`, `t.text`, `t.go`, `t.text3`, and `t.fs` — all semantic tokens. No adapter changes were needed. Teardown (deleting the unused `ConsoleListView` if Compose had been chosen) is not applicable since Views is retained.

None — plan executed exactly as written. No Rule 1/2/3 auto-fixes needed.

## Known Stubs

None. All visible fields wire through `ConsoleHolder.state` (raw → filtered → ConsoleListView) in production, and through explicit fake fixtures in previews.

## Threat Surface Scan

No new network endpoints, auth paths, or file access patterns. ConsoleScreen is read-only (CONS-01 send path remains deferred). T-25-04-01 (render-time filter) mitigated — `ConsoleFilters.apply` verified at render, `ConsoleHolder` untouched (`ConsoleFiltersTest` enforces re-reveal invariant). T-25-04-02 (rendering untrusted text) accepted — text rendered as inert `Text` composable in Compose (via `ConsoleRowsAdapter.onBindViewHolder` setting `textView.text = line.rawMessage` in the Views path — no eval, no HTML rendering).

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` — FOUND ✓
- `app/src/main/java/works/mees/dinghy/preview/ConsolePreviews.kt` — FOUND ✓
- Commit `55755c3` (Task 1) — FOUND ✓
- Commit `c51d434` (Task 2) — FOUND ✓
- `ConsoleFiltersTest` GREEN ✓
- `:app:assembleDebug` GREEN ✓
- `ConsoleFilters.apply` in ConsoleScreen ≥1: 2 ✓
- `ConsoleFilters.apply` in ConsoleHolder: 0 ✓
- No `.id` on ConsoleLine: 0 ✓
- `focus = null` ≥1: 3 ✓
- `gutter = null` ≥1: 4 ✓
- No raw filter ligatures: 0 ✓
- `DinghyIcons.Hide*` tokens: 3 ✓
- `@Preview` / `@Nexus7Previews` annotations in ConsolePreviews.kt: 15 (21 renders) ✓
- Landscape preview (widthDp=800): 2 references ✓
- `ConsoleScreen(` in ConsolePreviews.kt: 11 ✓
- COMPONENTS.md class-equivalent exception added: ✓
