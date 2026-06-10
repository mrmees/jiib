# Phase 25: Browse Screens — Research

**Researched:** 2026-06-10
**Domain:** Android Compose/Views hybrid screen migration onto Phase-23 component kit
**Confidence:** HIGH (codebase-grounded; all claims verified against actual source files)

---

<user_constraints>
## User Constraints (from CONTEXT.md)

### Locked Decisions
- **D-01/D-02/D-03:** Wave-0 throwaway Compose spike on flox (release-mode gfxinfo) decides
  Files and Console toolkit independently per surface. Pass/fail bar = ADR-0001 Addendum-2:
  **0 frozen frames + p90 within established floor budget**. The measured result drives the
  choice; do not pre-decide. If ambiguous, surface the numbers to the owner.
- **D-04:** Files Focus = image-backed DetailCard (dimmed thumbnail bg + future-print stat lines).
  Portrait stacks (detail → controls → list), landscape = Focus|Field side-by-side.
- **D-05:** Flat file list — NO folder navigation.
- **D-06:** Sort by file AGE only, direction toggle. No filter facet, no name/size sort.
- **D-07:** FootButtonBar = Print (through full-screen Confirm with thumbnail/details) · Delete (red,
  Confirm) · Back. SpoolWarningGuard preserved on print.
- **D-08:** Delete-scoping (`deleteAllowed(…)`) already shipped — migration MUST NOT regress it.
- **D-09:** Macros = one screen, Bookmarked launcher PRIMARY + System "Manage" as an in-screen mode
  via a foot button (shares MacroHolder). Converts the Phase-24 D-01 holdout sub-nav.
- **D-10:** Bookmarked launcher = ListRow list (not the old LazyVerticalGrid of MacroTiles).
- **D-11:** Bookmarked list sized so ~9-12 entries fill Field before scrolling.
- **D-12:** Macro Execution param entry → Field-takeover per param (replaces MacroExecutionPopup).
  Existing `MacroInvocation` string sanitizer stays on the dispatch path.
- **D-13:** Macro PROMPT-protocol dialogs (Phase 12 `// action:prompt_*`) stay floating overlays;
  out of scope for D-12.
- **D-14:** Console = Field-only live log; no Focus region.
- **D-15:** The 3 filters = restyled FootButtonBar toggles (always-visible, no Field-takeover).
  ConsoleHolder keeps RAW lines; ConsoleFilters applied at render only.
- **D-16:** Webcam = token-conform only; render path (SurfaceView / Bitmap) and picker logic UNTOUCHED.
- **D-17:** Webcam-screen crash is MANDATORY to fix. Capture logcat first, then fix.
  Also re-verify WR-02 URL-resolver bug (connectionStore vs activeConfig).
- **D-18:** Verify idle-list HIDE gating on-device (per-profile webcam toggle, independent state).
- **D-19:** Conformance folds in per-screen (≥64px targets or documented exception, fsSp S/M/L,
  portrait+landscape rotation).
- **D-20:** Preview-first + tokenized-first — @Preview matrix (6 combos + fs=L), stringResource
  strings, DinghyIcons registry glyphs with each screen.
- **D-21:** Icon law — never auto-pick; if no glyph already selected, STOP and ASK owner.

### Claude's Discretion
- Spike harness shape for D-01 (throwaway scene vs. toggle in real screen).
- Files single-metric sort: full `SortFilterControlRow` or leaner date-direction control.
- Macros "Manage mode" toggle affordance (foot button mode-swap vs. Field-takeover mode list) and
  how the Execute foot button coexists with the param Field-takeover.
- NavHost route shape for rebuilt screens (existing Phase-24 nav entries preserved).

### Deferred Ideas (OUT OF SCOPE)
- Console↔Macros nav-flow refinements (auto-jump, direct nav button) — deferred to Phase 29.
- Files filter facets (folder/location, has-thumbnail, file-type).
- Webcam picker as true ListRow list / restructure.
- Console filters as a Field-takeover or dedicated sub-page.
</user_constraints>

---

## Summary

Phase 25 migrates four existing screens — Files, Macros, Console, Webcam — onto the Phase-23
component-class kit. This is primarily a codebase investigation job: understand what each screen
currently does, how the kit works, where behavior must be preserved verbatim, and what the
Wave-0 spike must resolve before the main migrations proceed.

The central fork is the **D-01 toolkit spike** for Files and Console. Both are currently
RecyclerView-in-AndroidView with specifically engineered workarounds (`MATCH_PARENT` layout
params, `clipToBounds()`, `height(maxHeight)` pin) that achieve excellent baseline performance
(Files p50=8.9 ms, Console p50=7.8 ms per Phase-22 captures). The Phase-23 kit's `ListBlock` is
a Compose `LazyColumn`; the spike determines whether the Compose path meets the ADR-0001
Addendum-2 gate (0 frozen frames + p90 within the floor budget) on flox.

Webcam has two pre-migration blockers that are **mandatory**: a crash (undiagnosed, no logcat yet)
and a latent URL-resolver bug where `AppShell.kt:214` still reads `connectionStore.config` (write-
dead since Phase 14) instead of `container.activeConfig`.

Macros requires replacing `MacroExecutionPopup` (full-screen overlay form) with a
Field-takeover pattern per param, and collapsing the two separate screens
(BookmarkedMacrosScreen / SystemMacrosScreen) into a single screen with an in-screen mode switch.

**Primary recommendation:** Run the Wave-0 spike on flox first — it gates the entire Files and
Console migration strategy. Fix the webcam crash and WR-02 URL resolver before starting
WebcamScreen conformance work.

