# Phase 13 — Optimization / Network Efficiency / End-to-End Reliability UAT

## Status

**FAILED (2026-06-03)** — gate run on flox. Scenario A (klippy-restart recovery) PASSES with a minor nav
side-effect (app returns to the Home screen on recovery). **Scenario B (mid-print network drop) FAILS**:
no Syncing splash, no indication the connection was lost — the feed just freezes (numbers stop). Root
cause diagnosed (printer-independent, tablet-side): see **## Gaps** below. Phase NOT marked complete; a
gap-closure plan is required.

> This is the **binding phase gate** (D-09). Full unit suite GREEN is necessary but NOT sufficient — a real
> Klipper FIRMWARE_RESTART and a real mid-print LAN drop cannot be exercised in unit tests, and this
> project's whole reliability class (mock-vs-reality, 3 strikes) is exactly what green units miss.

## Preconditions

- Full `:app:testReleaseUnitTest` is GREEN (the three Wave-0 fix-driver tests + the whole suite). — **DONE** (Task 1, exit 0, forced `--rerun-tasks`).
- `:app:assembleRelease` SUCCESSFUL; signed release APK installed on flox. — **DONE** (Task 1, `adb install -r` → Success).
- Browser / Mainsail / KlipperScreen remains closed during the workflow (avoid competing subscriptions).
- Use safe operating conditions: a homed, supervised printer; bed at a safe Z before any probe nudge.
- Use a safe throwaway gcode file for any start-print check.
- Both printers reachable: **E5 = 192.168.1.120:7125**, **E3 = 192.168.1.121:7125** (the repro printer).

## Environment

- **Timestamp:** _(fill on run)_
- **Device/tablet:** flox (Nexus 7 2013 / LineageOS 18.1 / API 30 / Adreno 320 — the perf FLOOR)
- **App commit/build:** through `1feb38b` (13-03 complete), release APK
  `app-armeabi-v7a-release-signed.apk` debug-signed + installed on flox (Task 1, this session).
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
| A-E5 | E5 (192.168.1.120) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) | PASS* | Recovery works, feed resumes, print registers without restart. *Side-effect: app navigates back to the Home screen on recovery (G-A1, minor — "not the worst behavior"). |
| A-E3 | E3 (192.168.1.121) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) | PASS* | Same as A-E5 (recovery works; returns to Home on recovery). |

### Scenario B — D-09b (mid-print drop): network drop → reconnect → print state resyncs

Steps per printer: (5) start a print (or continue the one from A); (6) drop the network (toggle tablet
WiFi off, or pull the printer's network briefly); (7) restore the network — observe a **Syncing** splash,
then the print state resyncs correctly (printState back to Printing, live progress/temps resume) — no
frozen feed, no stale print.

| # | Printer | Check | Result | Notes |
|---|---------|-------|--------|-------|
| B-E5 | E5 (192.168.1.120) | mid-print network drop → reconnect → print state resyncs (printState→Printing, live progress/temps resume) | **FAIL** | No Syncing splash, no "connection lost" indication — feed just freezes (numbers stop moving). Drop is never detected (G-B1). Root cause is tablet-side (printer-independent), so this fails identically regardless of printer. |
| B-E3 | E3 (192.168.1.121) | mid-print network drop → reconnect → print state resyncs (printState→Printing, live progress/temps resume) | **FAIL** | Same root cause as B-E5 (printer-independent — missing WS keepalive + Disconnected-not-Syncing on the network-reconnect path). |

### D-03 — Syncing splash observed every recovery

| # | Check | Result | Notes |
|---|-------|--------|-------|
| D03 | A brief **Syncing** splash is visible on EVERY recovery (each Scenario-A SAVE_CONFIG and each Scenario-B reconnect) — recovery is never silent | **FAIL** | Splash not confirmed on Scenario A recovery; definitively ABSENT on Scenario B (the drop is never detected, and the network-reconnect path emits Disconnected, not Syncing). |

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

**FAILED (2026-06-03).** Scenario A passes (with the minor Home-screen nav side-effect, G-A1). Scenario B
fails on the mid-print network-drop path (G-B1) — the binding D-09b requirement is not met. Phase NOT
marked complete; gap-closure required (see ## Gaps). Probe-Calibrate + Temp/Move/Files spot-checks were
not separately recorded (Scenario B blocked the full run); to be re-run in the gap-closure UAT.

## Gaps

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
