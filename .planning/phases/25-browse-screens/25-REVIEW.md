---
phase: 25-browse-screens
reviewed: 2026-06-10T16:14:02Z
depth: standard
files_reviewed: 15
files_reviewed_list:
  - app/src/main/AndroidManifest.xml
  - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
  - app/src/main/java/works/mees/dinghy/preview/ConsolePreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/MacrosPreviews.kt
  - app/src/main/java/works/mees/dinghy/preview/WebcamPreviews.kt
  - app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt
  - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
  - app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt
  - app/src/main/res/values/strings.xml
  - app/src/test/java/works/mees/dinghy/command/MacroInvocationTest.kt
  - docs/ui_design/COMPONENTS.md
  - tools/verify_ligatures.py
findings:
  critical: 2
  warning: 8
  info: 5
  total: 15
status: issues_found
---

# Phase 25: Code Review Report

**Reviewed:** 2026-06-10T16:14:02Z
**Depth:** standard
**Files Reviewed:** 15
**Status:** issues_found

## Summary

Reviewed the four rebuilt browse screens (Files/Console/Macros/Webcam), their preview matrices,
the icon-registry additions, the AppShell wiring changes, strings, the macro-injection test, the
COMPONENTS.md spike exception, and the ligature gate. Cross-references were traced into
`MacroInvocation`, `ConsoleFilters`, `ScreenScaffold`, `OutlinedControl`, and `SpoolScreen`
(the e-stop precedent).

Two blockers: the FilesScreen emergency-stop confirm guard dispatches **nothing** (a dead
safety control that also duplicates the AppShell-level e-stop), and the WebcamScreen refactor
broke the documented full-focus layout — the Field slot is now always non-null, so the feed is
squeezed to ~50% of the screen whenever the cam picker should be hidden, contradicting the
file's own KDoc and the preview KDoc. Warnings cluster around the stateless Console seam's dead
`rawLines` logic, unreachable macro-failure feedback, new-code hardcoded strings (including a
defined-but-unused `macros_rejected` resource), a numeric-param sanitizer gap, missing a11y on
icon-only foot buttons, a sub-floor 13sp font, un-registered raw ligatures, and DataStore writes
on a composition scope (the documented recurring trap, re-authored in this diff).

The MacroInvocationTest buildTyped coverage, the ConsoleListView spike exception documentation
(COMPONENTS.md §8), the verify_ligatures Phase-25 NEEDED additions, and the DinghyIcons registry
`all` list (all 9 new tokens present and listed) are correct as far as traced.

## Critical Issues

### CR-01: FilesScreen emergency-stop confirm is a no-op — dead safety control

