---
phase: 11-spool-management-spoolman-camera-qr
plan: 06
subsystem: spool
tags: [wave-4, spool-ui, drawer-tile, capability-gate, appshell-routing, active-spool-card, files-style-picker, d03-variants, d05-material-chips, d06-color-swatch, d08-color, d16-typography]
requires:
  - "11-02: SpoolmanModels (SpoolmanSpool/Filament/Status + D-08 normalizedColorHex/colorSwatches) + SpoolmanParsers (parseSpoolmanSpools/Filaments/Materials/Vendors/Locations)"
  - "11-04: SpoolmanClient inventory reader + ActiveSpoolFacade + AppContainer.activeSpool/spoolmanPresent + SpineHandle.activeSpool"
provides:
  - dest-spool: "Dest.Spool route enum value + the capability-greyed inventory_2 drawer tile (D-02, mirrors the Webcam tile gate)"
  - appshell-spool-arm: "AppShell when(dest) Dest.Spool -> SpoolScreen routing arm + spoolEnabled threaded into AppDrawer (the plan-check BLOCKER fix)"
  - spine-spoolman-client: "SpineHandle.spoolmanClient (default no-op) + AppContainer.currentSpoolmanClient + MoonrakerService construction of MoonrakerSpoolmanClient"
  - spool-holder: "SpoolHolder (SpoolPickerState over SpoolmanClient + activeSpool flow) + pure buildSpoolQuery + SpoolSort/SpoolFilters"
  - active-spool-card: "ActiveSpoolCard + deriveActiveSpoolCardState (every D-03 variant) on Print Status with Scan/Change/Clear"
  - spool-screen-picker: "SpoolScreen (Dest.Spool host, Focus/Field/Gutter) + SpoolPicker (dense list + D-04/05/06 chips + sorts)"
affects:
  - "11-07 (QR scan surface) wires the onScan hook AppShell passes the Spool screen + the active-spool card Scan action to a real scan Dest/sub-surface"
  - "11-08 (print-start gate) is independent; the active-spool truth + SpoolmanClient inventory reads this plan consumes are shared"
tech-stack:
  added: []
  patterns:
    - "Webcam runtime-greyed-tile gate (webcamEnabled) replicated for spoolEnabled (D-02 capability greying)"
    - "Webcam routing arm (Dest.Webcam -> WebcamScreen) replicated for Dest.Spool -> SpoolScreen"
    - "FileBrowserHolder StateFlow+asStateFlow+scope.launch+runCatching idiom replicated for SpoolHolder"
    - "LastJobCard token-card + swatch + icon-led stat-row grammar replicated for ActiveSpoolCard"
    - "FilesScreen ScreenScaffold Focus/Field/Gutter + showFocus portrait-collapse replicated for SpoolScreen"
    - "Session client exposed on SpineHandle (fileBrowser precedent) — UI never sees a raw JsonRpcClient (D-02)"
    - "Pure derive() state-derivation idiom for deriveActiveSpoolCardState + buildSpoolQuery (host-testable)"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolHolder.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/ActiveSpoolCard.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt
    - app/src/main/java/works/mees/dinghy/ui/spool/SpoolPicker.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/route/TopRoute.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppDrawer.kt
    - app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/di/SpineHandle.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/service/MoonrakerService.kt
