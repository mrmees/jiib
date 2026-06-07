# Dinghy Display — project notes

## Design philosophy (bake into every screen)
- **Focus / Field / Gutter layout grammar.** Every screen is built from three regions —
  Focus (one primary item, with square visual content centered), Field (a divisible
  info/control grid), Gutter (touch actions). Portrait stacks them full-width (~40/40/20,
  gutter up to 2 rows); landscape is a single grid — Focus | Field as 50/50 columns with a
  full-width gutter row sharing the same column lines. Either Focus or Field may be omitted.
  No persistent status bar — context lives inside a region. See LAYOUT.md.
- **Layout non-negotiables (see LAYOUT.md ⚠ section):** (1) everything tabular on one shared
  grid — region divides align with gutter button edges/centers; (2) aspect ratios are sacred —
  a square must render square (use `aspect-ratio`, center square content in its cell); the
  1-row-landscape / 2-row-portrait gutter difference is the mechanism that protects this;
  (3) no hardcoded sizes — every dimension is a %, `fr`, `aspect-ratio`, or container-relative
  unit, never absolute px (except hairlines, the touch floor, and the `--fs` text step).
- **Fill the usable space.** This is primarily control-surface software that doubles as a
  nicely formatted display during standby / normal printing. Every screen should fill its
  usable area with useful, structured information. Empty/negative space is only acceptable
  when it is a *deliberate* focus choice, not a layout gap. Prefer a *useful control* over a
  passive status badge, and encode status as color on an existing element (e.g. axis labels
  green=homed / amber=unhomed) rather than spending a cell on an indicator. Choose items that
  are genuinely useful to the screen's current function.
- **Outline-led, touch-first controls.** Interactive elements are bounded by a 2px outline +
  soft glow on a transparent fill; primary/pressed states tint faintly with the accent. Touch
  targets are big (≥64px tall) — built for gloved, greasy, fat-fingered taps at arm's length.
- **Structured blocks over loose pills.** Prefer structured, aligned blocks of information to
  scattered chips.
- **Button intent = color, by SAFETY of the action** (esp. the gutter). Green = safe /
  non-destructive (accept, done, commit) · Blue/accent = ordinary physical command, no special hazard
  (home, unload, fan) · Amber(yellow) = proceed at peril / caution (toolhead jog motion, load/heat
  filament, reset, undo, unexpected live change) · Red = destructive or dangerous (stop/e-stop, disable
  steppers [loses homing], force-move armed, host interruption) · White/neutral = basic setting
  adjustment AND plain navigation. Overridable per case. See THEMING.md for the full spectrum + worked
  examples.
- **Back = NEUTRAL / outline (D-10), NOT red and NOT green.** Plain navigation changes no printer state,
  so it spends no safety color — Back is `--text` on `--outline`. Its gutter POSITION must be consistent
  app-wide (right-aligned/centered, never varying screen to screen). 15.1 applies this on the Move screen
  only; the **app-wide Back inventory/sweep is DEFERRED to Phase 15.2's conformance audit** — do NOT
  assume Back is already app-wide neutral. See THEMING.md → "Back = NEUTRAL / outline" for the full rule.
- **Icons: never the same glyph twice on one screen.** If you'd repeat one, use a 1–3 letter
  text label instead (e.g. "XY"/"Z" homes vs. arrows; chevrons for Z vs. arrows for the XY pad).
- **Icons are real Material Symbols by default** — `IconRef.Ligature` (rendered from the bundled
  font) when the glyph is present, or an OFFICIAL Google vector drawable (path data verbatim, never
  hand-traced) when the bundled font is too old to carry it. Hand-authored custom drawables are ONLY
  for genuinely-custom printer-domain glyphs Material Symbols lacks (nozzle, bed, bed-tilt, spool).
  The shape-coded status indicators (octagon/triangle) ARE in the font and are NOT custom. Retires
  D-17's "no Material Symbols font" stance — the font is already a shipped dependency used app-wide.
  (The "never the same glyph twice on one screen" rule above still holds.)
- **Square the smallest buttons.** When a Field stacks multiple button rows, the shortest row's
  height should equal its per-column width (equal spans) so the smallest targets are square.
- **Dense cells drop labels.** At ≥3 columns (portrait) / ≥6 (landscape), a cell shows a single
  icon or ≤3-char value scaled to ~75% of its constraining dimension — no text labels (the Gutter
  is exempt; it keeps icon+label). When a cell must show a value AND its source, overlay the value
  on a large background glyph (e.g. the big axis letter behind the live X/Y/Z value).
