---
id: console-macro-page-ux-flow
created: 2026-06-02
source: user request (Phase 8 on-device UAT session)
priority: medium
resolves_phase: null
---

# Console/Macro page UX flow improvements (post-Phase-8 revisit)

Captured by Matthew during the Phase 8 on-device UAT, after the Macros & Console
functional core was proven working on flox + the live Ender 5 Plus. These are
follow-up UX refinements to the console/macro surfaces built in Phase 8 — the
functional core works; these make the flow nicer to live in. Revisit the
console + user-macro pages together and address:

1. **Console filters sub-page.** Develop a dedicated sub-page for the console
   filters instead of (or in addition to) the inline opt-in toggle row built in
   08-05. The current `ConsoleScreen` applies `ConsoleFilters`
   (HIDE_TEMPERATURES / HIDE_TIMELAPSE / HIDE_PROMPT_COMMANDS) as a render-time
   view via toggles in the Field; a single-purpose filters sub-page would give
   room to grow the filter set without crowding the console itself. Must stay
   within the Focus/Field/Gutter grammar + theming LAW (`docs/ui_design/`), and
   keep D-04 (raw list always survives — filters never mutate scrollback).

2. **Console ↔ user-macros navigation button.** Add a direct navigation control
   between the Console screen and the user (Bookmarked) Macro launcher, so you
   can hop between "watch the printer talk" and "fire a macro" without bouncing
   through the swipe-up App Drawer each time. Mind the icon-no-repeat law and the
   button-intent color grammar (accent = physical command, etc.).

3. **Auto-jump to Console after firing a macro.** Change the default behavior so
   that immediately after a user macro executes from the Execution popup, the app
   navigates to the Console page — so the user sees the macro's gcode responses /
   effect land live without manually opening Console. Consider whether this should
   be a setting (default on) vs hardwired; firing → console is the natural "did it
   work?" loop. Tie into the existing `when(dest)` nav in `AppShell` + the
   MacroHolder execute callback.

## Affected code (Phase 8)
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` (filter toggle row → sub-page)
- `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt`, `MacroExecutionPopup.kt`
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` (nav `when(dest)` + post-execute jump)
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` (any new Dest if the filters sub-page is a route)

## Notes
- Likely a small dedicated UI-polish phase (X.Y) or folded into a later console/macro phase.
- All three are additive UX over a working core — none block the Phase 8 functional-core gate.
