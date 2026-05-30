# Phase 3: Service, Shell & State-Driven Navigation - Implementation Context

<domain>
Turn the headless connection/state spine (Phase 2) into a living app: a foreground service that
owns the Moonraker connection across Activity recreation and screen-off, a user-facing connection
config (host/port/optional key) persisted to DataStore that replaces the static `DevConfig`,
`klippy_state`-driven top-level routing (splash/main/job), a persistent modern shell with an
always-reachable Emergency Stop, and the reusable UI primitives + command-dispatch policy every
later panel inherits.
</domain>

<canonical_refs>
**Source docs the executor MUST read before building.** Paths relative to repo root
(`/mnt/e/claude/personal/github/dinghy-display`).

- `docs/adr/0001-ui-toolkit-decision.md` — **Hybrid toolkit is LAW.** Compose for the shell and most
  panels; **classic Views (custom `Canvas`/RecyclerView) for the high-churn surfaces** — the temperature
  graph, Files list, Console scrollback. The Phase-3 dashboard "shared render/throttle primitive" is the
  **Views-based** graph surface, because that's the one the Temperature panel extends in Phase 4.
- `.planning/ROADMAP.md` (Phase 3 section, lines ~95–110) — the five locked success criteria (CONN-01,
  SHELL-01..05, PRIM-01..05). These are the WHAT; this doc is the HOW.
- `.planning/PROJECT.md` — scope, **Out of Scope** (single-printer v1, landscape-first, no Play Services,
  camera deferred), and the constraint set (minSdk 23 / Adreno 320 / 2GB / armeabi-v7a).
- `../gtk4_klipperscreen/docs/Screen_Catalog.md` — **panel INVENTORY reference ONLY.** Use it to know
  *what panels exist and what each needs*, NOT as a UI to clone. See the **Shell** decision: this app is
  modern Material 3, not a KlipperScreen port.
- `.planning/phases/02-connection-state-foundation/02-04-SUMMARY.md` — how the spine (`MoonrakerSession`)
  is wired and the **mock-looser-than-server** lesson (the live `identify` `url` bug that green tests hid).
  Phase 3 wires this spine into a service for the first time, so re-read the integration shape.
</canonical_refs>

<decisions>

