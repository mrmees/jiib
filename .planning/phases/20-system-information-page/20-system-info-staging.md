# Phase 19 System Information Page - Staging Notes

Source project: `/mnt/e/claude/personal/github/dinghy-display`

These notes are staging context for a future GSD discussion/planning pass. They are not written
into the real project checkout.

## ⚠ Roadmap Action Required Before Planning

Phase 19's current ROADMAP **SC-1 must be rescoped**. It currently reads:

> "The page shows host system info (CPU/mem/temp/throttle/uptime/distro), Klipper + Moonraker
> versions, and disk/network status sourced from Moonraker machine/server endpoints"

The owner deliberately narrowed Phase 19 to a **host-health-only** page. Drop these from SC-1:

- Klipper + Moonraker **versions** (already shown in About; not re-shown here)
- **Disk / storage** status
- **Network** status

Rationale (owner-confirmed): versions already live on the About screen, and disk/network are
low-glance-value on a wall-mounted printer screen. The page earns its keep as a host-health
view (temp / throttle / load), not a telemetry dump.

SC-2 (throttled cadence via central subscribe), SC-3 (graceful degradation), and SC-4 (verified
on both real SBCs) **stand unchanged**.

Restated SC-1 target:

> The page shows host system identity (CPU model/cores, total RAM, distro, kernel), live host
> load (CPU %, memory used/available), and a host-health summary (hostname, CPU temp, throttle/
> health state, uptime) sourced from Moonraker `machine.system_info` / `machine.proc_stats`.

## Core Direction

Phase 19 is a **read-only printer-host health / diagnostics page**. It describes the SBC that
runs Klipper + Moonraker for the **active printer** — not the Dinghy app itself (that is About),
and not the printer's motion/heater hardware (those live on their own screens).

- No control surface. Nothing on this page dispatches a command.
- Active-printer scoped. Re-resolves when the user switches printers (Phase 14 multi-printer).
- A diagnostics/health view that reuses the central subscribe and existing label:value render
  primitives rather than introducing a new data path.

## Entry Point

Top-level **drawer entry** in the global swipe-up drawer, alongside Files, Macros, Console, etc.
Treated as a frequently-glanceable health view, not a buried setting.

- Not a Settings sub-page. (Settings → System keeps its own version+build line; this page does
  not duplicate or absorb it.)

## Screen Layout

Uses the standard Focus / Field / Gutter grammar.

### Focus — Health Summary

At-a-glance "is this host okay" zone. Contents:

- Hostname / host model
- CPU temperature
- **Health chip** (see Health Chip section)
- Uptime

This is the only part of the otherwise-telemetry page that raises a flag.

### Field — Grouped Detail (single scroll)

Two labeled sections, each a stack of label:value rows. Reuse the About screen's
`LabelValueRow` (label : monospace-value) primitive.

**Host** (static-ish identity, from `machine.system_info`):

- CPU model / description + core count
- Total RAM
- Distribution name + version
- Kernel

**Live load** (live-updating, throttled, from `machine.proc_stats`):

- CPU load %
- Memory used / available

Rows are in fixed logical order as listed above — NOT alphabetical (these are a small
heterogeneous identity/telemetry set, not a homogeneous list like Phase 18).

Each row carries a **leading Material Symbol icon** (the app is icon-forward, consistent with
Phases 17/18). Per-row icons — e.g. cpu / cores / memory / kernel / distro / load / RAM. Planner
verifies exact ligature names and falls back only if unavailable. (This is a deliberate step
beyond the plain About `LabelValueRow`, which is iconless: reuse its label:value structure but
add a leading icon slot.)

### Gutter

- `Back` only.
- Suppress the global swipe-up drawer on this screen (matches Phase 18 behavior).

## Health Chip

The single non-telemetry signal on the page. Reuses the Phase-15.1 **shape-coded status**
system (shape carries safety, not color alone).

Two-mode behavior with explicit precedence:

1. **Throttle authoritative when reported** (Raspberry Pi hosts, e.g. Ender 5 Plus / RPi 4):
   - Active under-voltage or active throttling → **caution** (red octagon)
   - Previously occurred since boot, not currently active → **warn** (amber triangle)
   - Clean → **healthy** (go / shapeless)
