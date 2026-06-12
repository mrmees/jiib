# jiib — design philosophy & non-negotiables

## Design philosophy (bake into every screen)
- **Focus / Field grammar (Gutter removed in the jiib redesign).** Every redesigned screen is
  built from two regions — Focus (one primary item, square visual content centered) and Field
  (a divisible info/control surface, typically a scrollable list + `FootButtonBar`). Portrait
  stacks them full-width; landscape is a single grid — Focus | Field as 50/50 columns.
  Either region may be omitted. No persistent status bar — context lives inside a region.
  **The Gutter is NO LONGER a first-class region** for new screens: its jobs are rehomed to a
  foot-of-list `FootButtonBar` (per-screen actions), a floating `FloatingEStop` overlay (Stop,
  printing-only), and the System page (power / device settings). Pre-redesign screens still use
  the Kotlin `ScreenScaffold.gutter` slot for backward compatibility — that slot is preserved
  in the code but the grammar no longer names it a first-class layout region.
  See the rewritten `LAYOUT.md` for the full two-region law. See `COMPONENTS.md` for the
  component-class catalog (ListRow, DetailCard, FillMeter, FootButtonBar, FloatingEStop,
  SortFilterControlRow and more).
- **Layout non-negotiables (see LAYOUT.md ⚠ section):** (1) everything tabular on one shared
  grid — region divides align with foot-button-row edges; (2) aspect ratios are sacred — a
  square must render square (use `aspect-ratio`, center square content in its cell); the
  portrait-stack / landscape-side-by-side layout difference is the mechanism that protects this;
  (3) no hardcoded sizes — every dimension is a %, `fr`, `aspect-ratio`, or container-relative
  unit, never absolute px (except hairlines, the touch floor, and the `--fs` text step). **Plus
  the unit `U`:** every vertical element in a redesigned screen is an integer number of units U
  (DPI-derived from landscape content height, constant through rotation). See `LAYOUT.md §"The
  unit U"` and `COMPONENTS.md §4`.
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
- **Button intent = color, by SAFETY of the action — the FOUR-CLASS scheme (R5, 2026-06-12;
  supersedes the old C1/C5/D-10 assignments).** Buttons (never list rows) are FILLED — fill is what
  says "button." **Red/stop** = could be destructive (cancel, e-stop, disable steppers) ·
  **Amber/warning** = could be destructive but part of the process (toolhead jog, load/heat
  filament, resets) · **Green/go** = the screen's EXPECTED action (Print, Load, Save, accept) ·
  **Accent** = neutral items and plain navigation (Back, Home). The old white/neutral-outline
  button intent is RETIRED. Overridable per case. See THEMING.md for the full law + worked examples.
