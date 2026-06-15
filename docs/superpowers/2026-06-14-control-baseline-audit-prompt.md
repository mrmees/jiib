# Next-session kickoff prompt — Control/Button baseline audit

> Saved 2026-06-14. Paste the block below to start the next session. This is the contents-side
> successor to the FocusFrame Phase-2 region-framing work (merged PR #2, `add29041`): Phase 2
> centralized the region FRAME (`RegisteredRegion`); this pass centralizes the CONTROLS inside it.

---

Let's plan and run a system-wide CONTROL/BUTTON baseline audit on dinghy-display — the
contents-side successor to the just-merged Phase-2 region-framing work (RegisteredRegion
centralized the region FRAME; now centralize the controls inside those regions).

SCOPE
IN: every interactive CONTROL system-wide, wherever it lives (foot bar, field, AND inside the
focus body) — OutlinedControl-based action buttons, Sort/Filter selector groups
(SortFilterControlRow), increment steppers (IncrementPicker/AdjusterPanel), toggles
(OutputToggleControl etc.), the 004 Scrubber, dense value cells. The contents of the FOCUS are
fair game.
OUT (do NOT touch this pass): field LIST ITEMS / ListRow rows — those are a SEPARATE field-list
audit. The Extrusion page is excluded entirely. Decide in/out for ColorWheel/ColorSwatchGrid and
the docked e-stop.

GOALS (two intertwined)
1. Inventory EVERY in-scope control and collapse individualized padding/styling to defined
   component CLASSES with ONE baseline for how a control is drawn / aligned / sized / shaded /
   colored / labeled / stated. buttons, selectors, and toggles must EACH have a class.
   ** PRIMARY OBJECTIVE: every control class holds 1U HEIGHT (the established uDp unit-grid
   standard) by default unless I specifically say otherwise — and its padding / insets / inner
   spacing should CORRESPOND to (derive from / be expressed relative to) the 1U measurement,
   not arbitrary fixed dp, consistent with the U-relative / ratios-only law and R23/R24
   (icon at 0.6U). **
2. Give every NAMED control a defined title / icon / severity(intent) / type, stored in ONE
   place, so standards apply uniformly. Surface every control MISSING an assigned icon as an
   owner ASK-list — do NOT pick glyphs yourself (icon law, docs/ui_design/CLAUDE.md); source
   against img/material-icon-bucket.json.

START BY BRAINSTORMING with me — do NOT write code. First produce a complete control INVENTORY
+ TAXONOMY (classes + attribute schema incl. STATE styles: default/pressed/selected/disabled/
pending, and a MANDATORY a11y label/contentDescription per control), THEN we decide the collapse
strategy. Explicitly EXCLUDE ListRow/field rows and the Extrude screen from the inventory.

CONSTRAINTS / CONTEXT TO READ FIRST
- docs/ui_design/{CLAUDE.md, COMPONENTS.md, THEMING.md, LAYOUT.md} — R5/R8 four-class intent
  (Intent: Danger/Warn/Go/Accent/Neutral; Back=accent+first), R11 type ramp, R23/R24 LocalUnitDp
  icon sizing, UAT-5 1U control cap + the All-1U ruling, the unit U / U-relative + ratios-only
  law, "dense cells drop labels", "square the smallest buttons", §7b stroke/floor/spacing table,
  token-only law (THEME-01).
- designsystem/control/OutlinedControl.kt + Intent — the existing primitive to consolidate ONTO
  (build on it, don't reinvent).
- designsystem/components/{SortFilterControlRow, FootButtonBar, IncrementPicker, AdjusterPanel}.kt
  and the Scrubber.
- Memory: [[dinghy-component-class-workflow]] (the operating principle); the PAUSED spacing-scale
  pass in [[dinghy-focus-frame]] (already inventoried ~200 padding sites by role — RESUME from it,
  don't redo); [[dinghy-command-catalog-drift]] + docs/commands/catalog.json + CommandRegistry +
  CommandCatalogDriftTest (REUSE for command-firing controls, don't duplicate title/icon);
  [[dinghy-never-pick-icons-ask]], [[dinghy-check-img-source-assets]].

KEY DECISIONS to settle in brainstorming: (a) where the single source lives — extend
CommandRegistry/DinghyIcons vs a new ControlSpec catalog; (b) the stable per-control identity/key
for named controls vs one-off contextual ones; (c) the attribute schema (style + state + a11y);
(d) how control padding/insets/inner spacing express RELATIVE to 1U (U-relative fractions vs the
current fixed dp), and where those shared values live; (e) the icon-assignment leg = owner
ASK-list workflow. Don't regress the Phase-2 region work — controls are now flush and the region
owns the frame, so a control should look IDENTICAL inside-focus vs outside-focus.

WORKFLOW: superpowers brainstorm → writing-plans → subagent-driven-development (GSD off). Run the
final spec AND plan past Codex (gpt-5.5/xhigh) via the non-reaping background path
([[codex-rescue-bg-dispatch-dies-midstream]]) before executing; alert me on criticals. Builds run
Windows-side via E:\Android\gw.bat; verify pure style-resolution with host tests + UAT the look on
BOTH flox (armeabi-v7a, 0a64b42e) and moto (arm64-v8a, ZY22LBDRM9). Implementers stage explicit
paths, never `git add -A`. HARD RULE: never pick an icon — ASK me.
