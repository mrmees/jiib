---
phase: 16
slug: home-print-status-redesign
status: superseded
superseded_by: docs/ui_design/
superseded_on: 2026-06-11
shadcn_initialized: false
preset: none
created: 2026-06-06
reviewed: 2026-06-06
---

# Phase 16 — UI Design Contract: Home / Print-Status Redesign

> Visual and interaction contract for the definitive, **state-driven** Home / Print-Status surface.
> This is NOT a greenfield design — it is governed by a pre-authored, binding design LAW
> (`docs/ui_design/CLAUDE.md` + `LAYOUT.md` + `THEMING.md`) and the Phase-16 staging note (the
> primary spec). This UI-SPEC is the *contract that reconciles* the staging note + CONTEXT decisions
> against that LAW, in this project's grammar (Focus / Field / Gutter, semantic tokens, `fsSp` scale).
> It does NOT restate the staging note verbatim — read the staging note first.

**Design LAW (read before implementing — supersedes any generic UI guidance):**
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables
- `docs/ui_design/LAYOUT.md` — Focus / Field / Gutter grammar; orientation; the interactive-grid flexible-tile rule (added this phase); C3/C6
- `docs/ui_design/THEMING.md` — semantic tokens; button-intent-by-safety; status-by-shape; `fsSp`; C1–C7
- `docs/ui_design/images/03-print-status.png` — binding visual target for the **Printing** state
- Staging note: `/mnt/e/claude/personal/github/parallel_dinghy/phase-16-home-status-redesign-staging.md` — the four-state model + per-state layouts (THE primary spec)

---

## Design System

| Property | Value |
|----------|-------|
| Tool | none (native Android — Jetpack Compose + classic Views hybrid per ADR-0001) |
| Preset | not applicable |
| Component library | In-house Dinghy design system (`docs/ui_design/`) — `OutlinedControl`, `ConfirmGuard`, `ScreenScaffold`, `ProgressRing`, `GraphView`, `PresetSelector`, `StatusSlot`/shape glyphs |
| Icon library | Bundled vector drawables (`res/drawable/*.xml`); status glyphs `ic_status_octagon` / `ic_status_triangle`; new babystep compress/expand glyphs (this phase) |
| Font | Geist (UI) + Geist Mono (tabular numerals for all live data) |

**No raw colors.** Every color routes through `LocalTokens.current` (Compose) / `.toArgb()` (Views).
The only sanctioned raw-color carve-outs (author-hex PromptMarkup D-03, Spoolman spool color) do **not**
apply on this surface.

---

## Layout Grammar (replaces generic Spacing Scale)

This project does NOT use a fixed px spacing scale — **ratio-only sizing is hard law** (LAYOUT
NON-NEGOTIABLE 3). Every dimension is a `%`, `fr`, `aspect-ratio`, or container-relative unit. The only
permitted fixed values are: hairline borders, the **≥64px touch-target floor**, and the `--fs` text step.

### Focus / Field / Gutter per state

| State | Focus | Field | Gutter |
|-------|-------|-------|--------|
| **Standby** | App icon (P16) + centered glance-list overlay (minimal, glanceable) | Adaptive **launcher grid** (Drawer = flexible/growing tile) | Preheat · Power (inert, design-only) — **no E-Stop** |
| **Printing** | Thumbnail base + progress ring overlaid + numeric progress/status at ring bottom (preserve `03-print-status.png` composition) | ONE framed print-stats list (icon-led rows) + **shortcut row** (Tune = flexible tile) OR **babystep row** (early-layer window) | Pause · Cancel · **E-Stop** |
| **Paused** | Same as Printing, **dimmed**, with a pause-icon overlay (Focus carries paused state) | Same as Printing (no extra pause-status row); babystep replacement still applies | Resume · Cancel — **no E-Stop** |
| **Terminal** (Complete / Cancelled / Error) | Result hero: clean preview/thumbnail (no ring, no dim, no result-icon overlay; app-icon fallback) | ONE framed print-stats element (reuse Printing stats-frame, omit live-only fields); **Error** appends ≤3 meaningful error lines (hidden if none) | Dismiss · Reprint |

