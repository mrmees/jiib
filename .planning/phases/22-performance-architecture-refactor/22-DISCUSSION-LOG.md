# Phase 22: Performance & Architecture Refactor - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-08
**Phase:** 22-performance-architecture-refactor
**Areas discussed:** Refactor aggressiveness, Measurement strategy, Visual-fidelity bar, Deferred-item scope

---

## Refactor Aggressiveness

| Option | Description | Selected |
|--------|-------------|----------|
| Full restructure | Surgical hot-path fixes + split PrintStatusScreen god-component + push 28 AppShell flow-collections down into screens | ✓ |
| Surgical only | Targeted hot-path fixes only (@Immutable, ImmutableList, remember, guard AndroidView.update, graph fills); leave architecture as-is | |
| Restructure, measure-gated | Surgical first, re-measure, do each structural restructure only if surgical pass doesn't clear the lag | |

**User's choice:** Full restructure
**Notes:** The two architectural moves ARE the root causes per CONCERNS; Phase 23 expects structural work before redesigns; 3 phases of runway + host suite + on-device gate de-risk it. Claude folded in defaults: 6-DataStore consolidation OUT (cold-start, not nav-lag); P0–P2 core, P3 nits opportunistic.

---

## Measurement Strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Baseline-first, manual gfxinfo | Release-mode gfxinfo baseline per suspect screen on flox BEFORE coding, fix, re-capture | ✓ |
| Trust CONCERNS, confirm after | Skip upfront baseline; fix P0/P1, one on-device pass at the end | |
| Wire Macrobenchmark module | Invest in repeatable scripted frame metrics | |

**User's choice:** Baseline-first, manual gfxinfo
**Notes:** Concrete before/after per screen, no tooling tax. Macrobenchmark stays parked (FrameTimingMetric unreliable on API 23). Baseline sweep set seeded from CONCERNS + nav transitions (drawer open, screen-to-screen) since "navigation lag" was the reported symptom.

---

## Visual-fidelity bar

| Option | Description | Selected |
|--------|-------------|----------|
| Reads-the-same on flox | Owner's eye on flox is the bar; provably-invisible flattening ships, subtle changes owner-approved on-device | (premise corrected) |
| Pixel-identical only | Only flatten where bit-identical | |
| Defer glow, do the rest | Punt outline+glow overdraw to Phase 23/24 | |

**User's choice:** "I thought we already took all the glow out?" → triggered an in-code verification.
**Notes:** Owner was correct. Verified: no draw code consumes the `*Glow` tokens; OutlinedControl draws only a 2px border, ProgressRing only arcs. SC3's "stacked outline+glow" premise is STALE. Follow-up question posed with corrected premise:

| Option (overdraw, corrected premise) | Description | Selected |
|--------|-------------|----------|
| Retarget to real overdraw, reads-same bar | Record glow premise stale; retarget SC3 to GraphView fills + stacked alpha/scrim; reads-same-on-flox bar; keep dead *Glow tokens | ✓ |
| Same, and prune dead glow tokens | Also delete unused *Glow tokens this phase | |
| Pixel-identical only | Only bit-identical flattening | |

**User's choice (corrected):** Retarget to real overdraw, reads-same bar. Keep dead glow tokens (23/24 redesigns may revive them).

---

## Deferred-item scope

| Option | Description | Selected |
|--------|-------------|----------|
| Keep 22 pure perf/arch | Rotation→24; FGS icon + spool robustness + gutter wiring→25; 22 stays perf/arch only | ✓ |
| Fold FGS icon in here | Same, plus do the quick FGS notification-icon fix in 22 | |
| Let me reassign individually | Walk each deferred item one at a time | |

**User's choice:** Keep 22 pure perf/arch
**Notes:** The "deferred to Phase 22" labels in CONCERNS/STATE predate the 22→25 restructure. H.264 rotation → Phase 24 (its SC3). FGS Bluetooth→jiib icon, spool robustness, Pause/Resume + Tune gutter wiring → Phase 25. Doc-hygiene follow-up noted: correct the stale labels in CONCERNS.md/STATE.md in a future GSD edit.

---

## Claude's Discretion

- Exact split seams/file names for PrintStatusScreen and which flows move where in the AppShell push-down (behavior-preserving; full @Preview matrix + flox check for any ScreenScaffold/layout-touching change).
- GraphView overdraw approach (secondary-trace opacity vs off-screen-bitmap pre-rasterize) — pick per measurement.
- kotlinx-collections-immutable version pin and converter wiring.

## Deferred Ideas

- H.264-while-rotating fix → Phase 24.
- FGS notification small-icon (24dp jiib status glyph) → Phase 25.
- Spool transient-failure robustness hardening → Phase 25.
- Pause/Resume + Tune gutter wiring → Phase 25.
- Macrobenchmark module wiring → parked (revisit only if manual gfxinfo insufficient).
- 6-DataStore consolidation → backlog (cold-start, not this milestone's target).
- Prune dead *Glow tokens → revisit Phase 23/24.
- Doc-hygiene: correct stale "deferred to Phase 22" notes in CONCERNS.md/STATE.md.
