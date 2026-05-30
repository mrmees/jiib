# Project Research Summary

**Project:** Dinghy Display
**Domain:** Native Android Moonraker/Klipper printer-control touchscreen (KlipperScreen-equivalent for old Android tablets)
**Researched:** 2026-05-29
**Confidence:** HIGH

## Executive Summary

Dinghy Display is a native Android application that replaces the KlipperScreen-on-Linux + VNC/XSDL stack with a self-contained APK that runs on cheap, retired Android hardware (Nexus 7 2013 class: Android 6.0, Tegra-era GPU, 2GB RAM). The entire technical stack is well-understood — Moonraker exposes a complete JSON-RPC websocket + REST API with verified documentation, the modern AndroidX/Compose ecosystem now targets minSdk 23 as its baseline, and the KlipperScreen Screen Catalog provides an authoritative inventory of what a complete feature set looks like. The research converges on a clear, dependency-ordered build sequence: harden the websocket transport and state store first, gate every panel off a capability model derived from `printer.objects.list`, and only then build UI panels in the order that delivers the fastest testable "connect → start a print → watch it finish" loop on Matthew's real Ender 5 Plus.

The central recommendation is to build the connection/state layer as a foreground Service owning a single OkHttp WebSocket, merging `notify_status_update` partial diffs into one normalized `PrinterState` `StateFlow`, and having the printer's Klippy lifecycle (not the user) own the primary navigation route — mirroring KlipperScreen's `__main` / `__print` / `__splashscreen` model exactly. This approach is well-supported by the Moonraker protocol (push-only, partial diffs, no replay) and by Android's lifecycle model (foreground Service survives Activity recreation, `FLAG_KEEP_SCREEN_ON` survives tablet sleep). The connection layer should be buildable and testable against a mock WebSocket before any panel exists, which is the project's single most valuable risk-reduction step.

The key risk is hardware performance on the Nexus 7. One genuine conflict emerged between the research outputs: STACK.md recommends Jetpack Compose (jank parity with Views since 1.9, Baseline Profiles as the mitigation) while PITFALLS.md establishes that Baseline Profiles are a no-op on Android 6.0/7.0 (the framework AOT-compiles on those versions, so profile-guided optimization is unavailable). This conflict must be resolved by a real-device benchmark on the Nexus 7 early in the foundation phase before committing the toolkit for high-churn surfaces. See the Open Decision section below.

---

## OPEN DECISION: UI Toolkit for High-Churn Surfaces

**This is the single unresolved conflict in the research and the most consequential early decision.**

### Position A — Compose everywhere (STACK.md)

Jetpack Compose's scroll-jank parity with Views is documented since Compose 1.9. The app's UI is a good Compose fit: mostly static panels with a small set of frequently-updating values. Baseline Profiles ship a pre-compiled hot path that cuts startup and scroll cost by ~30%. Compose dramatically reduces code volume for dynamic/capability-gated panels, which matters for a solo-maintained ~34-panel eventual target.

### Position B — Views/RecyclerView for high-churn surfaces (PITFALLS.md)

Baseline Profiles are a **no-op on Android 6.0 and 7.0** (API 23–24). On those versions, the framework AOT-compiles the app at install time using its own profile, so `androidx.profileinstaller` does nothing useful. The Nexus 7 2013 runs Android 6.0.1. This eliminates Compose's primary performance lever on the **only target hardware that matters**. The "Compose matches Views" benchmarks are run on modern hardware with profiles installed — neither condition holds on a Tegra Nexus 7. Classic Views / RecyclerView remain the conservative, proven option for list-heavy and continuously-updating surfaces.

### The Deciding Factor

A real-device benchmark on the Nexus 7 2013, in release mode, during the Foundation phase. Specifically: scroll the Files list (LazyColumn vs. RecyclerView) and render the live temperature dashboard (heater rows + graph updating at 2–4 Hz) on the actual device. Emulator results are not valid. Modern Pixel results are not valid.

