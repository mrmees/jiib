---
phase: 14-multi-printer-switching
plan: 05
subsystem: devices-switcher-screen + drawer-wiring
tags: [devices, switcher, drawer-tile, active-name, dest-devices, status-landing, wave-3, multi-printer, D-01, D-02, D-03]
requires:
  - "14-01: ProfileStore (profiles/activeId flows + setActive writer) + Profile.displayName()"
  - "14-02: AppContainer.activeProfile (Flow<Profile?>) — the active-name + active-id source"
  - "14-03: Dest.Devices route enum + AppShell placeholder Box arm (replaced here) + the webcam re-key (NOT touched here)"
  - "14-04: Settings owns profile CRUD (the Add-printer tile + edit/delete target)"
provides:
  - "ui/screen/DevicesScreen.kt — the full-screen field-of-printers switcher (ScreenScaffold Field + green Intent.Go gutter Back); tap-to-switch persists active via setActive + signals onSwitched (D-02)"
  - "AppDrawer Devices tile LIVE (dest = Dest.Devices) + a 15sp t.text2 ellipsized active-name subtitle (the ONLY subtitled tile, D-03)"
  - "AppShell Dest.Devices host arm (DevicesScreen) + swipe-suppress + activeName collect/thread + onSwitched = navigateTo(Dest.PrintStatus) (the FIX-4 D-02 Status-landing gate)"
affects:
  - "plan 06 (wave 4): the live two-printer on-device UAT exercises the switcher — tap a Devices tile lands on PrintStatus (not Devices) after the recovery Splash; the active-name subtitle reflects the switch"
tech-stack:
  added: []
  patterns:
    - "scroll-Field screen → suppress swipe-drawer + explicit green gutter Back (FilesScreen precedent)"
    - "DrawerTile square-tile grammar reused verbatim for data-driven profile tiles (aspectRatio(1f), 12dp gaps, 16dp pad, t.rCtrl, 2dp outline, t.bg)"
    - "collect-in-AppShell → thread-as-param into AppDrawer (the webcamEnabled/spoolEnabled precedent) for activeName"
    - "switch = setActive(id) only; the runConfigLoop seam rebinds (no second rebind path, T-14-11); explicit navigateTo(Dest.PrintStatus) because ShellNavState.dest is preserved across the Splash"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
decisions:
  - "Used `dns` for the profile-tile device glyph, `bolt` for the active-marker, `add` for the Add-printer tile, `arrow_back` for the gutter Back — four distinct glyphs, none repeated on-screen (icon-no-repeat law). `cable` stays on the drawer Devices tile (off-screen, no conflict)."
  - "Active-tile emphasis (D-03) = accentSoft fill tint + accentLine outline + a bolt marker glyph in the top-end corner; inactive saved tiles keep the ordinary accentLine/surface2 live styling. All three signals so the active printer reads at a glance per UI-SPEC §Color reserved-list item 2."
  - "Profile name fsSp(20f) SemiBold Geist; host:port fsSp(17f) Regular GeistMono t.text2 — the UI-SPEC 20/17 floors. Add-printer label kept at fsSp(16f) (it has no host line, so it mirrors the drawer tile-label tier, not the 20sp printer-name tier)."
metrics:
  duration: ~12min
  completed: 2026-06-05
---

# Phase 14 Plan 05: Devices Switcher Screen + Drawer/Shell Wiring Summary

Built the one net-new Phase-14 screen — the **Devices switcher** — and made printer switching reachable
and visible. The greyed Devices drawer tile is now LIVE with the active-printer-name subtitle (D-01/D-03);
AppShell hosts `Dest.Devices` (replacing the 14-03 placeholder Box) with swipe-drawer suppression; and a
switch tap explicitly navigates to `Dest.PrintStatus` (D-02) so the recovery Splash lands on the NEW
printer's Status rather than bouncing back to Devices. No rebind logic was added — tapping a tile only
persists the active id via `setActive`, and the existing `runConfigLoop` seam does the teardown + rebind
(T-14-11). The webcam holder re-key was NOT duplicated here (it landed fully in plan 03).

## What Was Built

### Task 1 — DevicesScreen, the field-of-printers switcher (commit `bcdc36b`)

