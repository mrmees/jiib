# Phase 3: Design System & Theming Foundation - Context

**Gathered:** 2026-05-31
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the reusable visual + interaction **substrate** every later screen inherits, per the canonical
`docs/ui_design/` system (LAW) — so panels are assembled from a settled vocabulary, not redesigned ad
hoc. This phase translates a **web/CSS-idiom design system** (`oklch()` colors, CSS custom-property
tokens, `aspect-ratio`/`fr`/`clamp()`) into the **Compose + classic-Views hybrid** (ADR 0001).

Delivers:
- The **semantic-token theme system** — dark + light + a user-custom override (THEME-01) and the S/M/L
  text-size setting (THEME-02), every component referencing role tokens (never raw color).
- The **Focus / Field / Gutter** responsive layout grammar on one shared tabular grid, portrait
  (stacked) and landscape (Focus|Field 50/50 + full-width gutter on the same column lines) — sacred
  aspect ratios, ratio-only sizing (UI-01).
- The **outline-led, touch-first control language** with button-intent colors (UI-02; ≥64px targets).
- The core reusable **components/primitives**: full-screen **Confirm guard** (PRIM-03), the
  **single-setting scrubber/stepper page** (PRIM-01, keyboard-free numeric entry), the **severity toast**
  (PRIM-04), and the **progress-ring + line-graph render primitives** (the shared render/throttle surface
  the Print Status home (Phase 4) and the Temperature graph (Phase 5) both consume).
- Geist / Geist Mono bundled. Previewable on-device via a harness; wired to the Phase-2 spine where it
  shows live data.

**Explicitly NOT in this phase:** the foreground service, the real launcher/routing, the App Drawer
navigation, the Settings screen UI (theme/text-size/connection editing lands in Phase 4), the
command-dispatch primitive (PRIM-05, Phase 4), and any actual printer-control panel. This phase builds
the *substrate and a preview harness*, not the running app.

**Perf floor (hard):** Nexus 7 2013 / Adreno 320 / 2GB / armeabi-v7a / 1920×1200. Static glow OK; **no
continuous "breathing"/looping animation**. One-shot transitions allowed only if cheap.

</domain>

<decisions>
## Implementation Decisions

### Theming — token system (THEME-01, THEME-02)
- **D-01 — User-custom scope = accent + status roles only.** A user custom theme can recolor the
  personality/affordance knobs — `--accent`, `--heat`, `--go`, `--stop` (and reasonably `--bg`) — and the
  remaining ~30 role tokens **derive from the chosen base**. NOT a full all-token editor (avoids an
  unwieldy Phase-4 settings UI and unreadable themes), NOT just curated presets. The override *mechanism*
  is built here; the editing UI is Phase 4 (SET-01).
- **D-02 — Custom = override-on-a-base.** A custom theme picks a base (dark or light) and stores only the
  **token deltas** on top; any token not overridden inherits the base. Cheap to persist (deltas), and a
  half-configured custom theme is still fully usable.
- **D-03 — Bake oklch → sRGB once; pick custom in sRGB.** The authored dark/light `oklch()` values are
  converted to **exact sRGB** a single time (build-time or a checked-in token table) and stored as Compose
  `Color`. User-custom colors are picked with a standard **sRGB** picker. **No runtime oklch** (Compose's
  oklch/OKLAB color space is API 26+; the floor is 23) — keeps it API-23-safe and fast on Adreno.
- **D-04 — `--fs` (S/M/L) is the app's SOLE text-size authority.** Apply the `--fs` multiplier
  (S≈1.0 / M≈1.15 (larger default) / L≈1.32) to **dp-based** sizes so the OS accessibility `fontScale`
  does **not** double-apply. Predictable layout on a dedicated arm's-length printer screen, consistent
  with the "text fills its box / no hardcoded px / `clamp()`" layout rules. Persist the S/M/L choice
  (DataStore when persistence lands).

### Hybrid token bridge (Compose ↔ Views) — load-bearing
- **D-05 — One toolkit-agnostic `ThemeTokens` resolver is the single source of truth.** Resolved values
  (sRGB colors + shape/type/`--fs` values) live in a plain-Kotlin token set exposed as a `StateFlow`.
  **Compose** adapts it via a `CompositionLocal`; the **Views** layer (the line graph, later Files/Console)
  **collects the same flow**. One resolver, two thin adapters — mirrors ADR 0001's "view-models stay
  toolkit-agnostic" rule and the Phase-2 spine's StateFlow pattern. This is what makes "a theme is a token
  remap" true across BOTH toolkits (a theme swap recolors the Canvas graph too).
