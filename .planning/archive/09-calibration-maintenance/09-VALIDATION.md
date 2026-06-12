---
phase: 9
slug: calibration-maintenance
status: planned
nyquist_compliant: true
wave_0_complete: false  # set true after 09-01 captures fixtures + RED scaffolds
created: 2026-06-02
---

# Phase 9 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from `09-RESEARCH.md` § Validation Architecture. The load-bearing
> Nyquist principle for this phase: **every routine's result-parse and
> gating-predicate is a PURE function fed a captured-from-real-hardware JSON
> fixture** — host-testable with no device. The on-device run is the
> mock-vs-reality backstop (two prior strikes: Phases 2/5).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit (kotlin.test) — project standard; 615 tests green as of Phase 8 |
| **Config file** | `app/build.gradle.kts` (test deps) — no separate runner config |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --tests '*Calibration*' --tests '*ScrewsTilt*' --tests '*BedMesh*' --tests '*ManualProbe*' --tests '*FilesDelete*' --no-daemon"` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testReleaseUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–90 seconds (full suite); quick run ~15s |
| **On-device proxy** | `adb` install of release build on **flox** + **live Ender 5 Plus** (mock-vs-reality backstop; `gfxinfo` for heatmap perf) |

---

## Sampling Rate

- **After every task commit:** Run quick run command (calibration + files-delete pure tests)
- **After every plan wave:** Run full suite command (`:app:testReleaseUnitTest`)
- **Before `/gsd-verify-work`:** Full suite green + on-device UAT on flox + live Ender 5 Plus (incl. the D-15 delete-scoping check in `09-UAT.md`)
- **Max feedback latency:** ~90 seconds

---

## Per-Task Verification Map

| Req (proposed) | Behavior | Test Type | Automated Command (`--tests` filter) | File Exists | Status |
|--------|----------|-----------|--------------------------------------|-------------|--------|
| CALIB-02 | Screws-tilt: structured `results` → worst-out-of-tolerance screw + "X of N in tol" (D-03) | unit (pure parser) | `*ScrewsTiltResultTest*` | ❌ W0 — feed REAL E5 fixture | ⬜ pending |
| CALIB-02 | Screws-tilt: join `results["screwN"]` (1-based loop index) to config `screwN`/`screwN_name` by index (Pitfall 1) | unit | `*ScrewsTiltResultTest*` | ❌ W0 | ⬜ pending |
| CALIB-03 | Z_TILT/QGL: `applied==true` → done; RpcError → failed (Pitfall 2) | unit (pure state machine) | `*TiltResultTest*` | ❌ W0 | ⬜ pending |
| CALIB-04 | Bed-mesh: `mesh_matrix`→color, `probed_matrix`→dots, empty-state from `profile_name`/`mesh_matrix` (Pitfall 4); profile list from `profiles` keys | unit (pure model) | `*BedMeshModelTest*` | ❌ W0 | ⬜ pending |
| CALIB-04 | Bed-mesh heatmap Canvas: allocation-free onDraw + token recolor + Adreno-320 perf | on-device (gfxinfo) | manual — flox + gfxinfo two-part gate | ❌ device checkpoint | ⬜ pending |
| CALIB-05 | Manual-probe: `is_active` drives page state; `Z position: a --> b <-- c` parse → z_position | unit (pure parser) | `*ManualProbeStateTest*` | ❌ W0 | ⬜ pending |
| CALIB-05 | Z_ENDSTOP_CALIBRATE vs PROBE_CALIBRATE gate by probe-present (A3) | unit (pure predicate) | `*ProbePresentGateTest*` | ❌ W0 | ⬜ pending |
| CALIB-01 | Hub lists ONLY supported routines (`hasObject` gating; no dead buttons) | unit | `*CalibrationGateTest*` | ❌ W0 | ⬜ pending |
| CALIB-06 | Files delete: only the `print_stats.filename`-matching file is undeletable during print (D-15) | unit (pure predicate) | `*FilesDeleteGateTest*` | ❌ W0 | ⬜ pending |
| (cross) | SAVE_CONFIG → klippy shutdown→ready → re-handshake (D-12) | integration (existing G2 harness) | `*ReHandshake*` (extend 05-10 G2 test) | ✅ extend | ⬜ pending |
| (cross) | gcode.script gets G4 120s timeout (no false "could not be sent") | unit (existing dispatcher test) | `*CommandDispatcher*` | ✅ exists | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Off-Hardware Testability (the Nyquist core)

