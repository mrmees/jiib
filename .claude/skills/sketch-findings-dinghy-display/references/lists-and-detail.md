# Lists, Browse & Detail — Spoolman

From sketch **002** (winner: Variant C · sort-row + filter-picker). The collection/detail archetype.
Visual north star = the existing Spoolman page (reference mock in `sources/.../reference-mock.html`).

## Design Decisions
- **Two-pane (landscape):** LEFT = `DetailCard` (of the selected item) + the **control rows**; RIGHT =
  the scrollable list + a `FootButtonBar`. Portrait stacks: detail (50%) → controls → list (50%).
- **Sort and Filter are two different verbs** — never conflated (the design session wrongly flattened
  filters into sorts):
  - **Sort** = in-place toggle that reorders (Name `match_case` / Date `calendar_clock` / Weight
    `balance`), with a direction arrow on the active one.
  - **Filter** = opens a **list of options** (Material `experiment` / Color `palette` / Brand
    `storefront`); accent when a value is set.
- **Field-takeover picker (reusable pattern):** tapping a filter swaps the **Field** (list area) in
  place to that facet's option list (no separate screen) — pick returns filtered; tap the filter again
  to close; `delete_sweep` clears. The Field is already a list, so it just changes what it's listing.
  This pattern recurs (file-type filters, macro-param pickers).
- **Control rows** = a leading **type-icon tile** (`sort` / `filter_list`) then the option tiles. No
  text headers, no spool count. Icon tiles fill ~70% U; the leading tile is recessed (`--bg-2`),
  non-interactive.
- **DetailCard:** color-reactive border (`--ring` = the filament's color), header = identity +
  top-right **Weigh** (`scale`, = re-weigh to update remaining) and **Delete** (`delete`, red). **No
  Spoolman icon** on the Spoolman page. Console-style readout rows with leading icons
  (storefront/palette/thermostat/calendar_add_on).
- **FillMeter:** horizontal bar tinted by the filament's real color, `735 / 1000 g · 74%` — the
  "how much is left" read.
- **Foot buttons** = Home (`home`) · Scan (`qr_code`) · **Load/Unload (conditional)**: show **Unload**
  (`expand_circle_down`, neutral) only when the selected spool *is* the loaded one, else **Load**
  (`expand_circle_up`, accent).
- **ListRow:** color dot · title (`material · name`) + brand subtitle · trailing weight (mono).
  Translucent (content); selected = accent border + tint.

## CSS / HTML Structures
```html
<div class="home land">
  <div class="col"> DetailCard + sortRow + filterRow </div>      <!-- left -->
  <div class="col"> listBlock(spoolRows | pickerOptions) + foot </div>   <!-- right; field-takeover -->
</div>
```
```css
.detail { border:2px solid var(--ring, var(--hair)); }   /* color-reactive */
.fill-bar > i { background: <filamentColor>; box-shadow: 0 0 12px -2px currentColor; }  /* tinted */
.ctlrow { display:grid; grid-template-columns:auto 1fr 1fr 1fr; }   /* leading type-tile + 3 options */
.typetile { background: var(--bg-2); }   /* recessed, non-interactive */
.sbtn2.filter.on { color: var(--accent-2); }   /* active filter: accent, NO value label */
```
```js
// Field-takeover: filter tile toggles the picker in place
onPick = f => { fieldMode = (fieldMode===f) ? 'spools' : f; render(); };
```

## What to Avoid
- Flattening filters into sort buttons (the design-session mistake).
- Quick-pick filter chips above the list (redundant with the control row).
- A picker header with a back-pill + facet-icon — unnecessary; the filter tile stays visible to toggle.
- Value labels under active filter tiles, group-label words, "N spools" counts — all cut.

## Origin
Sketch 002. Sources: `sources/002-spoolman-reconcile/index.html` + `reference-mock.html`.
