---
phase: 24
slug: navigation-spine
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-06-09
---

# Phase 24 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Authoritative validation design: see `24-RESEARCH.md` → **## Validation Architecture**.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 4 host unit tests (Robolectric where a Context is needed); Kotlin `kotlinx-coroutines-test` for Flow logic |
| **Config file** | `app/build.gradle.kts` (test source set); runs Windows-side via `E:\Android\gw.bat` |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --tests works.mees.dinghy.<changed-class>* --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Estimated runtime** | ~60–180 seconds (host JVM; no device) |

> ⚠ Gradle does NOT run from WSL `./gradlew` — drive via `E:\Android\gw.bat` (see CLAUDE.md build env). Guard hangs with `timeout` + taskkill (see [[dinghy-display-gradle-hang-interop]]).

---

## Sampling Rate

- **After every task commit:** Run the quick command scoped to the changed class.
- **After every plan wave:** Run the full host suite.
- **Before `/gsd-verify-work`:** Full host suite green + the on-device flox gates (below) owner-approved.
- **Max feedback latency:** ~180 seconds (host); on-device gates are batched at the Wave-N on-device checkpoint.

---

## Per-Task Verification Map

> Planner fills this row-by-row. Host-testable seams (pure functions, Flow logic) get an `<automated>` command; morph/overlay/orientation behaviors are Manual-Only (on-device flox) below.

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 24-XX-YY | XX | N | nav-arch | — / — | N/A (local-LAN, no new attack surface) | unit | `{command}` | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] RED test stubs (must compile day-one per [[dinghy-wave0-red-scaffold-compile.md]]) for the host-testable seams identified in RESEARCH § Validation Architecture:
  - [ ] Connection-tier route derivation (the reshaped `TopRoute.derive` / waterfall top-tier as a pure function)
  - [ ] Pop-to-root-on-foot-gun logic (D-04: print start/end while on Move/Extrude/Calibration → pop to root)
  - [ ] Capability-driven idle-list membership (D-08 HIDE rules: Spool/Outputs/Webcam/Macros-if-bookmarks)
- [ ] No new test framework needed — existing JUnit host infra covers it.

*Stubs must compile against the whole test source set (Gradle compiles it before `--tests` filtering).*

---

## Manual-Only Verifications (on-device flox)

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Morph cross-fade holds frame budget (idle↔printing↔terminal, ~150ms one-shot) | SC-1 / D-13 | Adreno-320 frame budget is the floor; emulators lie | Trigger print start/end on flox; capture `gfxinfo framestats`; confirm no frozen frames, fall back to hard-cut if it janks |
| Morphing root owner-approval, BOTH orientations | SC-1 | Visual/interaction judgment | Owner eyeballs idle/printing/terminal Focus+Field+foot in portrait & landscape on flox |
| Floating e-stop appears only when printing, top-left of Focus, opens Confirm guard | SC-4 / D-14 | Overlay positioning + print-gating is visual | Start a print on flox; confirm e-stop visible every screen, tap → full-screen Stop Confirm |
| System foot button opens drawer; PrintStatus-as-root introduces no print-monitoring regression | SC-5 | Interim-hub wiring + regression smoke | On-device smoke: tap System → drawer; drive a print, confirm monitoring unaffected |
| Land-on-root after recovery Splash (owner-accepted regression) | D-02 / FIX-4 | Reconnect behavior is runtime-only | Force a reconnect blip on flox; confirm return to morphing root (not prior drill-down) — expected & accepted |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or a Wave 0 dependency (or are justified Manual-Only above)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s (host)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
