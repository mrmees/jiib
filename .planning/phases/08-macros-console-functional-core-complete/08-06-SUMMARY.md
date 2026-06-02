---
phase: 08-macros-console-functional-core-complete
plan: 06
subsystem: ui-macros
tags: [macros, MACRO-01, MACRO-02, MACRO-03, D-06, D-07, D-08, D-09, D-10, security-V5]
requires:
  - "08-03: MacroParamParser, MacroInvocation (V5 sanitizer), MacroPrefs, MacroModels"
  - "08-04: store.macroBodies seam"
  - "Capabilities.macros (derived) + hasMacroIgnoreCase"
  - "designsystem/NumpadPage, OutlinedControl, ScreenScaffold, SeverityToast, MaterialSymbol"
  - "command/CommandDispatcher (PRIM-05), PrinterCommands.scriptParams"
  - "ui/screen/TokenTextField (sanctioned themed keyboard field)"
provides:
  - "MacroHolder + MacroScreensState (combine -> StateFlow view models, MACRO-01/03)"
  - "MacroExecutionPopup (action-gate popup, sanitized dispatch, D-08/D-09/D-10)"
  - "BookmarkedMacrosScreen (D-07 launcher) + SystemMacrosScreen (D-06 manager)"
  - "MacroParam.isNumeric widget-route derivation"
affects:
  - "08-07 wires Dest.Macros/Dest.Console into AppShell + drawer tiles + MacroPrefs intents + store.macroBodies -> holder.setMacroBodies + drawer-swipe suppression on the System list"
tech-stack:
  added: []
  patterns:
    - "detached SupervisorJob + UNDISPATCHED combine collector (08-05 ConsoleHolder pattern) so a TestScope-rooted holder finishes clean"
    - "screens take reveal/bookmark intents as CALLBACKS (wired to MacroPrefs in 08-07) so MacroHolder stays at its 4-arg test contract"
    - "NumpadPage as the sole numeric-clamp owner; MacroInvocation.buildTyped between raw values and scriptParams (V5 sanitizer-in-path)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt
decisions:
  - "MacroHolder constructor matches the RED test exactly (scope, StateFlow<Capabilities>, StateFlow<Set<String>> bookmarks, StateFlow<Boolean> revealHidden) — it does NOT take a MacroPrefs object; bookmark/reveal MUTATION is exposed by the screens as callbacks the 08-07 shell wires to MacroPrefs suspend fns."
  - "Macro bodies enter the holder via setMacroBody (test seam) / setMacroBodies (production seam fed by store.macroBodies in 08-07), NOT a constructor StateFlow — keeps the test 4-arg contract."
  - "Execute uses MacroInvocation.buildTyped (numeric unquoted, string quoted+rejected) not build(Map) — same V5 sanitizer file, correct Klipper grammar for numerics."
  - "NumpadPage range = a generous -100000..100000 envelope (macro bodies declare no range); NumpadPage remains the SOLE clamp owner (S4); printer-side range limits surface as a DispatchEvent.Failure toast."
metrics:
  duration: ~25 min
  completed: 2026-06-02
---

# Phase 8 Plan 06: Macros — Three Screens & Sanitized Dispatch Summary

The three Macro screens land the MACRO-01/02/03 UI: `MacroHolder` folds `Capabilities.macros` +
bookmarks + reveal-hidden + parsed bodies into one `StateFlow<MacroScreensState>` (underscore-default-hide,
bookmark partition, capability gate); the `MacroExecutionPopup` IS the action gate with auto-detected
NumpadPage/keyboard param fields and a V5-sanitized dispatch; the Bookmarked launcher and System
manage-visibility screens complete the list/run/manage loop. This was the phase's last RED scaffold —
`MacroHolderTest` is GREEN and the **full 615-test `:app:testReleaseUnitTest` suite compiles and passes
with 0 failures**.

## What Was Built

- **MacroHolder** (Task 1) — `combine(capabilities, bookmarks, revealHidden, macroBodies)` →
  `MacroScreensState{ macros, visibleMacros, bookmarkedMacros, revealHidden, unavailable }`.
  `visibleMacros` applies the underscore-default-hide filter unless `revealHidden` (MACRO-03);
  `bookmarkedMacros` is only the user-pinned names matched case-insensitively (Moonraker lowercases
  macro names); empty `Capabilities.macros` sets `unavailable` (capability gate, no dead tiles); each
  `MacroVm.params` is parsed from its probed body. **Turns `MacroHolderTest` GREEN (5/5).**
- **MacroExecutionPopup** (Task 2) — full-screen overlay that IS the deliberate action gate (no
  ConfirmGuard, D-08). Numeric params → `NumpadPage` (keyboard-free, sole clamp owner S4); string/null
  params → `TokenTextField` system keyboard (the ONE sanctioned alpha site, D-10). Execute →
  `MacroInvocation.buildTyped` (V5 reject-on-forbidden-char sanitizer, T-08-06-T1) →
  `PrinterCommands.scriptParams` → `dispatcher.dispatch(key="macro_<name>", GCODE_SCRIPT)`. Per-macro
  busy key disables Execute on in-flight (PRIM-05). A local `MacroParamRejected` or the printer's `!!`
  rejection surfaces as a `SeverityToast` (redacted `DispatchEvent.Failure`, V7).