- **Scrollable Fields disable the swipe-up drawer.** The global nav affordance is a swipe-up App
  Drawer — EXCEPT on any screen whose Field is a finger-scrollable list (Files; console later): a
  full-canvas vertical-drag detector fights the list's own scroll. Those screens suppress the swipe
  and MUST keep an explicit exit in the Gutter (e.g. Files' "Cancel picker"). (2026-06-02.)
- **Content images fit, they don't crop.** A thumbnail/preview shown as content — including as a
  dimmed card background — uses `Fit` so the WHOLE image is visible, centered/letterboxed on the
  surface. `Crop` zooms into a center strip in the tall, narrow landscape Focus/Field panes (it
  violates sacred aspect ratios, LAYOUT §2). The one exception is a deliberately *circular* fill
  (the Print Status ring center), which is meant to be filled. (2026-06-02.)
- **Image-backed info card (shared grammar).** A panel pairing a preview with details uses ONE
  grammar: the dimmed thumbnail as the background with left-aligned, vertically-centered icon-led
  stat lines overlaid (the "overlay the value on a large background" rule applied to a whole card).
  Shared by Print Status' last-job card and the Files Focus — they differ only in WHICH fields show
  (Files Focus = future-print fields only: est time · filament · layers · height · size · modified —
  never elapsed/finished/status, which are job history). Filenames render in Geist Mono (a filename
  is a data value). (2026-06-02.)
- **Panel text fills the box.** A text block in a panel takes the FULL width of its cell, never an
  arbitrary inner ruler width. Size the text to fit up to the box; only a single line that genuinely
  overflows the full width may marquee-scroll. (Locking scroll lines to a narrower width reads as
  "stopping mid-screen.") (2026-06-02.)
- **Theme-able via tokens.** Dark-first baseline; everything routes through semantic tokens
  (`--bg/--surface/--text/--outline/--accent/--heat/--go/--stop` …). See THEMING.md.
- **Token carve-out: macro-authored PromptMarkup author-hex (D-03).** The Macro Prompt Protocol
  (Phase 12) renders a Klipper macro author's inline `<color:#hex>`/`<bgcolor:#hex>` text runs as the
  author's EXACT literal hex — NOT a role token. This is a *bounded* exception to the tokens-only law:
  the hex is content DATA the author chose (like a Spoolman spool color — the precedent), not chrome.
  It is scoped to `PromptMarkupText` text runs ONLY — all prompt chrome (dialog bg, header, button
  outlines/intents, close, toasts, Field) stays token-routed, and the semantic button styles still map
  to tokens (`promptStyleColor`). The Phase-21 conformance audit treats this as sanctioned, not a
  violation. See THEMING.md → "Carve-out: macro-authored PromptMarkup author-hex". (2026-06-04; 12-05.)
- **Keyboard carve-out: Save-name fields.** The alphanumeric keyboard is barred from *printer
  controls*, but a **pre-filled, optional-override name field for SAVING a named artifact** (the
  bed-mesh **Save-name** dialog, pre-filled `YY.MM.DD_HH.MM`) is a Settings-class text entry, NOT a
  hot-path control — the system keyboard is permitted there. The input is allowlist-validated
  (`[A-Za-z0-9_.-]+`, no whitespace/control/`;`/newline) before it can be saved. Future keyboard
  requests reference THIS established rule instead of reopening the debate. (Owner decision,
  2026-06-02; 09-05.)
- **Delete is scoped to the active print file, not idle-only.** This **supersedes** the original
  "Delete is idle-only" rule. During a print, ONLY the currently-printing file
  (`print_stats.filename`) is undeletable; every other file stays deletable, even mid-print. When the
  printer is idle, all files are deletable. The scoping check is the pure host-tested predicate
  `deleteAllowed(selectedPath, activePrintFilename, printState)` — applied at BOTH the FilesScreen
  delete-enabled gate and the FileBrowserHolder dispatch gate (one shared helper so the path-form
  match can't regress; the relative, no-leading-`gcodes/` form matches `print_stats.filename`). The
  old blanket "block all deletes during any print" behavior was a Phase-7 UAT defect (D-15).
  (2026-06-02; 09-06.)

- **Recovery routing: a socket reconnect now shows the full Syncing Splash.** This **supersedes** the
  earlier "socket `ConnectionState` is chrome, never routes" rule (the old D-05). As of Phase 13, the
  top-level route derivation (`ui/route/TopRoute.derive`) routes the **full recovery Splash** when the
  socket is mid-reconnect (`connection !is Connected`) with a config present and klippy otherwise Ready —
  in addition to the klippy-not-Ready case. So a **silent mid-print network drop is visibly non-silent**:
  a half-open WiFi drop (detected by the new OkHttp `pingInterval` keepalive) raises the Syncing Splash,
  then resyncs. This is SAFE — and does NOT bounce the user off their screen — because the shell nav
  state (`dest` + back-stack + in-progress calibration routine) was **hoisted above the Splash/Shell
  switch** (`ShellNavState`, owned by `RootController`); the user returns to the screen they were on, not
  Home. The recovery Splash also has a **minimum perceptible dwell** (~600ms, a `RootController`-owned UI
  latch that only delays HIDING the splash, never the actual recovery) so a fast recovery is still seen.
  The Splash's Disconnected/Error "Unreachable" surface (Retry + Edit connection) is preserved, so a
  printer that is simply OFF stays reachable, not an eternal dead "Syncing". (Matthew, 2026-06-03; 13-05.)

## Hi-fi visual language
- Type: Geist + Geist Mono (tabular numerals for live data).
- Accent: seed-generated signature color (the user's). Heat = the CAUTION color (D-13), no longer a
  nozzle/bed identity — temperature identity rides `directional.temperature` (= accent) + the data pool.
  Go/Stop: green/red (user-overridable; shape carries safety). Surfaces are pure-neutral (D-16).
- Shape: soft — 22px cards, 16px controls, pill chips; status safety carried by SHAPE (octagon=stop,
  triangle=caution; go is shapeless). See THEMING.md.
- Motion: alive — progress fills + sheen, ring draws on (static glow; no continuous breathing — Adreno-320 budget).
- Files: `hifi.css` (tokens + components), `Print Status Hi-Fi.html` (design canvas).