**File:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:300-310` (guard), `:396-404` (FloatingEStop), `:134-147` (live overload signature)
**Issue:** The live `FilesScreen` overload renders its own `FloatingEStop` over the Focus
(`state.isPrinting`) and routes it to `FileGuard.EStop`, whose `ConfirmGuard` is:

```kotlin
FileGuard.EStop -> {
    ConfirmGuard(
        title = stringResource(R.string.files_estop_guard_title),
        ...
        onConfirm = { guard = null },   // <-- dispatches NOTHING
        onCancel = { guard = null },
        ...
    )
}
```

Confirming "Emergency stop" closes the dialog and does nothing — the live overload has no
`CommandDispatcher` parameter at all, so there is no path to `EMERGENCY_STOP`. Compare the
precedent this pattern was copied from, `SpoolScreen.kt:174-186`, where `onConfirm` calls
`dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)`.

Compounding it: since Phase 24 (FIX-1), `AppShell.kt:1027-1046` already overlays a *working*
app-level `FloatingEStop` on every destination while printing, at the same `TopStart` +
`padding(14.dp)` position with the same unit-grid size. On the Files screen while printing the
two e-stops render nearly on top of each other (offset only by the Focus box's 8/4dp padding).
A tap landing on the exposed strip of the local one opens a confirm guard that, when confirmed,
**does not stop the printer** — the user believes an emergency stop was issued. This also
violates the "never the same glyph twice on one screen" icon law.

**Fix:** Remove the Files-local `FloatingEStop`, `FileGuard.EStop`, `onEmergencyStop` plumbing,
and the three `files_estop_guard_*` strings — the AppShell-level overlay already covers Files.
Alternatively (if a per-screen e-stop is intended), thread the session dispatcher into
`FilesScreen` and dispatch in `onConfirm` exactly as SpoolScreen does:

```kotlin
onConfirm = {
    dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit)
    guard = null
},
```

### CR-02: WebcamScreen Field slot is now always non-null — full-focus layout regressed to a half-screen feed

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt:171-195` (field slot), `:59-64` (contradicted KDoc); contract at `designsystem/layout/ScreenScaffold.kt:68-95`
**Issue:** Pre-change, `field` was `null` when `!showField`, so `ScreenScaffold` gave the feed
the full stage (the documented camera_feed rule: "landscape device + landscape feed → NO Field
(full-focus, cycle-on-tap)"; "A single cam never needs the picker (full-focus)"). The 25-06
refactor moved Back into a `FootButtonBar` *inside the field lambda* and made `field` always
non-null:

```kotlin
field = {
    if (showField) { CamPicker(...) }
    FootButtonBar(uDp = grid.uDp, ...) { /* Back */ }
},
gutter = null,
```

`ScreenScaffold` weights focus/field 50/50 whenever **both** slots are non-null (landscape Row
and portrait Column alike). Consequences whenever `showField == false` — i.e. the single-cam
case in *any* orientation, and the multi-cam landscape-feed-in-landscape case (the most common
printer-cam setup, 16:9 on a landscape tablet):

1. The feed is squeezed to ~50% of the screen; the other half is an almost-empty Field.
2. With `showField == false` there is no `weight(1f)` sibling, so the `FootButtonBar` floats at
   the **top** of the Field column instead of being pinned to the foot.

This directly contradicts the in-file KDoc, the camera_feed rule it cites, and
`WebcamPreviews.kt:108-110` ("Single cam … Focus fills the full screen") — the preview KDoc
asserts behavior the implementation can no longer produce.

**Fix:** Restore the conditional field and host Back in the gutter (or as a focus-foot bar)
when the picker is hidden:

```kotlin
field = if (showField) {
    {
        CamPicker(..., modifier = Modifier.weight(1f).fillMaxWidth().padding(8.dp))
        FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) { /* Back */ }
    }
} else null,
gutter = if (showField) null else {
    { FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(8.dp)) { /* Back */ } }
},
```

(or any equivalent that keeps the feed full-focus when `!showField` and pins the bar to the
foot — verify with the `WebcamSingleCam` portrait + landscape previews afterward).

## Warnings

### WR-01: Console stateless seam builds and discards `rawLines`; `rawLineCount` is non-functional

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt:134-138`
**Issue:**

```kotlin
rawLines = List(rawLineCount) { null }.map {
    lines.firstOrNull() ?: ConsoleLine(...)
}.let { if (rawLineCount == 0) emptyList() else lines },
```

The constructed `rawLineCount`-sized list is thrown away by `.let { ... else lines }` — the
parameter exists precisely to distinguish "raw lines exist but all are filtered out" from "no
lines yet", and the implementation collapses both to `lines`. With `rawLineCount > 0` and an
empty filtered `lines`, `ConsoleContent` wrongly shows the `EmptyConsole` "Console is quiet"
fresh-connect overlay. Current previews never pass `rawLineCount` explicitly, so the bug is
latent, but the seam is wrong and the allocation is dead code.
**Fix:** `ConsoleContent` only consumes `rawLines.isEmpty()` — change its parameter to
`rawLineCount: Int` (live overload passes `rawLines.size`) and have the stateless overload pass
the value through directly, deleting the fake-list construction.

### WR-02: Macro dispatch failure / running feedback is unreachable — printer rejections silently dropped

**File:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:335-341, 354-360, 469-477`
**Issue:** `execute()` dispatches and immediately calls `onExecuted()`, which flips
`fieldMode` back to `Launcher`, removing `MacroParamEntryField` from composition. The
`DispatchEvent.Failure` collector (`LaunchedEffect(dispatcher, macro.name)`) and both
`SeverityToast`s live *only* inside `MacroParamEntryField`, and `dispatcher.events` is a hot
flow with no replay — so a printer-side rejection (the exact case the file's own S4 comment
promises will "surface as a DispatchEvent.Failure toast", e.g. a `MACRO_NUMERIC_RANGE`
violation) arrives after the surface is gone and is shown nowhere. The `running` Info toast is
likewise never visible. The old `MacroExecutionPopup` stayed open while running; the merge lost
that property.
**Fix:** Either stay in ParamEntry until the in-flight key clears/fails (call `onExecuted()`
from a `LaunchedEffect` watching `running` going false with no failure), or hoist the failure
collector + toast to `MacrosContent` (screen level) so it survives the mode flip.

### WR-03: New-code hardcoded English literals — including a defined-but-unused `macros_rejected` resource

**File:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:780, 248, 262, 287, 871-879`; `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:338, 351`; `app/src/main/res/values/strings.xml:367`
**Issue:** Phase-25 code must ship `stringResource` strings from day one
(PREVIEW_AND_TOKENS convention; `MacrosPseudolocaleSpotCheck` exists to catch exactly this).
Violations in the new screens:
- `SpoolWarningGuard` title: `text = "Print $fileName?"` (FilesScreen.kt:780) — while
  `R.string.files_confirm_print_title` ("Print %1$s?") already exists and is used by the
  sibling clean-pass guard at line 248.
- Rejection toasts: `"${macro.name} was rejected: ${event.message}"` /
  `"${macro.name} was rejected: ${e.reason}"` (BookmarkedMacrosScreen.kt:338, 351) — while
  `R.string.macros_rejected` (`%s was rejected: %s`, strings.xml:367) was added this phase and
  is referenced **nowhere** (dead resource).
- `?: "file"` fallbacks (FilesScreen.kt:248, 262, 287) and the `selectedFileDetails` labels
  ("Time", "Size", "Modified", "Filament", "Layers", "Metadata is unavailable. The print can
  still start.", FilesScreen.kt:871-879) — all render in user-facing ConfirmGuard messages.
**Fix:** Route all of the above through resources; consume `macros_rejected` for both toast
sites (note: it is built inside non-composable contexts — resolve the template via
`context.getString` or pre-resolve in the composable).

### WR-04: `buildTyped` numeric path defence gap — empty values emit malformed `KEY=`; raw default expressions are emitted unquoted

**File:** `app/src/main/java/works/mees/dinghy/command/MacroInvocation.kt:69-70`; `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:324-326, 343-353`
**Issue:** For `isNumeric = true`, the value is emitted unquoted with no numeric-shape
validation (`rejectForbidden` blocks `\n \r \t ; "` and control chars — but not spaces, `=`,
braces, or emptiness). Two concrete paths through the new screen:
1. A numeric param with no `|default(...)` seeds `values[name] = ""`; Execute then dispatches
   `MACRO KEY=` — a malformed token sent to the printer with no local rejection.
