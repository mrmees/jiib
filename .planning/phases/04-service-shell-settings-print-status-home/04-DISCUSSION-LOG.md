# Phase 4: Service, Shell, Settings & Print-Status Home - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-05-31
**Phase:** 4-service-shell-settings-print-status-home
**Areas discussed:** Print Status home, The Stop control, Splash & first-run, Shell surfaces
**Note:** CONTEXT regenerated against `docs/ui_design/` (LAW). Service/FGS/connection/routing
decisions from the prior CONTEXT carried forward as-is; the UI/shell half was regenerated here.

---

## Print Status home

### Gutter scope
| Option | Description | Selected |
|--------|-------------|----------|
| Stop only | Just the Stop control; Tune/Pause added in their phases | |
| Stop + greyed Tune/Pause | All three tiles, Tune/Pause disabled "coming soon" | ✓ |
| You decide | Claude picks per design philosophy | |

### Idle Focus (Klippy ready, no print)
| Option | Description | Selected |
|--------|-------------|----------|
| Live temp/status readout | Repurpose Focus to nozzle/bed temps + "Ready" | ✓ |
| Keep ring, idle 0% | Ring drawn at 0% with "Ready" label | |
| You decide | Claude composes the idle Focus | |

### Render primitive on home
| Option | Description | Selected |
|--------|-------------|----------|
| Ring + numbers only | Match mockup 03; defer GraphView to Phase 5 | |
| Add heater sparkline | Add GraphView sparkline — prove both render primitives now | ✓ |
| You decide | Claude decides | |

**Notes:** Adding the sparkline means ring + sparkline + live grid render on one screen —
flagged as an Adreno-320 perf watch (Phase-3 two-part gate re-open conditions apply).

---

## The Stop control

### What Stop fires
| Option | Description | Selected |
|--------|-------------|----------|
| Always firmware E-stop | Stop = printer.emergency_stop always, idle or printing | ✓ |
| Context-aware | printing → cancel, idle → emergency_stop | |
| Cancel + separate E-stop | Gutter Stop = cancel; E-stop is a distinct affordance | |

### E-stop confirm copy
| Option | Description | Selected |
|--------|-------------|----------|
| Warn about recovery | Spell out "firmware restart required to recover" | |
| Terse | Minimal copy ("Emergency stop?" / "This halts the printer.") | ✓ |
| You decide | Claude writes the copy | |

**Notes:** Consequence — firing E-stop drives klippy → shutdown, so the splash recovery set
must include firmware_restart (covered in Splash area). Print-cancel deferred to Phase 7.

---

## Splash & first-run

### Klippy-down recovery actions
| Option | Description | Selected |
|--------|-------------|----------|
| Retry + FW Restart + Restart | Retry, firmware_restart, host restart | ✓ |
| Retry + FW Restart only | Minimal viable recovery | |
| You decide | Claude picks the set | |

### First run / no saved config
| Option | Description | Selected |
|--------|-------------|----------|
| Auto-open Settings | Route straight into Settings connection section | |
| Splash connect prompt | "Set up your printer" CTA → Settings | ✓ |
| You decide | Claude designs first-run entry | |

### Bad connection (saved config failing)
| Option | Description | Selected |
|--------|-------------|----------|
| Retry + Edit connection | Retry plus an Edit-connection path into Settings | ✓ |
| Retry only | Retry only (risks trapping the user) | |
| You decide | Claude ensures no dead end | |

---

## Shell surfaces (App Drawer + Settings)

### Future panel tiles
| Option | Description | Selected |
|--------|-------------|----------|
| Greyed "coming soon" | Show all roadmap tiles; future ones greyed | ✓ |
| Hidden until built | Only show working tiles | |
| You decide | Claude decides | |

### Red Power tile behavior
| Option | Description | Selected |
|--------|-------------|----------|
| Host power menu | Confirm-guarded machine.shutdown / machine.reboot | |
| Greyed coming-soon | Placeholder, inert in Phase 4 | ✓ |
| Global E-stop shortcut | Drawer-reachable emergency_stop | |

### Theming depth
| Option | Description | Selected |
|--------|-------------|----------|
| Dark/Light + S/M/L + accent picker | Toggle + text size + single accent-color picker | ✓ |
| Full role-token editor | Editors for all role tokens | |
| Dark/Light + S/M/L only | No custom color editing | |

### Feature toggles
| Option | Description | Selected |
|--------|-------------|----------|
| Defer — none yet | No toggles section in Phase 4 | ✓ |
| Scaffold a couple | mDNS auto-scan / show-sparkline toggles | |
| You decide | Claude adds only real toggles | |

**Notes:** Settings structure (conventional Android list, token-themed, keyboard allowed) was
already locked by `docs/ui_design/` and not re-asked.

---

## Claude's Discretion

- Command-dispatch (PRIM-05) timeout value, busy-state visual, debounce window.
- Persistent notification content / channel / tap target.
- Exact sparkline placement and idle temp-readout composition.
- Splash reason-text formatting from `klippy_state`; terse E-stop confirm wording.

## Deferred Ideas

- Print-cancel / pause / resume / Tune (Phases 5 & 7).
- Full multi-role-token custom-theme editor (later polish).
- Red Power tile real behavior + "Devices" power-device panel (future).
- Full multi-trace temperature history graph (Phase 5).
- mDNS-primary / multi-printer (v2); boot-autostart + Doze/always-on + screensaver (Phase 8).
- Navigation-Compose adoption; richer trusted-client auth UI.
