# Phase 28: System / Settings Cluster - Context

**Gathered:** 2026-06-12
**Status:** Ready for planning

<domain>
## Phase Boundary

Restyle the System-cluster screens — `SettingsScreen`, `ThemeScreen`/`ThemeEditorScreen`,
`PrintersScreen`, `SystemInformationScreen`, `AboutScreen` — onto the jiib redesign, **C6-EXEMPT**
(denser close-interaction surfaces, held in hand; keyboard allowed; the ≥64px floor intentionally
NOT applied). The phase ALSO completes the navigation story the P24 spine left interim: a **real
System page** replaces the drawer-as-System-hub, and the **swipe-up gesture + AppDrawer are
deleted entirely** (the folded retire-swipe-up todo). Orphaned drawer-only destinations
(Temperature, Console, Fine-Tune) rehome onto the WaterfallHome idle list; the printing shortcut
grid gains a System entry for mid-print access.

**This is a UX migration phase** plus the folded theme-editor functional fixes (S/V color picking,
dead-plumbing deletion, idle seedTheme reactivity, dev-cycler drag). Connection editing, theme
apply, printer add/remove/switch, and sysinfo reads must survive functionally intact (SC-5); host
tests green + on-device smoke.

**Out of scope:** real power control (host shutdown/reboot, Moonraker power devices — the Power
entry stays an inert stub); reconnect/process-death robustness and ship items (Phase 29); any new
printer capability; print-control surfaces (they keep the ≥64px floor).

</domain>

<decisions>
## Implementation Decisions

### System page & nav retirement
- **D-01: Real System page.** A new jiib-style NavDest route carries the system cluster. The
  System foot button on WaterfallHome opens it; the interim "System foot opens the App Drawer"
  wiring (P24 D-09/D-10 `HomeAction.OpenDrawer`) is retired.
- **D-02: Direct-tap rows + static info Focus.** System page rows navigate on tap directly (no
  select-then-open step). The Focus is a STATIC strip showing **app identity** (jiib lockup +
  version + active printer name — brand-header posture, not live data), capped at **20% of HEIGHT
  in portrait and 40% of WIDTH in landscape** (owner-specified ratios).
- **D-03: Entry order = Printer-first:** Printers → Settings → Theme → System Info → About →
  Power(stub). Connection management leads since everything else is per-printer.
- **D-04: Swipe-up gesture + AppDrawer die entirely.** Delete the gesture, `SwipeUpAccumulator`,
  `AppDrawer`, and every per-screen drawer-swipe suppression carve-out (e.g. the Files-list
  carve-out). Closes the `2026-06-11-retire-swipe-up-nav.md` todo.
- **D-05: Orphans rehome to the home idle list.** Temperature, Console, and Fine-Tune become rows
  on the WaterfallHome idle list (REVISES P24 D-07 "printing-only" posture). The list scrolls;
  ~11 rows max is fine. Icon law applies — if any of the three needs a glyph not already
  registered, STOP and ASK (LauncherTemp/Console/FineTune glyph choices are owner calls).
- **D-06: Mid-print System access = printing shortcut grid.** The PrintStatus printing-mode
  shortcut grid (which already carries Tune) gains a System entry — same destination, no new
  grammar. Theme/Settings changes mid-print are legitimate.
- **D-07: Preheat foot button survives** on WaterfallHome as the fast path; the Temperature list
  row is the full surface.
- **D-08: Power stays an inert stub** on the System page — red/danger styling, greyed/inert, the
  same visual-reminder posture as the greyed calibration routines. Real power control is deferred
  to its own phase.

### Densification grammar (C6)
- **D-09: Dense jiib ListRow variant** as a reusable kit component — same translucent-outline
  list language, leading icon, but compact sub-1U rows and tighter spacing. One visual family
  app-wide; config screens just run denser. No conventional-Android second visual family.