2. `MacroParam.default` is the raw captured Jinja *expression* (per MacroModels.kt KDoc), seeded
   verbatim into the field; a default like `printer.extruder.target * 0.5` containing spaces is
   emitted unquoted and token-splits into extra `KEY=VALUE` pairs on the macro line (e.g.
   overriding another param). The KDoc claim that numeric values "are produced by NumpadPage
   clamping" does not hold for the default-seed path, which bypasses NumpadPage entirely.
`MacroInvocationTest` covers neither case (no empty-numeric test, no space-in-numeric test).
**Fix:** In `buildTyped`, when `isNumeric` require the value to match a numeric literal
(`value.toDoubleOrNull() != null`) and reject otherwise; in the screen, skip params whose value
is blank (don't emit `KEY=`/`KEY=""` overriding the macro's own default). Add the two regression
tests to `MacroInvocationTest`.

### WR-05: Icon-only foot buttons expose no contentDescription; the new `cd_*` strings are dead

**File:** `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt:205-235`; `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt:92-147`; `app/src/main/res/values/strings.xml:303-304, 343-345, 351-354`; `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:546-551`
**Issue:** `OutlinedControl` has no contentDescription parameter. Console's four foot controls
are icon-only (`label = ""`), so TalkBack gets the raw ligature text rendered by
`MaterialSymbol` (e.g. "mode_heat_off") or nothing meaningful. The strings added for exactly
these glyphs — `cd_console_hide_temps/timelapse/prompts`, `cd_files_print`, `cd_files_delete`,
`cd_macros_leader/manage/execute/unbookmarked` — are referenced nowhere in Kotlin (grep-clean):
all nine are dead resources. The ManageMode bookmark-state `DinghyIconView` (line 546) also
passes no `contentDescription`, so bookmark state is color/glyph-only. Active-filter state on
the Console toggles is likewise communicated only by outline color (no
`selected`/`stateDescription` semantics).
**Fix:** Add a `contentDescription: String? = null` parameter to `OutlinedControl` (applied via
`Modifier.semantics { }` or passed into `MaterialSymbol`), wire the existing `cd_*` strings at
each icon-only call site, and add toggle-state semantics (`stateDescription` or
`semantics { selected = … }`) on the three Console filters.

