---
phase: 04-service-shell-settings-print-status-home
plan: 06b
subsystem: print-status-home
tags: [ui, print-status, sparkline, render, views-interop, perf-gate, blocked]
status: BLOCKED-AT-CHECKPOINT
requires:
  - "PrintStatusScreen + PrintStatusHolder (reserved sparkline slot + sparkline StateFlow<FloatArray>) — 04-06"
  - "GraphView / GraphViewHost (classic-Views Canvas, ThemeableView push-tokens seam, D-06) — 03-05"
  - "RingBuffer (bounded rolling window, cap 120, D-12) — 03-02"
  - "LocalTokens (Compose token seam, THEME-01) — 03-03"
provides:
  - "Heater sparkline (GraphViewHost) wired into the reserved Print Status Field slot — recolors on theme flip (D-09 in-anger Views proof)"
affects:
  - "PrintStatusScreen (additive within the Field; Focus/Field/Gutter structure unchanged)"
tech-stack:
  added: []
  patterns:
    - "AndroidView host (GraphViewHost) inside a Compose ScreenScaffold Field slot — factory-once, update pushes tokens+snapshot (D-06 cross-toolkit recolor)"
    - "Holder snapshot fed at the store's existing ~4Hz conflation — no second sampling/throttle layer"
key-files:
  created: []
  modified:
    - "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt"
decisions:
  - "Task 2 (on-device combined-render perf gate + Stop round-trip) is BLOCKED: the production MainActivity is still the Phase-1 scaffold placeholder and NO harness composes the real combined Print Status surface (ring + sparkline + 2x3 grid + gutter). The gate cannot be measured honestly yet — fabricating numbers from the isolated Phase-3 BenchScene would mis-report it. Returned as checkpoint:human-verify (blocking)."
metrics:
  duration: 14
  completed_date: "2026-05-31"
---

# Phase 4 Plan 06b: Print Status Heater Sparkline + Combined-Render Perf Gate Summary

Heater sparkline (classic-Views `GraphView` via `GraphViewHost`) wired into the reserved Print Status
Field slot, recoloring on a theme flip (D-09 in-anger Views proof); the combined-render perf gate is
BLOCKED at the checkpoint because no on-device code path composes the real Print Status surface yet.

## What Was Done

### Task 1 — Heater sparkline wired into the reserved Field slot — COMPLETE (commit `6deb8d5`)

Filled the sparkline slot that `04-06` reserved in `PrintStatusScreen`'s Field, hosting the Phase-3
classic-Views `GraphView` via `GraphViewHost`/`AndroidView` (ADR 0001 — the high-churn graph surface is
Views, not Compose). Wiring copies the `GalleryScreen` analog verbatim:

```kotlin
GraphViewHost(
    tokens = tokens,                  // LocalTokens.current — theme flip recolors the Canvas (D-06)
    snapshot = sparkline,             // holder.sparkline (primary-heater RingBuffer snapshot)
    modifier = Modifier.fillMaxWidth().weight(0.6f),  // exactly the reserved slot — additive, no relayout
)
```

- Fed by `holder.sparkline.collectAsStateWithLifecycle()` (the primary-heater `RingBuffer` snapshot the
  `04-06` holder already exposes) at the store's existing ~4 Hz conflation cadence — **no second
  sampling/throttle layer** (grep for `sample(`/`debounce(`/`delay(` in the file returns nothing).
- `LocalTokens.current` passed through so a dark→light/custom flip recolors the Views Canvas via the
  `applyTokens` push-tokens seam (D-06).
- Fills ONLY the reserved slot (`weight(0.6f)` Box → `GraphViewHost`); the `ScreenScaffold`
  Focus/Field/Gutter call sites from `04-06` are unchanged — the diff is purely additive within the Field.
- `:app:compileDebugKotlin` GREEN.

**Acceptance grep checks (all pass):** `GraphViewHost` present, `holder.sparkline`/`sparkline` fed,
`LocalTokens.current` passed to the host, no second throttle, ScreenScaffold structure intact.

### Task 2 — On-device combined-render perf gate + Stop round-trip — BLOCKED (checkpoint)

This is a `checkpoint:human-verify` (`gate="blocking"`). A real flox device (`0a64b42e`, genuine
Adreno 320 hardware) IS attached, so the gfxinfo-framestats measurement was attempted on-device. It is
blocked by a missing prerequisite, NOT by lack of hardware:

**The production app cannot reach the Print Status screen in this build.** `MainActivity`
(`app/src/main/java/works/mees/dinghy/MainActivity.kt`) is still the **Phase-1 scaffold placeholder** —
it renders `"Dinghy Display — scaffold"` with NO routing, NO `PrinterStateStore`, NO `TopRoute.derive()`
wiring, and NO `PrintStatusScreen`. A release build was assembled (`:app:assembleRelease`), debug-signed
via `sign-release.bat`, and `adb install -r`'d successfully; launching it and screen-capturing confirmed
the scaffold placeholder (evidence: `/tmp/dd-perf/screen1.png`).

