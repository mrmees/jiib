# Phase 13 — Optimization / Network Efficiency / End-to-End Reliability UAT

## Status

**RE-RUN PENDING (after 13-05)** — the 1st run FAILED (2026-06-03; see ## Gaps G-B1 + G-A1). The 13-05
gap-closure fixes are committed (Task 1 pingInterval keepalive `404e00e`, Task 2 nav-state hoist
`5451638`, Task 3 socket-reconnect Splash + min-dwell `777a74d`), full unit suite GREEN + assembleRelease
SUCCESSFUL, and a freshly debug-signed release APK is installed on flox. The result rows below are reset
to pending for the **2nd run**; the ## Gaps history is retained for context. Phase NOT marked complete
until both scenarios PASS on both printers.

> **1st-run summary (2026-06-03, retained):** Scenario A (klippy-restart recovery) PASSED but bounced the
> app to Home on recovery (G-A1); Scenario B (mid-print network drop) FAILED — no Syncing splash, the
> feed just froze (G-B1, the silent half-open drop). Both root-caused tablet-side and fixed in 13-05.

> This is the **binding phase gate** (D-09). Full unit suite GREEN is necessary but NOT sufficient — a real
> Klipper FIRMWARE_RESTART and a real mid-print LAN drop cannot be exercised in unit tests, and this
> project's whole reliability class (mock-vs-reality, 3 strikes) is exactly what green units miss.

## Preconditions

- Full `:app:testReleaseUnitTest` is GREEN (incl. the new MoonrakerSocketClientTest pinning the keepalive + the updated TopRouteTest reconnect→Splash arms). — **DONE** (13-05, exit 0).
- `:app:assembleRelease` SUCCESSFUL; signed release APK installed on flox. — **DONE** (13-05, `app-armeabi-v7a-release-signed.apk` debug-signed + `adb install -r` → Success).
- Browser / Mainsail / KlipperScreen remains closed during the workflow (avoid competing subscriptions).
- Use safe operating conditions: a homed, supervised printer; bed at a safe Z before any probe nudge.
- Use a safe throwaway gcode file for any start-print check.
- Both printers reachable: **E5 = 192.168.1.120:7125**, **E3 = 192.168.1.121:7125** (the repro printer).

## Environment

- **Timestamp:** _(fill on run)_  — **Run #2 (after 13-05)**
- **Device/tablet:** flox (Nexus 7 2013 / LineageOS 18.1 / API 30 / Adreno 320 — the perf FLOOR)
- **App commit/build:** through `777a74d` (13-05 Tasks 1-3 complete: G-B1a keepalive + G-A1 nav-hoist +
  G-B1b reconnect-Splash/min-dwell), release APK `app-armeabi-v7a-release-signed.apk` debug-signed +
  installed on flox (this session).
- **Printers:** E5 = Ender 5 Plus (Pi 4, `192.168.1.120:7125`); E3 = Ender 3 Pro (RockPro64,
  `192.168.1.121:7125`). Both klicky detachable probes. **The E5 is unproven, not assumed-good (D-02) —
  both printers run both scenarios.**

## Checklist — Task 2 acceptance rows (one-to-one with 13-04-PLAN Task 2)

### Scenario A — D-09a (the headline): SAVE_CONFIG → Syncing splash → feed resumes (no app restart) → start print registers (printState→Printing)

Steps per printer: (1) point app at printer, confirm live temps/position updating; (2) issue SAVE_CONFIG
(or FIRMWARE_RESTART) from Mainsail/console; (3) observe a brief **Syncing** splash (D-03), then the feed
RESUMES live without force-stopping the app; (4) start a print — Home Print-Status registers it
(printState→Printing, progress ring + stats live), NO app restart.

| # | Printer | Check | Result | Notes |
|---|---------|-------|--------|-------|
| A-E5 | E5 (192.168.1.120) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) → **stays on the screen you were on (NOT forced to Home, G-A1)** | _(PASS/FAIL)_ | Re-run after 13-05 fixes (watch for the Syncing splash explicitly; confirm no Home bounce). |
| A-E3 | E3 (192.168.1.121) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) → **stays on the screen you were on (NOT forced to Home, G-A1)** | _(PASS/FAIL)_ | Re-run after 13-05 fixes. |

### Scenario B — D-09b (mid-print drop): network drop → reconnect → print state resyncs