---

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| File list + selection | Screen (Compose or Views) | FileBrowserHolder (state) | Holder owns list data; screen owns selection UI |
| File sort (age/direction) | Screen UI only | FileBrowserHolder (re-sort list) | Sort state lives in screen; holder re-fetches or screen re-orders in-place |
| Print / delete confirm guards | Screen (full-screen Compose overlay) | FileBrowserHolder (dispatch) | Guard is a screen concern; holder dispatches the command |
| Spool-warning gate | Screen (SpoolWarningGuard) | FileBrowserHolder + SpoolmanClient | Already implemented; migration must preserve it |
| Delete-scoping predicate | Shared utility (`deleteAllowed`) | FileBrowserHolder (gate at dispatch) | Both screen and holder check; must NOT be removed from either |
| Macro param entry | Screen Field-takeover (new in P25) | MacroHolder (body/params) | Popup→Field is a screen-layer concern; holder provides live params |
| Macro dispatch (sanitized) | MacroInvocation (command layer) | CommandDispatcher | String sanitizer is a command-layer gate; never bypassed |
| Console filter toggles | Screen (render-time) | ConsoleFilters utility | Raw lines stay in holder; filters applied at render only — D-15 |
| Webcam render path | WebcamHolder + SurfaceView/Bitmap bridge | AppShell (holder lifecycle) | Render path is explicitly UNTOUCHED (D-16) |
| Webcam crash diagnosis | adb logcat capture | WebcamScreen / WebcamHolder | Unknown root cause; must be captured first |
| Webcam URL resolution | AppShell (webcam holder construction) | container.activeConfig | WR-02 bug: still reads connectionStore; must fix |
| Idle-list hide gating | AppShell (webcamEnabled flow) | container.webcamTileEnabled | Already wired; on-device verification only (D-18) |

---

## Standard Stack

No new dependencies in this phase. All kit components already exist.

### Phase-23 Kit APIs (verified from source) [VERIFIED: codebase]

| Component | File | Key API | Notes |
|-----------|------|---------|-------|
| `ListRow` | `designsystem/components/ListRow.kt` | `ListRow(selected, onClick, uDp, leadingContent?, trailingContent?) { content }` | `uDp` from `rememberUnitGrid`; transparent unselected / accentSoft selected |
| `ListBlock` | `designsystem/layout/ListBlock.kt` | `ListBlock(modifier) { items(…, key=…) { ListRow(…) } }` | edge-faded LazyColumn; `Arrangement.spacedBy(8.dp)` internal |
| `FootButtonBar` | `designsystem/components/FootButtonBar.kt` | `FootButtonBar(uDp, modifier) { OutlinedControl(…) }` | Goes INSIDE `field` lambda, NOT in `gutter`; `gutter = null` on redesigned screens |
| `DetailCard` | `designsystem/components/DetailCard.kt` | `DetailCard(modifier, ringColor?) { column content }` | t.surface bg, 3dp border, rCard radius; ringColor = raw data hex (THEME-01 carve-out) |
| `FillMeter` | `designsystem/components/FillMeter.kt` | (see source) | fractional bar, data-tinted fill |
| `SortRow` / `FilterRow` | `designsystem/components/SortFilterControlRow.kt` | `SortRow(options, activeKey, onSelect, uDp)` / `FilterRow(options, onSelect, uDp)` | MANDATORY leading type-tile (DinghyIcons.Sort / .FilterList); options via `ImmutableList<SortOption<K>>` / `ImmutableList<FilterOption<K>>`; DinghyIcon per option (never raw ligature) |
| `rememberUnitGrid` | `designsystem/layout/UnitGrid.kt` | `val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))` | Call inside `BoxWithConstraints`; `grid.uDp` is the U anchor |
| `FloatingEStop` | `designsystem/components/FloatingEStop.kt` | (see source) | Box sibling, printing-only, standard dispatch pattern |
| `OutlinedControl` | `designsystem/control/OutlinedControl.kt` | `OutlinedControl(label, onClick, modifier, intent, symbol?, icon?)` | intent-colored outline; `icon: DinghyIcon` overload for registry tokens |

### SpoolScreen Pilot Patterns (the reference implementation) [VERIFIED: codebase]

The SpoolScreen (`ui/spool/SpoolScreen.kt`) is the fully-realized template. Critical patterns:

1. **Stateless preview seam:** two overloads — a `holder`-consuming live overload, and a `state`-only
   stateless overload for `@Preview`. Both delegate to a `private fun SpoolContent(...)`. Phase 25
   screens MUST follow this two-overload pattern.

2. **FieldMode enum for in-screen sub-navigation:** SpoolScreen uses `FieldMode` (sealed class)
   to switch the Field between list view and filter-picker in-place. Phase 25 Macros will need the
   same pattern for the Bookmarked vs. Manage modes (D-09).

3. **FloatingEStop wiring:** `isPrinting = printerState.printState == PrintState.Printing || Paused`.
   The e-stop guard is a Box sibling AFTER SpoolContent. Files must carry FloatingEStop the same way
   (the user may be browsing during a print — Files is mid-print-valid per Phase-24 D-04).

4. **gutter = null:** All rebuilt screens pass `gutter = null` to `ScreenScaffold`. Actions are in
   `FootButtonBar` inside the `field` lambda.

5. **FootButtonBar placement:** Inside the field Column, as the last element, BEFORE `ScreenScaffold`
   returns. NOT passed to the gutter slot.

---

## Architecture Patterns

### System Architecture Diagram

