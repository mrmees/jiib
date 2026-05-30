# Phase 2: Connection & State Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-30
**Phase:** 2-connection-state-foundation
**Areas discussed:** Reconnect policy, Disconnected/stale state, Dev config, Auth reality

---

## Reconnect Policy

| Option | Description | Selected |
|--------|-------------|----------|
| Forever, capped backoff | Exp backoff + jitter, cap interval ~30s, retry indefinitely; recovers within ~30s after a router reboot | |
| Forever, uncapped | Pure exponential backoff, no ceiling; gentle on network but slow recovery after long outage | ✓ |
| Give up after N tries | Stop after a retry budget, show manual reconnect state | |

**User's choice:** Forever, uncapped — **with a refinement after pushback.**
**Notes:** Claude pushed back: uncapped means slow recovery on an unattended wall screen (retry interval climbs to minutes), and a home LAN doesn't need network-gentleness. Offered a ~30s cap as the same "never gives up" behavior with bounded recovery. User reframed it better than the cap: keep uncapped *because* a printer can sit off for weeks and shouldn't be spammed, but **build a disconnected overlay whose reconnect affordance immediately fires a connection attempt.** Resolved as: uncapped backoff + a Phase-2 `requestReconnectNow()` hook that short-circuits the backoff delay; the overlay UI itself is deferred to Phase 3 (shell). This satisfied Claude's recovery-speed concern via user-initiated retry rather than a cap.

---

## Disconnected / Stale State

| Option | Description | Selected |
|--------|-------------|----------|
| Retain last-known + stale flag | Keep last values, expose stale/disconnected marker so panels grey out; resync overwrites on reconnect | ✓ |
| Clear to null/unknown | Drop all state on disconnect; safer against lies but a blip wipes the screen | |

**User's choice:** Retain last-known + stale flag
**Notes:** None — clean accept. Last reading dimmed beats a blank screen on an always-on display.

---

## Dev Config

| Option | Description | Selected |
|--------|-------------|----------|
| gitignored local.properties → BuildConfig | Host/port/key from gitignored props into BuildConfig; nothing secret committed | ✓ |
| Committed default + env/arg override | Commit a placeholder default overridable by gradle/env var | |
| Instrumentation args only | Config only via test args (like Phase-1 smoke); app can't be hand-launched to watch reconnect | |

**User's choice:** gitignored local.properties → BuildConfig
**Notes:** None.

---

## Auth Reality

| Option | Description | Selected |
|--------|-------------|----------|
| Open / trusted-client (no key) | Home LAN, no key; real-hardware proves no-auth path, auth path is mock-only | ✓ |
| Has an API key / force_logins | Real-hardware run exercises full oneshot-token + X-Api-Key flow | |
| Not sure / can configure either | Planner makes auth testable both ways, decide at execution | |

**User's choice:** Open / trusted-client (no key)
**Notes:** Consequence captured in CONTEXT D-06 — CONN-02 (optional auth) is proven in mock-socket tests only; the live run covers the no-auth path. Honest scope flagged for the planner.

---

## Claude's Discretion

- **Golden-frame fixtures** — user chose "Ready for context" rather than discussing. Claude's default captured in CONTEXT: capture real frames from the live Ender 5 Plus to seed the golden corpus; hand-author only adversarial/edge frames (interleaved notifications for STATE-05, `401`, klippy shutdown/error).
- **Capabilities model scope (STATE-02)** — offered but not discussed; left to planner with a reasonable default (model what v1 panels gate on, pure unit-tested function).
- **Throttle/conflation mechanism (STATE-03)** — planner's call, with the load-bearing constraint that conflation applies to status updates but NOT the `notify_gcode_response` line stream.
- Module/package layout, the `callbackFlow` bridge, JSON-RPC `Map<id, CompletableDeferred>` correlation, and reconnect supervision structure — planner/executor's call (stack itself is locked).

## Deferred Ideas

- **Disconnected-printer overlay UI** (immediate-retry button calling the Phase-2 `requestReconnectNow()` hook) → Phase 3 (shell).
- **User-facing connection config screen (CONN-01)** → already roadmapped to Phase 3.