### Practical Implication for the Roadmap

The Foundation phase (Phase 1) **must include an on-device performance spike before finalizing the toolkit for panel work.** Build a minimal harness: connect to the real printer, subscribe to live data, render a scrolling list and a live-updating value. Profile on the Nexus 7 with `adb shell dumpsys gfxinfo`. This spike gates all subsequent panel architecture.

### Fallback Position (recommended if Compose underperforms)

A **hybrid approach** is acceptable and explicitly named: use Compose for static/low-churn panels (Move jog grid, Macros list, Console input, Extrude controls) and classic Views (`RecyclerView`, custom `View`) for high-churn surfaces (Files list with thumbnails, temperature graph/dashboard, Console scrollback, Job Status live summaries). The connection/state/service layer is toolkit-agnostic and does not change. `AndroidView` interop makes mixing viable but adds complexity — reserve it for surfaces that demonstrably need it.

**Resolution gate:** The on-device spike in Phase 1 produces a go/no-go decision. If Compose scrolls cleanly on the Nexus 7 at 60fps during a print, proceed with Compose everywhere. If not, apply the hybrid and move on.

---

## Key Findings

### Recommended Stack

The headline finding from STACK.md: as of June 2025, AndroidX moved its default `minSdk` from 21 to 23. The project's minSdk 23 floor is exactly the new modern baseline — not below it. The entire current Compose-first stack is in-bounds, and the trap is the opposite of what you'd fear: don't chase bleeding-edge Compose 1.12 / AGP 9 (requires compileSdk 37), pin to the stable 2026.05 BOM / Compose 1.11 / AGP 8.7.x line.

**Core technologies:**
- **Kotlin 2.1.x** — language; coroutines/Flow, kotlinx.serialization, Compose compiler, the whole modern Android ecosystem is Kotlin-first
- **Jetpack Compose (BOM 2026.05 / Compose 1.11)** — UI toolkit; jank parity with Views since 1.9, dramatically less code for dynamic/gated panels, minSdk 23 — subject to on-device validation spike
- **OkHttp 4.12** — WebSocket + HTTP engine; one TLS/connection-pool stack for both the Moonraker WebSocket and Retrofit REST; trivially wrapped in `callbackFlow`
- **Retrofit 2.11** — Moonraker REST endpoints (file lists, thumbnails, metadata)
- **kotlinx.serialization 1.7** — JSON/JSON-RPC; compile-time codegen (no reflection, fast on weak CPU); ideal for Moonraker's heterogeneous `notify_*` partial diffs and `JsonElement` walking
- **Coroutines + Flow (kotlinx-coroutines 1.9)** — `callbackFlow` WebSocket bridge, `StateFlow` printer state, `SharedFlow` console events, structured reconnect logic
- **Coil 3.x** — thumbnails; Compose-native, minSdk 23 exact match, fixed OOM decoding large PNGs on API ≤23
- **DataStore (Preferences) 1.1** — connection config persistence; coroutine-safe, no ANR risk on slow flash
- **Material 3 (via BOM)** — dark/high-contrast theme suits a printer-side always-on screen
- **Custom Compose `Canvas`** — temperature history graph; a rolling polyline over a ring buffer is lighter than any library, avoids per-frame allocation

**What NOT to use:** Gson (reflection, slow on weak CPU), MPAndroidChart (heavy, View-based, GC-heavy on live updates), Scarlet (unmaintained), Hilt/Dagger in v1 (codegen overkill for a small object graph), WebRTC in v1 (heavy on Tegra), any Google Play Services/Firebase (target hardware lacks current Play Services).

**Build config:** minSdk 23 / targetSdk 35 / compileSdk 35 / AGP 8.7.x. Pin every version in `gradle/libs.versions.toml`. Compose 1.12 will require compileSdk 37 + AGP 9 — stay on the 1.11 line.

### Expected Features

