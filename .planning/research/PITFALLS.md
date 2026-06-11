# Pitfalls Research
**Status:** historical (research snapshot; see CLAUDE.md for current stack)

**Domain:** Native Android Moonraker/Klipper printer-control client on old hardware (minSdk 23, Nexus 7 2013), sideloaded APK, always-on wall-mounted, single printer v1
**Researched:** 2026-05-29
**Confidence:** HIGH on the verified technical traps (OkHttp cleartext bug, Baseline Profiles unavailable on M, Doze suspends network + ignores wake locks, Moonraker subscribe/identify flow); MEDIUM on the UX/scope judgments drawn from the Screen Catalog.

This file is deliberately specific to *this* domain. Generic "use coroutines / don't block the main thread" advice is omitted except where the old-hardware or Moonraker context changes the stakes. Phases referenced are the v1 functional-core slices implied by PROJECT.md: **P0 Foundation/Connection**, **P1 Shell + Dashboard**, **P2 Print-control panels (Move/Temp/Extrude)**, **P3 Files/Job Status**, **P4 Macros/Console**, **P5 Hardening (always-on, lifecycle, capability gating polish)**.

---

## Critical Pitfalls

### Pitfall 1: Assuming cleartext-blocking works on API 23 (the OkHttp Marshmallow cleartext bug)

**What goes wrong:**
The whole product is "point it at a Moonraker on the LAN." Moonraker on the local network is almost always plain `http://`/`ws://` (no TLS). On Android 6 (API 23) the platform's `NetworkSecurityPolicy.isCleartextTrafficPermitted(host)` overload that OkHttp tries to call **does not exist until API 24**, so OkHttp's `AndroidPlatform` throws and falls back to "always permit cleartext." The inverse trap is the one that actually bites: if you later raise `targetSdk` to 28+, cleartext is blocked **by default**, and your LAN connection silently dies with an obscure `CLEARTEXT communication ... not permitted` error that you will not reproduce on a modern test device pointed at an HTTPS endpoint.

**Why it happens:**
Developers test against a Moonraker reverse-proxied behind HTTPS, or on a newer Android device, and never exercise the bare-`http`-to-`192.168.x.x` path on actual Android 6. The cleartext default flipped across API levels (permitted ≤27, blocked by default 28+), so behavior depends on `targetSdk`, not `minSdk`.

**How to avoid:**
- Ship a `res/xml/network_security_config.xml` that **explicitly permits cleartext** for the local-network case, and reference it from the manifest. Do not rely on `android:usesCleartextTraffic="true"` alone — use the config so you can scope it.
- Because the user types an arbitrary `host:port`, you cannot enumerate domains. Use `<base-config cleartextTrafficPermitted="true">` (whole-app cleartext permitted) for v1, and document it as a deliberate LAN-trust decision. Optionally allow `https://` when the user opts in.
- Pin `targetSdk` decisions early and test cleartext on a real API 23 device *and* whatever `targetSdk` you ship — the bug surface is the `min`/`target` combination, not either alone.

**Warning signs:**
Connection works in the emulator/new phone, fails on the Nexus 7. `CLEARTEXT not permitted` in logcat. Works for one tester (HTTPS proxy) but not another (bare Moonraker).

**Phase to address:** **P0** — this is connection-layer foundation. A working `ws://192.168.x.x` round-trip on a real API 23 device is the P0 exit gate.

---

### Pitfall 2: Banking on Baseline Profiles / Compose perf tooling that doesn't exist on Android 6

**What goes wrong:**
Modern Compose performance guidance ("apply a Baseline Profile, jank drops, startup improves 30%") **does not apply to Android 6/7 (L and M)**. On those versions the app is compiled AOT by the framework at install time, so Compose baseline profiles are a no-op. You design assuming you can profile your way out of Compose overhead, then discover the single biggest lever is unavailable on your *only* target hardware.

**Why it happens:**
All current Compose perf docs are written for API 24+/R8 baseline-profile pipelines. The "Compose now matches Views for jank (since 1.9)" benchmarks are run on modern hardware with profiles installed — neither condition holds on a Tegra-era Nexus 7.

