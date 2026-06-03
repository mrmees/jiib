# Phase 09 Calibration & Maintenance UAT

## Status

PENDING - live Ender 5 Plus on-device UAT has not been executed (runs in the 09-07 on-device gate).

## Preconditions

- `09-VERIFICATION.md` automated release gate is PASS (full `:app:testReleaseUnitTest` green).
- All calibration screens are nav-wired (09-07) and reachable from the Calibration hub.
- Browser/KlipperScreen remains closed during the workflow.
- Use safe operating conditions: a homed printer, supervised, bed at a safe Z before any probe nudge.
- Use a safe throwaway gcode file for the delete-scoping check.

## Environment

- **Timestamp:** PENDING
- **Printer:** Ender 5 Plus (live; `192.168.1.120:7125`)
- **Device/tablet:** flox (LineageOS 18.1 / API 30 / Adreno 320 — the perf FLOOR)
- **App commit/build:** PENDING
- **Moonraker host:** PENDING
- **Safe delete file:** PENDING

## Checklist (the five Manual-Only Verifications, per 09-VALIDATION § Manual-Only Verifications)

| # | Step | Requirement | Expected Evidence | Result | Notes |
|---|---|---|---|---|---|
| 1 | Bed-mesh heatmap render + Adreno-320 fill-rate | CALIB-04 / BEDM-01 | Install release on flox; open the bed-mesh page on a live E5 mesh; the Canvas heatmap renders the probed grid with the token ramp + probe dots; run the two-part liveness+latency `gfxinfo` gate (Phase 3/5 discipline) — allocation-free, no frozen frames, p95 within budget | PENDING |  |
| 2 | Z_TILT_ADJUST run-and-converge (the QGL proxy, D-02) | CALIB-03 / ZCAL-01 | On the live E5, Run Z-tilt; the page goes Running → Done when `z_tilt.applied == true`; a printer rejection surfaces as the redacted Failure toast (NEVER inferred from `applied==false` alone). QGL is unverified by design — identical code path | PENDING |  |
| 3 | Screws-tilt guided loop end-to-end | CALIB-02 / BEDL-01 | flox + live E5: run `SCREWS_TILT_CALCULATE`; the worst out-of-tolerance screw + its clock-turn + CW/CCW direction display correctly; turn → re-probe → advance cycles correctly through the loop | PENDING |  |
| 4 | Z-calibrate Accept → SAVE_CONFIG → spine recovers (G2) | CALIB-05 / ZCAL-01 | On the live E5, open Probe-Calibrate; Start opens a manual-probe session (live `z_position` hero updates as you nudge with TESTZ; the `// Z position:` bracket renders, tolerating `??????` bounds); Accept captures the offset; amber Save → SAVE_CONFIG restarts klippy and the spine re-handshakes and recovers (G2 `notify_klippy_ready`); Back is suppressed while the session is live | PENDING |  |
| 5 | **D-15 delete-during-print scoping** | CALIB-06 / D-15 | Start a print of a known file; in Files, confirm ONLY the printing file is undeletable (Delete disabled + "Cannot delete the file that is currently printing." if attempted via the holder) while EVERY other idle file stays deletable mid-print; when idle, all files are deletable | PENDING |  |

## Failure Recording

If any item fails:

- Record exact observed app state (screen, vm state, toast text).
- Record printer state, `print_stats.filename`, and `manual_probe.is_active` where relevant.
- Record whether the browser/KlipperScreen was used.
- Record reproduction steps and any `gfxinfo` / `adb logcat` capture.
- Do not mark the item passed.

## Gate Result

PENDING - Phase 9 live calibration UAT is not complete. Runs in the 09-07 on-device gate; closing it
is the precondition for marking BEDM-01 / BEDL-01 / ZCAL-01 Complete (they stay Pending until then).
