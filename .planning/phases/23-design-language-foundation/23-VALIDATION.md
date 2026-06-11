---
phase: 23
slug: design-language-foundation
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 23 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit (host unit tests) + Compose @Preview matrices (visual) |
| **Config file** | app/build.gradle (test sourceset); built Windows-side via `E:\Android\gw.bat` |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests <ClassFilter> --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~ (TBD by planner — host unit suite) |

---

## Sampling Rate

- **After every task commit:** Run the quick host unit-test filter for the touched component class
- **After every plan wave:** Run the full host unit suite
- **Before `/gsd-verify-work`:** Full host suite green + @Preview matrices render + on-device flox spot-check (pilot SpoolScreen, both orientations)
- **Max feedback latency:** (TBD by planner)

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| (filled by planner) | | | UX foundation | — | N/A (local-only UI) | unit/preview | | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] Component-class test stubs that compile day-one (fail() bodies, no refs to unbuilt symbols — see [[dinghy-wave0-red-scaffold-compile]])
- [ ] `verify_ligatures.py` gate confirming the 6+ new Material Symbols glyphs resolve in the bundled v2.944 font (icon ligature presence is ASSUMED per research A1)
- [ ] `DinghyIconsTest` drift-guard extended for the new registrations

*If none: "Existing infrastructure covers all phase requirements."*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| SpoolScreen pilot renders correctly in BOTH orientations | SC-3 | Visual/layout fidelity + unit-grid constant-through-rotation can't be host-asserted | Owner eyeball on flox, portrait + landscape (per [[dinghy-display-ondevice-iteration]]) |
| Unit grid U constant through rotation | SC-2/4 | Live DPI + rotation behavior is device-only | Rotate device on pilot screen; U must not change |

*If none: "All phase behaviors have automated verification."*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency target set
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
