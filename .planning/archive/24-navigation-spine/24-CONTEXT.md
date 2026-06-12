# Phase 24: Navigation Spine - Context

**Gathered:** 2026-06-09
**Status:** Ready for planning

<domain>
## Phase Boundary

Build the jiib redesign's **navigation skeleton**. Turn `PrintStatusScreen` into the morphing
waterfall **root** (idle / printing / terminal — root whenever a print exists, no longer a navigable
destination). Adopt **Navigation-Compose** for the drill-down back-stack, retiring the hub-and-spoke
`when(dest)` + `ShellNavState` holder in `AppShell.kt`. Remove the gutter app-wide. Add the floating
printing-only emergency-stop. Surface a **"System" entry** (foot button) that rehomes device/system
settings off the printer waterfall.

**This phase is the SPINE, not the screen migrations.** Individual drill-down screens
(Files/Macros/Console/Webcam, the adjustment screens, motion/calibration, the System/Settings cluster)
are rebuilt onto the Phase-23 component classes in phases 25–28. Phase 24 stands up the skeleton those
migrations hang from and leaves screen internals largely untouched.

</domain>

<decisions>
## Implementation Decisions

### Navigation-Compose adoption (scope)
- **D-01: Spine-only migration.** Migrate the **root** (PrintStatus waterfall) + the **top-level
  drill-down destinations** to a Navigation-Compose `NavHost` back-stack, retiring the old
  `when(dest)` + `ShellNavState` hub-and-spoke holder. **KEEP** the four existing **in-screen local
  sub-nav back-stacks** — Calibration routine, Fine-Tune group, Outputs detail, Macros
  bookmarked-vs-system — as in-screen state for now. Phases 25–27 convert those when those screens are
  rebuilt. Do NOT promote them to nav routes/nested graphs this phase (avoids double-touching screens
  that get reworked anyway). The vestigial App Drawer may keep using the same nav entry points.
- **D-02: Connection lifecycle is the TOP tier of the waterfall.** Model the existing top-of-app
  connection states — *no printer configured → connecting → is Moonraker reachable → ready
  (PrintStatus root)* — as the explicit top of the conditional waterfall, routed through the new nav
  spine, instead of the current binary `Splash`. This is the `TopRoute.derive` /
  `Connect`/`Splash`/`Shell` logic in `route/TopRoute.kt`, reshaped as a coherent stepped progression.