### WR-06: 13sp metadata text below the 15sp floor

**File:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:719-727`
**Issue:** The file-row modified-date trailing text uses `fontSize = fsSp(13f, t.fs).sp` —
below the owner's 15sp metadata floor ([[dinghy-font-sizes-too-small]]), which this same diff
explicitly annotates as the floor elsewhere (ConsoleScreen.kt:265, 289 "metadata floor 15sp").
**Fix:** `fontSize = fsSp(15f, t.fs).sp` on the modified-date Text (and re-check the fs=L
overflow preview for row-height clipping after the bump).

### WR-07: Raw ligature strings bypass the icon registry and the subset gate

**File:** `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt:583-589`; `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:802-804`; `tools/verify_ligatures.py:63-114`
**Issue:** The merged Macros screen's Show-hidden toggle passes raw
`symbol = "visibility" / "visibility_off"`. Neither name is registered in `DinghyIcons` **nor**
present in `verify_ligatures.py`'s `NEEDED` set — so the planned font subset (which, per the
DinghyIcons KDoc, iterates the registry's `IconRef.Ligature` names) would silently drop both
glyphs → tofu, and the device-free resolution gate never proves they resolve. COMPONENTS.md §5
("Icon-registry-only law") makes registry routing mandatory on redesigned screens; "preserved
verbatim" is a reuse justification, not a registry exemption — this screen was re-authored this
phase and was the right moment to register them. (`"warning"` at FilesScreen.kt:804 is at least
in `NEEDED` via the D-08 list, but is also un-registered.)
**Fix:** Register `Visibility`/`VisibilityOff` (and `Warning`) tokens in `DinghyIcons` + `all`
(reusing the already-chosen glyphs — no new icon selection involved), add
`"visibility", "visibility_off"` to `NEEDED`, and switch the call sites to the
`icon = DinghyIcons.…` overload.

### WR-08: Macro bookmark DataStore writes still ride the AppShell composition scope

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:666-667`; `app/src/main/java/works/mees/dinghy/di/AppContainer.kt:132`
**Issue:**

