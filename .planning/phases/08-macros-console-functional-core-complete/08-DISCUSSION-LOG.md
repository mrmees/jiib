# Phase 8: Macros & Console — Functional-Core Complete - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-02
**Phase:** 8-Macros & Console — Functional-Core Complete
**Areas discussed:** Console scope, Console filtering, Macro screens/structure, Macro parameters, Keyboard triage

---

## Approach

User opted to describe the desired Console + Macros vision in his own words first (rather than run the
structured per-area question turns), explicitly to avoid tangents on capabilities he didn't want. The
gray-area menu (Console text entry / Macro parameter entry / Macro visibility & safety / Console
history + structure) was used as a checklist to map his description against, not as a question script.

---

## Console scope

| Option | Description | Selected |
|--------|-------------|----------|
| Read-only monitor | Passive feed of printer output, no text input | ✓ |
| Full terminal (send G-code) | On-screen keyboard + send arbitrary G-code (CONS-01) | |

**User's choice:** Read-only for now. "The whole goal of this project is easy touch input for typical
printer functions, not super in-depth macro execution or troubleshooting. Maybe we'll add text input later."
**Notes:** This descopes CONS-01 (send G-code) + success-criterion #2 → recorded as deferred; Phase 8
console is CONS-02 only. User confirmed the descope.

## Console filtering

| Option | Description | Selected |
|--------|-------------|----------|
| Mirror Mainsail fork toggles | Opt-in regex toggles: Hide temperatures / Timelapse / prompt commands | ✓ |
| No filtering | Raw feed only | |
| + user-defined custom regex | Add Mainsail's custom `consolefilters` | deferred |

**User's choice:** Filtering like his Mainsail fork (`/mnt/e/claude/personal/github/mainsail` @ `76fcbd2`,
which he'd just committed). Built-in toggles mirrored; custom user regex filters deferred (keyboard-heavy).
**Notes:** Fork's design — raw `notify_gcode_response`/`gcode_store` stream stays independent of the
display filter so a future prompt engine isn't starved — captured as an architecture constraint (D-04).

## Macro screens / structure

| Option | Description | Selected |
|--------|-------------|----------|
| Three screens | System list (manage visibility) → Bookmarked list (launcher) → Execution popup | ✓ |
| Single combined screen | One macro screen with inline run | |

**User's choice:** Three separate screens, described verbatim (System list with check/select +
underscore hidden by default; Bookmarked list = everyday launcher; Execution popup = name + params +
Execute/Cancel gutter).
**Notes:** Execution popup IS the deliberate action gate (no separate ConfirmGuard). Drawer "Macros"
tile opens the Bookmarked list; System list reached from there. (Nav assumptions stated and confirmed.)

## Macro parameters

| Option | Description | Selected |
|--------|-------------|----------|
| A — Freeform | One text field, type `KEY=value` | |
| B — Auto-detected fields | Parse macro gcode body for `params.X` + `\|default()`, generate fields | ✓ |

**User's choice:** B — "that's the best we can do." Accepts it's heuristic (Klipper macros don't
formally declare a param schema). Mainsail uses the same approach; fork referenced.

## Keyboard triage (PRIM-02 resolution)

| Option | Description | Selected |
|--------|-------------|----------|
| Keyboard only on macro execution popup | Alpha keyboard for string params; NumpadPage for numeric | ✓ |
| Keyboard on console too | (Not needed — console is read-only) | |

**User's choice:** The macro execution popup is the one place the on-screen keyboard is allowed
(for string params). Console needs no keyboard (read-only).

---

## Claude's Discretion

- Exact console scrollback bound, auto-scroll stick-to-bottom behavior, timestamp display.
- Focus/Field/Gutter composition of the 4 screens (no mockup exists; designed within the grammar/laws).
- Execute/Cancel gutter intent-colors (per THEMING safety doctrine; finalize in UI phase).
- Whether the Console gets its own drawer tile (likely) vs shared structure.

## Deferred Ideas

- **CONS-01 (send arbitrary G-code)** — deferred to a later phase; REQUIREMENTS.md to be updated.
- **User-defined custom regex console filters** — power-user, keyboard-heavy; built-in toggles only.
- **Macro Prompt Protocol** (`action:prompt_*`) — Phase 12; raw stream kept clean to support it.
