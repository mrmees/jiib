# Phase 4: Service, Shell, Settings & Print-Status Home - Context

**Gathered:** 2026-05-31 (regenerated against `docs/ui_design/` — supersedes the prior partially-superseded draft)
**Status:** Ready for planning

<domain>
## Phase Boundary

Turn the headless, gate-proven Moonraker spine (Phase 2) into a **living app** on the Phase-3
design substrate. This phase delivers:

- A **foreground service** that owns the connection across Activity recreation (rotation) and
  screen-off, replacing the static `DevConfig`.
- A conventional **Settings screen** (keyboard allowed) that persists the connection
  (host/port/optional key) plus theme + text size, and becomes the live config source.
- **`klippy_state`-driven top-level routing**: splash during startup/error, **Print Status home**
  on ready, with content that adapts to whether a print is active.
- A **swipe-up full-screen App Drawer** for navigation (maximum canvas, no persistent status bar).
- The **Print Status home** — the monitor/landing surface — proving the Phase-3 render/throttle
  primitives in anger, with a **Stop → full-screen Confirm guard** control.
- The shared **command-dispatch primitive** (PRIM-05: timeouts + in-flight/busy + debounce) that
  wraps every Moonraker action call.

**Not in this phase:** print-cancel/pause/resume/tune (Phases 5 & 7), a separate Job Status panel
(Phase 7 deepens Print Status into it), real Power-device / host-power control, full custom-theme
token editor, always-on/Doze survival/boot-autostart (Phase 8).
</domain>

<decisions>
## Implementation Decisions