The research grounded the feature set against the actual Moonraker external API docs (2026) and the KlipperScreen Screen Catalog. The competitive bar is concrete: a user will not abandon their VNC/KlipperScreen setup unless this app can run a real print start-to-finish without forcing them back to the browser mid-job.

**Must have — table stakes (v1 core):**
- Resilient WebSocket + JSON-RPC client + `notify_*` event bus — foundation; nothing works without it; highest-risk, highest-value item
- Connection config (host:port, optional API key / trusted-client) persisted, with explicit connecting/ready/reconnecting/error states
- Capability model from `printer.objects.list` — the backbone that keeps every panel honest; build immediately after transport
- Subscription manager (`printer.objects.subscribe`, re-subscribe on reconnect) feeding all live panels
- UI primitives: numeric keypad, confirm-action dialog, toast/message (system IME for text)
- App shell with always-reachable Emergency Stop, persistent chrome (title bar, back/home, live status)
- Main menu as launcher + thermal dashboard (heater rows + temperature graph)
- Temperature panel (per-heater targets, preheat presets, cooldown, keypad, history graph from `server.temperature_store`)
- Move panel (X/Y/Z jog with distance presets, home, disable-motors-with-confirm, live position)
- Extrude panel (extrude/retract, load/unload macro-gated, presets, tool select, block below `min_extrude_temp`)
- Files / Print (browse `server.files.get_directory`, thumbnails + metadata, confirm-before-start dialog)
- Job Status (live progress/thumbnail/temps/speed/Z/time, pause/resume/cancel-with-confirm/restart)
- Macros (discover `gcode_macro` entries, run via `printer.gcode.script`, basic param entry, hide `_underscore` macros)
- Console (`server.gcode_store` backfill + `notify_gcode_response` live, severity coloring)

**Should have — competitive differentiators (first expansion after v1 validation):**
- Fine-tune mid-print (babystep Z / speed % / flow %) — standout value-per-effort; nearly free once Job Status exists
- Camera (native MJPEG) — most-requested post-core addition; first post-core expansion per PROJECT.md
- Fans, Power devices, LEDs — follow on demand

**Defer to v2+:** Bed Mesh, Bed Level, Z Calibrate, Input Shaper, Pressure Advance, Retraction, Limits, Pins, Spoolman, Exclude Object, WebRTC, multi-printer switching, portrait layout.

**Anti-features (explicitly out):** Wi-Fi/NetworkManager management (Android owns Wi-Fi), host OS management/updater, slicer, file upload from tablet, full config editing, Google Play distribution.

**Cross-cutting quality requirements:**
- Single mandatory destructive-action confirm policy for: Emergency Stop, Cancel Print, Restart Print, Disable Motors, Power on/off, Cooldown-while-printing; E-Stop fast-but-deliberate (hold/double-tap rather than a slow modal)
- Notify stream throttled to ~2–4 Hz before hitting the UI — prevents GPU churn on Tegra
- Off-UI-thread thumbnail decode with `inSampleSize` downsampling and bounded bitmap cache; lazy-fetch metadata as rows scroll into view
- Landscape-first layout, big touch targets for dirty/gloved fingers at 1280×800 @7"
- `FLAG_KEEP_SCREEN_ON` on Activity window; screensaver dim overlay with wake-on-tap for burn-in protection

### Architecture Approach

The architecture is MVI-flavored over a single source-of-truth `PrinterState` `StateFlow`, fed by one long-lived OkHttp WebSocket living in a started foreground Service. All `notify_status_update` partial diffs are merged into the retained `PrinterState` — never replaced, never cleared on transient drops. The Klippy lifecycle (not the user) owns the primary navigation route: `ready → main_menu`, `printing/paused → job_status`, `startup/shutdown/error/disconnected → splash`. Commands and state are deliberately decoupled: a command's ack means "Moonraker accepted it," not "the world changed" — UI truth comes only from merged state.

The `connection/` package has zero Android-UI dependencies, making it unit-testable against a mock WebSocket with no emulator — the project's most valuable testing seam.