- **D-03: Wire existing surfaces only — no new onboarding.** Reuse the current Settings / Splash /
  Unreachable surfaces as-is for the connection tier. Do NOT build a dedicated first-run setup screen
  or a guided connection wizard (that's a future phase). The not-ready connection states remain **hard
  overrides** — the back-stack cannot dismiss them (you can't "back" out of "not connected").
- **D-04: Print-state transition → pop-to-root on foot-gun screens only.** If a print STARTS (or ENDS)
  while the user is deep in a drill-down screen the *new* state intentionally hides — **Move, Extrude,
  Calibration** (mid-print foot-guns) — pop the back-stack to the morphing root. Screens that stay
  valid mid-print (**Temperature, Macros, Fine-Tune, Console, Webcam**) are left undisturbed. Honors
  the "printing narrowing is a guard rail" intent. A state change never yanks the user off a still-valid
  screen.

### Idle action list (the new primary nav)
- **D-05: Data-driven typed list model now; editor + inline controls deferred to v2.** Build the idle
  Field action list as an ordered `List<HomeAction>` of **typed items** rendered by the Phase-23
  `ListRow`. v1 ships a hardcoded default order/membership, but the model leaves room for a future
  `InlineControlAction` variant (extruder/bed/etc. controls living inside a row) and user
  reorder/persist. **NO customization editor and NO inline direct-controls this phase** — those are the
  v2 feature (see Deferred Ideas). This is cheap future-proofing so v2 is additive, not a re-architect.
- **D-06: v1 idle list order (locked):** `Spool → File → Move → Extrude → Macros → Calibration →
  Outputs → Webcam`. (Owner-specified order, 2026-06-09.)
- **D-07: Temperature and Console are OFF the idle list.** Both are reachable while **printing** (the
  locked printing-state list is Temp/Macros/Fine-Tune/Console/Webcam) but not idle. Idle heat prep is
  the **Preheat** foot button. Fine-Tune is likewise printing-only (live adjust). Idle stays lean.
- **D-08: Absent capabilities HIDE (drop out), not grey.** Rows whose capability is unavailable simply
  drop out and the list compacts: **Spool** (no Spoolman), **Outputs** (no controllable outputs),
  **Webcam** (no cam / toggle off), **Macros** (shown only **if bookmarks exist**). This **unifies**
  today's mixed behavior (Webcam/Spool currently grey, Outputs hides) → all **hide**. Matches the
  lists-first / "your list" mental model.

### System page + Power
- **D-09: Foot button relabeled "System" (was "Power").** The idle third foot button is **"System"**,
  not "Power". This is a deliberate **deviation from** the sketch-findings note
  (`…jiib-redesign-direction.md` / layout-navigation.md said the foot button is "Power"). Rationale:
  it's a navigation/settings **door**, not a destructive action, so per the intent-color law it reads
  **neutral** (NOT red — red is reserved for stop/cancel/destructive). The destructive power-*off*
  lives *inside*, behind the existing full-screen Confirm guard, not on this button.
- **D-10: No dedicated System-page screen this phase — the "System" foot button opens the existing
  App Drawer.** The App Drawer already hosts the device/system entries (Printers/Devices, Theme,
  Settings, About, System Info, Power), so it serves as the **interim System hub**. SC-5 ("System page
  reachable, hosts Power + device-settings entry points") is satisfied via the drawer for now. Building
  the real dedicated System hub page + wiring Power is **deferred** to its natural home, Phase 28
  (System/Settings cluster).
- **D-11: Power stays unwired.** Today's "Power" is a greyed, deliberately-inert "coming soon"
  host-power placeholder (`AppDrawer.kt:258`, T-04-07-E) — there is **no** `machine/device_power`
  integration or power-control UI. Phase 24 does NOT wire it; the Power entry remains as-is inside the
  drawer. (New Moonraker device-power surface is its own focused effort, Phase 28-adjacent.)
- **D-12: App Drawer stays LIVE, as-is.** Because the "System" foot button opens it (D-10), the drawer
  is **not** dev-gated and **not** trimmed this phase. It does double duty as the interim System hub
  *and* the testing affordance (SC-2's "App Drawer may remain"). Its now-redundant printer-action tiles
  (Files/Move/etc., handled by the waterfall) stay harmlessly for now.

### Morph + e-stop
- **D-13: State morph = cheap one-shot cross-fade (~150ms), flox-verified.** Idle↔printing↔terminal
  transitions use a short one-shot content cross-fade (the UI law permits "one-shot transitions if
  cheap"; transitions are rare — only on print start/end). **No** continuous/breathing animation
  (Adreno-320 budget). Verify the transition holds frame budget on flox; fall back to hard-cut if it
  janks.
- **D-14: Floating e-stop = reuse locked behavior.** Red (`--stop`), shown on **every** screen **only
  when printing**, top-left of the Focus over the preview corner (decoupled from layout flow). Tap →
  the existing full-screen Stop **Confirm guard** (per the UI law — not a hold gesture, not a dialog).
  Locked in sketch-findings; carried forward unchanged, no new decision needed.

### Claude's Discretion
- Exact `NavHost` route shape, holder lifetime/hoisting across the graph (the ~20 session holders
  currently built in `AppShell` and re-keyed on spine rebuild must survive nav-destination changes —
  hoist them above the `NavHost`), back-stack semantics at root (Back at root falls through to OS /
  closes app, as today), and how the connection-tier hard-overrides coexist with the `NavHost` (gate
  above vs redirect/start-destination switch) — all left to research + planning, grounded in the
  existing `AppShell.kt` / `RootController` patterns.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### jiib redesign LAW (the design contract — read first)
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — the redesign design-direction index
  (auto-load before building any redesigned UI).
- `.claude/skills/sketch-findings-dinghy-display/references/layout-navigation.md` — **the morphing
  waterfall home**: Focus-as-card, state→content map, foot-of-list, floating e-stop, split ratios,
  list edge-fade affordance. The primary spec for this phase.
- `.planning/notes/2026-06-09-jiib-redesign-direction.md` — the five schisms, the locked one-morphing-
  surface model, "where the gutter's jobs went" (nav→waterfall, actions→foot, Stop→floating,
  Power→System page), the unit `U`, Field-takeover picker, oklch caution-reads-red hazard. **Note this
  phase's D-09 deviation:** the foot button is "System", not "Power".
- `.planning/notes/2026-06-09-component-classes-catalog.md` — the component-class catalog companion.
- `docs/ui_design/LAYOUT.md` — the Phase-23-rewritten two-region Focus/Field grammar (gutter removed),
  unit `U`, foot-of-list, floating e-stop overlay spec.
- `docs/ui_design/COMPONENTS.md` — the Phase-23 component-class catalog (ListRow / FootButtonBar /
  FillMeter / etc.) this phase consumes.
- `docs/ui_design/CLAUDE.md` + `docs/ui_design/THEMING.md` — design non-negotiables, intent colors
  (relevant to D-09 neutral "System" button), `--fs` S/M/L. **HARD RULE: never invent/choose icons —
  ask the owner.**

### Code being reworked (the nav skeleton)
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — the ~1000-line `when(dest)` holder +
  ~20 session holders + overlays being migrated to a `NavHost` (the heart of this phase).
- `app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt` — `Dest` enum + `TopRoute.derive` (the
  connection-tier logic reshaped per D-02/D-03).
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — the drawer kept as interim System hub
  (D-10/D-12); `AppDrawer.kt:258` is the inert Power tile (D-11).
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt` — the screen becoming the
  morphing root (already adapts on `printState` per D-06/16-06; the idle Standby launcher tiles become
  the data-driven idle action LIST).
- `MainActivity.kt` / `RootController` — owns the Splash/Connect/Shell switch + the perceptibility
  latch (the connection-tier host).

### Stack note
- `gradle/libs.versions.toml` — **Navigation-Compose is NOT yet a dependency** (deferred per CLAUDE.md
  until this phase). Add `androidx.navigation:navigation-compose` ~2.8.x (minSdk-23-safe per the stack
  table). Confirm pin against the current Compose BOM.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase-23 component kit** (`UnitGrid`/`rememberUnitGrid`, `ListBlock` with edge-fade, `ListRow`,
  `FootButtonBar`, `FillMeter`, `DetailCard`, intent-colored `OutlinedControl`) — the idle action list,
  foot bars, and morphing surfaces are built from these, not bespoke layout.
- **`PrintStatusScreen` already morphs on `printState`** (D-06/16-06: Standby / printing / Terminal(Error)
  states exist, plus the bounded ≤3 error-line projection). This phase reshapes that into the
  Focus-as-card + data-driven Field list, it does not invent state-handling from scratch.
- **`ShellNavState`** (hoisted nav holder above the Splash/Shell switch, 13-05 Task 2) — its job
  (preserve nav across a recovery Splash) must be preserved through the Nav-Compose migration; the
  reason it exists (a transient recovery Splash must not reset the user to Home) is load-bearing.
- **App Drawer capability gating** (`webcamEnabled`/`spoolEnabled`/`outputsEnabled` collected in
  AppShell) — the same capability signals drive the idle-list HIDE rule (D-08).

### Established Patterns
- **Session-holder hoisting + spine re-key:** ~20 holders are `remember(store)`-keyed and re-key on
  spine rebuild (reconnect); several have `DisposableEffect { onDispose { holder.cancel() } }`
  leak-cancels. The `NavHost` must sit BELOW these holders (hoisted above the graph) so a nav-destination
  change does not tear down/rebuild session state — preserve the re-key + leak-cancel discipline.
- **Local sub-nav via BackHandler priority** (Calibration/FineTune/Outputs/Macros register `BackHandler`s
  in registration order = priority). These STAY in-screen (D-01) — do not migrate to nav routes.
- **Overlays float outside `when(dest)`** (MacroExecutionPopup, ScanSurface, PromptDialog, DevThemeCycler)
  — they must continue to float over ANY destination after the `NavHost` migration.
- **Swipe-up drawer gesture** is suppressed on scrollable/list screens (Files/Console/Macros/Calibration/
  Webcam/Spool/Outputs/SystemInfo/Devices/Theme/Settings/About) to avoid fighting list scroll. Preserve
  this suppression behavior for the drawer-as-interim-hub.

### Integration Points
- **`TopRoute.derive` (pure)** → the connection-tier waterfall (D-02). Keep it pure/host-testable; the
  perceptibility latch stays a `RootController` UI concern.
- **`PrinterState.printState`** drives the morph + the D-04 pop-to-root-on-foot-gun logic.
- **Capability flows** (`spoolmanPresent`/`outputsPresent`/`webcamTileEnabled`, macro `bookmarks`) →
  idle-list membership (D-08).

</code_context>

<specifics>
## Specific Ideas

- **v1 idle order (verbatim owner):** `Spool → File → Move → Extrude → Macros (if bookmarks exist) →
  Calibration → Outputs → Webcam`.
- **"System" not "Power"** for the third idle foot button (neutral intent), opening the existing drawer.
- **"At this stage it can just go to the current drawer"** — the owner's explicit minimal-scope steer
  for the System page: don't build a new hub screen yet.
- Morph transition: a **subtle** one-shot cross-fade, not instant, not animated/looping.

</specifics>

<deferred>
## Deferred Ideas

- **v2: user-customizable home action list.** Add/remove/sort the idle list, mixing destination pages
  AND **direct inline controls** (extruder, bed, macros, outputs as inline mini-controls inside a row).
  Phase 24 only builds the data-driven typed list MODEL to make this additive (D-05); the editor UI +
  the `InlineControlAction` row variant are the v2 feature. → future milestone.
- **Dedicated System hub page + Power wiring.** Build the real System page screen (power-device
  controls via Moonraker `machine/device_power` + the rehomed Printers/Theme/Settings/SystemInfo/About
  entries, restyled) → Phase 28 (System/Settings cluster). Interim: the App Drawer (D-10/D-11).
- **Trim/retire the App Drawer's printer-action tiles.** Once the waterfall fully covers real use and a
  dedicated System page exists, the drawer's redundant printer tiles can be removed and the drawer
  fully retired/dev-gated. Not this phase (D-12).

### Reviewed Todos (not folded)
- `todo.match-phase` returned generic UI-keyword matches (Phase-11 spool hardening, bookmarked-macros
  density, dev-overlay drag, 3-cell increment-picker). None is about the navigation spine — they belong
  to their own screens/phases (the macro-density + increment-picker ones land in phases 25/26). Not
  folded.

</deferred>

---

*Phase: 24-navigation-spine*
*Context gathered: 2026-06-09*
