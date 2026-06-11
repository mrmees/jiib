# Phase 24: Navigation Spine - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-09
**Phase:** 24-navigation-spine
**Areas discussed:** Nav-Compose scope, Idle action list, System page + Power, Drawer + morph motion

---

## Nav-Compose scope

### Migration scope
| Option | Description | Selected |
|--------|-------------|----------|
| Spine-only | Root + top-level drill-down to NavHost; keep 4 local sub-nav back-stacks for phases 25–27 | ✓ |
| Full graph now | All 17 Dests + nested sub-navs to one NavHost this phase | |
| Let me think out loud | Owner describes the cut | |

**User's choice:** Spine-only — **plus** an addition: bring the absolute top of the waterfall (the
setup/connection process: does a printer exist → is it connected → is Moonraker accessible) into the
nav spine, which isn't modeled yet.
**Notes:** Today that lifecycle lives above the shell in `RootController`/`TopRoute.derive` as binary
hard-override Splash/Connect states. Owner wants it modeled as the coherent top tier of the waterfall.

### Connection-lifecycle build depth
| Option | Description | Selected |
|--------|-------------|----------|
| Wire existing states | Model existing states as the top tier; reuse current Settings/Splash/Unreachable surfaces; no new screens | ✓ |
| Wire + light setup surface | Above + a minimal dedicated "no printer yet" first-run surface | |
| Full onboarding flow | Guided discover→add→test→connect wizard (flagged as scope creep) | |

**User's choice:** Wire existing states.
**Notes:** Keeps the phase about the skeleton; not-ready states stay hard overrides.

### Morph ↔ back-stack interaction (print starts/ends mid-drill-down)
| Option | Description | Selected |
|--------|-------------|----------|
| Pop to root on foot-gun screens | Print start pops to root only from Move/Extrude/Calibration; leaves Temp/Macros/Fine-Tune/Console/Webcam alone | ✓ |
| Never auto-navigate | State change only changes the root; never moves the user | |
| Pop to root always | Any transition pops to root regardless of screen | |

**User's choice:** Pop to root on foot-gun screens.

---

## Idle action list

### v2-customizability prep
| Option | Description | Selected |
|--------|-------------|----------|
| Data-driven model, defer editor | Ordered typed `List<HomeAction>`; hardcode v1 order; leave room for inline-control variant + reorder/persist; no editor/inline controls now | ✓ |
| Hardcode now, refactor in v2 | Hardcode rows; restructure when v2 lands | |
| Build customization now | Build add/remove/sort editor + inline controls (flagged as own phase) | |

**User's choice:** Data-driven model, defer editor.
**Notes:** Owner surfaced the v2 vision — user-customizable list mixing destination pages AND direct
inline controls (extruder/bed/macros/outputs). Asked whether to plan for it now; agreed: design the
model, don't build the feature.

### v1 idle list membership/order
**User's choice (verbatim order):** Spool → File → Move → Extrude → Macros (if bookmarks exist) →
Calibration → Outputs → Webcam.

### Temperature & Console placement
| Option | Description | Selected |
|--------|-------------|----------|
| Both off idle (Preheat covers heat) | Temp/Console printing-list only; idle heat = Preheat foot button | ✓ |
| Add Console to idle, Temp stays off | | |
| Add both to idle | | |

**User's choice:** Both off idle.

### Capability-absent gating
| Option | Description | Selected |
|--------|-------------|----------|
| Hide absent capabilities | Spool/Outputs/Webcam/Macros rows drop out; list compacts; unifies today's grey/hide mix to hide | ✓ |
| Grey absent capabilities | Show greyed/disabled rows | |

**User's choice:** Hide absent capabilities.

---

## System page + Power

### Idle-foot "Power" button behavior
| Option | Description | Selected |
|--------|-------------|----------|
| Opens the System page | Red Power foot button → System hub (power controls on top, then device entries) | |
| Power = direct controls; System separate | Foot button → power toggles; System reached elsewhere | |
| Relabel it "System" | Foot button labeled "System" (neutral, not red) → opens hub; power controls are top item | ✓ |

**User's choice:** Relabel it "System".

### System page reachability while printing/terminal
| Option | Description | Selected |
|--------|-------------|----------|
| Idle-only via foot; Drawer otherwise | Real entry is the idle "System" foot button; mid-print via vestigial App Drawer | ✓ |
| Always reachable | Persistent System entry in every state (adds chrome) | |

**User's choice:** Idle-only via foot; Drawer otherwise.

### System page contents this phase
| Option | Description | Selected |
|--------|-------------|----------|
| Hub shell + entries; wire Power for real | Build hub + wire Moonraker machine/device_power + Confirm guard | |
| Hub shell + entries; Power still deferred | Build hub; Power entry stays inert | |
| Let me decide the cut | Owner weighs in | ✓ (free-text) |

**User's choice (free-text):** "At this stage it can just go to the current drawer."
**Notes:** No dedicated System-page screen built this phase. The "System" foot button opens the
existing App Drawer (which already hosts Printers/Theme/Settings/About/SystemInfo/Power) as the interim
hub. Power stays unwired (today's tile is a greyed "coming soon" placeholder, `AppDrawer.kt:258`).
Dedicated System page + Power wiring deferred to Phase 28.

---

## Drawer + morph motion

### App Drawer fate
**Resolved implicitly by the System-page decision:** the drawer stays LIVE and as-is (it's the interim
System hub + testing affordance) — not dev-gated, not trimmed this phase.

### State morph transitions
| Option | Description | Selected |
|--------|-------------|----------|
| Hard-cut | Instant content swap; cheapest, zero jank | |
| Cheap one-shot cross-fade | ~150ms one-shot fade on state change; UI law permits; verify on flox | ✓ |
| You decide | Default hard-cut if uncertain | |

**User's choice:** Cheap one-shot cross-fade.

---

## Claude's Discretion

- Exact NavHost route shape, holder hoisting above the graph, root back-stack semantics, and how the
  connection-tier hard-overrides coexist with the NavHost — left to research + planning, grounded in
  existing `AppShell.kt`/`RootController` patterns.

## Deferred Ideas

- **v2 user-customizable home action list** (add/remove/sort destinations + inline direct controls) —
  model built now (D-05), feature deferred to a future milestone.
- **Dedicated System hub page + Power wiring** (Moonraker `machine/device_power`) — Phase 28.
- **Trim/retire the App Drawer's redundant printer-action tiles** — once a real System page exists and
  the waterfall covers real use.