**How to avoid:**
- Settle the toolkit decision in research with the Nexus 7 as the benchmark device, not a modern phone. (STACK.md should make this call; this pitfall is why it matters.) If Compose is chosen, validate scrolling the Files list and the live dashboard on real hardware *before* committing the architecture.
- Treat classic Views / `RecyclerView` as the conservative default for the high-churn surfaces (Files list, Console log, temperature dashboard) even if the rest is Compose. A hybrid is acceptable.
- Avoid deep composable nesting and heavy `Modifier` chains on the always-visible shell; these cost the same every frame and you have no AOT profile to amortize them.

**Warning signs:**
Smooth in the emulator, visibly janky list scrolling on the Nexus 7. Frame times >16ms in `gfxinfo`/`dumpsys` on device. "It's fine on my Pixel."

**Phase to address:** **P0/P1** — toolkit choice is foundational and the dashboard is the first real render-load test. Do not defer the "is the toolkit fast enough on the actual tablet" question to P5.

---

### Pitfall 3: Websocket reconnection storms and lost `notify_*` events across drops

**What goes wrong:**
On flaky LAN/Wi-Fi (and especially after Doze, see Pitfall 5), the websocket drops. Naive reconnect loops hammer Moonraker the instant a socket closes, producing a reconnect storm when Klipper/Moonraker is itself restarting (firmware restart, host reboot, Klipper shutdown). Worse: while disconnected you **miss every `notify_status_update` / `notify_gcode_response` / `notify_klippy_*` event**. Moonraker push notifications are fire-and-forget — there is no replay. If you reconnect and just resume listening, your UI shows stale temps, a stale print state, a missing pause/cancel that happened while you were dark, and a Console missing lines.

**Why it happens:**
Developers treat the websocket like a chat connection: reconnect and keep going. But Moonraker state is *push-only after subscribe*; the source of truth on reconnect is a fresh `printer.objects.query` + re-`subscribe`, plus `server.gcode_store` for missed console lines. Skipping the resync leaves the UI lying about a running print.

**How to avoid:**
- Reconnect with **exponential backoff + jitter** and a cap (e.g. 0.5s → 30s). Never tight-loop.
- On every (re)connect, run the full handshake in order: `server.connection.identify` → check `server.info`/Klippy state → `printer.objects.query` (full snapshot) → `printer.objects.subscribe` (resume push). Treat the query result as the new source of truth; do not trust pre-drop cached state.
- After reconnect, pull `server.gcode_store` to backfill Console history so it isn't silently missing lines.
- Distinguish "socket closed" from "Klippy not ready" — a connected socket can still report Klippy in `startup`/`error`/`shutdown`. Don't reconnect-storm a healthy socket just because the printer object query failed.

**Warning signs:**
Temps frozen after a Wi-Fi blip; UI says "printing" after a print finished while disconnected; Moonraker logs show rapid connect/disconnect; Console gaps after wake. Reconnect interval that never grows.

**Phase to address:** **P0** for the backoff + handshake/resync skeleton (this is the resilient-connection requirement). **P3/P5** for correctly resyncing print state into Job Status.

---

### Pitfall 4: Modeling the printer as "connected = ready" and ignoring Klippy lifecycle states

**What goes wrong:**
The websocket being open says nothing about whether Klipper can take commands. Klippy moves through `startup` → `ready`, and can drop to `shutdown` or `error` (thermal runaway, MCU error, emergency stop). Apps that key UI off "socket connected" will show live jog/extrude/print controls while Klipper is in `shutdown`, send commands that error or get rejected, and present a broken Job Status during a `startup`. Partial/missing printer objects during startup also cause null-deref crashes if you assume every object is always present.

**Why it happens:**
The Screen Catalog encodes this correctly (ready→`main_menu`, active print→`job_status`, startup/error/shutdown→`splash_screen`) but it's easy to skip when porting to a fresh app — you wire up the happy path and never simulate a Klipper shutdown. Moonraker also returns only the objects that exist/are subscribed, so "extruder1" or "filament_switch_sensor" simply won't be in the payload on some printers.

**How to avoid:**
- Make Klippy state a **first-class top-level UI router**, mirroring KlipperScreen's splash/main/job model. Gate *all* command-sending controls behind `klippy_state == ready`.
- Surface `shutdown`/`error` with the actual error text and the recovery actions (firmware restart / restart Klipper / restart host), exactly the splash-screen recovery pattern.
- Treat every printer object as optional. Parse defensively; never assume `heater_bed`, a second extruder, or a filament sensor exists. This is the same discipline as capability gating (Pitfall 8).