- **D-10: The 15sp text floor HOLDS.** Density comes from tighter rows/spacing, NOT smaller type;
  the `fsSp` S/M/L scale stays intact. (Recurring lesson: fonts get picked too small — don't.)
  The old EX(set) 13sp Material-label exemption dies with the restyle.
- **D-11: Fit-on-one-page is a hard goal for Settings + About only** (at M text size). Theme
  editor, Printers, and SysInfo densify but scroll freely.
- **D-12: Keyboard fields = densified TokenTextField inline.** System keyboard directly on the
  C6 surfaces (no Field-takeover ceremony); numeric fields keep the numeric keyboard.

### Printers interaction model
- **D-13: R4 Edit/Delete = FootButtonBar mode toggles.** Edit (neutral intent) + Delete
  (stop/red intent) toggle buttons on the foot bar. Arming a mode highlights the toggle; printer
  rows then respond per mode — Edit opens the connection editor, Delete prompts the ConfirmGuard.
  Tapping the toggle again (or Back) disarms. Normal mode: row tap switches the active printer.
  Closes `2026-06-05-printers-edit-delete-mode-buttons.md` (R4).
- **D-14: The 15.2 Settings-IA boundary is FINAL.** Settings = per-printer feature toggles;
  Printers = connection/add/remove/switch; Theme = look; About = app-global + dev-enable. Nothing
  moves. This closes the Settings-vs-Devices boundary question for good.
- **D-15: Printers layout.** Focus = the ACTIVE printer's card (name, host:port, color-coded
  connection state, Klippy state). Field = profile rows (active marked; tap switches). Foot =
  Add (accent) + Edit (neutral) + Delete (stop) + Back. The add/edit connection form stays an
  inline densified form, restyled.

### Theme editor scope
- **D-16: WR-02 fix = hue wheel + 2D saturation/value square.** Full color freedom for pool/status
  custom colors; honors the D-03 (P15) "the color is yours" promise. Persisted colors carry S/V,
  re-open restores them.
- **D-17: WR-01 fix = DELETE the dead maxItems plumbing** (`setMaxItems` + the persist/sanitize/
  resolve axis). The 4-slot pool grid is the designed shape; re-add later only if a real need
  appears.
- **D-18: Editor layout = one dense scroll.** Single scrolling page densified toward 1-2
  screenfuls; sub-pickers stay inline expansions. No sectioned sub-pages. `ThemeScreen` remains
  the thin wrapper hosting the editor (promotion-not-fork contract intact).
- **D-19: Phase-14 WR-02 seedTheme fix rides along** — the no-active-profile branch of
  `AppContainer.seedTheme` collects the global theme flow reactively (mirror the active branch's
  `flatMapLatest`) instead of the one-shot `firstOrNull()`.
- **D-20: Dev cycler overlay drag restored** — give the panel its own bare-surface drag region so
  it drag-relocates again (chip taps keep consuming their events). Dev-only tool; mechanical fix.

### Claude's Discretion
- Exact dense-row heights/spacing, section grouping, toggle/dropdown control shapes — owner
  judges at UAT.
- SystemInformationScreen + AboutScreen composition (content selection, densification shape) —
  restyle on the dense kit; no content decisions were mandated beyond D-11.
- The System page's NavDest shape, back-stack behavior, and how the FloatingEStop/UAT-4 corner
  reservation applies on the new page — grounded in the P24 spine patterns.
- How the home idle list integrates the three new rows (ordering within the D-06 P24 order,
  capability gating if any) — propose, owner judges at UAT. Glyphs need owner sign-off (D-05).
- S/V square implementation details (size, inline vs expanding) within the one-dense-scroll rule.
- Whether `WR-03` webcam null-key (same phase-14 todo) is touched — it is NOT in scope (webcam,
  not settings); leave for Phase 29 unless trivially adjacent.

### Folded Todos
- `2026-06-11-retire-swipe-up-nav.md` — swipe-up drawer nav retired entirely (D-04).
- `2026-06-05-settings-densify-one-page-restyle.md` (C6) — densify grammar D-09..D-12 (was
  already locked by roadmap SC-3).
- `2026-06-05-printers-edit-delete-mode-buttons.md` (R4) — D-13 (was already locked by SC-3).
- `2026-06-05-phase-15.1-review-deferred-findings.md` — WR-01 → D-17 (delete), WR-02 → D-16
  (S/V square). The info items ride the rebuild where trivially adjacent.
- `2026-06-05-phase-14-review-deferred-wr02-wr03.md` — WR-02 idle seedTheme → D-19. WR-03
  (webcam null-key) explicitly NOT folded — stays pending for Phase 29.
- `2026-06-05-dev-overlay-panel-drag.md` — D-20 drag-relocate restore.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### jiib redesign LAW (read first)
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — design direction incl. Post-Phase-26
  UAT rules UAT-1..UAT-5 (auto-load before building any redesigned UI).
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit grid `U`,
  content/control fill, intent colors, icon registry, Focus/Field-no-gutter.
