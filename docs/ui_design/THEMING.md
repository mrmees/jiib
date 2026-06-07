# Theming — Dinghy Display (canonical token-law)

> **This is UI LAW (D-13).** Every later phase builds against this doc. It is the canonical,
> in-repo description of the **as-built** Phase-15.1 theme model — reconciled to the actual
> Kotlin theme code (`app/src/main/java/works/mees/dinghy/theme/`), not to any earlier wireframe.
> Where this doc and the code ever drift, the code (`ThemeTokens`, `TokenBridge`, `SeriesColor`,
> `StatusSlot`, `OklchRamp`) is authoritative and this doc is the bug.
>
> **Deep rationale lives in the sibling repo** `../theme_theory/COLOR-SYSTEM.md` (the generative
> color-system design-theory: seed→palette derivation, the contrast-ranked data pool, status-by-shape,
> palette modes, the sequential ramp). That prose is POINTED TO, not vendored in here — it is design
> theory in a separate repo that will drift, and downstream agents read `docs/ui_design/`, not the
> sibling. This doc carries the operative rules; COLOR-SYSTEM.md carries the "why." **Where this doc
> (encoding CONTEXT D-05/D-06/D-07) and COLOR-SYSTEM.md disagree on accent-leads, THIS DOC WINS** — the
> owner overrode the generator's defaults (nozzle = accent, not pool[0]; data never borrows status
> colors in Colorful). See the N-series and directional sections.

User customization is a first-class feature. Light/Dark is the baseline; a user can go further and
override colors. To keep that cheap, **every component references semantic role tokens — never a raw
color.** A theme is one resolved `ThemeTokens` value: a single user **seed** drives the WHOLE chrome
(accent / surfaces / text), plus an independently-editable contrast-ranked **data pool** and three
user-editable **status** colors, at a dark/light polarity and an S/M/L text-size. (The one sanctioned
raw-color exception is the macro-author PromptMarkup carve-out, below.)

## How themes are applied (generate-and-cache, not attribute-flips)

A theme is **generated in Kotlin and cached as a fully-resolved, baked token set**, not a runtime swap
of CSS variables. The pipeline:

1. **Seed → palette.** A host-pure generator (`theme/Palette.kt`, a bit-for-bit port of the sibling
   `color.js`) takes the user seed + dark/light flag + palette mode and produces the chrome
   (surfaces/text/accent/status) **plus** the contrast-ranked data **pool** and the directional hues —
   all as OKLCH string hexes.
2. **Bridge → dinghy tokens.** `theme/TokenBridge.kt` maps the generator output onto dinghy's role
   tokens and **derives the in-between tiers** (the `-soft` / `-line` / `-glow` alpha variants) in
   Kotlin. It also applies sparse user overrides (status + pool indices) and re-derives the directional
   standards (D-07, below).
3. **Bake to sRGB once.** `theme/BakedTokens.kt` bakes every OKLCH literal to an sRGB `Color`. **OKLCH
   renders WRONG on API < 26** (the Nexus-7 floor), so `ThemeTokens` only ever holds resolved baked
   `Color`s — never an oklch string.
4. **Resolve + cache.** `theme/ThemeResolver.kt` produces the immutable `ThemeTokens`. It is toolkit-
   agnostic: Compose reads the `Color`s directly; classic Views read `.toArgb()`. The `@Immutable`
   annotation lets Compose skip recomposition.

A user's **custom theme** is the same mechanism — change the seed/overrides/mode, regenerate, the whole
UI follows with no component edits. There is **no hand-picked per-role override of the chrome**; the
seed drives everything. The independently-editable surfaces are the **data pool** (per-index override)
and the **three status colors** (D-03, below).

## Role tokens (the `ThemeTokens` field set)

Token names below are the historical `--token` aliases; the authoritative names are the `ThemeTokens`
Kotlin fields they map to (in parentheses). Values shown are illustrative dark/light reference points —
they are now **seed-generated per theme**, not fixed literals.

