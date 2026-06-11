# Phase 25: Browse Screens - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-10
**Phase:** 25-browse-screens
**Areas discussed:** Files & Console Views-vs-Compose, Files screen anatomy, Macros nav/form/flow, Console & Webcam shaping

---

## Files & Console: Views vs Compose

| Option | Description | Selected |
|--------|-------------|----------|
| Measure first, then decide per-surface | Wave-0 flox gfxinfo spike: Compose LazyColumn+ListRow vs current Views per surface; parity → migrate, jank → keep Views + visual-conform | ✓ |
| Keep Views, conform visually | Restyle RecyclerView rows to match ListRow, wrap with Compose chrome; zero perf risk; duplicated styling | |
| Go full Compose, gated | Rewrite both to Compose ListRow, hard flox gate; reopens the perf question Phase 22 avoided | |

**User's choice:** Measure first, then decide per-surface.
**Notes:** Data-driven, matches the project's measure-on-flox discipline. Pass/fail bar = ADR-0001 Addendum-2 (0 frozen frames + p90 within floor budget). Per-surface independent decision. (→ D-01/02/03)

---

## Files screen anatomy

| Option | Description | Selected |
|--------|-------------|----------|
| Spoolman two-pane | Image-backed DetailCard Focus + ListBlock/FootButtonBar Field | ✓ |
| List-only, push detail | Field-only list, tap pushes detail screen | |

| Option (folders) | Description | Selected |
|--------|-------------|----------|
| In-place drill + leading Up row | Folder ListRows, drill in, ".." up row | |
| In-place drill + breadcrumb | Drill in + breadcrumb path strip | |
| Flat, no folders | All gcode flat, ignore directory structure | ✓ |

| Option (filter) | Description | Selected |
|--------|-------------|----------|
| Sort-only (name/date/size) | Three sort tiles, no filter | |
| Sort + a filter facet | Field-takeover filter (folder / has-thumbnail) | (initially ✓, then revised) |

**User's choice:** Spoolman two-pane; flat (no folder nav); **revised** the filter answer mid-discussion to: "Never mind for now, stick to file age as only sorting metric."
**Notes:** Net result — flat list, sort by **age only** (direction toggle), **no filter facet**, no name/size sort. A follow-up question on what the filter would filter by triggered the simplification. (→ D-04/05/06)

---

## Macros: nav + form + flow

| Option (nav) | Description | Selected |
|--------|-------------|----------|
| One screen, Bookmarked primary + Manage mode | Idle entry → launcher; System manage reached via foot button; shared MacroHolder | ✓ |
| Two separate nav routes | Bookmarked + System each a Navigation-Compose destination | |

| Option (form) | Description | Selected |
|--------|-------------|----------|
| Tile grid (controls), density fix | Filled launch tiles in a grid, fill ~9-12 then scroll | |
| ListRow list (content) | Each bookmark a translucent ListRow, tap to execute | ✓ |

| Option (param entry) | Description | Selected |
|--------|-------------|----------|
| Keep full-screen popup, restyle | Multi-field form stays a floating overlay, restyled | |
| Field-takeover per param | Tapping a param swaps the Field to its entry surface | ✓ |

| Option (flow refinements) | Description | Selected |
|--------|-------------|----------|
| Auto-jump to Console after firing only | Navigate to Console after a macro executes | |
| Both: auto-jump + Console↔Macros button | Also a direct nav button | |
| Defer both to ship polish | Keep this phase to the visual migration | ✓ |

**User's choice:** One screen (Bookmarked primary + Manage mode); ListRow list; Field-takeover per param; defer flow refinements.
**Notes:** Converts the Phase-24 D-01 in-screen sub-nav. Density fix folded in. Macro-prompt-protocol dialogs stay overlays (distinct from param entry). (→ D-09..D-13)

---

## Console & Webcam shaping

| Option (Console filters) | Description | Selected |
|--------|-------------|----------|
| Field-takeover filter picker | Filter tile swaps Field to the toggle list | |
| FootButtonBar toggles | 3 always-visible filter toggles at the foot of the log | ✓ |

| Option (Webcam scope) | Description | Selected |
|--------|-------------|----------|
| Cam-picker-as-ListRow Field, render untouched | Restructure picker into a ListRow list + fix crash | |
| Token-conform only, no restructure | Re-token chrome + FootButtonBar, render untouched | ✓ |

**User's choice:** Console = restyled foot toggles; Webcam = token-conform only, no restructure.
**Notes:** Console stays Field-only log; D-04 raw-scrollback-survives preserved. Webcam light touch — but the deferred webcam-screen crash fix is treated as mandatory (a crashing screen can't be owner-approved). (→ D-14..D-18)

---

## Claude's Discretion
- Spike harness shape + per-surface decision recording (D-01).
- Files single-metric sort control rendering (full SortFilterControlRow vs leaner date-direction toggle).
- Macros "Manage mode" toggle affordance + Execute foot button vs param Field-takeover coexistence.
- NavHost route shape for rebuilt screens (entry points already exist from Phase 24).

## Deferred Ideas
- Console↔Macros nav-flow refinements (auto-jump, nav button) → Phase 29 ship polish.
- Files filter facets (folder/has-thumbnail/file-type) → future, if real need emerges.
- Webcam picker as a true ListRow list / restructure → future.
- Console filters as Field-takeover / dedicated sub-page → not adopted (kept foot toggles).
