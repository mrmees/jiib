# Phase 2: Connection & State Foundation - Context

**Gathered:** 2026-05-30
**Status:** Ready for planning

<domain>
## Phase Boundary

A resilient, fully-testable Moonraker connection/state **spine** — and nothing visible. One OkHttp
websocket feeds a diff-merged single-source-of-truth `PrinterState` `StateFlow` with: capability
detection, reconnect-with-resync (`identify → objects.query → objects.subscribe`), optional auth
(oneshot-token + `X-Api-Key`), correct JSON-RPC `id` correlation under interleaved `notify_*`, and
high-rate streams conflated to ~2–4 Hz at the state layer. Klippy lifecycle
(ready/printing/startup/error/shutdown) is exposed as a first-class field that WILL drive routing
later. Connection state (connecting/connected/disconnected/error) is always observable.

The spine connects via a **static/dev config** (no user-facing config screen — that's CONN-01 in
Phase 3, where UI + DataStore live). It is proven against a **mock socket** replaying golden Ender 5
Plus frames AND a **real Ender 5 Plus** on the LAN.

**Explicitly NOT in this phase:** no panels, no app shell, no foreground service, no config UI, no
navigation, no Emergency Stop button, no UI primitives. No reuse of the Phase-1 throwaway smoke test
(`CleartextMoonrakerSmokeTest.kt`) — that is a disposable probe, not the start of the connection
layer. This is the load-bearing foundation everything else codes against.

</domain>

<decisions>
## Implementation Decisions

### Reconnect Policy (CONN-03)
- **D-01 — Uncapped exponential backoff + jitter, retry forever.** No ceiling on the backoff
  interval and no give-up budget. Rationale (user): a 3D printer can legitimately sit powered-off
  for **weeks**; the app must not spam the LAN with reconnect attempts when the printer is obviously
  offline for a good reason. Gentle-to-the-network is the deliberate default here.
- **D-02 — The spine MUST expose a manual "reconnect now" trigger** (e.g. `requestReconnectNow()` on
  the connection manager) that **cancels the pending backoff `delay()` and fires a connection attempt
  immediately.** This is the escape hatch that makes uncapped backoff acceptable on an unattended
  appliance: a printer that's actually back online is recovered the instant a human interacts, not on
  the backoff clock.
  - **Phase boundary:** Phase 2 owns the *hook only*. The disconnected-overlay UI that calls it
    (a "disconnected" surface with an immediate-retry affordance, fired on user tap) is a **Phase 3
    (shell)** concern — there is no UI in Phase 2. Do not build the overlay here; just expose the
    trigger and make it unit-testable (backoff cancels, immediate attempt issued).

### Disconnected / Stale State Policy (STATE-01, CONN-06)
- **D-03 — Retain last-known values + a `stale`/`disconnected` marker.** On socket drop, `PrinterState`
  keeps the last temps/position/job/etc. and exposes an explicit staleness/connection marker so
  (future) panels can grey out rather than blank. A momentary Wi-Fi blip must NOT wipe the screen —
  a last reading shown dimmed is more useful on an always-on display than empty/unknown.
- **D-04 — The resync handshake overwrites stale state with truth on reconnect.** After
  `identify → objects.query → objects.subscribe`, the fresh `objects.query` snapshot replaces the
  retained values so displayed state is correct (never stale) post-recovery (CONN-04). The
  connection-state `StateFlow` (connecting/connected/disconnected/error) is always observable
  alongside the data (CONN-06).

### Dev / Static Config + Auth Reality (CONN-02)
- **D-05 — Static config via gitignored `local.properties` → `BuildConfig` fields.** Host/port/optional
  API key are read from a gitignored properties file into `BuildConfig` at build time. Nothing secret
  (no real printer IP, no key) is committed. Set once on the dev machine; mirrors how the Phase-1
  smoke test took host/port from instrumentation args. The app can still be hand-launched on the
  tablet to watch it connect/reconnect (unlike a test-args-only approach).