- **`ui/screen/DevicesScreen.kt`** (NEW): `@Composable fun DevicesScreen(container, onAddPrinter, onSwitched, onBack)`.
  - Collects `container.profileStore.profiles` (`emptyList`) + `container.profileStore.activeId` (`null`) via
    `collectAsStateWithLifecycle`.
  - `ScreenScaffold(field = { grid }, gutter = { Back })` — **Focus omitted** (no single primary item),
    mirroring the Files scroll-Field + gutter-Back pattern.
  - **Field:** `LazyVerticalGrid(GridCells.Fixed(4))` reusing the `DrawerTile` square-tile grammar VERBATIM
    (`aspectRatio(1f)` sacred squares, `spacedBy(12.dp)`, `.padding(16.dp)`, `RoundedCornerShape(t.rCtrl)`,
    `2.dp` outline, `t.bg` background). One `PrinterTile` per profile (keyed on `profile.id`) + a final
    `AddPrinterTile` (keyed `"__add_printer__"`).
  - **Profile tile:** `dns` device glyph (sizeSp 40), `profile.displayName()` at `fsSp(20f)` SemiBold Geist,
    `"${profile.host}:${profile.port}"` at `fsSp(17f)` Regular GeistMono `t.text2`.
  - **Active tile** (`profile.id == activeId`, D-03): `t.accentSoft` fill tint + `t.accentLine` outline + a
    `bolt` accent marker glyph in the top-end corner. Inactive saved tiles use the ordinary
    `t.accentLine`/`t.surface2` live styling.
  - **Add-printer tile:** an `add` glyph + "Add printer" label → `onAddPrinter` (Settings, D-01).
  - **Switch tap:** `scope.launch { container.profileStore.setActive(profile.id) }` THEN `onSwitched()`. NO
    `ConfirmGuard`, NO `publishSpine`/disconnect/manual-rebind (T-14-11) — the persisted active-id flip drives
    `activeConfig` → the existing `runConfigLoop` rebind → recovery Splash. `onSwitched()` is the explicit
    D-02 navigation hook (AppShell maps it to `navigateTo(Dest.PrintStatus)`).
  - **Gutter:** a single full-width green `Intent.Go` `OutlinedControl` Back (`arrow_back`) → `onBack` (the
    explicit exit required because the swipe-drawer is suppressed for this scroll-Field).
  - Token-pure (no raw `Color(0x…)`), no looping animation (`rememberInfiniteTransition` absent).

### Task 2 — Devices tile LIVE + active-name subtitle, host Dest.Devices, land on Status (commit `00f5c7e`)

- **`AppDrawer.kt`**:
  - The `DrawerTileSpec(label = "Devices", symbol = "cable", …)` flipped `dest = null` → `dest = Dest.Devices`
    (keeps the unique `cable` glyph).
  - Added an `activeName: String? = null` param to `AppDrawer` (threaded the same way as
    `webcamEnabled`/`spoolEnabled`), passed into `DrawerTile` as `subtitle = if (tile.dest == Dest.Devices)
    activeName else null` — so ONLY the Devices tile gets a subtitle.
  - `DrawerTile` gained a `subtitle: String?` param; when non-null it renders an extra `Text` under the 16sp
    label at `fsSp(15f)` Regular `t.text2`, `maxLines = 1` + `TextOverflow.Ellipsis`. The live/greyed STYLING
    (190-220) was left untouched — Devices is a compile-time `dest != null` live tile.
- **`AppShell.kt`**:
  - Collected `val activeName by container.activeProfile.map { it?.displayName() }.collectAsStateWithLifecycle(initialValue = null)`
    (mirrors the `activeProfileId`/`webcamCount` collects) and passed `activeName = activeName` into the
    `AppDrawer(...)` call.
  - Added `Dest.Devices` to the swipe-suppress `setOf(...)`.
  - Replaced the `Dest.Devices -> Box(Modifier.fillMaxSize())` placeholder with
    `DevicesScreen(container, onAddPrinter = { navigateTo(Dest.Settings) }, onSwitched = { navigateTo(Dest.PrintStatus) }, onBack = { goBack() })`.
    `onSwitched = { navigateTo(Dest.PrintStatus) }` is the FIX-4 gate (D-02).
  - Added `import works.mees.dinghy.ui.screen.DevicesScreen`.
  - The webcam holder build (199-211) was NOT touched (the D-06 re-key landed in plan 03).