- `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md` — list grammar
  the dense variant derives from.
- `docs/ui_design/LAYOUT.md` — two-region grammar, unit `U`, §"Post-Phase-26 UAT formatting
  rules", C6 exemption definition.
- `docs/ui_design/THEMING.md` — token system, intent-by-safety, C6 exemption.
- `docs/ui_design/COMPONENTS.md` — Phase-23 kit (`ListRow`/`ListBlock`/`FootButtonBar`/
  `OutlinedControl`/`DetailCard`); the dense ListRow variant (D-09) extends this kit.
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — `@Preview` 6-combo + fs=L matrix, `stringResource`,
  `DinghyIcons` registration (applies to every rebuilt screen + the new System page).
- `docs/ui_design/CLAUDE.md` — icon never-auto-pick law (D-05 glyphs need owner sign-off).

### Prior phase contracts that bind this phase
- `.planning/phases/24-navigation-spine/24-01-PLAN.md` — NavDest model, `HomeAction`/
  `buildIdleActions` (D-05 revises its D-07 posture; D-01 retires its OpenDrawer interim hub).
- `.planning/phases/27-motion-calibration/27-CONTEXT.md` — the list-hub precedent (its D-05) the
  System page deliberately DIVERGES from (direct-tap, D-02), plus D-15/D-16/D-17 conformance and
  icon-law language this phase inherits.
- `docs/adr/0001-ui-toolkit-decision.md` + Addendum-2 — no-frozen-frames budget on flox.

### Folded todos (full problem statements)
- `.planning/todos/pending/2026-06-11-retire-swipe-up-nav.md`
- `.planning/todos/pending/2026-06-05-settings-densify-one-page-restyle.md`
- `.planning/todos/pending/2026-06-05-printers-edit-delete-mode-buttons.md`
- `.planning/todos/pending/2026-06-05-phase-15.1-review-deferred-findings.md`
- `.planning/todos/pending/2026-06-05-phase-14-review-deferred-wr02-wr03.md`
- `.planning/todos/pending/2026-06-05-dev-overlay-panel-drag.md`

### Code being migrated / retired (current implementations)
- `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` (432 ln) — per-printer
  feature toggles; densify to one page (D-11).
- `app/src/main/java/works/mees/dinghy/ui/screen/ThemeScreen.kt` (45 ln, thin wrapper) +
  `ThemeEditorScreen.kt` (765 ln) — the editor rebuild (D-16/D-17/D-18).
- `app/src/main/java/works/mees/dinghy/ui/screen/PrintersScreen.kt` (673 ln) — D-13/D-15.
- `app/src/main/java/works/mees/dinghy/ui/systeminfo/SystemInformationScreen.kt` (363 ln) —
  dense restyle, scrolls freely.
- `app/src/main/java/works/mees/dinghy/ui/screen/AboutScreen.kt` (265 ln) — densify to one page;
  keeps dev-enable toggle.