- **Portrait:** Focus / Field / Gutter stack full-width (~40 / 40 / 20, tunable). Gutter up to 2 rows.
- **Landscape:** Focus | Field as 50/50 columns on the shared grid; full-width Gutter row below sharing the same column lines. (See `03-print-status.png` landscape.)
- **Aspect ratios are sacred** — the progress ring renders a true square in both orientations.

### Interactive-grid flexible-tile rule (PROMOTED TO LAW this phase — add to `LAYOUT.md`)

> When a grid **of user-interaction surfaces** has awkward leftover space, ONE explicitly chosen tile
> may grow so the remaining controls stay regular, touch-friendly, and predictable. **Scope: grids of
> interactive surfaces ONLY** — never stat grids, text lists, graphs, or non-interactive info frames.

- Standby launcher grid → flexible tile is **`Drawer`**.
- Active (Printing/Paused) shortcut grid → flexible tile is **`Tune`**.
- **Babystep row is EXEMPT** — it is a fixed dedicated 3-cell row, not a flexible grid.

---

## Typography

> **This project uses a fixed, design-system-defined type ramp — the `fsSp` scale — NOT ad-hoc
> per-screen size choices.** The ramp below is LAW-locked exactly as this project's spacing is a
> ratio-only system and its color is a semantic-token system. It is the project's canonical,
> pre-existing type scale (`docs/ui_design/THEMING.md` → `--fs` / `fsSp`), not a set of free choices
> made in this phase. **This phase introduces ZERO new font sizes — every size used is a *role* on the
> existing locked ramp.**
>
> **On the generic "5 tiers > max 4 sizes" heuristic — not applicable here.** The same checker
> correctly waived the 60/30/10 color heuristic (this project uses a semantic-token system) and the
> multiples-of-4 spacing heuristic (this project uses ratio-only sizing). A design-system-defined type
> scale is the **same category**: a LAW-locked scale, not a per-phase free choice. The ramp has **five
> roles by design**. The two smallest roles (Body 17–18sp + Metadata floor 15sp) are kept distinct
> *deliberately* because of the standing project lesson [[dinghy-font-sizes-too-small]] (Claude
> repeatedly sizes Dinghy fonts too small). Collapsing Body into Metadata — or dropping any tier to
> satisfy a "max 4" rule — would **violate the LAW** and re-introduce the exact too-small-font failure
> the ramp exists to prevent. **Authority for five roles:** THEMING.md `fsSp` scale +
> [[dinghy-font-sizes-too-small]]. The tier count stays at five.

Font sizing is the `fsSp(baseSp, t.fs)` scale ONLY — never a bare `.sp`. Honor the
[[dinghy-font-sizes-too-small]] floors. These are the **base** sizes (before the user `--fs` S/M/L
multiplier; M≈1.15 is the default).

| Role | Base size | Weight | Line height | Usage on this surface |
|------|-----------|--------|-------------|------------------------|
| Focus value (display) | **30sp+** | Geist Mono | 1.1× (tight — single-line hero) | Time-remaining hero, terminal result figure |
| Tabular stat | **26sp** | Geist Mono | 1.1× (tight — single-line value) | Stat-grid values (layer, filament, nozzle/bed, elapsed, finish-by, current Z, applied offset) |
| Title | **20–22sp** | Geist | 1.2× | Filename (Geist Mono — a filename is a data value), result label |
| Body | **17–18sp** | Geist | **1.4×** | Glance-list rows, launcher tile labels, gutter button labels |
| Metadata floor | **15sp (hard floor)** | Geist | **1.4×** | Stat-row captions (LAYER / NOZZLE / BED…), printer subtitle |

- **Body / Metadata line height = 1.4×** — declared so multi-line stat captions and glance-list rows
  wrap predictably without implementer guessing. Single-line numeric roles (Focus value, Tabular stat)
  stay tight (1.1×) so the hero/stat figures don't gain dead vertical space; Title is 1.2×.
- **Dense-cell label-drop rule:** at ≥3 columns (portrait) / ≥6 (landscape), interactive cells show a
  single icon or ≤3-char value at ~75% of the constraining dimension — no text labels (Gutter is exempt:
  it keeps icon + label).
