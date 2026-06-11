---
phase: 26
slug: adjustment-screens
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-10
---

# Phase 26 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit4 host unit tests (Gradle `testDebugUnitTest`) + Compose `@Preview` matrix + on-device UAT (flox) |
| **Config file** | `app/build.gradle.kts` (existing — no Wave 0 install) |
| **Quick run command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests <filter> --no-daemon" 2>&1 \| tr -d '\r'` |
| **Full suite command** | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon" 2>&1 \| tr -d '\r'` |
| **Estimated runtime** | ~120 seconds (full host suite) |

---

## Sampling Rate

- **After every task commit:** Run quick command with the touched test class filter
- **After every plan wave:** Run full suite command
- **Before `/gsd-verify-work`:** Full suite must be green + `assembleDebug` builds
- **Max feedback latency:** 180 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | — | — | UX migration (no REQ-IDs) | — | N/A | unit | (per plan) | — | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Clamp-authority regression tests (Phase-17 suite) identified and kept green — no new infra needed
- [ ] New shared-component tests (IncrementPicker / AdjusterPanel) scaffolded compiling-RED per [[dinghy-wave0-red-scaffold-compile]] (fail() bodies, no refs to unbuilt symbols)

*Existing Gradle/JUnit infrastructure covers all phase requirements.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Owner approval both orientations on flox | SC-1 | Visual/interaction quality judgment | Build, install on flox, owner navigates each rebuilt screen portrait + landscape |
| Scrubber no-rebuild-mid-drag feel | SC-3 | Drag latency/feel not host-testable | Drag scrubber on-device; fill/thumb/value update in place, value sticks on release |
| ≥64px targets / fsSp S/M/L / rotation conformance | SC-4 | Rendered-size + rotation behavior | Preview matrix at fs=L + on-device spot check at S/M/L, rotate each screen |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
