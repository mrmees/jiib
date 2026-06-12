# Phase 14: Multi-Printer Switching - Context

**Gathered:** 2026-06-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Generalize the single persisted `ConnectionConfig` into a **managed set of named printer
profiles** plus a **persisted active-profile selection**, so a user with more than one Klipper
machine (the dev's Ender 5 Plus + Ender 3 Pro) registers each printer once and switches the
active connection without re-entering host/port/key. Switching cleanly tears down the current
spine and rebinds the foreground service to the selected profile; capability detection, the
command registry, and every screen follow the active printer with **no stale state** from the
prior one. An active-printer indicator + switcher is reachable from the shell and the choice
survives restart/process-death.

**Key architectural fact:** the switching machinery **already exists** —
`MoonrakerService.runConfigLoop` collects `ConnectionStore.config` via `collectLatest` and on
every emission `cancelAndJoin()`s the prior session before building the new spine (D-03, clean
teardown seam). Phase 14's job is the **profile data model + active-selection routing + Settings
CRUD + a switcher surface + per-printer theming** — NOT new protocol. Switching = changing which
`ConnectionConfig` the active flow emits; the existing seam does the rebind for free.

**In scope:** profile set (CRUD), active selection, switcher screen (the greyed "Devices" tile),
per-printer full theme, clean rebind on switch, delete/empty-state routing.
**Out of scope:** new per-printer surfaces beyond switching (fine-tune, output controls, webcam,
system info — own phases 15-18); profile import/export; cloud/remote profiles.
</domain>

<decisions>
## Implementation Decisions

### Switcher UX & Entry Point
- **D-01:** Wire the existing greyed **"Devices"** drawer tile (`cable` icon, currently
  `dest = null`) LIVE → opens a new **full-screen Field-of-printers list** (a new `Dest.Devices`
  route). One tile per saved profile (name + `host:port`, active one highlighted) following the
  App-Drawer tile grammar, plus an **"Add printer"** tile that jumps to Settings. Scales to N
  printers and leaves room for per-printer status. **No hi-fi mockup exists** for this screen —
  it is NOT locked by a mockup; it must follow LAYOUT.md/THEMING.md grammar. `UI hint: yes` →
  consider `/gsd-ui-phase 14` for this surface.
- **D-02:** Switching is **instant rebind through the Splash**. Tap a printer → immediately
  persist the new active profile → the existing `collectLatest` seam tears down + rebinds →
  RootController's normal recovery Splash covers the reconnect → lands on the new printer's
  Status. **No confirm guard** (switching is non-destructive). Reuses all existing routing.
- **D-03:** The **active-printer name shows on the Devices drawer tile** (e.g. label/subtitle
  "Devices — Ender 5 Plus"). Indicator + switcher entry in one place; no new persistent chrome
  (design system has no persistent status bar — status is color-on-an-element). The list tile for
  the active printer is also highlighted.
- **D-04:** Switching **mid-print just switches** — the old printer keeps printing untouched
  (Moonraker is server-side; switching only changes which printer the TABLET watches). No warning
  needed; you can switch back anytime and the print is still there.

### Profile Data Model & Migration
- **D-05:** Each profile has a **generated stable UUID** as its identity key. `name`/`host`/
  `port`/`apiKey` can all change without breaking the active-profile pointer or per-printer prefs.
  Two profiles MAY share a host (same box, different port/key). Profile carries: `id` (UUID),
  optional `name`, `host`, `port`, `apiKey?`, plus per-printer theme (D-08).
- **D-06:** Per-printer prefs **re-key on the profile-id** (not host). Applies to the existing
  host-keyed preferred-webcam pref (`preferred_cam_<host>` → profile-id keyed) and any future
  per-printer settings. Survives host edits; keeps two same-host profiles distinct. NOTE: this
  re-keys an existing pref — but per D-07 there is **no data migration** (fresh start), so the old
  `preferred_cam_<host>` simply stops being read.
- **D-07:** **No migration — fresh start.** On upgrade the new profile store starts empty; the
  user re-adds their printers (acceptable: single dev, on-device, two printers). The old single
  `ConnectionStore` host/port/key keys are simply not read by the new profile model. First run →
  0 profiles → Connect prompt (D-11). Removes all migration code from scope.
- **D-08:** **Each profile carries a FULL theme** — its own base (dark/light) + accent +
  text-size — overriding the global default. Switch printers → the whole app look changes (the
  strongest "which printer am I driving" signal). Reuses the existing `ThemeResolver` /
  `TokenDelta` substrate (already supports full custom themes). ⚠ This rewires theme **seeding**:
  from "one global `ThemePrefs`" to "**active profile's theme**, re-seeded into `ThemeResolver`
  on every switch." Treat as a first-class workstream (net-new vs roadmap's "STANDARD" note).
- **D-09:** Theme is edited via the **existing Settings "Appearance" section, which now acts on
  the ACTIVE printer's theme** (no new screen). The current dark/light + text-size + accent
  controls are reused 1:1; only their write target changes (active profile's theme vs the lone
  global `ThemePrefs`).
- **D-10:** Profile **name is optional, defaults to its host** (or `host:port`) for display.
  User can rename to "Ender 5 Plus" etc. No forced keyboard entry to add a printer.

### Active-State & Delete Behavior
- **D-11:** **0 profiles → existing Connect prompt → Settings** (reuse D-11/`hasConfig`-false
  routing from earlier phases). Drives off "no active profile" instead of "no config." Same
  first-run flow that exists today; nothing new to design for the empty state.
- **D-12:** Deleting the **active** profile (with others still saved) → **auto-select another
  remaining profile as active + rebind** (Splash → its Status). Never leaves a dead no-active
  state while valid printers exist. Deleting the **last** profile → falls to the Connect prompt
  (D-11).

### Settings CRUD UX
- **D-13:** Settings "Connection" section becomes a **list of saved profiles** (name /
  `host:port`, active marked), each tappable to **edit**, plus an **"Add printer"** entry that
  opens the **existing host/port/key + mDNS-scan form** (reused 1:1). Editing the active profile
  while connected re-saves → the seam live-rebinds (D-02 path). Appearance section below edits the
  active printer's theme (D-09).
- **D-14:** **Deleting a profile is behind the full-screen Confirm guard** (the established
  destructive-action pattern, `08-confirm.png`) — deleting a printer loses its config + theme, so
  it is destructive. Consistent with how the app already guards irreversible actions.

### Claude's Discretion
- Exact UUID generation scheme, the new profile-store persistence shape (single
  `profiles.preferences_pb` JSON blob vs Proto vs a per-profile key scheme), and how the
  active-profile-id flow composes with the profiles flow to produce the active `ConnectionConfig`
  the service consumes — left to research/planning. The contract: the service keeps consuming a
  `Flow<ConnectionConfig?>` (the active profile's connection) so its rebuild loop is untouched.
- Whether the Devices list shows live per-printer status (connected/last-seen) in v1 or just
  name/address — planner's call based on cost (the spine only knows the ACTIVE printer's state).
- Theme-switch timing/flicker handling during the rebind Splash — planner's call.
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The rebind seam (the heart of this phase — reuse, don't reinvent)
- `app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt` — `runConfigLoop` /
  `buildSpineAndLaunch`: the config-driven clean teardown + atomic `SpineHandle` republish (D-03).
  Switching feeds a new `ConnectionConfig` here; do NOT add a parallel rebind path.
- `app/src/main/java/works/mees/dinghy/config/ConnectionStore.kt` — the current single-config
  DataStore(Preferences) store + pure `sanitize` validation. The profile store generalizes this.
- `app/src/main/java/works/mees/dinghy/config/ConnectionConfig.kt` — the immutable connection
  value (host/port/apiKey, redacting `toString`). A profile wraps one of these + id/name/theme.
- `app/src/main/java/works/mees/dinghy/di/AppContainer.kt` — process-scoped service-locator:
  owns `connectionStore`, `themePrefs`, `themeResolver`, publishes the spine, derives `hasConfig`.
  The active-profile-id selection + active-`ConnectionConfig` derivation lives here.
- `app/src/main/java/works/mees/dinghy/di/SpineHandle.kt` — the immutable per-session snapshot
  (atomic republish on rebuild). Unchanged by Phase 14, but explains the no-stale-state guarantee.

### Routing & shell integration
- `app/src/main/java/works/mees/dinghy/ui/shell/RootController.kt` — the SINGLE routing authority
  (Splash vs Shell vs Connect/Settings). Drives the empty-state (D-11) and the Splash-on-switch
  (D-02). `hasConfig` here becomes "has an active profile."
- `app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt` — the `DRAWER_TILES` set incl. the
  greyed `Devices` tile (`cable`, `dest = null`) to wire LIVE (D-01), the runtime-greying pattern
  (webcam/spool precedent) for showing the active-printer name, and the icon-no-repeat law.
- `app/src/main/java/works/mees/dinghy/ui/route/*.kt` — `Dest` enum (add `Devices`) + `TopRoute`
  (`Connect`/`Splash`/`Shell`) the root controller derives.

### Settings + theming
- `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` — the connection form
  (host/port/key + mDNS scan + Save) + the Appearance section (dark/light, S/M/L, accent picker)
  to restructure for profiles (D-13) and re-point at the active profile's theme (D-09).
- Theme substrate to reuse for per-printer themes (D-08): `ThemeResolver`, `ThemePrefs`,
  `TokenDelta` under `app/src/main/java/works/mees/dinghy/theme/`. `AppContainer.seedTheme` is the
  seeding path to rewire (global → active-profile theme).
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamPrefs.kt` (host-keyed `preferred_cam_<host>`)
  — the per-printer pref to re-key on profile-id (D-06).

### UI design law (the switcher screen has no mockup — follow the grammar)
- `docs/ui_design/LAYOUT.md` — Focus/Field/Gutter grammar for the new Devices list screen.
- `docs/ui_design/THEMING.md` — semantic tokens, button intents, `--fs`; the per-printer theme
  data all routes through role tokens (THEME-01, never raw colors).
- `docs/ui_design/images/02-app-drawer.png` (tile grammar to mirror for the Devices list),
  `docs/ui_design/images/08-confirm.png` (the delete Confirm guard, D-14).
- `docs/ui_design/CLAUDE.md` — design non-negotiables (static glow, no breathing animation, ≥64px
  targets, color-as-status).

### Phase dependency
- `.planning/phases/13-optimization-network-efficiency-end-to-end-reliability/` — the
  connection/state spine + reconnect/Splash refinements this phase builds on.
- `docs/adr/0001-ui-toolkit-decision.md` — the Compose+Views hybrid the new screen lives in.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **`MoonrakerService.runConfigLoop`** — the clean teardown+rebind already exists; switching just
  feeds it a different `ConnectionConfig`. The single most important reuse — DO NOT build a second
  rebind path.
- **`ConnectionStore` shape** (injected DataStore, fail-safe read flow, suspend writers, pure
  `sanitize`) — the template the profile store copies/generalizes.
- **Settings connection form + mDNS scan + `OutlinedControl`/`TokenTextField`** — reused 1:1 as
  the per-profile add/edit form (D-13).
- **Appearance controls + `ThemeResolver`/`TokenDelta`** — reused for per-printer themes; only the
  write target + seeding source change (D-08/D-09).
- **AppDrawer runtime-greying pattern** (webcam/spool tiles flip live/greyed on a runtime flag) —
  the model for the Devices tile showing the active printer's name.
- **The full-screen Confirm guard** (`08-confirm.png` pattern) — reused for delete (D-14).

### Established Patterns
- **"Service constructs, UI consumes"** (D-02): the UI never opens a socket. Profile selection +
  active-config derivation belong in `AppContainer`/the store; the service reads the resulting
  `Flow<ConnectionConfig?>`.
- **Atomic spine republish** (`SpineHandle` + `publishSpine`): guarantees no stale state across a
  switch — the no-stale-state success criterion is already structurally enforced by the rebind.
- **Process-scoped, connection-independent prefs** (`themePrefs`, `macroPrefs`, `webcamPrefs`):
  per-printer theme is the FIRST pref that becomes *connection-DEPENDENT* (keyed on active
  profile) — a deliberate departure planning must handle in the seeding wiring.
- **`hasConfig`-driven routing** (D-11): the empty-state reuses this; generalize to "has active
  profile."

### Integration Points
- `AppContainer`: add the profile store + active-profile-id selection; derive the active
  `ConnectionConfig` flow the service consumes; re-derive `hasConfig`; re-seed `ThemeResolver`
  from the active profile's theme on switch.
- `MoonrakerService`: ideally UNCHANGED — keep consuming a `Flow<ConnectionConfig?>`.
- `Dest` enum + `AppDrawer` (Devices tile live + active-name) + new Devices list screen + Settings
  restructure (profile list/CRUD + Appearance→active-profile).
- `WebcamPrefs` re-key (D-06).
</code_context>

<specifics>
## Specific Ideas

- "We'll need separate app themes for different printers" (Matthew) — the per-printer FULL-theme
  decision (D-08). Each printer should look distinct so you instantly know which machine you're
  driving. E.g. Ender 5 Plus = one theme, Ender 3 Pro = another.
- Real hardware to prove against: Ender 5 Plus = `192.168.1.120:7125`, Ender 3 Pro =
  `192.168.1.121:7125` (both klicky probes). Success criterion 4 = switch between these two live
  and drive each (connect → monitor → a control action) without re-entering details.
</specifics>

<deferred>
## Deferred Ideas

- **Profile import/export / QR-share of a printer profile** — a new capability; own phase if ever.
- **Live per-printer status in the Devices list** (connected/last-seen badges for the NON-active
  printers) — would need background probing of inactive printers (the spine only knows the active
  one). Flagged as planner's-discretion for v1; full multi-printer live status is its own scope.
- **Cloud/remote (non-LAN) printer profiles** — out of project scope (local-network Moonraker).

### Reviewed Todos (not folded)
The `todo.match-phase` matches (`phase-11-spool-feature-robustness-hardening`,
`console-macro-page-ux-flow`, `macrobenchmark-module-wiring`, `status-progress-ring-dual-source-jump`,
`benchmark-harness-fairness-fixes`, `files-delete-gating-too-broad`) were keyword-fuzzy false
positives (matched generic tokens like "phase"/"app", not multi-printer scope). **None folded** —
none concern profile management or printer switching.
</deferred>

---

*Phase: 14-multi-printer-switching*
*Context gathered: 2026-06-04*
