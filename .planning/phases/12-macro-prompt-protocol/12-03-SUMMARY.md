---
phase: 12-macro-prompt-protocol
plan: 03
subsystem: prompt-protocol
tags: [kotlin, coroutines, flow, holder, state-flow, prompt-protocol, host-testable, disconnect-safety]

# Dependency graph
requires:
  - phase: 12-macro-prompt-protocol
    provides: "12-01 parseAction/disconnectEvent + 12-02 reduce/promptView/initialPromptState + the PromptView/PromptStateData/PromptOpts model"
  - phase: 09-calibration
    provides: "ProbeCalibrateHolder template (ctor(scope, store, events?); MutableStateFlow→StateFlow; init{scope.launch{collect}}; dispatcher-Failure fold)"
  - phase: 02-spine
    provides: "store.gcodeResponses un-throttled SharedFlow + store.printerState.connection (ConnectionState) + CommandDispatcher DispatchEvent.Failure + PrinterCommands.scriptParams"
provides:
  - "Spine-level PromptEngine holder: gcode-stream subscribe + parse + reduce into a StateFlow<PromptView>"
  - "Disconnect-is-a-LOCAL-close-with-no-prompt_end on a Connected→Disconnected/Error edge (D-10 / Pitfall 6 / T-12-09)"
  - "Stable dispatch keys (buttonKey/footerKey separate namespaces, closeKey) + closeGcode + scriptParamsFor + dispatchMethod for the overlay"
  - "prompt:-keyed dispatcher-Failure fold to latestPromptError (the overlay toast source)"
affects: [12-04 overlay-render, 12-05 AppShell-wiring, macro-prompt-protocol]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Toolkit-agnostic holder mirroring ProbeCalibrateHolder (ctor(scope, store, events?, opts); MutableStateFlow exposed read-only; init-launched collectors; NO second throttle; NO Compose import)"
    - "Connection-edge guard (distinctUntilChanged + a wasConnected latch) so only a true Connected→down edge triggers the local close — not the first idle emission"
    - "Engine owns dispatch-key bookkeeping but never calls dispatch itself (it exposes keys+params); guarantees zero dispatch on disconnect by construction"

key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/prompt/PromptEngine.kt"
    - "app/src/test/java/works/mees/dinghy/prompt/PromptEngineTest.kt"
  modified: []

key-decisions:
  - "Disconnect uses a wasConnected latch on the distinctUntilChanged connection stream so only a genuine Connected→Disconnected/Error EDGE fires the local close; the idle-Disconnected first emission (no prior Connected) does NOT close — avoids a spurious close before the session is even up"
  - "The engine NEVER calls CommandDispatcher.dispatch for buttons/close — it exposes keys + scriptParamsFor params; the AppShell wires dispatch in 12-05. This makes zero-dispatch-on-disconnect a structural guarantee, not a runtime check"
  - "buttonKey = prompt:<epoch>:<index>, footerKey = prompt:<epoch>:footer:<index> (separate namespace) so a content button N and a footer button N never share a dispatcher key (Codex pre-execute finding); epoch isolates a replaced prompt's keys (Pitfall 4)"
  - "latestPromptError is a plain @Volatile var (not a StateFlow) mirroring ProbeCalibrateHolder's latestError — the overlay reads it on a Failure event; the message is already redacted by CommandDispatcher (T-12-12)"

patterns-established:
  - "PromptEngineTest drives a real PrinterStateStore under runTest(UnconfinedTestDispatcher); setConnectionState() supplies the Connected baseline + the transition edge (seed() preserves connection, so it can't drive transitions); bounded runCurrent() only"

requirements-completed: []

# Metrics
duration: ~12min
completed: 2026-06-04
---

# Phase 12 Plan 03: PromptEngine Spine Holder Summary

**Landed the spine-level `PromptEngine` holder — an independent, non-starved subscriber on the un-throttled `store.gcodeResponses` stream that folds each parsed line through the pure 12-02 reducer into a `StateFlow<PromptView>`, closes the prompt LOCALLY on a Connected→Disconnected/Error edge while dispatching NO `prompt_end` gcode (the cross-client safety property D-10 / Pitfall 6, unit-pinned), and exposes the stable collision-free dispatch keys + the `prompt:`-keyed Failure fold the 12-05 overlay wires — all in a Compose-free, host-testable holder mirroring `ProbeCalibrateHolder`, with `PromptEngineTest` and the full prompt suite GREEN.**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-06-04T22:41Z
- **Completed:** 2026-06-04
- **Tasks:** 1
- **Files modified:** 2 created