**Major components:**
1. **MoonrakerClient** — OkHttp WebSocket + REST, sends JSON-RPC, surfaces raw frames, reconnects with backoff+jitter
2. **JSON-RPC correlator** — `Map<Long, CompletableDeferred<JsonElement>>` maps outgoing request `id`s to suspending results; `notify_*` messages (no `id`) route to NotifyDispatcher
3. **NotifyDispatcher** — routes `notify_status_update` / `notify_gcode_response` / `notify_klippy_*` to typed handlers
4. **PrinterRepository** — single source of truth; merges diffs into `PrinterState`; holds `ConnectionState` + `Capabilities`; exposes suspend command methods
5. **CapabilityDetector** — on every Klippy-ready, queries `printer.objects.list` + config, produces immutable `Capabilities`; re-runs on reconnect
6. **ConnectionService** — foreground Service hosting components 1–5; ongoing notification; service-lifetime `CoroutineScope`
7. **Per-panel ViewModels** — map `PrinterState` slices → immutable `UiState`; translate user intents → repository commands
8. **Navigation host** — state-driven root route from `ConnectionState + PrinterState`; user back-stack on top

**Connection handshake (verified against Moonraker docs):**
`connect ws://` → `server.connection.identify` → check `server.info`/Klippy state → `printer.objects.query` (full snapshot → new source of truth) → `printer.objects.subscribe` (push diffs) → on reconnect, repeat entire handshake; backfill Console via `server.gcode_store`

**Key patterns:**
- Per-slice `StateFlow.map{}.distinctUntilChanged()` — prevents a temperature tick recomposing the Move panel (critical on Tegra)
- `@Stable`/`@Immutable` on state objects — lets Compose skip recomposition on unchanged slices
- Thumbnail decode on `Dispatchers.IO` with `inSampleSize` sized to display resolution
- Mock `WebSocketTransport` interface — feed canned `notify_*` frames, assert `PrinterState` evolution, no emulator needed

**Anti-patterns explicitly called out:**
- WebSocket in a ViewModel or Activity (rotation kills it; use foreground Service)
- Replacing state with each diff (diffs are partial; merge into retained full state)
- Optimistic UI from command acks (UI truth from merged state only)
- Clearing state on transient socket drop (keep last state; show reconnect banner)
- One monolithic `StateFlow` recomposing everything (per-slice distinctUntilChanged)
- User-owned home route (printer state machine owns the root)

### Critical Pitfalls

1. **Cleartext blocked on API 23 (OkHttp Marshmallow cleartext bug)** — Ship `res/xml/network_security_config.xml` with `<base-config cleartextTrafficPermitted="true">` from day one. Test `ws://192.168.x.x` on a real API 23 device at your shipping `targetSdk`. Warning sign: works on a modern phone, silently fails on the Nexus 7.

2. **Baseline Profiles are a no-op on Android 6.0/7.0** — The framework AOT-compiles on L/M; profile-guided optimization does nothing. Compose's main performance lever on modern hardware does not exist on the Nexus 7. Resolve by on-device benchmark in Phase 1 before committing panel architecture. Hybrid (Compose for static, Views for high-churn) is the named fallback.

3. **Reconnect without resync leaves the UI lying** — Moonraker push notifications are fire-and-forget; no replay. On every reconnect, run the full `identify → objects.query → objects.subscribe` handshake; treat the query result as the new source of truth. Backfill Console via `server.gcode_store`. Distinguish transport drop (keep last state, show reconnect banner) from Klippy disconnected/shutdown (route to splash).

4. **Treating connected socket as "Klippy ready"** — The WebSocket being open says nothing about whether Klipper can take commands. Route the UI on `klippy_state`, not socket state. Gate all command-sending controls behind `klippy_state == ready`. Surface shutdown reason + recovery actions on the splash screen.

5. **Unthrottled `notify_status_update` melting the Tegra GPU** — Conflate/sample the notify stream to ~2–4 Hz before the UI. Render temperature graph from a ring buffer at throttled cadence, not per-event. Set this pattern in the dashboard — it's the first always-visible high-rate surface.

