# Phase 8: Macros & Console — Functional-Core Complete - Context

**Gathered:** 2026-06-02
**Status:** Ready for planning

<domain>
## Phase Boundary

The two "escape hatches" that round out the functional core: a **read-only Console** that
monitors printer output, and a **Macros** feature (manage which macros surface, bookmark them, and
run them with parameters). Closes the functional-core-complete gate.

**In scope:**
- Read-only Console feed — severity-colored history, backfilled from `server.gcode_store`, updated
  live via `notify_gcode_response`, bounded scrollback, with opt-in noise filters.
- Macros across three screens: System list (manage visibility), Bookmarked list (everyday launcher),
  Execution popup (params + run).

**Out of scope (this phase):**
- **Console text input / sending arbitrary G-code (CONS-01)** — DEFERRED (see Deferred Ideas). The
  project's goal is easy touch input for typical functions, not a troubleshooting terminal.
- User-defined custom regex console filters (keyboard-heavy, power-user) — DEFERRED.
- Macro Prompt Protocol (`action:prompt_*` interactive dialogs) — that is Phase 12. This phase only
  keeps the raw stream architecturally clean so Phase 12 can tap it.
</domain>

<decisions>
## Implementation Decisions

### Console — read-only monitor
- **D-01:** The Console is **read-only this phase.** No on-screen keyboard, no command send. It is a
  passive feed the user can watch if they want.
- **D-02:** Content = command/response history with **severity coloring** (`!!` → error, `//` →
  echo/warning, plain → normal), **backfilled from `server.gcode_store`** on connect AND reconnect
  (do not silently drop lines that arrived while disconnected), **live via `notify_gcode_response`**,
  with **bounded scrollback**. (Exact bound + auto-scroll-stick-to-bottom = planner's call;
  sensible default e.g. ~1000 lines, stick-to-bottom unless the user has scrolled up.)
