---
phase: 08-macros-console-functional-core-complete
verified: 2026-06-02T20:00:00Z
status: human_needed
score: 3/3
overrides_applied: 0
human_verification:
  - test: "Confirm macro param popup shows correct params when opened immediately after a cold connect (before configfile bodies have loaded)"
    expected: "Either a loading state is shown, OR params populate reactively once macroBodies arrives — NOT silently running the macro with no params"
    why_human: "WR-03 (code review): MacroExecutionPopup.values is remember(macro.name) seeded at tap time. If the LaunchedEffect that feeds macroBodies into MacroHolder has not yet fired, MacroVm.params is empty at tap time. The test requires opening the popup within ~1s of first connect to reproduce the cold-connect window. Grep cannot determine timing behavior."
---

# Phase 8: Macros & Console Functional-Core Complete — Verification Report

**Phase Goal:** The escape hatches that prevent the user from ever needing SSH or a browser for anything unusual — run gcode_macros with parameter entry, and send/inspect raw G-code with severity-colored history. Closes the functional-core-complete gate.
**Verified:** 2026-06-02T20:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | User can list and run gcode_macros across System list, Bookmarked launcher, and Execution popup with auto-detected param entry and Execute/Cancel | VERIFIED | `MacroHolder.buildState()` combines capabilities + prefs + parsed bodies; `SystemMacrosScreen` shows `state.visibleMacros` (underscore-filtered), `BookmarkedMacrosScreen` shows `state.bookmarkedMacros` (ONLY bookmarked), `MacroExecutionPopup` renders `MacroParam` fields per detected type (NumpadPage for numeric, TokenTextField for string), `execute()` dispatches via `MacroInvocation.buildTyped → PrinterCommands.scriptParams → dispatcher.dispatch` |
| 2 | Read-only command/response history with severity coloring, backfilled from gcode_store and updated live via notify_gcode_response, bounded scrollback, opt-in noise filters | VERIFIED | `ConsoleSeverity.classify()` implements the verbatim Mainsail prefix map; `ConsoleFilters.apply()` has the three Mainsail regexes and never mutates input; `ConsoleHolder` collects both `gcodeResponses` and `consoleBackfill` as RAW (no filter); `ConsoleScreen` applies filters at render only (D-04); `ConsoleListView` is RecyclerView-in-AndroidView with `clipToBounds`, `itemAnimator = null`, incremental `notifyItemInserted`; NO TextField/keyboard anywhere in ConsoleScreen (D-01) |
| 3 | On reconnect, Console history backfills correctly from server.gcode_store — disconnect-window lines recovered, not silently dropped | VERIFIED | `gcode_store` read is inside `runHandshake()` at `MoonrakerSession.kt:354-362` (inherits reconnect AND `notify_klippy_ready` reruns — Pitfall 4 closed); REPLACE semantics: `store.setGcodeBackfill(lines)` → `_consoleBackfill.value = lines` (StateFlow) → `ConsoleHolder.replaceRaw()` → `ring.replaceAll(snapshot)` → `_state.value = ring.snapshot()`; on-device UAT check 4 PASSED on real flox + live Ender 5 Plus |