Steps per printer: (5) start a print (or continue the one from A); (6) drop the network (toggle tablet
WiFi off, or pull the printer's network briefly); (7) restore the network — observe a **Syncing** splash,
then the print state resyncs correctly (printState back to Printing, live progress/temps resume) — no
frozen feed, no stale print.

| # | Printer | Check | Result | Notes |
|---|---------|-------|--------|-------|
| B-E5 | E5 (192.168.1.120) | mid-print → tablet WiFi off → within ~10-20s a full Syncing splash appears (drop detected via the new keepalive) → restore WiFi → print state resyncs (printState→Printing, live progress/temps resume) | _(PASS/FAIL)_ | Re-run after 13-05 fixes (pingInterval keepalive + reconnect→Splash). |
| B-E3 | E3 (192.168.1.121) | mid-print → tablet WiFi off → within ~10-20s a full Syncing splash appears (drop detected via the new keepalive) → restore WiFi → print state resyncs (printState→Printing, live progress/temps resume) | _(PASS/FAIL)_ | Re-run after 13-05 fixes. |

### D-03 — Syncing splash observed every recovery

| # | Check | Result | Notes |
|---|-------|--------|-------|
| D03 | A brief **Syncing** splash is visible on EVERY recovery (each Scenario-A SAVE_CONFIG and each Scenario-B reconnect) — recovery is never silent | _(PASS/FAIL)_ | Re-run after 13-05 fixes (the ~600ms min-dwell latch makes a fast recovery splash perceptible on BOTH paths). |

### Backstop — Probe-Calibrate z_offset fresh after SAVE_CONFIG (Pitfall 3, SC-1/SC-3)

| # | Check | Result | Notes |
|---|-------|--------|-------|
| PROBE | Open Probe-Calibrate after a SAVE_CONFIG; displayed z_offset is correct/fresh (proves removing `refreshProbeZOffset` did NOT regress config freshness) | _(PASS/FAIL)_ | |

### Live-data spot-checks — SC-3 behavior-preserving (each screen still correct on-device)

| # | Screen | Check | Result | Notes |
|---|--------|-------|--------|-------|
| SC-TEMP | Temp graph | Live temperature graph still renders + updates correctly on-device | _(PASS/FAIL)_ | |
| SC-MOVE | Move | Move panel still shows correct live position + jogs correctly | _(PASS/FAIL)_ | |
| SC-FILES | Files | Files list still loads + shows correct live data (thumbnails, list) | _(PASS/FAIL)_ | |

## Gate Result

**RE-RUN PENDING (after 13-05).** 1st run FAILED (2026-06-03) — Scenario A passed with a Home-bounce
side-effect (G-A1), Scenario B failed on the silent mid-print network drop (G-B1). The 13-05 gap-closure
(keepalive + nav-hoist + reconnect-Splash/min-dwell) is committed, GREEN, and installed on flox. Awaiting
Matthew's 2nd on-device run on E5 + E3. Phase NOT marked complete until both scenarios PASS on both
printers (Syncing splash confirmed visible on every recovery, no Home bounce, probe z_offset fresh,
Temp/Move/Files spot-checks pass).

## Gaps

> **Re-run after 13-05 fixes** — both gaps below are addressed by 13-05 (G-B1a OkHttp pingInterval
> keepalive `404e00e`; G-A1 nav-state hoist `5451638`; G-B1b socket-reconnect Splash + ~600ms min-dwell
> `777a74d`). This history is retained for context; the binding verification is the 2nd on-device run
> above.

### G-B1 — Mid-print network drop is never detected (BLOCKING, SC-3/D-09b)
- **Symptom (on-device):** toggling the tablet WiFi off mid-print produces NO Syncing splash and NO
  "connection lost" indication — the feed just freezes (numbers stop). On WiFi restore there is no visible
  resync. Reproduces independent of printer (it is the tablet's connection that drops).
- **Root cause (code-confirmed):**
  1. `MoonrakerSocket.defaultClient()` sets `connectTimeout` + `readTimeout(0)` but **no `pingInterval`**
     (`grep pingInterval app/src/main/java` → none). A WiFi-off drop is a half-open TCP socket; with no
     websocket keepalive ping, OkHttp never detects the dead peer, so `onFailure` never fires,
     `SocketEvent.Closed` is never emitted, and the reconnect supervisor never runs — the feed freezes
     indefinitely. (This is why the D-07b unit test is GREEN yet the live path fails: `FakeWebSocket`
     synthesizes `Closed`; real OkHttp never produces it without keepalive — the project's 4th
     mock-vs-reality strike.)
  2. Even once a drop IS detected, the supervisor's network-reconnect branch emits
     `ConnectionState.Disconnected` (MoonrakerSession `run()` Network/Served branches), NOT `Syncing` — so
     there is no Syncing splash on the network-reconnect path (Syncing is currently only wired for the
     klippy-restart re-handshake). D-09b/D-03 require the splash here too.
- **Fix direction:** add an OkHttp `pingInterval` (e.g. ~10 s) to `defaultClient()` so half-open drops are
  detected promptly → `onFailure` → `Closed` → supervisor reconnects → print-state resync; and surface a
  Syncing-style indicator on the network-reconnect path so recovery is visibly non-silent. Optionally a
  status-staleness watchdog as belt-and-suspenders. Re-verify on-device (both scenarios, both printers).

### G-A1 — App returns to the Home screen on recovery (MINOR, UX)
- **Symptom:** after a Scenario-A SAVE_CONFIG recovery, the app navigates back to the Home/Print-Status
  screen rather than staying on the screen the user was on. Matthew: "isn't the worst behavior."
- **Likely cause:** the recovery/spine-rebuild re-keys the shell (e.g. `remember(store)` in AppShell)
  and resets the nav back-stack to the default destination.
- **Disposition:** minor; fold into the gap-closure plan if cheap, else defer (decide with Matthew).

**Resume-signal:** report the 6-item results — E5×a, E5×b, E3×a, E3×b, Syncing-splash, Probe z_offset —
plus the Temp/Move/Files spot-checks. Type "approved" if all pass, or describe which combination froze /
failed to resync (and which printer) so a gap-closure plan can be cut.