- **D-06 — Auth reality: the test-bed Ender 5 Plus Moonraker is OPEN / trusted-client (no API key).**
  Consequence for the real-hardware proof: the on-device run exercises the **no-auth** path only. The
  full optional-auth flow (CONN-02 — websocket oneshot-token + REST `X-Api-Key`, and graceful
  `401 auth required` state) is therefore proven in **mock-socket tests only**. This is the honest
  scope: success-criterion #5 (auth end-to-end) is satisfied at the mock-socket level, not on live
  hardware, because the live printer doesn't require auth. The planner must make the auth path
  fully mock-testable (oneshot-token success, `X-Api-Key` on REST, graceful `401`).

### Claude's Discretion
- **Golden-frame fixtures:** The user opted not to discuss this in depth. Default decision the planner
  should adopt: **capture real `notify_status_update` / `objects.query` frames from the live Ender 5
  Plus** (the test bed exists) to seed the mock-socket golden corpus, rather than hand-authoring purely
  synthetic frames — the success criteria explicitly call for "golden Ender 5 Plus frames." Hand-author
  only the adversarial/edge cases the real printer won't naturally produce (interleaved-`notify_*`
  ordering for the STATE-05 id-correlation test, `401`, klippy shutdown/error).
- **Capabilities model scope (STATE-02):** Left to the planner. Reasonable default — model what v1
  panels actually gate on (heaters incl. bed, extruder count, fans, macros, power devices) as an
  immutable, pure-function-derived `Capabilities` object, re-run on every reconnect; don't over-build
  a generic mirror of everything `objects.list` returns. Keep it a pure, unit-tested function
  (input: `printer.objects.list` response → output: `Capabilities`).
- **Throttle/conflation mechanism (STATE-03):** Mechanism is the planner's call, but one constraint
  is load-bearing — the ~2–4 Hz conflation applies to **status updates** (latest-wins is fine);
  it must **NOT** be applied to the `notify_gcode_response` line stream that Console (Phase 7) will
  consume, where conflation would silently drop lines. Keep the raw event bus separable from the
  throttled state.
- Module/package layout, the `callbackFlow` socket bridge, the JSON-RPC `Map<id, CompletableDeferred>`
  correlation layer, and reconnect coroutine supervision structure are all the planner/executor's call
  (the stack itself is locked — see canonical refs).

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase scope & requirements
- `.planning/ROADMAP.md` § "Phase 2: Connection & State Foundation" — the five success criteria this
  phase must close, and the **DEEPER research note** (pin the exact `notify_*` set + auth handshake
  edge cases before planning).
- `.planning/REQUIREMENTS.md` — the nine requirements mapped here: **CONN-02** (optional auth),
  **CONN-03** (resilient ws + auto-reconnect/backoff), **CONN-04** (resync handshake), **CONN-06**
  (connection state visible), **STATE-01** (diff-merged single source of truth), **STATE-02**
  (capability detection/gating model), **STATE-03** (~2–4 Hz throttle), **STATE-04** (Klippy lifecycle
  drives routing), **STATE-05** (`id` correlation, no in-order assumption).

### Locked technology stack (do NOT re-decide; pins already verified in Phase 1)
- `CLAUDE.md` (repo root) § "Technology Stack" → "Networking deep-dive: the Moonraker JSON-RPC model"
  — the prescriptive connection design: **one OkHttp `WebSocket` wrapped in `callbackFlow`**, a thin
  JSON-RPC layer over **kotlinx.serialization** with `Map<id, CompletableDeferred<JsonElement>>` for
  response correlation, `notify_*` routed to `SharedFlow`s by method name, `StateFlow` for current
  state, structured-concurrency reconnect with backoff that re-sends `objects/subscribe` and re-emits
  a connection-state `StateFlow`. Also why OkHttp over Ktor/Scarlet/Java-WebSocket for this project.
- `gradle/libs.versions.toml` — the pinned, minSdk-23-safe versions already in place: OkHttp 4.12,
  Retrofit 2.11 + kotlinx-serialization converter, kotlinx.serialization 1.7.3, coroutines 1.9.0.
  Use these; do not add deps that raise the merged-manifest minSdk floor (verifyMinSdk gate enforces it).

### Phase 1 context (the reality this builds on)
- `.planning/phases/01-platform-gate-toolkit-benchmark-cleartext-smoke-test-scaffol/01-CONTEXT.md`
  — D-01a hardware correction (Adreno 320 / 2GB / 1920×1200 / armeabi-v7a), and the throwaway-smoke-test
  boundary (D-10/D-11): the Phase-1 probe is disposable and must NOT seed the Phase-2 connection layer.
