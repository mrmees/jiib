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
| 24-01-01 | 01 | 0 | SC-2 | T-24-01-SC | nav-compose pin (Google Maven, no slopcheck) | unit | `gw.bat :app:dependencies --configuration debugRuntimeClasspath \| grep navigation-compose:2.8.9` | ❌ W0 | ⬜ pending |
| 24-01-02 | 01 | 0 | SC-2 | — | D-06/D-08 idle order + hides; D-04 pure shouldPopToRoot (FIX-8) | unit | `gw.bat :app:testDebugUnitTest --tests *HomeActionTest --tests *PopToRootTest` | ❌ W0 | ⬜ pending |
| 24-01-03 | 01 | 0 | SC-2 | T-24-01-01 | Dest→NavDest full rename (18 files) compiles ALL THREE source sets (FIX-4); parseStartDest total | unit | `gw.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin :app:compileDebugAndroidTestKotlin` | ❌ W0 | ⬜ pending |
| 24-02-01 | 02 | 0 | SC-1 | — | ASK-OWNER glyph gate (no Claude-picked icon) | checkpoint | manual (owner names Webcam/Preheat/System glyphs) | ❌ W0 | ⬜ pending |
| 24-02-02 | 02 | 0 | SC-1 | T-24-02-01 | owner tokens registered + font-verified + drift-guarded | unit | `python tools/verify_ligatures.py && gw.bat :app:testDebugUnitTest --tests *DinghyIconsTest` | ❌ W0 | ⬜ pending |
| 24-03-01 | 03 | 1 | SC-2/SC-4 | T-24-03-01/02 | NavHost + hoisted holders (4 leak-cancels) + app-level FloatingEStop (FIX-1) + overlay-first Back (FIX-2) + startDest seed (FIX-7) + FIX-3 land-on-root | unit | `gw.bat :app:compileDebugKotlin :app:compileDebugUnitTestKotlin` | ❌ W0 | ⬜ pending |
| 24-03-02 | 03 | 1 | SC-2 | — | D-04 pop-to-root via single pure shouldPopToRoot (FIX-8) | unit | `gw.bat :app:testDebugUnitTest --tests *PopToRootTest && gw.bat :app:compileDebugKotlin` | ❌ W0 | ⬜ pending |
| 24-04-01 | 04 | 2 | SC-1/SC-3 | — | data-driven idle list + Preheat/System foot bar + Webcam-icon swap (FIX-5) | unit | `gw.bat :app:testDebugUnitTest --tests *HomeActionTest && gw.bat :app:compileDebugKotlin` | ❌ W0 | ⬜ pending |
| 24-04-02 | 04 | 2 | SC-1/SC-3/SC-4 | T-24-04-01 | Crossfade morph + NavDest seam + REMOVE in-screen e-stop (FIX-1) | unit | `gw.bat :app:assembleDebug` | ❌ W0 | ⬜ pending |
| 24-05-01 | 05 | 3 | SC-1..5 | — | full host suite GREEN + fresh non-stale APK | unit | `gw.bat :app:testDebugUnitTest` | ❌ W0 | ⬜ pending |
| 24-05-02 | 05 | 3 | SC-1..5 | T-24-05-01 | on-device flox UAT (Manual-Only below) | manual | flox owner walk (e-stop on Move, morph gfxinfo, both orientations, System→drawer, recovery→root+sub-nav reset) | ❌ W0 | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] RED test stubs (must compile day-one per [[dinghy-wave0-red-scaffold-compile.md]]) for the host-testable seams identified in RESEARCH § Validation Architecture:
  - [ ] Connection-tier route derivation (the reshaped `TopRoute.derive` / waterfall top-tier as a pure function)
  - [ ] Pop-to-root-on-foot-gun logic (D-04) as a PURE route-layer predicate `shouldPopToRoot`/`FOOT_GUN_DESTS` — host-runnable in `src/test` (NO TestNavController; the JVM set has no Robolectric, FIX-8)
  - [ ] Capability-driven idle-list membership (D-08 HIDE rules: Spool/Outputs/Webcam/Macros-if-bookmarks)
- [ ] No new test framework needed — existing JUnit host infra covers it.

*Stubs must compile against the whole test source set (Gradle compiles it before `--tests` filtering).*

---

## Manual-Only Verifications (on-device flox)

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Morph cross-fade holds frame budget (idle↔printing↔terminal, ~150ms one-shot) | SC-1 / D-13 | Adreno-320 frame budget is the floor; emulators lie | Trigger print start/end on flox; capture `gfxinfo framestats`; confirm no frozen frames, fall back to hard-cut if it janks |
| Morphing root owner-approval, BOTH orientations | SC-1 | Visual/interaction judgment | Owner eyeballs idle/printing/terminal Focus+Field+foot in portrait & landscape on flox |
| Floating e-stop appears AND works on a DRILL-DOWN screen (e.g. Move) while printing, top-left of Focus, opens Confirm guard; ABSENT when idle (FIX-1) | SC-4 / D-14 | Overlay positioning + print-gating + drill-down coverage is visual | Start a print on flox; drill into Move; confirm e-stop visible top-left, tap → full-screen Stop Confirm + confirm issues estop; verify absent when idle |
| System foot button opens drawer; PrintStatus-as-root introduces no print-monitoring regression | SC-5 | Interim-hub wiring + regression smoke | On-device smoke: tap System → drawer; drive a print, confirm monitoring unaffected |
| Land-on-root AND sub-nav-reset after recovery Splash (owner-accepted regression, FIX-3) | D-02 / FIX-3 | Reconnect behavior is runtime-only | Force a reconnect blip on flox while deep in a Calibration routine / FineTune group; confirm return to WaterfallHome (not prior drill-down) AND sub-nav reset to hub — both expected & accepted |

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or a Wave 0 dependency (or are justified Manual-Only above)
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 180s (host)
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
