---
phase: 23-design-language-foundation
plan: "03"
subsystem: designsystem-icons-control
tags: [wave-1, icon-registry, control-api, css-fix, tdd]
dependency_graph:
  requires:
    - 23-01 (verify_ligatures.py + DinghyIconsTest baseline GREEN)
  provides:
    - 6 owner-assigned Phase-23 redesign glyphs registered in DinghyIcons.kt + DinghyIcons.all
    - img/material-icon-bucket.json play_circle/stop_circle notes reconciled
    - hifi.css .ctl.warn caution-reads-amber (oklch color-mix bug fixed)
    - OutlinedControl DinghyIcon-aware overload + ligatureOf helper (control-API boundary closed)
  affects:
    - 23-05 (SortRow/FilterRow/FootButtonBar/FloatingEStop consume the DinghyIcon overload)
    - 23-06 (SpoolScreen pilot rebuild consumes DinghyIcons.ExpandCircleUp/Down + Sort/FilterList)
tech_stack:
  added: []
  patterns:
    - DinghyIcon.kt val-entry registration + DinghyIcons.all hand-rolled list (established Phase 19 pattern)
    - TDD RED (fail() test) -> GREEN (implement) -> no refactor needed (two-phase cycle)
    - Internal pure helper (ligatureOf) as the host-testable seam for a Compose composable
key_files:
  created:
    - app/src/test/java/works/mees/dinghy/designsystem/control/OutlinedControlIconTest.kt
  modified:
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - img/material-icon-bucket.json
    - docs/ui_design/reference/hifi.css
    - app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt
decisions:
  - "ligatureOf throws IllegalArgumentException for Drawable-backed icons — control glyphs in this design system are ALWAYS ligatures; a Drawable in a control symbol slot is a programming error, surfaced loudly"
  - "DinghyIcon overload delegates entirely to the existing String? overload — one rendering path, no duplicated body, full back-compat for pre-redesign call sites"
  - ".ctl.warn CSS fixed to var(--heat) direct — oklch short-path hue interpolation through red eliminated; Kotlin side was already correct (t.heat direct)"
metrics:
  duration: "~25 minutes"
  completed: "2026-06-09"
  tasks_completed: 4
  files_modified: 5
---

# Phase 23 Plan 03: Icon Registry + Control-API + CSS Fix Summary

**One-liner:** Registered 6 owner-assigned redesign glyphs in DinghyIcons, reconciled stale bucket notes, fixed the oklch amber-reads-red CSS bug, and added a DinghyIcon-aware OutlinedControl overload (TDD, host-tested).

## What Was Built

### Task 1: 6 Phase-23 Redesign Glyphs Registered

Added 6 new `val` entries to `DinghyIcons` object and to `DinghyIcons.all`:

| Val name | Ligature | Function |
|----------|----------|----------|
| `Sort` | `sort` | Sort control-group leader |
| `FilterList` | `filter_list` | Filter control-group leader |
| `ExpandCircleUp` | `expand_circle_up` | Load spool (replaces old play_circle note) |
| `ExpandCircleDown` | `expand_circle_down` | Unload spool (replaces old stop_circle note) |
| `ResetWrench` | `reset_wrench` | Reset a single setting |
| `ResetSettings` | `reset_settings` | Reset all settings |

All owner-assigned from the 2026-06-09 Spoolman sketch session. `DinghyIconsTest` GREEN (drift guard derives dynamically from `DinghyIcons.all`); `verify_ligatures.py` exits 0 (`69 needed, 3953 ligatures in font, missing: []`).

### Task 2: play_circle/stop_circle Bucket Notes Reconciled

Updated `notes` fields for both `play_circle` and `stop_circle` in `img/material-icon-bucket.json`:

- **Before:** `"load spool / swap spool "` / `"unload spool"`
- **After:** `"available for reassignment — load/unload moved to expand_circle_up/expand_circle_down (jiib redesign 2026-06-09)"`

Neither glyph was added to `DinghyIcons.kt`. JSON remains valid (`python json.load` succeeds). The glyphs are retained in the bucket (may be wanted for a future "swap" concept).

### Task 3: `.ctl.warn` oklch Color-Mix Bug Fixed in hifi.css