**No harness composes the combined surface either.** `grep` across `app/src` for `PrintStatusScreen` /
`PrintStatusHolder` shows they are referenced ONLY by themselves and a host-side holder unit test. The
Phase-3 `BenchActivity`/`RenderBenchScene` host the **isolated** ring+graph (the Phase-3 criterion-#5
path), and the debug-only `GalleryActivity` hosts the component gallery — **neither composes the real
combined Print Status surface** (ring + heater sparkline + live 2×3 numeric grid + gutter) that THIS
gate exists to measure (the D-09 PERF WATCH; 03-PERF-RESULTS.md re-open condition #2 is explicitly about
"the real Temperature screen composites materially heavier than this test path").

**Consequences:**
1. **Perf gate** — there is no on-device code path (production or harness) that draws the combined
   surface, so the gfxinfo-framestats p95 / frozen-frame measurement of THAT surface cannot be taken.
   Re-reporting the isolated Phase-3 BenchScene result (50.1 ms p95) would mis-attribute it as the
   combined-surface number — exactly the post-hoc grandfathering 03-PERF-RESULTS.md forbids. NOT done.
2. **Stop round-trip** — `Stop → ConfirmGuard → printer.emergency_stop → klippy shutdown → Splash` needs
   the wired app graph AND a live Ender 5 Plus reachable; neither is exercisable through the scaffold
   build. NOT done.

This blocker is consistent with the plan's wave ordering: `04-06b` was split out of `04-06` as the
combined-render gate, but the MainActivity screen-graph wiring (MainActivity → TopRoute → Shell →
PrintStatusScreen) is a SEPARATE Phase-4 plan that has not landed. The gate's prerequisite is that
wiring plus a debug harness (or wired app) that can host the real surface on-device.

## On-Device Evidence Captured (Task 2 attempt)

| Step | Result |
|------|--------|
| Device attached | `0a64b42e` (genuine Adreno 320 / 2GB / armeabi-v7a flox, LineageOS 18.1 / API 30) |
| `:app:assembleRelease` | BUILD SUCCESSFUL (R8/minify; only benign default-ctor R8 warnings) |
| `sign-release.bat` (zipalign + debug-sign) | `SIGN_EXIT=0` |
| `adb install -r` signed release | `Success` |
| Launch + screencap | Renders **"Dinghy Display — scaffold"** placeholder — NOT a routed Print Status screen (`/tmp/dd-perf/screen1.png`) |
| gfxinfo framestats capture | **NOT taken** — no combined-surface code path to measure |

## Deviations from Plan

None to the executed scope. Task 1 landed exactly as written. Task 2 surfaced an unmet prerequisite
(MainActivity still scaffold; no combined-surface harness) that is the checkpoint's blocker — reported
honestly rather than fabricated. No Rule 1-4 deviations were applied.

## Two-Part Perf Gate (the criterion to apply when unblocked)

Per `03-PERF-RESULTS.md` FINAL VERDICT (A-variant), to be re-measured on the REAL combined surface:
1. **Liveness gate (hard floor, project soul):** allocation-free draw path + NO animation loop (redraw
   tracks the ~3 Hz feed, not Choreographer) + **ZERO frozen frames** (no frame > 700 ms).
2. **Sparse-redraw latency gate:** value-driven redraw **p95 ≤ ~66 ms** on flox.
Plus the three 03-PERF-RESULTS.md re-open conditions (composite-floor stability, heavier full-screen
composite, ~3 Hz cadence assumption). Isolated Phase-3 ring+graph was 50.1 ms p95 / 0 frozen; the
combined surface adds the 2×3 grid + gutter, so it MUST be re-measured, not grandfathered.

## How to Unblock

1. Land the MainActivity screen-graph wiring (MainActivity → `TopRoute.derive()` → Shell →
   `PrintStatusScreen` with the live `AppContainer` + `PrintStatusHolder`), OR add a debug harness
   (Bench/Gallery scene) that composes the real combined Print Status surface.
2. With a live Ender 5 Plus reachable and a heat ramp / active print: open Print Status, confirm the
   sparkline updates and recolors on theme flip, then `dumpsys gfxinfo works.mees.dinghy reset` → dwell
   ~30 s → capture `framestats` → run `tools/gfxinfo-parser/parse_framestats.py`. Apply the two-part gate.
3. Verify Stop → ConfirmGuard → `printer.emergency_stop` → Klippy shutdown → Splash recovery surface,
   then recover via firmware_restart.

## Self-Check: PASSED

- `PrintStatusScreen.kt` modified and present — FOUND.
- Commit `6deb8d5` (Task 1 sparkline wiring) — see git log below.
- Task 2 honestly returned as a blocking checkpoint (no fabricated perf numbers).
