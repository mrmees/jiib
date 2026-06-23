# Manage Printers Rework — Design

**Date:** 2026-06-23
**Status:** Approved (pending spec review)
**Surfaces:** `PrintersScreen.kt`, `PrinterConnectionEditor.kt` (and the discovery/scan machinery)

## Goal

Reorganize the printer-management surfaces so the common actions live where they belong:
manual add + network discovery at the top of the printer list, destructive delete tucked inside
the individual printer's own editor, and a self-explanatory instructions Focus. Discovery becomes
a one-tap add-and-connect flow with an editor fallback when a found printer can't connect.

## Current state (for reference)

- **`PrintersScreen` ("Manage Printers")** — Field = printer-profile rows. Foot bar = Back / Add /
  Edit / Delete (4 icon-only buttons). `PrinterMode = Normal | EditArmed | DeleteArmed`: Normal tap =
  switch active; EditArmed tap = open inline editor; DeleteArmed tap = `ConfirmGuard` delete. Focus =
  active-printer card (name, host:port, connection-state ring + label).
- **`PrinterConnectionEditor` ("individual printer settings page")** — Field rows = Name / Host /
  Port / API Key / **Find** / **Advanced**. Tapping a row swaps the Focus into that field's editor;
  the **Find** row opens an in-Focus scan panel (`ConnFindPanel`) that fills host/port on pick. Foot =
  Back / Test / Save. Opened inline from Printers (Edit-armed or Add) and from Printer Settings →
  Connection.

## Changes

### 1. Manage Printers screen (`PrintersScreen`)

**Field list, top → bottom:**

1. **Add printer** row — `DinghyIcons.PrinterAdd`, label `printers_add`. Direct tap → open the editor
   as a *new, blank* printer (manual entry). Always direct-action; unaffected by Edit-arm.
2. **Find on network** row — `DinghyIcons.Search`, label `conn_row_find`. Direct tap → Focus-takeover
   scan panel (see §4). Always direct-action; unaffected by Edit-arm.
3. **Printer profile rows** — unchanged rendering. Tap behaviour is mode-driven:
   - Normal → `setActiveProfile` + `onSwitched()`.
   - Edit-armed → open that printer's editor inline.

**Foot bar — 2 buttons (≤2 → icon + text):**

- **Back** — `Intent.Accent` (unchanged).
- **Edit** — `Intent.Accent` **always** (outlined in color), with `fill = t.accentSoft` **only when
  armed** (filled when selected). Uses the existing `FootAction.fill` param — no component change.
- **Delete is removed from this screen.**

**Mode machine simplification:** `PrinterMode` collapses to `Normal | EditArmed`. Delete the
`DeleteArmed` case, `armDelete()`, `RowTapEffect.RequestDelete`, the `pendingDelete` state, and the
on-screen `ConfirmGuard`. The `BackHandler` priority chain drops its `pendingDelete` branch.

**Focus = static instructions** (replaces the active-printer card in all states, including empty):
a short how-to block, e.g.

> - **Add a printer** with the button at the top of the list.
> - **Tap a printer** to make it the active one.
> - **Edit** a printer: tap Edit, then tap the printer.
> - **Delete** a printer from inside its own settings.

Rendered in a `FocusFrame` titled with the existing Printers identity
(`system_row_printers` / `SystemRowPrinters`). New string resources (one per line, or a single
multi-line resource). This doubles as the empty state — with zero printers the Field still shows the
Add + Find rows and the instructions explain the screen, so the old `printers_empty_*` Focus branch
is no longer needed on this screen.

### 2. Printer editor (`PrinterConnectionEditor`)

- **Remove the Find row** — drop `ConnRow.Find`, `ConnFindPanel`, and the scan machinery
  (`discovery.discover()` collection, `SCAN_WINDOW_MS`, `discovered`/`scanning`/`scanned`/`scanRequest`
  state, the scan `LaunchedEffect`, and the `onScan`/`onPick` plumbing). These move to the Printers
  screen (§4). Editor field rows become: Name / Host / Port / API Key / Advanced.
- **Add a Delete row at the very bottom, after Advanced** — `DinghyIcons.Delete`, label
  `printers_delete`, `Intent.Danger` tint. Shown **only when editing an existing printer**
  (`profile != null`), never when adding. It is a direct **action** row (not a Focus-editor row):
  tapping it raises a `ConfirmGuard` (reuse `printers_delete_confirm_title/body`, `printers_delete`,
  `common_back`, `destructive = true`) → on confirm `container.deleteProfile(profile.id)` → `onDone()`
  (returns to the printer list). The `ConfirmGuard` is hosted inside the editor composable.

