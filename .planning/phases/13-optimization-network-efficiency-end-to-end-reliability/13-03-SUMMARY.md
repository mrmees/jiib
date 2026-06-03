---
phase: 13-optimization-network-efficiency-end-to-end-reliability
plan: 03
subsystem: net
tags: [moonraker, cadence-audit, request-contract, refresh-probe-z-offset, configfile, save-config, d-04, d-05, d-06, guardrail]

# Dependency graph
requires:
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 01
    provides: GREEN ProbeZOffsetFreshnessTest (the executable Pitfall-3 gate) + live e5/e3 SAVE_CONFIG .jsonl captures
  - phase: 13-optimization-network-efficiency-end-to-end-reliability
    plan: 02
    provides: notify_klippy_ready re-handshake re-runs runHandshake (re-reads configfile) — kept ProbeZOffsetFreshnessTest GREEN
provides:
  - docs/request-cadence-contract.md — the committed request-cadence guardrail phases 10-12 must honor (SC-1, D-06)
  - The audit's single applied fix landed — redundant refreshProbeZOffset configfile re-query removed end-to-end
  - Declared-vs-live call-site distinction (oneshotToken flagged declared-not-live: REST GET /access/oneshot_token, no websocket call site)
affects: [13-04]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Wire-truth cadence contract as a committed guardrail doc (sibling to moonraker-capabilities.md) — declared-spec vs live-call-site distinction"
    - "Edge-driven one-shot is the compliant shape; 'refresh on page open' query is the removed anti-pattern (cadence contract Rule 3)"
    - "Removal gated on a real GREEN test (ProbeZOffsetFreshnessTest), not a SUMMARY sentence (Pitfall 3)"

key-files:
  created:
    - docs/request-cadence-contract.md
  modified:
    - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt

key-decisions:
  - "The contract distinguishes registry-DECLARED specs from ACTUAL production call sites — oneshotToken is the ONE declared-not-live entry (production fetches it via REST GET /access/oneshot_token in MoonrakerAuth.fetchOneshotToken, NO websocket call site); every other CommandRegistry.all entry has a live call site."
  - "The redundant refreshProbeZOffset configfile re-query removed only AFTER confirming ProbeZOffsetFreshnessTest is GREEN (it exercises runHandshake's configfile read, not refreshProbeZOffset) — the executable Pitfall-3 gate, run GREEN before (gate) and after (regression guard)."
  - "No timer/page-open configfile re-query replaces it — that would violate the very contract this plan documents (Rule 3)."

requirements-completed: []

# Metrics
duration: ~4min
completed: 2026-06-03
---

# Phase 13 Plan 03: Cadence Audit Contract + The One Applied Fix Summary

**The cadence audit deliverable landed — `docs/request-cadence-contract.md` is the committed wire-truth guardrail phases 10–12 must honor (every Moonraker call inventoried as declared-spec vs live-call-site, the single persistent `objects.subscribe` set justified object-by-object, the two-plane 250 ms throttle documented, and a RULES-FOR-FUTURE-PHASES block forbidding per-screen polling) — plus the audit's ONE real finding applied: the redundant `refreshProbeZOffset()` configfile re-query removed end-to-end (method + service wiring + SpineHandle field + AppShell caller), gated strictly on the GREEN `ProbeZOffsetFreshnessTest` (Pitfall 3 — a real test, not a prose sentence), with the build green and the gate held as the post-removal regression guard.**

## Performance

- **Duration:** ~4 min
- **Completed:** 2026-06-03
- **Tasks:** 2 (Task 1 doc; Task 2 the gated end-to-end removal)
- **Files:** 1 created (the contract doc), 4 modified (the four refreshProbeZOffset call sites)

## The Pitfall-3 gate (test-backed, not prose)

Before touching any production code, ran the gate test:

```
:app:testReleaseUnitTest --tests works.mees.dinghy.net.ProbeZOffsetFreshnessTest  →  exit=0 GREEN
```

`ProbeZOffsetFreshnessTest.reHandshakeAfterRestart_refreshesProbeZOffsetToNewSavedValue` connects (seeds
`probe.z_offset = 1.250` via the handshake's configfile one-shot), changes the saved offset to `1.475`,
drives the captured klippy drop→ready, and asserts the store's `probeZOffset` StateFlow reflects `1.475`
AFTER the re-handshake. It exercises `runHandshake`'s configfile read (`MoonrakerSession` step 7, the
`store.setProbeZOffset` at ~511) — it does NOT reference `refreshProbeZOffset()`. So a GREEN result proves
the re-handshake itself keeps `probe.z_offset` fresh, which is exactly the premise the removal depends on.
The SAME test is the post-removal regression guard (re-run GREEN after the removal).

## Task 1 — `docs/request-cadence-contract.md` (SC-1, D-06)

A 159-line wire-truth guardrail modeled on `docs/moonraker-capabilities.md`:

- **Purpose block** — kills the recurring chattiness / mock-vs-reality bug class; protects the LAN AND the
  weak SBC host CPU (D-05); authored from the real code cross-checked against the committed
  `docs/commands/e5-saveconfig-capture.jsonl` + `e3-saveconfig-capture.jsonl`.
