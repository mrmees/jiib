# Phase 18: Preview Harness & Tokenization Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-06
**Phase:** 18-preview-harness-tokenization-foundation
**Areas discussed:** Exemplar screen set, Exemplar depth / template, Classic-View surfaces, Icon 'alternate name'

---

## Exemplar screen set

PrintStatus was pre-framed as the obvious anchor (4 `PrintStatusMode` states = textbook
`@PreviewParameter` demo). The question was the other 1-2.

| Option | Description | Selected |
|--------|-------------|----------|
| FineTune + a list screen (BookmarkedMacros) | FineTune proves capability-gating/busy-locks; LazyColumn list proves collection fixtures; both pure Compose | |
| ThemeScreen + FineTune | ThemeScreen = heaviest token consumer; FineTune = capability-gating; skips list proof | |
| Just PrintStatus + one more | 2 exemplars total, tightest scope | |
| You pick the best 2 | Claude chooses | |

**User's choice:** Free text — "Fine tune and spool man" → **FineTune + Spool** (with PrintStatus anchor = 3 total).
**Notes:** Owner chose Spool over the suggested list/macros screen. Spool is the better pick for proving the preview-safe Coil/image strategy (filament thumbs / QR) + dense data fixtures, and exercises a Views-in-Compose picker sub-surface. Final exemplar set = PrintStatus + FineTune + Spool, three distinct archetypes (multi-state / capability-gating / image+dense-data).

---

## Exemplar depth / template

| Option | Description | Selected |
|--------|-------------|----------|
| Core three + RTL/a11y, NOT @Stable | @Preview + strings + icons + start/end RTL + tokenized cd/≥48dp; skip @Stable | ✓ |
| Core three ONLY | @Preview + strings + icons; RTL/a11y/@Stable all deferred | |
| Full per-screen pass | Everything incl. @Stable stability-report + ImmutableList migration | |

**User's choice:** Core three + RTL/a11y, NOT @Stable.
**Notes:** Mechanical riders (a11y descriptions/≥48dp, start/end RTL modifiers) propagate cheaply and belong in the template phases 19-21 copy; `@Stable` is measure-then-fix and risky to bake into a copy-paste template (carpet-`@Immutable` → stale-UI bugs), so it stays deferred to the Phase-22 backfill.

---

## Classic-View surfaces

| Option | Description | Selected |
|--------|-------------|----------|
| Exclude + graceful placeholder | No dedicated View previews; LocalInspectionMode placeholder so embedding screens render cleanly; live truth via start_dest/flox/BenchActivity | ✓ |
| Separate View harness now | Extend BenchActivity's ViewsBenchScene to theme-render GraphView/BedMesh/Webcam | |
| Exclude entirely, no placeholder | Document limitation only; PrintStatus preview shows blank/broken GraphView region | |

**User's choice:** Exclude + graceful placeholder.
**Notes:** `@Preview` is Compose-only. PrintStatus embeds `GraphView`, so a `LocalInspectionMode` placeholder branch (same mechanism as the Coil image strategy) keeps embedding previews clean with a labeled stand-in. View-surface live/perf truth stays on `start_dest` + flox + the existing `BenchActivity`/`ViewsBenchScene` path — no new harness built (scope-creep + still host-rendered).

---

## Icon 'alternate name'

| Option | Description | Selected |
|--------|-------------|----------|
| Swap-handle for forks | Alternate = stable canonical name per token; one-place remap for community icon-set swaps | (current goal) |
| Runtime fallback glyph | Alternate = second concrete glyph auto-rendered if primary missing | |
| Both — fallback AND swap handle | Concrete alternate that renders as fallback AND documents the swap point | |

**User's choice:** Free text — "Eventually I would like to provide an option for icon or text or combo for icons and text. Currently the goal is easy swap/redefine and localisation."
**Notes:** Current-phase model = swap/redefine + localisation handle (the swap-handle direction): semantic token → primary reference (font ligature OR `ic_*` drawable) + alternate/canonical name as the one-place remap point. Owner surfaced a FUTURE want — a per-control icon/text/icon+text presentation mode — which is a new capability deferred to its own phase. Forward-compat captured: keep icon token + label string-token SEPARABLE (don't fuse into a labeled-icon primitive) so the combo mode composes the two later (D-08).

---

## Claude's Discretion

Owner explicitly left these to research/planner:
- `start_dest` readiness-gate interaction (bypass vs no-op-until-Klippy-Ready).
- Fixture-module location + naming (must be reachable from `main` sourceset).
- Where the preview-first/tokenized-first convention is documented (docs/ vs module README vs CLAUDE.md).
- Build-wiring verification of `compose-ui-tooling` (debugImplementation) vs `compose-ui-tooling-preview` (currently `implementation` — confirm leave/move).

## Deferred Ideas

- Per-control icon / text / icon+text combo presentation mode — future user-facing phase.
- Exhaustive every-screen `@Preview` + ~240-literal string backfill + full icon migration + a11y/RTL/@Stable riders + `compose-preview-screenshot` regression net — Phase 22, one co-sequenced per-screen pass.
- `@Stable` stability-report + `ImmutableList` migration — Phase 22.
- Test-matcher migration (literal text → resource-id/testTag) — exemplars only now; rest rides Phase 22.

### Reviewed todos (not folded)
- Phase 11 spool feature robustness hardening (Codex review) — Spool feature robustness, not tokenization.
- Bookmarked macros density (size-to-fit ~9-12 then scroll) — Macros-screen layout fix.
- Dev theme/printer cycler overlay drag-relocate regression — dev-overlay bug, tangential to `start_dest`.