| Token | Role | `ThemeTokens` field |
|---|---|---|
| `--bg` | app background | `bg` |
| `--bg-2` | sunken well / inset | `bg2` |
| `--surface` | raised surface (cards, screen body) | `surface` |
| `--surface-2` | raised +1 (tracks, wells) | `surface2` |
| `--surface-3` | raised +2 | `surface3` |
| `--text` | text strong | `text` |
| `--text-2` | text muted | `text2` |
| `--text-3` | text faint | `text3` |
| `--hair` | decorative hairline (alpha) | `hair` |
| `--outline` | **interactive control bound** (the affordance edge) | `outline` |
| `--outline-2` | control, emphasised/hover | `outline2` |
| `--accent` | signature color — primary/motion (**USER-OVERRIDABLE via seed**) | `accent` |
| `--accent-2` | accent, brighter (text/icon) | `accent2` |
| `--accent-soft` / `--accent-line` / `--accent-glow` | accent tint / outline / glow (alpha) | `accentSoft` / `accentLine` / `accentGlow` |
| `--heat` | **CAUTION color** (proceed-at-peril) — see note | `heat` |
| `--heat-soft` / `--heat-glow` | caution tint / glow (alpha) | `heatSoft` / `heatGlow` |
| `--go` | success / confirm (**USER-OVERRIDABLE**, D-03) | `go` |
| `--go-soft` / `--go-glow` | go tint / glow (alpha) | `goSoft` / `goGlow` |
| `--stop` | danger / destructive (**USER-OVERRIDABLE**, D-03) | `stop` |
| `--stop-soft` / `--stop-glow` | stop tint / glow (alpha) | `stopSoft` / `stopGlow` |
| `--edge-glow` | neutral control glow (alpha) | `edgeGlow` |
| (the pool) | contrast-ranked **data** colors, `pool[i]` | `pool: List<Color>` |
| (directional) | temperature / xy / z identity colors | `directional: Directional` |
| `--r-screen/-card/-ctrl/-pill` | corner radii (30/22/16/999 px) | `rScreen/rCard/rCtrl/rPill` |
| `--fs` | user text-size multiplier (S≈1.0 / M≈1.15 / L≈1.32) | `fs: Float` |
| (palette mode) | the active rendering mode | `mode: PaletteMode` |

### Surfaces are PURE-NEUTRAL (D-16)

Background and surface tiers are **achromatic** — no cool/blue tint baked into the chrome. (The older
hi-fi reference carried a small cool chroma on surfaces; that is **superseded**. Surfaces carry no hue
of their own so the seed accent + data pool + status colors are what the eye reads, against a neutral
ground.)

### `--heat` is the CAUTION color, NOT heater identity (D-13)

`--heat` / `ThemeTokens.heat` is the **caution / proceed-at-peril** role color. It is **no longer a
nozzle/bed "amber heat" identity color.** Heater / temperature IDENTITY is carried by
`directional.temperature` (= the accent) and the data **pool** — never by `--heat`. (`StatusSlot.Caution`
maps to `heat`: in dinghy, `heat` *is* the caution color.)

### Status colors are USER-OVERRIDABLE (D-03)

`stop` / `go` / `caution(=heat)` are **fully user-editable** via the Theme Editor (same per-slot
override mechanism as data-pool slots, keyed by `StatusSlot.key` — `"stop"/"caution"/"go"` — which
ride the same persisted `String→Long` override map as the integer pool indices, and cannot collide
with them). A user *may* even make `stop` green. **This is safe because shape + icon + position carry
100 % of the safety meaning** (see the Status Shape Vocabulary). The one exception: **High-Contrast
mode force-overrides status back to stoplight RYG** regardless of the user's override (the CVD/
accessibility escape hatch — see Palette Modes).

## Button intent = color (semantic, by SAFETY of the action)

**Color is determined primarily by the SAFETY of the action** — a spectrum safe → ordinary → caution
→ dangerous. This especially governs the **gutter** (primary actions). Pick the color by asking "how
risky is this tap?", not by the kind of widget.

- **Green** (`--go`) — **safe / non-destructive**: accept, done, commit.
- **Blue / accent** (`--accent`) — an **ordinary physical command with no special hazard**: home,
  unload filament, toggle a fan.
- **Amber / caution** (`--heat`, the caution family) — **proceed at peril**: anything that moves the
  toolhead or drives heat/filament where a mistake can crash or burn — jog motion, load/heat filament —
  plus reset / undo / unexpected live change.
- **Red** (`--stop`) — **destructive or dangerous**: stop / e-stop, disable steppers (loses the homing
  state), force-move while armed, host interruption.
- **White / neutral** (`--text` on `--outline`) — basic setting adjustment / secondary follow-up,
  **and plain navigation** (see Back, next).

