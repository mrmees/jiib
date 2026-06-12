---
phase: 28-system-settings-cluster
fixed_at: 2026-06-12T00:00:00Z
review_path: .planning/phases/28-system-settings-cluster/28-REVIEW.md
iteration: 1
findings_in_scope: 10
fixed: 10
skipped: 0
status: all_fixed
---

# Phase 28: Code Review Fix Report

**Fixed at:** 2026-06-12
**Source review:** `.planning/phases/28-system-settings-cluster/28-REVIEW.md`
**Iteration:** 1

**Summary:**
- Findings in scope: 10 (1 Critical + 9 Warning; Info findings IN-01..IN-10 out of scope)
- Fixed: 10
- Skipped: 0

**Verification gates:** every fix compiled via Windows-side `:app:compileDebugKotlin` before commit;
full `:app:testDebugUnitTest` host suite GREEN after the final fix. Fixes were applied in an isolated
git worktree and fast-forwarded onto `master` (40a4f5a → 838b79d, 10 commits).

## Fixed Issues

### CR-01: "Clear key" followed by Save silently resurrects the cleared API key

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`, `app/src/test/java/works/mees/dinghy/ui/screen/PrintersModeToggleTest.kt`
**Commit:** 52e7c17
**Status:** fixed (host-tested; on-device clear→save spot-check recommended at UAT)
**Applied fix:** Added a local `keyCleared` state (set by the Clear-key button, reset by the
seed `LaunchedEffect(profile?.id)`) and a new pure, package-level helper
`resolveEditorKeyOnSave(storedKey, keyCleared, fieldInput)` that nulls the stale snapshot key
once cleared and feeds `cleared = keyCleared && fieldInput.isBlank()` into
`AppContainer.resolveApiKeyEdit`. Save now routes through this helper. The immediate
`saveProfile(apiKey = null)` on Clear is retained. Added 4 host tests covering
clear→blank-save (null persists — the review's required case), clear→typed-save,
preserve, and replace paths.

### WR-01: Dev-cycler panel drag broken by a stale `pointerInput` closure

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt`
**Commit:** a5af261
**Status:** fixed: requires human verification (on-device — confirm the panel follows the finger and relocates persistently)
**Applied fix:** The drag handler now reads the LIVE `offset` MutableState inside the gesture
lambda (`val cur = offset ?: Offset(startInsetPx, startInsetPx)`) and accumulates deltas onto it
with the clamp inline, instead of the frozen composition-scope `pos` val. Key dropped from
`pointerInput(boxSize)` to `pointerInput(Unit)` since `boxSize` is also a MutableState read live
in the handler.

### WR-02: Back press while the delete ConfirmGuard is open never cancels the delete prompt

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`
**Commit:** 88b8180
**Status:** fixed: requires human verification (on-device — Back with guard open must act as "Keep", not pop the route)
**Applied fix:** Exactly the review's suggestion — `pendingDelete != null` added to the
`BackHandler` gate, with dismissal priority pendingDelete → editingTarget → mode-disarm.

### WR-03: Settings babystep field commits per-keystroke and re-seeds from the flow

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt`
**Commit:** 9be6748
**Status:** fixed: requires human verification (on-device — type "12" quickly; value must stick; blur after "0" must resync to the coerced value)
**Applied fix:** Took the review's second option (gate the reseed on focus) rather than
commit-on-blur, to avoid losing a typed value when the user navigates away without a focus-loss
event: added `layersEditing` tracked via `Modifier.onFocusChanged`, replaced the
`remember(babystepLayers)` re-key with `LaunchedEffect(babystepLayers, layersEditing)` that
re-seeds ONLY while unfocused. Per-keystroke durable commits are retained; on blur the field
resyncs to the persisted (possibly `setLayerCount`-coerced) value, which also covers the
"0 coerced to 1" echo.

