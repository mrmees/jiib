---
phase: 08-macros-console-functional-core-complete
reviewed: 2026-06-02T00:00:00Z
depth: standard
files_reviewed: 26
files_reviewed_list:
  - app/src/main/java/works/mees/dinghy/DinghyApp.kt
  - app/src/main/java/works/mees/dinghy/command/CommandRegistry.kt
  - app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt
  - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
  - app/src/main/java/works/mees/dinghy/net/JsonRpc.kt
  - app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt
  - app/src/main/java/works/mees/dinghy/state/ConsoleScrollback.kt
  - app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleFilters.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleLine.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleRowsAdapter.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleSeverity.kt
  - app/src/main/java/works/mees/dinghy/ui/console/GcodeStoreParse.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroModels.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroParamParser.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/MacroPrefs.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
findings:
  critical: 0
  warning: 4
  info: 5
  total: 9
status: issues_found
---

# Phase 8: Code Review Report

**Reviewed:** 2026-06-02
**Depth:** standard
**Files Reviewed:** 26
**Status:** issues_found

## Summary

Reviewed the 26 production Kotlin files added/changed in phase 08 (macros + console functional core).
The security-critical gate (`MacroInvocation`) is **solid** — a REJECT-on-forbidden-char policy that
covers exactly the specified set (`\r \n ; \t "` + ASCII 0x00–0x1F + 0x7F), validates BEFORE
concatenation, never escapes/strips, and is correctly the sole code path between user free-text and
`PrinterCommands.scriptParams` (verified end-to-end through `MacroExecutionPopup.execute()`). The
numeric unquoted path is safe because its values only ever come from `NumpadPage`/`formatNumeric`
(digits/`.`/`-`) and keys come from the regex-constrained parsed macro name set. The untrusted-JSON
parsers (`parseGcodeStore`, `MacroParamParser`, `MoonrakerSession.gcodeBodyOrNull`) are all total /
skip-bad-field tolerant as required. No Critical findings.

The defects are quality/robustness issues, not correctness-breaking under normal operation:

