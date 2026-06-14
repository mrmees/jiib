# Console Screen Cleanup — Design

**Date:** 2026-06-13
**Status:** Approved (pending user review of this spec)
**Scope:** Quick polish pass on the read-only Console screen. Single concern: reclaim the vertical
space the header region wastes, by making the screen one Focus pane whose frame *contains* the
console feed, with the button row beneath it.

## Problem

The Console screen is conceptually a **single Focus pane plus a bottom button row**, but the code
builds it as a two-region `ScreenScaffold`:

- `focus` slot → a `FocusFrame` that wraps **nothing but a one-line blurb** ("Live printer console
  output.").
- `field` slot → the actual `ConsoleListView` scrollback + the `FootButtonBar`.

Consequences:
- Two stacked boxes where there should be one. The outlined `FocusFrame` frames a useless blurb
  while the real content (the feed) lives *outside* the frame, in a separate region below it.
- The blurb eats vertical height on a screen whose entire job is maximum scrollback.
- In landscape, `ScreenScaffold` splits focus/field **side-by-side** (50/50), which would put the
  blurb in the left half and the feed in the right half — nonsense for a console.
- The file's header KDoc is stale: it still claims "Field-only `ScreenScaffold`, `focus = null`
  (D-14), no gutter (D-15)" — none of which matches the current code (the Focus-header law added the
  `FocusFrame` after that comment was written).

## Goal

The console feed lives **inside** the Focus outline/header. One pane, one outline, the feed as its
content, the button row below. Nothing else about the screen changes (filters, severity coloring,
e-stop behavior, empty/backfill states, the Views/RecyclerView scrollback are all retained as-is).

## Design

Replace the `ScreenScaffold(focus = …, field = …)` arrangement in `ConsoleContent` with a plain
vertical `Column`:

```kotlin
Box(fillMaxSize, background = t.bg) {
    BoxWithConstraints(fillMaxSize) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Column(Modifier.fillMaxSize()) {
            FocusFrame(
                title = stringResource(R.string.cd_launcher_console),
                icon = DinghyIcons.LauncherConsole,
                uDp = grid.uDp,
                modifier = Modifier.fillMaxWidth().weight(1f),   // fills all space above the bar
                isPrinting = isPrinting,
                onEmergencyStop = onEmergencyStop,
                onPanic = onEmergencyStop,
                contentInset = 0.dp,                              // feed fills the frame edge-to-edge
            ) {
                // The feed, INSIDE the outline. Pinned-height wrapper kept (the Files scroll lesson):
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                    ConsoleListView(
                        lines = lines,
                        modifier = Modifier.fillMaxWidth().height(maxHeight),
                    )
                    when {
                        rawLineCount == 0 && !backfillFailed ->
                            EmptyConsole(Modifier.matchParentSize())
                        backfillFailed ->
                            BackfillFailedNotice(Modifier.fillMaxWidth())
                    }
                }
            }
            FootButtonBar(uDp = grid.uDp) {
                // Unchanged: Back (accent) + Hide-temps + Hide-timelapse + Hide-prompts toggles.
            }
        }
    }
}
```

### Decisions baked in

1. **Delete the blurb.** Remove the `Text(console_focus_blurb)` from the screen and remove the now
   unused `console_focus_blurb` string from `res/values/strings.xml`. (Confirmed only used here.)
2. **Feed inside the frame, edge-to-edge (`contentInset = 0.dp`).** Rows already carry their own
   12dp horizontal / 4dp vertical padding, and the row background (`palette.background = t.surface`)
   equals the `FocusFrame` fill (`t.surface`) — so the feed reads as one seamless terminal pane. The
   `FocusFrame`'s rounded-corner content clip stays, so nothing overpaints the outline/corners.
3. **E-stop untouched.** It stays as the `FocusFrame` header morph (idle identity glyph ↔ red e-stop
   while printing). No re-homing, no shell-fallback `FloatingEStop` — the safest path, and it keeps
   the screen inside the Focus-header law (console already owns its e-stop, `screenOwnsEstop`).
4. **Drop `ScreenScaffold` for this screen.** It is a single-pane, special-use screen; the
   two-region focus/field split is the wrong tool and is the source of the landscape side-by-side
   bug. A plain `Column` is the honest structure and fixes landscape for free (frame fills, bar at
   bottom, in both orientations).
5. **Keep the pinned-height `BoxWithConstraints` + `.height(maxHeight)` wrapper** around
   `ConsoleListView` (the Files/Console Views-in-Compose scroll lesson — without it the RecyclerView
   over-measures). The empty/backfill overlays stay as overlays within that wrapper.
6. **Rewrite the stale header KDoc** in `ConsoleScreen.kt` to describe the single-Focus-pane
   structure (it currently describes the retired `focus = null` / gutter design).

### Out of scope (explicitly not touched)

- Row styling (font, size, severity colors, error/warning shape glyphs, spacing).
- The three filter toggles and their FootButtonBar presentation.
- Missing affordances (scroll-to-bottom, timestamps, copy/select, clear).
- The `ConsoleListView` / `ConsoleRowsAdapter` update strategy, the raw-holder/filter invariant
  (D-04), severity classification, scrollback cap.

## Affected files

- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` — restructure `ConsoleContent`;
  delete the blurb `Text`; rewrite the header KDoc. (Both `ConsoleScreen` overloads keep their
  signatures; only the shared `ConsoleContent` body changes.)
- `app/src/main/res/values/strings.xml` — remove `console_focus_blurb`.

## Testing / verification

- Unit tests (`ConsoleFiltersTest`, `ConsoleHolderTest`, `ConsoleSeverityTest`) are pure-logic and
  untouched — they should still pass.
- Build the split-ABI debug APK and install on **both** flox (Nexus 7 / armeabi-v7a) and moto
  (arm64-v8a) per the test-devices rule. Force a clean rebuild and verify APK mtime > the fix commit
  before installing (stale-APK UAT gate).
- On-device eyeball (owner): portrait + landscape; confirm one outlined pane with the feed inside it
  and the button row below, no blurb, feed reaches the frame edges, scroll/stick-to-bottom intact.
- While a print is running: confirm the header icon still morphs to the red e-stop and the confirm
  guard fires.
```
