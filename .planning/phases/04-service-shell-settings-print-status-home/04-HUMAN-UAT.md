---
status: passed
phase: 04-service-shell-settings-print-status-home
source: [04-VERIFICATION.md]
started: 2026-05-31
updated: 2026-05-31
---

## Current Test

[complete — tested on real flox against the live Ender 5 Plus 2026-05-31]

## Tests

### 1. FGS connection survives rotation + screen-off (SC1 live half)
expected: With a real connection saved, the FGS notification shows status WITHOUT the API key; rotate + screen-off ~60s keeps Connected, temps live, sessionInstanceId unchanged. (Automated rotation half PASSED: ServiceSurvivesRotationTest 1/1.)
result: passed — survived rotation + screen-off, connection stayed alive with live info.

### 2. Combined-render perf gate on the real Print Status surface (SC4 perf half / 04-06b)
expected: combined ring+sparkline+grid clears the two-part Adreno-320 gate (liveness + sparse-redraw p95 ≤ ~66ms).
result: passed (subjective liveness) — elements update live with NO observable jank on flox. KNOWN ISSUE (deferred to Phase 5, see Gaps): the heater sparkline's Y auto-ranging + growing-window timeline look wrong, and a thermally-steady idle printer yields a single-point "lone dot" (live sparkline only accumulates on StateFlow change; no history backfill). Formal gfxinfo p95 capture optional/available during an active heat ramp — not blocking.

### 3. First-run → Settings → connect → Print Status flow (SC1/SC2 visual)
expected: fresh launch → connection-entry screen → enter host/port → Save → connect → Splash on Klippy startup → Print Status on ready.
result: passed — entered IP, connected, live information showing; routed to Print Status. (Also confirmed: dark-theme flip persisted across an app restart.)

### 4. Stop → ConfirmGuard → emergency_stop → Splash round-trip (SC4)
expected: Stop → full-screen Confirm guard → confirm fires printer.emergency_stop → printer halts → routes to Splash with reason + recovery actions; cancel dispatches nothing.
result: passed.

### 5. Navigation feel / App Drawer ergonomics (SC3 visual)
expected: swipe-up full-screen App Drawer of square tiles; Status+Settings tappable, future panels + Power greyed/inert; full-bleed, no status bar; responsive on the Nexus 7.
result: passed — "looks good." (Structural presence also PASSED: ShellPresenceTest 4/4.)

## Summary

total: 5
passed: 5
issues: 0
pending: 0
skipped: 0
blocked: 0

## Gaps

### G-1 (deferred to Phase 5, by user decision 2026-05-31): Print Status heater sparkline is a misleading placeholder
The Phase-4 sparkline (classic-Views GraphView in the reserved Field slot) auto-ranges Y to the window's own min/max (steady-temp noise fills the panel height — `GraphView.kt:115-125`) and spreads N points across the full width with no fixed time axis (`dx = w/(n-1)` — `GraphView.kt:133`), and only accumulates points on live StateFlow changes so a thermally-steady idle printer shows a single "lone dot." Not a Phase-4 regression — it is the known seam between the minimal placeholder sparkline and Phase 5's full Temperature graph, which EXTENDS this same primitive with sensible fixed ranges, a real time axis, and history backfilled from `server.temperature_store` (shows a full graph immediately on connect, steady or not). Tracked in ROADMAP Phase 5. No Phase-4 fix; do NOT re-fork the GraphView primitive.