decisions:
  - "Exposed the session SpoolmanClient on SpineHandle (default no-op object) rather than handing the UI a raw rpc — the fileBrowser precedent; UI never sees a JsonRpcClient (D-02). The default avoided editing the three headless SpineHandle test construction sites (the 11-04 deviation pattern), so the unit suite stayed green untouched"
  - "ActiveSpoolCard derivation is a pure deriveActiveSpoolCardState(spoolmanPresent, status, detail, changedExternally) — host-testable like derive(); the screen resolves the /v1/spool/{id} detail once-per-id via currentSpoolmanClient and feeds it in"
  - "The detail endpoint returns a SINGLE object (not an array) inside the proxy-v2 envelope, so parseSpoolmanSpools (array-only) returns empty — added a small parseSpoolDetail walk of the envelope response object for the card"
  - "Card shown only when spoolmanPresent (D-02) and placed ABOVE the state-driven field content (useful idle AND mid-print — a runout/M600 swap is a print-time concern), not gated to idle"
  - "Picker is a pure Compose LazyColumn (the Webcam CamPicker precedent), so the Files Views-in-Compose pinned-height RecyclerView scroll lesson is MOOT — no AndroidView host; the Focus/Field/Gutter collapse still applies"
  - "D-06 color two-step fires ONLY on swatch tap (the slow operation): applyColorSwatch hits /v1/filament?color_hex&color_similarity_threshold=20 then folds the returned filament ids into filament.id=<csv>; an empty similarity result sends filament.id=-1 (deliberate no-match) rather than dropping the filter"
  - "Picker rows are NEVER gated on print-state (deliberately avoids the Files Delete-blocks-all-during-print deferred defect, per MEMORY)"
  - "SpoolHolder applied filters/sort/selection are session-derived (remember keyed on the store in the shell), not DataStore-persisted (Discretion)"
  - "inventory_2 chosen as the Spool tile glyph (unique among DRAWER_TILES — icon-no-repeat law); no beta=true (Spool is not a development flag, unlike the amber Webcam)"
metrics:
  duration: ~18m
  completed: 2026-06-04
  tasks: 3
  files: 11
---

# Phase 11 Plan 06: Spool UI — Drawer Tile + Active-Spool Card + Files-Style Picker Summary

Built the "what's loaded + browse/select" half of the Spool view (SPOOL-02 card, SPOOL-03 picker): `Dest.Spool` + the capability-greyed `inventory_2` drawer tile, the **AppShell `Dest.Spool -> SpoolScreen` routing arm** with `spoolEnabled` threaded into the drawer (the plan-check BLOCKER fix — without it the tile navigated nowhere and never greyed), a compact active-spool card on Print Status spanning **every D-03 state variant** with Scan/Change/Clear, and a Files-style Focus/Field/Gutter picker with D-04/05/06 filters + recent/low-remaining sorts. All color is rendered from the D-08 normalized split swatch (never a raw untrusted hex), every control routes through `LocalTokens` (THEME-01), and **all text uses `fsSp(...)` at the D-16 scale with zero hardcoded `.sp`**. `:app:compileDebugKotlin`, `:app:assembleDebug`, and the full `:app:testDebugUnitTest` all pass — the wire lands in the live route graph and no test regressed.

## What Was Built

### Task 1 — Dest.Spool + greyed tile + AppShell arm + SpoolHolder (commit `be5f541`)
- `TopRoute.kt`: `Spool` added to the `Dest` enum between `Webcam` and `Settings` (the scan surface is NOT a Dest, D-02).
- `AppDrawer.kt`: a `DrawerTileSpec(label="Spool", symbol="inventory_2", dest=Dest.Spool)` (icon-no-repeat) + a `spoolEnabled: Boolean = false` param folded into `DrawerTile`'s `live` decision (`tile.dest != Dest.Spool || spoolEnabled`) EXACTLY as `webcamEnabled` — greyed when the spoolman component is absent (D-02). No `beta=true`.
- `AppShell.kt`: (a) `val spoolEnabled by container.spoolmanPresent.collectAsStateWithLifecycle(initialValue = false)`; (b) the per-session `SpoolHolder` `remember`ed keyed on `store` (re-keys on reconnect) over the session `spoolmanClient` + `activeSpool` flow (idle no-op/`MutableStateFlow(null)` fallbacks); (c) the `Dest.Spool -> SpoolScreen(holder, dispatcher, onBack, onScan)` arm; (d) `spoolEnabled = spoolEnabled` passed into `AppDrawer(...)`; (e) `Dest.Spool` added to the swipe-suppress set (scrollable picker).
- `SpoolHolder.kt`: `SpoolPickerState` + the FileBrowserHolder idiom — `MutableStateFlow`/`asStateFlow`, a `scope.launch` reaction to the upstream `activeSpool` flow (D-10 reconcile of the "loaded" marks), `runCatching` SpoolmanClient reads that degrade to empty, suspend mutators (`selectSpool`/`toggleMaterialFamily`/`toggleVendor`/`toggleLocation`/`applyColorSwatch`/`applySort`/`refresh`), and the pure `buildSpoolQuery(filters, sort)` (D-04/05/06 + recent/low/default).
- `SpineHandle.kt`: `spoolmanClient: SpoolmanClient = object : SpoolmanClient {}` (the `fileBrowser` precedent; default no-op kept the three headless test sites compiling untouched).
- `AppContainer.kt`: `currentSpoolmanClient` snapshot accessor (mirrors `currentFileBrowser`).
- `MoonrakerService.kt`: constructs `MoonrakerSpoolmanClient(rpc)` and assigns `spoolmanClient = spoolmanClient` on the handle.