### Service, connection & lifecycle (CARRIED FORWARD from prior CONTEXT — still valid)
- **D-01 FGS type `specialUse`:** the connection-holding foreground service declares
  `foregroundServiceType="specialUse"` (with `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_SPECIAL_USE`
  perms + manifest `<property>` subtype). No background timeout (vs Android 15's `dataSync` 6h cap);
  Play-Store justification is moot (sideload). Not enforced on the API-30 test device but declare it
  for `targetSdk 35` / newer hardware. Rules out `dataSync` / `connectedDevice`.
- **D-02 Service ownership:** a **started** FGS (`startForegroundService` + `START_STICKY`) owns a
  `serviceScope` that constructs and runs the spine
  (`OkHttpClient → MoonrakerSocket → JsonRpcClient → PrinterStateStore(serviceScope) → MoonrakerSession`,
  then `session.run()`). StateFlows are held in a **process-scoped service-locator / `Application`-held
  singleton — NO Hilt for v1**. The Activity just collects the flows; **no service binding** needed for
  state observation. A persistent low-importance notification shows connection/printer status + taps back in.
- **D-03 Config change → recreate the session:** saving a new connection tears down the current
  `MoonrakerSession` run (cancel its job) and rebuilds it with the new config, then relaunches `run()`
  (host/key are construction-time inputs; `requestReconnectNow()` alone can't swap them). Must not leak
  the old socket/scope — the rebuild is the clean seam.
- **D-04 Persistence + connection model:** **DataStore (Preferences)**. Connection = host/IP, port
  (default `7125`), optional API key. **mDNS** via Android `NsdManager` is an *additive* scan
  (`_moonraker._tcp`) offered in Settings; **manual entry is the floor and is always present** (NSD on
  old Android is flaky / printer may be on another subnet — discovery never blocks connecting). API-key
  auth stays minimal (reuse the Phase-2 `MoonrakerAuth` path); no trusted-client UI beyond the key field.

### Top-level routing
- **D-05 Driven by `klippy_state` / `printState`, never socket `ConnectionState`.** Mechanism = a
  top-level `when(klippyState)` gate + a simple route/state holder for the active drawer destination.
  **Do NOT add Navigation-Compose** for v1 (handful of destinations; lean graph on 2GB). Design it so
  adding a Phase-5+ panel is a one-line destination addition.
- **D-06 Route map:** klippy non-ready (Startup/Error/Shutdown/Disconnected) → **hard override to
  splash** (no drawer escape while down). klippy Ready + **idle** → Print Status home (idle view).
  Ready + **printing** → Print Status home (printing view). **Print Status is the single home surface
  whose content adapts to `printState`** — there is NO separate Job Status screen in Phase 4 (Phase 7
  deepens this surface into the core print-loop gate).

### Print Status home (SHELL-04)
- **D-07 Gutter = Stop + greyed Tune & Pause placeholders.** Show all three tiles as in mockup `03`,
  but Tune (Phase 5) and Pause (Phase 7) are disabled "coming soon"; only **Stop** is wired.
- **D-08 Idle Focus repurposes to a live temp/status "Ready" readout** (a 0%/idle progress ring carries
  no info — fill the space usefully per the design philosophy). Printing Focus = the `ProgressRing`.
  The Focus is **state-adaptive** (printing → ring; idle → temp/status readout).
- **D-09 Home composition = Focus (ring | temp readout) + 2×3 numeric stat grid + a compact heater
  sparkline (`GraphView`).** Adding the sparkline proves the **Views render primitive in anger** in
  Phase 4 (not just the ring). ⚠ **PERF WATCH:** ring + sparkline + live numeric grid on one screen is
  new render surface for the Adreno 320 — measure against the Phase-3 two-part gate; the re-open
  conditions in `03-PERF-RESULTS.md` apply. The mockup `03` shows NO graph — this is a deliberate add.

### Stop control (SHELL-02)
- **D-10 Stop = ALWAYS `printer.emergency_stop`**, present and active whether idle or printing — the
  firmware panic halt. Routes through the full-screen `ConfirmGuard` (PRIM-03, already built) with
  **terse** copy (e.g. "Emergency stop?" / "This halts the printer."). Graceful **print-cancel is
  deferred to Phase 7**. Firing it drives `klippy_state → shutdown`, which routes to the splash recovery
  surface (D-12) — the user recovers via Firmware Restart there.

### Splash & first-run (SHELL-05, CONN-01)
> The splash is a **hard override with no reachable App Drawer**, so every escape hatch MUST live on the
> splash itself or the user is trapped.
- **D-11 First run / no saved config:** splash shows a friendly **"Set up your printer" connect prompt**
  → opens the Settings connection section; after save, the app connects.
- **D-12 Klippy shutdown/error (Moonraker reachable):** splash surfaces the `klippy_state` reason text +
  recovery actions **Retry + `printer.firmware_restart` + `printer.restart`** (restart the Klipper host).
- **D-13 Saved connection failing (printer off / wrong host):** splash offers **Retry + "Edit
  connection"** (→ Settings) — no dead end (retrying a typo'd host forever helps nobody).

### Shell / App Drawer (SHELL-01)
- **D-14 Navigation = swipe-up full-screen App Drawer** of square destination tiles; maximum content
  canvas, **no persistent title/status bar** (status = color on existing elements + surfaced inside the
  drawer). **Live tiles in Phase 4: Status (Print Status home) + Settings.** All other tiles
  (Move/Temp/Files/Tools/Macros/Devices) **AND the red Power tile** are greyed **"coming soon"** in
  Phase 4. Tapping a tile collapses the drawer to that destination.

### Settings screen (SET-01, PRIM-02)
- **D-15 Settings = conventional Android scrollable list** (NOT the Focus/Field/Gutter grammar — the one
  screen exempt by design), **token-themed**, **system keyboard allowed** (PRIM-02 confines all
  alphanumeric entry to Settings). Owns: connection (host/port/key + an mDNS **scan** button), theme,
  text size.
- **D-16 Theming in Phase 4 = dark/light toggle + S/M/L text size + a single accent-color picker**
  (writes a `TokenDelta` accent override via the existing `ThemePrefs`). The full multi-role-token
  custom editor is **deferred** (substrate already supports it; the editor UI is a later polish increment).
- **D-17 No feature-toggles section in Phase 4** — almost nothing exists to gate yet (panels, camera,
  always-on are later). Add the section when there are real toggles.

### Command-dispatch primitive (PRIM-05)
- **D-18 A single shared command-dispatch wrapper** enforces an explicit network timeout (no infinite
  hang on a dropped packet), an in-flight disabled/**busy** state, and **tap debounce** for **all**
  Moonraker action calls (Stop, the splash recovery actions, every future control). **NOT yet built —
  this is a Phase-4 deliverable.** Failures surface via `SeverityToast` (PRIM-04, already built).

### Claude's Discretion (research/planner territory — sensible defaults expected)
- Command-dispatch timeout value, busy-state visual (disable + spinner), and debounce window.
- Persistent low-importance notification content / channel / tap target.
- Exact sparkline placement within the Field grid and the idle temp-readout composition.
- Splash reason-text formatting derived from `klippy_state`.
- Confirm-guard exact wording (terse) for the E-stop.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### UI design system (LAW — supersedes any generated UI-SPEC)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables (outline-led controls, button
  intent = color, fill usable space, icons-never-twice).
- `docs/ui_design/LAYOUT.md` — the **Focus / Field / Gutter** grammar, one shared grid, sacred aspect
  ratios, ratio-only sizing, orientation rules. Drives Print Status, splash, confirm, drawer layout.
- `docs/ui_design/THEMING.md` — semantic role tokens, dark/light values, **button-intent colors**,
  `--fs` text-size step. Governs the accent picker (D-16) and all coloring.
- `docs/ui_design/reference/hifi.css` — canonical token + component source of truth (reproduce values
  in the Compose/Views stack; it is reference, not copy-verbatim code).
- `docs/ui_design/images/01-splash.png`, `02-app-drawer.png`, `03-print-status.png`,
  `08-confirm.png` — the Phase-4 hi-fi mockups (portrait + landscape). NOTE: `03` shows the **printing**
  state only; Phase 4 must also render the **idle** state (D-08) and a **sparkline** (D-09), neither of
  which appears in the mockup.
- `.planning/phases/04-service-shell-settings-print-status-home/04-UI-SPEC.md` — **DEAD pointer** to
  `docs/ui_design/`. Do not use as a spec; do not re-run `/gsd-ui-phase`.

### Toolkit & architecture
- `docs/adr/0001-ui-toolkit-decision.md` — **Hybrid toolkit is LAW.** Compose for shell + most panels;
  **classic Views (custom `Canvas`) for high-churn surfaces** — the heater sparkline / temp graph
  uses `GraphView` (Views), hosted in Compose via `AndroidView`.
- `.planning/phases/02-connection-state-foundation/02-04-SUMMARY.md` — how the spine
  (`MoonrakerSession`) is wired, and the **mock-looser-than-server** lesson (the live `identify` `url`
  bug green tests hid). Phase 4 wires this spine into a service for the first time.

### Requirements & scope (the WHAT)
- `.planning/ROADMAP.md` — Phase 4 section: goal + 5 success criteria.
- `.planning/REQUIREMENTS.md` — CONN-01, SET-01, SHELL-01..05, PRIM-02, PRIM-05.
- `.planning/PROJECT.md` — scope, Out of Scope (single-printer v1, no Play Services, camera deferred),
  constraint set (minSdk 23 / Adreno 320 / 2GB / armeabi-v7a).

### Reference only (NOT a UI to clone)
- `../gtk4_klipperscreen/docs/Screen_Catalog.md` — panel **inventory** reference (what panels exist /
  what each needs). This app is modern token-themed UI per `docs/ui_design/`, NOT a KlipperScreen port.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets — the spine (Phase 2, gate-proven on real Ender 5 Plus)
- `net/MoonrakerSession.kt` — reconnect supervisor; service launches `run()` in `serviceScope`, exposes
  `connectionState`, calls `requestReconnectNow()`. Constructed with
  `(store, rpc, socketEvents: (token?) -> Flow<SocketEvent>, auth, baseWsUrl, …)`.
- `state/PrinterStateStore.kt` — `printerState` / `capabilities` / `gcodeResponses` StateFlows;
  constructed with a `CoroutineScope`. The ~4 Hz high-rate conflation (the **throttle** half of the
  render/throttle primitive) is already built; `DEFAULT_SAMPLE_MS = 250`.
- `state/PrinterState.kt` — `klippyState` (Disconnected/Startup/Ready/Error/Shutdown) + `printState`
  (Standby/Printing/Paused/Complete/Error/Cancelled) are the **routing inputs** (D-05/D-06).
- `net/MoonrakerSocket.kt`, `auth/MoonrakerAuth.kt` — socket events flow + oneshot-token/`X-Api-Key` auth.
- `config/DevConfig.kt` — **to be replaced** as the config SOURCE by DataStore (D-04); keep the
  URL-shape helpers (`ws://host:port/websocket`, `http://host:port`) as a model.

### Reusable Assets — Phase-3 design substrate (the render half + primitives, all BUILT)
- `render/ProgressRing.kt` (Compose Canvas) — Print Status Focus (printing state, D-08).
- `render/GraphView.kt` + `render/GraphViewHost.kt` (Views + `AndroidView`) — the **heater sparkline**
  (D-09); implements `ThemeableView` so a theme flip recolors it.
- `render/RingBuffer.kt` — bounded rolling window (cap 120) feeding the sparkline.
- `designsystem/ConfirmGuard.kt` (PRIM-03) — full-screen confirm; the **Stop** control's mandatory gate.
- `designsystem/SeverityToast.kt` (PRIM-04) — command-dispatch failure feedback (D-18).
- `designsystem/control/OutlinedControl.kt` — Intent{Neutral/Accent/Warn/Danger/Go} 2px-outline controls
  (gutter buttons, drawer tiles, recovery actions).
- `designsystem/layout/ScreenScaffold.kt` — slot-based Focus/Field/Gutter primitive (Print Status,
  splash). NOTE: Settings is exempt (D-15) — conventional Android list, not this scaffold.
- `designsystem/ScrubberPage.kt` (PRIM-01) — keyboard-free numeric page (not central to Phase 4; later panels).
- `theme/*` — `DinghyTheme` (pins `LocalDensity(fontScale=1f)`, the single `--fs` authority),
  `LocalTokens`, `ThemeResolver` (single `StateFlow<ThemeTokens>`), `ThemePrefs` (DataStore;
  `TokenDelta` sparse override — the **accent picker** writes here, D-16), `BakedTokens`, `Geist`.

### Integration Points (first-time wiring — none of this exists yet)
- **No `Application` class, no service-locator/DI container, no foreground service, no Settings screen.**
  `MainActivity` is the scaffold placeholder. Phase 4 introduces ALL the app wiring for the first time.
- **PRIM-05 command-dispatch wrapper does not exist** — Phase 4 builds it (D-18).
- Cleartext posture (ws://+http://) is owned by the shared `AndroidManifest.xml` +
  `res/xml/network_security_config.xml` (Phase 1) — do NOT duplicate; add only new permissions
  (FGS + `FOREGROUND_SERVICE_SPECIAL_USE` + multicast lock if NSD needs it).

### Constraints
- armeabi-v7a-only release; Compose UI resolves to 1.11.1; **minSdk-23 floor enforced** by the
  `verifyMinSdk` task — every new dep must hold the floor. DataStore 1.1.x is already in the catalog
  (added Phase 3 for ThemePrefs); confirm whether connection prefs share that store or get their own.
</code_context>

<specifics>
## Specific Ideas

- **Moonraker conventions:** default port `7125`; REST `http://host:port`; WS `ws://host:port/websocket`.
  E-stop = `printer.emergency_stop`; firmware restart = `printer.firmware_restart`; host restart =
  `printer.restart` (confirm exact JSON-RPC method names/paths against the live spine during planning).
- `server.connection.identify` REQUIRES a non-empty `url` arg (live-verified; the spine already sends
  `https://mees.works/dinghy-display`). Do not regress this.
- **Accent** = cool signature blue (`--accent`); **heat** = amber; **go/stop** = green/red — per
  THEMING.md button-intent semantics. The accent picker (D-16) overrides `--accent` only.
- Mockup fidelity is HIGH for splash/drawer/print-status/confirm — but Phase 4 deliberately adds the
  idle state (D-08) and the heater sparkline (D-09) that the mockups don't show.
</specifics>

<deferred>
## Deferred Ideas

- **Print-cancel / pause / resume / restart-print / Tune** — Phases 5 (Temperature/Move/Extrude) & 7
  (Job Status). The Phase-4 gutter shows Tune/Pause as greyed placeholders only.
- **Full multi-role-token custom-theme editor** — later polish; Phase 4 ships the accent picker only.
- **Red Power tile real behavior** (host power menu via `machine.shutdown`/`machine.reboot`, and/or
  Moonraker power-device control) — greyed "coming soon" in Phase 4; a future phase wires it.
- **"Devices" power-device panel** — future panel.
- **Full multi-trace temperature history graph** — Phase 5 Temperature extends `GraphView`; the Phase-4
  home sparkline is the lighter in-anger proof.
- **mDNS as the *primary* connection path / multi-printer pick-list** — additive in v1; multi-printer is
  v2 (PROJECT.md Out of Scope).
- **Boot-autostart + full Doze/battery-exemption + `FLAG_KEEP_SCREEN_ON` + burn-in screensaver** —
  Phase 8 (Hardening & Release). Phase 4 starts the service on app launch only.
- **Navigation-Compose adoption** — revisit when the destination count outgrows the v1 state-holder.
- **Trusted-client / richer auth UI** — beyond the optional API-key field.

### Open questions for research/planning
- NSD/mDNS reliability on the target + whether a multicast lock (`CHANGE_WIFI_MULTICAST_STATE`) is needed.
- `specialUse` manifest `<property>` subtype tag + exact permission set for `targetSdk 35` (no-op
  enforcement on the API-30 device, but must be declared correctly).
- Connection prefs: share the existing DataStore (ThemePrefs) or a separate prefs surface.
- Exact Moonraker JSON-RPC methods for `emergency_stop` / `firmware_restart` / `restart` — confirm live.
</deferred>

---

*Phase: 4-service-shell-settings-print-status-home*
*Context gathered: 2026-05-31*