- **D-03:** **Opt-in noise filters**, mirroring the Mainsail fork (default OFF): **Hide temperatures**
  (`^(?:ok\s+)?(B|C|T\d*):`), **Hide Timelapse** (the fork's timelapse rule set), **Hide prompt
  commands** (`^(?:// )?action:prompt`). See canonical refs for the exact patterns.
- **D-04:** **Architecture constraint:** the display filter is a VIEW-layer concern only. The raw
  `notify_gcode_response` + `gcode_store` stream stays **upstream of / independent of** the console
  display filter, so a future prompt engine (Phase 12) and the backfill never get starved by what the
  console hides. (Directly mirrors the fork's commit `76fcbd2` design note.)
- **D-05:** Console scrollback is the **3rd Views-in-Compose scroll surface** (after Files + temp
  graph). Apply the Files scroll lesson from the 2026-06-02 polish pass: a real Android view in
  `AndroidView` over-measures and composites over neighbors unless given a **pinned height**
  (`BoxWithConstraints` → `.height(maxHeight)`) **+ `Modifier.clipToBounds()`**, and the global
  **swipe-up drawer gesture must be suppressed** on any finger-scrollable Field screen.

### Macros — three screens
- **D-06:** **System macro list** — displays ALL available macros (from `printer.objects.list` /
  config), with a check/select control per macro choosing which ones surface in Dinghy.
  **Underscore-prefixed macros are hidden by default** here (the real printers carry 96 / 60 macros,
  mostly internal helpers — MACRO-03). The user can reveal/select them.
- **D-07:** **Bookmarked macro list** — shows ONLY the user-selected macros; this is the everyday
  launcher. The **drawer "Macros" tile opens this screen**; a control on this screen reaches the
  System list to manage selection.
- **D-08:** **Macro execution popup** — shows the macro name, **auto-detected parameter fields**, and
  **Execute / Cancel** in the gutter. The popup **IS the deliberate action gate** — no separate
  ConfirmGuard layered on top of it.
- **D-09:** **Parameter detection = parse the macro's gcode body** for `params.X` references and their
  `|default(...)` values, generate one field per detected param (option B, chosen knowingly as "the
  best we can do" given Klipper macros don't formally declare a param schema). Mainsail does the same
  — use the fork as a reference for the parsing approach. Heuristic; acceptable to miss edge cases.
- **D-10:** The **macro execution popup is the ONE place the on-screen (system) keyboard is allowed**
  in printer controls — for string parameter values. **Numeric params should use the existing
  `NumpadPage`** rather than the alpha keyboard where the param is clearly numeric. This is the
  resolution of the PRIM-02 "triage console/macro-param input per-control" deferral.

### Layout / grammar
- **D-11:** No console/macros hi-fi mockup exists — layout is **designed fresh within the
  Focus/Field/Gutter grammar** and the design laws in `docs/ui_design/`. (Not pre-locked like Move/Temp.)

### Claude's Discretion
- Exact scrollback bound, auto-scroll behavior, timestamp display on console lines.
- Precise Focus/Field/Gutter composition of each of the 3 macro screens + the console screen
  (subject to the grammar + laws; will be firmed in `/gsd-ui-phase` if run).
- Gutter button intent-colors for Execute/Cancel (per the THEMING safety doctrine — macro execute is
  a physical command, likely accent/blue or amber given macros can move/heat; finalize in UI phase).
- Whether the console is its own drawer tile or shares structure — likely a new tile; planner decides
  alongside the Macros tile wiring.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Console filtering (the reference implementation)
- `/mnt/e/claude/personal/github/mainsail` @ commit `76fcbd2` — Matthew's Mainsail fork; the console
  filter set to mirror. Specifically:
  - `src/store/gui/console/getters.ts` — `getConsolefilterRules` builds the regex list: Hide
    temperatures `^(?:ok\s+)?(B|C|T\d*):`, Hide Timelapse (`timelapseConsoleFilters`), Hide prompt
    commands `^(?:// )?action:prompt`.
  - `src/store/gui/console/types.ts` — filter flags (`hideWaitTemperatures`, `hideTlCommands`,
    `hidePromptCommands`, custom `consolefilters`).
  - `src/components/settings/SettingsConsoleTab.vue`, `src/pages/Console.vue` — where the toggles surface.
  - Commit message documents the raw-stream-independent-of-filter architecture note (see D-04).

### Moonraker / Klipper data model
- `docs/moonraker-capabilities.md` — authoritative real-printer field catalog (Ender 5 `192.168.1.120:7125`
  / Ender 3 `192.168.1.121:7125`). Note: 96 / 60 macros observed (mostly underscore-internal). Build
  ONLY against confirmed fields. `server.gcode_store`, `notify_gcode_response`, and macro-body
  introspection (via `configfile` config) are research items — the catalog does not yet detail them.

### Design law (UI)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables (incl. the 2026-06-02 laws:
  scroll-Field ⇒ no swipe drawer; content images Fit; image-backed card; panel text full-width).
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar + the ⚠ non-negotiables.
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent-by-safety color doctrine, `--fs`.

### Requirements
- `.planning/REQUIREMENTS.md` — MACRO-01, MACRO-02, MACRO-03, CONS-02 (active this phase); CONS-01
  (to be moved to deferred — see Deferred Ideas); PRIM-02 (keyboard-triage, resolved by D-10).
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `command/CommandRegistry.kt`, `command/CommandDispatcher.kt`, `command/PrinterCommands.kt` — the
  established command path; register macro-run + any console commands here. Reuse the pending-state
  dispatch pattern from Phases 6/7.
- `render/RingBuffer.kt` — candidate backing store for the bounded console scrollback.
- `designsystem/NumpadPage.kt` — numeric param entry on the macro execution popup (D-10).
- `designsystem/ConfirmGuard`, `SeverityToast` (PRIM-04), `MaterialSymbol`, `ScreenScaffold` — the
  shared primitives; severity coloring vocabulary already exists.
- `ui/files/FileListView.kt` + `FilesScreen.kt` — the **proven RecyclerView-in-AndroidView pattern**
  (pinned height + clipToBounds) to copy for the console scrollback (D-05).
- `ui/shell/AppDrawer.kt` — the greyed "Macros" tile (`symbol = "code"`, `dest = null`) to wire; a
  Console tile likely added alongside.

### Established Patterns
- Capability gating via `state/DeriveCapabilities.kt` / `Capabilities.kt` — macros discovered from
  `printer.objects.list`; gate the Macros feature on macro presence.
- Toolkit-agnostic view-models exposing `StateFlow` (ADR-0001 hybrid) — console + macro holders follow
  the Phase-5/7 holder pattern.

### Integration Points
- The websocket session's `notify_gcode_response` subscription + a one-shot `server.gcode_store` read
  feed the console holder; macro list reads from `printer.objects.list` + config (`configfile`) for
  param-body parsing.
</code_context>

<specifics>
## Specific Ideas

- Console "like our Mainsail fork": passive monitor with opt-in noise toggles — see canonical refs.
- Three distinct macro screens (System list / Bookmarked list / Execution popup) — Matthew's explicit
  structure, captured verbatim in D-06..D-08.
- "Enter ANY parameters" → auto-detected fields (D-09), keyboard allowed only here (D-10).
</specifics>

<deferred>
## Deferred Ideas

- **Console text input / send arbitrary G-code (CONS-01).** Explicitly deferred — the console is
  read-only for now; text-send may return in a later phase. **ACTION: move CONS-01 from active to
  deferred in `REQUIREMENTS.md` and adjust Phase 8 success criteria (drop "type and send" criterion #2).**
- **User-defined custom regex console filters** (the fork's `consolefilters`) — power-user, keyboard-
  heavy; ship the built-in toggles only for now.
- **Macro Prompt Protocol** (`action:prompt_*` interactive dialogs) — Phase 12; this phase only keeps
  the raw stream clean (D-04) so it slots in.

### Reviewed Todos (not folded)
- `benchmark-harness-fairness-fixes.md` — matched on generic keywords only (bench harness, not
  macros/console). Not relevant to Phase 8.
- `status-progress-ring-dual-source-jump.md` — Phase-4 Status defect; unrelated.
- `macrobenchmark-module-wiring.md` — benchmarking infra; unrelated.
</deferred>

---

*Phase: 8-Macros & Console — Functional-Core Complete*
*Context gathered: 2026-06-02*