- Live numeric data is **Geist Mono, tabular numerals** (no width jitter as digits change).

---

## Color (semantic tokens — by SAFETY of the action)

All values are **role tokens**, seed-generated per theme — there are no fixed hexes. Button intent =
color by safety (THEMING "Button intent = color" + C1/C5).

| Role token | Where it applies on this surface |
|------------|----------------------------------|
| `accent` (`--accent`) | Progress ring fill; **nozzle/bed temperature identity** (`directional.temperature` = accent — see CONFLICT below); the screen's natural primary action (C5); the Spoolman over-budget filament line (D-1c, attention-not-warning) |
| `go` (`--go`) | Resume (returns to safe running), Reprint **only if** styled as the natural primary (else neutral — see below) |
| `heat` (= caution, `--heat`) | Reserved for proceed-at-peril; **not used as a heater identity** here (D-13). Preheat is an ordinary physical command → **accent**, not caution |
| `stop` (`--stop`) | E-Stop, Cancel (destructive: ends the job), Power tile chrome (inert red stop-intent), Dismiss only if it discards (it does not — see below) |
| `outline` / `text` (neutral) | **Back-class / dismiss** plain navigation; launcher tiles; Tune; stat frame chrome |
| `pool[i]` / `seriesColor(i)` | Any multi-series data identity (none new this phase; ring is single-series accent) |

**Accent reserved for (explicit, never "all interactive"):**
- the progress ring fill,
- the live temperature identity (nozzle/bed current value — `directional.temperature`),
- the screen's single natural primary action per state (C5),
- the Spoolman over-budget filament line (attention emphasis, D-1c).

### Per-state button intents (the binding contract)

| Control | Intent | Rationale |
|---------|--------|-----------|
| **Preheat** (Standby) | **accent** | ordinary physical command, no special hazard (THEMING blue/accent) |
| **Power** (Standby) | **stop / red, INERT** | render as the drawer's red Power tile, nonfunctional in P16 (D-04) |
| **Pause** (Printing) | **accent** | ordinary physical command; fires immediately, no guard |
| **Cancel** (Printing/Paused) | **stop / red** | destructive — ends the job; routes through ConfirmGuard |
| **E-Stop** (Printing only) | **stop / red** + octagon glyph | dangerous; tap→confirm, long-press→immediate |
| **Resume** (Paused) | **go / green** | returns the job to a safe running state |
| **Dismiss** (Terminal) | **neutral / outline** | plain clearing nav; does NOT discard pending input (C7) — neutral, not red |
| **Reprint** (Terminal) | **accent** | the natural primary action of a terminal screen (C5); no guard |
| **Babystep Compress / Expand** | **accent** outline, **icon carries action** | live physical jog of Z; expected action of the row (C1) → accent, not caution |
| Launcher tiles (Files/Temp/Move/…) | **neutral / outline** | navigation |

**Back-position consistency (D-10):** any Back/Dismiss occupies the consistent app-wide gutter position
(right-aligned). Dismiss is neutral (C7 — it clears, it does not discard pending input).

### Status by SHAPE, not color alone (D-01/D-02)

- E-Stop button carries the **octagon** glyph (`ic_status_octagon`) in `stop` color.
- `Terminal(Error)` does **NOT** get special stop/error chrome beyond the error lines — it is the same
  visual treatment as Complete/Cancelled (staging note). No octagon on the terminal hero.
- A `caution` state (if any surfaces) uses the **triangle** glyph. `go`/ok/normal states are
  **color-only, no glyph** (no circle in dinghy) — a normal print stays calm.

---

## Motion

- Static glow only. The progress ring **draws on once** and the fill updates at the throttled ~2–4 Hz
  cadence. **NO continuous breathing / looping animation** (Adreno-320 fill-rate floor).
- Paused dim + pause-icon overlay is a static state, not an animation.
- One-shot cheap transitions are acceptable (e.g. a state change), but nothing that loops.

---

## Accessibility (contentDescription contract)

Icon-only controls on this surface MUST carry an explicit `contentDescription` for TalkBack — no
implementer gaps. (Labelled gutter buttons already announce via their visible text label.)

