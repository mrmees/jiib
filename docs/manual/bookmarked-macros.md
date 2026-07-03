# Macros

Launch and run your Klipper `gcode_macro` commands. Tap a macro in the list to load it into the Focus card, fill in any parameters, and tap Execute.

**Getting there:** Home → Macros (the row is always present; it does not disappear when no macros are bookmarked or when no macros exist at all).

## The screen

The screen has two modes that share the same layout.

**Launcher mode** (default on entry): the Focus card shows the selected macro's name as the title, its description if one is defined, and any parameter fields. The list below shows only your bookmarked macros. When nothing is selected yet, the Focus card prompts "Select a macro to run." When no macros are bookmarked, the list area shows "No macros pinned" with a prompt to open Manage.

**Manage mode** (reached via the Manage foot button): the Focus card explains what bookmarking does and notes that underscore-prefixed helper macros are hidden by default. The list shows all visible macros as toggle switches — on means bookmarked.

The foot bar changes between modes (see below).

## Options & controls

### Focus card (Launcher mode — macro selected)

- **Description** — the macro's Klipper `description:` docstring, shown in subdued text below the title. Hidden when the macro declares no description.

- **"Loading parameters…" notice** — shown when the `configfile` body read has not yet arrived (cold-connect race). Execute stays disabled until this clears.

- **Parameter fields (typed mode)** — one field per parameter detected in the macro's `.gcode` body. Numeric parameters (`int` or `double` type) use the decimal IME; values are clamped to −100,000–100,000 on commit and at Execute time. String parameters use a text keyboard. Parameters with a detected default pre-fill to that default; blank fields are omitted from the call entirely (the macro's own Jinja default applies). Parameters the macro author declared without a default are marked with a trailing `*` in the label — this is a display hint only; Execute is not hard-blocked when they are blank.

- **Arguments field (raw mode)** — shown instead of typed fields when the macro body uses `rawparams`, or when no parameters can be inferred. A single text field accepts the full argument string, passed verbatim after the macro name. A hint reads "Passed to the macro verbatim."

- **Running toast** — an informational toast reading "Running *name*… waiting for the printer." appears inside the Focus card while a dispatch for this macro is in flight.

- **Rejection toast (local)** — an error toast inside the Focus card when a parameter value is rejected before dispatch (forbidden characters in a string param or in the Arguments field, or a non-numeric value in a numeric field). Dismisses automatically when you select a different macro.

### List (Launcher mode)

- **Macro row** — each bookmarked macro appears as a selectable row. Tapping a row loads that macro into the Focus card and makes Execute active (once the configfile body loads). Tapping the already-selected row does not deselect it.

### Foot bar (Launcher mode)

Three buttons render icon-only at this count.

- **Back** — leaves the Macros screen and returns to Home. Intent: accent.

- **Manage** — switches the Field to Manage mode. The Focus card switches to the Manage explainer. Intent: accent.

- **Execute** — dispatches the selected macro via `printer.gcode.script`. Disabled (dimmed to 38% opacity) until a macro is selected and its parameter metadata has loaded, and while a dispatch for the same macro is already in flight. Intent: green (the expected action). Plumbing: sends `NAME KEY="VALUE" …` (typed mode — string params are quoted; numeric params emit unquoted as `KEY=VALUE`) or `NAME <rawargs>` (raw mode); string values are validated and rejected on control characters, semicolons, newlines, tabs, and double-quotes before dispatch — the invocation is refused entirely if any value fails validation, never silently stripped.

### List (Manage mode)

- **Macro toggle row** — each visible macro appears as a labeled switch. The switch is on when the macro is bookmarked. Toggling adds or removes the macro from the bookmarked set; the Launcher list updates immediately. Macros whose names start with `_` (underscore-prefixed helpers) are hidden by default and only appear when the Show/Hide toggle is on.

### Foot bar (Manage mode)

Two buttons render with icon and text label.

- **Back** — returns to Launcher mode (does not leave the Macros screen). Intent: accent.

- **Show / Hide** — toggles whether underscore-prefixed (`_`-prefixed) system helper macros appear in the Manage list. Label and icon swap to reflect the current state. When hidden macros are revealed the button reads "Hide" (accent intent); when they are not revealed it reads "Show" (neutral intent).

### Screen-level error toast

A dismissing error toast appears at the bottom of the screen when the printer rejects a dispatched macro (Moonraker returns an error for the `printer.gcode.script` call). It auto-dismisses after 6 seconds. This is distinct from the local rejection toast in the Focus card, which fires before dispatch.

### Unavailable state

When the connected printer reports no `gcode_macro` entries at all, both the Focus card and the list area show an explanatory message: "This printer reports no gcode macros. Add `gcode_macro` entries in your config to use this screen." The foot bar remains present.

## Related

[Concepts](concepts.md) — gating overlays, e-stop, and connection state.