Every routine's RESULT-PARSE and GATING-PREDICATE is a **pure function** fed a
captured-from-real-hardware JSON fixture — host-testable with NO device:

- **screws-tilt:** pure `parseScrewsTilt(results, config) → GuidedLoopState` → worst screw, in-tol count.
- **bed-mesh:** pure `BedMeshModel.from(bedMeshObject)` → matrices, min/max, empty-state, profile names.
- **z-tilt/QGL:** pure state machine over {dispatched, `applied`, RpcError}.
- **manual-probe:** pure `parseZPosition(line)` + `is_active` page-state derivation.
- **files-delete:** pure `deleteAllowed(selectedPath, activePrintFilename, printState) → Boolean`.

---

## Wave 0 Requirements (fixture-capture-first — NON-NEGOTIABLE)

Capture REAL shapes from the live E5 **before** writing any parser (Pitfall 6 / mock-vs-reality backstop):

- [ ] `app/src/test/resources/fixtures/screws_tilt_adjust_e5.json` — REAL `SCREWS_TILT_CALCULATE` run capture (CALIB-02)
- [ ] `app/src/test/resources/fixtures/bed_mesh_e5.json` — REAL post-`BED_MESH_CALIBRATE` `objects/query?bed_mesh` (CALIB-04)
- [ ] `app/src/test/resources/fixtures/z_tilt_e5.json` — REAL post-`Z_TILT_ADJUST` capture (CALIB-03)
- [ ] `app/src/test/resources/fixtures/manual_probe_e5.json` — REAL `manual_probe` mid-session capture (CALIB-05)
- [ ] `app/src/test/resources/fixtures/configfile_screws_e5.json` — `configfile.settings["screws_tilt_adjust"]` (D-04/D-06 screw coords/names)
- [ ] RED scaffolds: `*ScrewsTiltResultTest`, `*TiltResultTest`, `*BedMeshModelTest`, `*ManualProbeStateTest`, `*ProbePresentGateTest`, `*CalibrationGateTest`, `*FilesDeleteGateTest`
- [ ] Framework install: none — JUnit/kotlin.test already present

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Bed-mesh heatmap render + Adreno-320 fill-rate | CALIB-04 | GPU fill-rate not measurable off-device | Install release on flox; open bed-mesh page on live E5 mesh; run two-part liveness+latency gate via `gfxinfo` (Phase 3/5 discipline) |
| QGL run-and-converge | (D-02) | No gantry hardware on either test printer | **Unverified by design.** `Z_TILT_ADJUST` is the on-device proxy (identical code path on real dual-Z) |
| Screws-tilt guided loop end-to-end | CALIB-02 | Requires physical screw-turning + re-probe cycle | flox + live E5: run loop, confirm worst-screw + clock-turn display advances correctly |
| Z-calibrate accept → SAVE_CONFIG recovery | CALIB-05 / D-12 | Requires real klippy restart | flox + live E5: ACCEPT → SAVE_CONFIG → confirm spine reconnects (G2 path) |
| Files delete scoping during print | CALIB-06 / D-15 | Requires active print | flox + live E5: start print, confirm only the printing file is undeletable (`09-UAT.md` check) |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references (the 5 real-hardware fixtures above)
- [ ] No watch-mode flags
- [ ] Feedback latency < 90s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** planned 2026-06-02 (7 plans; Wave 0 = 09-01 fixtures+RED; per-task verify mapped)
