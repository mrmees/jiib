# Phase 20: System Information Page - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-08
**Phase:** 20-system-information-page
**Areas discussed:** Icon glyphs, Health-chip thresholds, SC-1 rescope

> Owner directed me to read the staging doc `parallel_dinghy/phase-19-system-info-staging.md`
> first. It locked ~90% of the phase (layout, health-chip behavior, formatting, data sources,
> cadence, degradation, boundaries) and narrowed scope to host-health-only. Discussion was limited
> to the three genuine gaps below.

---

## Which open items to settle

| Option | Description | Selected |
|--------|-------------|----------|
| Icon glyphs | Owner selects the drawer-tile, Focus temp, and per-row ligatures | ✓ |
| Health-chip thresholds | Non-Pi temp-fallback warm/hot cutoffs | ✓ |
| SC-1 rescope | Confirm host-health-only narrowing + copy staging doc into repo | ✓ |
| Nothing — doc is enough | Skip discussion, point CONTEXT at the doc | |

**User's choice:** Icon glyphs, Health-chip thresholds, SC-1 rescope (all three).

---

## Icon glyphs

Proposed a 10-slot candidate slate from the curated palette / Material Symbols.

| Option | Description | Selected |
|--------|-------------|----------|
| Approve slate as-is | Lock all 10 candidates exactly as proposed | |
| I'll override some | Approve most, override specific slots | ✓ |
| I'll pick fresh in bucket tool | Hold icons, owner bookmarks the full set | |

**User's choice:** `"pulse alert" for drawer. rest are fine as is`
**Notes:** Drawer-tile glyph overridden to `pulse_alert` (from proposed `monitor_heart`). The
other 9 approved as proposed: dns / thermostat / schedule / developer_board / memory /
deployed_code / code_blocks / speed / data_usage. Planner must verify each resolves in ttf v2.944
(esp. `pulse_alert`) and STOP if absent — no silent fallback (icon law).

---

## Health-chip thresholds

| Option | Description | Selected |
|--------|-------------|----------|
| warn 70 / caution 80 | ≥70°C warn (amber △), ≥80°C caution (red ⯃); mirrors Pi 80°C soft-throttle | ✓ |
| warn 75 / caution 85 | More headroom; ≥85°C = ARM hard-throttle point | |
| Defer to research | Measure RockPro64 temps, propose cutoffs against real data | |

**User's choice:** warn 70 / caution 80.
**Notes:** Locked numbers (not deferred). Researcher still confirms the RockPro64 omits throttle
data and which temp field it exposes, but the cutoffs are fixed for both fleet printers' parity.

---

## SC-1 rescope + staging doc

| Option | Description | Selected |
|--------|-------------|----------|
| Apply now + copy doc in | Rewrite ROADMAP SC-1 now + copy staging doc into phase dir | ✓ |
| Copy doc in, flag SC-1 | Copy doc, only record the SC-1 edit in CONTEXT | |
| Just flag both in CONTEXT | Leave doc external, note edits only | |

**User's choice:** Apply now + copy doc in.
**Notes:** Applied this session — rewrote ROADMAP Phase-20 SC-1 to host-health-only and updated the
Goal + Research-note lines to match; copied the staging doc to
`.planning/phases/20-system-information-page/20-system-info-staging.md`. Staging doc's internal
"Phase 19" labels are stale numbering (= Phase 20 in current roadmap).

## Claude's Discretion

None — owner settled all three open items; remaining ambiguity is pure research (field shapes,
channel names, ligature verification), captured as "Open Planner Work" in CONTEXT, not Claude
discretion.

## Deferred Ideas

- Klipper/Moonraker versions on this page (already in About)
- Disk/storage section (owner-narrowed out)
- Network section / per-interface detail (owner-narrowed out)
- Per-process / per-core CPU breakdown
- Side-by-side multi-host view
