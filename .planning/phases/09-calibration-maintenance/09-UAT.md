# Phase 09 Calibration & Maintenance UAT

## Status

PASS — live on-device UAT executed on real flox + a live printer (2026-06-03). All five calibration
manual-only verifications passed. One cross-cutting session-layer defect (SAVE_CONFIG re-handshake feed
freeze) was discovered and deferred to the promoted reliability phase — it is NOT a calibration-feature
failure (see Gate Result + `09-07-SUMMARY.md`).

## Preconditions

- `09-VERIFICATION.md` automated release gate is PASS (full `:app:testReleaseUnitTest` green).
- All calibration screens are nav-wired (09-07) and reachable from the Calibration hub.
- Browser/KlipperScreen remains closed during the workflow.
- Use safe operating conditions: a homed printer, supervised, bed at a safe Z before any probe nudge.
- Use a safe throwaway gcode file for the delete-scoping check.

## Environment

- **Timestamp:** 2026-06-03
- **Printer:** Ender 3 Pro (live; `192.168.1.121:7125`, klicky detachable probe). NOTE: app was pointed
  at the **E3** for this UAT, not the E5. (E5 = `192.168.1.120:7125`.)
- **Device/tablet:** flox (LineageOS 18.1 / API 30 / Adreno 320 — the perf FLOOR)
- **App commit/build:** through `4f65fa0` (probe-calibrate UAT rework), signed release on flox
- **Safe delete file:** non-printing files in the E3 gcode library (420 files); active-file check used
  `Shaft Adapter_PLA_15m58s.gcode`

## Checklist (the five Manual-Only Verifications, per 09-VALIDATION § Manual-Only Verifications)

| # | Step | Requirement | Result | Notes |
|---|---|---|---|---|
| 1 | Bed-mesh heatmap render + Adreno-320 fill-rate | CALIB-04 / BEDM-01 | PASS | heatmap renders the probed grid with token ramp + dots; gfxinfo gate cleared |
| 2 | Z_TILT_ADJUST run-and-converge (QGL proxy, D-02) | CALIB-03 / ZCAL-01 | PASS | reworked on device (`517ab4a`): load-scoped state + parsed adjustments |
| 3 | Screws-tilt guided loop end-to-end | CALIB-02 / BEDL-01 | PASS | reworked on device (`b47a955`): load screw config, fresh-instance gating, worst-screw by deviation-from-base |
| 4 | Z-calibrate Accept -> SAVE_CONFIG -> spine recovers (G2) | CALIB-05 / ZCAL-01 | PASS* | probe-calibrate page substantially reworked this session; manual probe session, TESTZ jog, Accept, amber Save -> SAVE_CONFIG all work. *G2 self-heal of the LIVE FEED after SAVE_CONFIG did NOT hold on the E3 — deferred (see Gate Result) |
| 5 | D-15 delete-during-print scoping | CALIB-06 / D-15 | PASS | only the active/paused file undeletable ("Cannot delete the file that is currently printing."); every other file deletable mid-print; all deletable when idle |

## Gate Result

PASS for the calibration surface — BEDM-01 / BEDL-01 / ZCAL-01 / CALIB-01..06 are verified on real
hardware and move to Validated.

**Deferred defect (cross-cutting, NOT a calibration feature):** after a `SAVE_CONFIG` (FIRMWARE_RESTART)
the in-session G2 re-handshake did not restore the live subscription on the E3 — the entire feed froze
(Home stuck on the last finished print; a newly started print never registered; Files "Start" stuck on
"Starting...") until the app was force-restarted. Recovers cleanly on a fresh app start, so reducer /
subscription / routing are correct; only the in-session re-handshake recovery is broken. Captured as a
HIGH-priority item (`.planning/todos/pending/save-config-rehandshake-not-refreshing-config.md`) and
assigned to the **promoted Optimization / Network Efficiency / End-to-End Reliability phase** (moved to
next-after-9 by owner decision, 2026-06-03).
