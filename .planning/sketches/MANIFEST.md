# Sketch Manifest — jiib Visual Redesign

## Design Direction
A fundamental redesign moving jiib from KlipperScreen-style **tile grids + hub-and-spoke nav + a
space-eating gutter** to a **lists-first, conditional-waterfall** app. The home is **one morphing
Focus/Field surface** (no gutter) where printer *state* surfaces the relevant actions as a list; the
gutter's jobs are rehomed to **foot-of-list action buttons** + a **floating printing-only e-stop** +
a separate **System page**. Visual north star = the existing Spoolman page (preserved reference mock at
`docs/ui_design/sketches/spoolman-filament-manager-reference.html`). Built on **real jiib semantic
tokens** (Geist/Geist Mono, dark+light, S/M/L text-size) — see `themes/default.css`. Full direction:
`.planning/notes/2026-06-09-jiib-redesign-direction.md`. Component-class catalog:
`.planning/notes/2026-06-09-component-classes-catalog.md`.

## Reference Points
- **Spoolman / Filament Manager** mock (the locked look) — `docs/ui_design/sketches/spoolman-filament-manager-reference.html`
- Existing UI LAW — `docs/ui_design/` (Focus/Field grammar survives; **Gutter is removed**)
- Token law — `docs/ui_design/reference/hifi.css` + `THEMING.md`

## Sketches

| # | Name | Design Question | Winner | Tags |
|---|------|----------------|--------|------|
| 001 | waterfall-home | Does one morphing Focus/Field surface feel coherent across idle/printing/terminal, no gutter? | **A · Focus-as-card** | layout, navigation, home |
| 002 | spoolman-reconcile | How do true filter facets + sort coexist in Spoolman's left grid, with edge-fade list + tokens? | _pending_ | spoolman, filtering, components |