- `docs/adr/0001-ui-toolkit-decision.md` — HYBRID verdict. Not directly needed for the headless spine,
  but note: the spine's view-models must stay **toolkit-agnostic** (`StateFlow` consumable by both
  Compose and classic Views) since the temp graph / files list / console are Views later.

### Device-reality caveat (affects the real-hardware proof)
- The physical "Nexus 7 2013" (`flox`) runs **LineageOS 18.1 / Android 11 / API 30**, not stock
  Android 6 / API 23 (see repo `CLAUDE.md` "Device reality" + Phase-1 SUMMARYs). `minSdk 23` is the
  retained install floor; on-device evidence reflects an API-30 runtime on genuine Adreno 320 / 2GB
  hardware.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **None reusable for the spine — greenfield connection layer.** The repo has only the Phase-1 build
  scaffold (`:app` + `:macrobenchmark` + `build-logic`), the benchmark scenes
  (`app/.../bench/*`, throwaway-ish, benchmark-only), and the **disposable** smoke test
  `app/src/androidTest/java/works/mees/dinghy/smoke/CleartextMoonrakerSmokeTest.kt`. That smoke test
  hand-rolls a minimal JSON-RPC exchange (ws open, REST GET, subscribe+ack-by-id, await deterministic
  `notify_status_update`) and **explicitly must NOT be reused** — but it is a useful *reference* for
  the exact Moonraker call shapes (`printer.objects.subscribe` params, REST `server/info` +
  `printer/info` `result` keys, the `notify_status_update` params-array structure).
- Base package is `works.mees.dinghy`. App namespace/module structure is established.

### Established Patterns
- Pinned version catalog (`gradle/libs.versions.toml`) is the single source of versions — add the
  connection-layer deps there, not inline. `verifyMinSdk` build-logic plugin asserts the merged
  manifest stays minSdk 23.
- kotlinx.serialization is already wired and used (smoke test parses Moonraker JSON with
  `ignoreUnknownKeys = true; isLenient = true` — the right posture for Moonraker's loose/heterogeneous
  payloads; carry that forward into the real layer).

### Integration Points
- Nothing to integrate with yet — this phase IS the integration surface every later phase (service,
  shell, panels) codes against. Design the `PrinterState` `StateFlow`, the connection-state
  `StateFlow`, the `notify_*` `SharedFlow`(s), the `Capabilities` model, and the command-dispatch
  suspend API as the **public contract** later phases consume.

</code_context>

<specifics>
## Specific Ideas

- **The reconnect UX model (user's framing):** uncapped backoff is *intentional* so a printer that's
  off for weeks isn't getting hammered — paired with a **disconnected overlay (Phase 3) whose
  reconnect affordance immediately fires an attempt** via the Phase-2 `requestReconnectNow()` hook.
  "The app doesn't need to be spamming the network if it's obvious it's offline for some good reason."
- **Stale-but-shown over blank:** on a wall-mounted always-on screen, the last reading dimmed beats an
  empty screen during a transient drop.
- **Mock-socket fidelity:** seed the golden corpus from the user's real Ender 5 Plus; hand-author only
  the adversarial frames (interleaved notifications for STATE-05, `401`, klippy shutdown/error) the
  idle printer won't naturally emit.

</specifics>

<deferred>
## Deferred Ideas

- **Disconnected-printer overlay UI** (a disconnected surface with an immediate-retry button that calls
  the Phase-2 `requestReconnectNow()` hook) → **Phase 3 (Service, Shell & State-Driven Navigation)**.
  Phase 2 exposes the trigger; the shell renders the overlay and wires the tap.
- **User-facing connection config screen (CONN-01)** → already roadmapped to **Phase 3** (UI + DataStore).
  Phase 2 stays on the static/dev config.

Otherwise: discussion stayed within phase scope. The capability-gated panels, temperature graph,
console, etc. that naturally came up are already scoped to Phases 3–7 by the roadmap and were not
pulled forward.

</deferred>

---

*Phase: 2-connection-state-foundation*
*Context gathered: 2026-05-30*