2. **Temp-threshold fallback when throttle data is absent** (non-Pi hosts, e.g. Ender 3 /
   RockPro64, which has no `vcgencmd`-style throttle flags) — full **3-state parity** with the
   throttle mode so the chip means the same thing visually on both printers:
   - Below warm cutoff → **healthy** (go / shapeless)
   - Above warm cutoff → **warn** (amber triangle)
   - Above hot cutoff → **caution** (red octagon)

Precedence rule: throttle state wins whenever the host reports it; temp-threshold health is the
fallback only when throttle data is unavailable. Never blend both into one chip simultaneously.

The chip always shows something actionable on both fleet printers, and renders the same three
shape-coded states regardless of which signal is driving it.

## Value Formatting

App-defined defaults (locked at staging to keep the phase run from re-deciding these):

- CPU temp: whole degrees, e.g. `48°C`.
- CPU load: integer percent, e.g. `12%`.
- Memory: `used / total`, auto-scaled units, e.g. `612 MB / 3.8 GB`.
- Uptime: compact `2d 3h 14m`, dropping leading zero units from the left (under a day → `3h 14m`).
  This is **host system uptime** (`proc_stats.system_uptime`), NOT Klipper or Moonraker uptime.
- Core count: integer. Total RAM: GB.

## Data Sources & Cadence

- Host identity, CPU/distro/kernel/RAM: `machine.system_info`
- Live CPU/mem/temp/throttle, host uptime: `machine.proc_stats`

Cadence honors the Phase-13 efficiency contract:

- Live values ride the **central subscribe** at a throttled display cadence.
- **No dedicated polling loop** for this screen.
- Subscribe-on-enter / release-on-exit lifecycle (do not keep the subscription alive when the
  screen is off-stage).

Working assumption for the planner to confirm: Moonraker pushes process stats via a
`notify_proc_stat_update`-style channel (~1 Hz) that is separate from the Klipper object
subscribe; the screen taps that channel and throttles display, rather than adding polling.

## Graceful Degradation

Per SC-3:

- Missing or unsupported fields render as `—`.
- The page never blocks or crashes on a sparse or older Moonraker.
- Hide only the unavailable value, never the labeled row's existence — keep the page layout
  stable across both SBCs.
- Whole-page sparse or disconnected state leans on the app's existing global connection
  handling; individual present-but-empty fields still degrade to `—`.

## Product Boundaries

- Read-only. No control surface, no command dispatch, no Apply/ConfirmGuard flow.
- Not About — About is the Dinghy app's own version/identity; this is the printer host.
- Not Settings → System — that keeps its own version+build line; no duplication here.
- No Klipper/Moonraker versions on this page (they live in About).
- No disk/storage section in Phase 19.
- No network section in Phase 19.
- No per-process or per-core breakdown; no per-interface network detail.
- Active-printer scoped only; no side-by-side multi-host view.

## Open Planner Work

- Capture and verify exact `machine.system_info` and `machine.proc_stats` field shapes on BOTH
  real printers: Ender 5 Plus (RPi 4) and Ender 3 (RockPro64). Record which fields each omits.
- Confirm the throttle flag shape on the Pi: current-state vs has-occurred-since-boot bits, and
  which map to caution vs warn.
- Confirm the RockPro64 truly omits throttle data (drives the temp-fallback path) and what CPU
  temp field it exposes.
- Define the CPU-temp fallback thresholds (warm / hot cutoffs) appropriate to these SBCs.
- Confirm the live channel name + push cadence for proc stats, and the correct throttled display
  cadence under the Phase-13 contract.
- Confirm the exact `system_info` fields for CPU description, core count, total RAM, distro
  name/version, and kernel — and uptime field source (`system_info` vs `proc_stats`).
- Verify exact Material Symbol ligature names for the per-row icons (cpu / cores / memory /
  kernel / distro / load / RAM) and the Focus temp glyph; fall back only if unavailable.
- Update ROADMAP SC-1 per the Roadmap Action section above before/at the discuss pass.