| Icon-only element | `contentDescription` (verbatim contract) |
|-------------------|-------------------------------------------|
| **Babystep Compress** | `"Compress — move nozzle closer to bed"` |
| **Babystep Expand** | `"Expand — move nozzle farther from bed"` |
| Babystep step-size center cell | `"Babystep step size, {value} millimeters — tap to change"` (e.g. `"Babystep step size, 0.05 millimeters — tap to change"`) |
| E-Stop octagon glyph | inherits the button's `"Stop"` label (button is labelled) — glyph is decorative (`null`) |
| Pause-icon overlay (Paused Focus) | `"Print paused"` (state announcement on the Focus, not a control) |

- The babystep Compress/Expand cells stay **icon-only visually** (no on-screen text labels — the
  glyphs carry direction). The strings above are the accessibility/TalkBack contract only.
- The two babystep glyphs MUST be distinct silhouettes (icon-never-twice rule) AND carry the distinct
  descriptions above so non-visual users can tell direction apart.

---

## Per-state interaction contract

### Standby
- **Glance metrics (Focus overlay, minimal):** Nozzle temp · Bed temp · **MCU/host temp OR host load**
  (discretion rule below) · Active spool remaining (hidden if Spoolman unavailable). **No** explicit
  connection-state line.
  - *Glance metric discretion (delegated to planner):* prefer a **real MCU/host temperature** when a
    usable `mcu`/`temperature_host`/`temperature_sensor` reading exists; **fall back to host load**
    (`proc_stat`/`system_info`) otherwise. Keep the list minimal/glanceable — never a dense stat dump.
- **Launcher order (fixed curated, P16):** Files · Temperature · Move · Extrusion · Calibration ·
  Spoolman (if available) · Macros (only if bookmarked macros exist) · Console (low-priority filler,
  only when useful to round out the grid) · **Drawer (always present, flexible/growing tile)**.
  - Forward stubs do **NOT** appear in the launcher grid — they live in the App Drawer only (D-02).
- **Gutter:** Preheat (accent) · Power (red, inert). No E-Stop.

#### Preheat — spool-aware (D-01)
- If Spoolman available AND active spool exposes filament temps
  (`SpoolmanFilament.settingsExtruderTemp` / `settingsBedTemp`) → fire `applyPreset` **directly** to
  those temps (no chooser). Guard each temp independently — fire whichever of nozzle/bed the spool
  provides.
- Otherwise (no Spoolman, or BOTH temps null) → open the Phase-5 **`PresetSelector`** (fixed
  PLA/PETG/ABS/TPU, keyboard-free).

### Printing
- **Focus:** preserve the `03-print-status.png` composition — thumbnail base + accent progress ring +
  numeric progress (`68%`) inside ring + time-remaining hero + filename + printer subtitle below.
- **Field stat rows (stable, placeholder when unavailable):** Nozzle temp · Bed temp · Elapsed /
  Remaining · Current layer / total (placeholder if unavailable) · Current Z · Applied Z offset (when
  non-zero OR during babystep window). **Nozzle + bed emphasized.**
  - **EXCLUDED from stats:** Speed % and Flow % (those belong under Tune / Phase 17).
- **Optional Spoolman print line** (hidden if Spoolman unavailable): job filament length vs available;
  informational; if available < required → render line in **accent** for attention (NOT warning/stop).
- **Shortcut row (flexible tile = Tune; no Drawer tile — drawer stays swipe-only mid-print):**
  | Spoolman | Bookmarked macros | Row |
  |----------|-------------------|-----|
  | available | yes | Tune · Temperature · Macros · Spoolman |
  | none | yes | Tune · Temperature · Macros · Console |
  | available | no | Tune · Temperature · Spoolman · Console |
  | none | no | Tune · Temperature · Console (**Tune grows**) |
- **Tune tile** = the Phase-17 Fine-Tune stub (D-03). **WebRTC** is the existing runtime-gated Webcam
  tile — no new stub.
- **Gutter:** Pause (immediate, no guard) · Cancel (ConfirmGuard) · E-Stop (tap→confirm, long-press→
  immediate; no special long-press progress affordance in P16).

