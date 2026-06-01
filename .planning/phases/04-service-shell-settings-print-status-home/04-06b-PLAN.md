---
phase: 04-service-shell-settings-print-status-home
plan: 06b
type: execute
wave: 4
depends_on: ["04-06"]
files_modified:
  - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
autonomous: false
requirements: [SHELL-04]
must_haves:
  truths:
    - "A compact heater sparkline (Views GraphView via GraphViewHost) renders in the reserved Print Status Field slot"
    - "A theme flip recolors the sparkline (ThemeableView push-tokens)"
    - "The combined ring + sparkline + live grid surface holds the Phase-3 two-part perf gate on flox"
  artifacts:
    - path: "app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt"
      provides: "Heater sparkline (GraphViewHost) wired into the reserved Field slot + dispatcher-failure toast"
      contains: "GraphViewHost"
  key_links:
    - from: "ui/printstatus/PrintStatusScreen.kt"
      to: "render/GraphViewHost.kt"
      via: "GraphViewHost(tokens, snapshot) heater sparkline"
      pattern: "GraphViewHost"
    - from: "ui/printstatus/PrintStatusScreen.kt"
      to: "ui/printstatus/PrintStatusHolder.kt"
      via: "holder.sparkline snapshot feeds the GraphView"
      pattern: "sparkline"
---

<objective>
Print Status home (part 2 of 2, SHELL-04) — the heater sparkline and the on-device combined-render
perf gate, split out of the original 04-06 per review concern #4 (the original plan was the highest
overrun risk on the Adreno 320 floor).

This plan fills the sparkline slot 04-06 reserved in the Field, hosting the Phase-3 `GraphView`
(classic Views custom Canvas) via `GraphViewHost`/`AndroidView`, fed by the primary-heater snapshot
the 04-06 holder exposes. It then runs the combined ring + sparkline + live grid perf gate against
the Phase-3 two-part criterion on real flox hardware (the D-09 PERF WATCH).

Purpose: proves the Views render primitive "in anger" (D-09) and validates the new combined render
surface against the Adreno 320 budget — the deliberate add the mockup `03` omits.
Output: sparkline wiring in `PrintStatusScreen` + the on-device perf checkpoint.
</objective>

<execution_context>
@$HOME/.claude/get-shit-done/workflows/execute-plan.md
@$HOME/.claude/get-shit-done/templates/summary.md
</execution_context>

<context>
@.planning/PROJECT.md
@.planning/ROADMAP.md
@.planning/STATE.md
@.planning/phases/04-service-shell-settings-print-status-home/04-CONTEXT.md
@.planning/phases/04-service-shell-settings-print-status-home/04-PATTERNS.md
@.planning/phases/03-design-system-theming-foundation/03-PERF-RESULTS.md
@docs/ui_design/images/03-print-status.png
</context>

<tasks>

<task type="auto">
  <name>Task 1: Wire the heater sparkline (GraphViewHost) into the reserved Field slot</name>
  <files>app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt</files>
  <read_first>
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt (04-06 — has the reserved sparkline slot in the Field; this plan fills it without relayout)
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusHolder.kt (04-06 — exposes the primary-heater sparkline StateFlow<FloatArray> snapshot)
    - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt (EXACT analog L198-203: GraphViewHost(tokens = LocalTokens.current, snapshot = ring.snapshot()) — copy the placement/wiring so a theme flip recolors the Canvas)
    - app/src/main/java/works/mees/dinghy/render/GraphViewHost.kt (L28-33 GraphViewHost(tokens, snapshot, modifier, drawArea=true) — AndroidView host; pushes tokens + snapshot; ADR 0001 Views)
    - app/src/main/java/works/mees/dinghy/render/GraphView.kt (ThemeableView; recolors via applyTokens; pre-allocated paints/path — allocation-free onDraw)
    - app/src/main/java/works/mees/dinghy/render/RingBuffer.kt (bounded rolling window cap 120 feeding the snapshot)
  </read_first>
  <action>
    In the reserved sparkline slot of `PrintStatusScreen` (04-06 Field), host the heater sparkline:
    `GraphViewHost(tokens = LocalTokens.current, snapshot = holder.sparkline.collectAsStateWithLifecycle().value,
    modifier = …)` — copy the GalleryScreen L198-203 wiring verbatim for placement so a theme flip recolors the
    Canvas (D-09, the in-anger Views proof). Fill ONLY the reserved slot — do NOT change the ScreenScaffold
    Focus/Field/Gutter structure (additive within the Field). Update at the store's throttled cadence via the
    holder snapshot; do NOT add a second sampling layer. The Phase-3 GraphView already pre-allocates its paints/
    path (allocation-free onDraw); just feed it.
  </action>
  <verify>
    <automated>cmd.exe /c "E:\Android\gw.bat :app:compileDebugKotlin --no-daemon"</automated>
  </verify>
  <acceptance_criteria>
    - `grep -n 'GraphViewHost' app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` matches (sparkline now hosted)
    - `grep -n 'holder.sparkline\|sparkline' …PrintStatusScreen.kt` matches (fed by the holder snapshot, not a new source)
    - `grep -n 'LocalTokens.current' …PrintStatusScreen.kt` is passed to GraphViewHost (theme flip recolors the Canvas, D-09)
    - the change fills the reserved slot only — the ScreenScaffold focus/field/gutter call sites from 04-06 are unchanged (diff is additive within the Field)
    - no second sampling/throttle timer introduced (`grep -nE 'sample\(|debounce\(|delay\(' …` returns nothing)
    - `:app:compileDebugKotlin` succeeds
  </acceptance_criteria>
  <done>Heater sparkline renders in the reserved Field slot via GraphViewHost, recolors on theme flip, fed at the store cadence from the holder, with the layout structure unchanged.</done>
