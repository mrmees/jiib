# Phase 16: Home / Print-Status Redesign - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-06
**Phase:** 16-home-print-status-redesign
**Areas discussed:** Preheat action, Forward stubs, Terminal surfacing, Babystep setting

**Note:** A precursor staging note
(`/mnt/e/claude/personal/github/parallel_dinghy/phase-16-home-status-redesign-staging.md`) pre-locked
the four-state `PrintStatusMode` model, every state's Focus/Field/Gutter layout, the interactive-grid
flexible-tile rule, babystep mechanics, and terminal behavior. Discussion targeted ONLY the gaps the
staging note left open — these four questions — per the owner's instruction that the note "handles
most questions."

---

## Standby Preheat action

| Option | Description | Selected |
|--------|-------------|----------|
| Reuse PresetSelector | Preheat reveals the fixed PLA/PETG/ABS/TPU selector; pick → applyPreset | |
| Fire a default preset | Preheat immediately fires one default material (e.g. PLA), no chooser | |
| Open Temperature panel | Preheat navigates to the full Temperature screen | |

**User's choice:** Free-text — "If spoolman information is available, preheat to current spool temps,
otherwise open preheat selection page."
**Notes:** Becomes D-01, a spool-aware hybrid: fire `applyPreset` directly to the active spool's
filament temps (`SpoolmanFilament.settingsExtruderTemp`/`settingsBedTemp`, already in our model) when
Spoolman exposes them, else reuse the Phase-5 `PresetSelector`. Verified the data + active-spool
resolution already exist in `PrintStatusScreen`.

---

## Forward entry points / capability-gated stubs

| Option | Description | Selected |
|--------|-------------|----------|
| Drawer-only, add greyed now | Add greyed Output + System Info tiles to the drawer now (existing pattern) | ✓ |
| Drawer-only, defer stubs | Don't add P18/P19 stubs in P16 | |
| Standby launcher too | Surface greyed forward tiles in the standby launcher grid as well | |

**User's choice:** Drawer-only, add greyed now (D-02)
**Notes:** Uses the established `DrawerTileSpec.dest = null` greyed pattern. Standby launcher stays the
curated high-use list from the staging note. Tune (P17) stub stays on the active-print row (D-03);
Power stays inert/design-only (D-04).

---

## Terminal surfacing

| Option | Description | Selected |
|--------|-------------|----------|
| Passive (notes as-is) | Terminal sits on home; seen next time user navigates home; no auto-yank | ✓ |
| Auto-surface home | On entering Terminal, route the user to the home screen immediately | |

**User's choice:** Passive (D-05)
**Notes:** Honors the existing "shell does NOT reset user to Home" rule (G-A1, 13-05).

---

## Babystep app setting placement

| Option | Description | Selected |
|--------|-------------|----------|
| Settings tile | Babystep enable + layer-count under the general "Settings" tile, numeric keyboard | ✓ |
| Let planner place it | Defer placement to the planner | |

**User's choice:** Settings tile (D-06)
**Notes:** Overall app preference (not per-printer for P16); numeric keyboard allowed because it's in
Settings, not a printer control.

---

## Claude's Discretion

- Standby glance metric "MCU/host temp OR host load, whichever is available/useful" — rule left to
  research/planning.
- Rework strategy (in-place refactor of the 942-line `PrintStatusScreen` vs. restructure around the
  classifier) — planning/implementation call. Classifier extraction itself is locked.

## Deferred Ideas

- Per-printer Standby Focus image (future printer settings).
- User-customizable / reorderable standby launcher grid (future release).
- Power → full host/system power dialog (future; destructive/elevation path).
- Mid-print object exclusion `EXCLUDE_OBJECT` (v2).
- Saving Z-offset to config (stays a Calibration action; P16 babystep is session-only).
