# Phase 1: Platform Gate — Toolkit Benchmark, Cleartext Smoke Test & Scaffold - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 1-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol
**Areas discussed:** Hardware reality, Toolkit go/no-go bar, Benchmark fidelity, Scaffold shape

---

## Hardware Reality

| Option | Description | Selected |
|--------|-------------|----------|
| Have Nexus 7 + live printer | Nexus 7 2013 in hand + reachable Moonraker (Ender 5 Plus) on LAN; gate runs for real | ✓ |
| Have device, no printer ready | Device available but cleartext test needs a stand-in Moonraker | |
| No Nexus 7 yet | Don't have the target device — blocker for criteria 1 & 2 | |

**User's choice:** Have Nexus 7 + live printer
**Notes:** The whole phase is on real hardware against a real `ws://`/`http://` endpoint — no emulator, no fake server.

---

## Toolkit Go/No-Go Bar

| Option | Description | Selected |
|--------|-------------|----------|
| Recorded gfxinfo metric | Objective `dumpsys gfxinfo` threshold decides; documented in an ADR | ✓ |
| Eyeball + metric backup | User watches on device, makes the call; gfxinfo as supporting evidence | |
| Strict 60fps or bust | Must hold near-solid 60fps or go hybrid Views immediately | |

**User's choice:** Recorded gfxinfo metric
**Notes:** Proposed concrete bar (planner may refine): p95 frame time ≤ 16.6 ms, jank < 10%, no frames over ~700 ms, captured in release mode with Baseline Profile installed. Clears → Compose-everywhere; misses → hybrid Views for high-churn surfaces.

---

## Benchmark Fidelity

| Option | Description | Selected |
|--------|-------------|----------|
| Mimic worst-case panels | Files-style list w/ thumbnails + live temp graph + console spew, all at 2–4 Hz | ✓ |
| Generic list + live counter | Roadmap's literal ask: scroll a LazyColumn + one changing value | |
| Tiered: generic first, then realistic | Cheap generic gate first; build realistic sim only if borderline | |

**User's choice:** Mimic worst-case panels
**Notes:** A clean toy-benchmark result wouldn't predict real thumbnail/graph/console behavior on Tegra-era hardware; the go/no-go must be trustworthy for the actual app.

---

## Scaffold Shape

| Option | Description | Selected |
|--------|-------------|----------|
| App + macrobenchmark module now | Multi-module `:app` + `:macrobenchmark` from day one | ✓ |
| Bare single-module app | One `:app` module that compiles, installs, does cleartext | |
| You decide | Claude picks whichever keeps Phase 1 cleanest | |

**User's choice:** App + macrobenchmark module now
**Notes:** Baseline Profiles are mandatory per the stack docs and the benchmark needs the Macrobenchmark harness anyway — build it once.

---

## Claude's Discretion

- Exact module/package layout, Gradle plugin wiring, and synthetic-feed generation are left to planner/executor.
- Planner may tighten/loosen the proposed gfxinfo threshold numbers if research surfaces a more Nexus-7-appropriate bar; the *form* (recorded gfxinfo metric, release mode) is fixed.

## Deferred Ideas

None — discussion stayed within phase scope. Connection layer, `PrinterState`, panels, and auth were acknowledged as already-scoped to Phases 2+ and not pulled forward.
