# Phase 13 — Optimization / Network Efficiency / End-to-End Reliability UAT

## Status

PENDING — scaffold ready; the dual-printer dual-scenario on-device gate (Task 2, D-09) has NOT yet been
run. All result slots are empty and awaiting Matthew's live run on flox against BOTH live printers.

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
| A-E5 | E5 (192.168.1.120) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) | _(PASS/FAIL)_ | |
| A-E3 | E3 (192.168.1.121) | SAVE_CONFIG → Syncing splash → feed resumes no restart → start print registers (printState→Printing) | _(PASS/FAIL)_ | |

### Scenario B — D-09b (mid-print drop): network drop → reconnect → print state resyncs

Steps per printer: (5) start a print (or continue the one from A); (6) drop the network (toggle tablet
WiFi off, or pull the printer's network briefly); (7) restore the network — observe a **Syncing** splash,
then the print state resyncs correctly (printState back to Printing, live progress/temps resume) — no
frozen feed, no stale print.

| # | Printer | Check | Result | Notes |
|---|---------|-------|--------|-------|
| B-E5 | E5 (192.168.1.120) | mid-print network drop → reconnect → print state resyncs (printState→Printing, live progress/temps resume) | _(PASS/FAIL)_ | |
| B-E3 | E3 (192.168.1.121) | mid-print network drop → reconnect → print state resyncs (printState→Printing, live progress/temps resume) | _(PASS/FAIL)_ | |

### D-03 — Syncing splash observed every recovery

| # | Check | Result | Notes |
|---|-------|--------|-------|
| D03 | A brief **Syncing** splash is visible on EVERY recovery (each Scenario-A SAVE_CONFIG and each Scenario-B reconnect) — recovery is never silent | _(PASS/FAIL)_ | |

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

PENDING — _(fill on run)_

> All Scenario-A + Scenario-B rows must PASS on BOTH E5 and E3; the D-03 Syncing splash must be observed
> every recovery; the Probe-Calibrate z_offset must be fresh; the Temp/Move/Files spot-checks must each
> PASS. **Any FAIL → do NOT mark the phase complete — cut a `--gaps` closure plan** (note which printer /
> which combination froze or failed to resync).

**Resume-signal:** report the 6-item results — E5×a, E5×b, E3×a, E3×b, Syncing-splash, Probe z_offset —
plus the Temp/Move/Files spot-checks. Type "approved" if all pass, or describe which combination froze /
failed to resync (and which printer) so a gap-closure plan can be cut.
