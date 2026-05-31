# Phase 3: Design System & Theming Foundation - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 3-design-system-theming-foundation
**Areas discussed:** Custom-theme scope, Hybrid token bridge, On-device preview harness, Render primitive architecture

---

## Custom-theme scope

**Q: How far does the user-custom theme go in v1?**

| Option | Description | Selected |
|--------|-------------|----------|
| Accent + status roles | Recolor accent/heat/go/stop (+bg); rest derive from base | ✓ |
| Full token remap | All ~30 role tokens user-editable | |
| Preset alt-themes | Curated presets, no free picking | |
| Dark/Light only in v1 | Defer all customization to v2 | |

**Q: Custom-theme structure relative to dark/light?**

| Option | Description | Selected |
|--------|-------------|----------|
| Override on a base | Pick dark/light base, store only deltas, inherit the rest | ✓ |
| Independent 3rd theme | Fully self-contained token set | |
| You decide | — | |

**Q: Color space handling (oklch authored; native oklch is API 26+, floor 23)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Bake to sRGB, pick in sRGB | Convert oklch dark/light to sRGB once; custom picked in sRGB; no runtime oklch | ✓ |
| Runtime oklch | Perceptual oklch end-to-end | |
| You decide | — | |

**Notes:** Mechanism built this phase; the editing UI is Phase 4 (SET-01). Override-on-a-base keeps a half-finished custom theme usable and storage cheap. sRGB-bake is the API-23-safe, Adreno-fast path.

---

## Hybrid token bridge

**Q: Single source of truth for resolved tokens across Compose AND Views?**

| Option | Description | Selected |
|--------|-------------|----------|
| Toolkit-agnostic resolver | Plain-Kotlin ThemeTokens in a StateFlow; Compose via CompositionLocal, Views collect the same | ✓ |
| Compose-owned + bridge | MaterialTheme/CompositionLocal canonical; Views reach in via a bridge | |
| Android res themes | res/values attrs + ContextThemeWrapper drive both | |

**Q: How does the Views Canvas graph pick up a live theme change?**

| Option | Description | Selected |
|--------|-------------|----------|
| Push tokens + invalidate | Host pushes new ThemeTokens, calls invalidate(); View re-reads at draw | ✓ |
| Recreate the view | Bake colors at creation, rebuild AndroidView on theme change | |
| You decide | — | |

**Q: Does the app's S/M/L `--fs` stack on the OS font scale, or is the app sole authority?**

| Option | Description | Selected |
|--------|-------------|----------|
| App is sole authority | --fs applied to dp; OS fontScale not double-applied; predictable layout | ✓ |
| Stack on OS scale | Use sp so OS fontScale also applies | |
| You decide | — | |

**Notes:** The agnostic resolver mirrors ADR 0001's "view-models toolkit-agnostic" rule and is what makes "a theme is a token remap" true for the Canvas graph too. Sole-authority text scale chosen deliberately for a dedicated appliance screen.

---

## On-device preview harness

**Q: Primary preview/verification mechanism?**

| Option | Description | Selected |
|--------|-------------|----------|
| In-APK component gallery | Installable screen: every component × dark/light/custom × S/M/L on the real Nexus 7 | ✓ |
| Compose @Preview only | Android Studio render panels; not on-device | |
| Both | @Preview + on-device gallery | |

**Q: How is the gallery wired this phase (Phase 4 builds real routing)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Temp dev launcher, gated from release | Gallery is the launch surface this phase, behind a debug/dev flag, excluded from release | ✓ |
| Permanent (reachable from Settings) | Keep gallery in the shipped app | |
| You decide | — | |

**Q: Add automated screenshot-regression tests?**

| Option | Description | Selected |
|--------|-------------|----------|
| No — gallery + eyeball for v1 | Rely on on-device gallery; real risks (Adreno perf/appearance) aren't caught by host screenshot tests anyway | ✓ |
| Yes — add screenshot tests | JVM-side regression net | |
| You decide | — | |

**Q: How do we prove criterion #5 (graph + ring jank-free on Nexus 7)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Synthetic feed + gfxinfo on device | Deterministic 2-4 Hz feed, gfxinfo framestats on real flox; reuse Phase-1 methodology | ✓ |
| Eyeball live on device | Judge smoothness by eye | |
| You decide | — | |

**Notes:** The gallery doubles as Matthew's on-device sign-off surface and the perf-proof harness. gfxinfo framestats remains the system of record (Phase-1 precedent).

---

## Render primitive architecture

**Q: Toolkit split for ring vs. graph?**

| Option | Description | Selected |
|--------|-------------|----------|
| Ring=Compose, Graph=Views | Ring (low-churn single arc) = Compose Canvas; scrolling graph (high-churn) = Views custom-Canvas per ADR 0001 | ✓ |
| Both in Views Canvas | Ring and graph both classic Views for consistency | |
| You decide | — | |

**Q: Where does the bounded ring-buffer of samples live?**

| Option | Description | Selected |
|--------|-------------|----------|
| Toolkit-agnostic holder | Plain-Kotlin bounded buffer fed by the throttled StateFlow; unit-testable; survives recreation; View draws snapshots | ✓ |
| Inside the custom View | View owns the buffer | |
| You decide | — | |

**Q: Motion policy (reconciling hi-fi 'alive motion' vs. Adreno animation ban)?**

| Option | Description | Selected |
|--------|-------------|----------|
| Static redraw, cheap entry only | Redraw to current value at cadence; no tween/breathing/sheen; cheap one-shot on screen entry only | ✓ |
| Fully static | No animation at all, even one-shot | |
| You decide | — | |

**Q: Wire the gallery primitive to the live Phase-2 spine this phase?**

| Option | Description | Selected |
|--------|-------------|----------|
| Both synthetic + live | Synthetic feed for perf proof + live PrinterStateStore wire for real temps | ✓ |
| Synthetic only this phase | Defer live wiring to Phase 4 | |
| You decide | — | |

**Notes:** ADR 0001 only lists the high-churn surfaces (graph/Files/Console) for Views, so the ring stays in Compose. Buffer-in-holder keeps history across rotation/theme swap and is unit-testable. Motion policy explicitly retires the hi-fi doc's "status dot breathes."

---

## Claude's Discretion

- The exact Focus/Field/Gutter reusable Compose layout primitive shape (fully specified by LAYOUT.md).
- Geist/Geist Mono weight selection + `res/font` wiring (prefer static weights — variable fonts are API 26+).
- Per-component visual token mapping and the Confirm-guard / single-setting-page / severity-toast component contracts (driven by hifi.css + mockups).
- Module/package layout.

## Deferred Ideas

- Full all-token custom-theme editor → post-v1.
- Runtime oklch / perceptual custom-color picking → revisit only if needed.
- Screenshot-regression tests → add later if visual drift bites.
- Settings-screen theme/text-size/connection editing UI → Phase 4 (SET-01).
- PRIM-05 command-dispatch primitive → Phase 4.
- Real launcher / App Drawer nav / foreground service → Phase 4.
- Full temperature history graph (server.temperature_store backfill) → Phase 5 (extends this phase's line-graph primitive).
