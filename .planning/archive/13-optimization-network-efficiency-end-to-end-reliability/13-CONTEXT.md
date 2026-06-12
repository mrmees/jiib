# Phase 13: Optimization, Network Efficiency & End-to-End Reliability - Context

**Gathered:** 2026-06-03
**Status:** Ready for planning

<domain>
## Phase Boundary

A **refactor/quality pass over the already-built session layer — no new screens.** Three threads:

1. **Session-layer reliability fix (headline).** The `SAVE_CONFIG` / Klipper-restart re-handshake
   freezes the entire live feed until the app is force-restarted. The 05-10 `notify_klippy_ready`
   re-handshake (G2 fix) is **not holding** — observed broken on the Ender 3; the Ender 5 is
   **unproven, not assumed-good** (we haven't exercised SAVE_CONFIG on it since Phase 5).
2. **Request-cadence audit.** Prove the (already subscribe-driven) app isn't noisy on the LAN or the
   weak Klipper host before stacking three more feature phases on it. Produce a documented cadence
   contract + fix any violations.
3. **End-to-end reliability hardening** for the in-session resync class (see scope decision D-07).

**Scout finding that anchors scope:** the architecture is *already* sound — one central
`objects.subscribe` handshake, capability-derived subset, **no per-screen polling** (the `delay(4_000)`
loops in every screen are toast auto-dismiss timers, NOT polling), high-rate plane conflated at ~250ms
(`DEFAULT_SAMPLE_MS`), control plane immediate, gcode stream separately un-throttled, static config
read one-shot off the hot path. So this phase is **verify-tighten-and-fix**, not a rebuild.

</domain>

<decisions>
## Implementation Decisions

### Re-handshake / klippy-restart recovery (headline)
- **D-01: Root-cause first, then decide the fix.** Researcher/debugger must determine WHY the
  in-session re-subscribe dies before picking a remedy (does `notify_klippy_ready` actually fire +
  get caught? does `objects.subscribe` re-register get silently dropped by Moonraker after a klippy
  restart on the same socket? is the frame collector / `rpc.statusUpdates` torn down?). The
  force-full-reconnect-on-`klippy_ready` path (the one that demonstrably works on app restart) is the
  named fallback if the in-session path can't be made reliable — but it is NOT the default; understand
  the failure first.
- **D-02: Do NOT assume the bug is Ender-3-specific.** The "G2 verified on E5" sign-off was Phase 5
  and predates any SAVE_CONFIG exercise on the E5 since. Treat printer-independence as **unproven** —
  the bug may be present everywhere. Investigation and verification both span both printers.
- **D-03: Recovery shows a brief Syncing splash EVERY time.** Honest and unambiguous: every
  klippy-restart recovery surfaces the Connecting/Syncing state. ⚠ The current in-session re-handshake
  runs **silently** (re-runs `runHandshake()` on `attemptScope` with no `ConnectionState` emit) — so
  "emit Syncing on `klippy_ready` re-handshake start, Connected when the reseed lands" is itself a
  concrete code change to bake in, regardless of which recovery mechanism wins.

### Request-cadence audit
- **D-04: Audit is preventative** — no observed chattiness today. The goal is to PROVE the
  subscribe-driven model is clean before phases 10–12 stack on it.
- **D-05: Protect the host CPU, not just the LAN.** The Klipper hosts are weak SBCs (E5 = Pi 4, E3 =
  RockPro64); cadence discipline guards Moonraker's CPU as much as the WiFi. Both are audit targets.
- **D-06: Deliverable = a committed request-cadence contract doc + fixes.** Document every Moonraker
  call the app makes (one-shot vs subscribe, trigger, frequency) under `docs/`, and fix anything that
  violates it. The doc becomes the **guardrail future feature phases (10–12) must honor** so they
  don't reintroduce chattiness. Known suspect to confirm/decide during the audit: `refreshProbeZOffset()`
  re-queries `configfile` on every Probe-Calibrate page open — if the re-handshake fix keeps config
  fresh, that masking re-query may become redundant.

### Phase 13 ↔ 14 boundary
- **D-07: Phase 13 owns ALL in-session resync.** Every reliability class triggered by the
  printer/session lands here: klippy-restart recovery, **network-drop-mid-print reconnect, and the
  print-state resync that rides along with it.** If the SAVE_CONFIG fix strengthens general
  reconnect-resync, bank it now rather than artificially deferring working code.
- **D-08: Phase 14 shrinks to the Android/OS/ship layer only** — process-death recovery, Doze /
  always-on survival, burn-in screensaver, R8 release build, signed APK. ⚠ This **moves ROADMAP
  Phase 14's "reconnect print-state resync" line into Phase 13** — flag for a roadmap touch-up; the
  phase numbers/order are otherwise unchanged.

### Verification contract (the 3-strike mock-vs-reality class)
- **D-09: Live UAT gate = BOTH printers, BOTH scenarios.** (a) `SAVE_CONFIG → start print → assert
  the feed stays live and the new print registers (printState→Printing) without an app restart`, and
  (b) `mid-print network drop → reconnect → print state resyncs correctly`. Run on the E5 (Pi 4) AND
  the E3 (RockPro64) — consistent with D-02's "can't rule out the E5."
- **D-10: Fixture-first automated regression — harden the mock to the REAL wire contract.** Capture
  the actual klippy-restart wire sequence live (`notify_klippy_ready` + what Moonraker actually does
  to the subscription/socket) BEFORE coding the fix, then harden `FakeWebSocket` to replay that exact
  contract, then write the `SAVE_CONFIG→Printing` regression against it. Same capture-first discipline
  as the Phase-9 calibration fixtures. ⚠ The existing `KlippyReadyResyncTest.kt` is **green while the
  real behavior fails on-device** — it is the canonical example of the mock that lied and the test
  file to harden, not trust as-is.

### Claude's Discretion
- The exact recovery mechanism (fixed in-session re-subscribe vs force-reconnect) is left to
  research/planning per D-01 — decide it from the root cause, not up front.
- Specific cadence-contract format and where under `docs/` it lives.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The headline bug
- `.planning/todos/pending/save-config-rehandshake-not-refreshing-config.md` — full repro (live, E3,
  2026-06-03) + the investigation checklist. The spec for D-01/D-02.

### Session / reliability code (the surface being fixed)
- `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` — the reconnect supervisor + resync
  handshake; `connectAndServe` (~186–319), the `notify_klippy_ready` re-handshake path (~231–250,
  silent today — D-03), `runHandshake()` (~322+), `refreshProbeZOffset()` (~114, the cadence suspect).
- `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` — the two-plane model: ~250ms
  conflated high-rate plane (`DEFAULT_SAMPLE_MS`) vs immediate control plane vs un-throttled gcode flow.
- `app/src/main/java/works/mees/dinghy/state/DeriveCapabilities.kt` — `V1_SUBSCRIBE_CORE` +
  `deriveSubscribeSet` (the subscribe subset to audit/minimize, D-06).
- `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` — every Moonraker call spec
  (one-shot reads: `objectsQuery`, `temperature_store`, `gcode_store`, `files.metadata`,
  `history.list`) — the inventory for the cadence contract.

### Verification baseline (what to harden)
- `app/src/test/java/works/mees/dinghy/net/KlippyReadyResyncTest.kt` — green-but-lying 05-10 resync
  tests; harden the `FakeWebSocket` they drive to the real wire contract (D-10).
- `.planning/phases/05-core-print-control-panels-temperature-move-extrude/05-10-PLAN.md` +
  `05-10-SUMMARY.md` — the original G2 `notify_klippy_ready` re-handshake design that isn't holding.

### Project / capability context
- `docs/moonraker-capabilities.md` — live-captured Moonraker API shapes (cadence-contract source of truth).
- `docs/adr/0001-ui-toolkit-decision.md` — ADR-0001 toolkit split; holders stay toolkit-agnostic.
- `docs/ui_design/THEMING.md` + `docs/ui_design/LAYOUT.md` — connection-state surface (Syncing/stale)
  rules for D-03.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`ConnectionState` five-state lifecycle + the `stale`/dimmed model (D-04 in PrinterState)** — already
  the right surface for D-03's Syncing splash and the recovery's stale-then-snap-fresh behavior.
- **`rehandshakeMutex` + `handshakeComplete` gate** in MoonrakerSession — the existing serialization
  for re-handshakes; the fix extends this path rather than inventing a new one.
- **`FakeWebSocket` + `runTest` virtual-time harness** — the deterministic socket substitute to harden
  per D-10.

### Established Patterns
- **No per-screen polling.** Everything derives from the central subscribe. Any fix or audit finding
  must preserve this — do NOT introduce screen-level queries (the `delay(4_000)` loops are toast
  timers, not a precedent for polling).
- **Capture-first / fixture-first** (Phase 9) — capture real wire behavior before writing the parser
  or the mock. D-10 applies it to the klippy-restart sequence.
- **One-shot reads split off the throttled hot path** (SpineHandle) — the established place for static
  reads; the cadence contract documents and polices this boundary.

### Integration Points
- `MoonrakerService` (FGS) owns the session scope and wires `refreshProbeZOffset`, metadata, and
  history one-shots — any cadence change touches the service ↔ session ↔ store seams.

</code_context>

<specifics>
## Specific Ideas

- "We haven't been testing the app on the Ender 5 so can't rule it out" — the explicit driver for D-02
  (don't assume the bug is printer-specific) and D-09 (both printers in the gate).
- "Aside from the network we also don't want to hammer the host running Klipper" — D-05; the audit is
  about host CPU politeness as much as LAN bytes.
- Brief Syncing splash on every recovery is the deliberate, honest choice (D-03) even at the cost of
  interrupting the screen on a routine SAVE_CONFIG.

</specifics>

<deferred>
## Deferred Ideas

- **Process-death recovery, Doze / always-on survival, burn-in screensaver, R8 release, signed APK** —
  Phase 14 (D-08).
- **CONS-01 arbitrary G-code SEND** — still deferred (console is read-only); not a reliability item.
- Other pending todos NOT in this phase's scope: `console-macro-page-ux-flow`,
  `status-progress-ring-dual-source-jump`, `benchmark-harness-fairness-fixes`,
  `macrobenchmark-module-wiring`, `files-delete-gating-too-broad` (D-15 already resolved the active-file
  scoping in Phase 9). Revisit in their own phases / quick tasks.

</deferred>

---

*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Context gathered: 2026-06-03*