### Task 2 — Active-spool card on Print Status (commit `72b21a3`)
- `ActiveSpoolCard.kt`: `deriveActiveSpoolCardState(spoolmanPresent, status, detail, changedExternally)` — a pure `when` over EVERY D-03 variant (`Unavailable`/`Disconnected`/`NoActive`/`Loading`/`Loaded{stale,changedExternally}`). The card copies LastJobCard: material / D-08 split swatch / vendor / remaining (GeistMono 26sp tabular hero) / location, archived D-09 badge, stale D-11 badge, changed-externally D-10 badge. Swatch from `colorSwatches` only (multi-color split), via a `normalizeColorHex`-guarded `parseNormalizedHex` (T-11-06-01). Scan/Change = accent `OutlinedControl`, Clear = red Danger.
- `PrintStatusScreen.kt`: new `onOpenSpool` param; collects `spoolmanPresent` + `activeSpool`; a `LaunchedEffect(activeSpoolId)` resolves the `/v1/spool/{id}` detail via `currentSpoolmanClient.getSpool` (`parseSpoolDetail` walks the lone-object proxy-v2 envelope); the card renders above the state-driven field, only when `spoolmanPresent`. Change/Scan/card → `onOpenSpool`; Clear → `post_spool_id {}` (`SetSpoolArgs(null)`, D-13).

### Task 3 — Dest.Spool screen + Files-style picker (commit `33ef25d`)
- `SpoolScreen.kt`: the `Dest.Spool` host on `ScreenScaffold` — `showFocus = maxWidth > maxHeight || selected != null` (portrait collapse), Focus = the selected-spool detail (material / split swatch / vendor / remaining 30sp hero · used linked D-04 / location / D-09 archived badge), Field = `SpoolPicker`, Gutter = red Back / accent Scan / green **Load spool** (`spoolmanPostSpoolId` via `dispatcher`; the holder reconciles the resulting `notify_active_spool_set`, D-10).
- `SpoolPicker.kt`: a pure Compose `LazyColumn` dense list (scroll lesson moot) + a sort toggle (Browse/Recent/Low-remaining) + material-family chips (D-05 comma multi-select) + the fixed color palette swatches firing the D-06 two-step ONLY on tap + vendor/location/"No location" chips (D-04). Every row renders its ACTUAL `colorSwatches` (D-06), the active spool marked green "Loaded". No print-state row gating.

## Verification

- `:app:compileDebugKotlin` → BUILD SUCCESSFUL (after each task).
- `:app:assembleDebug` → BUILD SUCCESSFUL — the `Dest.Spool` arm + `spoolEnabled` thread land in the live route graph and the APK packages.
- `:app:testDebugUnitTest` (full) → BUILD SUCCESSFUL, no regressions (the three SpineHandle test construction sites stayed green via the no-op default — no test edits needed).
- Grep gates:
  - `AppShell.kt` contains `Dest.Spool ->` (`Dest.Spool` count 3), `spoolEnabled` collected from `container.spoolmanPresent`, and `spoolEnabled = spoolEnabled` passed into `AppDrawer(...)`.
  - **No hardcoded `.sp`** in any of the four new spool UI files — every `.sp` sits on an `fsSp(...)` call (confirmed by grep for raw `N.sp`/`Nf.sp` literals → none).
  - Font scale matches D-16: card titles 22sp, remaining/used tabular 26–30sp, row labels 17–18sp, metadata floor 15sp (never 12–13), focus values 30sp, icons 18–64sp via `MaterialSymbol(sizeSp = fsSp(...))`.
  - No raw `Color(0x` literal in `ui/spool/` (THEME-01) — color comes from tokens or the sanctioned `parseNormalizedHex` (D-08 normalized → `android.graphics.Color.parseColor`).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Exposed the session SpoolmanClient on SpineHandle (the picker had no inventory reader)**