```
User tap (screen)
        │
        ▼
  Screen Composable (stateless seam / live overload)
  ┌────────────────────────────────────────────────┐
  │  BoxWithConstraints                            │
  │    rememberUnitGrid(minOf(w,h))                │
  │    ScreenScaffold(focus=…, field=…, gutter=null)
  │    ├── Focus (DetailCard or omitted)           │
  │    └── Field                                  │
  │        ├── [SortRow if applicable]             │
  │        ├── ListBlock { items { ListRow } }     │
  │        │     OR ConsoleListView/FileListView    │
  │        │     (if Views path retained by spike) │
  │        └── FootButtonBar { OutlinedControls }  │
  │    [FloatingEStop as Box sibling, if printing]  │
  └────────────────────────────────────────────────┘
        │                        │
        ▼                        ▼
  Holder.state (StateFlow)   CommandDispatcher.dispatch(…)
        │                        │
        ▼                        ▼
  Holder (FileBrowserHolder /  MacroInvocation.buildTyped()
  MacroHolder / ConsoleHolder /  → CommandRegistry method
  WebcamHolder)                  → Moonraker JSON-RPC
        │
        ▼
  PrinterState / Capabilities / WebcamHolder.vm
```

### Recommended Project Structure

No new packages needed. Migrations slot into existing packages:

```
ui/files/       FilesScreen.kt (rebuilt)
                FileListView.kt (may be retained if Views wins spike)
                FileRowsAdapter.kt (retained)
                FileBrowserHolder.kt (UNTOUCHED)
ui/macros/      BookmarkedMacrosScreen.kt (rebuilt — merged + FieldMode)
                SystemMacrosScreen.kt (removed or absorbed)
                MacroExecutionPopup.kt (removed — replaced by Field-takeover in screen)
                MacroHolder.kt (UNTOUCHED)
                MacroParamParser.kt (UNTOUCHED)
                MacroInvocation.kt (UNTOUCHED — stays on dispatch path)
ui/console/     ConsoleScreen.kt (rebuilt)
                ConsoleListView.kt (may be retained if Views wins spike)
                ConsoleRowsAdapter.kt (retained)
                ConsoleHolder.kt (UNTOUCHED)
                ConsoleFilters.kt (UNTOUCHED)
ui/webcam/      WebcamScreen.kt (re-tokened, crash fixed, WR-02 fixed)
                WebcamHolder.kt (UNTOUCHED)
ui/shell/       AppShell.kt (WR-02 fix: line 214 connectionStore → activeConfig)
```

### Pattern 1: The D-01 Toolkit Spike Harness

The spike compares the current Views implementation against a throwaway Compose
`LazyColumn + ListRow` build of each surface. Procedure (from Phase-22 GFXINFO-BASELINE.md):

```kotlin
// Source: Phase-22 GFXINFO-BASELINE.md — the capture protocol
// 1. Build release APK (gw.bat :app:assembleRelease) + debug-sign + install
// 2. Navigate to the surface under test
// 3. adb shell dumpsys gfxinfo <package> reset
// 4. Exercise: scroll list up/down ~4× (for Files/Console)
// 5. adb shell dumpsys gfxinfo <package> framestats > capture.txt
// 6. python3 tools/gfxinfo-parser/parse_framestats.py capture.txt [--warmup 10]
//
// Phase-22 BASELINE for comparison (these are the floors to meet or beat):
//   Files  — scroll: p50=8.91ms, p90=41.95ms, p95=42.96ms, 0 frozen frames
//   Console — live+scroll: p50=7.79ms, p90=9.26ms, p95=10.29ms, 0 frozen frames
```

The throwaway Compose scene can be a separate Activity/screen or a dev-gated toggle inside the
real screen. The Phase-22 approach was a real-device `adb input` drive — that approach is
reusable here. The spike MUST run in release mode (debug Compose is 5-10× slower per CLAUDE.md).

### Pattern 2: Views-in-Compose Pinned Height (load-bearing — preserve if Views retained)

```kotlin
// Source: FileListView.kt:49, ConsoleListView.kt:54
BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
    // Pin to EXACT height — AndroidView over-measures (wraps all rows) when given loose height.
    // clipToBounds() prevents rows compositing over Compose siblings in the Android layer.
    FileListView(
        modifier = Modifier.fillMaxWidth().height(maxHeight),
    )
}
```

If the spike keeps Views for a surface, this pattern (and `MATCH_PARENT` layout params in the
factory block) is load-bearing and must not be removed. The equality guards (`applyTokens`
diff-check from Phase 22, plan 22-05) prevent spurious AndroidView updates.

### Pattern 3: Field-Takeover Sub-Navigation (from SpoolScreen)

```kotlin
// Source: SpoolScreen.kt — FieldMode pattern
sealed interface FieldMode {
    data object SpoolList : FieldMode
    data class FilterPicker(val category: SpoolFilterCategory) : FieldMode
}
// Rendered in SpoolContent:
when (state.fieldMode) {
    is FieldMode.SpoolList -> {
        ListBlock(Modifier.weight(1f).padding(horizontal = 8.dp)) {
            items(state.spools, key = { it.id }) { spool ->
                ListRow(selected = …, onClick = …, uDp = grid.uDp) { SpoolRowContent(…) }
            }
        }
        FootButtonBar(uDp = grid.uDp) { … }
    }
    is FieldMode.FilterPicker -> {
        // Field is replaced by the option list in-place — no separate screen push
        FilterPickerContent(…)
    }
}
```