6. **Inconsistent / missing confirmation gates** — KlipperScreen itself is inconsistent (Power/Pins fire immediately). Do better: one confirm-action primitive for the entire destructive set. Define it in Phase 2, enforce it in every panel phase.

7. **Capability-gated controls showing anyway** — Drive every panel's control set from `Capabilities` derived from `printer.objects.list`. Hide (not disable) controls for absent capabilities. Test against a printer with a different config than your own.

---

## Implications for Roadmap

All four research files converge on the same dependency-ordered build sequence.

### Phase 1: Foundation — Connection, State, and On-Device Validation

**Rationale:** The connection/state layer is the load-bearing, highest-risk foundation. Every panel depends on it. It can be built and tested entirely against a mock WebSocket before any Android UI exists. The toolkit conflict must be resolved here, before panel architecture is locked in.

**Delivers:**
- OkHttp WebSocket + JSON-RPC correlator + NotifyDispatcher (pure Kotlin, zero Android-UI deps)
- `PrinterState` model + diff-merge + `ConnectionState` sealed class
- `CapabilityDetector` (pure function: `objects.list` response → `Capabilities`)
- `network_security_config.xml` cleartext permitted — tested on real API 23 device
- Reconnect loop with exponential backoff + jitter; full `identify → objects.query → objects.subscribe` handshake
- Mock `WebSocketTransport` testing seam; golden-frame fixture tests from real Ender 5 Plus captures
- **On-device performance spike:** connect to real printer, render scrolling list + live-updating values on Nexus 7 in release mode, profile with `gfxinfo` → go/no-go on toolkit

**Avoids:** Pitfall 1 (cleartext), Pitfall 2 (Compose myth), Pitfall 3 (reconnect/resync), Pitfall 4 (socket ≠ ready)

**Exit gate:** `ws://192.168.x.x` connects and receives live `notify_status_update` on real Android 6; mock-WebSocket tests pass; toolkit decision is recorded.

**Research flag:** STANDARD — all patterns verified against official sources. On-device spike is implementation work, not research.

---

### Phase 2: Service + Shell + State-Driven Navigation

**Rationale:** The foreground Service is the lifecycle host. The shell and state-driven navigation prove the `connect → splash → main` loop. UI primitives are a hard dependency of every panel — define the confirm-action policy and throttling pattern here.

**Delivers:**
- `ConnectionService` foreground Service; battery-optimization exemption; `FLAG_KEEP_SCREEN_ON`
- `ConnectionConfigStore` (DataStore/Preferences) + first-run connect screen
- State-driven root routing: `ready → MainMenu`, `printing/paused → JobStatus`, `startup/shutdown/error → Splash`
- Splash/connecting/error/recovery surface (surfaces shutdown reason, offers firmware restart)
- Persistent chrome: title bar, back/home, live status chip, always-reachable Emergency Stop
- UI primitives: numeric keypad, confirm-action dialog (ALL destructive actions route through this), toast/message popup
- Notify stream throttled to ~2–4 Hz at the repository/ViewModel layer

**Avoids:** Pitfall 4 (Klippy state router), Pitfall 5 (Doze/wake), Pitfall 6 (confirm policy defined once), Pitfall 9 (throttling established before first high-rate surface)

**Exit gate:** App connects to Ender 5 Plus, splash during startup, main menu on Klippy ready, job_status during a print, E-stop always reachable, Emergency Stop goes through confirm.

**Research flag:** STANDARD — foreground Service + Doze + DataStore patterns are well-documented.

---

### Phase 3: Core Print-Control Panels — Temperature + Move + Extrude

**Rationale:** Simpler panels that validate capability gating and the confirm-action policy against real hardware before the higher-complexity print-loop panels.

