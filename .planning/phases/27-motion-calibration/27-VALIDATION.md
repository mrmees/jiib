---
phase: 27
slug: motion-calibration
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-11
---

# Phase 27 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests (Gradle `test` sourceset; no Robolectric) |
| **Config file** | `app/build.gradle.kts` (existing — no Wave 0 install needed) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests <FilterClass> --no-daemon" \| tr -d '\r'` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" \| tr -d '\r'` |
| **Estimated runtime** | ~120–180 seconds (full suite, Windows-side Gradle) |

---

## Sampling Rate

- **After every task commit:** Run the quick run command (targeted `--tests` filter for the touched area)
- **After every plan wave:** Run the full suite command
- **Before `/gsd-verify-work`:** Full suite must be green + `assembleDebug` compiles
- **Max feedback latency:** ~180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | — | — | — (UX migration) | — | — | unit | — | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Ligature drift guard (`DinghyIconsTest` / `verify_ligatures.py`) must stay green if any new icon tokens register
- [ ] RED scaffolds (if any) must compile day-one — fail() bodies only, no refs to unbuilt symbols (Gradle compiles the whole test sourceset before `--tests` filtering)

*Existing infrastructure covers host-test needs; no framework install required.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Move/Motion grid layout owner approval, both orientations | SC-1 | Visual judgment on real hardware | flox on-device UAT, portrait + landscape |
| Wizard flows coherent with redesigned nav/back-stack | SC-2 | Live probe/mesh routines need a real printer | flox + E3/E5 routine walk-through |
| On-device smoke: homing/jog, probe/mesh/screws intact | SC-5 | Physical motion verification | flox + printer, owner drives |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
