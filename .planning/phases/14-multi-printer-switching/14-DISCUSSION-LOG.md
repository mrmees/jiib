# Phase 14: Multi-Printer Switching - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-04
**Phase:** 14-multi-printer-switching
**Areas discussed:** Switcher UX & entry point, Profile model & migration, Active-state & delete behavior, Settings CRUD UX

---

## Switcher UX & Entry Point

| Option | Description | Selected |
|--------|-------------|----------|
| Full-screen printer list | Field-of-tiles screen (new Dest.Devices), tap-to-switch, 'Add printer' tile | ✓ |
| Compact picker over the drawer | Small dialog/sheet listing profiles in place | |
| Reuse Settings profile list | Devices tile deep-links into Settings connection section | |

**User's choice:** Full-screen printer list

| Option | Description | Selected |
|--------|-------------|----------|
| Instant rebind, show Splash | Persist active → existing collectLatest rebinds → recovery Splash → new Status | ✓ |
| Confirm first, then rebind | Confirm guard before switching | |
| Instant, no Splash | Background rebind, stay in place | |

**User's choice:** Instant rebind, show Splash

| Option | Description | Selected |
|--------|-------------|----------|
| On the Devices drawer tile | Active printer name as the tile label/subtitle | ✓ |
| Header on the Devices list only | Only highlighted in the list | |
| Both tile + list highlight | Name on tile AND list highlight | |

**User's choice:** On the Devices drawer tile (active tile in list also highlighted)

| Option | Description | Selected |
|--------|-------------|----------|
| Just switch — print keeps running | Old printer keeps printing; tablet only changes what it watches | ✓ |
| Warn that current printer is printing | One-line note/confirm | |
| You decide | — | |

**User's choice:** Just switch — print keeps running

**Notes:** No hi-fi mockup exists for the Devices/switcher screen — it must follow LAYOUT.md/THEMING.md grammar (roadmap `UI hint: yes`).

---

## Profile Model & Migration

| Option | Description | Selected |
|--------|-------------|----------|
| Generated UUID | Stable random id; host/port/name freely editable; same-host profiles allowed | ✓ |
| host:port as natural key | Printer IS its host:port | |
| You decide | — | |

**User's choice:** Generated UUID

| Option | Description | Selected |
|--------|-------------|----------|
| Switch to profile-id keying | Re-key per-printer prefs on stable id | ✓ |
| Keep host-keying as-is | Leave preferred_cam_<host> | |
| You decide | — | |

**User's choice:** Profile-id keying — **plus** added requirement: "we'll need separate app themes for different printers."

| Option | Description | Selected |
|--------|-------------|----------|
| Full theme per printer | base + accent + text-size per profile, overrides global | ✓ |
| Accent color only | Just accent per printer | |
| Optional override, else global | Opt-in custom theme | |

**User's choice:** Full theme per printer

| Option | Description | Selected |
|--------|-------------|----------|
| Settings Appearance edits active printer | Existing Appearance section writes the active profile's theme | ✓ |
| Per-printer theme on Devices/edit screen | Theme controls colocated with each printer | |
| You decide | — | |

**User's choice:** Settings Appearance edits active printer

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-migrate into profile #1 | Existing config → profile #1, global theme → its theme | |
| Fresh start (re-add printers) | No migration; re-add from scratch | ✓ |
| You decide | — | |

**User's choice:** Fresh start (re-add printers) — no migration code

| Option | Description | Selected |
|--------|-------------|----------|
| Optional, defaults to host | Blank name → displays as host | ✓ |
| Required free-text name | Must name before saving | |
| You decide | — | |

**User's choice:** Optional, defaults to host

**Notes:** Per-printer full theming is the biggest net-new surface vs the roadmap's "STANDARD — just profile management + clean rebind" note; it rewires theme seeding (global ThemePrefs → active profile's theme, re-seeded on switch).

---

## Active-State & Delete Behavior

| Option | Description | Selected |
|--------|-------------|----------|
| Connect prompt → Settings | Reuse D-11 routing for 0 profiles | ✓ |
| Empty Devices list with 'Add printer' | New empty-state UI | |
| You decide | — | |

**User's choice:** Connect prompt → Settings

| Option | Description | Selected |
|--------|-------------|----------|
| Auto-select another, rebind | Delete active → pick next remaining + rebind; last → Connect | ✓ |
| Go idle → Devices list to pick | Idle state, user picks | |
| You decide | — | |

**User's choice:** Auto-select another, rebind

---

## Settings CRUD UX

| Option | Description | Selected |
|--------|-------------|----------|
| Profile list + add/edit form | List of profiles (active marked) + 'Add printer' opens existing form | ✓ |
| Inline editable list | Inline-expandable editable rows | |
| You decide | — | |

**User's choice:** Profile list + add/edit form

| Option | Description | Selected |
|--------|-------------|----------|
| Confirm guard before delete | Full-screen Confirm guard (08-confirm.png) | ✓ |
| Swipe / inline delete, no confirm | Quick delete with undo | |
| You decide | — | |

**User's choice:** Confirm guard before delete

---

## Claude's Discretion

- UUID generation scheme + profile-store persistence shape (JSON blob vs Proto vs per-profile keys).
- Active-profile-id × profiles flow composition producing the active `ConnectionConfig` (contract: service keeps consuming `Flow<ConnectionConfig?>`).
- Whether the Devices list shows live status for NON-active printers in v1.
- Theme-switch timing/flicker handling during the rebind Splash.

## Deferred Ideas

- Profile import/export / QR-share of a profile.
- Live per-printer status badges for non-active printers (needs background probing).
- Cloud/remote (non-LAN) profiles — out of project scope.
- Reviewed-not-folded todos: 6 keyword-fuzzy `todo.match-phase` false positives (none concern profile management/switching).
