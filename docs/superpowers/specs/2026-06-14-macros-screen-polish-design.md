# Macros Screen Polish — Design

**Date:** 2026-06-14
**Status:** Approved — ready for planning
**Scope:** Polish pass on the Macros screen. Fix the Focus/Field grammar conversion and
upgrade macro parsing so parameter entry is accurate. Treat macros as "a command-line
interface made easier."

---

## Problem

The Macros screen was converted to the two-region Focus/Field grammar incorrectly:

- **The Focus is empty** — wasted real estate, violates the grammar every other polished
  screen (Spoolman, Temperature, Move Hub) follows.
- **Selecting a macro replaces the Field list** with macro info, instead of bringing the
  selected macro into the Focus while the list stays put.

Behind that, the parsing plumbing throws away most of what Klipper exposes about a macro.
At handshake we read the `configfile` and keep only each macro's `.gcode` body — purely to
regex parameters out of it. The `description` is discarded, and the param regex is
Mainsail-verbatim (dot-syntax only), so it misses bracket syntax and has no notion of
required vs optional params or `rawparams`.

The core goal of this pass: **accurately parse the parameters a macro can take so the user
can enter them and pass them through** (e.g. a "pause at layer" macro needs an integer
before it runs).

---

## Non-Goals (explicitly out of scope)

Per the scoping decision, the following from the Codex extraction recommendation
(`docs/moonraker_macro_extraction_recommendation.md`) are **deferred**, not built:

- Live `variable_*` macro state via `objects/query` / websocket `objects.subscribe`.
- `SET_GCODE_VARIABLE` write analysis ("side effects").
- `printer.*` dependency maps.
- `rename_existing` surfacing.
- `/printer/gcode/help` as a description source (may be added later as a fallback only).

These are CLI-dashboard / power-user features that do not serve a touchscreen launcher.
Keeping them out keeps the plumbing change small and focused.

---

## Design

### 1. Plumbing — parsing

**Files:** `net/MoonrakerSession.kt` (one-shot `configfile` read at handshake, ~lines
540–596), `ui/macros/MacroParamParser.kt`.

**Capture `description`:**
- From the same `gcode_macro <name>` section already read for `.gcode`.
- Priority chain: `configfile.settings[section].description` →
  `configfile.config[section].description` → none.
- No new Moonraker request — the configfile object is already read at handshake.

**Upgrade the param parser** (currently dot-syntax `params.NAME` only):
- **Bracket syntax** — also match `params["NAME"]` and `params['NAME']`.
- **`rawparams` detection** — if the body references `rawparams`, flag the macro as
  raw-args mode.
- **`required` inference** — `params.X` used *without* a nearby `default(...)` = required;
  with a `default(...)` = optional, and the default is pre-filled.
- **Type hints** — keep extracting `|int` / `|float` / `|string`. Used only to choose
  numeric vs full keyboard for the input field. Treated as a hint, never a guarantee.
- **Ordering** — parameters appear in source (appearance) order, de-duplicated.

### 2. Data model

**File:** `ui/macros/MacroModels.kt`.

- `MacroVm` gains:
  - `description: String?`
  - `usesRawParams: Boolean`
- `MacroParam` gains:
  - `required: Boolean`
  - (already has `name`, `type`, `default`, computed `isNumeric`)

### 3. UI — Focus/Field behavior

The current three Field-modes (Launcher / ParamEntry / Manage) collapse to **two**,
because ParamEntry stops being a Field-takeover and moves into the Focus.

- **Field** = the launcher list (bookmarked macros), **always visible**. Tapping a macro
  selects it into the Focus; the list does not disappear.
- **Focus** = the selected macro:
  - Icon + name + `description` (description line omitted when absent).
  - Its parameter inputs: keyboard-backed fields. **Numeric IME** when the type hint is
    int/float (or the default parses as a number); **full keyboard** otherwise.
  - Each field pre-filled with the parsed default where one exists.
  - **Raw-args macros** (rawparams detected, or no inferable params): a single freeform
    "arguments" text field instead of per-param fields. Whatever the user types is appended
    verbatim (after sanitization).
  - **Empty-state (nothing selected):** a titled empty-state Focus ("Select a macro to
    run" + macros icon). Satisfies the mandatory-FocusFrame-header law and avoids a
    surprise execution target. (Chosen over auto-selecting the first macro.)
- **Execute** = green foot button (the expected-action intent).
  - Empty param fields are **omitted** from the emitted command (Klipper applies its own
    default). Execute is **not** hard-gated on required params — it is a CLI; you can fire,
    and Klipper surfaces any error.
- **Manage** stays exactly as today — a foot-button mode for pin / unpin / reveal-hidden.

Portrait stacks Focus over Field; landscape is the two regions side by side (standard
grammar). Layout reference (portrait):

```
┌─ FOCUS ───────────────┐
│ [▣] PAUSE_AT_LAYER     │
│ Pause the print at a   │
│ given layer number.    │
│ ─────────────────────  │
│ LAYER      −  [ 10 ]  + │   ← keyboard-backed field, numeric IME
└────────────────────────┘
┌─ FIELD (list) ─────────┐
│ PRINT_START            │
│ PAUSE_AT_LAYER     ◄    │
│ LOAD_FILAMENT          │
│ …                      │
└────────────────────────┘
[ Back ]        [ Execute ]
```

### 4. Security

Keep the existing `command/MacroInvocation.kt` REJECT-on-forbidden-char sanitizer (rejects
`\n`, `\r`, `\t`, `;`, `"`, and ASCII control chars; no escaping). **The new raw-args field
runs through the same sanitizer** — it is the obvious injection surface and gets no pass.
Numeric params remain plain-numeric-literal validated on the dispatch path.

### 5. Testing

- `MacroParamParserTest` — add fixtures for bracket syntax, `rawparams`, and
  required-vs-optional inference. New fixture body covering a rawparams macro and a
  bracket-syntax macro alongside the existing `macro_bodies_e5.json`.
- `MacroHolderTest` — assert `description` plumbs through to `MacroVm`; assert the
  `usesRawParams` flag surfaces.
- `MacroInvocationTest` — raw-args field is sanitized (forbidden chars rejected).

---

## Component boundaries

- **Parser** (`MacroParamParser`): pure function, `gcode body → List<MacroParam> +
  usesRawParams`. No I/O, fully unit-testable.
- **Session/plumbing** (`MoonrakerSession`): extends existing configfile extraction to also
  emit `description` per macro. No new network surface.
- **Holder** (`MacroHolder`): combines capabilities + bookmarks + bodies + descriptions
  into `MacroScreensState`. Unchanged combine topology; new fields threaded through.
- **Screen** (`BookmarkedMacrosScreen`): Focus renders selected macro + params; Field
  renders list; Manage mode unchanged. Toolkit-agnostic state in, Compose out.
- **Invocation** (`MacroInvocation`): unchanged contract; raw-args string flows through the
  same sanitizer as typed params.

---

## Open items deferred to implementation

- Exact empty-param omission point (collect-then-filter in the screen vs. in
  `MacroInvocation.buildTyped`). Lean toward filtering before building the command.
- Whether numeric IME vs full keyboard is decided in the holder (on `MacroParam`) or in the
  screen. Lean toward a computed property on `MacroParam`.