</task>

<task type="checkpoint:human-verify" gate="blocking">
  <name>Task 2: On-device combined-render perf gate (ring+sparkline+grid) + Stop round-trip on flox</name>
  <action>Measure the combined Print Status render surface (ring + sparkline + live grid) against the Phase-3 two-part perf gate on flox via gfxinfo framestats, and verify the Stop→ConfirmGuard→printer.emergency_stop round-trip drives Klippy to shutdown and routes to the splash recovery surface. Record p95 + frozen-frame count; flag any regression past the 03-PERF-RESULTS.md re-open conditions.</action>
  <what-built>
    The Print Status home now renders ring + heater sparkline (GraphView) + a live 2×3 numeric grid on one
    screen — the NEW combined render surface flagged as the D-09 PERF WATCH. This checkpoint measures it against
    the Phase-3 two-part gate on the real Adreno-320 device (flox), reusing the Phase-1 gfxinfo framestats
    parser, and confirms the Stop→ConfirmGuard→emergency_stop round-trip drives the printer to shutdown and
    routes to the splash recovery surface.
  </what-built>
  <how-to-verify>
    On the real flox device, with the Ender 5 Plus reachable and a live heat ramp running (or a print active):
    1. Build + install: `cmd.exe /c "E:\Android\gw.bat :app:installDebug --no-daemon"`
    2. Open the Print Status home. Confirm: idle shows the temp/status "Ready" readout (NOT a 0% ring);
       starting a print/heat shows the ProgressRing + live sparkline + grid updating at ~2–4 Hz; a theme flip
       recolors the sparkline.
    3. Perf gate — during an active heat ramp:
       `cmd.exe /c "E:\Android\Sdk\platform-tools\adb.exe shell dumpsys gfxinfo works.mees.dinghy reset"`,
       dwell ~30s on the Print Status screen, then capture framestats and run them through the Phase-1 parser.
       Expected (Phase-3 A-variant two-part gate, 03-PERF-RESULTS.md): allocation-free / no-loop liveness +
       sparse-redraw p95 ≲ ~66ms, ZERO frozen frames. If it regresses past the re-open conditions in
       03-PERF-RESULTS.md, record the numbers and flag for a render-budget fix (do not silently pass).
    4. Stop round-trip: tap Stop → ConfirmGuard appears → confirm. Expected: `printer.emergency_stop` fires,
       Klippy goes to shutdown, the app routes to the splash recovery surface (Retry + firmware_restart +
       restart). Recover via Restart firmware.
  </how-to-verify>
  <resume-signal>Type "approved" with the captured p95 / frozen-frame numbers once the combined surface holds the two-part gate and the Stop→shutdown→splash round-trip works, or describe the regression.</resume-signal>
</task>

</tasks>

<threat_model>
## Trust Boundaries

| Boundary | Description |
|----------|-------------|
| RingBuffer snapshot → GraphView | in-process render data only; no privilege or network crossing |
| Stop tap → ConfirmGuard → Moonraker | destructive E-stop crosses to the printer (re-verified end-to-end here) |

## STRIDE Threat Register

| Threat ID | Category | Component | Disposition | Mitigation Plan |
|-----------|----------|-----------|-------------|-----------------|
| T-04-06b-DoS | Denial of Service (perf) | combined render surface | mitigate | on-device two-part perf gate enforces the Adreno 320 budget before sign-off; consumes the store's existing ~4Hz conflation (no second throttle) |
| T-04-06b-R | Resource | GraphView allocation on draw | mitigate | reuse the Phase-3 pre-allocated GraphView (allocation-free onDraw); no per-frame allocation |
| T-04-06b-E | Elevation/destructive | Stop → emergency_stop | mitigate | re-verifies the ConfirmGuard + dispatcher-gated E-stop end-to-end on real hardware |
</threat_model>

<verification>
- `:app:compileDebugKotlin` green with the sparkline wired into the reserved slot
- On-device checkpoint: combined ring + sparkline + grid holds the Phase-3 two-part perf gate; Stop→shutdown→splash round-trip works (with captured numbers)
</verification>

<success_criteria>
- Heater sparkline renders in the reserved Field slot and recolors on theme flip (D-09)
- Combined render surface holds the Phase-3 two-part gate on flox (D-09 PERF WATCH)
- Stop→ConfirmGuard→emergency_stop→shutdown→splash round-trip verified end-to-end
</success_criteria>

<output>
Create `.planning/phases/04-service-shell-settings-print-status-home/04-06b-SUMMARY.md` when done
</output>