```kotlin
onToggleBookmark = { name -> scope.launch { container.macroPrefs.toggleBookmark(name) } },
onSetRevealHidden = { reveal -> scope.launch { container.macroPrefs.setRevealHidden(reveal) } },
```

`scope` is AppShell's `rememberCoroutineScope()`. These are DataStore writes launched on a
composition scope — the documented recurring trap ([[dinghy-compose-write-scope-cancellation]],
the Phase-14 switch-revert bug): a recovery Splash decomposing AppShell in the same frame as a
toggle tap cancels the write mid-`edit` and the bookmark change is silently dropped. The lines
were carried over verbatim, but they were re-authored in this diff (the 25-05 Macros rewiring)
and the sanctioned pattern exists: `AppContainer.writeScope` + intent methods (the
`setActiveProfile` precedent at AppContainer.kt:150).
**Fix:** Add `AppContainer.toggleMacroBookmark(name)` / `setMacroRevealHidden(reveal)` intent
methods that launch on `writeScope`, and call those from AppShell (no `scope.launch`).

## Info

### IN-01: Row-thumbnail padding applied after size shrinks the image

**File:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:698-700`
**Issue:** `Modifier.size(fsSp(40f, t.fs).dp).padding(end = 8.dp)` — padding after `size`
carves 8dp out of the 40dp box, so the bitmap draws at 32×40 (squashed by `Crop`).
**Fix:** Put the spacing outside the image (`Modifier.padding(end = 8.dp).size(…)` or a
`Spacer`), keeping the drawn area square.

### IN-02: FilesPreviews fixture KDoc contradicts the fixture

**File:** `app/src/main/java/works/mees/dinghy/preview/FilesPreviews.kt:102-110`
**Issue:** `filesNothingSelected` is documented as "Empty list — nothing selected" but
`fileRows = fakeFileRows` (5 rows). The empty-list Field state (`files_empty` /
`files_loading` / `files_error_load`) has no preview coverage at all.
**Fix:** Correct the KDoc and add one empty-list preview (cheap, exercises the
`FilesListField` empty branch).

### IN-03: `isPortraitFeed` ignores cam rotation

**File:** `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt:309-317`
**Issue:** The camera_feed Field-show rule keys only off `aspect_ratio`; a 16:9 cam with
`safeRotation` 90/270 (which `WebcamViewHost` honors at line 239) renders portrait but is
classified landscape → the Field is hidden in landscape when the rotated feed actually leaves
side room.
**Fix:** Fold rotation into the predicate: `if (rotation % 180 != 0) swap(w, h)` using the same
`safeRotation` the renderer uses.

### IN-04: formatBytes truncates; formatDate omits the year

**File:** `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt:881-888`
**Issue:** Integer division renders 4,892,000 B as "4 MB" (vs 4.7 MB); `"MMM d, HH:mm"` makes
files modified >1 year ago ambiguous ("Jun 9" of which year?). Both feed the row metadata and
the confirm-guard details.
**Fix:** One decimal for MB (`"%.1f MB"`), and include the year when the timestamp is outside
the current year.

### IN-05: Webcam holder remember key narrower than the config it captures

**File:** `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt:249-265`
**Issue:** `remember(store, activeCfg?.host ?: "", activeProfileId)` captures the full
`activeCfg` (host/port/apiKey) at construction but only re-keys on `host`. A port or API-key
edit on the same host leaves the holder streaming with the stale cfg until something else
rebuilds the spine. Benign if every config edit always rebuilds the spine (re-keying `store`),
but the key is narrower than the data captured — fragile against future config-flow changes.
**Fix:** Key on the fields actually captured: `remember(store, activeCfg, activeProfileId)`
(ConnectionConfig is a data class; equality re-keys only on real changes).

---

_Reviewed: 2026-06-10T16:14:02Z_
_Reviewer: Claude (gsd-code-reviewer)_
_Depth: standard_