#### Babystep row (early-layer window only; replaces the shortcut row)
- Shown only when layer data is available AND within the configured early-layer window. If layer data
  unavailable → babystep stays **hidden** (no time-based fallback).
- **Dedicated 3-cell row** (exempt from the flexible-tile rule and from the C3 vertical-arrangement
  concern — explicitly allowed here): `[ Compress ]  [ step-size value ]  [ Expand ]`.
  - **Compress** = drop / decrease gap / nozzle CLOSER to bed. **Expand** = raise / nozzle FARTHER.
    Icons only — **no text labels**; the two glyphs must be distinct silhouettes (icon-never-twice rule).
    Each carries its `contentDescription` from the Accessibility section.
  - Center cell shows the **step size only**; tapping it cycles `.02 → .05 → .10 → .15 → .20`.
  - The **current applied Z offset** lives in the **print-stats frame**, NOT in the babystep row.
- Intent: Compress/Expand = **accent** (expected physical action, C1); icon carries the action direction.
- Wiring (carried from 17-CONTEXT, verify on a live first layer): `SET_GCODE_OFFSET Z_ADJUST=±n MOVE=1`
  ↔ readback `gcode_move.homing_origin[2]`. **Session-only** — saving to config is NOT in scope.

#### Babystep app setting (lives under the Settings tile — Phase-15.2 four-tile IA)
- Overall app preference (not per-printer for P16). Feature-toggle-class item under **Settings**
  (Printers · Theme · Settings · About).
- Explicit enable/disable toggle (default **enabled**) + positive-integer layer-count input (default
  **5**) via the **standard numeric keyboard** (allowed: Settings is keyboard-permitted; the
  no-alphanumeric-keyboard LAW governs printer *controls*, not Settings — see
  [[numeric-keyboard-for-numeric-fields]]).
- **Persistence MUST route through `AppContainer.writeScope`** intent helpers, never
  `rememberCoroutineScope()` ([[dinghy-compose-write-scope-cancellation]]).

### Paused
- Focus = Printing focus, **dimmed**, with a pause-icon overlay. Field/toolset identical to Printing
  (no extra pause-status row). Babystep replacement still applies if in-window.
- Gutter: Resume (go/green) · Cancel (ConfirmGuard, red). **No E-Stop** (the user is already
  intentionally intervening; Cancel ends the job).

### Terminal (Complete / Cancelled / Error)
- Moonraker-derived only — **no app-remembered terminal state**. Visible until dismissed, reprint
  starts, or Moonraker reports another state. **Passive** (D-05): a mode of Home; does NOT yank the
  user from another screen — seen next time they navigate Home (honors G-A1 shell rule).
- Focus = result hero: clean preview/thumbnail (no ring/dim/result-icon overlay); app-icon fallback.
- Field = single framed stats element (reuse Printing stats-frame; omit live-only fields that no longer
  make sense; stable-placeholder policy for missing core stats).
- `Terminal(Error)` ONLY: append the last **≤3 meaningful printer error lines** from console/printer
  history; hide the area if none. **No** special stop/error styling beyond those lines.
- Gutter: **Dismiss** · **Reprint**.
  - **Dismiss** → `SDCARD_RESET_FILE`. May optimistically clear local terminal presentation. On
    failure → surface feedback (SeverityToast), do **not** silently lose terminal context. Allowed even
    after error; hot heaters do not block it. (Neutral intent — clears, does not discard input.)
  - **Reprint** → directly issues the existing print-start command on Moonraker's current/last file path
    (Moonraker-provided only, no local cached fallback). **No confirm guard**, does **not**
    `SDCARD_RESET_FILE` first. If printer not ready, command/state feedback handles failure. Available
    for complete/cancelled/error when a usable file path is exposed.

---

## Copywriting Contract

