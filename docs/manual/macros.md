# Macros

<img src="../screenshots/v0.1.0/macros-flox-landscape-light_2026-07-03.png" width="640"/>
<img src="../screenshots/v0.1.0/macros-moto-portrait-dark_2026-07-03.png" width="220"/>

Run any `gcode_macro` defined in your Klipper config. Macros with declared parameters get
an input form; macros without named parameters (or those that slurp `rawparams`) get a
single free-form arguments field. Pin the ones you reach for often to the launcher list for
one-tap access.

**Getting there:** Home → Macros.

## The screen

The screen has two modes, toggled via the foot bar:

**Launcher mode** (default on entry) — the Focus card shows the selected macro's detail:
name as the card title, its `description:` docstring from the config (if set), and a
parameter form or arguments field. The list below shows only your bookmarked macros. Tapping
a row loads it into the Focus card; Execute runs it.

**Manage mode** — the Focus card shows an explainer about bookmarking and the underscore
visibility rule. The list shows all macros (with the underscore-hidden ones concealed unless
Show is active), each as a toggle switch. Flipping a toggle bookmarks or unbookmarks that
macro.

If the printer reports no `gcode_macro` entries at all, both the Focus card and the list show a
notice; Execute is disabled, but Back and Manage remain active.

**Foot bar (Launcher mode):** Back · Manage · Execute.

**Foot bar (Manage mode):** Back · Show / Hide.

## Options & controls

### Focus card — selected macro detail

**Macro name** — displayed as the Focus card title once a row is tapped. Before any
selection the card shows "Select a macro to run."

**Description** — the macro's `description:` value from your Klipper config, shown beneath
the title in secondary text. Hidden when the macro has no description configured.

**"Loading parameters…" notice** — shown instead of the parameter form while the app is
still waiting for the configfile body to arrive after a fresh connection. Execute stays
disabled during this state.

> The configfile body is fetched once per connection handshake via Moonraker; the notice
> disappears and the form populates as soon as the response lands.

**Parameter fields (named-params form)** — one field per parameter discovered in the
macro's `.gcode` body. Appears when the body is loaded AND the macro declares named
parameters via `params.NAME` or `params["NAME"]` Jinja references.

- **Numeric field** — shown for parameters declared with an `|int` or `|double` type
  filter. Opens the decimal keyboard. Accepts digits, an optional leading minus, and one
  decimal point. Pre-filled from the parameter's `|default(...)` value if declared.
  Clamped to ±100,000 on commit and on Execute. Label shows `NAME` or `NAME *` if the
  parameter is referenced without a `|default(...)` and the macro author expects you to
  supply it.

- **Text field** — shown for parameters with a `string` type filter, an unclassified type,
  or discovered via a `{% if 'NAME' in params %}` membership guard. Opens the text
  keyboard. Pre-filled from `|default(...)` if declared. Labeled the same way as numeric
  fields.

  Parameters left blank are omitted from the call entirely — the macro's own Jinja default
  handles them. Sending `KEY=` would override the Jinja default, so the app never sends a
  blank-valued key.

**Arguments field** — shown instead of the named-params form when the macro body reads the
`rawparams` variable, or when no named parameters are detected in the body. A single text
field labeled "Arguments"; its contents are passed to the macro verbatim.

**Running toast** — while the dispatch is in flight, an info toast reads "Running
\<NAME\>… waiting for the printer." Execute stays dimmed during this state.

**Error toast** — appears for six seconds (screen-level) when Moonraker rejects a dispatch.
A separate inline toast appears immediately when the app's own sanitizer rejects a
parameter value before the call is even sent.

> All macro calls dispatch via `printer.gcode.script` (Moonraker JSON-RPC). Named-param
> macros assemble the gcode string as `MACRO_NAME KEY=value …`; raw-args macros send
> `MACRO_NAME <arguments>`. Both paths apply the V5 sanitizer (`MacroInvocation`).

### List — Launcher mode

**Bookmarked macro rows** — one row per macro you have pinned. Tapping a row selects it
into the Focus card above (or to the left in landscape). The selected row is highlighted.
Rows appear in the order Moonraker reports macros, filtered to your pinned set.

**"No macros pinned" notice** — shown in place of the list when no macros have been
bookmarked yet. Go to Manage to pin some.

### List — Manage mode

**Macro toggle rows** — one row per visible macro. The toggle (right side of the row)
bookmarks or unbookmarks the macro. Bookmarked macros appear in the Launcher list and can
be run from there without returning to Manage.

Underscore-prefixed macro names (`_my_helper`) are hidden by default; use Show to reveal
them (see Show / Hide below).

> Bookmark state is stored per-printer in `macros.preferences_pb` (DataStore). Case
> differences between stored names and Moonraker's reported names are reconciled
> automatically (Moonraker lowercases macro object names).

### Foot bar — Launcher mode

**Back** (accent) — leaves the Macros screen and returns to Home.

**Manage** (accent) — switches to Manage mode, replacing the launcher list with the full
macro list and toggle controls.

**Execute** (green) — sends the selected macro to the printer. Disabled (dimmed) when:
no macro is selected; the configfile body has not yet landed; or the macro is already
dispatched and in flight. The button remains enabled between prints — macros are not
gated by print state.

### Foot bar — Manage mode

**Back** (accent) — returns to Launcher mode (does not leave the Macros screen).

**Show / Hide** (accent when showing, neutral when hiding) — toggles whether
underscore-prefixed `_helper` macros appear in the Manage list. Default is hidden. The
label and icon switch between "Show" (reveal) and "Hide" (conceal) to reflect the current
state.

> The Show/Hide preference is stored per-printer in `macros.preferences_pb` (DataStore).

## Related

[Concepts](concepts.md) · [Home](home.md) · [Console](console.md)