**Delivers:**
- **Temperature panel:** per-heater current/target/power, preheat presets, cooldown, keypad entry, history graph (ring buffer + throttled Canvas from `server.temperature_store` backfill); max_temp enforcement
- **Move panel:** X/Y/Z jog with distance presets, home axes, disable-motors-with-confirm, live position from `toolhead`/`motion_report`; gray out un-homed axes
- **Extrude panel:** extrude/retract, load/unload (macro-gated, shows "missing macro" popup), presets, tool select; block below `min_extrude_temp` from `can_extrude`
- Capability gating enforced across all three panels

**Avoids:** Pitfall 7 (timeouts + in-flight disabled state + debounce established here), Pitfall 8 (capability gating per panel), Pitfall 9 (temperature graph is first sustained high-rate render)

**Exit gate:** Preheat → wait for temp → extrude → jog all work on real Ender 5 Plus without the browser. No janky graph on Nexus 7.

**Research flag:** STANDARD — Moonraker API verified, patterns established.

---

### Phase 4: Files/Print + Job Status — The Core Value Loop

**Rationale:** Together these panels deliver the primary product value. They are the v1 exit gate. Files must exist before Job Status (metadata/thumbnail reuse).

**Delivers:**
- **Files / Print:** `server.files.get_directory` browser; lazy-load thumbnails + metadata as rows scroll into view (off-thread `inSampleSize`-downsampled Coil decode, bounded cache); confirm-before-start dialog with thumbnail; graceful fallback for thumbnail-less files
- **Job Status:** live progress bar, thumbnail, temps/speed/Z/time from `print_stats`/`virtual_sdcard`/`display_status`/`gcode_move`; state-driven button row (printing: pause/cancel; paused: resume/cancel; complete: restart/files); cancel must confirm
- Resync-after-reconnect validated for print state (Pitfall 3 complete path)

**Avoids:** Pitfall 3 (print state resync on reconnect), Pitfall 7 (lazy pagination for file browse), thumbnail OOM (aggressive downsampling)

**Exit gate:** Connect to Ender 5 Plus → browse files → start a print → watch Job Status for the full duration → pause, resume, cancel — all without the browser open. This is the v1 definition.

**Research flag:** STANDARD. Thumbnail URL resolution verified in API docs.

---

### Phase 5: Macros + Console

**Rationale:** The "escape hatches" that prevent users from needing SSH or a browser for anything unusual. Simpler than print-loop panels, depend on the event bus already built. Complete v1 scope.

**Delivers:**
- **Macros:** discover `gcode_macro *` from `printer.objects.list`; filter hidden macros; run via `printer.gcode.script`; basic param entry; "missing macro" popup
- **Console:** `server.gcode_store` backfill on connect/reconnect; live `notify_gcode_response`; severity coloring; cap scrollback ~1000 lines; system IME for input

**Avoids:** Pitfall 10 (scope discipline — this completes v1, nothing more)

**Exit gate:** Run a custom macro; type and send G-code from Console; confirm Console backfills correctly after a reconnect.

**Research flag:** STANDARD — straightforward application of existing event bus.

---

### Phase 6: Hardening — Always-On, Lifecycle, and Release

**Rationale:** The app is a wall-mounted appliance. Hardening for Doze, sustained operation, and sideload distribution separates "works in dev" from "works unattended for 14 hours on battery."

**Delivers:**
- Doze survival validation (20+ min unplugged screen-off → connection alive or cleanly resyncs)
- Screensaver / burn-in protection (dim overlay with wake-on-tap; does not stall reconnection)
- Process-death recovery (kill app mid-print → relaunch → Job Status restores from fresh query)
- Full "Looks Done But Isn't" checklist from PITFALLS.md (all 9 items)
- R8 minification + shrinking for release
- Signed release APK via `apksigner` + GitHub Actions CI; published as GitHub Release asset
- First-run setup checklist UX: disable battery optimization, disable Wi-Fi sleep when screen off

**Avoids:** Pitfall 5 (Doze hardening complete path)

**Exit gate:** Alive/resyncs after 20-minute unplugged screen-off. Signed APK installs cleanly on real Nexus 7.