- **BookmarkedMacrosScreen + SystemMacrosScreen** (Task 3) — the launcher (adaptive grid of pinned-only
  tiles → popup; `Manage macros` + green Back; `No macros pinned` empty state) and the manager
  (scrollable ALL-visible-macros list, per-row check/select toggle accent-when-pinned, `Show hidden`
  gutter toggle, underscore-helpers intro copy, green Back). Both carry the macros-unavailable copy.

## How It Wired Together

`Execute → MacroInvocation.buildTyped(name, [(key,value,isNumeric)…]) → PrinterCommands.scriptParams(gcode)
→ dispatcher.dispatch(GCODE_SCRIPT)`. The raw `values` map never reaches `scriptParams` directly — the
sanitizer is always between (grep-verified). `MacroHolder` combines the already-derived
`Capabilities.macros` with the two prefs StateFlows + a locally-owned bodies map.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] MacroHolder combine collector left the TestScope hung (UncompletedCoroutinesError)**
- **Found during:** Task 1 (first MacroHolderTest run — assertions passed but the test body never finished)
- **Issue:** A plain `combine(...).launchIn(scope)` over four never-completing StateFlows kept an active
  child job under the `runTest` TestScope, so every test failed with `UncompletedCoroutinesError` despite
  correct assertions.
- **Fix:** Adopted the proven 08-05 `ConsoleHolder` pattern — run the combine collector in a detached
  `SupervisorJob` child scope (not a structured child of `scope`), `start = UNDISPATCHED` so the first
  combined value computes synchronously, and cancel the collector via `scope`'s `invokeOnCompletion`.
- **Files modified:** `app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt`
- **Commit:** 50e04d1

### Contract Adaptations (not deviations — reconciling the RED test with the plan prose)

- The plan described `MacroHolder(scope, capabilities, prefs: MacroPrefs, macroBodies: StateFlow)` and
  `toggleBookmark`/`setRevealHidden` intents on the holder. The **RED test** (08-01, authoritative) fixes
  the constructor as `(scope, StateFlow<Capabilities>, StateFlow<Set<String>>, StateFlow<Boolean>)` and
  exercises `setMacroBody(name, body)`. I matched the test exactly: bodies enter via
  `setMacroBody`/`setMacroBodies` (the production seam 08-07 feeds from `store.macroBodies`), and
  bookmark/reveal **mutation** is exposed by the screens as `onToggleBookmark` / `onSetRevealHidden`
  callbacks that 08-07 wires to the `MacroPrefs` suspend functions. This keeps the holder pure and
  host-testable without a DataStore.

## Known Stubs

The bookmark/reveal mutation callbacks (`onToggleBookmark`, `onSetRevealHidden`) and the popup's
`onRunMacro`/`onManage`/`onBack` navigation are intentionally left as caller-supplied parameters — they
are wired to `MacroPrefs` and the shell back-stack in **08-07** (the nav-wiring plan, per this plan's
explicit out-of-scope note "Nav wiring is 08-07"). `holder.setMacroBodies(store.macroBodies)` is
likewise wired in 08-07. These are not dead stubs: the screens are fully functional once 08-07 supplies
the lambdas, and the plan scopes that wiring to the next plan.

## Verification Evidence

- `:app:testReleaseUnitTest --tests *MacroHolderTest*` → BUILD SUCCESSFUL (5/5 GREEN).
- **Full `:app:testReleaseUnitTest` → BUILD SUCCESSFUL, 615 tests, 0 failures** — the entire phase-8
  test source set (MacroHolderTest, MacroInvocationTest, MacroParamParserTest, MacroPrefsTest,
  ConsoleHolderTest, ConsoleFiltersTest, ConsoleSeverityTest, GcodeStoreParseTest, ConsoleScrollbackTest)
  compiles and passes. Last RED scaffold closed.
- `:app:assembleRelease` → BUILD SUCCESSFUL.
- Token-purity grep over all four new files → zero raw `Color(...)` / named-color literals
  (only `Color.Transparent`, the FilesScreen-sanctioned API constant).
- Sanitizer-in-path grep → Execute routes raw `values` through `MacroInvocation.buildTyped` before
  `scriptParams`; no raw param string reaches `scriptParams` directly (T-08-06-T1 mitigated).

## Threat Model Disposition

| Threat ID | Disposition | Evidence |
|-----------|-------------|----------|
| T-08-06-T1 (raw param → gcode injection) | mitigated | Execute → `MacroInvocation.buildTyped` (reject-on-forbidden-char) → `scriptParams`; sanitizer always between (grep-verified) |
| T-08-06-T2 (numeric out-of-range) | mitigated | `NumpadPage` `range` is the sole clamp owner (S4); popup/`MacroInvocation` add no clamp |
| T-08-06-I (rejection toast leaks secret) | mitigated | `DispatchEvent.Failure` is redacted by `CommandDispatcher` (method/key only, never API key, V7) |
| T-08-06-SC (package install) | accepted | zero new packages |

## Self-Check: PASSED

- Created files all FOUND: MacroHolder.kt, MacroExecutionPopup.kt, BookmarkedMacrosScreen.kt, SystemMacrosScreen.kt, MacroModels.kt (modified).
- Commits all FOUND: 50e04d1, 7487fef, 255e7e9.
- Full `:app:testReleaseUnitTest` GREEN (615 tests, 0 failures); `:app:assembleRelease` SUCCESSFUL.