## Accomplishments
- `PromptEngine.kt` — `ctor(scope, store, events? = null, opts = PromptOpts())` mirroring `ProbeCalibrateHolder`: `MutableStateFlow<PromptView>` seeded from `promptView(initialPromptState(opts))`, exposed read-only as `view`; the full `PromptStateData` held in a private var (only the 6-key view leaks).
- Collector (1): subscribes `store.gcodeResponses` (the SAME un-throttled SharedFlow the Console reads, as an INDEPENDENT collector — D-13 / T-12-10), runs `parseAction(line)?.let { reduce }` and republishes the view. `parseAction` is total (12-01) and `reduce` never wedges (12-02), so a garbage/out-of-order stream degrades deterministically and never throws.
- Collector (2): maps `printerState.connection`, `distinctUntilChanged()`, and on a true **Connected→Disconnected/Error edge** (guarded by a `wasConnected` latch) runs `reduce(state, disconnectEvent())` and republishes — a LOCAL close that clears prompt+pending+container and dispatches NOTHING (D-10 / Pitfall 6 / T-12-09). `Syncing`/`Connecting` do NOT close (A3).
- Collector (3, only if `events != null`): folds `DispatchEvent.Failure` whose `key.startsWith("prompt:")` into `latestPromptError` (the overlay toast); a non-prompt key is ignored. The message is already redacted by `CommandDispatcher` (T-12-12).
- Dispatch bookkeeping: `buttonKey(i)="prompt:<epoch>:<i>"`, `footerKey(i)="prompt:<epoch>:footer:<i>"` (separate namespace — no content/footer same-index collision, the Codex pre-execute finding), `closeKey="prompt:close"`, plus `closeGcode`, `dispatchMethod` (GCODE_SCRIPT), and `scriptParamsFor(gcode)` reusing `PrinterCommands.scriptParams`. The engine exposes keys+params but never dispatches itself.
- `PromptEngineTest` GREEN (8 tests): stream→visible; end→hidden; Connected→Disconnected closes locally with zero dispatches; Connected→Error also closes; Syncing/Connecting do NOT close; prompt-keyed Failure folds, non-prompt ignored; stable keys with `buttonKey(n) != footerKey(n)` for all n + epoch isolation across a replaced prompt.

## Task Commits

1. **Task 1: PromptEngine holder — stream subscribe, reduce, disconnect-local-close, dispatch keys** - `57580f7` (feat)

_Task 1 was authored `tdd="true"` but the project-level MVP+TDD gate is inactive (`tdd_mode: false`), so it is a single feat commit (matching the 12-01/12-02 precedent) — the holder + its host tests land together, unit-GREEN._

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/prompt/PromptEngine.kt` (created) — the spine holder: 3 init-launched collectors (gcode-stream reduce, disconnect-local-close, Failure fold), read-only `view: StateFlow<PromptView>`, `latestPromptError`, and the stable dispatch-key/params helpers. Plain Kotlin — no Compose import.
- `app/src/test/java/works/mees/dinghy/prompt/PromptEngineTest.kt` (created) — 8 host tests over a real `PrinterStateStore` + a `MutableSharedFlow<DispatchEvent>`, covering every `<behavior>` case incl. the disconnect-emits-no-prompt_end assertion.

## Decisions Made
- **Disconnect fires on a true edge, not on the idle-Disconnected first emission.** The connection stream starts at `Disconnected` (the store's default). A `wasConnected` latch on the `distinctUntilChanged` stream means the local close only fires when the prior state was `Connected` — so the engine never spuriously "closes" a prompt that was never up. This matches `ProbeCalibrate`'s edge handling intent.
- **The engine never calls `dispatch`.** Rather than inject a `CommandDispatcher` and prove "zero calls" at runtime, the engine exposes keys + `scriptParamsFor` params and lets the AppShell wire dispatch (12-05). Zero-dispatch-on-disconnect is then a STRUCTURAL guarantee (the disconnect path has no dispatch seam at all), and the test asserts the view closed with an empty recording list — the strongest unit form of T-12-09 available before the 12-05 on-device gate.
- **`setConnectionState` (not `seed`) drives the test transitions.** `PrinterStateStore.seed` preserves the current `connection` field, so it cannot drive a connection edge; the test establishes the `Connected` baseline and the down-edge via `setConnectionState`.

## Deviations from Plan

None - plan executed exactly as written. (Task 1 is a single feat commit rather than a TDD test→feat split because `tdd_mode` is disabled project-wide; this matches the 12-01/12-02 precedent and is not a behavioral deviation. The plan's optional `reset()` seam from the ProbeCalibrate template was not added — the engine is a single long-lived session holder with no per-page re-entry latch to clear, and no behavior or acceptance criterion calls for it.)

## Issues Encountered
- None. `PromptEngineTest` was GREEN on the first real run; the full prompt suite (PromptFixtureTest / PromptReducerTest / PromptParseTest / PromptMarkupTest / PromptEngineTest) remains GREEN with no regressions.

## Next Phase Readiness
- The wiring centerpiece is in place: the live gcode stream now becomes a `StateFlow<PromptView>`, with the disconnect-local-close cross-client safety property unit-pinned. 12-04 consumes `view` (the markup AST + items) for the Compose/Views overlay render; 12-05 constructs the engine in the AppShell, hoists the overlay, and wires `CommandDispatcher.dispatch(buttonKey/footerKey/closeKey, dispatchMethod, scriptParamsFor(...))` — then on-device-gates the disconnect-no-prompt_end property against a second live client (the 12-05 manual gate).
- No blockers. PROMPT-01/PROMPT-03 stay satisfied at the reducer+engine layer; their phase-level closure is the 12-04/12-05 frontend + the on-device gate.

## Self-Check: PASSED

Both created files exist on disk; the task commit (`57580f7`) is present in git history. `PromptEngineTest` + the full prompt suite verified GREEN via `:app:testDebugUnitTest`. `PromptEngine.kt` has NO `androidx.compose` import (grep confirmed).

---
*Phase: 12-macro-prompt-protocol*
*Completed: 2026-06-04*
