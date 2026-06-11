# Phase 25 Icon Map — Browse Screen Glyph Assignments

**Status:** COMPLETE — all gaps resolved by owner 2026-06-10
**Produced by:** Plan 25-02 Task 1
**Source of truth:** `img/material-icon-bucket.json` + existing `DinghyIcons.kt` entries

---

## Glyph Map

| Screen | Function | Ligature | Owner Source | Registration | Status |
|--------|----------|----------|-------------|--------------|--------|
| **Files** | Start print | `print` | bucket: "start a print" | `DinghyIcons.Print` (NEW) | ✅ Confirmed |
| **Files** | Delete file | `delete` | bucket entry (no notes) | `DinghyIcons.Delete` (NEW) | ✅ Confirmed |
| **Files** | Back | `arrow_back` | `DinghyIcons.Back` (already registered) | REUSE | ✅ Already present |
| **Files** | Sort by age/date | `calendar_clock` | bucket: "sort by date in spoolman page"; `DinghyIcons.CalendarClock` (already registered) | REUSE | ✅ Already present |
| **Files** | Pick spool (spool-warning dialog) | `inventory_2` | `DinghyIcons.Inventory` (already registered) | REUSE | ✅ Already present |
| **Files** | Scan QR (spool-warning dialog) | `qr_code_scanner` | NOT in bucket; `DinghyIcons.QrCode` = `qr_code` (different ligature) | See note below | ⚠️ See note |
| **Console** | Hide-temperatures toggle (hide state) | `mode_heat_off` | bucket: "hide temperature messages in console" | `DinghyIcons.HideTemps` (NEW) | ✅ Confirmed |
| **Console** | Hide-timelapse toggle (hide state) | `video_camera_back` | bucket: "webcam menu icon, also show timelapse in console" | `DinghyIcons.HideTimelapse` (NEW) | ✅ Confirmed |
| **Console** | Hide-prompts toggle (hide state) | `chat_error` | bucket: "hide macro prompt messages in console" | `DinghyIcons.HidePrompts` (NEW) | ✅ Confirmed |
| **Macros** | Screen/section leader glyph | `code` | bucket: "macros" (note = "macros") | `DinghyIcons.MacrosLeader` (NEW) | ✅ Confirmed |
| **Macros** | Manage mode foot button | `bookmark_manager` | bucket entry (no notes) | `DinghyIcons.ManageMacros` (NEW) | ✅ Confirmed |
| **Macros** | Bookmarked trailing affordance | `check_circle` | `DinghyIcons.CheckCircle` (already registered) | REUSE | ✅ Already present |
| **Macros** | **Execute macro** (foot button, D-12) | `play_arrow` | **OWNER-APPROVED 2026-06-10** — new entry, not in bucket; owner-sanctioned assignment | `DinghyIcons.ExecuteMacro` (NEW) | ✅ Owner-approved |
| **Macros** | **Unbookmarked trailing affordance** (pin hint) | `radio_button_unchecked` | **OWNER-APPROVED 2026-06-10** — existing code's ligature explicitly sanctioned; outside bucket but owner-approved | `DinghyIcons.UnbookmarkedMacro` (NEW) | ✅ Owner-approved |

---

## Notes

### `qr_code_scanner` vs `qr_code`
The current `FilesScreen.kt` spool-warning dialog uses `qr_code_scanner` (a different ligature
from `qr_code`). The bucket has `qr_code` registered as `DinghyIcons.QrCode`. The spool-warning
dialog is NOT a primary Files action — it is the existing SpoolWarningGuard overlay which is
being preserved as-is (D-07). This is therefore a PRE-EXISTING call site that is NOT in scope
for the Wave-2 FilesScreen migration tokens. If the migration plan later touches this sub-dialog,
`qr_code_scanner` would need owner confirmation. For now: no new token needed for this function.

### `code` vs `bolt` for Macros
- `bolt` = `DinghyIcons.LauncherMacros` — the App Drawer / idle-list tile glyph for Macros
- `code` = `DinghyIcons.MacrosLeader` (new) — the in-screen section leader (bucket: "macros")
These are distinct surfaces; both are owner-assigned; no conflict.

### `bookmark_manager` vs `tune` for Manage mode
Current code uses `tune` for the "Manage macros" button; `tune` is already registered as
`DinghyIcons.LauncherCalibration`. The bucket has `bookmark_manager` as a separate entry.
Using `bookmark_manager` for Manage mode is more semantically precise and avoids reusing
a token across two unrelated functions. Mapped to `DinghyIcons.ManageMacros`.

### Sort-direction arrow
NOT in scope. The shipped `SortFilterControlRow` component hardcodes `arrow_upward`/`arrow_downward`
internally (owner-approved in Phase 23). No token to register.

---

## D-21 Gaps — RESOLVED (2026-06-10)

### Gap 1: Macro Execute glyph — RESOLVED

**Owner decision:** `play_arrow`

The new Phase-25 design (D-12) replaces the `MacroExecutionPopup` with a Field-takeover
param-entry + an **Execute foot button** that fires the macro. The owner assigned `play_arrow`
as a new owner-sanctioned entry (it was not previously in `img/material-icon-bucket.json`).
Registered as `DinghyIcons.ExecuteMacro` (`macros_execute`).

### Gap 2: Unbookmarked trailing affordance — RESOLVED

**Owner decision:** `radio_button_unchecked`

In Manage mode, each macro row shows a trailing affordance: bookmarked = `check_circle` (already
`DinghyIcons.CheckCircle`). For the NOT YET BOOKMARKED state, the owner explicitly sanctioned
the existing code's `radio_button_unchecked` ligature, even though it is outside the bucket.
Registered as `DinghyIcons.UnbookmarkedMacro` (`macros_unbookmarked`).

---

## Summary of New Tokens to Register (once gaps resolved)

| Token name | Ligature | Alternate handle |
|------------|----------|-----------------|
| `DinghyIcons.Print` | `print` | `files_print` |
| `DinghyIcons.Delete` | `delete` | `files_delete` |
| `DinghyIcons.HideTemps` | `mode_heat_off` | `console_hide_temps` |
| `DinghyIcons.HideTimelapse` | `video_camera_back` | `console_hide_timelapse` |
| `DinghyIcons.HidePrompts` | `chat_error` | `console_hide_prompts` |
| `DinghyIcons.MacrosLeader` | `code` | `macros_leader` |
| `DinghyIcons.ManageMacros` | `bookmark_manager` | `macros_manage` |
| `DinghyIcons.ExecuteMacro` | `play_arrow` | `macros_execute` |
| `DinghyIcons.UnbookmarkedMacro` | `radio_button_unchecked` | `macros_unbookmarked` |
