---
phase: 2
reviewers: [codex]
reviewed_at: 2026-05-30T18:03:16Z
plans_reviewed: [02-01-PLAN.md, 02-02-PLAN.md, 02-03-PLAN.md, 02-04-PLAN.md]
reviewer_notes: "Running inside Claude Code CLI — claude skipped for independence. gemini/coderabbit/opencode/qwen/cursor not installed. Codex was the only available external reviewer."
---

# Cross-AI Plan Review — Phase 2

## Codex Review

**Summary**
The plans are strong on separation of concerns and testability, especially the pure reducer, fake socket, id-correlation, and reconnect/state-store seams. The main risk is that several critical integration details are still underspecified or internally inconsistent: request lifecycle cleanup, handshake ordering around `objects.list`, auth retry semantics, `Connected` timing, and conflation boundaries. As written, the plans are close, but I would not execute them unchanged because a few gaps could produce hanging coroutines, stale "connected" state, or retry loops that misclassify protocol/auth failures.

**Strengths**
- Clear wave decomposition: contracts/fixtures → pure state → protocol → session integration.
- Correct emphasis on JSON-RPC `id` correlation under interleaved `notify_*`.
- Good separation of raw notification streams from throttled state, especially preserving `notify_gcode_response`.
- Pure reducer and capability derivation are the right testable shape.
- Reconnect-now hook is explicitly testable, which is essential with uncapped backoff.
- Auth scope is honest: live hardware proves no-auth, mock tests prove API-key path.
- Human verification gate for Wi-Fi-yank is appropriate.

**Concerns**
- **HIGH — Pending RPCs can hang forever on socket close.** `JsonRpcClient` needs explicit request timeouts and must fail/remove all pending `CompletableDeferred`s when the socket closes, send fails, parsing fails for a response, or the client is cancelled. Otherwise reconnect/handshake can deadlock under exactly the failures this phase targets.
- **HIGH — Handshake ordering conflicts with capability detection.** Plan 04 says `identify → objects.query → objects.subscribe` exactly once, but also says subscribe set is derived from `objects.list`. The real sequence must include `printer.objects.list` before derived query/subscribe, and `Capabilities` must be exposed/re-run on every reconnect.
- **HIGH — `Connected` state timing is ambiguous.** If `ConnectionState.Connected` is emitted on socket open instead of after identify + fresh query + subscribe seed, downstream panels can observe "connected" while state is stale or not resynced. Keep `Connecting` until resync completes, or add an internal syncing state.
- **HIGH — Socket/client ownership is underdesigned.** `MoonrakerSocket` as a cold `callbackFlow` plus a separate `send()` path risks sending before `onOpen`, sending after close, or having no active socket handle. Define a concrete session object or outbound channel whose lifecycle is tied to the active websocket collection.
- **MEDIUM — Auth error policy is too broad.** "Any identify error → AuthRequired" will hide protocol bugs such as duplicate identify, invalid params, or server-side failures. Map clear unauthorized shapes to `AuthRequired`; preserve other RPC errors as `ProtocolError`/`ServerError`.
- **MEDIUM — Auth retry semantics are missing.** With a bad API key, the retry-forever supervisor may repeatedly fetch tokens and reconnect. Auth-required should probably enter a stable error state or retry very gently until config changes/manual reconnect.
- **MEDIUM — Uncapped backoff needs overflow-safe arithmetic.** `base * 2^attempt` will eventually overflow or become unusable. "No ceiling" still needs safe duration handling and tests for large attempts.
- **MEDIUM — Conflation may delay/drop important state transitions.** Sampling the assembled `PrinterState` can delay or suppress stale markers, Klippy shutdown/error, print-state transitions, or recovery flags. Conflate only high-rate status fields, while connection/lifecycle/auth transitions remain immediate.
- **MEDIUM — Golden fixture capture is a hidden live dependency in an autonomous wave.** Plan 01 is `autonomous: true` but requires a live Ender 5 Plus capture. Add an explicit fallback fixture strategy or make that a human/manual setup step.
- **MEDIUM — Capability `powerDevices` is modeled but never populated.** The roadmap/success criteria mention power capability. Either add the Moonraker source for power devices in Plan 04 or explicitly defer/remove it from Phase 2 success criteria.
- **MEDIUM — REST/auth transport needs injection.** `MoonrakerAuth` tests require fake 401/token responses. The plan should require injectable `Call.Factory`/REST client; otherwise unit tests will drift toward real network or hard-to-test code.
- **LOW — `@Immutable` slightly leaks Compose into the headless state contract.** It may be acceptable in the app module, but "toolkit-agnostic" would be cleaner without Compose annotations in core state.
- **LOW — Fixture redaction is too narrow.** Grepping only `192.168`/`.local` misses hostnames, file paths, macro names, thumbnails, and printer-specific metadata. Redaction criteria should be broader.

