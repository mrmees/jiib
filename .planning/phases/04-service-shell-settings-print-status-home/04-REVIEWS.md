---
phase: 4
reviewers: [codex]
reviewed_at: 2026-05-31T23:25:53Z
plans_reviewed: [04-01-PLAN.md, 04-02-PLAN.md, 04-03-PLAN.md, 04-04-PLAN.md, 04-05-PLAN.md, 04-06-PLAN.md, 04-07-PLAN.md]
note: Single external reviewer (Codex). Claude skipped — running inside Claude Code; Gemini/Cursor/OpenCode/Qwen/CodeRabbit not installed.
---

# Cross-AI Plan Review — Phase 4

## Codex Review

## Summary

The plan set is substantially stronger than average: it has clear phase boundaries, explicit requirement traceability, good reuse of existing Phase 2/3 assets, and a sensible wave order from pure primitives to service wiring to screens to shell integration. The core architecture is directionally right for this app: service-owned Moonraker spine, process-scoped state exposure, pure route derivation, Settings as the only keyboard surface, and Print Status as the single adaptive home. The main risks are not conceptual; they are execution risks around a few Android lifecycle seams, some testability assumptions, and a small amount of scope inflation inside Phase 4 that could slow the phase or destabilize the Nexus 7 floor.

## Strengths

- The plans preserve the most important architectural seam: the Activity does not own the connection.
- Dependency ordering is mostly sound: persistence and pure primitives first, service wiring second, screens third, final shell integration last.
- The plans explicitly reuse proven assets instead of inventing substitutes: `PrinterStateStore`, `MoonrakerSession`, `GraphViewHost`, `ConfirmGuard`, `ThemePrefs`, `ThemeResolver`.
- The route model is appropriately lean for v1. Avoiding Navigation-Compose here is justified.
- Requirement-to-plan mapping is strong. The set clearly covers `CONN-01`, `SET-01`, `SHELL-01..05`, `PRIM-02`, and `PRIM-05`.
- The plans repeatedly guard against regressions already learned in Phase 2, especially the `identify.url` issue and “mock looser than server.”
- The Print Status plan is disciplined about using the existing store throttle and not adding a second sampling layer.
- Security posture is appropriately scoped: key redaction, no service export, no accidental auto-connect from mDNS, no raw secret exposure in logs/notification.
- Human checkpoints are placed where pure automation is insufficient: rotation survival, combined render perf, and shell behavior on actual hardware.

## Concerns

- **HIGH**: `04-05` assumes `AppContainer` can expose or reach a live `MoonrakerSession` for `requestReconnectNow()`, but `04-03` only explicitly publishes flows plus dispatcher. That interface contract is under-specified. Without a defined session-control surface, Splash retry wiring is brittle.
- **HIGH**: `04-07` routing is internally inconsistent. `MainActivity` routes `TopRoute.Connect -> SettingsScreen`, `TopRoute.Splash -> SplashScreen`, and `TopRoute.Shell -> AppShell`, but `SplashScreen` also needs to open Settings and `AppShell` also hosts Settings. The current plan does not define a single source of truth for “temporary Settings navigation outside Shell,” so the branch logic is likely to get messy or duplicated.
- **HIGH**: `04-03`’s rotation-survival test is optimistic about observing “no fresh Connecting/Syncing transition” from `connectionState`. If the service restarts, reconnects, or republishes flows in a way the test harness itself triggers, this can produce false positives/negatives. The plan needs a more concrete continuity signal.
- **MEDIUM**: `04-06` is carrying too much for one wave: adaptive home, new holder, graph integration, stat grid, ConfirmGuard flow, dispatcher failure toast plumbing, and perf validation. That is the likeliest plan to overrun or destabilize the phase.
- **MEDIUM**: mDNS in `04-01` is arguably premature for this phase. It is explicitly additive and unreliable on target hardware, yet it adds manifest permission, multicast lock handling, callbackFlow complexity, and UI branching in Settings before the core shell is even live.
- **MEDIUM**: `04-03` introduces a fairly custom reactive service-locator publication model. It can work, but the plan does not clearly state how stale flows/session references are replaced atomically when a session rebuild happens. That is a subtle correctness risk.
- **MEDIUM**: `04-04` uses Material3 `OutlinedTextField`s in a project otherwise guided by a custom token system and Phase-3 control language. That may be acceptable for Settings, but the plan should explicitly define how Material colors/typography are bridged to the token theme to avoid a visually off-spec screen.
- **MEDIUM**: `04-05` derives splash reason text only from enums because no `state_message` exists in `PrinterState`. That means the user loses the valuable Moonraker/Klippy recovery detail described in the phase context unless Phase 2/4 expands state transport. The plan currently accepts a weaker UX than the phase intent.
- **MEDIUM**: `04-06`’s holder tracks “at minimum extruder and heater_bed,” but capability gating is a core requirement pattern. The plan should be stricter about printers without beds, without standard heater names, or with chamber heaters.
- **LOW**: `04-04` says “never render a saved API key back unmasked.” If the field is always blank on revisit, users cannot tell whether a key is stored. That is secure, but UX-hostile unless there is an explicit “saved” indicator.
- **LOW**: `04-07` introduces `onOpenSettings` in `AppShell` but its own task text then routes via `Dest.Settings` internally. That suggests unused API surface or unresolved design.
- **LOW**: Test coverage is good for pure logic but still thin around process death, notification permission denial on newer Android, and service idle behavior after config clear.

## Suggestions

- Define a minimal `SessionControl` interface in `AppContainer` now, with only what UI needs in Phase 4, such as `requestReconnectNow()`. Do not expose raw `MoonrakerSession`.
- Simplify top-level routing before implementation. Recommended model:
  - `MainActivity` always hosts a single root shell controller.
  - Root controller decides between `Splash` and `Shell`.
  - `Settings` exists as a destination within shell, plus a controlled escape from splash/first-run into that same destination state.
