---
phase: 3
slug: design-system-theming-foundation
status: draft
nyquist_compliant: false
wave_0_complete: true
created: 2026-05-31
---

# Phase 3 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.
> Derived from RESEARCH.md § "Validation Architecture". This is an Android UI-substrate
> phase: the **system of record for perf (criterion #5) is on-device `gfxinfo framestats`**,
> and #1–#4 are validated by **manual review in the on-device gallery against the
> `docs/ui_design/` reference screens** (D-09: NO screenshot-regression tests in v1).
> A thin band of **host-side JVM unit tests** covers the logic that *can* be cheaply asserted
> (oklch→sRGB baked-table correctness, the bounded ring-buffer holder, token-delta serialization).

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework (host unit)** | JUnit4 + kotlin.test (`testImplementation`), Robolectric only if a token needs `Color`/`Context` |
| **Framework (perf, system of record)** | `:macrobenchmark` module + `bench/*` scenes → `gfxinfo framestats` parser (Phase-1 methodology, D-10) |
| **Config file** | `app/build.gradle.kts` (unit test sourceSet); existing `:macrobenchmark` module |
| **Quick run command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| **Full suite command** | `cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest :app:verifyMinSdk --no-daemon"` + on-device gallery review |
| **Estimated runtime** | ~30–60s unit; perf proof is a manual on-device run on `flox` |

> Builds run Windows-side via `E:\Android\gw.bat` (`./gradlew` does NOT run from WSL — see CLAUDE.md). Exit code is authoritative; pipe through `tr -d '\r'`.

---

## Sampling Rate

- **After every task commit:** Run `:app:testDebugUnitTest` (only meaningful for tasks with host-testable logic).
- **After every plan wave:** Run the full unit suite + `:app:verifyMinSdk` (the floor must stay minSdk 23 — every new dep is a floor risk).
- **Before `/gsd-verify-work`:** Unit suite green; the on-device gallery installs and renders; the **gfxinfo perf proof for criterion #5 captured on real `flox` tablet**.
- **Max feedback latency:** ~60s for unit; perf proof is a deliberate manual gate, not continuous.

---

## Per-Task Verification Map

> Mapped against the final plan/task IDs. The rows below mark the load-bearing,
> host-testable seams identified in research; most visual/layout tasks are **manual-only**
> (see that section) by design (D-09).

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 03-01-T2 | 03-01 | 1 | THEME-01 | T-03-01 | N/A (local-only UI) | unit | `:app:testDebugUnitTest --tests *BakedTokenTableTest` | ❌ W0 | ⬜ pending |
| 03-01-T2 | 03-01 | 1 | THEME-01/02 | T-03-01 | N/A | unit | `:app:testDebugUnitTest --tests *TokenDeltaSerializationTest` | ❌ W0 | ⬜ pending |
| 03-01-T2 | 03-01 | 1 | THEME-02 | — | N/A | unit | `:app:testDebugUnitTest --tests *FontScaleTest` | ❌ W0 | ⬜ pending |
| 03-01-T2 | 03-01 | 1 | THEME-01/02 | T-03-01 | Fail-safe on corrupt persisted theme (D-02) | unit | `:app:testDebugUnitTest --tests *ThemePrefsFallbackTest` | ❌ W0 | ⬜ pending |
| 03-02-T2 | 03-02 | 2 | PRIM-04/STATE | T-03-05 | N/A | unit | `:app:testDebugUnitTest --tests *RingBufferHolderTest` | ❌ W0 | ⬜ pending |
| 03-05-T2 | 03-05 | 3 | THEME-01 | T-03-05 | Graph input sanitize + downsample cap (Pitfall 4) | unit | `:app:testDebugUnitTest --tests *GraphDownsampleTest` | ❌ W0 | ⬜ pending |
| 03-07-T2 | 03-07 | — | (criterion #5) | T-03-05 | N/A | perf/manual (device checkpoint, autonomous:false) | `dumpsys gfxinfo works.mees.dinghy framestats` on `flox` → `tools/gfxinfo-parser/parse_framestats.py` | ✅ (Phase-1 harness) | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `app/src/test/.../theme/BakedTokenTableTest.kt` — assert the checked-in oklch→sRGB baked sRGB values match the committed conversion-script output for the canonical tokens (accent/heat/go/stop/bg) within tolerance; guards against silent table drift from `hifi.css`. (owned by 03-01 Task 2)
- [ ] `app/src/test/.../theme/TokenDeltaSerializationTest.kt` — round-trip a custom theme (base + token deltas only, D-02) through DataStore-Preferences serialization. (owned by 03-01 Task 2)
- [ ] `app/src/test/.../theme/FontScaleTest.kt` — `fsSp(base, fs)` math + DataStore S/M/L round-trip (D-04/THEME-02). (owned by 03-01 Task 2)
- [ ] `app/src/test/.../theme/ThemePrefsFallbackTest.kt` — corrupt/partial persisted theme fails safe (invalid base→Dark, invalid fs→M, partial delta map keeps-valid/drops-junk, garbage ARGB→drop-that-override) → fully-usable theme, no throw (D-02). (owned by 03-01 Task 2)
- [ ] `app/src/test/.../render/RingBufferHolderTest.kt` — bounded ring-buffer holder (D-12): capacity, eviction, snapshot stability under concurrent push. (owned by 03-02 Task 2)
- [ ] `app/src/test/.../render/GraphDownsampleTest.kt` — graph input sanitize + downsample cap (pure helper, D-11/Pitfall 4): rendered vertices ≤ pixel width, NaN/Infinity filtered, empty→empty, constant series intact. (owned by 03-05 Task 2)
- [ ] No new test framework needed — JUnit4 already present; add Robolectric ONLY if a `Color` assertion needs it (prefer pure `Long`/`Int` sRGB assertions to keep it host-pure).

*Reuse the existing `:macrobenchmark` + `bench/*` harness for the perf proof — do NOT build a new perf rig. (perf proof owned by 03-07 Task 2)*

---

## Manual-Only Verifications

> By design (D-07/D-09): the real risks here (Adreno fill-rate perf, appearance on the actual panel,
> grid-line alignment, theme-remap-across-both-toolkits) are precisely what host tests CANNOT catch.

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Theme is a token remap across BOTH toolkits (dark→light / custom recolors the Compose surfaces AND the Views Canvas graph) | THEME-01, UI-01 | Cross-toolkit visual repaint can't be asserted host-side | Install debug APK on `flox`; in gallery flip dark/light/custom; confirm the line graph recolors with the rest |
| Focus/Field/Gutter renders correctly portrait (stacked) + landscape (Focus\|Field 50/50 + full-width gutter on same column lines); sacred squares render square; ratio-only sizing | UI-01 | Visual fidelity vs `docs/ui_design/` reference screens | Rotate device in gallery; compare against `docs/ui_design/images/*.png`; verify gutter column-line alignment (the A4 risk) |
| Outline-led controls + button-intent colors; ≥64px touch targets | UI-02 | Touch-ergonomics + appearance | Eyeball + finger-test on `flox` |
| Confirm guard (PRIM-03), single-setting scrubber/stepper (PRIM-01, keyboard-free), severity toast (PRIM-04) | PRIM-01/03/04 | Interaction + appearance | Drive each from the gallery; confirm no alphanumeric keyboard appears for the scrubber |
| S/M/L `--fs` scales type app-wide; OS fontScale does NOT double-apply | THEME-02 | Requires OS-level fontScale interaction | Set device fontScale to max, toggle S/M/L in gallery, confirm only `--fs` governs size |
| **Criterion #5:** ring + line-graph draw the bounded buffer at ~2–4 Hz WITHOUT jank (static glow only, no continuous animation) | criterion #5 | **On-device GPU perf is the whole point — host emulators lie about Adreno** | Drive ring/graph from the deterministic 2–4 Hz synthetic feed; capture `gfxinfo framestats` p95 on real `flox`; assert no jank against Phase-1 budget ⚠️ *RE-SCOPED 2026-05-31 — see note below* |

> **Criterion #5 budget re-scoped (2026-05-31):** "assert no jank against Phase-1 budget" above used the
> Phase-1 `p50 ≪ 16.6 ms` / `~42 ms` floors, which were measured on a tiny `dp(260)` graph and were INVALID
> when generalized to a full-screen redraw on flox (~24 ms of every frame is the unavoidable Adreno-320
> full-window composite floor — physics, not jank). Criterion #5 closed **PASS** on a two-part gate:
> (1) liveness — allocation-free, no animation loop, ZERO frozen frames; (2) sparse-redraw latency p95 ≤
> ~66 ms (derived from the composite floor, not reverse-fit; met at 50.1 ms). Full rationale + mandatory
> Phase-6 re-validation + the three re-open conditions: `03-PERF-RESULTS.md` § FINAL VERDICT, and the
> dated Addendum in `docs/adr/0001-ui-toolkit-decision.md`.

---

## Validation Sign-Off

- [ ] All host-testable seams (baked token table, ring-buffer holder, token-delta serialization) have unit tests
- [ ] Sampling continuity: visual/perf tasks explicitly marked manual-only (not silently un-verified)
- [ ] Wave 0 covers the three unit-test files above
- [ ] `:app:verifyMinSdk` passes (floor held at minSdk 23 after any new dep, e.g. DataStore)
- [ ] gfxinfo perf proof for criterion #5 captured on real `flox` tablet
- [ ] No watch-mode flags
- [ ] `nyquist_compliant: true` set in frontmatter once the map is complete

**Approval:** pending