**FGS type — `specialUse`:** The connection-holding foreground service declares
`foregroundServiceType="specialUse"` (with `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`
permissions and the manifest `<property>` subtype tag).
- Rationale: honest fit for "maintain a persistent LAN session to a 3D printer," **no background
  timeout** (unlike Android 15's 6h `dataSync` cap), and the Play-Store justification that `specialUse`
  normally demands is moot — we sideload. Future-proof if the APK ever runs on a newer device.
- Constraints: on the API-30 test device the type-permission isn't enforced, but declare it anyway for
  `targetSdk 35` and newer hardware. Rules out `dataSync` and `connectedDevice`.

**Connection UX — manual entry PLUS mDNS auto-discovery:** First run with no saved config routes to a
connection screen offering (a) a scan for `_moonraker._tcp` services via Android `NsdManager` with a
pick-list, and (b) always-available manual host/IP + port (default `7125`) + optional API key.
- Rationale: nicer modern first-run, but discovery is best-effort.
- Constraints: **manual entry is the floor and is always present** — NSD on old Android is flaky and the
  printer may be on a different subnet, so discovery never blocks connecting. Persist via **DataStore
  (Preferences)**; this replaces `config/DevConfig` as the live config source. API-key auth stays minimal
  (the Phase-2 `MoonrakerAuth` path already exists); no trusted-client UI beyond the key field.

**Shell — minimal chrome, maximum canvas, edge-swipe full-screen app drawer (REVISED in `/gsd-ui-phase 3` — supersedes the earlier persistent-nav-rail decision):** NO persistent navigation rail and NO persistent top app-bar. The content surface is full-bleed (maximum open canvas). Navigation is an **edge-swipe-opened, full-screen app drawer** of big function tiles (Home/Dashboard + Job Status enabled; Temperature/Move/Files/Console rendered greyed "coming soon"); a **thin persistent edge handle** at the screen edge provides discoverability. Tapping a tile collapses the drawer to that panel.
- Rationale: Matthew's explicit steer, revising the original rail call — *"remove the sidebar in favor of an expandable, full-screen app drawer… goal is usable space and clean interface."* On a 7″ panel, maximum content canvas + on-demand navigation beats always-visible chrome. Still modern Material 3, NOT a KlipperScreen port. This consciously trades the rail's one-tap panel switching for a cleaner canvas — accepted, and we iterate designs from a minimal baseline.
- Constraints: landscape-first (locked in PROJECT.md). **No information is persistent on the content canvas** for now — connection/printer status is surfaced inside the app drawer and contextually, NOT in an always-on bar (Matthew: "start with maximum open canvas… we iterate designs"). Accent color is **cyan `#5BC8FF`** (chosen to stay clear of the amber=heating / green=at-temp / red=stop status semantics). The single bit of persistent chrome is the thin edge handle.

**Navigation mechanism — state-holder routing, NO Navigation-Compose dep (v1):** A top-level
`when(klippyState)` gate selects splash vs. in-app shell; inside the shell a simple route/state holder
selects the active app-drawer destination. Do **not** add `androidx.navigation:navigation-compose` for v1.
- Rationale: the top-level route is *reactive to `klippy_state`*, which fits a `when` wrapper far better
  than a back-stack model; v1 has a handful of destinations; keeps the dependency graph lean on 2GB
  hardware. (Matches the CLAUDE.md guidance: adopt Nav-Compose only when the panel count grows.)
- Constraints: this is the project's first navigation structure — design it so adding Phase-4+ panels is
  a one-line destination addition.

**Top-level routing semantics:**
- `klippy_state` non-ready (Startup/Error/Shutdown/Disconnected) → **hard override to splash**, which
  surfaces the shutdown/error reason text + recovery actions (retry, firmware/Klipper restart where
  exposed). The user cannot escape splash into panels while Klippy is down.
- `klippy_state` Ready + **print active** → app **lands** on Job Status but the user can **freely
  navigate** to Temperature/Move/etc. to tune or jog mid-print. Returning "home" shows Job Status.
  NOT a hard lock — don't trap the user mid-print.
- Routing is driven by `PrinterState.klippyState` / `PrinterState.printState` (already first-class fields),
  never by socket `ConnectionState`.

**Service ownership & lifecycle:** A **started** foreground service (`startForegroundService` +
`START_STICKY`) owns a `serviceScope` that constructs the spine (`OkHttpClient → MoonrakerSocket →
JsonRpcClient → PrinterStateStore(serviceScope) → MoonrakerSession`) and launches `session.run()`.
The Activity observes the StateFlows through a process-held container (manual service-locator /
`Application`-held singletons — **no Hilt** for v1); no service binding is required for state observation.
- Rationale: the connection must survive Activity recreation (rotation) and screen-off; a started FGS is
  the right tool; the spine's StateFlows are process-scoped so the UI just collects them.
- Constraints: **boot-autostart and full Doze/always-on survival are Phase 8**, not here — Phase 3 starts
  the service on app launch. A persistent low-importance notification shows connection/printer status and
  taps back into the app.

**Config-change handling — recreate the session:** Saving a new connection (host/port/key) tears down
the current `MoonrakerSession` run (cancel its job) and rebuilds it with the new config, then relaunches
`run()`. (Host/key are construction-time inputs to the session/auth; `requestReconnectNow()` alone can't
swap them.)
- Constraints: must not leak the old socket/scope; the rebuild is the clean seam.

**Shared render/throttle primitive — Views graph at the state cadence:** The state-layer conflation
(~4 Hz, `PrinterStateStore.DEFAULT_SAMPLE_MS = 250`) already exists. Phase 3 establishes the **view-side**
render primitive as a **classic-Views custom-`Canvas` graph** (per ADR 0001), shown on the dashboard as a
compact heater sparkline/value, hosted in Compose via `AndroidView`. Phase 4's Temperature panel EXTENDS
this exact primitive into the full history graph.
- Constraints: graph architecture is settled HERE, not discovered late (SHELL-03). Keep view-models
  toolkit-agnostic (StateFlow in, both Compose and the Views graph consume it).

**Command-dispatch primitive (PRIM-05) + confirm policy (PRIM-03):** A single shared command-dispatch
wrapper enforces an explicit network timeout (no infinite hang on a dropped packet), an in-flight
disabled/busy state, and tap debounce for **all** Moonraker action calls. A single confirm-dialog
primitive is the mandatory gate for the destructive set: **emergency stop, cancel print, disable motors,
restart print, cooldown-while-printing**. E-stop is fast-but-deliberate (**hold-to-confirm**, not a slow
modal) and — per the revised minimal-chrome shell — lives **only on the Home/Dashboard surface for now**
(NOT global chrome on every screen); additional surfaces gain it as later control panels are built. Text
entry uses the **system IME**; numeric entry uses the **custom big-key keypad** (PRIM-01). Keypad /
on-screen-keyboard / severity-styled toast primitives are built here as panel-consumable components.

</decisions>

<deferred>
- **mDNS being the *primary* connection path / multi-printer pick-list** — discovery is additive in v1;
  multi-printer switching is explicitly v2 (PROJECT.md Out of Scope).
- **Boot-autostart + full Doze/battery-optimization-exemption + `FLAG_KEEP_SCREEN_ON` + burn-in
  screensaver** — all Phase 8 (Hardening & Release).
- **Navigation-Compose adoption** — revisit when the destination count outgrows the v1 state-holder.
- **Trusted-client / richer auth UI** — beyond the optional API-key field, deferred.
</deferred>

<specifics>
- Modern feel is a stated requirement, not a nice-to-have (Matthew). Material 3, navigation rail,
  dark high-contrast surface (theme is already `Theme.AppCompat.DayNight.NoActionBar` to support the
  Views hybrid).
- Moonraker conventions: default port `7125`; REST `http://host:port`; WS `ws://host:port/websocket`
  (shapes already in `DevConfig`). E-stop = `printer.emergency_stop`.
- `server.connection.identify` REQUIRES a non-empty `url` arg (live-verified; the spine already sends
  `https://mees.works/dinghy-display`).
</specifics>

<open_questions>
- **NSD/mDNS on API 23+:** reliability and whether a multicast lock (`CHANGE_WIFI_MULTICAST_STATE`) is
  needed; resolve during planning. Manual entry is the guaranteed fallback regardless.
- **`specialUse` manifest plumbing:** confirm the exact `<property>` subtype tag + permission set for
  `targetSdk 35` (no-op enforcement on the API-30 device, but must be declared correctly).
- **DataStore dependency:** add `androidx.datastore:datastore-preferences` (minSdk-23-safe) to
  `gradle/libs.versions.toml` — it is NOT currently in the catalog.
- Persistent-notification content/channel specifics (status text, tap target) — settle in planning.
</open_questions>

<code_context>
- **Reusable:** `MoonrakerSession` at `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt` —
  the reconnect supervisor; service launches `run()` in its scope, exposes `connectionState`, and calls
  `requestReconnectNow()`. Constructed with `(store, rpc, socketEvents: (token: String?) -> Flow<SocketEvent>,
  auth, baseWsUrl, …)`. The `socketEvents` lambda builds a `MoonrakerSocket` per attempt and returns its
  `.events()` flow; for the keyed path it folds the oneshot token into the URL via
  `auth.buildAuthedWsUrl(baseWsUrl, token)`.
- **Reusable:** `PrinterStateStore` at `.../state/PrinterStateStore.kt` — `printerState` / `capabilities`
  / `gcodeResponses` StateFlows; constructed with a `CoroutineScope`. The ~4 Hz high-rate conflation +
  immediate control-plane split is ALREADY built (the throttle half of the "shared render/throttle"
  primitive lives here; Phase 3 adds the render half).
- **Reusable:** `PrinterState` at `.../state/PrinterState.kt` — `klippyState` (Disconnected/Startup/
  Ready/Error/Shutdown) and `printState` (Standby/Printing/Paused/Complete/Error/Cancelled) are the
  routing inputs; `ConnectionState` is the 5-state socket lifecycle (Connecting/Syncing/Connected/
  Disconnected/Error) for the status chrome.
- **Reusable:** `MoonrakerSocket` at `.../net/MoonrakerSocket.kt` — `events(): Flow<SocketEvent>` (Open/
  Frame/Closed), built via the `MoonrakerSocket.real(client, wsUrl)` companion (URL baked into the
  `Request` at construction) over a shared `OkHttpClient` (`readTimeout(0)`). Its `wsUrl` defaults to
  `DevConfig.wsUrl` today — Phase 3 supplies the DataStore-sourced URL instead.
- **Reusable:** `MoonrakerAuth` at `.../auth/MoonrakerAuth.kt` — oneshot-token + `X-Api-Key` when keyed;
  engages only when an API key is configured.
- **Replace:** `config/DevConfig` (static, BuildConfig-backed) — Phase 3 swaps the config SOURCE to
  DataStore; keep the URL-shape helpers as a model.
- **Pattern:** cleartext posture (ws://+http://) is owned by the shared `AndroidManifest.xml` +
  `res/xml/network_security_config.xml` (Phase 1). Do NOT duplicate it; add only new permissions
  (FGS + multicast if needed).
- **Integration:** there is NO Application class, DI container, or service yet, and `MainActivity` is the
  scaffold placeholder. Phase 3 introduces all the app wiring for the first time.
- **Constraint:** armeabi-v7a-only release; Compose UI resolves to 1.11.1; minSdk-23 floor enforced by
  the `verifyMinSdk` task — every new dep must hold the floor.
</code_context>

<success_criteria>
1. User can enter/discover and save a Moonraker connection that persists across restarts (DataStore) and
   the app connects with it; the foreground service keeps the connection alive across rotation and
   screen-off (replacing `DevConfig`).
2. Splash during Klippy startup; routes to the main shell on Klippy ready; lands on Job Status when a
   print is active (but navigation stays open); splash shows shutdown/error reason + recovery actions —
   all driven by `klippy_state`, never socket state.
3. Home/Dashboard is a compact thermal dashboard proving the Views-based render/throttle graph primitive,
   smooth at ~2–4 Hz on the Nexus 7; navigation to other functions is via an edge-swipe full-screen app
   drawer (thin edge handle for discoverability), not a persistent rail — maximum content canvas.
4. Emergency Stop (`printer.emergency_stop`) is present on the Home/Dashboard surface, hold-to-confirm;
   the app has minimal/no persistent chrome — connection/printer status is surfaced in the app drawer and
   contextually, not an always-visible status bar.
5. The shared confirm dialog gates the destructive set; the shared command-dispatch primitive enforces
   timeout + in-flight/busy + debounce for all action calls; keypad, keyboard, and severity toast
   primitives exist and are panel-consumable.
</success_criteria>