**Warning signs:**
Crash or no-op when you `M112`/E-stop and then try to jog; Job Status renders garbage during boot; NPE on printers without a heated bed or with a single extruder; controls active during a thermal-runaway shutdown.

**Phase to address:** **P1** (state router as part of the shell + dashboard) so every later panel inherits correct gating; reinforced in **P3** (Job Status state-driven button rows).

---

### Pitfall 5: Doze, wake locks, and Wi-Fi sleep silently killing the always-on connection

**What goes wrong:**
The product is "always-on wall-mounted screen." But in Doze the system **suspends network access and ignores wake locks** — exactly what kills a long-lived monitoring websocket. On a wall tablet the screen may be off (or you blanked it for burn-in), the process gets backgrounded, Wi-Fi may be set to sleep when the screen is off, and your connection dies. The user walks up expecting live print status and gets a dead/disconnected screen, or worse, a process that was killed and lost all print-monitoring state.

**Why it happens:**
Developers test with the app foregrounded and the screen on (charging, plugged in). Doze and App Standby only kick in after the device is unplugged/idle/screen-off — easy to never hit during dev. On Android 6 specifically, Doze is the *first* version with this behavior and is aggressive.

**How to avoid:**
- This is a kiosk/appliance, not a Play-Store app — lean into that. Keep the print-monitoring surface in the **foreground with the screen on** using `FLAG_KEEP_SCREEN_ON` (per-window flag, not a wake lock) while connected/printing.
- Request **battery-optimization exemption** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) so the app can hold network + wake locks in Doze. This is legitimate for a wall-mounted appliance and acceptable for sideloaded distribution (no Play policy review).
- Document a setup step: disable "Wi-Fi off when screen sleeps" / battery optimization for the app. A first-run checklist beats a mysteriously-dead screen.
- For genuine screen-off operation, prefer a foreground Service holding the connection over relying on the Activity surviving.

**Warning signs:**
Connection alive while you're using it, dead after the tablet sits untouched for 10+ minutes unplugged. Print state lost after coming back. Works when charging, dies on battery (Doze only runs when unplugged on older versions).

**Phase to address:** **P5 (always-on hardening)** for the exemption + foreground-service work, but the *connection layer in P0 must be written to survive and resync after a long sleep* (ties to Pitfall 3) — don't architect P0 assuming the socket lives forever.

---

### Pitfall 6: Inconsistent / missing confirmation gates on destructive printer actions

**What goes wrong:**
This domain has real-world consequences: cancelling a 14-hour print, disabling motors mid-print, cutting power to the printer, or firing an emergency stop. The Screen Catalog explicitly flags that KlipperScreen itself is **inconsistent** here — Cancel, Disable Motors, Object Exclusion, and Shutdown are confirmed, but **Power and Pins fire immediately with no confirmation**. If you port that inconsistency (or worse, drop confirmations to "simplify"), a fat-fingered tap on a 7" resistive-ish touchscreen kills a print or de-energizes a heater unexpectedly.

**Why it happens:**
Confirmations get added ad-hoc per panel by whoever built it, so the policy is accidental rather than designed. On a wall-mounted touchscreen near a running machine, accidental taps are *more* likely than on a phone in hand.

**How to avoid:**
- Define a **single destructive-action policy** up front and route every high-impact action through one confirm-dialog primitive (PROJECT.md already lists a reusable confirm-action dialog — make it mandatory for the destructive set). Destructive set for v1: Emergency Stop, Cancel Print, Restart Print, Disable Motors, Power on/off, Cooldown-while-printing.
- Keep **Emergency Stop always reachable and itself fast** — it's the one action where a confirmation tradeoff matters (KlipperScreen makes E-stop confirmation *configurable*; default to a quick double-tap or hold rather than a slow modal, since E-stop is a safety action you want to succeed *fast*).
- Decide deliberately for Power/Pins rather than inheriting "no confirmation."

**Warning signs:**
Any high-impact button that calls Moonraker directly in its click handler with no dialog. QA "I accidentally cancelled my print." Different panels using different confirmation styles.

**Phase to address:** **P1** (define the confirm primitive + policy in the shell) so P2/P3/P4 panels consume it consistently. Verify in each panel-building phase.

---