**Research flag:** STANDARD — Doze/foreground-service patterns and APK signing are well-documented.

---

### Phase 7: Post-Core Expansion — Fine-Tune + Camera

**Rationale:** Fine-tune is nearly free given Job Status already exists; it's the highest-value-per-effort post-core feature. Camera (native MJPEG) is the most-requested post-core addition for a wall-mounted tablet.

**Delivers:**
- **Fine-tune panel:** babystep Z (`SET_GCODE_OFFSET MOVE=1`), speed % (`M220`), flow % (`M221`); live values from `gcode_move`
- **Camera (MJPEG):** `server.webcams.list` for URLs; OkHttp streaming → split multipart boundary → per-frame `BitmapFactory.decodeByteArray` with `inSampleSize` → Compose `Image`; bitmap reuse to avoid GC pressure

**Exit gate:** Fine-tune active during a first layer. MJPEG camera stream visible during a print.

**Research flag:** Fine-tune: STANDARD. Camera: NEEDS SHALLOW RESEARCH — validate the specific MJPEG framing against Matthew's actual camera setup (ustreamer/mjpg-streamer/crowsnest) before planning begins. Also verify fine-tune babystep persistence behavior (interaction with `SAVE_GCODE_STATE` / `[save_variables]`).

---

### Phase Ordering Rationale

- **Foundation before panels** is the hard dependency: every panel observes the `PrinterState` StateFlow; panels cannot be built until the state contract is frozen. This also front-loads the highest-risk work (transport reliability, toolkit validation on real hardware) when it's cheapest to course-correct.
- **Toolkit validation in Phase 1** prevents the most expensive rework scenario: discovering Compose janks on the Nexus 7 after all panels are built in Compose.
- **Temperature/Move/Extrude before Files/Job Status**: simpler panels validate the capability gating model and confirm-action policy before applying them to higher-complexity print-loop panels.
- **Files before Job Status** (within Phase 4): Job Status reuses thumbnail/metadata fetching from Files; they ship together but Files is implemented first.
- **Macros/Console after print-loop**: they're the escape hatches, not the core loop. Independent architecturally, but their value is only realized once the core loop works.
- **Hardening after functional completeness**: the "looks done but isn't" checklist requires a complete app to verify. Doze hardening also reveals edge cases easier to diagnose with a full feature set.
- **Fine-tune + Camera after validation**: fine-tune is nearly free and high-value; camera is the first named post-core expansion. Neither belongs in v1 scope but both ship fast after validation.

### Research Flags

**Needs shallow research during planning:**
- **Phase 7 (Camera):** Validate MJPEG framing against Matthew's actual camera setup before planning
- **Phase 7 (Fine-tune):** Verify babystep persistence behavior against Klipper docs

**Standard patterns — skip research phase:**
- Phase 1: Connection/WebSocket/JSON-RPC — fully verified against Moonraker official docs
- Phase 2: Foreground Service + DataStore + Doze exemption — well-documented Android patterns
- Phase 3: Temperature/Move/Extrude — Moonraker API verified, patterns established
- Phase 4: Files/Job Status — Moonraker file API and metadata structure verified
- Phase 5: Macros/Console — straightforward application of existing event bus
- Phase 6: Hardening/release — Doze, R8, APK signing all standard

---

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | AndroidX minSdk 23 baseline confirmed; Compose BOM/AGP version matrix confirmed; OkHttp/Retrofit/kotlinx.serialization all verified. Compose-vs-Views conflict is real and well-sourced from both sides. |
| Features | HIGH | Grounded in actual Moonraker external API docs (2026) and KlipperScreen Screen Catalog. Every API method cited was verified. |
| Architecture | HIGH | Moonraker protocol semantics (partial diffs, no replay, identify handshake) verified against official docs. Android foreground Service + `FLAG_KEEP_SCREEN_ON` + Doze exemption all verified. |
| Pitfalls | HIGH | OkHttp cleartext bug: verified against OkHttp issue tracker + Android cleartext docs. Baseline Profile no-op on L/M: verified against Android Compose performance docs. Doze network suspension: verified against Android Doze docs. |