- Tighten `04-03` publication semantics. Specify one atomic spine snapshot object, for example `data class SpineHandle(...)`, published via one `StateFlow<SpineHandle?>`, with derived flows hanging off that. That avoids partially swapped references.
- Reduce Phase 4 scope by moving mDNS to a follow-up increment unless the team explicitly wants it now. Manual host/port entry already satisfies the requirement floor.
- If mDNS stays, make its UI fully optional and lazy. Do not construct discovery machinery until the user taps Scan.
- Add a small explicit plan item for how `PrinterState` will carry splash-relevant recovery text if available. Even a nullable `statusMessage` populated from `printer.info` would align the implementation better with the context.
- Strengthen the rotation-survival test by asserting continuity of the same service-owned spine instance or same monotonic session marker, not just absence of intermediate states.
- In `04-04`, specify token-aware `TextField` styling or a thin wrapper so Settings does not visually drift from the design system.
- In `04-06`, define the grid fallback rules for printers lacking expected heaters or file/job fields. Otherwise the UI logic will get ad hoc.
- Add an explicit “API key saved” affordance in Settings without displaying the secret itself.
- Consider splitting `04-06` into two executable units if schedule slips:
  - adaptive home + grid + Stop/Confirm
  - sparkline integration + perf checkpoint
- Add one explicit negative-path test for “config cleared while service is running” to confirm the service goes idle cleanly and the UI routes back to setup without leaked connection state.

## Risk Assessment

**Overall risk: MEDIUM**

The architecture is good and the phase is very likely to succeed functionally, but there are a few high-leverage seams that are not fully nailed down yet: session control exposure, atomic spine publication during rebuild, and top-level routing ownership between Splash, Shell, and Settings. Those are fixable before execution. The other meaningful risk is scope concentration in `04-06` plus optional mDNS complexity. If those are tightened, this drops toward low risk; if left ambiguous, they are the places most likely to produce rework on the API 30 / Nexus 7 floor.

---

## Consensus Summary

Only one external reviewer (Codex) was available this run — Claude was skipped (this command runs inside Claude Code), and Gemini/Cursor/OpenCode/Qwen/CodeRabbit are not installed. So there is no cross-model triangulation; the synthesis below is a prioritization of Codex's findings, not a multi-model consensus. To get a true cross-check, install a second CLI (e.g. `gemini`) and re-run `/gsd-review --phase 4 --gemini`.

**Overall verdict:** MEDIUM risk. Architecture is sound and requirement coverage is strong; the real risk is a handful of under-specified cross-plan interface seams plus scope concentration in `04-06`. All findings are fixable before execution.

### Must-fix before execution (HIGH)

1. **Session-control contract is under-specified (`04-03` ↔ `04-05`).** `04-05` (Splash) needs `requestReconnectNow()` but `04-03` only publishes flows + dispatcher. Define a minimal `SessionControl` interface on `AppContainer` (just what the UI needs) — do **not** leak the raw `MoonrakerSession`.
2. **Top-level routing has no single source of truth (`04-07`).** `MainActivity`, `SplashScreen`, and `AppShell` can all reach Settings, with no defined owner for "temporary Settings outside Shell." Collapse to one root controller: root decides Splash vs Shell; Settings is a Shell destination plus a controlled escape from Splash/first-run into that same state. Resolves the related LOW `onOpenSettings`-vs-`Dest.Settings` inconsistency too.
3. **Rotation-survival test is unreliable (`04-03`).** Asserting "no fresh Connecting/Syncing transition" can false-pass/fail if the harness itself triggers a republish. Assert a concrete continuity signal instead — same service-owned spine instance or a monotonic session marker.

### Should-fix (MEDIUM)

- **`04-06` is overloaded** (adaptive home + holder + graph + stat grid + ConfirmGuard + toast plumbing + perf). Most likely plan to overrun. Consider splitting: (a) adaptive home + grid + Stop/Confirm, (b) sparkline integration + perf checkpoint.
- **mDNS in `04-01` may be premature** — additive, unreliable on target HW, and pulls in permission/multicast-lock/callbackFlow complexity before the shell is even live. Either defer to a follow-up (manual host/port already meets the floor) or make the discovery machinery fully lazy (construct nothing until the user taps Scan).
- **Atomic spine publication (`04-03`)** — specify how stale flow/session refs are swapped atomically on session rebuild. Recommend one `StateFlow<SpineHandle?>` snapshot with derived flows hanging off it, to avoid partially-swapped references.
- **Material3 `OutlinedTextField` vs token system (`04-04`)** — define how Material colors/typography bridge to the Phase-3 theme tokens so Settings doesn't drift off-spec.
- **Splash reason text loses Klippy detail (`04-05`)** — enums-only loses Moonraker/Klippy recovery messages from the phase intent. Add a nullable `statusMessage` to `PrinterState` (e.g. from `printer.info`).
- **Capability gating too loose (`04-06`)** — "at minimum extruder + heater_bed" needs explicit fallback rules for printers without beds, nonstandard heater names, or chamber heaters.

### Nice-to-have (LOW)

- Add an "API key saved" affordance in Settings without revealing the secret (current always-blank field is secure but UX-hostile).
- Add a negative-path test: "config cleared while service running" → service goes idle cleanly, UI routes back to setup with no leaked connection state.
- Broaden test coverage around process death, notification-permission denial on newer Android, and post-config-clear service idle behavior.

### Divergent Views
None — single reviewer.

To incorporate this feedback into planning:
  `/gsd-plan-phase 4 --reviews`