| Element | Copy |
|---------|------|
| Standby gutter primary | **Preheat** |
| Standby gutter (inert) | **Power** |
| Printing primary / hold-safe pair | **Pause** · **Cancel** · **Stop** (E-Stop button reads "Stop", octagon glyph) |
| Paused gutter | **Resume** · **Cancel** |
| Terminal gutter | **Dismiss** · **Reprint** |
| Cancel confirmation (ConfirmGuard) | Heading: **Cancel print?** · Body: **This stops the current job. The printer will not finish this print.** · Confirm: **Cancel print** (red) · Dismiss: **Keep printing** (neutral) |
| E-Stop confirmation (ConfirmGuard) | Heading: **Emergency stop?** · Body: **Immediately halts the printer (firmware E-stop). You will need to restart Klipper to print again.** · Confirm: **Emergency stop** (red) · Dismiss: **Cancel** (neutral) |
| Babystep step-size center cell | step value only (e.g. **0.05**) — no label |
| Standby glance — Spoolman absent | spool line hidden (no "no spool" placeholder) |
| Stat unavailable (placeholder) | em-dash **—** in the value slot (keeps the row + caption stable) |
| Terminal(Error) error area empty | area hidden (no "no errors" copy) |
| Preheat with no Spoolman temps | falls through to PresetSelector (no error copy needed) |

> No empty-state "no data" screens apply here: Standby IS the no-print state, and it is a dashboard, not
> an empty state. Missing individual stats use the em-dash placeholder, not empty-state copy.

---

## Conflicts & Resolutions (surfaced, not silently resolved)

1. **Mockup `03-print-status.png` shows nozzle/bed in AMBER; LAW says temperature identity = accent.**
   - The hi-fi mockup predates Phase-15.1's D-13 / N-series rules. `--heat` is now the **caution** color,
     NOT a heater identity (THEMING D-13); temperature identity rides `directional.temperature` (= accent).
   - **Resolution:** implement nozzle/bed temperature values in **`directional.temperature` (accent)**,
     NOT amber. The mockup's amber is superseded by the current LAW. (This matches how Phase-15.1 already
     rewired the existing PrintStatusScreen nozzle/bed readouts.) Update the artboard during the
     Documentation Merge.

2. **Babystep horizontal 3-cell row vs C3 (vertical quantity → vertical arrangement).**
   - C3 says a vertical quantity (Z) should not sit in a horizontal row when the orientation affords
     vertical. The staging note **explicitly grants an exception** for the babystep row.
   - **Resolution:** the babystep row is an **explicit, documented C3 exception** (staging note authority).
     No conflict to escalate — record it in LAYOUT.md alongside the flexible-tile rule.

3. **Reprint as `go` (green) vs `accent` (C5 natural primary).**
   - Reprint is non-destructive (could read green) but is also the terminal screen's natural primary action.
   - **Resolution:** **accent** (C5 — the natural primary action takes accent), consistent with Files'
     "Print file" being accent. Dismiss stays neutral (C7).

---

## Documentation Merge Direction (part of this phase's deliverable)

Per the staging note + CONTEXT specifics — docs need not become strict declarative law but MUST explain
the state layouts + merge direction:
- `docs/ui_design/README.md` — replace the **stale Print Status section** with the four-state model.
- `docs/ui_design/LAYOUT.md` — promote the **interactive-grid flexible-tile rule** to hard law (scope:
  interactive grids only) + record the babystep horizontal-row C3 exception.
- Print-Status **artboards** (`images/03-print-status.png` + new state artboards) — update to the
  four-state model and the accent-temperature resolution.
- **Do NOT edit `THEMING.md`** (no color/shape-status rule changes this phase).

---

## Registry Safety

| Registry | Blocks Used | Safety Gate |
|----------|-------------|-------------|
| n/a (native Android, no shadcn / no component registry) | none | not applicable |

---

## Checker Sign-Off

- [ ] Dimension 1 Copywriting: PASS
- [ ] Dimension 2 Visuals (Focus/Field/Gutter conformance, sacred aspect ratios): PASS
- [ ] Dimension 3 Color (semantic tokens only, intent-by-safety, status-by-shape): PASS
- [ ] Dimension 4 Typography (`fsSp` design-system ramp — 5 LAW-locked roles, font floors honored, body line height declared): PASS
- [ ] Dimension 5 Layout (ratio-only sizing, ≥64px touch floor, flexible-tile rule scoped): PASS
- [ ] Dimension 6 Registry Safety: PASS (n/a)

**Approval:** pending