**Overall confidence: HIGH**

The research is unusually well-grounded for a greenfield project. The Moonraker API is stable and well-documented. The Android patterns are established. The KlipperScreen catalog provides a complete reference inventory. The main genuine uncertainty (Compose perf on the Nexus 7) is identified, named, and has a concrete resolution protocol — only the device can answer it.

### Gaps to Address

- **Compose vs. Views on Nexus 7** — the only unresolved question that affects architecture. Resolution: on-device performance spike in Phase 1. No amount of additional research closes this; only the Nexus 7 can.
- **MJPEG framing specifics for Matthew's camera setup** — defer to Phase 7 planning; a quick read of the actual ustreamer/crowsnest HTTP response format will close it.
- **Fine-tune babystep persistence behavior** — defer to Phase 7 planning; check Klipper docs for `SAVE_GCODE_STATE` / `[save_variables]` interaction with `SET_GCODE_OFFSET`.
- **Auth edge cases** — the oneshot token websocket flow and `401 auth required` handling are documented in Moonraker but weren't deeply exercised. Address during Phase 1 implementation when auth headers are wired up.

---

## Sources

### Primary (HIGH confidence)

- Moonraker external API docs (2026) — `external_api/introduction/`, `external_api/jsonrpc_notifications/`, `external_api/printer/`, `external_api/file_manager/`, `external_api/server/`, `printer_objects/` — verified API methods, notify semantics, subscribe behavior, partial diff structure, thumbnail URL shape, `server.connection.identify`
- Android Developers — `develop/ui/compose/performance`, `develop/ui/compose/migrate/compare-metrics` — Compose/Views jank parity since 1.9; Baseline Profile effectiveness by API level (no-op on L/M confirmed)
- Android Developers — `jetpack/androidx/versions` + Lifecycle/Activity release notes — AndroidX minSdk 21→23 in June 2025
- Android Developers — `develop/background-work/background-tasks/awake/screen-on`, `.../awake` — `FLAG_KEEP_SCREEN_ON` activity-only, Doze network suspension, foreground service
- Android Developers — `privacy-and-security/risks/cleartext-communications`, `/security-config` — cleartext default by `targetSdk` level
- Android Developers — `training/monitoring-device-state/doze-standby` — Doze whitelist / battery-optimization exemption
- OkHttp issue #3325 — `NetworkSecurityPolicy` not honored on API 23
- Coil 3 changelog / upgrading_to_coil3 — minSdk 23 bump, API ≤23 OOM fix
- KlipperScreen GTK4 fork Screen Catalog (`gtk4_klipperscreen/docs/Screen_Catalog.md`) — routing model, capability-gating discipline, inconsistent confirmation gates, host-portability boundaries, panel inventory

### Secondary (MEDIUM confidence)

- Android developer blog (Dec 2025, Nov 2025, April 2026 Compose releases) — pausable composition default, Compose 1.11/1.12, compileSdk 37/AGP 9 for 1.12
- Compose BOM 2026.05.00 / Maven — Compose 1.11 version matrix
- Context7: `/square/okhttp`, `/coil-kt/coil`, `/websites/developer_android_develop_ui_compose` — library currency confirmation
- Vico 2.x — Compose+Views Canvas-based charting (alternative to custom Canvas for temperature graph)
- Medium: Eureka Engineering — Android unidirectional architecture with StateFlow/SharedFlow
- Medium: OkHttp + SharedFlow WebSocket in Compose — repository/VM/service layering

### Tertiary (LOW-MEDIUM confidence — reference only)

- `niqdev/ipcam-view`, `perthcpe23/android-mjpeg-view` — old Java MJPEG reference implementations; use as implementation reference for Phase 7, not as dependencies

---
*Research completed: 2026-05-29*
*Ready for roadmap: yes*