1. **WARNING** — Console+macro holders leak collector coroutines on every reconnect (store re-key
   does not cancel the previous holder's collectors). Consistent with the pre-existing Phase-5 holder
   pattern, but Phase 8 doubles the leak surface and it compounds per reconnect.
2. **WARNING** — `ConsoleListView` falls off its incremental-append fast path permanently once the
   1000-line ring fills, forcing a full `notifyDataSetChanged()` on every subsequent live line —
   directly relevant to the Adreno-320 scroll floor.
3. **WARNING** — Popup param fields are captured once at tap time; if macro bodies load after the
   popup opens, a parametered macro shows "No parameters" and silently runs with none.
4. **WARNING** — `consoleBackfillFailed` is hardcoded `false` in `AppShell`, so the
   `BackfillFailedNotice` UI is dead code and the user is never told history failed to load.

## Warnings

### WR-01: Console/Macro holders leak collector coroutines on every reconnect

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:148-175`
(with `app/src/main/java/works/mees/dinghy/ui/console/ConsoleHolder.kt:61-66` and
`app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt:75-79`)

**Issue:** `consoleHolder = remember(store) { ConsoleHolder(scope = scope, ...) }` and
`macroHolder = remember(store, capabilitiesFlow) { MacroHolder(scope = scope, ...) }` build a NEW
holder whenever the spine rebuilds (every reconnect re-keys `store`). Each holder's collectors live on
a `collectorJob` that is cancelled only via `scope.coroutineContext[Job]?.invokeOnCompletion { ... }`
— i.e. when the *composition* `rememberCoroutineScope()` dies, NOT when the holder instance is
discarded by `remember`. So every reconnect orphans the previous `ConsoleHolder`'s two collectors and
the previous `MacroHolder`'s combine collector; they keep collecting the dead session's flows until
the whole shell leaves composition. On a flapping connection this accumulates indefinitely. (Note:
the Phase-5 `TemperatureHolder`/`MoveHolder`/`ExtrudeHolder` `scope.launch` directly and share the
same defect — this is a pre-existing pattern, but Phase 8 adds two more leaking holders.)

**Fix:** Cancel the prior holder when `remember(store)` swaps it. Either give the holders a
`close()`/`cancel()` and call it from a `DisposableEffect(store)`, or build each holder on a child
scope derived per-key and cancel that scope on dispose. Example for the holder side:

```kotlin
// ConsoleHolder
fun cancel() { collectorJob.cancel() }
```
```kotlin
// AppShell
val consoleHolder = remember(store) { ConsoleHolder(scope, store.gcodeResponses, store.consoleBackfill) }
DisposableEffect(consoleHolder) { onDispose { consoleHolder.cancel() } }
```

### WR-02: Console scrollback drops its incremental-append fast path once the ring fills (1000 lines)

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt:81-94`

**Issue:** The single-append detection is `lines.size == oldCount + 1 && adapter.matches(lines.subList(0, oldCount))`.
Both the holder's `ConsoleScrollback` ring and the adapter cap at `DEFAULT_CAPACITY = 1000`. Once both
are full, every new live line produces a holder snapshot of size 1000 while `oldCount` is also 1000, so
`lines.size == oldCount + 1` is never true. The code then falls to the `else if (!adapter.matches(lines))`
branch → `submitRows()` → `notifyDataSetChanged()` on EVERY new line. That defeats the whole point of the
S2 incremental hot path exactly when the console is busiest (a long print streaming responses) — the worst
case for the Adreno-320 floor the project is measured against, and a likely source of visible scroll jank
/ stick-to-bottom flicker.

**Fix:** Detect the "appended-one-then-evicted-one" case too: when `lines.size == oldCount` (both at cap)
and `lines.subList(0, oldCount - 1) == adapter.items.subList(1, oldCount)`, treat it as an append+evict and
drive `appendLine()` (which already does the incremental `notifyItemInserted` + `notifyItemRangeRemoved`).
Verify against the live-flow gfxinfo gate (08-07 step 7) with a pre-filled 1000-line scrollback, not an
empty one.

### WR-03: Macro popup param fields are frozen at tap time — late-arriving bodies show "No parameters"

**File:** `app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt:100-102` and
`app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:261,281-289`

**Issue:** `macroPopupFor` captures the `MacroVm` (including its `params`) at the instant the tile is
tapped. The `values` map is `remember(macro.name) { ... macro.params ... }`. If the `configfile` macro-body
read has not landed yet (the `setMacroBodies` `LaunchedEffect` is async and runs on connect), the captured
`MacroVm.params` is empty, so the popup renders "No parameters. Execute runs this macro as-is." and Execute
dispatches the bare macro name with NO params — for a macro that actually declares them. The popup does not
re-read the holder after open, so it never recovers even once bodies arrive. This is a silent functional
miss for MACRO-02 in the cold-connect window.

**Fix:** Either (a) pass the macro NAME to the popup and have it read the live `MacroVm` from the holder's
`state` (so params update reactively), or (b) gate the popup open / show a "loading parameters" state until
`macroBodies` for that macro is non-empty. Re-seed `values` when `macro.params` changes
(`remember(macro.name, macro.params)`), not on name alone.

### WR-04: Backfill-failed notice is dead — `consoleBackfillFailed` is hardcoded false

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:184-185` and
`app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt:92-95,237-257`

**Issue:** `val consoleBackfillFailed = false` with the inline comment "No dedicated store flag yet;
default false." `ConsoleScreen` accepts `backfillFailed` and renders `BackfillFailedNotice` when true, but
because the flag is always false that branch is unreachable. The UI-SPEC behavior — telling the user
"History unavailable... live responses will still appear" when `server.gcode_store` failed — is never
delivered. `MoonrakerSession` wraps the `gcode_store` read in `runCatching` and silently leaves the
backfill at its empty default (`MoonrakerSession.kt:354-363`), so a real backfill failure is
indistinguishable from a genuinely quiet console — the user sees the "Console is quiet" empty state
instead of the failure notice.

**Fix:** Thread a real signal: add a `consoleBackfillFailed: StateFlow<Boolean>` (or fold a status enum)
to `PrinterStateStore`, set it in the `gcode_store` `runCatching`'s failure branch, and collect it in
`AppShell` instead of the literal. Either wire it or delete the dead `backfillFailed` param + notice
composable so the dead branch isn't mistaken for working behavior.

## Info

### IN-01: `ConsoleRowsAdapter.lastOrNull()` is unused dead code

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleRowsAdapter.kt:98`

**Issue:** `lastOrNull()` is public on the adapter but never called — `ConsoleListView` does its
append-detection via `matches(subList)`, not `lastOrNull()`. Dead API surface.

**Fix:** Remove `lastOrNull()`, or use it in the append-detection path if that reads cleaner.

### IN-02: `setHasFixedSize(false)` on a uniform-row console list

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt:65`

**Issue:** Console rows are single-line, fixed-padding `TextView`s of effectively uniform height.
`setHasFixedSize(false)` forfeits a cheap RecyclerView layout optimization on the exact hot scroll
surface the Adreno-320 floor cares about. (Perf is out of v1 scope, hence Info, but this is a one-line
correctness-adjacent hint for the floor.)

**Fix:** `setHasFixedSize(true)` if row height never depends on adapter content (it does not here).

### IN-03: `ConsoleRowView` hardcodes `textSize = 14f` outside the `--fs` token system

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleRowsAdapter.kt:127`

**Issue:** Every Compose surface in this phase routes type size through `fsSp(..., t.fs)` (the S/M/L
text-size LAW). The Views-side console row uses a raw `14f` sp, so the console scrollback does not
respond to the global text-size setting like the rest of the app. Theme color IS bridged via the
palette, but size is not. THEMING.md treats `--fs` as part of the token contract.

**Fix:** Bridge the resolved `fs` multiplier into `ConsoleRowPalette` (or a sibling param) and apply it
to `textSize`, mirroring how the color tokens are bridged.

### IN-04: `MoonrakerSession` imports `JsonPrimitive` walkers but the macro/extruder walk is duplicated null-safe boilerplate

**File:** `app/src/main/java/works/mees/dinghy/net/MoonrakerSession.kt:417-435`

**Issue:** `objectOrNull` / `floatOrNullAt` / `gcodeBodyOrNull` re-implement the same null-safe JSON
walking that `parseGcodeStore` and other call sites also do ad hoc. Minor duplication; not a defect.
Consider consolidating the wire-walk helpers into one shared util to keep the "bad field skipped, never
fatal" discipline in one place.

**Fix:** Optional — extract shared `JsonObject` null-safe accessors used by both the session and the
console/macro parsers.

### IN-05: `MacroPrefs.bookmarks` stores macro names case-sensitively but lookup is case-insensitive

**File:** `app/src/main/java/works/mees/dinghy/ui/macros/MacroPrefs.kt:49-68` and
`app/src/main/java/works/mees/dinghy/ui/macros/MacroHolder.kt:125`

**Issue:** `addBookmark`/`toggleBookmark` persist the name verbatim (e.g. tile passes `macro.name`,
which is the lowercased Moonraker object name). `MacroHolder.buildState` matches bookmarks with
`equals(..., ignoreCase = true)`. This is correct today because discovered names are consistently
lowercased, but `toggleBookmark`'s membership test (`name in current`) is case-SENSITIVE while the
holder's match is case-INSENSITIVE — if a name ever differs in case between store and discovery, toggle
could add a duplicate-cased entry that the holder still shows as bookmarked, making the unpin appear to
not work. Low risk given the lowercase invariant.

**Fix:** Normalize bookmark names to a canonical case on write (e.g. `name.lowercase()`), or make the
`toggleBookmark` membership test case-insensitive to match the holder.

---

## Narrative Findings (AI reviewer)

All findings above are narrative (direct adversarial code review). No `<structural_findings>` block was
provided for this review.

**Security gate verdict (load-bearing, block_on:high):** PASS.
- `MacroInvocation.rejectForbidden` (`MacroInvocation.kt:79-92`) rejects on `\n \r \t ; "` and all
  `0x00..0x1F` + `0x7F` before any concatenation, throwing `MacroParamRejected`; it never escapes,
  strips, or truncates. Verified.
- The only path from keyboard free-text to dispatch is `MacroExecutionPopup.execute()`
  (`MacroExecutionPopup.kt:119-138`) → `MacroInvocation.buildTyped` → `PrinterCommands.scriptParams`.
  Raw `values[...]` never reaches `scriptParams` directly. Verified.
- Numeric unquoted path is safe: values originate only from `NumpadPage` clamping + `formatNumeric`
  (digits/`.`/`-`), keys from the `[A-Za-z_0-9]+` regex-parsed macro body, and numeric values are still
  run through `rejectForbidden` as defence-in-depth. Verified.

**Parser robustness verdict:** PASS. `parseGcodeStore` (whole walk in `runCatching` → `emptyList`,
per-entry `mapNotNull`), `MacroParamParser.parseMacroParams` (total — regex `findAll`, never throws),
and `gcodeBodyOrNull` (handles string OR array OR missing → null) all satisfy skip-bad-field tolerance.

**Concurrency verdict:** the detached-`SupervisorJob` + `UNDISPATCHED` collector pattern itself is sound
for the test-determinism reason documented; the leak is at the call site (`remember(store)` re-key not
cancelling the prior holder) — see WR-01. `ConsoleScrollback` `@Synchronized` ring eviction is correct
and thread-safe.

---

_Reviewed: 2026-06-02_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