### WR-04: PrintersContent / ThemeEditorContent preview seams duplicated the live layout

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt`
**Commit:** 3dfcdb5
**Status:** fixed: requires human verification (on-device smoke of Printers + ThemeEditor — two interactive screens were rewired to delegate)
**Applied fix:** `PrintersScreen` now resolves state/lambdas and calls `PrintersContent` (the
~155-line duplicated body deleted). `ThemeEditorContent` gained defaulted no-op callback params
(slot-picker wheel/S-V/clear/done, appearance, seed, presets, slot taps, randomize/reset) so the
existing `@Preview` matrix compiles unchanged while `ThemeEditorScreen` hoists picker-seeding
state (`pickerSeedColor`/`slotHue`/sat-value `LaunchedEffect`) and delegates ALL rendering to the
seam (~350 duplicated lines deleted). The flagged drift was fixed in the shared body: the
status-picker sublabel now branches on `t.mode` (Colorful vs mode-gated copy) in the ONE place
both the app and previews render.

### WR-05: Systematic hardcoded English strings on the rebuilt screens

**Files modified:** `app/src/main/res/values/strings.xml`, `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt`
**Commit:** 577db1b
**Status:** fixed
**Applied fix:** 52 new keys (`printers_*`, `conn_state_*`, `theme_*`, `common_on`/`common_off`);
every flagged literal now routes through `stringResource`, including the previously-bypassed
`common_save`/`common_done`. `ConnectionState.label()` → `labelRes(): Int`,
`StatusSlot.label()`/`PaletteMode.label()` → `labelRes()`, `PALETTE_MODES` →
`List<Pair<String, Int>>` (@StringRes labels). Notes for the owner:
(1) copy normalization — `PaletteMode.label()`'s "High-contrast" was unified with the chip copy
"High contrast" under one `theme_mode_high_contrast` key; (2) the Dev cycler overlay's labels
(THEME/Style/Size/Printer/Dismiss, dev-only tool) were LEFT literal — the review allows this only
with owner sanction, so no in-file exemption note was added pending that call.

### WR-06: SysInfo health chip constructs an ad-hoc, unregistered DinghyIcon

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt`
**Commit:** ddb7978
**Status:** fixed
**Applied fix:** `HealthState.Warn` now uses the already-registered `DinghyIcons.Warning`
token (same "warning" ligature); the inline `DinghyIcon(IconRef.Ligature(...))` construction and
the now-unused `IconRef` import were removed. No new glyph invented (registry-only law).

### WR-07: Dead `onAddPrinter` parameter + stale AppShell wiring

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`, `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt`, `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt`
**Commit:** 3d7d9ae
**Status:** fixed
**Applied fix:** Deleted the `onAddPrinter` parameter from `PrintersScreen`, the misleading
`onAddPrinter = { navController.navigate(NavDest.Settings) }` wiring in AppShell, and the `{}`
no-op in RootController.

### WR-08: 1U touch-floor missing on the connection editor's tap rows

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt`
**Commit:** 2bdedac
**Status:** fixed (S-text-size visual spot-check recommended at UAT)
**Applied fix:** Per the GAP-A "All 1U" ruling — `PrinterConnectionEditor` is now wrapped in
`BoxWithConstraints` + `rememberUnitGrid(minOf(maxWidth, maxHeight))` (it had no grid);
`SecureToggleRow` and `DiscoveredPrinterRow` take `uDp` and apply `.heightIn(min = uDp)`
(fixed `vertical = 14.dp` padding reduced to 8.dp breathing room; `DiscoveredPrinterRow` also
gained `verticalAlignment = CenterVertically` for the floored height).

### WR-09: SaturationValueSquare keys `pointerInput` on `hue` and captures callbacks without `rememberUpdatedState`

**Files modified:** `app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt`
**Commit:** 838b79d
**Status:** fixed: requires human verification (on-device — S/V drag must survive a concurrent wheel touch; settle must target the right profile/global)
**Applied fix:** Exactly the review's suggestion — `pointerInput(Unit)` (the gesture never reads
`hue`), with `onHandleMove`/`onSettle` wrapped via `rememberUpdatedState` and the wrapped values
invoked inside the gesture block, so the never-recaptured lambda always calls the latest closures.

## Skipped Issues

None — all 10 in-scope findings were fixed.

## Process notes

- WR-07/WR-08 both touch `PrintersScreen.kt`; an initial combined commit was reset and split so
  each finding's commit is atomic (final hashes above are the authoritative ones).
- Human-verification items above are flagged because Tier-1/Tier-2 verification (re-read +
  compile/tests) proves syntax and the host-testable logic, not interactive gesture/focus/back
  semantics — fold them into the phase UAT pass.

---

_Fixed: 2026-06-12_
_Fixer: Claude (gsd-code-fixer)_
_Iteration: 1_
