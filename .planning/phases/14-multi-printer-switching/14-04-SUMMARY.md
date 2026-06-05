---
phase: 14-multi-printer-switching
plan: 04
subsystem: settings-ui
tags: [settings, profiles, multi-printer, crud, confirm-guard, theming, wave-2]
requires:
  - "ProfileStore.upsert (D-11 first-add-active) / delete (D-12 auto-pick) — plan 01"
  - "AppContainer.activeProfile / activeConfig / hasConfig — plan 02"
provides:
  - "Settings Connection section as a profile-list CRUD (add/edit/delete) reusing the host/port/key + mDNS form 1:1 (D-13)"
  - "First-ever add auto-actives → hasConfig true → routes into Shell via onConnectionSaved (D-11 FIX-1)"
  - "Delete affordance behind the full-screen ConfirmGuard → profileStore.delete (D-14 + D-12)"
  - "Appearance section persists to the ACTIVE profile's theme (D-09); global themePrefs retained as idle/new-profile default"
affects:
  - "plan 05 (Devices switcher) shares the same profileStore CRUD surface"
  - "plan 06 fills the on-device UAT — first-ever add → Shell; editing active accent fires NO recovery Splash"
tech-stack:
  added: []
  patterns:
    - "list-mode/form-mode toggle via a sealed EditTarget (NewProfile | EditProfile) — one form serves add + edit"
    - "state-gated full-screen ConfirmGuard overlay (FilesScreen precedent) for the destructive delete"
    - "Appearance dual-write: live themeResolver.set* unchanged + persist re-pointed to active profile (themePrefs fallback when idle)"
    - "TokenDelta.overrides.mapKeys{it.key.name} to persist deltas (no phantom toArgbMap)"
key-files:
  created: []
  modified:
    - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
decisions:
  - "Tasks 1 + 2 committed as ONE commit — they touch the same file inseparably (delete + Appearance retarget both depend on the activeProfile collection and the new form/list structure); splitting would have produced a non-compiling intermediate"
  - "Active-profile row marker = a textual 'ACTIVE' accent label (no Material glyph) — avoids an icon-no-repeat collision with the '+ Add printer' row on the same screen, and the Settings screen is a conventional list (no MaterialSymbol renderer wired here)"
metrics:
  duration: ~12min
  completed: 2026-06-05
---

# Phase 14 Plan 04: Settings Profile CRUD + Appearance Retarget Summary

Restructured `SettingsScreen.kt` so the "Connection" section is a **list of saved printer profiles**
(add/edit/delete) backed by `ProfileStore`, reusing the existing host/port/key + mDNS form 1:1, with
deletion behind the full-screen `ConfirmGuard`; and re-pointed the "Appearance" section's persist
writes from the global `themePrefs` to the **active profile's** theme while keeping the live retheme
immediate. No new control types — `OutlinedControl`/`TokenTextField`/`Intent`/`ConfirmGuard` reused
verbatim; no new dependencies.

## What Was Built (commit `9e6b583`)

### Connection → profile-list CRUD (Task 1, D-13)
- The Connection section now has **list mode** (default) and **form mode**, toggled by a sealed
  `EditTarget` (`NewProfile` for the blank add form, `EditProfile(profile)` for a pre-filled edit).
- **List mode** collects `profileStore.profiles` + `profileStore.activeId` via
  `collectAsStateWithLifecycle` and renders one `ProfileRow` per profile — name at `fsSp(20f)`
  SemiBold (Geist), `host:port` at `fsSp(17f)` Regular (Geist Mono, `t.text2`), the **active** row
  accent-outlined (`t.accentLine`) + `t.accentSoft` fill + an accent `ACTIVE` marker — plus an
  `AddPrinterRow`.
- **Form mode** reuses the host/port/key fields + the mDNS "Scan" button (6 s settle window) + the
  discovered-printer rows **byte-identical**. The only change is the Save handler:
  `ConnectionStore.save(config)` → `container.profileStore.upsert(profile)`.
  - **New** profile: `Profile(id = Profile.newId(), name = null, host, port, apiKey, themeBase =
    <current base>, fsChoice = <current fs>, themeDeltaArgb = <current accent delta>)` — inherits the
    user's current look (RESEARCH Pattern 3).
  - **Edit**: `existing.copy(host=…, port=…, apiKey=…)` — preserves the stable `id` + all theme fields.
  - **No-clobber** blank-key-on-edit retained (blank field keeps `existing.apiKey`); "Clear key"
    re-upserts `existing.copy(apiKey = null)`.
- **FIRST-ADD ROUTING (D-11 / FIX-1):** no explicit `setActive` here — `ProfileStore.upsert`
  auto-selects the new profile active when the store has no active id, so the first-ever add flips
  `hasConfig` true (plan 02) and the root controller routes into the Shell; the retained
  `onConnectionSaved()` lands the user on Print-Status, NOT a dead Connect/Settings screen.
- Client-side validation mirrors `ConnectionStore.sanitize` (blank host / port ∉ 1..65535) before
  the upsert (T-14-08).

