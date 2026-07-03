# Font Picker

Choose the typeface jiib uses for either its interface or its live data readouts. The two pickers work identically; only the font library differs.

**Getting there:** Home → System → App Settings → **Interface font** row (opens the Interface font picker) or **Data font** row (opens the Data font picker).

## The screen

The Focus card shows the currently selected font's display name rendered in that font at full size — the card itself is the live preview. A caption above names what the font applies to. Tapping any row in the list immediately selects that font and re-renders the Focus card in it; the whole app switches to the new face at the same moment.

The list contains every bundled font for the picker's kind. Each row renders the font's name in that font, so you can compare faces directly without selecting them. The current selection is highlighted.

The foot bar has one button: **Back** (accent, returns to App Settings).

## Options & controls

### Focus card

- **Preview text** — the selected font's name, rendered in that font and scaled to fill the Focus card body. Updates immediately on every tap. There is no separate Apply step.
- **Caption** — static explainer above the preview:
  - Interface font picker: "Choose the font for buttons, labels, and titles."
  - Data font picker: "Choose the font for live numbers (temps, positions, progress). Monospaced for steady digits."

### List — Interface font (16 fonts)

Each row shows the font's name in its own face. Tap to select. Defaults to **Geist**.

| Font | Notes |
|---|---|
| Geist | Default; geometric sans-serif |
| Aldrich | Single weight |
| Arvo | Slab serif |
| Asimovian | Single weight |
| Bakbak One | Single weight |
| Carlito | Metric-compatible with Calibri |
| Faustina | Serif |
| Genos | |
| Goldman | |
| Kanit | Thai-origin sans |
| Noto Sans | Broad Unicode coverage |
| Oxanium | |
| Rasa | Gujarati-origin serif |
| Roboto | |
| Sarpanch | |
| Zilla Slab | Slab serif |

### List — Data font (10 fonts)

All entries are genuinely monospaced with tabular numerals — digits in live readouts (temperatures, positions, progress percentages) stay column-stable regardless of value. Defaults to **Geist Mono**.

| Font | Notes |
|---|---|
| Geist Mono | Default |
| Anonymous Pro | |
| Cascadia Code | Microsoft OFL |
| Courier Prime | |
| Datatype | |
| Kode Mono | |
| M PLUS 1 Code | |
| Nova Mono | Single weight |
| Share Tech Mono | Single weight |
| Space Mono | |

### Foot bar

- **Back** — returns to App Settings. The selection you made is already saved; there is no cancel.

## Plumbing notes

All fonts are bundled static TTFs — no network access required. Variable-axis fonts are not used (variable-font selection requires API 26; jiib's floor is API 23). Selection is written to the app-wide DataStore via `AppContainer.setInterfaceFont` / `setDataFont` on the process-lifetime write scope, so a selection survives navigation immediately.

## Related

[App Settings](app-settings.md) — the parent screen where font rows appear.
