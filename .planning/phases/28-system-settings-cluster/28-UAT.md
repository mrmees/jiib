---
phase: 28
slug: system-settings-cluster
plan: 09
status: pending
created: 2026-06-12
---

# Phase 28 — On-Device UAT Record

> Owner UAT walk on **flox** (Nexus 7 2013 / Adreno 320 / LineageOS 18.1 / API 30 / armeabi-v7a).
>
> Build: verified-fresh release APK (debug-signed) produced by forced `--rerun-tasks` rebuild.
>
> **Pre-gate result:**
> - `:app:testDebugUnitTest` — **GREEN** (84 tasks executed, BUILD SUCCESSFUL)
> - `:app:assembleRelease` — **BUILD SUCCESSFUL** (exit 0, forced `--rerun-tasks`)
> - APK mtime: 2026-06-12 13:36:27 CST (epoch 1781289387)
> - Last source commit: 77d129e `docs(28-05): complete drawer-retirement plan` @ 2026-06-12 13:32:40 CST (epoch 1781289160)
> - Freshness check: **PASS** — APK mtime post-dates last commit by ~4 minutes
> - Install: `adb install -r` → **Success** (Incremental install on flox `0a64b42e`)
>
> Printer addresses: E5+ = 192.168.1.120:7125 / E3 = 192.168.1.121:7125

---

## Manual-Only Verification Items

| # | Item | Requirement | Status | Owner Notes |
|---|------|-------------|--------|-------------|
| 1 | Restyled cluster + System page — both orientations | SC-1 | pending | |
| 2 | C6 density + fit-one-page at S/M/L | SC-2/SC-3 | pending | |
| 3 | fsSp S/M/L + rotation correctness | SC-4 | pending | |
| 4 | No-regression live smoke (connection edit, theme apply incl. S/V restore, printer add/remove/switch, sysinfo read) | SC-5 | pending | |
| 5 | Swipe-up gesture dead + DRAWER_TILES reachability | D-04/D-05 | pending | |
| 6 | Mid-print e-stop reachable on System cluster screens | D-06 | pending | |

---

## Item Detail

### Item 1 — Restyled cluster + System page (SC-1)

**What to check:**
Open each of the following screens in BOTH portrait and landscape orientation:
- Settings
- Theme / Theme Editor (including the new S/V square)
- Printers (including Edit and Delete mode toggles + FootButtonBar)
- System Information
- About
- System page (NavDest.System — reachable from the home idle list)

**Pass criteria:**
- Each screen reads as jiib design: dense rows, token colors, correct intent colors per button type (accent=Add, stop=Delete, go=Apply, neutral=Back/Edit)
- System page Focus shows the jiib lockup + version + active printer name, capped at 20%-height portrait / 40%-width landscape ratio
- System page rows navigate directly to their destination on tap (no intermediate selection)
- Printers screen: FootButtonBar shows Add/Edit/Delete/Back; tapping Edit arms edit mode (row opens inline editor); tapping Delete arms delete mode (row shows ConfirmGuard on tap); tapping armed button again or Back disarms

**Status:** pending

---

### Item 2 — C6 density + fit-one-page (SC-2/SC-3)

**What to check:**
In the device Settings app, set text size to S, M, and L in turn. At each size:
1. Open Settings screen — confirm it fits on one page without scrolling at M
2. Open About screen — confirm it fits on one page without scrolling at M
3. Confirm density reads well in-hand; nothing feels cramped to the point of unreadability at L

**Pass criteria:**
- Settings fits one page at M text size (portrait and landscape)
- About fits one page at M text size
- Dense rows are legible at all three sizes; nothing clips at L

**Status:** pending

---

### Item 3 — fsSp S/M/L + rotation correctness (SC-4)

**What to check:**
While on each major screen (System page, Settings, Theme Editor, Printers, System Information, About):
1. Rotate the device between portrait and landscape
2. Confirm the layout reflows correctly (no overlap, no off-screen content)
3. At L text size, confirm no text drops below the 15sp floor (no text becomes invisible or illegible)

**Pass criteria:**
- All screens reflow correctly on rotation in both orientations
- No text below the 15sp legibility floor at L size
- fsSp scaling is perceptibly larger at L vs S (not identical)

**Status:** pending

---

### Item 4 — No-regression live smoke (SC-5)

**What to check (requires live printer — E5+ at 192.168.1.120:7125 or E3 at 192.168.1.121:7125):**