## Deviations from Plan

None — both tasks executed exactly as written. No auto-fixes were required; both `compileDebugKotlin` runs
succeeded on the first attempt and the full unit suite stayed GREEN.

## Verification

- `gw.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL** (Task 1: DevicesScreen).
- `gw.bat :app:compileDebugKotlin --no-daemon` — **BUILD SUCCESSFUL** (Task 2: drawer + shell wiring).
- `gw.bat :app:testDebugUnitTest --no-daemon` (FULL suite) — **BUILD SUCCESSFUL** — no regression from the
  drawer-param addition + the Dest.Devices host swap.
- Source assertions:
  - `DevicesScreen` uses `ScreenScaffold` with a Field tile grid + a green `Intent.Go` gutter Back; Focus omitted.
  - A profile-tile tap calls `container.profileStore.setActive(profile.id)` then `onSwitched()`; there is NO
    `ConfirmGuard` and NO `publishSpine`/disconnect on the switch path.
  - `DevicesScreen` declares `onSwitched: () -> Unit` and invokes it after `setActive` (the D-02 hook).
  - The active tile (`id == activeId`) renders `accentLine` outline + `accentSoft` tint + a unique `bolt`
    marker glyph, distinct from inactive tiles (D-03).
  - Name at `fsSp(20f)` SemiBold, `host:port` at `fsSp(17f)` GeistMono; all colors via `t.*`/`LocalTokens`.
  - No `rememberInfiniteTransition` / looping-animation API used.
  - `AppDrawer.kt` Devices tile has `dest = Dest.Devices`; `AppDrawer`/`DrawerTile` accept an `activeName`/
    `subtitle` param and render a 15sp `t.text2` ellipsized subtitle for the Devices tile only.
  - `AppShell.kt` collects `activeProfile.map { it?.displayName() }`, passes `activeName` to `AppDrawer`;
    `Dest.Devices` is in the swipe-suppress set and has a `when(dest)` host arm rendering `DevicesScreen(...)`.
  - The `Dest.Devices` host arm passes `onSwitched = { navigateTo(Dest.PrintStatus) }` (the FIX-4 gate).
  - This plan does NOT modify the webcam holder build (no `profileId`/`webcamBitmapHolder` edit in the AppShell
    diff — that landed in plan 03).

## Threat Mitigations Applied

- **T-14-11 (Tampering / a UI-driven second rebind path):** MITIGATED. The Devices tile tap calls ONLY
  `profileStore.setActive(profile.id)` + `onSwitched()` → `navigateTo(Dest.PrintStatus)`. No `publishSpine`,
  no manual disconnect, no parallel rebind sequencer appears in `DevicesScreen.kt` or this plan's `AppShell`
  diff — the proven `runConfigLoop` seam owns the teardown/rebind (code-review asserted; verified in the diff).
- **T-14-12 (Information disclosure / active-printer name + host:port rendered):** ACCEPTED per the register —
  name + `host:port` are non-secret user-facing identifiers; the API key is never rendered (the tile shows
  only host/port, and `Profile.toString()` redacts the key). Local single-user device.
- **T-14-SC (npm/pip/cargo installs):** N/A — no new external packages this phase.

## Known Stubs

None. The 14-03 `Dest.Devices -> Box(Modifier.fillMaxSize())` placeholder stub is now RESOLVED — replaced by
the real `DevicesScreen`. No new stubs introduced.

## Self-Check: PASSED

- Created file present on disk: `app/src/main/java/works/mees/dinghy/ui/screen/DevicesScreen.kt` (verified).
- Modified files present: `AppDrawer.kt`, `AppShell.kt` (verified in the HEAD~2..HEAD diff stat).
- Commits `bcdc36b` (Task 1) and `00f5c7e` (Task 2) exist in git log (verified).
- Devices tile live + active-name subtitle wired; DevicesScreen hosts on Dest.Devices; switch lands on
  PrintStatus (D-02); full unit suite GREEN; webcam holder untouched.