- **D-06 — Views repaint via push-tokens + `invalidate()`.** The `AndroidView` host hands the current
  `ThemeTokens` to the custom View; on a theme change it pushes the new set and calls `invalidate()`; the
  View re-reads token colors at draw time. No view recreation (cheap on Adreno; preserves graph
  ring-buffer state across a theme/orientation change).

### Preview / verification harness
- **D-07 — In-APK component gallery is the primary preview surface.** A real, installable screen renders
  every token/component × dark/light/custom × S/M/L so it's eyeballed on the **real Nexus 7** — matching
  the project's build-device-side, prove-on-real-hardware ethos and giving the graph perf proof a home.
  (Compose `@Preview` is fine for quick Compose iteration but is secondary — it can't run on-device or
  exercise the Views Canvas graph.)
- **D-08 — Gallery is the TEMP dev launcher this phase, gated out of release.** Since Phase 4 builds the
  real launcher/routing, the gallery is the app's launch surface *for Phase 3* behind a debug/dev flag,
  excluded from the shipped release APK. Phase 4's routing replaces it.
- **D-09 — No screenshot-regression tests in v1.** Rely on the on-device gallery + manual review. The real
  risks here (Adreno perf, appearance on the actual panel) are precisely what host-side screenshot tests
  CANNOT catch; keep Phase 3 lean and add them later only if visual drift bites.
- **D-10 — Prove criterion #5 with a synthetic feed + `gfxinfo` on the real device.** Drive the gallery's
  ring/graph from a deterministic 2-4 Hz synthetic feed and capture `gfxinfo framestats` on the real
  `flox` tablet, reusing the Phase-1 benchmark methodology (gfxinfo is the system of record). Hard p95
  evidence, not vibes.

### Render primitives — ring + line graph (criterion #5; "settled HERE, not late")
- **D-11 — Ring = Compose Canvas; line graph = Views custom-Canvas.** ADR 0001 names only the high-churn
  surfaces (temp graph, Files, Console) for classic Views. The progress ring redraws a single arc at
  ~2-4 Hz (low-churn) → Compose Canvas, themeable inline. The scrolling history **graph** is the genuine
  high-churn surface → **Views custom-Canvas** hosted via `AndroidView`. Principled per ADR 0001.
- **D-12 — Bounded ring-buffer lives in a toolkit-agnostic holder.** A plain-Kotlin bounded structure fed
  by the throttled `StateFlow` — unit-testable, survives View recreation/rotation/theme swap; the custom
  View only draws the current snapshot. (Later backfilled from `server.temperature_store` in Phase 5.)
- **D-13 — Motion: static redraw at cadence + cheap one-shot screen-entry only.** Live data (ring/graph)
  redraws straight to the current value at the throttled cadence — **no per-update tween, no breathing
  dot, no perpetual sheen.** A cheap one-shot "draw-on" is permitted *only* on screen entry (optional).
  This reconciles the hi-fi `CLAUDE.md` "alive motion" language with the project's hard constraint
  ("one-shot OK if cheap; no continuous/looping animation"). The hi-fi doc's "status dot breathes" is
  explicitly OUT.
