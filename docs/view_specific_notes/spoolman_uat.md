# Spoolman View — On-Device UAT Record (Plan 11-09)

The one-shot end-to-end gate for the whole Spool view: build + install on flox
(LineageOS 18.1 / API 30 / real Adreno 320 / 2GB / armeabi-v7a) against a live
printer + Spoolman, verified hands-on. This is the 8th-strike-prevention gate —
green unit suites have hidden runtime bugs in seven prior phases.

**Hosts**

- Spoolman: `http://192.168.1.253:7912`
- Ender 5 Plus Moonraker: `http://192.168.1.120:7125` (active baseline = **5**)
- Ender 3 Pro Moonraker: `http://192.168.1.121:7125` (active baseline = **1**)
- Sacrificial spool for reweight tests: **spool 3** (CMYK Yellow, PLA+ 2.0)

---

## Live-Test Safety — Captured Baselines (save step, BEFORE any write)

Captured `2026-06-04` via `tools/spoolman-probe.py` (read-only) and saved as
`*-before.json` fixtures. **These are the restore targets.**

| Subject | Field | Baseline value | Fixture |
|---------|-------|----------------|---------|
| Ender 5 Plus | active `spool_id` | **5** | `docs/commands/spoolman-uat-baseline-ender5-before.json` |
| Ender 3 Pro  | active `spool_id` | **1** | `docs/commands/spoolman-uat-baseline-ender3-before.json` |
| Spool 3 (sacrificial) | `remaining_weight` | **579.0** | `docs/commands/spoolman-uat-baseline-spool3-before.json` |
| Spool 3 | `used_weight` | 421.0 (linked: `initial − remaining`) | (same) |
| Spool 3 | `initial_weight` | 1000.0 | (same) |

Probe summaries at capture (both printers): `has_spoolman_component: true`,
`spoolman_connected: true`, `pending_reports_count: 0`, `proxy_v2_has_response_key: true`.

> Restore commands are at the bottom of this doc. **Never issue delete calls.**

---

## Autonomous Prep Results (done before the human gate)

| Step | Result |
|------|--------|
| Build `:app:assembleDebug` (Windows-side, `gw.bat`) | **BUILD SUCCESSFUL** (debug auto-signed) |
| Install on flox (`adb -s 0a64b42e install -r`) | **Success** (`app-armeabi-v7a-debug.apk`) |
| Instrumented `ScanSurfaceLifecycleTest` on real flox (`:app:connectedDebugAndroidTest`) | **2/2 PASS, 0 failures** — `scanSurfaceReleasesCameraOnDispose` (9.32s) + `noCameraDeviceDegradesToManualPicker` (1.06s); the device run that couldn't happen at compile time. Camera-release (D-14) + no-camera degrade (D-15) are hardware-proven. |
| Baselines captured | E5=5, E3=1, spool 3 = 579.0g (see table above) |

---

## Hands-on UAT Checks (Matthew runs on flox + a live printer)

Record PASS/FAIL + any runtime finding per check. The headline is **check 3**.

| # | Check | SC / Decision | Result | Notes |
|---|-------|---------------|--------|-------|
| 1 | Capability gate: Spool tile live + active-spool card on Status | SC-1 / D-02,D-03 | ☐ | |
| 2 | Picker: list loads, material chip filters (PLA→7), color swatch similarity, recent/low sorts | SPOOL-03 / D-05,D-06 | ☐ | |
| 3 | **Headline scan-to-assign: load filament → scan its label → confirm → active flips in Spoolman** | **SC-2/SC-4** / D-12 | ☐ | |
| 4 | Permission + no-camera degrade → manual picker still sets active | SC-3 / D-15 | ☐ | |
| 5 | Camera release on nav-away/background | D-14 | ☐ | |
| 6 | Warn-only print-start gate (mismatch/low/no-spool → amber, "Print anyway" 1 tap, never blocks) | SPOOL-07 / D-01 | ☐ | |
| 7 | Change-during-print: set active mid-print, no interruption, card reconciles | SPOOL-09 | ☐ | |
| 8 | External-change reconcile (Fluidd/probe set) → card flips without manual refresh | SPOOL-08 / D-10 | ☐ | |
| 9 | Measured gross-weight on spool 3: remaining/used linked, corrected after write | SPOOL-09 / D-04 | ☐ | |
| — | Font-size sanity: nothing cramped (15sp floor) | D-16 | ☐ | |

---

## RESTORE (mandatory, after the checks)

Run from the repo root. These reverse every authorized live write.

```bash
# 1. Restore active spool per printer (whichever you set during UAT)
#    E5 → 5
curl -s -X POST http://192.168.1.120:7125/server/spoolman/spool_id \
  -H 'Content-Type: application/json' -d '{"spool_id": 5}'
#    E3 → 1
curl -s -X POST http://192.168.1.121:7125/server/spoolman/spool_id \
  -H 'Content-Type: application/json' -d '{"spool_id": 1}'

# 2. Restore spool 3 remaining_weight if check 9 modified it
curl -s -X PATCH http://192.168.1.253:7912/api/v1/spool/3 \
  -H 'Content-Type: application/json' -d '{"remaining_weight": 579.0}'

# 3. Re-confirm and save the after-restore fixtures
python3 tools/spoolman-probe.py 192.168.1.120 --out docs/commands/spoolman-uat-baseline-ender5-after-restore.json
python3 tools/spoolman-probe.py 192.168.1.121 --out docs/commands/spoolman-uat-baseline-ender3-after-restore.json
python3 tools/spoolman-probe.py 192.168.1.120 --spool-id 3 --out docs/commands/spoolman-uat-baseline-spool3-after-restore.json
```

**Restore confirmation:** ☐ E5 back to 5  ☐ E3 back to 1  ☐ spool 3 = 579.0g

---

## Result

_To be completed after the hands-on run. Record per-check PASS/FAIL above,
confirm the restore, then resume with "approved" or describe issues for
gap-closure planning._
