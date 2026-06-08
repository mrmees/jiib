# Phase 20: System Information Page - Context

**Gathered:** 2026-06-08
**Status:** Ready for planning

<domain>
## Phase Boundary

A read-only **printer-host health / diagnostics page** for the **active printer's** SBC — the
machine that runs Klipper + Moonraker. It is NOT the Dinghy app itself (that's About) and NOT the
printer's motion/heater hardware (those live on their own screens).

- **No control surface.** Nothing on this page dispatches a command. No Apply/ConfirmGuard flow.
- **Active-printer scoped.** Re-resolves when the user switches printers (Phase 14 multi-printer).
- Reuses the **central subscribe** and the existing **label:value render primitive** (About's
  `InfoRow`) — no new data path.

**Owner-narrowed scope (host-health-only).** Versions, disk/storage, and network are explicitly
**OUT** of this page (the staging doc rescoped them away — versions already live in About; disk/
network are low-glance-value on a wall-mounted screen). ROADMAP SC-1 was rewritten to match (done
this session).

> **The complete design is locked in the staging doc — `20-system-info-staging.md` (see Canonical
> References). It is authoritative for ~90% of this phase** (layout, health-chip behavior, value
> formatting, data sources, cadence, degradation, boundaries). This CONTEXT records only the
> owner decisions the staging doc left open, plus carry-forward constraints and the SC-1 rescope.

</domain>

<decisions>
## Implementation Decisions

> The staging doc (`20-system-info-staging.md`) is authoritative for everything not restated here.
> The decisions below settle the gaps it left open. **Note:** the staging doc is titled "Phase 19"
> — that is stale numbering. System Information = **Phase 20** in the current roadmap (Phase 19 =
> Output Controls, already shipped). Treat every "Phase 19" reference in the staging doc as Phase 20.

### Icon slate (OWNER-SELECTED — do not substitute)
Per the project icon law ([[dinghy-never-pick-icons-ask]]), every glyph below was chosen/approved by
the owner. The staging doc improperly delegated glyph choice to "the planner verifies ligature
names" — that is overridden here. The planner MUST wire exactly these and MUST NOT invent or swap
any. All are Material Symbols Outlined ligatures (axes FILL0/wght400/GRAD0), consistent with the
existing `DinghyIcons` registry.

- **D-01 (drawer-tile entry, OWNER OVERRIDE):** System Information drawer tile → **`pulse_alert`**
- **D-02 (Focus · hostname / host model):** → **`dns`**
- **D-03 (Focus · CPU temp):** → **`thermostat`** (reuse "general temperature")
- **D-04 (Focus · uptime):** → **`schedule`**
- **D-05 (Host · CPU model + cores):** → **`developer_board`**
- **D-06 (Host · total RAM):** → **`memory`**
- **D-07 (Host · distro name + version):** → **`deployed_code`**
- **D-08 (Host · kernel):** → **`code_blocks`**
- **D-09 (Live · CPU load %):** → **`speed`** (reuse generic-speed glyph)
- **D-10 (Live · memory used / available):** → **`data_usage`**
- **D-11 (planner verification gate):** Before wiring, verify each glyph above actually resolves in
  the bundled Material Symbols ttf (v2.944) via the Phase-18.1 `verify_ligatures.py` tooling.
  **Pay special attention to `pulse_alert`** (newer glyph — most likely to be absent). If any
  ligature is missing, **STOP and ask the owner** — do NOT silently fall back to a different glyph.
- The **health chip** itself needs no glyph — it renders via the Phase-15.1 shape-coded status
  system (shape carries safety).

### Health-chip temp-fallback thresholds (non-Pi hosts)
The chip is throttle-authoritative on Pi hosts (Ender 5 Plus / RPi 4); on non-Pi hosts that omit
throttle flags (Ender 3 / RockPro64) it falls back to CPU-temp thresholds with full 3-state parity.

- **D-12:** Temp-fallback cutoffs are **warn ≥ 70 °C** (amber triangle), **caution ≥ 80 °C** (red
  octagon), **< 70 °C = healthy** (go / shapeless). Chosen to mirror the Pi's 80 °C soft-throttle
  so the chip reads identically on both fleet printers. These are app-defined defaults — the
  researcher confirms the RockPro64 actually omits throttle data and exposes a CPU-temp field, but
  the cutoff numbers are LOCKED (not "defer to research").

### SC-1 rescope (APPLIED this session)
- **D-13:** ROADMAP Phase-20 **SC-1 was rewritten** to host-health-only (dropped versions/disk/
  network); the **Goal** and **Research note** lines were updated to match. The staging doc was
  **copied into the repo** at `.planning/phases/20-system-information-page/20-system-info-staging.md`
  so downstream agents can read it. No further ROADMAP action needed before planning.

### Claude's Discretion
None — the staging doc + decisions above leave nothing to Claude's discretion on owner-facing
choices. Remaining ambiguity is pure research (field shapes, channel names) — see Deferred /
staging-doc "Open Planner Work".

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Phase design (AUTHORITATIVE — read FIRST)
- `.planning/phases/20-system-information-page/20-system-info-staging.md` — **locks ~90% of this
  phase**: layout (Focus health summary + Field grouped single-scroll detail), health-chip 2-mode
  behavior + precedence, value formatting, data sources, cadence, graceful degradation, product
  boundaries, and the "Open Planner Work" research checklist. (Titled "Phase 19" = stale numbering →
  read as Phase 20.)

### UI LAW
- `docs/ui_design/LAYOUT.md` — Focus / Field / Gutter grammar (this page uses the standard grammar:
  Focus health summary, Field grouped detail, Gutter = Back only, drawer suppressed on-screen).
- `docs/ui_design/THEMING.md` — semantic role tokens; status-color grammar for the health chip.
- `docs/ui_design/CLAUDE.md` — design non-negotiables incl. the icon law.

### Cadence / data
- `docs/request-cadence-contract.md` — Phase-13 efficiency contract; live values ride the central
  subscribe at a throttled display cadence, subscribe-on-enter / release-on-exit, **no dedicated
  poll loop** (SC-2).
- `docs/moonraker-capabilities.md` — partial `machine.system_info` / `proc_stats` / `server.info`
  field shapes already captured; researcher confirms full shapes against the live API on both SBCs.

### Status system (health chip)
- Phase-15.1 **shape-coded status** system (shape carries safety, not color alone) — the health
  chip reuses it for its 3 states (healthy / warn-triangle / caution-octagon). Locate the existing
  implementation in the codebase (`works.mees.dinghy` status/theme layer).

### Command-catalog drift (HARD GATE — [[dinghy-command-catalog-drift]])
- `docs/commands/catalog.json` + `docs/commands/printer-matrix.json` — `server.info` is already
  wired (`MR-server.info`), but **`machine.system_info` and `machine.proc_stats` are NOT** — adding
  their `MR-*` CommandSpecs to `CommandRegistry.all` REQUIRES matching rows in BOTH JSON files or
  `CommandCatalogDriftTest` (D-10 guard) fails the build. This WILL bite if missed.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt` → `InfoRow`**: the label :
  monospace-value primitive (Geist label / GeistMono value). Staging doc calls it `LabelValueRow`
  but the actual symbol is `InfoRow`. Reuse its structure and **add a leading Material-Symbol icon
  slot** (About's is iconless) for the per-row icons (D-02..D-10).
- **`app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt`**: where the new System Information
  drawer tile (`pulse_alert`, D-01) is registered, alongside Files / Macros / Console.
- **`app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt`**: icon registry — add
  the new bindings here (run the `verify_ligatures.py` gate per D-11).
- **Central subscribe / proc-stat channel**: `MoonrakerSession.kt` + state layer. Working
  assumption (planner to confirm): Moonraker pushes `notify_proc_stat_update`-style frames (~1 Hz)
  on a channel separate from the Klipper object subscribe; the screen taps it and throttles display.
- Existing `render/` primitives (GraphView etc.) exist but are **NOT** used here — staging doc locks
  this page to label:value rows + a Focus summary. **No sparklines / gauges / graphs.**

### Established Patterns
- **Capability-gating** (Phase 6) + graceful degradation: missing fields → `—`; keep the labeled
  row present (stable layout across both SBCs); whole-page sparse/disconnected leans on the app's
  global connection handling (SC-3).
- **Status = color/shape on an element** (no persistent status bar) — health chip is the only flag.

### Integration Points
- New `machine.system_info` / `machine.proc_stats` CommandSpecs → `CommandRegistry.all` (+ the
  catalog/matrix JSON rows, see Canonical Refs drift gate).
- Drawer tile entry → `AppDrawer.kt`; active-printer re-resolution hooks into the Phase-14
  multi-printer switch.

</code_context>

<specifics>
## Specific Ideas

- Page is **icon-forward** per Phases 17/18 — every detail row carries a leading Material Symbol;
  the Focus summary carries its temp glyph + the shape-coded chip.
- Row order is **fixed logical order** (as listed in the staging doc), NOT alphabetical — this is a
  small heterogeneous identity/telemetry set, unlike the homogeneous Phase-18 list.
- Value formatting is locked by the staging doc (temp whole °C; load integer %; memory `used / total`
  auto-scaled; uptime compact `2d 3h 14m` dropping leading-zero units — **host** uptime from
  `proc_stats.system_uptime`, not Klipper/Moonraker uptime; cores integer; RAM in GB).

</specifics>

<deferred>
## Deferred Ideas

Out of scope for Phase 20 (per the staging doc's Product Boundaries) — not lost, just not here:

- **Klipper / Moonraker versions on this page** — already shown in About; do not duplicate.
- **Disk / storage section** — owner-narrowed out of Phase 20.
- **Network section / per-interface detail** — owner-narrowed out of Phase 20.
- **Per-process or per-core CPU breakdown** — out of scope.
- **Side-by-side multi-host view** — active-printer scoped only.

### Open Planner Work (research, not owner decisions — from staging doc)
These are for the researcher/planner to resolve against the live printers; they are NOT owner gray
areas and should not be re-asked:
- Capture/verify exact `machine.system_info` + `machine.proc_stats` field shapes on BOTH printers
  (Ender 5 Plus / RPi 4 and Ender 3 / RockPro64); record which fields each omits.
- Confirm the Pi throttle-flag shape: current-state vs has-occurred-since-boot bits → caution vs warn.
- Confirm the RockPro64 omits throttle data (drives the temp-fallback) and which CPU-temp field it
  exposes.
- Confirm the live proc-stat channel name + push cadence and the correct throttled display cadence
  under the Phase-13 contract.
- Confirm exact `system_info` fields for CPU description / core count / total RAM / distro / kernel,
  and the uptime field source.
- Run the `verify_ligatures.py` gate on the full D-01..D-10 glyph set (esp. `pulse_alert`).

</deferred>

---

*Phase: 20-system-information-page*
*Context gathered: 2026-06-08*
