---
phase: 16
reviewers: [codex]
reviewed_at: 2026-06-06T05:06:59Z
plans_reviewed: [16-01-PLAN.md, 16-02-PLAN.md, 16-03-PLAN.md, 16-04-PLAN.md, 16-05-PLAN.md, 16-06-PLAN.md, 16-07-PLAN.md, 16-08-PLAN.md]
self_skipped: claude (running inside Claude Code CLI — skipped for independence)
unavailable: [gemini, coderabbit, opencode, qwen, cursor]
---

# Cross-AI Plan Review — Phase 16

> Single external reviewer (**Codex / gpt-5.x**). `claude` was skipped (self), and gemini/coderabbit/
> opencode/qwen/cursor are not installed. The orchestrator (Claude) independently **verified every
> concrete HIGH/MEDIUM claim against the live codebase** — annotations are inline below the Codex
> review. Codex went **4-for-4 on the HIGH findings** (not a Phase-11-style false positive).

## Codex Review

**Summary**
Overall, the plans are strong on architecture and validation intent: the classifier, command registry, per-temp preheat guard, writeScope persistence, and mandatory live babystep UAT are all the right pressure points. I would not ship these plans unchanged, though. There are several concrete compile/wiring gaps and one state-modeling bug around `temperature_sensor` partial diffs that the internal checker appears to have missed.

**Strengths**
- The `PrintStatusMode` classifier is correctly pure and covers the important edges: all 6 raw print states, Standby-stays-Standby, and klippy state excluded from terminal classification.
- The DAG correction where `16-02` depends on `16-03` for canonical `BABYSTEP_STEPS` is directionally right.
- Babystep semantics are well specified: nullable `currentLayer` gate, no time fallback, signed `Z_ADJUST`, `homing_origin[2]` readback, no `SAVE_CONFIG`, and live first-layer UAT.
- Spool-aware Preheat is correctly extracted as a pure `selectPreheatPath()` and avoids the dangerous "missing temp becomes 0" failure.
- `16-03` explicitly calls out `CommandRegistry.all`, which is the right dead-wiring guard.
- `16-05` correctly emphasizes `AppContainer.writeScope` and bans `rememberCoroutineScope()` for persistence writes.
- `16-08` is appropriately non-autonomous and treats live device validation as blocking.