- **Found during:** Task 1 (the SpoolHolder needs `SpoolmanClient` inventory reads, but `SpineHandle` deliberately exposes NO raw `JsonRpcClient`, and the 11-04 service comment said the UI "constructs off the same rpc" — which the UI cannot reach).
- **Issue:** `MoonrakerService` constructed the `ActiveSpoolFacade` but not a `MoonrakerSpoolmanClient`, and `SpineHandle` had no field to carry one; the picker holder had nothing to read inventory through.
- **Fix:** Added `spoolmanClient: SpoolmanClient = object : SpoolmanClient {}` to `SpineHandle` (the `fileBrowser` precedent — UI never sees a raw rpc, D-02), constructed `MoonrakerSpoolmanClient(rpc)` in the service, and exposed `AppContainer.currentSpoolmanClient`. The **default no-op** kept the three headless `SpineHandle` test construction sites (`AppContainerTest`/`ShellPresenceTest`/`DrawerWebcamGatingTest`) compiling unedited (avoiding the all-required-fields break the 11-04 deviation note hit).
- **Files modified:** `SpineHandle.kt`, `MoonrakerService.kt`, `AppContainer.kt`.
- **Commit:** `be5f541`.

**2. [Rule 2 - Missing functionality] Added a single-object detail parser for the active-spool card**
- **Found during:** Task 2 (the active-spool detail read `/v1/spool/{id}` returns a lone object in the proxy-v2 envelope, but `parseSpoolmanSpools` only handles an array `response`, so the card never resolved a detail).
- **Issue:** Without an object-shaped parse the card would stay in `Loading` forever for a loaded spool.
- **Fix:** Added a small null-safe `parseSpoolDetail(envelope, expectedId)` that walks the envelope `response` object, decodes it via the shared `MoonrakerJson`, and id-matches; a malformed/absent envelope → null (card stays Loading), never throws (T-11-06-01). Kept local to `PrintStatusScreen` (the only consumer this plan owns).
- **Files modified:** `PrintStatusScreen.kt`.
- **Commit:** `72b21a3`.

## Known Stubs

- **`onScan` is a no-op hook** in both the AppShell `Dest.Spool` arm and the active-spool card Scan/Change actions (they route to the Spool screen for now). This is intentional and documented: the QR scan sub-surface is owned by **11-07** (the next wave), which wires this hook to the real scan Dest/sub-surface. Not a defect — the consumer (scan surface) lands in a sibling plan; this plan exposes the seam exactly as the Webcam screen exposes its callbacks.

No data-source stubs: the card and picker are fed by the live `AppContainer.activeSpool`/`currentSpoolmanClient` flows (11-04), not mock/empty data.

## Threat Flags

None. The two trust boundaries this plan touches are mitigated as the register prescribes: a bad `color_hex` renders only via the D-08 `normalizeColorHex`-guarded `colorSwatches` → a neutral marker, never a parse crash (T-11-06-01); the absent-`spoolman`-component path greys the drawer tile (D-02) and drives the card's `Unavailable` variant, never a crash (T-11-06-02); the active-spool truth is read from `AppContainer.activeSpool` (facade-reconciled, D-10) and the card surfaces the changed-externally badge (T-11-06-03). No new network endpoints, auth paths, or schema surface beyond the planned proxy reads + the existing `post_spool_id` write.

## Self-Check: PASSED

- Files created: `SpoolHolder.kt`, `ActiveSpoolCard.kt`, `SpoolScreen.kt`, `SpoolPicker.kt` — all FOUND.
- Commits: `be5f541`, `72b21a3`, `33ef25d` — all FOUND in git log.
- `:app:assembleDebug` + full `:app:testDebugUnitTest` BUILD SUCCESSFUL; AppShell grep gates pass; zero hardcoded `.sp` in the new spool UI; no raw `Color(0x` in `ui/spool/`.