### Back = NEUTRAL / outline (D-10)

**Back is NEUTRAL / outline — never red, never green.** By the earns-color grammar, plain navigation
changes no printer state and so spends **no safety color**: it is `--text` on `--outline`. (This
supersedes the old assignments that put Back on the destructive-red or success-green slots entirely —
neither applies to plain navigation.)

**Back-position-consistency rule:** the Back control occupies a **consistent gutter location app-wide**
— right-aligned (centered also acceptable), never varying screen to screen — so muscle memory holds.

> **HONEST DEFERRAL — 15.1 applied Back-neutral on the MOVE screen ONLY.** Phase 15.1 *established*
> the Back = neutral/outline + consistent-gutter-position RULE and applied it on the **Move** screen.
> The **app-wide Back inventory/sweep** — auditing and conforming every existing surface's Back control
> to this rule — is **DEFERRED to the Phase 15.2 conformance audit**. Nothing here implies Back is
> already app-wide neutral; it is not. **→ 15.2 todo: app-wide Back-control sweep.**

These are defaults, overridable per case, but keep them consistent — color *is* the affordance signal.

### Conformance criteria — the C-series (15.2 D-10 guided core-screen review)

> These criteria were surfaced by the **D-10 guided on-device review** of the core daily-driver
> screens (15.2-05) and are now LAW. They sharpen the "Button intent = color" and Status rules into
> **testable conformance checks** so the 15.2 app-wide sweep (and every future phase) audits against
> them. Each maps to a column / finding in `.planning/phases/15.2-…/15.2-AUDIT.md`.