### 3. Edit-button styling (mechanics)

```kotlin
FootAction(
    label = stringResource(R.string.printers_edit),
    icon = DinghyIcons.Edit,
    onClick = onArmEdit,
    intent = Intent.Accent,                                   // always outlined in color
    fill = if (printerMode == PrinterMode.EditArmed) t.accentSoft else null, // filled when selected
)
```

`OutlinedControl` already draws a 2dp `intent.outlineColor` border on a `fill ?: t.surface`
background, so this is purely a call-site change.

### 4. Find-on-network flow (moved to Printers, new behaviour)

The discovery/scan machinery moves to `PrintersScreen`. Tapping the **Find on network** row takes over
the Focus with a scan panel (lift the `ConnFindPanel` UI): a **Scan** button + the discovered-printer
list. Scan states:

- **Scanning** → "Scanning…" (button disabled).
- **Found ≥1** → list of discovered printers (host + port), tap to pick.
- **Scanned, none found** → "Nothing found" message (`conn_scan_empty`). No auto-anything.

**On pick** of a discovered printer:

1. Build a `ConnectionConfig` for the discovered host:port (no API key) and run
   `container.runConnectionProbe(config)`.
2. **Probe OK** → build the profile via `buildProfileFromConnectionEditorSave(existing = null, …)`,
   `container.saveProfile(...)`, `container.setActiveProfile(id)`, leave the Find takeover → the list
   shows it active/connected.
3. **Probe fails** (auth/security/refused/timeout/etc.) → open the editor **pre-filled** with the
   discovered host/port as a *new, unsaved* profile (same editor used by manual Add, seeded). The user
   adds an API key / fixes it and Saves. Nothing is persisted until they Save — backing out leaves no
   dead printer.

**Manual Add is a separate path:** the Add row opens the same editor with *no* seed (blank). Find
(discovery) and Add (manual) never share an entry point.

**Decision (resolved):** probe-failure opens the editor *unsaved* (don't persist-then-edit), so an
unreachable discovery never litters the printer list. Manual entry stays a distinct flow.

## Data flow (Find pick)

```
Find row tap → Focus takeover (scan panel)
  Scan → discovery.discover() (bounded SCAN_WINDOW_MS) → discovered: List<DiscoveredPrinter>
  pick(printer) → runConnectionProbe(config(printer))
     ├─ ok   → saveProfile(newProfile) → setActiveProfile(id) → close takeover
     └─ fail → open editor seeded with printer.host/port (EditorTarget.New + seed)
```

The pick→decision logic (probe result → `AddAndConnect` vs `OpenEditorSeeded`) is extracted as a
**pure, package-level function** (mirroring `rowTapEffect` / `buildProfileFromConnectionEditorSave`)
so it is host-testable without Compose.

## Components / units touched

- `PrintersScreen.kt` — Field rows (Add/Find + profiles), 2-button foot bar, instructions Focus,
  mode-machine trim, scan state + Find takeover, pick decision helper.
- `PrinterConnectionEditor.kt` — remove Find, add Delete action row + `ConfirmGuard`, accept an
  optional host/port **seed** for the discovery-failure path.
- No new icons (`PrinterAdd`, `Search`, `Delete`, `Edit` all exist) → no icon-law ask.
- New string resources: instructions block (Printers Focus).

## Testing

- `PrintersModeToggleTest` — trim to the 2-mode (`Normal | EditArmed`) machine; drop DeleteArmed
  assertions.
- New host test for the Find pick→decision pure function (probe-ok → add/connect; probe-fail →
  open-editor-seeded).
- Editor: host test that Delete is gated on `profile != null`; confirm `deleteProfile` reassigns the
  active profile when the deleted one was active (verify existing behaviour before relying on it).
- `@Preview` matrices updated: Printers (instructions Focus, Add/Find rows, Edit unarmed/armed),
  editor (Delete row present when editing / absent when adding).
- Build green (assembleDebug + unit tests); on-device UAT on flox + moto (push matching ABI to both).

## Risks / notes

- **Deleting the active printer** from its editor (reachable via Printer Settings → Connection): rely
  on `container.deleteProfile` to reassign/clear the active profile. Verify before shipping.
- **Write-scope law** ([[dinghy-compose-write-scope-cancellation]]): every persist (saveProfile,
  setActiveProfile, deleteProfile) routes through `AppContainer` intent helpers on the process-scoped
  writeScope — unchanged from current call sites.
- **Edit-arm vs Add/Find rows:** Add and Find are always direct-action; only profile rows honor the
  mode. Document this in the row click handler.