### Delete ConfirmGuard (Task 2, D-14) + Appearance retarget (D-09)
- The edit form carries a **Cancel + Delete** row; **Delete** (red `Intent.Danger`) sets a
  `pendingDelete: Profile?` state that gates the full-screen `ConfirmGuard` (title "Delete printer?",
  message "This removes {name} and its saved theme. This can't be undone.", confirm "Delete" / cancel
  "Keep") — the FilesScreen state-gated overlay pattern. `onConfirm` →
  `scope.launch { profileStore.delete(victim.id) }`; the D-12 auto-pick (re-active another / clear if
  last) fires inside the store writer (plan 01).
- **Appearance** controls keep their LIVE `container.themeResolver.setBase/setFs/setDeltas` calls
  unchanged; the PERSIST write is re-pointed via three small helpers (`persistBase`/`persistFs`/
  `persistDeltas`): when `container.activeProfile` is non-null → `profileStore.upsert(active.copy(
  themeBase=…/fsChoice=…/themeDeltaArgb=…))`; when null (idle/first-run) → the global
  `themePrefs.setBase/setFs/setDeltas` (RETAINED as the new-profile default).
- Persisted delta map is built by `TokenDelta.overrides.mapKeys { it.key.name }` via a local
  `TokenDelta.toPersistedArgb()` extension — **NOT** a non-existent `toArgbMap()`.
- The Appearance mirror seed (`LaunchedEffect(activeProfile?.id)`) now seeds from the active
  profile's `toThemeResolved()` when present, else `themePrefs.flow.firstOrNull()`, so the screen
  opens reflecting the active printer's look and re-seeds on a switch/delete.

## Deviations from Plan

### Structural — Tasks 1 + 2 committed together
- **What:** The plan splits this into two tasks (Task 1 add/edit list, Task 2 delete guard +
  Appearance retarget), each ending in its own commit.
- **Why:** Both tasks edit the SAME file inseparably — the delete guard and the Appearance retarget
  both depend on the `activeProfile` collection and the new list/form structure introduced in Task 1.
  A Task-1-only intermediate that already collects `activeProfile` but still writes Appearance to the
  global `themePrefs` (Task 2's change) would be a contrived non-final state; splitting the single
  cohesive rewrite into two commits offered no atomicity benefit and risked a non-compiling middle.
- **Resolution:** One atomic `feat(14-04)` commit covering both tasks; both tasks' acceptance criteria
  are individually satisfied (see Verification). No scope change.

### [Auto — Rule 3] Active-row marker is a text label, not a Material glyph
- **Found during:** building `ProfileRow` (UI-SPEC calls for "an accent active-marker glyph").
- **Issue:** The Settings screen is a conventional Compose list and does not wire the `MaterialSymbol`
  ligature renderer; adding one solely for a marker glyph would risk an icon-no-repeat collision with
  the `+ Add printer` row on the same screen.
- **Resolution:** Used a textual accent `ACTIVE` label (`t.accent`, 15sp SemiBold GeistMono) as the
  at-a-glance active marker. Honors the accent-as-status law and the 15sp metadata floor without a
  glyph dependency. (The Devices switcher screen — plan 05 — is where the tile-glyph marker lives.)

## Verification

- `gw.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL** (Task 1 + Task 2 gate).
- `gw.bat :app:testReleaseUnitTest --no-daemon` — **BUILD SUCCESSFUL**, full suite GREEN (no regression).
- Source assertions (grep-verified in the committed file):
  - collects `profileStore.profiles` + `profileStore.activeId` + `container.activeProfile`; renders
    one `ProfileRow` per profile + an `AddPrinterRow`.
  - Save handler calls `container.profileStore.upsert(...)` (no `ConnectionStore.save` / `store.save`
    remains); new profiles use `Profile.newId()`; edit uses `existing.copy(...)` preserving id + theme.
  - `ConfirmGuard(title = "Delete printer?", …, onConfirm = { … profileStore.delete(victim.id) })` present.
  - Appearance persist targets `profileStore.upsert(active.copy(themeBase=…))` (+ fsChoice / delta)
    when active, else `themePrefs.setBase/setFs/setDeltas`; live `themeResolver.set*` retained.
  - persisted delta built via `overrides.mapKeys { it.key.name }`; grep shows **no** `toArgbMap`.
  - profile name uses `fsSp(20f, t.fs)` SemiBold; `host:port` uses `fsSp(17f, t.fs)` GeistMono.
  - no-clobber blank-key-on-edit branch (`if (typedKey == null && keyAlreadySaved) existing?.apiKey`)
    preserved.
- Manual (deferred to plan 06 UAT): first-ever add routes into the Shell (D-11); editing the active
  accent fires NO recovery Splash (distinctUntilChanged, plan 02).

## Known Stubs

None. The screen is fully wired to `ProfileStore`; no placeholder/empty data flows to the UI.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt` exists on disk (modified).
- Commit `9e6b583` exists in git log (verified).
- `:app:compileDebugKotlin` + `:app:testReleaseUnitTest` both BUILD SUCCESSFUL.
- No `toArgbMap` reference; no residual `ConnectionStore.save` in the Save path.