**Concerns**
- **HIGH — `16-06` cannot wire the Standby launcher as scoped.** `PrintStatusScreen` currently only has Files/Spool/Scan callbacks; the new launcher needs Move, Temp, Extrude, Calibration, Macros, Console, and Drawer. `16-06` does not list `AppShell.kt`, so SC-1 quick actions and the Drawer flexible tile cannot be completed as written.
- **HIGH — `16-05` misses the app assembly point for the new prefs.** A new `BabystepPrefs` likely needs a new DataStore created in `DinghyApp.kt`, or an explicit decision to reuse an existing app-global DataStore. The plan lists `AppContainer.kt` but not `DinghyApp.kt`, and current app setup documents five separate preference files.
- **HIGH — `16-04` temperature-sensor modeling is unsafe under partial diffs.** A single `glanceSensorTemp` selected from the current `notify_status_update` object can flip from MCU to chamber/host when only that object appears in a partial diff. Model retained sensor state, e.g. `temperatureSensors: Map<String, TemperatureSensorState>`, then derive preferred glance sensor from the retained map.
- **HIGH — `16-06` says to reuse `PresetSelector`, but it is currently private in `TemperatureScreen.kt`.** The plan must either extract it to a shared component or make it reusable and list that file. Otherwise the fallback Preheat path is a compile blocker.
- **MEDIUM — command sidecar paths are wrong/stale.** `16-03` lists `docs/command-catalog.json` and `docs/printer-matrix.json`; current tests read `docs/commands/catalog.json` and `docs/commands/printer-matrix.json`. This can cause drift-test failure or edits to dead files.
- **MEDIUM — Terminal error lines lack a data path.** `PrintStatusScreen` does not currently receive `ConsoleHolder` or console backfill/live lines. `16-06 Task 3` needs to specify whether AppShell passes a bounded error-line projection, AppContainer exposes one, or the requirement is deferred.
- **MEDIUM — `16-06` is under-scoped for a 942-line screen rewrite.** Three broad tasks cover four modes, nav, Preheat overlay, babystep controls, terminal behavior, icons, and layout. The plan has mostly build/unit gates before on-device UAT; it needs more intermediate seams or screenshot/UI-state verification.
- **MEDIUM — babystep builder validation is ambiguous.** "Coerce to nearest member (or reject)" should be decided. For a physical Z move, prefer reject/canonicalize from the fixed set with epsilon and Locale.US formatting, with invalid-input tests.
- **MEDIUM — per-temp Preheat should also guard heater capability.** `CommandRegistry.setHeater` is currently generically named but availability-gated on `extruder`. A bed-only direct temp could still dispatch to an unavailable `heater_bed`, or be gated oddly. Add capability checks or split target-specific command availability.
- **MEDIUM — roadmap wave labels conflict with the revised DAG.** The roadmap says `16-02` is Wave 1 parallel with `16-03`, but the plan makes `16-02` Wave 2 after `16-03`. Update the phase manifest/roadmap so orchestration does not parallelize them accidentally.
- **LOW — `16-07` punts artboards despite context saying Print-Status artboards update in scope.** If image regeneration is owner-side, make it an explicit blocking/manual doc asset gate or adjust scope. Do not let it disappear as a casual follow-up.
- **LOW — RED scaffold plans should include a final "no fail(" cleanup check.** Because Wave 0 intentionally adds `fail()` bodies, later plans should verify those fail-only placeholders are gone before phase completion.

**Suggestions**
- Add `AppShell.kt` to `16-06` and define a single launcher callback shape, e.g. `onNavigate(Dest)` plus `onOpenDrawer()`.
- Revise `16-05` to either add `babystep.preferences_pb` in `DinghyApp.kt` or explicitly store `BabystepPrefs` in an existing app-global DataStore.
- Change `16-04` from one glance temp field to retained sensor state plus a pure `selectGlanceSensor()` helper with tests for partial diffs and mcu/host preference stability.
- Extract `PresetSelector` into a shared UI component before `16-06`, or add a small plan task to make it internal/public safely.
- Fix `16-03` sidecar paths to `docs/commands/catalog.json` and `docs/commands/printer-matrix.json`.
- Add a pre-`16-08` verification step: `assembleDebug`, `assembleDebugAndroidTest`, full unit tests, grep for no leftover `fail("not yet implemented`, no `rememberCoroutineScope` in babystep paths, no `proc_stats`.
- Split `16-06` or add focused UI model tests for mode-to-layout/control derivation before touching the large Compose file.

**Risk Assessment: HIGH**
The architecture is sound, but the current plan set has multiple concrete blockers: missing app assembly for prefs, missing AppShell wiring for launcher navigation, private `PresetSelector` reuse, stale docs sidecar paths, and unstable `temperature_sensor` state under partial diffs. Fixing those before execution would bring the risk down to medium.

---

## Orchestrator Verification (Claude)

Every concrete, checkable claim was verified against the live code this session:

| # | Codex claim | Severity | Verdict | Evidence |
|---|-------------|----------|---------|----------|
| 1 | `PresetSelector` is `private` in `TemperatureScreen.kt`; 16-06 reuse is a compile blocker | HIGH | ✅ **CONFIRMED** | `TemperatureScreen.kt:406` = `private fun PresetSelector(`. 16-06 `files_modified` does NOT list `TemperatureScreen.kt`. |
| 2 | Command sidecar paths in 16-03 are wrong | MEDIUM | ✅ **CONFIRMED** | Real files: `docs/commands/catalog.json` + `docs/commands/printer-matrix.json`. `CommandCatalogDriftTest.kt:145,151` reads exactly those. 16-03 references `docs/command-catalog.json` + `docs/printer-matrix.json` (wrong dir) — would edit dead files + leave the drift test RED. |
| 3 | 16-06 omits `AppShell.kt`, blocking launcher nav | HIGH | ✅ **CONFIRMED** | 16-06 `files_modified` = only `PrintStatusScreen.kt` + 2 drawables. The Standby launcher needs nav callbacks AppShell must pass. |
| 4 | 16-05 omits `DinghyApp.kt` (DataStore creation point) | HIGH | ✅ **CONFIRMED** | All prefs DataStores are created in `DinghyApp.kt` via `PreferenceDataStoreFactory.create(produceFile = preferencesDataStoreFile("<name>.preferences_pb"))` (theme/connection/macros/webcam). A new `BabystepPrefs` store needs `DinghyApp.kt` touched + the instance threaded to `AppContainer`. 16-05 lists `AppContainer.kt` but not `DinghyApp.kt`. |
| 5 | `temperature_sensor` glance state unsafe under partial diffs | HIGH | ⚠ **SOUND (architectural)** — not bit-verified, but consistent with this project's retained-state reducer model. A glance sensor derived from the current diff object can flip; retain `Map<String, ...>` and select from the retained map. |

**Net:** 4/4 HIGH findings real; the MEDIUM sidecar-path bug real; the partial-diff concern architecturally sound. This is a high-value review, not a false-positive case.

---

## Consensus Summary

Single external reviewer, so "consensus" = Codex's findings filtered through orchestrator verification.

### Agreed Strengths
- Pure `PrintStatusMode` classifier with correct edge coverage (6 states, Standby-stays-Standby, klippy≠terminal).
- Babystep semantics fully specified; `selectPreheatPath` extraction avoids the missing-temp→0 trap; `CommandRegistry.all` dead-wiring guard present; writeScope discipline enforced; 16-08 correctly blocking-human.

### Agreed Concerns (priority order for `--reviews` replan)
1. **[BLOCKER] 16-06 missing `AppShell.kt`** — launcher/quick-action nav (SC-1) and Drawer flexible-tile cannot be wired. Add `AppShell.kt` + a launcher callback shape (`onNavigate(Dest)` / `onOpenDrawer()`).
2. **[BLOCKER] 16-06 `PresetSelector` is private** — extract it to a shared component (add a small task / list `TemperatureScreen.kt`) or the Preheat fallback won't compile.
3. **[BLOCKER] 16-05 missing `DinghyApp.kt`** — add the `babystep.preferences_pb` DataStore there and thread it to `AppContainer`, mirroring theme/macros/webcam.
4. **[BLOCKER] 16-04 partial-diff sensor flip** — model retained `temperatureSensors` map + a pure `selectGlanceSensor()` with partial-diff/preference-stability tests (also closes a Wave-0 test seam cleanly).
5. **[FIX] 16-03 sidecar paths** — `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` (currently wrong → CommandCatalogDriftTest would fail).
6. **[DECIDE] 16-06 Terminal error-line data path** — specify how error lines reach PrintStatusScreen (AppShell projection / AppContainer exposure / defer).
7. **[SCOPE] 16-06 under-scoped** — consider splitting or adding UI-model unit tests for mode→layout/control derivation before touching the 942-line file.
8. **[POLISH] babystep builder validation** (reject/canonicalize from the fixed set, Locale.US, epsilon); **per-temp Preheat heater-capability guard** (don't dispatch heater_bed if unavailable); **roadmap wave-label vs DAG** reconcile; **16-07 artboards** make an explicit owner-side gate; **no-`fail(` cleanup check** before phase completion.

### Divergent Views
None — single reviewer. The only point already known to the orchestrator is the roadmap-vs-frontmatter wave-label cosmetic (frontmatter is authoritative for execution; ROADMAP labels are presentational).