Phase 25 Macros will use the same pattern for Bookmarked (list of macros) vs. Manage mode
(system manage list) and for param entry (each param's Field-takeover entry surface).

### Pattern 4: @Preview Matrix (required by D-20)

```kotlin
// Source: docs/ui_design/PREVIEW_AND_TOKENS.md + Phase-18 pattern
// 6 combos: Dark/M · Dark/L · Light/M · Custom/M · Dark/S · Light/S
// Plus at least one landscape preview
@Preview(name = "Files Dark M", widthDp = 800, heightDp = 600) // landscape
@Preview(name = "Files Dark M portrait", widthDp = 480, heightDp = 800)
@Composable fun FilesScreenPreview() {
    // Uses stateless overload with FakeState fixtures — NO live Moonraker
    DinghyTheme(darkTheme = true, fs = FontScale.M) {
        FilesScreen(state = fakeFilesState(), …)
    }
}
```

### Anti-Patterns to Avoid

- **Using `gutter` slot on rebuilt screens:** FootButtonBar lives in the field Column, gutter = null.
  The gutter slot is backward-compat only for pre-redesign screens.
- **Raw ligature strings in DinghyIcons contexts:** `SortRow`/`FilterRow` options take `DinghyIcon`
  tokens, not raw string ligatures. Using a raw string bypasses the icon-registry enforcement.
- **Freezing MacroVm at tap time for params:** The MacroExecutionPopup's WR-03 fix resolves the live
  VM from the holder by name each recomposition (params arrive asynchronously on cold connect). The
  Field-takeover replacement must preserve this reactive resolution.
- **Writing DataStore from composition scope:** Any persistence writes (prefs, etc.) must use
  `AppContainer.writeScope`, not `rememberCoroutineScope()` (cancelled on nav, silently dropped —
  Phase-14 write-scope lesson from `dinghy-compose-write-scope-cancellation`).
- **Missing `key` on `items(...)` in ListBlock:** Causes whole-list recomposition on every state
  change, jank on Adreno 320.
- **Not calling `clipToBounds()` on AndroidView surfaces retained after spike:** Rows in the Android
  layer paint over Compose siblings without this.

---

## Don't Hand-Roll

| Problem | Don't Build | Use Instead | Why |
|---------|-------------|-------------|-----|
| Scrollable list with edge fades | Custom LazyColumn + gradient | `ListBlock` | Phase-23 standard; Adreno-320-safe gradient approach |
| List row with selection | Custom Row + border logic | `ListRow(selected, onClick, uDp)` | Selection state, fill convention, touch floor all baked in |
| Foot-of-list action row | Custom Row in gutter | `FootButtonBar(uDp)` | Correct placement (field, not gutter), grid-unit height |
| Selected-item detail panel | Custom Card | `DetailCard(ringColor?)` | Correct fill (surface), rCard radius, border convention |
| Touch-target grid unit | Hardcoded `64.dp` | `rememberUnitGrid(minOf(w,h)).uDp` | Rotation-constant, DPI-derived, overflow-proof |
| Sort controls | Custom Row + state | `SortRow(options, activeKey, onSelect, uDp)` | Mandatory leading type-tile; DinghyIcon enforcement |
| Filter controls | Custom Row + state | `FilterRow(options, onSelect, uDp)` | Same as above |
| Macro param sanitization | New validation layer | `MacroInvocation.buildTyped(…)` + existing `MacroParamParser` | V5 injection-rejection already proven; must not be bypassed |
| Delete-scoping logic | New `if (printing)` checks | `deleteAllowed(selectedPath, activePrintFilename, printState)` | Host-tested predicate; path-form match is non-obvious |

---

## The Four Current Screens — Behavior Inventory

### FilesScreen.kt (680 lines) — what must survive the migration [VERIFIED: codebase]

**State consumed:** `FileBrowserHolder.state` (StateFlow of FileBrowserState):
- `directory.rows` / `directory.upRow` — the file list (currently drives FileListView)
- `selectedFile` (`FileBrowserRow?`) — for Delete enabled gate + Focus card
- `selectedPreview` (`FilePreviewMetadata?`) — est time, filament, layers, height, size, modified
- `loading`, `error`, `pendingAction` — UI state gates

**Actions dispatched through holder:**
- `holder.loadRoot()` — on entry (LaunchedEffect)
- `holder.goUp()`, `holder.enterFolder(row)`, `holder.selectFile(row)` — list navigation
  (NOTE: D-05 cuts folder navigation — `Up` and `Directory` row kinds become irrelevant;
  the spike/migration must only handle `File` row kind in the flat list)
- `holder.requestStartSelected()`, `holder.requestDeleteSelected()`

**CRITICAL — must NOT regress:**
- `deleteAllowed(selectedPath, activePrintFilename, printState)` — both the screen gate AND
  the holder dispatch gate (the holder also checks; both are load-bearing)
- `SpoolWarningGuard` + `evaluatePrintStartGate(…)` — warn-only gate, never blocks print
- Full-screen `ConfirmGuard` for both Start (with thumbnail/details) and Delete
- `canStartPrint: Boolean` parameter gates the Print button

**Migration note (D-05 flat list):** The current `FileBrowserHolder` exposes directory
navigation (`loadRoot`, `enterFolder`, `goUp`, `upRow`). D-05 specifies a flat list — the
migration can either (a) always call `loadRoot()` and never render the Up row or directory rows,
or (b) ask the holder to return a flat list. The simplest approach: skip rendering
`FileBrowserRowKind.Up` and `FileBrowserRowKind.Directory` rows; only render `File` rows.
The holder's `loadRoot()` already fetches the top-level `gcodes/` folder, which contains
most gcode files. This is a screen-layer decision.

**Focus card:** The existing `FilePreviewFocus` private composable (lines 379-468) is a
solid template for the rebuilt `DetailCard` — it already follows the image-backed-card grammar
(dimmed AsyncImage background, stat lines overlaid). It should be reimplemented using
`DetailCard` as the outer container.

**Sort control (D-06):** The existing screen has no sort control. The migration adds a
single-metric age sort with direction toggle. Claude's discretion: a full `SortRow` with one
`SortOption` (schedule icon, direction up/down) or a simpler age-direction `OutlinedControl`
toggle. If `SortRow` is used, the mandatory leading type-tile applies.

### BookmarkedMacrosScreen.kt — what must survive [VERIFIED: codebase]

**State consumed:** `MacroHolder.state.bookmarkedMacros` — the user's pinned macro list.
Currently rendered as a `LazyVerticalGrid` of `MacroTile`s (D-10 replaces with `ListRow` list).
`state.unavailable` — shows no-macros copy instead of dead tiles.

**Actions:** `onRunMacro(macro: MacroVm)` — currently opens `MacroExecutionPopup`; D-12 replaces
this with a Field-takeover within the same screen.
`onManage` — currently navigates to `SystemMacrosScreen`; D-09 converts this to an in-screen mode.

**MacroExecutionPopup.kt — the popup being REPLACED by Field-takeover (D-12):**
This is 363 lines that implement the full param-entry flow. The Field-takeover replacement
must preserve ALL of this logic:
- Re-resolve live VM from holder by name (WR-03 reactive body resolution — cold-connect safety)
- `paramsKnown(macro.name)` gate — don't show Execute until body loaded
- `values = mutableStateMapOf` seeded from `param.default`
- Numeric param → tappable row that opens `NumpadPage` (MACRO_NUMERIC_RANGE = -100_000..100_000)
- String param → `TokenTextField` (the ONE sanctioned alpha-keyboard in printer controls)
- `MacroInvocation.buildTyped(…)` V5 sanitizer — `MacroParamRejected` → SeverityToast, no dispatch
- `DispatchEvent.Failure` event collect → toast for this macro's busy key
- `running = busyKey in inFlight` → Execute disabled while in-flight
- Execute key: `macro_<name>` (PRIM-05)

**Manage mode (D-09 design):** The Field shows the system macro list. The most natural
approach: a `MacroFieldMode` sealed class (e.g. `Launcher` / `ParamEntry(macro)` / `ManageMode`).
Launcher = `ListBlock` of bookmarked `ListRow`s. ManageMode = ListBlock of all macros with
pin/unpin affordance. ParamEntry = the param-entry Field for a tapped macro.

### SystemMacrosScreen.kt — what must survive (merges into BookmarkedMacrosScreen) [VERIFIED: codebase]

**State consumed:** `MacroHolder.state.visibleMacros`, `state.revealHidden`, `state.unavailable`.
**Actions:** `onToggleBookmark(name)`, `onSetRevealHidden(Boolean)`.
The `MacroSelectRow` private composable (lines 122-161) — macro name + check/uncheck affordance —
is the template for the Manage mode list rows. The `Show hidden` toggle must survive as a foot
button in the Manage mode FootButtonBar.

### ConsoleScreen.kt (257 lines) — what must survive [VERIFIED: codebase]

**State consumed:** `ConsoleHolder.state` — raw `List<ConsoleLine>`. Filter state is LOCAL to
the screen (`var hideTemps`, `var hideTimelapse`, `var hidePrompt` — all `mutableStateOf` in the
composable, default false). `ConsoleFilters.apply(lines, hideTemps, hideTimelapse, hidePrompt)`
is called at render time. The raw holder is NEVER starved by filter toggles.

**`backfillFailed: Boolean` parameter** — surfaces a warning notice at the top of the log.

**Current filter toggles:** `FilterToggle` composable (lines 139-176) = a ≥64dp outlined tile
with icon + label, active = accentLine edge + surface2 bg. These become `FootButtonBar` items
restyled with `OutlinedControl` (D-15), keeping the same toggle semantics.

**ConsoleListView.kt (Views):** The spike target. This is more complex than FileListView:
- `stackFromEnd = true` layout manager (auto-scroll to latest line)
- Smart append detection: `isSingleAppend` path avoids a full `submitRows` for live lines
- Append-evict path for ring-buffer steady state (avoids full-reset per line)
- `wasAtBottom` captured BEFORE mutation (load-bearing race condition fix)
If the Compose spike passes the gate, a `LazyColumn` with `reverseLayout = true` OR
`rememberLazyListState` + programmatic scroll-to-bottom achieves equivalent behavior.
The append-evict optimization is NOT needed in Compose (LazyColumn efficiently handles
full list updates via key-based diffing).

**D-14 (no Focus, Field-only):** The migration is simpler than Files — no DetailCard, no Focus.
The screen is the log + filter toggles. Full Field = log fills usable space.

### WebcamScreen.kt (267 lines) — what must survive [VERIFIED: codebase]

**Render path:** `FeedFocus` (lines 150-194) — H.264 path: `Media3SurfaceHost` when
`selectsH264Rung(cam) && vm.frame == null`; MJPEG/Snapshot path: `WebcamViewHost` (Bitmap frame).
**This entire render path is UNTOUCHED by D-16.**

**CamPicker:** Column of expanded/collapsed cam rows with `verticalScroll`. D-16 keeps this
structure; just re-token the chrome (border colors, text colors, font sizes via `fsSp`).

**Lifecycle:** `DisposableEffect(holder) { holder.start(); onDispose { holder.stop() } }` — must
be preserved verbatim.

**FootButtonBar addition (D-16):** Add a FootButtonBar with Back. Currently: `OutlinedControl`
in a `Row` in `gutter`. Migration: move to `FootButtonBar` in field, `gutter = null`.

---

## Webcam Crash & URL Resolver (D-17) — Investigation Summary

### Webcam-Screen Crash (undiagnosed)

**Source:** `.planning/todos/pending/2026-06-05-webcam-screen-crash.md`
- First observed during Phase 15.2-06 on-device sweep.
- Both dev printers are WebRTC/H.264 only; MJPEG is fixture-tested only.
- No stack trace yet. The crash is MANDATORY to fix for SC-1 (owner must approve on flox).
- **Action for Wave 0:** capture `adb logcat *:E Works.mees` while opening the Webcam tile.
  Most likely candidates: NullPointerException in Phase-21 webcam URL resolution, or a
  SurfaceView lifecycle ordering issue (Media3SurfaceHost registers before the surface is ready).

### WR-02 Latent URL Resolver Bug (confirmed still present)

**Source:** `.planning/todos/pending/2026-06-05-webcam-tile-gating-verification.md`

Confirmed via `AppShell.kt:214` (verified against current source):
```kotlin
// CURRENT (WRONG — connectionStore is write-dead since Phase 14):
val cfg by container.connectionStore.config.collectAsStateWithLifecycle(initialValue = null)
val activeCfg = cfg ?: ConnectionConfig(host = "")
```
The `connectionStore` config was migrated to `profileStore` in Phase 14. Every write since then
has gone to `profileStore`; `connectionStore.config` never updates. So `cfg` is always
`ConnectionConfig(host = "")` → webcam URLs resolve with empty host → empty URL → crash or
dead feed on a profile switch.

**Fix:** Replace line 214 with:
```kotlin
val activeCfg by container.activeConfig.collectAsStateWithLifecycle()
```
where `container.activeConfig` is the same source the rest of the app uses (Phase-14 migration).
The `webcamHolder` `remember(store, activeCfg.host, activeProfileId)` key on line 247+250 already
uses `activeCfg.host` — once `activeCfg` is sourced correctly, the holder re-keys on profile
switch and webcam URLs follow the active profile.

**Likely crash cause:** `activeCfg.host == ""` → `resolveWebcamUrl` with empty host → malformed
URL → OkHttp throws `IllegalArgumentException` or similar on the network thread, which may or may
not be caught depending on the Phase-21 error handling path.

### Webcam Idle-List HIDE Gating (D-18)

The wiring (`webcamEnabled = container.webcamTileEnabled`) is confirmed present in AppShell
(line 209). On-device verification (D-18) = toggle webcam per-profile in Settings, switch
printers, confirm the idle-list row appears/disappears independently. This is a UAT-only item
(no code change expected); confirmed it was deferred in the Phase-15.2-04 checkpoint.

---

## Phase-22 gfxinfo Baseline — Spike Reference [VERIFIED: codebase]

The D-02 pass/fail bar is concretely anchored to these numbers from `22-GFXINFO-BASELINE.md`:

| Surface | p50 (ms) | p90 (ms) | p95 (ms) | frozen | Toolkit |
|---------|--------:|--------:|--------:|:------:|---------|
| Files — list scroll | 8.91 | 41.95 | 42.96 | 0 | Views (RecyclerView) |
| Console — live + scroll | 7.79 | 9.26 | 10.29 | 0 | Views (RecyclerView) |

**The gate:** Compose throwaway must show **0 frozen frames** AND **p90 within established floor
budget**. For Files: p90 ≤ ~42ms (parity or better). For Console: p90 ≤ ~10ms is the Views
baseline — this is an aggressive target; the ADR-0001 original Compose-vs-Views benchmark showed
Compose p90 at ~65ms on a synthetic list scroll. However, that was a worst-case phase-1 benchmark
without a Baseline Profile, without `@Immutable` state, and without Phase-22 perf fixes. The
spike result determines whether the gap is bridgeable.

**If Console Compose p90 materially exceeds the Views baseline (~10ms):** The Views path is
retained. Console is by far the most likely surface to stay Views — the `stackFromEnd` pattern
and 7.8ms p50 are hard for Compose to match on this hardware.

**Spike design (Claude's discretion):** A throwaway Activity or a dev-flag screen toggle. The
Phase-22 approach (real release APK, `adb input` swipes, `dumpsys gfxinfo … framestats`) is the
established protocol. The parser at `tools/gfxinfo-parser/parse_framestats.py` is already present.

---

## Common Pitfalls

### Pitfall 1: FootButtonBar in gutter instead of field
**What goes wrong:** Portrait layout shows a gap between the list and the buttons; gutter slot
has different sizing than the field-foot position.
**Why it happens:** The pre-redesign screens use `gutter` for their action rows; the redesign
grammar moves them inside `field` as `FootButtonBar`. Porting the old pattern is a copy error.
**How to avoid:** Always `gutter = null`; FootButtonBar is the LAST element inside the field
Column.
**Warning signs:** Visible gap in portrait below the list; actions not visually part of the list.

### Pitfall 2: Macro param entry ExecuteButton enabled before body loads (cold connect)
**What goes wrong:** A parametered macro is tapped before its `configfile` body arrives (cold
connect). Without the `paramsKnown` gate, Execute runs the macro with NO parameters.
**Why it happens:** MacroVm is in the holder's list immediately (name known), but `params` only
populate when the body read completes.
**How to avoid:** Preserve the `holder.paramsKnown(macro.name)` check; show "Loading params…"
and disable Execute until `bodyLoaded` is true (WR-03 from the existing MacroExecutionPopup).
**Warning signs:** Execute fires with bare macro command (no param values) after cold connect.

### Pitfall 3: AndroidView over-measure (if Views retained after spike)
**What goes wrong:** RecyclerView wraps all rows, overlaps the Compose siblings above/below it,
and never scrolls.
**Why it happens:** AndroidView-hosted Views are measured in the Android layout system, not
Compose. Without explicit `MATCH_PARENT` params and `height(maxHeight)` pin, the constraint is
UNSPECIFIED.
**How to avoid:** `BoxWithConstraints` → `height(maxHeight)` + MATCH_PARENT layout params in
factory. `clipToBounds()` on the AndroidView modifier.
**Warning signs:** List content visible outside its bounds; can't scroll.

### Pitfall 4: Files folder navigation NOT removed (D-05 flat list)
**What goes wrong:** The migration leaves `FileBrowserRowKind.Directory` and `Up` rows in
the list. Users see confusing folder-entry tiles in what is supposed to be a flat list.
**Why it happens:** `FileBrowserHolder` still supports folder navigation; the screen must
filter row kinds.
**How to avoid:** Only render `FileBrowserRowKind.File` rows. Call `holder.loadRoot()` on
entry. Do NOT render `upRow` or directory rows.
**Warning signs:** Folder rows appear in the file list.

### Pitfall 5: Icon auto-pick (D-21 owner law)
**What goes wrong:** Any glyph the owner hasn't approved appears in the migrated screen.
**Why it happens:** Claude picks "a reasonable Material Symbol" rather than checking the
registry or asking.
**How to avoid:** Before ANY icon: grep `DinghyIcons`, check the hi-fi mockups, `ls img/`.
If nothing found, STOP and ASK. Never ever author a new drawable independently.
**Warning signs:** A glyph that isn't in `DinghyIcons.kt` or an existing screen.

### Pitfall 6: ConsoleFilters applied in holder instead of render (D-15)
**What goes wrong:** Filter toggles permanently discard lines from the source; toggling
a filter OFF cannot recover lines already filtered out.
**Why it happens:** Moving the filter call into the holder loop seems cleaner but breaks
re-show.
**How to avoid:** `ConsoleFilters.apply(rawLines, …)` ONLY inside the screen composable,
keyed on the local `hideTemps/hideTimelapse/hidePrompt` state. Holder exposes only RAW lines.
**Warning signs:** Toggling a filter ON/OFF causes lines to disappear permanently.

### Pitfall 7: WR-02 fix creates stale `activeCfg` key in remember
**What goes wrong:** Fixing the URL resolver breaks something else if `activeCfg` is sourced
from a different flow than the one used in the `remember(…, activeCfg.host, …)` keys.
**Why it happens:** `container.activeConfig` is the correct single source; using a different
flow creates an inconsistency.
**How to avoid:** Ensure `activeCfg` in the `remember` key and `activeCfg` passed to the
holder factory both come from the SAME `container.activeConfig` collection.

---

## Validation Architecture

### Test Framework

| Property | Value |
|----------|-------|
| Framework | JUnit4 (host tests, no Robolectric) + Compose UI test (@Preview compile gate) |
| Config file | `app/build.gradle.kts` (existing test config) |
| Quick run command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:testDebugUnitTest --no-daemon"` |
| Full suite command | `/mnt/c/Windows/System32/cmd.exe /c "E:\Android\gw.bat :app:assembleDebug :app:testDebugUnitTest --no-daemon"` |
| On-device (release) | `gw.bat :app:assembleRelease --no-daemon` + `sign-release.bat` + `adb install -r` |

### Phase Requirements → Test Map

| Behavior | Test Type | Automated Command | Notes |
|----------|-----------|-------------------|-------|
| `deleteAllowed(…)` still passes (D-08 regression) | unit | `testDebugUnitTest --tests "*DeleteGateTest*"` | Existing host test; must not regress |
| `MacroInvocation.buildTyped(…)` sanitizer intact | unit | `testDebugUnitTest --tests "*MacroInvocationTest*"` | Existing host test |
| Wave-0 gfxinfo spike: Files Compose | manual on-device | `adb shell dumpsys gfxinfo` | Release APK, flox, `parse_framestats.py` |
| Wave-0 gfxinfo spike: Console Compose | manual on-device | same | Same protocol |
| D-17 crash fix: Webcam no longer crashes | manual on-device | open Webcam tile on flox | After WR-02 fix |
| D-18 webcam idle-list gating | manual on-device | toggle webcam in Settings, switch printer | Visual verification |
| @Preview matrix compiles for all 4 screens | @Preview compile | `assembleDebug` | SC-4 gate |
| SC-5 regression: Files delete/print | manual on-device | select file, tap Print, tap Delete | flox UAT |
| SC-5 regression: macro run with/without params | manual on-device | run a macro with params on flox | flox UAT |
| SC-5 regression: console scrollback + filters | manual on-device | open Console, toggle all 3 filters | flox UAT |
| SC-5 regression: webcam playback (H.264) | manual on-device | open Webcam tile on flox | Requires WR-02 fix |

### Sampling Rate

- **Per task commit:** `gw.bat :app:testDebugUnitTest` (host tests, ~30s)
- **Per wave merge:** `gw.bat :app:assembleRelease` build-clean check
- **Phase gate:** Full host test suite green + on-device smoke on flox, owner-approved in both orientations before `/gsd-verify-work`

### Wave 0 Gaps

- [ ] `tools/gfxinfo-parser/parse_framestats.py` — already exists (Phase 22 used it); verify it's present
- [ ] Throwaway spike Activity/screen flag — new file needed for D-01 spike harness
- [ ] `adb logcat` capture harness for Webcam crash diagnosis — no file needed, just protocol documentation

---

## Security Domain

`security_enforcement` is not explicitly disabled. Phase 25 touches no auth, no network changes,
no new permissions.

### Applicable ASVS Categories

| ASVS Category | Applies | Standard Control |
|---------------|---------|-----------------|
| V5 Input Validation | YES (macro params) | `MacroInvocation.buildTyped` — V5 already implemented; migration must not bypass it |
| V2 Authentication | No | No new auth surfaces |
| V3 Session Management | No | No session state changes |
| V4 Access Control | No | No new access control |
| V6 Cryptography | No | No crypto |

### Threat Patterns

| Pattern | STRIDE | Standard Mitigation |
|---------|--------|---------------------|
| Macro param injection (G-code injection) | Tampering | `MacroInvocation.buildTyped` + `MacroParamRejected` — existing, must survive Field-takeover migration |
| String-param forbidden chars | Tampering | Same sanitizer; string field must route through `buildTyped`, not directly to `scriptParams` |

---

## Environment Availability

| Dependency | Required By | Available | Version | Fallback |
|------------|------------|-----------|---------|----------|
| flox (Nexus 7 2013) | D-01 spike, D-17 crash, D-18 gating, UAT | Must be confirmed at wave-0 start | LineageOS 18.1 / API 30 | None — cannot substitute |
| `adb` | Build + install + logcat capture | ✓ | via `E:\Android\Sdk\platform-tools\adb.exe` | — |
| `gw.bat` | Gradle builds | ✓ | `E:\Android\gw.bat` | — |
| `parse_framestats.py` | D-01 spike metrics | ✓ (Phase 22) | see `tools/gfxinfo-parser/` | — |
| Ender 3 Pro (192.168.1.121) | Webcam test (H.264 via ravens-perch) | Assumed available | — | Ender 5 Plus 192.168.1.120 |

---

## Assumptions Log

| # | Claim | Section | Risk if Wrong |
|---|-------|---------|---------------|
| A1 | WR-02 crash is caused by empty-host URL resolution from `connectionStore.config` | Webcam Investigation | Real crash cause is something else; diagnosing with logcat first resolves this |
| A2 | The Compose `LazyColumn` path for Files will be measurably slower on the spike | Spike Reference | If Compose passes the spike, Files migrates to pure Compose; no harm |
| A3 | Console Compose path will NOT pass the p90≤10ms bar on flox | Spike Reference | If it does pass, both surfaces migrate to Compose; simpler code |
| A4 | `container.activeConfig` is the correct replacement for `connectionStore.config` | WR-02 Fix | Different flow name; verify grep of AppContainer before coding the fix |

---

## Open Questions

1. **Does `container.activeConfig` exist as a public StateFlow on AppContainer?**
   - What we know: Phase-14 migrated all writes to `profileStore`; `container.activeConfig`
     is referenced in CONTEXT.md and the webcam-gating todo.
   - What's unclear: The exact property name on `AppContainer`. Confirm via grep.
   - Recommendation: `grep -n "activeConfig\|activeProfile\|activeCfg" app/src/main/java/works/mees/dinghy/di/AppContainer.kt` before writing the WR-02 fix.

2. **Does `FileBrowserHolder.loadRoot()` return only top-level files, or recursively?**
   - What we know: It calls `loadDirectory("gcodes")`. D-05 says flat list.
   - What's unclear: Whether Moonraker's `server.files.list` returns files recursively or just
     the top-level `gcodes/` directory.
   - Recommendation: For D-05 compliance, render only `FileBrowserRowKind.File` rows; ignore
     directory and up rows. The holder's folder-nav API is unused but doesn't need to be removed.

3. **What glyphs are needed for the Files SortRow age-direction option (D-06)?**
   - What we know: The sort is date/age with a direction toggle. `DinghyIcons.Sort` exists for
     the type-tile. An age/calendar icon is needed for the sort option tile.
   - What's unclear: Whether a calendar/schedule glyph is in `DinghyIcons`.
   - Recommendation: D-21 — check `DinghyIcons.kt`, then ASK owner if not present. Do NOT pick a
     glyph independently.

4. **What FootButtonBar actions does the Macros Manage mode need?**
   - What we know: Back (return to Launcher mode) + Show hidden toggle.
   - What's unclear: Whether "Back" in Manage mode means "return to Bookmarked launcher" (in-screen
     FieldMode swap) or "navigate back in the NavHost" (pops to waterfall).
   - Recommendation: In-screen mode swap (FieldMode toggle) — it mirrors how SpoolScreen handles
     its filter picker. The NavHost back pops the entire Macros screen.

---

## Sources

### Primary (HIGH confidence — verified against codebase)

- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` — current Files implementation
- `app/src/main/java/works/mees/dinghy/ui/files/FileListView.kt` — Views scroll surface
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` — current Console
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleListView.kt` — Views scroll surface
- `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` — current Bookmarked
- `app/src/main/java/works/mees/dinghy/ui/macros/SystemMacrosScreen.kt` — current System
- `app/src/main/java/works/mees/dinghy/ui/macros/MacroExecutionPopup.kt` — param entry popup
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt` — current Webcam
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — Phase-23 pilot (reference)
- `app/src/main/java/works/mees/dinghy/designsystem/components/` — all Phase-23 kit components
- `app/src/main/java/works/mees/dinghy/designsystem/layout/` — ListBlock, UnitGrid
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:214` — WR-02 bug confirmed
- `.planning/phases/22-*/22-GFXINFO-BASELINE.md` — Phase-22 baseline p50/p90/p95 numbers
- `.planning/phases/22-*/22-GFXINFO-AFTER.md` — Phase-22 post-refactor results
- `docs/adr/0001-ui-toolkit-decision.md` — ADR + Addendum-2 gate definition
- `.planning/todos/pending/2026-06-05-webcam-screen-crash.md` — crash todo
- `.planning/todos/pending/2026-06-05-webcam-tile-gating-verification.md` — WR-02 + gating todo
- `.planning/phases/25-browse-screens/25-CONTEXT.md` — D-01 through D-21 locked decisions

### Secondary (MEDIUM confidence)
- `docs/ui_design/COMPONENTS.md` — component catalog spec
- `docs/ui_design/LAYOUT.md` — Focus/Field grammar, unit U
- `docs/ui_design/CLAUDE.md` — design non-negotiables
- `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md` — lists archetype spec

---

## Metadata

**Confidence breakdown:**
- Spike protocol and baseline numbers: HIGH — direct from Phase-22 artifacts
- Component kit APIs: HIGH — read from actual source files
- Current screen behavior inventory: HIGH — read all four screen files
- WR-02 URL resolver bug status: HIGH — confirmed line 214 in AppShell.kt
- Webcam crash root cause: LOW — no logcat yet; diagnosis is hypothetical
- Macros Field-takeover design: MEDIUM — SpoolScreen pattern is clear; Macros mode-switching has multiple valid shapes

**Research date:** 2026-06-10
**Valid until:** Phase-23 and Phase-24 are complete; this research is valid until those outputs change. If `AppShell.kt` or the component kit changes before Phase 25 executes, re-verify the WR-02 fix target and component APIs.