**Score:** 3/3 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt` | Pure Mainsail-regex macro-body param parser (D-09) | VERIFIED | Contains verbatim `PARAM_REGEX` (`params\.([A-Za-z_0-9]+)`) and `PARAM_IN_REGEX`; `parseMacroParams` is total (never throws) |
| `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt` | REJECT-on-forbidden-char sanitizer + gcode-line assembly (V5 security gate, D-10) | VERIFIED | `rejectForbidden()` checks `\n \r \t ; "` + 0x00-0x1F + 0x7F BEFORE concatenation; throws `MacroParamRejected`, never escapes/strips; code review confirmed: "solid — validated BEFORE concatenation, never escapes/strips" |
| `app/src/main/java/works/mees/dinghy/ui/macros/MacroPrefs.kt` | DataStore for bookmarks + revealHidden (MACRO-03) | VERIFIED | Uses injected DataStore (`macros.preferences_pb`, own file not shared with connection/theme); `revealHidden` defaults to `false` (underscore-default-hide) |
| `app/src/main/java/works/mees/dinghy/ui/console/ConsoleSeverity.kt` | Pure prefix→tier classifier (D-02) | VERIFIED | `classify()` is total: `!! `→ERROR, `// action:`→ACTION, `// debug:`→DEBUG, `// `→WARNING, else→NORMAL; absent/empty/garbage→NORMAL, never throws |
| `app/src/main/java/works/mees/dinghy/ui/console/ConsoleFilters.kt` | 3 verbatim Mainsail filter regexes + pure apply (D-03/D-04) | VERIFIED | `HIDE_TEMPERATURES = Regex("""^(?:ok\s+)?(B|C|T\d*):""")`, `HIDE_TIMELAPSE` (6 rules), `HIDE_PROMPT_COMMANDS = Regex("""^(?:// )?action:prompt""")` present verbatim; `apply()` returns new list, input never mutated |
| `app/src/main/java/works/mees/dinghy/state/ConsoleScrollback.kt` | Object-typed bounded ring for ConsoleLine (NOT RingBuffer/FloatArray) | VERIFIED | `ArrayDeque<ConsoleLine>` with `DEFAULT_CAPACITY = 1000`; does NOT import or extend `render/RingBuffer`; provides `push`, `replaceAll`, `snapshot()` |
| `app/src/main/java/works/mees/dinghy/ui/console/GcodeStoreParse.kt` | Pure gcode_store JSON → List<ConsoleLine> walker (CONS-02) | VERIFIED | `parseGcodeStore()` wraps in `runCatching`; per-element `mapNotNull` skips missing `message`; derives severity via `ConsoleSeverity.classify(message)`; raw prefix preserved in `rawMessage` (D-04) |
| `app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt` | `setGcodeBackfill` + `consoleBackfill` + `setMacroBodies` + `macroBodies` seams | VERIFIED | All four present at lines 82-202 |
| `app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt` | `gcodeStore` CommandSpec registered and in `all` list | VERIFIED | `gcodeStore` spec at line 119; in `all` list at line 342 |
| `app/src/main/java/works/mees/dinghy/net/JsonRpc.kt` | `GCODE_STORE = "server.gcode_store"` const | VERIFIED | Line 116 |
| `app/src/main/java/works/mees/dinghy/ui/console/ConsoleHolder.kt` | Collects `gcodeResponses` (live) + `consoleBackfill` (replace) into raw StateFlow<List<ConsoleLine>> | VERIFIED | Two collectors in init (UNDISPATCHED); NO ConsoleFilters import (D-04 raw-store confirmed) |
| `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` | Field-only ScreenScaffold + 3 filter toggles + Back, NO keyboard | VERIFIED | `focus = null`; `ConsoleFilters.apply()` at render only; NO TextField/BasicTextField/send anywhere; "Console is quiet" empty-state copy present verbatim; all colors via LocalTokens (zero raw Color literals) |
| `app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt` | combine(Capabilities.macros, MacroPrefs, macroBodies) → StateFlow<MacroScreensState> | VERIFIED | `combine(capabilities, bookmarks, revealHidden, _macroBodies)` → `buildState()`; underscore-hide via `filter { reveal || !it.isHidden }`; bookmarked via `filter { it.isBookmarked }`; `unavailable` flag on empty capabilities |
| `app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt` | Param-entry popup = action gate; Execute→sanitized dispatch (D-08/D-09/D-10) | VERIFIED | `execute()`: `MacroInvocation.buildTyped()` → catch `MacroParamRejected` → `PrinterCommands.scriptParams()` → `dispatcher.dispatch()`; NO ConfirmGuard; per-macro busy key `macro_<name>`; NumpadPage for numeric params; TokenTextField for string params |
| `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` | Bookmarked launcher — ONLY user-selected macros + Manage control + empty-state | VERIFIED | Renders `state.bookmarkedMacros`; "No macros pinned" copy present; MacrosUnavailable when `state.unavailable` |
| `app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt` | ALL macros check/select + underscore-default-hide + reveal toggle (D-06/MACRO-03) | VERIFIED | Renders `state.visibleMacros`; "Show hidden" toggle calls `onSetRevealHidden`; per-row `toggleBookmark` via `onToggleBookmark`; underscore-intro copy present |
| `app/src/main/java/works/mees/dinghy/DinghyApp.kt` | `macros.preferences_pb` DataStore created + passed into AppContainer (B1) | VERIFIED | `PreferenceDataStoreFactory.create(..., "macros.preferences_pb")` at line 52; passed as `macroDataStore` into AppContainer ctor at line 60 |
| `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` | `macroDataStore` ctor param + exposed `MacroPrefs` instance (B1) | VERIFIED | Ctor param at line 52; `val macroPrefs: MacroPrefs = MacroPrefs(macroDataStore)` at line 76 |
| `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` | `Dest` enum += `Macros, Console` | VERIFIED | Line 30: `enum class Dest { PrintStatus, Temperature, Move, Extrude, Files, Macros, Console, Settings }` |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` | Live Macros tile (Dest.Macros) + new Console tile (terminal glyph) | VERIFIED | Macros: `dest = Dest.Macros` (line 122); Console: `symbol = "terminal"` (line 125); `terminal` is unused elsewhere in DRAWER_TILES |
| `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` | `when(dest)` branches for Macros/Console + drawer-swipe suppression on both scroll-Fields | VERIFIED | `Dest.Macros` branch at line 246 (sub-navigates to System list or Bookmarked + popup overlay); `Dest.Console` branch at line 267; swipe-suppress set at line 211: `if (dest !in setOf(Dest.Files, Dest.Console, Dest.Macros))` |
| `docs/moonraker-capabilities.md` | Phase 8 section recording gcode_store shape + macro-body nesting verdict + UAT results | VERIFIED | "Phase 8 — gcode_store + macro-body shapes (probed 2026-06-02)" at line 207; "Phase 8 — on-device UAT + console-scroll perf (2026-06-02)" at line 272 with verbatim gfxinfo numbers (p95=9ms, 0 frozen) |
| `app/src/test/resources/fixtures/gcode_store_e5.json` | Real probed gcode_store snapshot | VERIFIED | File exists; referenced by `GcodeStoreParseTest` via `getResource("/fixtures/gcode_store_e5.json")` |
| `app/src/test/resources/fixtures/macro_bodies_e5.json` | Real probed macro gcode bodies | VERIFIED | File exists; referenced by `MacroParamParserTest` via `getResource("/fixtures/macro_bodies_e5.json")` |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `MacroExecutionPopup.execute()` | `PrinterCommands.scriptParams` | `MacroInvocation.buildTyped() → catch MacroParamRejected → PrinterCommands.scriptParams(gcode)` | WIRED | Raw keyboard text never reaches `scriptParams` directly; the sanitizer is always between (code review confirmed end-to-end) |
| `MacroHolder` | `Capabilities.macros + MacroPrefs + store.macroBodies` | `combine(capabilities, bookmarks, revealHidden, _macroBodies)` in `init` | WIRED | Four-way combine drives `buildState()`; `setMacroBodies()` fed from `AppShell` `LaunchedEffect(macroHolder, store)` |
| `MoonrakerSession.runHandshake()` | `store.setGcodeBackfill` | `runCatching { rpc.request(CommandRegistry.gcodeStore, ...) → store.setGcodeBackfill(parseGcodeStore(...)) }` | WIRED | Inside `runHandshake()` at line 354-362; inherits reconnect + `notify_klippy_ready` reruns |
| `MoonrakerSession.runHandshake()` | `store.setMacroBodies` | Single `configfile` query result extended to also walk `gcode_macro *` sections (no duplicate query) | WIRED | Same `cfgResult` at line 372; macro-body extraction at lines 381-390 |
| `ConsoleHolder` | `PrinterStateStore.gcodeResponses + consoleBackfill` | `collectorScope.launch(UNDISPATCHED) { gcodeResponses.collect {...} }` + `consoleBackfill.collect {...}` in `init` | WIRED | Both collectors present; NO ConsoleFilters import (D-04 confirmed) |
| `ConsoleScreen` | `ConsoleFilters.apply` | `val filtered = ConsoleFilters.apply(rawLines, hideTemps, hideTimelapse, hidePrompt)` in render | WIRED | Applied at render only; `holder.state` stores raw (D-04) |
| `DinghyApp.kt` | `AppContainer.macroDataStore` | `AppContainer(..., macroDataStore = macroDataStore)` in `onCreate` | WIRED | `macros.preferences_pb` DataStore threaded in |
| `AppShell` | `ConsoleScreen + BookmarkedMacrosScreen + MacroHolder` | `when(dest)` branches with session-owned holders | WIRED | `consoleHolder = remember(store) { ConsoleHolder(...) }`; `macroHolder = remember(store, capabilitiesFlow) { MacroHolder(...) }` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `ConsoleHolder` | `_state: StateFlow<List<ConsoleLine>>` | `gcodeResponses` SharedFlow (live) + `consoleBackfill` StateFlow (reconnect) from `PrinterStateStore` | Yes — live lines from `notify_gcode_response`; backfill from `server.gcode_store` on every (re)connect | FLOWING |
| `MacroHolder` | `_state: StateFlow<MacroScreensState>` | `Capabilities.macros` (from `printer.objects.list`), `macroPrefs.bookmarks`, `macroPrefs.revealHidden`, `_macroBodies` (from `configfile` handshake read) | Yes — capabilities re-derived every reconnect; bodies from real printer configfile query | FLOWING |
| `parseGcodeStore` | `List<ConsoleLine>` | `server.gcode_store` JSON result from Moonraker websocket | Yes — real probed shape recorded in `docs/moonraker-capabilities.md` and fixture `gcode_store_e5.json` | FLOWING |

### Behavioral Spot-Checks

Step 7b: SKIPPED for build/Gradle tasks (gradle runs Windows-side via `gw.bat`, not available from WSL bash). The orchestrator confirmed `:app:testReleaseUnitTest` GREEN (615 tests, 0 failures) and `:app:assembleRelease` BUILD SUCCESSFUL prior to verification.

### Probe Execution

No `probe-*.sh` scripts defined for Phase 8. The phase's validation gate was a `checkpoint:human-verify` (08-07 Task 3) that ran on the real device — results recorded in `docs/moonraker-capabilities.md`.

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| MACRO-01 | 08-01, 08-06, 08-07 | User can list and run printer gcode_macros | SATISFIED | `BookmarkedMacrosScreen` + `SystemMacrosScreen` + `MacroExecutionPopup`; dispatch via `CommandDispatcher`; REQUIREMENTS.md: Phase 8, Complete |
| MACRO-02 | 08-01, 08-03, 08-06, 08-07 | User can enter parameters for macros that declare them | SATISFIED | `MacroParamParser` (verbatim Mainsail regex); `MacroExecutionPopup` param fields; `MacroInvocation` assembly + security gate; REQUIREMENTS.md: Phase 8, Complete |
| MACRO-03 | 08-01, 08-03, 08-06, 08-07 | User can hide/show which macros appear | SATISFIED | `MacroPrefs` DataStore (`revealHidden` default `false`); `MacroHolder.buildState()` underscore-filter; "Show hidden" toggle in `SystemMacrosScreen`; bookmark persistence; REQUIREMENTS.md: Phase 8, Complete |
| CONS-02 | 08-01, 08-02, 08-04, 08-05, 08-07 | User sees command/response history with severity coloring, backfilled from server.gcode_store, updated live, read-only, opt-in noise filters | SATISFIED | Full stack verified end-to-end; on-device UAT 7/7 PASS; REQUIREMENTS.md: Phase 8, Complete |
| CONS-01 | N/A | User can send arbitrary G-code command | EXPLICITLY DEFERRED | REQUIREMENTS.md: "Pulled from Phase 8 (2026-06-02) — console read-only, text-send unscheduled"; `08-CONTEXT.md` records the decision; `ConsoleScreen` has no keyboard/send — correct and intentional |

No orphaned requirements: Phase 8 claims exactly MACRO-01/02/03 and CONS-02, all four confirmed in REQUIREMENTS.md as Phase 8 / Complete. CONS-01 is explicitly deferred per REQUIREMENTS.md.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `AppShell.kt` | 185 | `val consoleBackfillFailed = false` (hardcoded) | INFO | WR-04 from code review: the `BackfillFailedNotice` UI branch in `ConsoleScreen` is unreachable dead code. Not a goal gap — the console still works and backfills correctly; the failure-notice UI just never triggers. Phase 13 (Optimization/Reliability) is the natural sink for this. |

No debt markers (TBD/FIXME/XXX) found in any Phase 8 production files. TODO/HACK/PLACEHOLDER markers: none.

Stub scan (return null / placeholder patterns): no stub patterns found in the four screen files or the production backend files. All implementations are substantive.

### Human Verification Required

### 1. Macro param popup behavior at cold connect (WR-03 cold-connect window)

**Test:** Connect to the printer cold (no prior session), immediately navigate to Macros → tap a macro that declares params (e.g. START_PRINT with BED_TEMP / EXTRUDER_TEMP) within roughly 1 second of the initial connect — before the `configfile` handshake read has had time to populate `macroBodies`.

**Expected:** Either: (a) the popup shows the correct params because `macroBodies` loaded fast enough, OR (b) the popup displays a "Loading parameters..." state and populates when the bodies arrive reactively, OR (c) the popup shows "No parameters" AND is documented as an accepted known limitation for the cold-connect window. What is NOT acceptable is silently dispatching `START_PRINT` with no params when the printer's macro requires them.

**Why human:** Code review WR-03 identified that `values = remember(macro.name) { ... macro.params ... }` captures `MacroVm.params` at tap time. If the `LaunchedEffect(macroHolder, store)` that feeds `macroBodies` into `MacroHolder` has not yet settled, `params` will be empty at tap time and the popup shows "No parameters — Execute runs this macro as-is." The popup does not re-read the holder after opening, so it cannot recover reactively once bodies arrive. The scenario requires real-device timing to reproduce (the cold-connect window is typically under a second but printer-dependent). The on-device UAT (check 5) was run after the connection was already established, so this specific cold-connect race was not exercised.

---

## Gaps Summary

No gaps. All three success criteria are implemented and verified in code. The phase goal — macro list/run/manage + read-only severity-colored Console with reconnect-resilient backfill — is achieved. The code review warnings (WR-01 holder coroutine leak, WR-02 ring-full fast-path drop, WR-04 dead backfill-failed notice) are quality/robustness findings that do not break the goal under normal operation, as stated by the code review. They are noted here for completeness and are candidates for Phase 13 (Optimization, Network Efficiency & Reliability).

The `human_needed` status is solely due to WR-03: the cold-connect-window popup behavior cannot be verified programmatically and was not covered by the on-device UAT as run. A quick manual test at cold-connect clears this.

---

_Verified: 2026-06-02T20:00:00Z_
_Verifier: Claude (gsd-verifier)_