**Bug:** `.ctl.warn { border-color: color-mix(in oklch, var(--heat) 62%, var(--outline)); }` — the amber-to-blue-gray hue path in oklch takes the short arc through hue ~0 (red), making caution borders read orange-red.

**Fix:** Replaced with `border-color: var(--heat);` — direct amber token, no interpolation.

The Kotlin `OutlinedControl.kt` was already correct (`Intent.Warn -> t.heat` direct); this was CSS-reference-only. Verified by single-line AND broadened per-block grep: zero `color-mix(in oklch ...--heat...--outline)` in the file.

### Task 4: DinghyIcon-Aware OutlinedControl Overload (TDD)

**RED phase:** Created `OutlinedControlIconTest.kt` with 3 failing tests (compile fails: `ligatureOf` unresolved). Committed as RED scaffold.

**GREEN phase:** Added to `OutlinedControl.kt`:

1. `internal fun ligatureOf(icon: DinghyIcon): String` — extracts the ligature name from a `Ligature`-backed `DinghyIcon`; throws `IllegalArgumentException` for `Drawable`-backed icons (programming error, surfaced loudly). This is the host-testable seam.

2. `@Composable fun OutlinedControl(label, onClick, modifier, intent, icon: DinghyIcon?, onLongClick)` — the new redesign-kit overload. Delegates entirely to the existing `symbol: String?` implementation via `symbol = icon?.let { ligatureOf(it) }`. One rendering path, no duplicated body.

The existing `symbol: String?` overload is preserved for pre-redesign call sites (Move/Extrude/etc.). New import: `DinghyIcon`, `IconRef`.

`OutlinedControlIconTest` GREEN: 3/3 (`Sort -> "sort"`, `ExpandCircleUp -> "expand_circle_up"`, Drawable-backed throws IAE).

## Verification

- `DinghyIconsTest` GREEN — drift guard picks up all 6 new entries automatically
- `verify_ligatures.py` exits 0 — `69 needed, 3953 in font, missing: []`
- `img/material-icon-bucket.json` valid JSON; play_circle/stop_circle notes reference expand_circle reassignment
- `hifi.css` single-line + broadened per-block grep = 0 color-mix oklch occurrences on .ctl.warn
- `OutlinedControlIconTest` GREEN (3/3) — ligatureOf behavior tested

## Commits

| Hash | Type | Description |
|------|------|-------------|
| `3e58faf` | feat | Register 6 Phase-23 redesign glyphs in DinghyIcons |
| `9b93be2` | chore | Reconcile play_circle/stop_circle bucket notes |
| `ea9f9c8` | fix | Replace color-mix oklch caution-reads-red with --heat direct |
| `4c27186` | test | Add RED OutlinedControlIconTest for ligatureOf behavior |
| `77045b0` | feat | Add DinghyIcon-aware OutlinedControl overload + ligatureOf helper |

## Deviations from Plan

None — plan executed exactly as written.

TDD gate compliance: RED commit `4c27186` (test) precedes GREEN commit `77045b0` (feat). Gate sequence holds.

Note: the full `:app:testDebugUnitTest` suite has 12 pre-existing failures (the Wave-0 RED scaffolds from plan 23-01 — `UnitGridTest`, `ListRowTest`, `FillMeterTest` all have `fail()` bodies by design, to be turned GREEN in plans 23-04 and 23-05). These are not regressions introduced by this plan.

## Known Stubs

None — this plan registers icons, fixes CSS, and adds a control-API overload. No product-facing stubs.

## Threat Flags

No new attack surface — icon registration + CSS reference fix over committed assets. The `ligatureOf` helper rejects non-Ligature icons loudly (programming-error surface, not user/network input).

## Self-Check: PASSED

- `DinghyIcons.kt` contains all 6 new vals + `all` list entries ✓
- `img/material-icon-bucket.json` valid JSON with expand_circle in play_circle/stop_circle notes ✓
- `hifi.css` .ctl.warn uses var(--heat) directly, no color-mix ✓
- `OutlinedControl.kt` contains `ligatureOf` helper + DinghyIcon overload ✓
- `OutlinedControlIconTest.kt` exists at `app/src/test/.../designsystem/control/` ✓
- Commits `3e58faf`, `9b93be2`, `ea9f9c8`, `4c27186`, `77045b0` present in git log ✓