**Suggestions**
- Add `JsonRpcClient.close(cause)` / socket-closed handling that atomically fails all pending requests and clears the map; add per-request timeout tests.
- Change handshake test expectations to: `identify → objects.list → deriveCapabilities → objects.query(subset) → objects.subscribe(subset)`, then expose both `printerState` and `capabilities`.
- Define `Connected` as "resynced and subscribed," not merely websocket-open.
- Replace vague `send()` ownership with an `ActiveSocket`/`RpcConnection` abstraction created after `onOpen`, cancelled on close, and unavailable outside its lifecycle.
- Add a typed error model: `AuthRequired`, `NetworkUnavailable`, `ProtocolError`, `ServerError`, `ParseError`.
- Make auth failures stop or enter slow/manual retry rather than normal reconnect churn.
- Inject `BackoffPolicy`, jitter RNG, socket factory, and REST client so tests are deterministic.
- Split state emission into immediate control-plane flows (`ConnectionState`, `KlippyState`, stale/auth) and sampled high-rate data.
- Make golden capture a separate manual prerequisite or provide committed redacted fixture fallbacks.

**Risk Assessment**
Overall risk: **MEDIUM-HIGH**. The architecture is sound, but the missing lifecycle/error details are in the exact areas that determine whether the spine is resilient: hanging RPCs, resync correctness, auth classification, and reconnect behavior. Fixing those in the plans before execution should bring the risk down to medium or low.

---

## Consensus Summary

Only one external reviewer (Codex) was available in this environment (Claude was skipped for
independence as the host CLI; Gemini/CodeRabbit/OpenCode/Qwen/Cursor are not installed), so this is a
single-reviewer pass rather than a true cross-AI consensus. Treat the findings below as one strong
independent perspective, not a vote.

### Agreed Strengths
- Wave decomposition and testable seams (pure reducer, FakeWebSocket, id-correlation, reconnect store).
- Honest auth scoping (live = no-auth per D-06; full auth path mock-only).
- gcode_response stream correctly kept separable from throttled status state.

### Key Concerns (single reviewer, prioritized by severity)
**HIGH — these are real plan gaps worth fixing before execution:**
1. **Pending-RPC cleanup on socket close** — `JsonRpcClient` must fail + clear all pending
   `CompletableDeferred`s on close/cancel/send-fail and enforce per-request timeouts, or
   handshake/reconnect can deadlock. (touches 02-03, 02-04)
2. **Handshake vs. capability ordering** — the real sequence needs `objects.list` *before*
   query/subscribe so the subscribe set is derived from detected capabilities; `Capabilities` re-run
   on every reconnect. The "exactly once: identify→query→subscribe" wording in 02-04 omits `objects.list`. (02-04, 02-02)
3. **`Connected` timing** — define `Connected` as "resynced + subscribed," not socket-open, or add an
   internal `Syncing` state, so panels never see "connected but stale." (02-01 contract, 02-04)
4. **Socket/send ownership** — replace the cold-`callbackFlow` + separate `send()` shape with a
   concrete `ActiveSocket`/`RpcConnection` whose lifecycle is bound to the live socket (no send before
   open / after close). (02-03)

**MEDIUM — worth addressing:**
- Narrow the auth-error policy (typed errors: `AuthRequired` vs `ProtocolError`/`ServerError`/`ParseError`) instead of "any identify error → AuthRequired".
- Define auth-failure retry as a stable/gentle state, not normal retry-forever churn against a bad key.
- Overflow-safe backoff arithmetic + tests for large attempt counts (uncapped ≠ unbounded `2^n`).
- Conflate only high-rate status fields; keep connection/Klippy/auth/stale transitions immediate.
- Plan 01 golden-fixture capture is a live-hardware dependency inside an `autonomous: true` wave — add a committed redacted-fixture fallback or mark it a manual prerequisite.
- `powerDevices` is modeled but unpopulated (it comes from a different Moonraker API, not `objects.list`) — wire its real source or explicitly defer it out of Phase-2 scope.
- Inject the REST `Call.Factory` so the 401/oneshot-token path is unit-testable.

**LOW:**
- `@Immutable` leaks Compose into the "toolkit-agnostic" core state contract.
- Fixture redaction (grep `192.168`/`.local`) is too narrow — also scrub hostnames, file/macro names, thumbnails.

### Divergent Views
N/A — single reviewer.

### Reconciliation note vs. existing artifacts
Several MEDIUM items are already partially addressed in CONTEXT/RESEARCH and may be lower-risk than
Codex (which did not weight every nuance) implies:
- **Conflation** — STATE-03 / D already mandate gcode separability; the concern sharpens it to *also*
  keep lifecycle/auth/stale transitions immediate, which is a genuine refinement.
- **`powerDevices`** — RESEARCH A4 explicitly notes power devices come from a different API and were
  intentionally left `emptyList()`; this is a "make the deferral explicit in success criteria" doc fix,
  not a missing feature.
- **Broad auth policy** — was a *deliberate* defensive choice for RESEARCH open-question A5 (exact ws
  auth error code is MEDIUM-confidence). Codex's typed-error refinement is compatible: default-unknown
  → AuthRequired can stay as a fallback while clearly-typed errors are classified.

The four HIGH items are the ones that most justify a `--reviews` replan pass.