- **C1 — Expected physical action is ACCENT, not caution/danger.** A button that performs a screen's
  EXPECTED physical action uses the **accent** intent — not caution/amber, not danger/red. (Reinforces
  "accent = an ordinary physical command with no special hazard": jogging an axis on the *Move* screen
  is the expected action of that screen, so it is accent, NOT caution. Caution/danger are reserved for
  genuinely hazardous or destructive taps, not the screen's own reason-to-exist.)
- **C2 — The increment picker is a 3-cell pattern.** Any increment/step picker is `[decrement] [center
  value display] [increment]` (three cells), with two interaction modes:
  - **(a) SHARED increment** (multiple controls share one step value): the **+/- buttons step the
    increment** through a predecided list, and **tapping the center value** scrolls up through the
    selection list.
  - **(b) SINGLE-MEASUREMENT increment** (the step applies to one value): **tapping the value performs
    the adjustment** and the **arrows perform the action**.
  *(The component to realise this is deferred — see AUDIT R1 / Phase 17.)*
- **C3 — Vertical adjustments use a vertical arrangement when the orientation allows it.** A control for
  a vertical quantity (e.g. **Z**) must NOT sit in a horizontal row when the active orientation
  (landscape vs portrait) layout affords a vertical arrangement. (A layout/density rule — also recorded
  in LAYOUT.md.)
- **C4 — A control that ENABLES a catastrophic state is FILLED with the STOP color while active.** When
  a control arms a dangerous/catastrophic mode (e.g. **force-move unlock**), its active state is **filled
  with the stop color** (not merely outlined) — the filled stop-red is the unmistakable "you are now in a
  dangerous mode" signal. (Complements the shape signal, e.g. the open-padlock silhouette.)
- **C5 — The screen's NATURAL PRIMARY ACTION takes ACCENT, and may be context-dependent.** The single
  action that is the reason the user came to the screen wears the **accent** intent, and which action
  that *is* may depend on state. Examples: **Temperature** — *Presets* is the accent action when not
  heating, but *Cooldown* becomes the active/accent action once heating; **Files** — *Print file* is the
  accent action.
- **C6 — Config/settings-type surfaces are EXEMPT from the ≥64px touch minimum and should be DENSIFIED.**
  Settings-class surfaces (Settings, Theme editor, and similar config pages) are a deliberate
  **close-interaction** use case — held in the hand, not read across the room — so they are **exempt from
  the ≥64px "super-touch-friendly" minimum-target rule** and should be **densified**: tighter rows,
  toggles / dropdowns / popups, fit-on-one-page. The **print-control surfaces remain fully bound by the
  ≥64px touch-friendly rule** — C6 does not relax them. (A layout/density rule — also recorded in
  LAYOUT.md.)
- **C7 — BACK is Neutral ONLY for plain navigation; a discarding/rejecting Back stays RED.** Plain
  navigational Back is **Neutral/outline** (D-10). But a Back/Cancel that **DISCARDS pending input** or
  **REJECTS a pending result** is a cancel-with-loss and stays **Danger/red** (`stop`). (This is why the
  Task-1 sweep correctly left `MeasuredWeightPage` and `ScanConfirmCard` Backs red — they discard a
  pending measurement / reject a pending scan result. No code change needed; this documents the rule.)
  See the `OutlinedControl`/`Intent` docstring for the matching code-level statement.

**Worked examples (the v1 panels), as built:**
- *Move gutter* — **All / home** (accent — the primary physical command) · **Disable** (red, un-homes)
  · **Back** (**neutral/outline**, D-10).
- *Move jog pad* — directional arrows. The **OUTLINE carries group identity** — the XY pad outline reads
  `directional.xy` (= `pool[0]`) and the Z-row outline reads `directional.z` (= `pool[1]`) (D-08); the
  home buttons' outlines likewise wear their group's directional color. The **ICON color carries STATE**:
  gray (unavailable) / caution (normal jog) / red (force-move armed). **Force-move (D-12):** a **green
  closed-padlock when SAFE**, a **red open-padlock when ARMED** — the lock **open/closed silhouette** is
  the redundant non-color signal.
- *Extrude gutter* — **Load** (caution, heats + drives filament) · **Unload** (accent) · **Back**
  (**neutral/outline**, D-10).

## Status Shape Vocabulary — shape IS the safety mechanism (D-01/D-02)

Status meaning is carried by **shape first, color second.** Color is the redundant channel; the
silhouette is the safety mechanism (legible in grayscale / CVD / at a glance). This is **NOT** shaped
touch targets — every element keeps its current form (rect buttons, round dots, plain letters); a small
**glyph** rides next to the status signal.

| State | Glyph | Notes |
|---|---|---|
| **stop** | **octagon-✕** (`ic_status_octagon`) | the universal "stop" silhouette |
| **caution** | **triangle-!** (`ic_status_triangle`) | the universal "warning" silhouette |
| **go / ok / homed** | **NONE — color only** | benign states stay quiet; **no circle glyph in dinghy** |

**Coverage = safety-critical only.** Only `stop` (octagon) and `caution` (triangle) get a glyph,
everywhere status appears — Print-Status dot, Move homed/unhomed axis letters + force-move, Stop/e-stop/
power buttons, console severity (ERROR=octagon, WARNING=triangle). Benign `go`/ok/homed states are
**color-only** (no glyph) so a normal print stays calm — the salience ladder: calm state quiet,
attention states pop. *(This adjusts the sibling spec's "go = circle": go has NO glyph in dinghy.)*

The three glyphs MUST read as genuinely distinct **silhouettes** (octagon vs triangle) so shape — not
just the inner mark — carries meaning at a glance. **The status icon's COLOR encodes state; a
surrounding control OUTLINE may encode a separate axis (e.g. Move's directional group color).** The
**lock open/closed** silhouette is its own shape signal for force-move armed/safe (D-12).

## The N-series rule — distinguishable data colors (D-05)

Any "Nth distinct data color" (graph traces, multi-bar readouts, any list needing per-item distinct
colors) reads its color from `ThemeTokens.seriesColor(i)` — a pure, host-testable helper both Compose
and classic-Views share. **The sequence is ALWAYS accent-led — `seriesColor(0) == accent` in EVERY
mode** — so the lead/most-watched series keeps its identity across a palette-mode switch. Past index 0
the rule depends on the active `PaletteMode`:

| Mode | Sequence (then wraps infinitely) |
|---|---|
| **Colorful** | `accent, pool[0], pool[1], pool[2], …` — accent + the full data pool |
| **Simple** | `accent, text` alternating (beyond 2, identity rides on label / dash / marker) |
| **High-Contrast** | `accent, stop, caution(=heat), go, text` cycling — stoplight-coded data |

- **Line-1 = accent in all three modes.**
- **Colorful NEVER uses status colors for data** (a normal trace is never stop-red — data wraps within
  `accent + pool`, cleaner than borrowing status slots).
- **High-Contrast deliberately presses RYG status colors into data duty** (the pool has collapsed; safe
  because actual status signals carry shape-glyphs + fixed position, so a red *line* ≠ the octagon
  e-stop).
- **No user-facing cap — wrap and repeat (D-09).** The series wraps forever via modulo; the generator's
  `maxItems` is an INTERNAL pool-size boundary only and does NOT cap how many series colors a consumer
  may request. (Empty-pool guard: Colorful falls back to accent rather than divide-by-zero.)
- **Negative index is a caller bug** — `seriesColor` throws `IllegalArgumentException` for `i < 0`.

## Directional standards — accent leads (D-06/D-07)

The temperature / XY-plane / Z-plane identity colors are **accent-led**, re-derived in ONE place
(`TokenBridge.build`):

| `directional` field | Color | Used by |
|---|---|---|
| `temperature` | **= accent** (D-07) | temperature surfaces, the nozzle trace + readout |
| `xy` | **= `pool[0]`** | the jog-pad XY outline; **bed** shares this `pool[0]` |
| `z` | **= `pool[1]`** | the Z-row outline; **chamber** shares this `pool[1]` |

**Rationale (owner):** in a whitespace-heavy minimal UI the personalization (accent) is easy to lose,
so the most-watched live channel (the nozzle/temperature) wears the **accent** — keeping the user's
accent prominent on every screen. accent survives ALL palette modes, so `temperature` never collapses
to text in Simple/High-Contrast. Bed/chamber dual-tag onto xy/z's pool slots (they never co-occur on
screen, so identity never collides in view).

> ⚠ **SUPERSEDES the Phase-15 wiring AND the sibling spec** (which pinned the nozzle trace to the
> first data-pool slot via warmth-tagged hues). This is the owner override referenced at the top:
> where COLOR-SYSTEM.md says data leads and the nozzle takes the leading pool slot, THIS DOC's
> accent-leads rule (D-05/D-06/D-07) wins — the nozzle wears the accent.

## Palette modes (D-04)

A theme renders in one of three modes (`ThemeTokens.mode: PaletteMode`):

| Mode | Status colors | Data series | Character |
|---|---|---|---|
| **Colorful** (default, D-15) | from the pool **by warmth**, **user-overridable** (D-03) | accent + full pool | full-color data |
| **Simple** | collapse to **text color** (shape + position still carry them) | accent / text alternation | near-monochrome |
| **High-Contrast** | **forced to stoplight RYG**, *overriding any user override* (the CVD/accessibility escape hatch) | accent / stop / caution / go / text cycle | maximum legibility |

**Fixed RYG status is NOT the default doctrine — it is the High-Contrast escape hatch ONLY.** In
Colorful, status comes from pool warmth and the user can override it (safe because shape carries
safety). In Simple, status is text-color (shape + position carry it). Only High-Contrast pins status to
stoplight red/amber/green and ignores user overrides.

## Sequential-data sub-system — the bed-mesh OKLCH ramp (D-11)

Sequential height data (the **bed mesh**) is **its own coloring sub-system, separate from the
categorical pool AND from the status red/amber/green language.** It uses a locked, perceptually-uniform
OKLCH ramp (`theme/OklchRamp.kt`): a **blue → teal → yellow** viridis/cividis-style ramp kept
**deliberately OFF pure red and pure green** so a tall mesh spot reads as "tall," NOT as a "FAILED"
spot. (A red/green height ramp would falsely imply pass/fail.) 32 stops, baked ONCE to sRGB ARGB ints
(no OKLCH math in `onDraw` — the Adreno-320 floor), indexed/lerped cheaply per cell by
`render/BedMeshHeatmapView`. The ramp is golden-locked against an independent oracle
(`tools/oklch-ramp-oracle.mjs`); see `../theme_theory/COLOR-SYSTEM.md` §11 for the rationale.

## Shape & type tokens

| Token | Value | Use |
|---|---|---|
| `--r-screen` | `30px` | screen / bezel radius |
| `--r-card` | `22px` | cards |
| `--r-ctrl` | `16px` | controls / buttons |
| `--r-pill` | `999px` | pills / chips |
| `--ui` | `'Geist', system-ui, sans-serif` | all UI text |
| `--mono` | `'Geist Mono', ui-monospace, monospace` | live numeric data (tabular) |
| `--fs` | `1.15` (M) | **user text-size multiplier** — S≈1.0 / M≈1.15 / L≈1.32 |

`--fs` (`ThemeTokens.fs`) scales type app-wide (the S/M/L user setting); **M is the larger default**
tuned for reading a phone at arm's length (~3 ft). Every type size is `fsSp(baseSp, fs)` (`baseSp * fs`)
— never a bare px. The OS `fontScale` is neutralised at the Compose root so `--fs` never double-applies.
`--fs` is the SOLE text-size authority (D-04).

## Carve-out: macro-authored PromptMarkup author-hex (D-03)

> Bounded exception to "every component references semantic role tokens — never a raw color."

The **Macro Prompt Protocol** (Phase 12) lets a Klipper macro author render rich text inside a
prompt via inline `PromptMarkup` runs: `<color:#rrggbb>…</color>` and `<bgcolor:#rrggbb>…</bgcolor>`.
Those runs render the author's **exact literal hex** (`Color(0xFF000000 or #rrggbb)` in
`PromptMarkupText.kt`) — they do **not** route through the role tokens. **This is intentional and
correct, not a token-purity violation:**

- The hex is **content DATA supplied by the macro author**, the same class of thing as a Spoolman
  spool's filament color — not app **chrome**. A theme remap must not silently recolor a value the
  author deliberately chose (a red "DANGER" run must stay red in light mode too).
- The carve-out is **bounded to markup text runs ONLY.** Every piece of prompt *chrome* — the
  dialog background, header, button outlines/intents, the close control, severity toasts, the
  scrollable Field — stays fully token-routed. The author-hex `Color(...)` constructor in
  `PromptMarkupText` is the **only** raw color in the entire prompt UI.
- **Precedent:** the Spoolman spool-color detail border (Phase 11) already renders an
  author/inventory-chosen color directly as content; this is the same principle applied to inline
  text runs. The protocol's *semantic* button styles (`primary`/`info`/`warning`/`error`/`success`)
  do still map to tokens (`promptStyleColor` → `accent`/`accent-2`/`heat`/`stop`/`go`).

### Second instance: the color-reactive spool glyph's filament spiral (Phase 18.3, D-10)

The custom **spool glyph** (`DinghyIcons.LauncherSpool` and the SpoolScreen surfaces, converted from the
owner's front-view source art `img/spool.svg`) is the **second named instance** of this same "filament
color is DATA, not chrome" exception — not a new rule, an extension of *this* carve-out. The glyph's
**wound-filament spiral** (the coil that shows through the spool-body windows) is tinted by the **loaded
filament's actual color** (Spoolman active-spool `colorHex` → gcode `filament_colors[0]` fallback →
empty spool), sourced from inventory/slicer data rather than the role tokens. That is sanctioned here
for exactly the same reason as the PromptMarkup author-hex above: a theme remap must not silently
recolor a physical value the user/slicer deliberately chose (white PLA stays white in dark mode; a red
filament stays red).

- **The carve-out is scoped to the filament SPIRAL ONLY.** The spool **body disc** (and its window
  frames), the spiral's **keyline**, and **all surrounding chrome** stay fully token-routed (`--text` /
  `--text2` / `--outline`, etc.). Only the spiral stroke (a flat color, or a two-stop runtime gradient
  along the coil for multi-color filament, D-04) carries the raw filament hex. The empty-spool fallback
  (D-03) draws no spiral at all, so a no-color state shows pure token chrome (an empty windowed disc).
- **True color, never clamped (D-05).** The spiral renders the **exact filament hex** — it is never
  pushed toward a contrast floor. Legibility is solved by **framing, not distortion**: a thin neutral
  (token) keyline outlines the coil so its edge reads against any background even when the fill ≈ surface
  (white PLA on light, black on dark).
- **`brandTint` was considered and REJECTED for the spiral.** The `brandTint` WCAG-3:1 contrast-floor
  helper (`theme/BrandTint.kt`) was evaluated for spiral legibility and deliberately rejected: clamping
  the filament color toward a contrast floor would **lie about the true color** (the very thing this
  carve-out exists to preserve). `brandTint` remains correct for **brand chrome only** (Splash lockup,
  About wordmark) — never for content-data color like the filament spiral.

The Phase-15.2 / later theme-UI conformance audit should treat author-hex inside PromptMarkup text runs
**and the spool glyph's filament spiral** as sanctioned by this carve-out, not flag them.

## The control language (the outline rule)

Interactive elements are a **2px outline + soft glow on a transparent fill**; primary/pressed
states tint faintly with the relevant color token. The outline bounds the touch target without
competing with content. See `.ctl` and its variants in `reference/hifi.css`.
