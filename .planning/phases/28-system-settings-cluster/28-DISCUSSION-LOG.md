# Phase 28: System / Settings Cluster - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-12
**Phase:** 28-system-settings-cluster
**Areas discussed:** System hub & nav retirement, Densification grammar (C6), Printers interaction model, Theme editor scope

---

## Todo Fold (pre-discussion)

| Todo | Folded |
|------|--------|
| 2026-06-11-retire-swipe-up-nav.md | ✓ |
| 2026-06-05-phase-15.1-review-deferred-findings.md (WR-01/WR-02 theme editor) | ✓ |
| 2026-06-05-phase-14-review-deferred-wr02-wr03.md (WR-02 seedTheme only; WR-03 stays) | ✓ |
| 2026-06-05-dev-overlay-panel-drag.md | ✓ |
| 2026-06-05-settings-densify-one-page-restyle.md (C6) | already locked by SC-3 |
| 2026-06-05-printers-edit-delete-mode-buttons.md (R4) | already locked by SC-3 |

---

## System hub & nav retirement

| Option | Description | Selected |
|--------|-------------|----------|
| Real System page | New jiib list NavDest carries the system cluster; interim drawer-hub retired | ✓ |
| Restyle the drawer as System | Keep the overlay drawer rebuilt as a list | |
| Drawer stays as-is | Restyle only the five content screens | |

**User's choice:** Real System page

| Option | Description | Selected |
|--------|-------------|----------|
| Kill both, rehome orphans | Delete swipe-up + AppDrawer entirely | ✓ |
| Kill gesture, keep drawer | Drawer survives behind a System-page entry | |
| Keep both for now | Defer retirement to Phase 29 | |

**User's choice:** Kill both, rehome orphans

| Option | Description | Selected |
|--------|-------------|----------|
| Add to home idle list | Temperature/Console/Fine-Tune become home rows (revises P24 D-07) | ✓ |
| Put them on the System page | | |
| Temperature via Preheat, rest on System | | |

**User's choice:** Add to home idle list

| Option | Description | Selected |
|--------|-------------|----------|
| Carry as inert stub | Red Power row, inert, visual-reminder posture | ✓ |
| Drop it entirely | | |
| Implement minimal host actions | Flagged as scope creep | |

**User's choice:** Carry as inert stub

| Option | Description | Selected |
|--------|-------------|----------|
| List + Focus selection grammar | P27 hub pattern (select then Open) | |
| Plain navigation list | | |
| List with direct tap + info Focus | Direct-tap rows + static identity Focus | ✓ |

**User's choice:** Option 3 with owner-specified caps — "limit the focus to 20% height in
portrait and 40% width in landscape."

| Option | Description | Selected |
|--------|-------------|----------|
| Keep Preheat foot | Fast path next to System | ✓ |
| Retire it | | |

**User's choice:** Keep Preheat foot

| Option | Description | Selected |
|--------|-------------|----------|
| Printer + connection | Live status strip | |
| App identity | jiib lockup + version + active printer name | ✓ |
| Host vitals | Mini health strip | |

**User's choice:** App identity

| Option | Description | Selected |
|--------|-------------|----------|
| System in printing shortcuts | Printing shortcut grid gains a System entry | ✓ |
| Not reachable mid-print | | |
| Persistent System affordance | | |

**User's choice:** System in printing shortcuts

| Option | Description | Selected |
|--------|-------------|----------|
| Frequency-first | Theme → Settings → Printers → SysInfo → About → Power | |
| Printer-first | Printers → Settings → Theme → SysInfo → About → Power | ✓ |
| You decide | | |

**User's choice:** Printer-first

---

## Densification grammar (C6)

| Option | Description | Selected |
|--------|-------------|----------|
| Dense jiib ListRow variant | Compact kit component, one visual family | ✓ |
| Conventional Android settings rows | | |
| Per-screen freeform | | |

**User's choice:** Dense jiib ListRow variant

| Option | Description | Selected |
|--------|-------------|----------|
| Keep 15sp floor | Density from spacing, not type | ✓ |
| 13sp floor for config metadata | | |
| You decide per element | | |

**User's choice:** Keep 15sp floor

| Option | Description | Selected |
|--------|-------------|----------|
| Settings + About only | Hard one-page goal at M; others scroll | ✓ |
| Everything except Theme editor | | |
| Soft goal everywhere | | |

**User's choice:** Settings + About only

| Option | Description | Selected |
|--------|-------------|----------|
| Densified TokenTextField inline | Inline system keyboard, numeric where numeric | ✓ |
| Field-takeover editing | | |
| You decide | | |

**User's choice:** Densified TokenTextField inline

---

## Printers interaction model

| Option | Description | Selected |
|--------|-------------|----------|
| Foot-bar mode toggles | Edit (neutral) + Delete (red) on FootButtonBar; rows respond per mode | ✓ |
| Mode buttons above the list | | |
| You decide | | |

**User's choice:** Foot-bar mode toggles

| Option | Description | Selected |
|--------|-------------|----------|
| Boundary is final | 15.2 IA dissolve holds | ✓ |
| Merge Settings into Printers | | |
| Something else moves | | |

**User's choice:** Boundary is final

| Option | Description | Selected |
|--------|-------------|----------|
| Active-printer detail + foot Add | Focus = active printer card; foot = Add/Edit/Delete/Back | ✓ |
| Selected-row detail | | |
| You decide | | |

**User's choice:** Active-printer detail + foot Add

---

## Theme editor scope

| Option | Description | Selected |
|--------|-------------|----------|
| Hue wheel + S/V square | Standard 2D picker, full color freedom | ✓ |
| Hue wheel + 2 sliders | | |
| Document hue-only as intentional | | |

**User's choice:** Hue wheel + S/V square

| Option | Description | Selected |
|--------|-------------|----------|
| Delete the dead plumbing | Remove setMaxItems + axis; 4-slot grid is the design | ✓ |
| Wire a pool-size control | | |

**User's choice:** Delete the dead plumbing

| Option | Description | Selected |
|--------|-------------|----------|
| One dense scroll | Single page, 1-2 screenfuls, inline sub-pickers | ✓ |
| Sectioned sub-pages | | |
| You decide | | |

**User's choice:** One dense scroll

---

## Claude's Discretion

- Exact dense-row heights/spacing, section grouping, toggle/dropdown shapes.
- SysInfo + About composition (content/densification beyond the one-page rule).
- System page NavDest shape, back-stack, FloatingEStop/UAT-4 application.
- Home idle list integration of the three new rows; glyphs need owner sign-off (icon law).
- S/V square implementation details within the one-dense-scroll rule.

## Deferred Ideas

- Real power control (host shutdown/reboot, Moonraker power devices) — own phase.
- Calibration hub hide-not-grey flip (carried from P27).
