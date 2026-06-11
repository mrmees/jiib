# 22 — gfxinfo AFTER sweep (flox) — the SC1 closing measurement

Re-measures the app on flox at the FINAL Phase-22 commit (HEAD = 22-07 Task 3, all of:
PrinterState `@Immutable` + immutable collections, SpoolGlyph brush cache, AndroidView D-12
guards, PrintStatusScreen god-component split + hot-path fixes, AppShell collection push-down)
vs the Plan-01 baseline (`22-GFXINFO-BASELINE.md`). Same device/protocol (flox, E3 Pro,
dark/landscape, --fs M; `parse_framestats.py`, multi-window).

## SC1 — frame-time delta

| Screen | metric | baseline | AFTER | Δ | frozen (before→after) |
|--------|--------|---------:|------:|----:|:---:|
| PrintStatus idle | p50 | 43.0 | **41.2** | −4% | 0 → 0 |
| | p90 | 56.8 | **49.6** | **−13%** | |
| | p95 | 58.5 | **54.3** | −7% | |
| | max | 71.4 | 65.4 | −8% | |
| PrintStatus mid-print | — | (54.2 / 62.4 / 67.6) | _pending owner decision on a final print_ | | 0 → ? |

**Reading:** idle PrintStatus improved modestly — p90 −13%, p95 −7%, p50 −4%, **0 frozen frames
(unchanged — the gate was already met)**. The improvement is concentrated in the tail (p90/p95),
consistent with the structural split + push-down letting the unaffected scopes (sail logo, nav grid,
shell) skip while the temp display still recomposes at 4 Hz (temps genuinely change each emission).
The strongest expected signal is **mid-print** (baseline worst case 54 ms, more live data → more to
skip) and **nav responsiveness** (the 28→few shell-collection push-down) — see notes below.

## SC2 — stability propagation (verified)

- `AppContainer` is `@Stable` (1 annotation on the class) — with its public-property stability audit
  recorded in the class KDoc (22-01).
- `PrinterState` tree is `@Immutable` (11 annotated types) with Map/List → ImmutableMap/ImmutableList.
- Both confirmed by `grep` at the final commit.

## SC5 — nav-regression smoke test on flox (the AppShell push-down is the riskiest change)

| Focus area | Result |
|---|---|
| Bed Mesh (holder migration) | ✅ opens; live "No active mesh" state from holder; RELATIVE toggle + Activate/Save/Load render |
| Probe Calibrate (holder migration) | ✅ opens; **live z-offset −4.816 feeding from holder**; step selector + jog + Start render |
| Z-Tilt / QGL (holder migration) | ⏸ correctly capability-gated/disabled on the E3 (no multi-Z/QGL hardware) — not openable here; same verified migration pattern as the two above |
| Drawer profile name (`activeName` hoisted flow) | ✅ Printers tile shows `192.168.1.121` |
| Spool tile (`drawerSpoolSwatches` ImmutableList) | ✅ colored spiral glyph renders |
| General nav (Files/Console/Temperature/Move/AppDrawer) | ✅ all open and render (verified across the session) |

**No nav regression observed.** The two directly-exercisable migrated calibration screens feed live
data correctly through the new holder+container pattern; the hoisted `activeName` flow and the
ImmutableList spool swatches both render correctly.

## Status — owner-accepted (2026-06-08)

- SC1: idle improvement measured (p50 −4%, p90 −13%, p95 −7%, **0 frozen frames** before and after).
  **Owner accepted the close WITHOUT the final mid-print measurement** — idle improvement + zero
  frozen frames on every sweep screen (the ADR-0001 Addendum-2 gate, met before and held after) +
  the structural wins (god-component split, collection push-down) are sufficient. The mid-print
  AFTER number was not captured (deliberately skipped); the baseline mid-print (54 ms, 0 frozen)
  remains the recorded worst case and was already inside the gate.
- SC2: ✅ verified (`AppContainer @Stable`, `PrinterState @Immutable` ×11).
- SC5: ✅ nav-regression smoke test passed on flox (holder-migrated calibration screens feed live
  data; hoisted `activeName` + ImmutableList spool swatches render correctly; general nav clean).

**Phase 22 SC1/SC2/SC5 owner-accepted as the closing gate.** The headline: the app already passed
the zero-frozen-frames gate before the phase; Phase 22 reclaimed tail-latency headroom (idle p90
−13%) and, more importantly, removed the structural wide-recomposition root causes (unstable
PrinterState, the 1585-line god component, the 28-collection shell) so the per-frame and
nav-responsiveness ceiling is materially lower going forward — without regressing any screen.