### Pitfall 7: Blocking the UI on slow/failed network calls (no timeouts, no optimistic state)

**What goes wrong:**
Every control action and file/thumbnail fetch is a network round-trip to a Pi over Wi-Fi. If the UI blocks (or appears frozen) while a call is in flight — start-print, fetch directory with thumbnails, query metadata — the screen feels dead. Worse, with no timeout a dropped Wi-Fi packet hangs the action forever and the user mashes the button, queueing duplicate commands (double "start print," double "cancel").

**Why it happens:**
Same as Pitfall 3's optimism: developers assume the LAN is instant and reliable. It usually is — until Doze, channel congestion, or a busy Pi makes a call take 5–10s.

**How to avoid:**
- All Moonraker calls get explicit connect/read timeouts (short for control actions, longer for file listings/thumbnails).
- Show immediate **in-flight / disabled state** on the tapped control and **debounce** so a second tap can't re-fire a destructive command.
- Prefer optimistic-with-reconcile for cheap state (target temp set) and confirmed-via-event for expensive state (print started → wait for `notify_status_update` print_stats to flip to `printing`, then update Job Status — don't assume success).

**Warning signs:**
Spinner-less freezes during file browse; duplicate G-code in the Console from double taps; "Start Print" that does nothing visible for seconds. No timeout configured on the HTTP/WS client.

**Phase to address:** **P2** (first control panels establish the in-flight/debounce pattern), reinforced in **P3** (file browse / start print).

---

### Pitfall 8: Showing capability-gated controls the connected printer doesn't support

**What goes wrong:**
Printers differ wildly: no second extruder, no filament sensor, no firmware retraction, no `LOAD_FILAMENT` macro, no bed mesh, no power devices. If panels render every control unconditionally, users get buttons that error, no-op, or send commands to nonexistent objects. The Screen Catalog calls capability gating out repeatedly as a core design principle, and PROJECT.md lists it as a v1 requirement — but it's easy to ship a panel "done" against your own printer and break on someone else's.

**Why it happens:**
You develop against your two printers (Ender 5 Plus, Ender 3 Pro) and they happen to have/not-have a fixed capability set. The matrix of "what Moonraker reports" only reveals itself across many printers.

**How to avoid:**
- Drive every panel's control set from the **actual subscribed printer objects + macro list + Moonraker component list** (`printer.objects.list`, `server.spoolman`/`server.power` presence, `gcode_macro` discovery), not from hardcoded assumptions.
- Hide (don't disable-and-confuse) controls for absent capabilities; show the specific "missing X macro" popups KlipperScreen uses where an action depends on a user macro.
- Build a tiny capability layer in P0/P1 that all panels query, so gating is centralized, not re-implemented per panel.

**Warning signs:**
Works on your printer, button does nothing / errors on a tester's printer; commands to `extruder1`/`heater_generic` that doesn't exist; "Load Filament" that silently fails because the macro isn't defined.

**Phase to address:** **P1** (capability layer in the shell), enforced in every panel phase (**P2–P4**). This is also a "looks done but isn't" magnet — see checklist.

---

### Pitfall 9: Unthrottled high-rate notify streams melting a Tegra-era GPU

**What goes wrong:**
Moonraker pushes `notify_status_update` frequently during a print — temperatures, position, progress all changing several times a second. If each notification triggers a recomposition / view update / graph redraw, you get continuous full-frame churn on a weak GPU with no AOT profile help (Pitfall 2). The always-visible dashboard (heater rows + temp graph) and Job Status are the worst offenders. Result: persistent jank, hot device, sluggish touch response — on the *one screen that's always up*.

**Why it happens:**
The push stream is treated as "update the UI on every event." On modern hardware that's fine; on a Nexus 7 each redraw is expensive and they pile up. The temperature graph in particular redraws an entire history series.

**How to avoid:**
- **Throttle/conflate** the notify stream into the UI at a human-meaningful rate (e.g. coalesce to ~2–4 Hz for temps/position; the user can't read faster). Coroutine `conflate()`/`sample()` or equivalent on the state flow.
- Update only changed fields; diff before pushing to the view. Avoid re-laying-out the whole dashboard per tick.
- Render the temperature graph with a fixed-size ring buffer and redraw at the throttled cadence, not per event. Consider drawing to a `Canvas`/`SurfaceView` rather than recomposing a complex node tree every frame.
- Stop/slow updates for offscreen panels — only the visible surface should consume the stream at full rate.

**Warning signs:**
Jank that gets *worse* during an active print (more notify traffic); device warm; `gfxinfo` showing sustained dropped frames on the dashboard; touch latency spikes while temps are changing.

**Phase to address:** **P1** (dashboard/graph is the first high-rate surface — set the throttling pattern here), reinforced **P3** (Job Status live summaries).

---

### Pitfall 10: Chasing KlipperScreen ~34-panel parity before the core print loop works

**What goes wrong:**
The Screen Catalog is seductive — 34 panels, every tuning knob, bed mesh heatmaps, input shaper, Spoolman. Building breadth-first means months of panels with no testable end-to-end "connect → monitor → drive a print" loop, which PROJECT.md explicitly names as the make-or-break path. You also risk porting Linux-host screens (Network/NetworkManager, host `mpv` camera, `systemctl`/Updater, System telemetry) **literally**, which the catalog flags as Android-portability boundaries that don't map 1:1.

**Why it happens:**
Parity is a concrete, enumerable target, so it feels like progress. Each panel is "just like the last one." The catalog's completeness invites literal translation.

**How to avoid:**
- Hold the v1 line from PROJECT.md: Connect + Move/Temp/Extrude/Files/JobStatus/Macros/Console, full stop. Everything else is post-v1.
- **Never port host-integration screens literally.** Drop NetworkManager (Android owns Wi-Fi). Camera is native MJPEG/WebRTC later, not `mpv`. Updater/System/`systemctl` are host concerns — expose only what Moonraker's API offers, adapted, and only post-core.
- Treat the catalog as a *capability inventory and safety-pattern reference*, not a build checklist. Steal its routing model, capability-gating discipline, and confirmation patterns — not its panel count.

**Warning signs:**
Sprint goals phrased as "implement panel N of 34"; building bed mesh / input shaper before Job Status pause/resume works; a `mpv`/NetworkManager-shaped abstraction creeping into the Android codebase; no real print driven on hardware by mid-project.

**Phase to address:** Roadmap-structure decision (pre-P0) and guarded at **every phase transition** — the phase order itself is the prevention.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Trust cached printer state on reconnect instead of re-querying | Faster perceived resume | UI lies about print state after a drop (Pitfall 3) | **Never** for print/Klippy state; OK for static config (kinematics) |
| Hardcode controls to your two printers' capabilities | Ship panels faster | Breaks on every other printer (Pitfall 8) | Only in a throwaway P0 spike, never in shipped panels |
| Whole-app `cleartextTrafficPermitted=true` | LAN just works | Broad cleartext allowance | **Acceptable & expected** for a LAN appliance; document it |
| `FLAG_KEEP_SCREEN_ON` + battery-opt exemption instead of a proper foreground Service | Connection stays alive cheaply | Activity death still loses monitoring state | OK for v1; revisit if process-death loss bites users |
| Compose everywhere | Single modern toolkit, faster dev | Jank on Tegra with no baseline-profile rescue (Pitfall 2) | Only if validated on the Nexus 7 for list/graph surfaces |
| One global state object updated per notify event | Simple wiring | GPU churn on weak hardware (Pitfall 9) | Never on the always-on dashboard; throttle from day one |

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Moonraker websocket | Reconnect-only, no state resync | On every connect: `server.connection.identify` → `printer.objects.query` snapshot → `printer.objects.subscribe`; backfill Console via `server.gcode_store` |
| Moonraker auth | Assuming open access; forgetting trusted-client/API-key paths | Support optional API key (`X-Api-Key` / query) and oneshot-token for the websocket; handle 401 gracefully with a clear "auth required" state, not a crash |
| Klippy lifecycle | Treating connected socket as "ready" | Route UI on `klippy_state` (`startup`/`ready`/`shutdown`/`error`); gate command controls behind `ready` |
| Thumbnails | Assuming base64-inline or assuming a fixed URL shape | Resolve thumbnail `relative_path` against the Moonraker server root; handle both small/large variants; some files have none — fall back gracefully |
| JSON-RPC requests | Ignoring `id` correlation; assuming responses arrive in order | Correlate every request/response by `id`; don't assume the next message is your reply (notifications interleave) |
| Cleartext on API 23 | Relying on `isCleartextTrafficPermitted` to block/allow | Set policy via `network_security_config.xml`; test on real API 23 + chosen `targetSdk` |
| Capability detection | Inferring features from printer *model* | Detect from `printer.objects.list`, macro list, and Moonraker component presence (`server.power`, `server.spoolman`) |

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Per-event UI update from notify stream | Jank worsens during active print | Conflate/sample stream to ~2–4 Hz; diff fields | Immediately on Tegra during a print |
| Temperature graph redraw per event | Dashboard stutters, device warm | Ring buffer + throttled redraw on Canvas/SurfaceView | As soon as a heater is active |
| Files list with full-size thumbnails decoded on UI thread | Slow/janky browse, OOM | Decode downsampled (`inSampleSize`) off-thread; cache; lazy-load on scroll | A folder with many large-thumbnail gcodes |
| Loading all thumbnails/metadata up front | Long blank file screen | Page/lazy-fetch metadata as rows scroll into view | Large gcode libraries |
| Deep Compose tree on the always-on shell | Sustained dropped frames | Flatten shell; consider Views for high-churn surfaces | Continuously, no AOT profile on M to amortize |
| 2GB RAM + bitmap churn | App killed/restarted (loses monitoring) | Aggressive bitmap recycling, downsampling, bounded caches | Long sessions / many thumbnails |

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| Storing API key in plaintext SharedPreferences | Key readable on a rooted/lost tablet | Acceptable-ish for a LAN appliance, but prefer EncryptedSharedPreferences where available; document the LAN-trust model |
| Blanket cleartext with no user awareness | MITM on a hostile network | Fine on a trusted home LAN (the design assumption); make it explicit, allow opting into HTTPS |
| No confirmation on E-stop/power/cancel | Accidental destructive action near a hot machine | Mandatory confirm policy (Pitfall 6); E-stop fast-but-deliberate (hold/double-tap) |
| Sideload with no signing discipline | Tampered APKs, no update trust | Sign releases consistently; publish checksums on GitHub Releases |

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| Inconsistent destructive-action confirmations | Lost prints from fat-finger taps | One confirm primitive for the whole destructive set |
| Showing unsupported controls | Buttons that error/no-op confuse users | Capability-gate: hide what the printer lacks |
| Blocking UI on slow LAN calls | Screen feels dead; double-taps queue dupes | Timeouts + in-flight disabled state + debounce |
| Stale state after a connection blip | UI lies about temps/print status | Resync snapshot on reconnect; show a clear "reconnecting" banner |
| Portrait/phone-shaped layouts | Cramped on a mounted 7" landscape tablet | Landscape-first per PROJECT.md; action bar on the edge like the catalog's BasePanel |
| Tiny touch targets ported from a desktop layout | Mis-taps on a small touchscreen | Size targets for finger use on 1280×800 @7" |

## "Looks Done But Isn't" Checklist

- [ ] **Connection:** Often missing reconnect-with-resync — verify by yanking Wi-Fi mid-print, restoring, and confirming temps/print state are *correct* (not stale) and Console backfilled.
- [ ] **Connection:** Often missing the real-API-23 cleartext test — verify `ws://192.168.x.x` connects on an actual Android 6 device with your shipping `targetSdk`.
- [ ] **Klippy state:** Often missing shutdown/error handling — verify by firing E-stop / triggering a Klipper shutdown and confirming the UI routes to a recovery surface, not broken controls.
- [ ] **Capability gating:** Often missing on printers unlike yours — verify against a printer with a *different* config (no second extruder / no filament sensor / no power device).
- [ ] **Destructive actions:** Often missing a confirmation somewhere — audit every high-impact button for the shared confirm primitive; specifically check Power and Cancel.
- [ ] **Always-on:** Often missing Doze survival — verify the connection is alive (or cleanly resyncs) after the tablet sits unplugged and screen-off for 20+ minutes.
- [ ] **Performance:** Often missing the on-device check — verify dashboard + file scroll are smooth on the actual Nexus 7 during an active print, not just the emulator.
- [ ] **Thumbnails:** Often missing the no-thumbnail / large-library case — verify a folder with many files and some thumbnail-less gcodes doesn't OOM or hang.
- [ ] **Process death:** Often missing print-monitoring recovery — verify killing the app and relaunching mid-print restores correct Job Status from a fresh query.

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| Built reconnect without resync | MEDIUM | Add the identify→query→subscribe handshake + gcode_store backfill; make query result the source of truth |
| Chose Compose, janks on Nexus 7 | HIGH | Move high-churn surfaces (list/graph/dashboard) to Views; isolate via interop rather than full rewrite |
| Cleartext blocked after raising targetSdk | LOW | Add/scope `network_security_config.xml`; retest on API 23 |
| Doze killing connection | LOW–MEDIUM | Add battery-opt exemption + `KEEP_SCREEN_ON`; if still lost on screen-off, add foreground Service |
| Per-event UI churn | LOW | Insert conflate/sample on the state flow; throttle graph redraw |
| Capability assumptions baked into panels | MEDIUM | Introduce central capability layer; refactor panels to query it |
| Ported a host screen literally (NetworkManager/mpv) | MEDIUM | Delete it; replace with Android-native or drop from v1 per scope |

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| 1. API 23 cleartext | P0 | `ws://192.168.x.x` connects on real Android 6 at shipping targetSdk |
| 2. Baseline-profile/Compose perf myth | P0/P1 | Dashboard + Files scroll smooth on Nexus 7 (not emulator) |
| 3. Reconnect storm / lost notifies | P0 (skeleton), P3/P5 (print resync) | Wi-Fi-yank mid-print restores correct, non-stale state |
| 4. Klippy lifecycle states | P1 | E-stop/shutdown routes to recovery, controls gated on `ready` |
| 5. Doze / wake-lock / Wi-Fi sleep | P5 (P0 architected for resync) | Alive/resyncs after 20+ min unplugged screen-off |
| 6. Inconsistent destructive confirms | P1 (policy), P2–P4 (enforce) | Every destructive button uses the shared confirm primitive |
| 7. Blocking UI / no timeouts / dup taps | P2, P3 | No infinite hangs; double-tap can't double-fire |
| 8. Unsupported controls shown | P1 (layer), P2–P4 | Works on a differently-configured printer |
| 9. Unthrottled notify → GPU churn | P1, P3 | No sustained dropped frames during active print |
| 10. Parity-chasing / literal host ports | Roadmap structure + every transition | Print loop works on hardware before any post-core panel |

## Sources

- Moonraker docs — JSON-RPC notifications, printer admin (subscribe/query), API changes (`server.connection.identify` replacing deprecated `server.websocket.id`): https://moonraker.readthedocs.io/en/latest/external_api/jsonrpc_notifications/ , https://moonraker.readthedocs.io/en/latest/external_api/printer/ , https://moonraker.readthedocs.io/en/latest/api_changes/ (HIGH)
- OkHttp HTTPS / TLS configuration history; OkHttp issue #3325 "NetworkSecurityPolicy is not honored on Android API 23" (the cleartext fallback bug): https://square.github.io/okhttp/features/https/ , https://github.com/square/okhttp/issues/3325 (HIGH)
- Android Developers — Cleartext communications & Network security configuration (cleartext default by API level; `targetSdk` 28 blocks by default): https://developer.android.com/privacy-and-security/risks/cleartext-communications , https://developer.android.com/privacy-and-security/security-config (HIGH)
- Android Developers — Jetpack Compose performance & Compare Compose/View metrics (Baseline Profiles ineffective on L/M; Compose matches Views since 1.9 on supported hw; Compose Material minSdk moved 21→23): https://developer.android.com/develop/ui/compose/performance , https://developer.android.com/develop/ui/compose/migrate/compare-metrics (HIGH)
- Android Developers — Optimize for Doze and App Standby (Doze suspends network access, ignores wake locks; whitelist/battery-opt exemption): https://developer.android.com/training/monitoring-device-state/doze-standby (HIGH)
- KlipperScreen GTK4 fork Screen Catalog — routing model, capability-gating principle, inconsistent confirmation gates (Power/Pins immediate), host-portability boundaries (Network/Camera/Updater/System), `Future App Design Takeaways`: `E:\claude\personal\github\gtk4_klipperscreen\docs\Screen_Catalog.md` (HIGH — authoritative scope reference)
- PROJECT.md — v1 scope contract, constraints, out-of-scope boundaries: `.planning/PROJECT.md` (HIGH)

---
*Pitfalls research for: Android Moonraker/Klipper printer-control client on old hardware*
*Researched: 2026-05-29*