- **D-14 — Wire the gallery primitive to BOTH the synthetic feed and the live Phase-2 spine.** Synthetic
  feed for the deterministic perf proof; a live wire to the Phase-2 `PrinterStateStore` StateFlow so the
  gallery shows real temps — proving the toolkit-agnostic render seam end-to-end (roadmap: "wired to the
  Phase-2 spine where it shows live data").

### Claude's Discretion (planner's call — fully specified by the design docs)
- The exact **Focus / Field / Gutter reusable Compose layout primitive** shape (a `ScreenScaffold`-style
  composable with focus/field/gutter slots + orientation-aware internal layout vs. custom `Layout`):
  `LAYOUT.md` fully specifies the grammar/mechanics; implement faithfully (one shared grid, `aspectRatio`
  for sacred squares, weights/`fillMax` for ratio-only sizing, the portrait-2-row ↔ landscape-1-row gutter
  mechanism). No re-litigation needed.
- **Geist / Geist Mono** bundling specifics (which static weights; variable-font support is API 26+ so
  prefer **static weights** for the API-23 floor) and `res/font` wiring — planner's call.
- Per-component visual token mapping, the exact Confirm-guard / single-setting-page / severity-toast
  component contracts, and module/package layout — driven by `hifi.css` + the hi-fi mockups; planner's
  call.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.** Paths relative to repo root
(`/mnt/e/claude/personal/github/dinghy-display`).

### The UI design system (LAW — this phase IS its build; there is NO generated UI-SPEC)
- `docs/ui_design/CLAUDE.md` — design philosophy + non-negotiables (Focus/Field/Gutter, outline-led
  controls, button-intent=color, "dense cells drop labels", theme-able via tokens, hi-fi visual language).
  NOTE the motion conflict resolved by **D-13**: the "alive motion / dot breathes" line is superseded by
  the Adreno animation ban.
- `docs/ui_design/LAYOUT.md` — the Focus/Field/Gutter grammar, the ⚠ NON-NEGOTIABLES (one shared tabular
  grid, sacred aspect ratios, ratio-only sizing), orientation mechanics, content-display rules, CSS
  scaffold classes (`.screen.port`/`.screen.land`, `.stage`, `.focus`, `.field`, `.gutter`).
- `docs/ui_design/THEMING.md` — the canonical role-token vocabulary with **exact dark + light values**
  (`--bg`/`--surface`/`--text`/`--outline`/`--accent`/`--heat`/`--go`/`--stop`…), button-intent=color,
  shape/type tokens, and **`--fs`** (S≈1.0 / M≈1.15 / L≈1.32; M is the larger default). The accent is the
  oklch blue `oklch(0.66 0.15 255)` — this SUPERSEDES the old `#5BC8FF` cyan in the pre-restructure 04
  context.
- `docs/ui_design/README.md` — design-bundle overview / index.
- `docs/ui_design/reference/hifi.css` (597 lines) — **the canonical token + component source.** Reproduce
  its values in the Compose/Views stack (reference, not code to copy verbatim).
- `docs/ui_design/reference/Print Status Hi-Fi.html` (825 lines) — the design canvas; the first concrete
  Focus/Field/Gutter instance (the screen the ring + stat-grid + gutter come from).
- `docs/ui_design/images/01-splash.png … 10-foundations.png` — hi-fi mockups of all core screens
  (portrait + landscape); `10-foundations.png` is the design-foundations sheet.

### Toolkit & architecture law
- `docs/adr/0001-ui-toolkit-decision.md` (107 lines) — **HYBRID is law.** Compose for shell/most panels;
  **classic Views custom-`Canvas`/RecyclerView for the high-churn surfaces** (temp graph, Files, Console).
  Grounds D-05/D-06/D-11 (graph=Views, ring=Compose, agnostic token source).
- `CLAUDE.md` (repo root) § "UI Design System" + § "Technology Stack" — the design-docs reading order
  (LAW), the hybrid toolkit lock, the pinned stack (Compose 1.11.1 / AGP 8.7.x / minSdk 23 / armeabi-v7a),
  and the "no continuous animation on Adreno" constraint.

### Phase scope & requirements
- `.planning/ROADMAP.md` § "Phase 3: Design System & Theming Foundation" (lines ~103–118) — the **five
  success criteria** this phase must close, and the STANDARD research note.
- `.planning/REQUIREMENTS.md` — the seven mapped requirements: **THEME-01** (semantic tokens, dark/light/
  custom), **THEME-02** (S/M/L `--fs`), **UI-01** (Focus/Field/Gutter responsive grammar), **UI-02**
  (outline-led control language + intent colors), **PRIM-01** (single-setting scrubber/stepper page),
  **PRIM-03** (full-screen Confirm guard), **PRIM-04** (severity toast).

### Phase-2 spine (reusable; the live-data + throttle seam this phase renders)
- `.planning/phases/02-connection-state-foundation/02-CONTEXT.md` — the spine contract; STATE-03 throttle
  decision (conflation applies to status updates, NOT the gcode-response line stream).
- `.planning/phases/04-service-shell-settings-print-status-home/04-CONTEXT.md` — ⚠ partially superseded,
  but its **render/throttle seam** notes and the reusable-code map (PrinterStateStore/PrinterState
  StateFlows) still apply; the *shell/nav/E-stop* decisions there are SUPERSEDED by `docs/ui_design/`.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`PrinterStateStore`** (`app/src/main/java/works/mees/dinghy/state/PrinterStateStore.kt`) — exposes
  `printerState` / `capabilities` / `gcodeResponses` StateFlows; the **~4 Hz high-rate conflation already
  exists here** (`DEFAULT_SAMPLE_MS = 250`). This is the **throttle half** of the "shared render/throttle"
  primitive; **Phase 3 adds the render half** (ring + graph) that consumes these flows. This is the live
  wire for D-14.
- **`PrinterState`** (`.../state/PrinterState.kt`) — the StateFlow data the render primitive draws (temps
  etc.). Toolkit-agnostic by design — consume from both Compose and the Views graph.
- **`Capabilities` / `DeriveCapabilities` / `PrinterStateReducer`** (`.../state/`) — present; relevant
  later for capability-gated panels, not core to the design substrate.
- **No existing UI/theme/designsystem code** — greenfield (`find` for theme/ui/designsystem dirs = none;
  no `res/font/` yet). This phase introduces the entire UI/token/component layer.

### Established Patterns
- **Pinned version catalog** `gradle/libs.versions.toml` is the single source of versions; add any new dep
  (Geist fonts are bundled assets, not a dep; **DataStore** is NOT in the catalog yet and is added when
  persistence lands) there — never inline. The `verifyMinSdk` build-logic plugin asserts the merged
  manifest stays **minSdk 23**; every dep must hold the floor.
- **Compose resolves to 1.11.1; armeabi-v7a-only release** (Phase 1). Cleartext posture is owned by the
  shared `AndroidManifest.xml` + `res/xml/network_security_config.xml` — do NOT duplicate.
- **kotlinx.serialization** is wired with the loose-JSON posture (`ignoreUnknownKeys`/`isLenient`).
- **Benchmark methodology** (Phase 1, `:macrobenchmark` + `bench/*` scenes): deterministic in-process
  synthetic feed → scene → `gfxinfo framestats` parser as system of record. **Reuse this for D-10** (the
  ring/graph perf proof on the real device).

### Integration Points
- The render primitive consumes the Phase-2 `PrinterStateStore` StateFlows (D-14) — the first time a UI
  surface reads the spine. There is **no Application class, DI container, or service yet** (Phase 4
  introduces those); the gallery (D-08) constructs whatever minimal wiring it needs to show live data.
- The `ThemeTokens` resolver (D-05) becomes the **public theming contract** every later phase's panels
  consume — design it as the stable seam.

</code_context>

<specifics>
## Specific Ideas

- **"A theme is a token remap" must hold across BOTH toolkits** — the explicit reason the token source is
  toolkit-agnostic (D-05) and the Views graph repaints on token push (D-06): a dark→light flip or a custom
  recolor recolors the Canvas graph, not just the Compose surfaces.
- **The gallery is how Matthew signs off** — install the APK, the gallery opens, eyeball every component ×
  theme × text size on the real flox tablet; the graph perf proof lives in the same harness (D-07/D-08/D-10).
- **Sole text-size authority** (D-04) is a deliberate appliance-screen choice: a dedicated arm's-length
  printer display wants predictable layout over honoring an OS fontScale the owner doesn't manage.
- **oklch is authoring-only** (D-03): perceptual authoring in the design docs, but the runtime is plain
  sRGB Compose `Color` for API-23 safety and Adreno speed.

</specifics>

<deferred>
## Deferred Ideas

- **Full all-token custom-theme editor** — beyond accent + status roles (D-01) is deferred; revisit post-v1
  if users want deeper control.
- **Runtime oklch / perceptual custom-color picking** — deferred (D-03 bakes to sRGB); revisit only if
  custom-hue fidelity becomes a real complaint.
- **Screenshot-regression tests (Paparazzi/Roborazzi)** — deferred (D-09); add later only if visual drift
  becomes a problem.
- **The Settings-screen theme/text-size/connection editing UI** — Phase 4 (SET-01); Phase 3 builds only the
  token override *mechanism* and the gallery preview, not the user-facing editor.
- **PRIM-05 shared command-dispatch primitive** — Phase 4 (it gates *action* calls; nothing in the design
  substrate dispatches commands).
- **The real launcher / App Drawer navigation / foreground service** — Phase 4; the gallery is a temporary
  dev launcher (D-08) until then.
- **Temperature history graph (full, backfilled from `server.temperature_store`)** — Phase 5 EXTENDS the
  Phase-3 line-graph primitive; this phase settles the architecture (D-11/D-12), not the full panel.

Discussion otherwise stayed within phase scope.

</deferred>

---

*Phase: 3-design-system-theming-foundation*
*Context gathered: 2026-05-31*