- **RULES FOR FUTURE PHASES** — (1) no `objects.subscribe` outside the central handshake; (2) no per-screen
  polling (the `delay(4_000)` loops are toast/failure-text auto-dismiss timers in `LaunchedEffect`, verified
  across `ui/` — NOT a polling precedent); (3) one-shot reads go through the `SpineHandle`, off the throttled
  hot path, edge-driven (filename change / idle transition / connect / klippy_ready), never timer-driven
  (`PrintMetadataHolder`, `LastJobHolder` are the canonical compliant shapes); (4) high-rate numeric data
  conflated to `DEFAULT_SAMPLE_MS = 250L` before reaching the UI.
- **Two-plane throttle model** — control plane (`print_stats` / `webhooks` / `toolhead.homed_axes`)
  immediate; high-rate plane conflated to 250 ms; gcode/console stream its own bounded buffer.
- **Call inventory — DECLARED vs LIVE.** Walked `CommandRegistry.all` and verified each entry against its
  real production call site. The table of LIVE calls (single persistent `objects.subscribe`, per-handshake
  one-shot seeds, edge-driven one-shots, per-tap actions) is separated from a flagged
  **declared-but-not-live** row (below).
- **Subscribe-set justification** — every `V1_SUBSCRIBE_CORE` object mapped to its consumer; notes
  `quad_gantry_level` is forward-compat (intersect drops it on both test printers, D-02) and the calibration
  objects only diff during a routine (idle-cheap).
- **The one applied fix** documented as the canonical Rule-3 example.

### Registry entries flagged declared-but-not-live-on-the-socket

| Entry | Reality | Verdict |
|-------|---------|---------|
| **`oneshotToken`** (`MR-access.oneshot_token`, declared `CommandRegistry.kt` ~82–87, in `all` ~440) | Production fetches the token via **REST `GET /access/oneshot_token`** in `MoonrakerAuth.fetchOneshotToken()` (~53–58), invoked from `MoonrakerSession.connectAndServe` ~195 ONLY on the keyed path. The JSON-RPC spec has **no production websocket call site**. | **Declared, not live on the socket (REST path).** |

`oneshotToken` is the sole declared-not-live entry; every other `CommandRegistry.all` spec has a live call
site (gcode/print/file specs dispatched per user action via `CommandDispatcher`).

## Task 2 — Remove the redundant `refreshProbeZOffset` end-to-end (SC-1, gated)

The handshake's step-7 configfile one-shot (`runHandshake` ~484–511) already reads `configfile` and
publishes `probe.z_offset` via `store.setProbeZOffset`. The Phase-13 re-handshake fix (13-02) makes the
post-SAVE_CONFIG `notify_klippy_ready` re-run `runHandshake()`, so the Probe-Calibrate page-open re-query
became pure redundancy. Removed the four coupled pieces together:

1. `MoonrakerSession.refreshProbeZOffset()` — the redundant `objects.query{configfile}` (~115–123).
2. `MoonrakerService` wiring (`refreshProbeZOffset = { serviceScope.launch { … } }`, ~179).
3. `SpineHandle.refreshProbeZOffset` field (~84).
4. `AppShell` Probe-Calibrate `onEnter` caller (`spine?.refreshProbeZOffset?.invoke()`, ~406) — replaced
   with a comment explaining the re-handshake keeps it fresh (no replacement query).

`grep -rn refreshProbeZOffset app/src/main/java/` → ZERO matches. The only remaining `configfile` query is
the single handshake one-shot (no timer/page-open re-query introduced).

## Verification (all hard-timeout-guarded; exit codes authoritative)

| Run | Filter | Result |
|---|---|---|
| Gate (BEFORE removal) | `--tests works.mees.dinghy.net.ProbeZOffsetFreshnessTest` | **exit=0 GREEN** |
| Release build + gate (AFTER removal) | `:app:assembleRelease` + `--tests …ProbeZOffsetFreshnessTest` | **exit=0 BUILD SUCCESSFUL + GREEN** |
| Full unit suite (AFTER removal) | `:app:testReleaseUnitTest` (no filter) | **exit=0 GREEN** |

(The release build emits pre-existing R8 proguard-rule warnings for retrofit2/okhttp — out of scope, not
introduced by this plan.)

## Deviations from Plan

None — both tasks executed exactly as written. No auto-fixes, no architectural changes, no scope creep.

## Known Stubs

None.

## Threat Flags

None — no new network endpoints, auth paths, file access, or schema changes. Removing a redundant query
REDUCES the wire surface (T-13-07 accept / T-13-08 mitigate per the plan's threat register: the removal is
gated + guarded by the GREEN ProbeZOffsetFreshnessTest; the D-09 on-device UAT in plan 13-04 is the live
backstop).

## Task Commits

1. **Task 1: cadence contract doc** — `f2d9d60`
2. **Task 2: refreshProbeZOffset removal end-to-end** — `f8f3ff9`

## Self-Check: PASSED

- `docs/request-cadence-contract.md` exists (159 lines), `RULES FOR FUTURE PHASES` + `objects.subscribe` +
  `250` + `oneshot` + `4_000` all present.
- Both task commits verified in git log (`f2d9d60`, `f8f3ff9`).
- `grep -rn refreshProbeZOffset app/src/main/java/` → zero matches.
- assembleRelease + full `:app:testReleaseUnitTest` + the gate test all returned exit=0 under the guard.

---
*Phase: 13-optimization-network-efficiency-end-to-end-reliability*
*Completed: 2026-06-03*