1. **Connection edit:** Navigate to Printers → Edit mode → tap a printer row → edit the host/port/key fields → Save → confirm reconnect works
2. **Theme apply (incl. S/V restore):** Open Theme Editor → pick a new S/V position on the square → tap Apply → re-open the editor and confirm the S/V crosshair is restored to the chosen position
3. **Printer add:** Add a new printer profile (any valid or dummy connection data)
4. **Printer switch:** Switch the active printer by tapping a different row in Normal mode
5. **Printer delete:** Delete a printer via Delete mode → row tap → ConfirmGuard → confirm
6. **System Information read:** Open System Information from the System page and confirm all fields populate (Moonraker version, Klipper version, host info, etc.)

**Pass criteria:**
- Connection edit round-trips without crash
- Theme apply persists; S/V square restores the last-saved saturation+value on re-open
- Printer add/switch/delete all work without crash
- System Information shows live data from the connected printer

**Status:** pending

---

### Item 5 — Swipe-up dead + DRAWER_TILES reachability (D-04/D-05)

**What to check:**
1. **Swipe-up dead:** Attempt a deliberate swipe-up gesture from the bottom of several screens (WaterfallHome, PrintStatus, Settings, System page). The App Drawer must NOT appear anywhere.
2. **Former drawer destinations reachable:** Confirm every destination that used to live in the drawer is now accessible:
   - Temperature — on the home idle list (WaterfallHome when idle)
   - Console — on the home idle list
   - Fine-Tune — on the home idle list
   - Printers — System page row
   - Settings — System page row
   - Theme — System page row
   - System Information — System page row
   - About — System page row
   - Power stub — System page row (greyed/inert, "Coming soon" or similar)
   - System entry in the printing shortcut grid (tap while a print is running)

**Pass criteria:**
- Swipe-up produces NO drawer anywhere; no visual glitch
- All 10 former drawer destinations are reachable via the new paths
- Temperature, Console, Fine-Tune appear on the home idle list
- System page rows navigate to their correct screens

**Status:** pending

---

### Item 6 — Mid-print e-stop reachable on System cluster (D-06)

**What to check:**
While a print is actively running on a connected printer:
1. Navigate to the System page from the printing shortcut grid System entry
2. From the System page, navigate into any sub-screen (Settings, Theme, Printers, System Info, About)
3. Confirm the floating e-stop (FloatingEStop overlay) is still visible and reachable on these screens
4. Tap the e-stop and confirm the full-screen Stop Confirm guard appears (no navigation away required; the e-stop must be functional)

**Pass criteria:**
- FloatingEStop overlay is present and visible on System page and all cluster sub-screens during a print
- Tapping the FloatingEStop opens the full-screen Stop Confirm guard
- E-stop is not accidentally hidden by any System cluster screen layout

**Status:** pending

---

---

## Gaps

### GAP-A — U-grid abandoned on Phase-28 surfaces (dense 10dp rows instead of 1U)

**Root cause:** UI-SPEC D-09 / CONTEXT D-09 said "sub-1U rows"; the UI-SPEC's Interaction Contract
section flattened that to "vertical padding 10dp top + 10dp bottom (instead of U-derived min)".
`ListRow.dense = true` implemented this as `Modifier.padding(vertical = 10.dp)` with no
`heightIn` floor. On device: row heights floated with content, no shared vertical rhythm.

**Owner UAT ruling (2026-06-12, verbatim intent):** "All 1U." Every row/control on every
Phase-28 screen (including System page, Settings, SysInfo, About, Printers, ThemeEditor) must
honor the unit-grid 1U height floor.

**Fix:**
- `ListRow`: deleted `dense` param; `heightIn(min = uDp)` applied unconditionally
- `TokenTextField`: deleted `dense` param and `heightIn(max = 48.dp)` cap
- `SystemPageScreen.PowerStubRow`: `heightIn(min = uDp)` added; `padding(vertical = 10.dp)` removed
- `AboutScreen.DevEnableRow`: `heightIn(min = uDp)` added; `BoxWithConstraints/rememberUnitGrid` wired
- All `dense = true` call sites removed in: SettingsScreen, PrintersScreen, SystemInformationScreen, SystemPageScreen

**Fix commit:** `e51254c fix(28-09): restore 1U height floor on all Phase-28 surfaces (GAP-A)`

**Docs updated:**
- `docs/ui_design/LAYOUT.md` C6 bullet amended — "sub-1U rows" concept revoked
- `28-UI-SPEC.md` Dense Row section amended with UAT ruling note

**Status:** resolved

---

## Overall Status

**Status:** pending

*Will be updated to `passed` / `partial` / `failed` after the owner walk.*