- **Back = ACCENT, FIRST position (R5/R8, 2026-06-12; supersedes D-10's neutral-Back).** Plain
  navigation wears the accent, and Back is always the **first (start-aligned) button** in a
  `FootButtonBar`, app-wide — muscle memory holds. A Back/Cancel that DISCARDS pending input is a
  cancel-with-loss and stays **stop/red** (the old C7 logic carries over). The long-deferred
  app-wide Back sweep is a checklist column in the 2026-06 normalization audit.
- **Icons: never the same glyph twice on one screen.** If you'd repeat one, use a 1–3 letter
  text label instead (e.g. "XY"/"Z" homes vs. arrows; chevrons for Z vs. arrows for the XY pad).
- **Icons are real Material Symbols by default** — `IconRef.Ligature` (rendered from the bundled
  font) when the glyph is present, or an OFFICIAL Google vector drawable (path data verbatim, never
  hand-traced) when the bundled font is too old to carry it. Hand-authored custom drawables are ONLY
  for genuinely-custom printer-domain glyphs Material Symbols lacks (nozzle, bed, bed-tilt, spool).
  The shape-coded status indicators (square-✕ `StatusStop` / triangle) ARE in the font and are NOT custom. Retires
  D-17's "no Material Symbols font" stance — the font is already a shipped dependency used app-wide.
  (The "never the same glyph twice on one screen" rule above still holds.)
- **🚫 NEVER create an icon or choose a glyph independently — ASK. (Owner law, 2026-06-07.)** Claude does
  NOT invent custom drawables and does NOT pick which Material Symbol/ligature represents a function on
  its own. The track record on this is bad — Claude messes it up almost every time (Phase 18.3: invented
  a fake side-view spool instead of using the owner's `img/spool.svg`; earlier phases mis-chose glyphs).
  The procedure when a screen needs an icon:
  1. **Check first** — is a glyph already selected for this function (in the registry `DinghyIcons`, the
     hi-fi mockups, an existing screen, or a source asset in `img/`)? Run `ls img/` + grep the repo for
     `*.svg` and the registry before doing anything. If one exists, USE it — never substitute.
  2. **If nothing is already selected, STOP and ASK Matthew** which glyph/asset to use. Do not guess, do
     not author a custom drawable, do not pick "a reasonable Material Symbol." Wait for his answer.
  3. Only author a hand-made custom drawable when Matthew has explicitly approved doing so for a specific
     printer-domain glyph — and even then, prefer converting a source SVG he provides.
  This rule overrides any "be proactive / use sensible defaults" instinct for icon/glyph selection.
- **Square the smallest buttons.** When a Field stacks multiple button rows, the shortest row's
  height should equal its per-column width (equal spans) so the smallest targets are square.
- **Dense cells drop labels.** At ≥3 columns (portrait) / ≥6 (landscape), a cell shows a single
  icon or ≤3-char value scaled to ~75% of its constraining dimension — no text labels
  (`FootButtonBar` buttons keep icon+label; they are 1-per-row-third, not dense cells). When a
  cell must show a value AND its source, overlay the value on a large background glyph (e.g. the
  big axis letter behind the live X/Y/Z value).
- **Every screen keeps an explicit exit.** There is no global nav gesture (the swipe-up App
  Drawer was deleted in Phase 28); navigation is explicit — the waterfall root reaches screens,
  and each screen's `FootButtonBar` carries Back (first position). A screen without a foot bar
  must still have an explicit way out. (Supersedes the old "scrollable Fields suppress the
  swipe-up drawer" rule, which guarded an affordance that no longer exists.)
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
- **Token carve-out: macro-authored PromptMarkup author-hex (D-03) AND the color-reactive spool spiral
  (18.3 D-10).** The Macro Prompt Protocol (Phase 12) renders a Klipper macro author's inline
  `<color:#hex>`/`<bgcolor:#hex>` text runs as the author's EXACT literal hex — NOT a role token. This is
  a *bounded* exception to the tokens-only law: the hex is content DATA the author chose, not chrome. It
  is scoped to `PromptMarkupText` text runs ONLY — all prompt chrome (dialog bg, header, button
  outlines/intents, close, toasts, Field) stays token-routed, and the semantic button styles still map
  to tokens (`promptStyleColor`). **The same carve-out covers the custom spool glyph's filament spiral
  (Phase 18.3, converted from the owner's front-view `img/spool.svg`):** the spool's wound-filament
  **spiral** (the coil seen through the body windows) is tinted by the loaded filament's actual color
  (the Spoolman-spool-color precedent made literal, D-02/D-05/D-10) — true hex, never clamped. That
  reactivity is scoped to the **SPIRAL ONLY**; the spool body disc, the spiral's neutral **keyline**, and
  all surrounding chrome stay token-routed (`--text`/`--text2`/`--outline`), and the empty-spool fallback
  draws no spiral (pure token chrome — an empty windowed disc). `brandTint`'s WCAG clamp was considered
  and **rejected** for the spiral (it would distort the true color; legibility comes from the keyline,
  not distortion) — `brandTint` stays for brand chrome only. The Phase-21 conformance audit treats BOTH
  as sanctioned, not a violation.
  See THEMING.md → "Carve-out: macro-authored PromptMarkup author-hex". (2026-06-04; 12-05 · 2026-06-07; 18.3-03.)
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

## Visual language
- Type: Geist + Geist Mono (tabular numerals for live data). Type ramp: see THEMING.md §"The
  type ramp" (R11 — 20sp list/button default).
- Accent: seed-generated signature color (the user's). Heat = the CAUTION/warning color (D-13), no
  longer a nozzle/bed identity — temperature identity rides `directional.temperature` (= accent) +
  the data pool. Go/Stop: green/red (user-overridable; shape carries safety). Surfaces are
  pure-neutral (D-16).
- Shape: soft — 22dp cards, 16dp controls, pill chips; status safety carried by SHAPE
  (square-✕ `StatusStop` = stop, triangle-! = caution; go is shapeless). See THEMING.md.
- Motion: alive but cheap — progress fills, ring draws on; NO continuous breathing/looping
  animation (Adreno-320 budget). "Glow" as a real blur is not on the API floor; glow tokens render
  as static alpha treatments (R6 — a true cached glow is aspirational polish, not law).
- North star of record: the as-built Spoolman screen + sketch sources
  (`.claude/skills/sketch-findings-dinghy-display/sources/`) + THEMING.md. The old hi-fi bundle
  (`reference/hifi.css`, `Print Status Hi-Fi.html`, `images/`) is HISTORICAL — superseded by the
  2026-06-09 jiib redesign; do not build from it.
