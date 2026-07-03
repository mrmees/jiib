# Concepts

App-wide grammar for every screen in jiib: how the layout works, how buttons behave,
what the emergency stop does, and what happens when the printer is busy.

## Layout

Every screen except Webcam is built from two regions stacked or placed side by side:

- **Focus card** — a bounded surface card (`surface` fill, rounded corners, `accentLine` edge)
  that shows one primary item or a state digest. Its content is always centered and scales to
  fill the available space without clipping.
- **list** (the Field) — a scrollable list of rows below the Focus card (portrait) or beside it
  (landscape). The foot bar lives at the bottom of the list column.

**Portrait:** Focus card and list stack full-width. The Focus card takes roughly 40% of the height; the
list takes the rest.

**Landscape:** Focus card and list sit side by side, each 50% of the content width on a shared column
grid. The foot bar aligns to the bottom of the list column on that same grid.

Every vertical dimension snaps to a unit **U**, derived from the shorter edge of the content area
and held constant through rotation. A list row is 1U tall. A foot bar row is 1U tall. Larger
screens show more rows, not larger rows.

The **Focus card** carries a mandatory 1U header: a start-aligned icon and a centered title.
The header icon is tappable on every screen to return to Home.

## Foot bar & buttons

The **foot bar** is a row of action buttons pinned to the bottom of the list column. **Back is
always the first button** (start/left position), app-wide, so muscle memory holds regardless of
the screen.

**With 2 or fewer buttons** the foot bar shows an icon and a text label on each button. **With
3 or more buttons** labels are dropped and only icons show.

Button color signals the safety of the action — not the kind of widget:

- **Red** — could be destructive: emergency stop, cancel a print, disable steppers, delete a file.
- **Amber** — part of the process but could be hazardous: heating and loading filament, running a
  macro that changes config, leaving a screen mid-operation.
- **Green** — the expected action for this screen: starting a print, saving, accepting a calibration
  result, jogging on the Move screen.
- **Accent** (your seed color) — plain navigation with no printer-state consequence: Back, Home,
  secondary follow-ups.

Buttons are filled; list rows are transparent with a thin outline. That contrast is what visually
distinguishes a button from a row.

A Back button that would **discard unsaved input** or **reject a pending result** stays red
rather than accent — for example, the Spoolman weight-entry page and the scan-confirm card.

## Emergency stop

The **e-stop** halts Klipper firmware immediately. After an e-stop you must restart Klipper to
print again.

On any screen that has a Focus card, the **header icon morphs into the e-stop button** while a
print is active or while a gated operation (homing, bed mesh, jogging, extruding) is running.
The icon changes to the red square-✕ glyph. The slot is the same size and position as the
identity icon — no layout shift.

**Tap** the red icon → a confirmation guard appears ("Emergency stop? / Immediately halts the
printer (firmware E-stop). You will need to restart Klipper to print again."). Tap **Emergency
stop** to confirm; tap **Cancel** to dismiss without acting.

**Long-press** the red icon → immediate halt with no guard (the panic path).

On **Webcam** — the one screen that does not use a Focus card — a floating red square-✕ button
appears at the top-left while printing or paused. It works the same way: tap for the guarded
path.

## Busy & gated states

Some operations are **gated**: while they run the screen shows their state in the Focus body
and may restrict what you can do. There are four states:

- **Idle** — nothing is running; all controls are live.
- **Busy (SoftBusy)** — a non-blocking command is in flight (e.g., a fire-and-forget gcode). A
  busy indicator appears; the rest of the screen stays live.
- **Locked (HardLock)** — a long-running operation is in progress (homing, mesh calibration,
  leveling, extruding). The Focus body switches to a large status label ("Homing…",
  "Calibrating bed mesh…", and so on). The e-stop remains live in the header. Back is armed: the
  system back gesture and the Back foot button both pop a confirmation guard ("Still running /
  The printer keeps working in the background. Leave this screen?") before navigating away. The
  guard uses amber (proceed-at-peril) intent — leaving is permitted but deliberate.
- **Unknown** — a HardLock operation exited abnormally (connection dropped, timeout) and the
  printer may still be running it. The Focus body shows "Still running" with a message: "The
  printer may still be working. Check the printer, then dismiss." A single **Dismiss** (accent)
  button clears the latch after the user has inspected the printer. The e-stop stays live above.

HardLock wins over SoftBusy; Unknown wins over both. The gating state does not affect a print
that was running before the operation started — the e-stop and the print-active morph coexist.

## Prompts & confirmations

**ConfirmGuard** — a full-screen decision gate used for destructive or hazardous actions
(emergency stop, cancel print, disable steppers, delete a file, restart Klipper, and others).
The background tints with the intent color (red-soft for destructive, amber-soft for
proceed-at-peril, green-soft for positive commit) so the gravity is visible before you read the
label. Two buttons in a row: **Cancel** on the left (neutral, no consequence), **action** on the
right (intent-colored). No other controls are reachable while the guard is on screen.

**PromptDialog** — a full-screen Macro Prompt overlay triggered by a running Klipper macro via
the `action:prompt_begin` protocol. It shows:

- A **header** with the macro-supplied title (up to 3 lines, then ellipsis).
- A **scrollable body** with text, images, and buttons supplied by the macro author. The body
  scrolls if the content is long; it never overflows.
- A **footer action bar** with the macro's footer buttons plus an always-present **Close** button
  (accent). Close emits `prompt_end` and dismisses the overlay without running any gcode.
- **Status toasts** appear above the footer: a "Sending… waiting for the printer." info toast
  (accent) while a button gcode is in flight; a red error toast if a button is rejected.

Macro authors can style body buttons with semantic styles (`primary`, `info`, `warning`, `error`,
`success`) that map to the app's token colors. Inline `<color:#rrggbb>` markup in text runs
renders the author's exact hex — that color is content data, not a theme token.

**SeverityToast** — a transient pill that appears during operations to surface an outcome:

| Severity | Border / icon color | Mark |
|----------|---------------------|------|
| Info     | accent              | `i`  |
| Success  | green               | `✓`  |
| Warning  | amber               | `!`  |
| Error    | red                 | `×`  |

The mark and the message text together carry the meaning — color alone is never the only signal.
The host controls show/hide timing; the toast itself does not animate continuously.

## Theming & text size

Go to [App Settings](app-settings.md) to change:

- **Theme** — dark or light baseline, seed color (drives the accent and the whole chrome palette),
  and custom palette-mode (Colorful / Simple / High-Contrast).
- **Interface font / Data font** — the typeface used for labels and for live numeric readouts
  respectively, chosen from the bundled library.
- **Text size** — S, M, or L. M is the default (tuned for reading at arm's length). All text in
  the app scales together; nothing renders below 15sp regardless of the setting.

## Related

[Home](home.md) — the root screen.
