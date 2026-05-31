---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: planning
last_updated: "2026-05-31T12:00:00.000Z"
last_activity: 2026-05-31
progress:
  total_phases: 9
  completed_phases: 2
  total_plans: 8
  completed_plans: 8
  percent: 22
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-05-30)

**Core value:** Direct, reliable printer control from an old Android tablet over Moonraker — install an APK, point it at the printer, and drive a print.
**Current focus:** Phase 3 — Design System & Theming Foundation (NEW, per the 2026-05-31 restructure)

## Current Position

Phase: 3 (Design System & Theming Foundation)
Plan: Not started — needs `/gsd-discuss-phase 3` (governed by `docs/ui_design/`)
Status: Ready to discuss/plan
Last activity: 2026-05-31

Progress (Phase 3): [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**

- Total plans completed: 8
- Average duration: — min
- Total execution time: 0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 01 | 4 | - | - |
| 02 | 4 | - | - |

**Recent Trend:**

- Last 5 plans: —
- Trend: —

*Updated after each plan completion*
| Phase 01 P01-01 | 38 | 3 tasks | 16 files |
| Phase 01 P01-03 | 7 | 2 tasks | 8 files |
| Phase 01 P01-02 | 9 | 3 tasks | 2 files |
| Phase 01 P01-04 | 20 | 2 tasks | 5 files |
| Phase 02 P01 | 7 | 3 tasks | 15 files |
| Phase 02 P02 | 6 | 2 tasks | 5 files |
| Phase 02 P03 | 14 | 2 tasks | 6 files |
| Phase 02 P04 | 110 | 3 tasks | 11 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- [Roadmap]: Infrastructure-first horizontal structure — connection/state spine built and proven (mock socket + real printer) before any panel.
- [Roadmap revision]: Cross-AI review (Codex) applied in full. Old Phase 1 split into Platform Gate (1) + Connection & State Foundation (2); old Phase 4 split into Files/Print (5) + Job Status (6). Now 8 phases.
- [Phase 1]: UI-toolkit choice (Compose-everywhere vs. hybrid-Views) is its own gate — an on-device Nexus 7 benchmark against a SYNTHETIC 2–4 Hz source (no full connection layer needed). It gates all panel architecture. Pin to Compose 1.11 / AGP 8.7.x line regardless.
- [Phase 2]: Foundation connects via a STATIC/dev config; the user-facing connection config screen (CONN-01) moved to the Shell phase (3) where UI + DataStore live.
- [Phase 3]: Added shared command-dispatch primitive (PRIM-05: timeouts + in-flight/busy + debounce). Shell thermal dashboard proves the SHARED render/throttle primitive; the Temperature panel EXTENDS it into the full graph.
- [SCOPE RESTRUCTURE 2026-05-31]: Matthew delivered a full app-wide design system at `docs/ui_design/` (now the canonical UI LAW — see repo-root CLAUDE.md). Scope broadened: phones→tablets, **portrait + landscape**, **full theming** (dark+light+custom + S/M/L text size); **Nexus 7 / Adreno 320 retained as the perf FLOOR, not the only target**. Connection entry + theme + feature toggles move to a conventional **Settings screen** (keyboard allowed); printer controls stay keyboard-free (single-setting scrubber pages); nav = **swipe-up App Drawer**; Stop → full-screen **Confirm guard**. Roadmap **8 → 9 phases**: new **Phase 3 = Design System & Theming Foundation** (token theming, Focus/Field/Gutter responsive grammar, control language, Confirm/single-setting/toast/render primitives); old shell phase became **Phase 4 (Service, Shell, Settings & Print-Status Home)**; phases 4-8 shifted +1. REQUIREMENTS +5 (THEME-01/02, UI-01/02, SET-01 → 55 v1). The generated `04-UI-SPEC.md` is reduced to a pointer; `04-CONTEXT/RESEARCH/VALIDATION` carry SUPERSEDED banners (service/routing valid, UI superseded) — regenerate Phase 4 via discuss/plan before executing.
- [Roadmap]: v1 = functional core only (Connect + Temp/Move/Extrude/Files/JobStatus/Macros/Console). Fine-tune + Camera are v2.
- [Phase ?]: [Phase 1/01-01]: verifyMinSdk delivered as a precompiled build-logic script plugin (not apply(from=)) so it uses AGP SingleArtifact.MERGED_MANIFEST; PKG-02 floor proven adversarially.
- [Phase ?]: [Phase 1/01-01]: Release APK ships armeabi-v7a only via splits.abi; Compose UI resolves to 1.11.1; cleartext posture owned by the shared manifest/NSC (single-owner for Wave-2).
- [Phase ?]: [Phase 1/01-03]: Toolkit benchmark harness built — one deterministic SyntheticFeed drives a Compose scene and a hybrid-Views scene rendering identical worst-case layout with REAL Coil decode at 1920x1200; gfxinfo framestats parser is system of record, FrameTimingMetric corroboration only (no baseline-profile gate).
- [Phase 1/01-02]: CONN-05 cleartext smoke PASSED on real hardware. DEVICE-REALITY FINDING: the physical "Nexus 7 2013" (flox) runs LineageOS 18.1 / Android 11 / API 30, NOT stock Android 6 / API 23 — so the proof exercised the NSC (API-24+) cleartext path; the API-23 manifest-flag path is config-validated + deferred. minSdk 23 retained as install floor. 01-04 benchmark runs on this device with an ART caveat (API-30 runtime newer than stock-6; same Adreno 320 / 2GB / 1920x1200).
- [Phase 1/01-04]: Toolkit verdict = HYBRID (Compose shell + classic Views for Files list / temp graph / Console scrollback). On-device release benchmark on real flox showed Views ~2x lower p95/max frame time; both cleared floors (p50<16.6ms, 0 frozen). ADR: docs/adr/0001-ui-toolkit-decision.md. Gates all Phase 2+ panel architecture.
- [Phase ?]: [Phase 2/02-01]: Live Ender-5-Plus capture unavailable at execution; synthetic fallback corpus copied to golden/*.json names — fallback is the autonomous floor, GoldenFixtures.resolve() prefers live when later added.
- [Phase ?]: [Phase 2/02-02]: Pure state layer landed — reduceSnapshot/reduceDiff deep-merge (STATE-01, no field-wipe), applyKlippyMethod folds notify_klippy_* (STATE-04), deriveCapabilities + deriveSubscribeSet (v1 superset INT objects.list, A3; powerDevices empty A4). All I/O-free; 02-04 re-runs derive* on every reconnect.
- [Phase 02]: [Phase 2/02-03]: Transport seam landed — MoonrakerSocket callbackFlow bridge (injectable WebSocketFactory, FakeWebSocket-substitutable, readTimeout(0)); concrete RpcConnection binds send/close to the live socket (send-before-open unrepresentable, send-after-close throws); onFailure completes the flow normally carrying a typed Closed(cause).
- [Phase 02]: [Phase 2/02-03]: JsonRpcClient correlates by id under interleaved notify_* (STATE-05), per-request withTimeout + close(cause) fails+clears all pending (review HIGH #1, no deadlock), routes notifications by method with a SEPARATE bounded gcode flow; RpcConnectionException is a plain Exception so completeExceptionally fails (not cancels) deferreds.
- [Phase ?]: [Phase 2/02-04]: Session spine integrated and GATE-PROVEN on the real Ender 5 Plus. Critical finding AT the on-device gate: server.connection.identify REQUIRES a non-empty 'url' arg — the spine omitted it, so the live connection never reached Connected, yet the ENTIRE JVM suite was green because the FakeWebSocket mock was more lenient than the real server. Fix sends url + tightened the harness to enforce the required-field contract (regression guard). Lesson: a mock looser than the server hides protocol bugs; the real-hardware gate is the backstop.
- [Phase ?]: [Phase 2/02-04]: Reconnect supervisor lands D-01 overflow-safe uncapped backoff+jitter (no give-up ceiling) + D-02 requestReconnectNow; ordered identify->objects.list->deriveCapabilities->query->subscribe once per reconnect overwriting stale state (D-04, STATE-02); Connected gated behind Syncing (CONN-06 review HIGH #3); gentle AuthRequired quiescence (no token-fetch storm); split-plane conflation samples only high-rate numeric fields while control-plane+gcode stay immediate (STATE-03).

### Pending Todos

None yet.

### Blockers/Concerns

- [Phase 1] Compose-vs-Views perf on the Nexus 7 is unresolved by design — only the on-device spike (synthetic 2–4 Hz feed) answers it. Hybrid (Views for high-churn: Files list, temp graph, Console scrollback) is the named fallback.
- [Phase 2] Auth handshake edge cases (oneshot-token websocket, `X-Api-Key`, `401`) need exercising during implementation; flagged for deeper Phase 2 research. JSON-RPC `id` correlation under interleaving notifications (STATE-05) must be covered by mock-socket tests.

## Deferred Items

Items acknowledged and carried forward from previous milestone close:

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| *(none)* | | | |

## Session Continuity

Last session: 2026-05-31T12:00:00.000Z
Stopped at: Scope restructure complete — adopted docs/ui_design as canonical UI; roadmap re-split to 9 phases (new Phase 3 = Design System & Theming Foundation). Next: /gsd-discuss-phase 3.
Resume file: docs/ui_design/README.md
