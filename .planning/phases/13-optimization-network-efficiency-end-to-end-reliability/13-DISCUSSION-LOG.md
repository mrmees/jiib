# Phase 13: Optimization, Network Efficiency & End-to-End Reliability - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-03
**Phase:** 13-optimization-network-efficiency-end-to-end-reliability
**Areas discussed:** Re-handshake fix strategy, Cadence-audit ambition, Phase 13/14 boundary, Verification contract

---

## Re-handshake fix strategy

| Option | Description | Selected |
|--------|-------------|----------|
| Root-cause first, then decide | Dig into WHY the in-session re-subscribe dies before picking a fix | ✓ |
| Go straight to force-reconnect | Treat in-session re-subscribe as unreliable; force a full socket reconnect on klippy_ready | |
| Force-reconnect, keep root-cause notes | Adopt force-reconnect but document why the in-session path failed | |

**User's choice:** Root-cause determination first — "but don't assume it's just the ender 3, we haven't been testing the app on the ender 5 so can't rule it out."
**Notes:** Critical correction — the "G2 verified on E5" sign-off was Phase 5 and predates any recent SAVE_CONFIG exercise on the E5. Printer-independence of the bug is unproven; investigation + verification must span both printers (→ D-01, D-02).

### Recovery UX (follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Stale-dimmed, then snap fresh | Keep last-known dimmed values, no full-screen interruption | |
| Brief Syncing splash every time | Show Connecting/Syncing on every recovery | ✓ |
| You decide during planning | Pick whatever falls out of the mechanism | |

**User's choice:** Brief Syncing splash every time.
**Notes:** Honest/unambiguous. Surfaced that the current in-session re-handshake runs SILENTLY (no ConnectionState emit) — so emitting Syncing is itself a code change (→ D-03).

---

## Cadence-audit ambition

| Option | Description | Selected |
|--------|-------------|----------|
| Preventative — verify & document | No observed problem; prove the model is clean | ✓ |
| Observed — something feels chatty | A concrete chattiness to hunt down | |
| Mixed — preventative + one suspect | Mostly preventative with a specific suspect | |

**User's choice:** Preventative — "aside from the network we also don't want to hammer the host running klipper."
**Notes:** Added the host-CPU dimension (weak SBCs: Pi 4 / RockPro64) as an explicit audit target alongside LAN noise (→ D-04, D-05).

### Audit output (follow-up)

| Option | Description | Selected |
|--------|-------------|----------|
| Cadence contract doc + fixes | Documented request-cadence contract + fix violations; guardrail for phases 10–12 | ✓ |
| Just measure + fix | Measure request rate, fix egregious, note in artifacts; no standalone doc | |
| Contract doc, defer fixes if clean | Produce doc; only fix on a measured violation | |

**User's choice:** Cadence contract doc + fixes (→ D-06).

---

## Phase 13/14 boundary

| Option | Description | Selected |
|--------|-------------|----------|
| 13 owns all in-session resync | 13 = klippy-restart + network-drop reconnect + print-state resync; 14 = Android-lifecycle only | ✓ |
| Strict: 13 = klippy-restart only | All reconnect print-state resync fenced to Phase 14 | |
| 13 owns it if it falls out free | Absorb reconnect-resync only if it comes free with the fix | |

**User's choice:** 13 owns all in-session resync.
**Notes:** Moves ROADMAP Phase 14's "reconnect print-state resync" line into Phase 13; Phase 14 shrinks to Android/OS/ship layer (→ D-07, D-08).

---

## Verification contract

### Live gate

| Option | Description | Selected |
|--------|-------------|----------|
| Both printers, both scenarios | E5 + E3, SAVE_CONFIG path AND mid-print network-drop reconnect | ✓ |
| Both printers, SAVE_CONFIG only | Both printers for SAVE_CONFIG; reconnect via automated + spot-check | |
| E3 primary, E5 spot-check | Full UAT on E3, quick confirm on E5 | |

**User's choice:** Both printers, both scenarios (→ D-09).

### Automated regression

| Option | Description | Selected |
|--------|-------------|----------|
| Harden mock to real wire contract | Capture real klippy-restart wire sequence → harden FakeWebSocket → write regression | ✓ |
| Live regression + light unit test | Test against existing mock; trust live UAT as the real gate | |
| Live UAT is the only gate | Don't trust mocks for this class at all | |

**User's choice:** Harden mock to real wire contract — fixture-first, like the Phase-9 calibration captures.
**Notes:** Existing `KlippyReadyResyncTest.kt` is green while the real behavior fails on-device — the canonical mock-that-lied; harden it (→ D-10).

---

## Claude's Discretion

- Exact recovery mechanism (fixed in-session re-subscribe vs force-reconnect) — decided from root cause per D-01.
- Cadence-contract doc format and location under `docs/`.

## Deferred Ideas

- Process-death recovery, Doze/always-on, burn-in, R8, signed APK → Phase 14.
- CONS-01 G-code SEND → still deferred (read-only console).
- Unrelated pending todos (console-macro-page-ux-flow, status-progress-ring-dual-source-jump, benchmark-harness-fairness, macrobenchmark-module-wiring, files-delete-gating) → own phases/quick tasks.