- `app/src/main/java/works/mees/dinghy/ui/screen/TokenTextField.kt` — densified inline field (D-12).
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — DELETED (D-04); its
  `DRAWER_TILES` registry is the checklist of destinations that must remain reachable.
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` + `SwipeUpAccumulator.kt` — gesture
  + drawer wiring removal (D-04); drawer-state/suppression plumbing dies.
- `app/src/main/java/works/mees/dinghy/ui/route/HomeAction.kt` — `OpenDrawer` retired → System
  page destination; `buildIdleActions` gains Temperature/Console/Fine-Tune rows (D-05).
- `app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusField.kt` — System foot retarget
  (D-01) + printing shortcut-grid System entry (D-06).
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — `seedTheme` no-active branch (D-19).
- `app/src/main/java/works/mees/dinghy/ui/shell/DevThemeCyclerOverlay.kt` — drag restore (D-20).
- `app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt` / `TokenBridge.kt` /
  `ThemeResolver.kt` — maxItems axis deletion (D-17) + S/V persistence (D-16).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase-23 kit** (`ListRow`, `ListBlock`, `FootButtonBar`, `OutlinedControl`, `DetailCard`,
  `UnitGrid`, `ScreenScaffold`) — the dense ListRow variant (D-09) extends this kit; foot bars
  carry the Printers mode toggles and System-page Back.
- **`DRAWER_TILES` spec list** (`AppDrawer.kt`) — the authoritative inventory of destinations +
  owner-locked glyphs; reuse its icon tokens (e.g. `SysInfoTile`, `OutputSection`) for System-page
  and home-list rows where they already exist.
- **`buildIdleActions`** — pure, tested builder; extends naturally for the three new rows (D-05).
- **`brandTint` + jiib lockup drawables** (18.2) — the app-identity Focus strip (D-02) reuses the
  About/Splash brand assets.
- **`ConfirmGuard`** — Printers Delete mode (D-13) reuses the existing destructive-action guard.
- **`TokenTextField`** — already token-themed; needs the dense-height variant only.

### Established Patterns
- **C6 exemption is already LAW** (THEMING.md/LAYOUT.md) — this phase implements it, not defines it.
- **AppShell suppresses swipe + supplies explicit Back for FFG-exempt screens** — once the gesture
  dies (D-04), the suppression plumbing simplifies to nothing; Back affordances must remain.
- **Per-printer theme/settings writes route through process-lifetime writeScope intent methods**
  ([[dinghy-compose-write-scope-cancellation]]) — the rebuilt editor and toggles must keep this;
  never `rememberCoroutineScope()` for persistence.
- **Preview-first/tokenized-first** (PREVIEW_AND_TOKENS.md) — every rebuilt screen + the new
  System page ships the `@Preview` matrix, `stringResource` strings, registry icons day one.
- **Icon never-auto-pick** — any new glyph (System-page rows, home-list Temperature/Console/
  Fine-Tune launchers if unregistered) is an owner decision; STOP and ASK.

### Integration Points
- **Nav spine (P24 NavHost)** — new `NavDest.System` route; System foot + printing shortcut grid
  both navigate to it; pop-to-root foot-gun wiring must not regress; back-stack from System →
  sub-screens → Back pops sanely.
- **`FOOT_GUN_DESTS`/`shouldPopToRoot`** — decide whether System cluster screens pop to root on
  print start (current Settings et al. behavior carries over unless owner says otherwise).
- **Capability/live gating** — drawer tiles carried live-vs-greyed logic (webcamEnabled,
  outputsEnabled, spool swatches, Devices active-name subtitle); whatever the home list + System
  page need of this moves with them before AppDrawer deletion.
- **FloatingEStop overlay (UAT-4)** — corner reservation applies to the System page and rebuilt
  screens when reachable while printing (D-06 makes the whole cluster print-reachable).
- **Host test surface** — `HomeActionTest`/nav tests cover `buildIdleActions` and routes; drawer
  tests (`visibleDrawerTiles` etc.) get deleted/migrated with the drawer.

</code_context>

<specifics>
## Specific Ideas

- **Focus ratio caps (verbatim owner steer):** System page info Focus "limit the focus to 20%
  height in portrait and 40% width in landscape."
- **App-identity Focus** — brand-header posture (jiib lockup + version + active printer name),
  NOT live telemetry.
- **Printer-first System order:** Printers → Settings → Theme → System Info → About → Power(stub).
- The owner explicitly rejected implementing power actions this phase ("carry as inert stub").

</specifics>

<deferred>
## Deferred Ideas

- **Real power control** (host shutdown/reboot via `machine.*`, Moonraker power devices behind
  ConfirmGuard) — its own phase; `Capabilities` already detects `device_power`.
- **Calibration hub hide-not-grey flip** (carried from P27 D-06) — still future.

### Reviewed Todos (not folded)
- `2026-06-05-bookmarked-macros-density.md` — Macros surface (P25 territory); not settings scope.
- `2026-06-10-webcam-aspect-ratio-overlay-back.md` — Webcam (P25 surface); not this phase.
- `2026-06-10-fw-retraction-glyph-assignment.md` — Fine-Tune rows; not settings scope.
- `2026-06-04-phase-11-spool-feature-robustness-hardening.md` — Spool robustness; Phase 29.
- `2026-06-08-phase-22-arm64-abi-ship-requirement.md` — ship requirement; Phase 29.
- `macrobenchmark-module-wiring.md` — perf tooling; not this phase.
- Phase-14 WR-03 (webcam null-key, same todo file as the folded WR-02) — webcam edge, Phase 29.

</deferred>

---

*Phase: 28-system-settings-cluster*
*Context gathered: 2026-06-12*
