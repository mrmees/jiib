# Complete (print-finished) home screen

**Date:** 2026-06-16
**Status:** design approved (pending written-spec review)
**Owner:** Matthew

## Problem

When a print finishes (`print_stats.state == "complete"`), the home currently falls through to the
non-printing **digest** (Heaters/Motors/Homed/Spool). The owner wants a dedicated **Complete** screen
that reuses the just-shipped active-print **data block** (filename, temps, times, filament, Z, layers
over the thumbnail) — the finished job, presented the same way it was shown mid-print — with a success
icon and a way to clear the job.

## Scope

**In:** `printState == Complete` (not a Klippy fault). Render the existing `ActivePrintFocus` body, a
`check_circle` header icon, and a **Dismiss + System** foot bar.

**Untouched / out of scope:**
- `Cancelled` and `Error` terminal states — keep today's digest (a later effort may give them their
  own treatment; not now).
- `Printing`/`Paused` (unchanged), the idle/standby digest, the data-block internals, routing,
  networking. No new Moonraker calls — `dismissPrint` already exists.

## Design

### Trigger & body

- New branch in `HomeFocus`: `showComplete = (printState == Complete) && !klippyFault`. When true,
  render the **same `ActivePrintFocus(state, printMetadata, httpBase)`** used for printing — the
  finished job's filename/temps/times/filament/Z/layers populate because `print_stats` retains them
  through the terminal states (and `PrintMetadataHolder` keeps the metadata key live through
  Complete/Cancelled/Error).
- `showActivePrint` (the body-render predicate) becomes `isPrinting || showComplete`.

### Header

- **Icon: `check_circle`** (newly registered, owner-chosen). The FocusFrame header icon only morphs to
  the red e-stop while `isPrinting`; Complete is `isPrinting == false`, so the static `check_circle`
  shows — no e-stop on the Complete screen (consistent with the idle/standby home, which also has
  none; the print is over).
- **Title: `COMPLETE · 100%`** — same format as printing (`homeStateLabelRes(Complete)` already maps
  to the `COMPLETE` label). The `100%` is **pinned for Complete**, NOT read from `state.progress` —
  a finished job's live progress may have reset, so the percent is hard-set to 100. Multi-printer
  prefix still applies.

### Progress stroke

- Full accent perimeter loop, **pinned**: `edge = FocusEdge.Progress(1f, t.accent)` for Complete
  (1f, not `state.progress`, for the same reason the percent is pinned).

### Foot bar (the only real UI change vs printing)

`HomeField` gains a Complete branch (alongside printing and idle):
- **Dismiss** — intent **green** (`Intent.Go`, the expected action), icon **`clear_all`** (newly
  registered, owner-chosen). Dispatches `CommandRegistry.dismissPrint` (`SDCARD_RESET_FILE`), which
  resets `print_stats` to standby → the home returns to the digest.
- **System** — intent **accent**, existing `FootSystem` glyph, navigates to `NavDest.System`.

Because System sits in the foot bar here (unlike the printing screen, where it was pushed into the
list), the field list is the plain idle-action list with **no System row appended** — i.e.
`buildIdleActions(..., isPrinting = false)` for Complete.

### Field list

Unchanged from the idle list (capability-gated nav rows). The owner's ask is "the same thing as the
printing status, just change the bottom buttons," and the list already reads from `idleActions`.

## Plumbing

- `PrintStatusScreen` (container overload): compute `isComplete = printState == Complete`; build
  `onDismiss = { dispatcher?.dispatch(CommandRegistry.dismissPrint, Unit); Unit }`. Keep
  `isPrinting` driving `buildIdleActions` (Complete → `isPrinting == false`, so no System row).
  Thread `isComplete`/`onDismiss` (plus the existing `printMetadata`/`httpBase`) into
  `PrintStatusContent` → `HomeFocus`/`HomeField`. Mirror in the stateless preview overload (defaults).
- `HomeFocus`: add the `showComplete` branch; choose the header icon
  (`if (showComplete) DinghyIcons.CheckCircle else DinghyIcons.PrintStatusStandby`); pass
  `FocusEdge.Progress(1f, t.accent)` for Complete; keep `isPrinting`/`onEmergencyStop` unchanged
  (both false/irrelevant for Complete, so no e-stop).
- `HomeField`: add the Complete foot branch (Dismiss + System) before/around the printing branch.
- Icons: register `CheckCircle = DinghyIcon(IconRef.Ligature("check_circle"), …)` and
  `FootDismiss = DinghyIcon(IconRef.Ligature("clear_all"), …)` in `DinghyIcons` + `DinghyIcons.all`,
  and add the two ligatures to `img/material-icon-bucket.json`.
- Strings: a `Dismiss` label (`printstatus_foot_dismiss`).

## Affected files

- `ui/printstatus/PrintStatusFocus.kt` — `showComplete` branch + Complete header icon + edge.
- `ui/printstatus/PrintStatusField.kt` — Complete foot branch (Dismiss + System).
- `ui/printstatus/PrintStatusScreen.kt` — `isComplete`/`onDismiss` plumbing (both overloads).
- `designsystem/icons/DinghyIcons.kt` + `img/material-icon-bucket.json` — `CheckCircle`, `FootDismiss`.
- `app/src/main/res/values/strings.xml` — `printstatus_foot_dismiss` = `Dismiss`.
- `preview/SampleFixtures.kt` — a Complete fixture (optional; `forState(Complete)` already exists and
  can be enriched like Printing so the Complete preview is representative).
- Tests: `HomeActionTest` (System NOT appended when `isPrinting == false`, already covered) + a small
  `DinghyIconsTest` pass for the two new glyphs (the uniqueness drift tests run automatically).

## Bundled fix: home list-row icons → accent

Separate small polish (owner, 2026-06-16): the home Field's list-row icons are the only list icons in
the app still rendered in the muted `t.text2`, while colored lists elsewhere (Calibration, Outputs)
tint theirs. Make the home nav rows accent.

- `PrintStatusField.kt` nav-destination rows (`ListRowIcon` ~line 104): `tint = t.text2` → `tint = t.accent`
  (accent = plain nav per the intent scheme; covers Files/Outputs/Webcam/System rows).
- `PrintStatusField.kt` Spool row (`ListRowIcon` ~line 232): keep the filament-color data tint, flip
  only the fallback: `tint = spoolColor ?: t.text2` → `tint = spoolColor ?: t.accent`.

(`t.accent` is the literal accent/nav token; `t.accent2` is the alternative the colored Calibration/
Outputs lists use — owner to confirm on-device which reads right; one-token swap either way.)

## Success criteria

1. A finished print shows the active-print data block (filename, heaters, Job/Print times, Filament,
   Z, Layers) over the thumbnail, a `check_circle` header icon, `COMPLETE · 100%` title, and a full
   accent progress ring — no e-stop.
2. Foot bar shows **Dismiss** (green, `clear_all`) + **System** (accent); the field list does NOT
   carry a System row.
3. Tapping **Dismiss** dispatches `SDCARD_RESET_FILE` (`CommandRegistry.dismissPrint`); the printer
   returns to standby and the home reverts to the digest.
4. `Cancelled`/`Error`/idle/printing are all unchanged.
5. Build + unit suite green (incl. `FontConformanceTest`, `DinghyIconsTest` for the new glyphs); the
   Complete Focus is previewable.
6. (Bundled) The home Field's nav-row icons render in accent; the Spool row keeps its filament-color
   tint with an accent fallback.
