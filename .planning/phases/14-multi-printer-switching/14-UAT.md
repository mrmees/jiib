# Phase 14-06 — On-Device UAT (Multi-Printer Switching)

**Device:** flox (Nexus 7 2013, LineageOS 18.1 / Android 11, Adreno 320 / 2GB / armeabi-v7a).
**Build:** debug APK through commit `781277f` (the durable-writeScope gap-closure fix), reinstalled on flox this session.
**Printers:** **Ender 5 Plus** Moonraker @ **192.168.1.120:7125** and **Ender 3 Pro** @ **192.168.1.121:7125** (both klicky probes).
**Run date:** 2026-06-04 (run by Matthew on flox + both live printers).
**Status:** ✅ **PASSED** (2026-06-04, after the `781277f` gap-closure). 5/6 items PASS; item 4 (mid-print switch) owner-deferred (not blocking — same teardown/rebind path proven by items 1/2). The instrumented `ProfileSurvivesRestartTest` (`7bb52dc`) objectively proves item 6 (SC-3) as well.

---

## Why this UAT is mandatory (mock-vs-reality)

14-VALIDATION §"Where a Fake would LIE": capabilities are **server-derived**, so no-stale-state across a
real spine rebind only emerges against **live Moonraker** — a fake session publishing a canned
`SpineHandle` won't catch a stale-capability bug. The active-id survival happy-path is now covered by the
instrumented `ProfileSurvivesRestartTest` (GREEN on flox, commit `7bb52dc`); the six items below are the
behaviors that ONLY a live two-printer run on real hardware can prove. **Any FAIL spawns a gap-closure
plan — do NOT mark the phase complete on a FAIL.**

---

## Prereqs

- [x] Task 1 instrumented `ProfileSurvivesRestartTest` GREEN on flox (Nexus 7 - 11), 1 test / 0 failures (`7bb52dc`).
- [x] Full `:app:testDebugUnitTest` GREEN (final phase regression; also re-GREEN after the `781277f` gap-closure).
- [x] Debug APK built via `E:\Android\gw.bat` and installed on flox (through `781277f`).
- [x] Both printers powered + reachable (E5+ 192.168.1.120:7125, E3 192.168.1.121:7125).

---

## The six UAT items

### Item 1 — Fresh start + add both printers (D-07 / D-11 / D-10)

First launch with 0 profiles → the Connect prompt routes to Settings. Add the **E5+** (host/port,
optional API key, give it a name + a distinct theme/accent). Add the **E3** (name + a DIFFERENT
theme/accent). Confirm each appears as a profile row.

- **Expected:** empty store → Connect prompt → Settings; both printers added; each shows as a profile row with its own name/theme.
- **result:** ✅ **PASS** — fresh add of both printers: each adds as its own profile row with a distinct theme (D-07/D-11/D-10 confirmed on flox).

---

### Item 2 — Switch + drive each, no re-entry (SC-4 / D-03 / D-08)

Open the **Devices** tile (drawer subtitle shows the active printer's name, D-03). Tap the **E5+** →
recovery Splash → lands on the E5+ Status; drive a control (a jog or a temp set). Tap the **E3** → Splash
→ E3 Status; drive a control. Neither switch re-asks for host/port/key. The whole app look changes per
printer (D-08).

- **Expected:** each tap → Splash → that printer's Status; a control action works on each; NO re-entry of host/port/key; the full theme flips per printer.
- **result:** ✅ **PASS** — Matthew: "switch works as fast as I can navigate the screens to do it." The active (⚡) marker moves to the tapped printer **every time**, each switch lands on that printer's Status, a control drives it, no host/port/key re-entry, and the whole app look flips per printer (D-08). _(This is the headline SC-4 item that initially FAILED with intermittent reverts before the `781277f` durable-writeScope fix — see Gaps.)_

---

### Item 3 — No-churn on theme edit (Pitfall 1 / `distinctUntilChanged`)

With one printer active, change its accent in Settings → confirm **NO** recovery Splash fires (the
connection must not bounce).

- **Expected:** an accent/theme edit on the active profile does NOT trigger a recovery Splash / reconnect — `distinctUntilChanged` suppresses the config churn.
- **result:** ✅ **PASS** — editing the active printer's theme/accent fires NO recovery Splash; the connection does not bounce (`distinctUntilChanged` / T-14-04 suppression confirmed on-device).

---

### Item 4 — Mid-print switch (D-04)

With a print running on one printer, switch to the other and back — the print continues untouched.

- **Expected:** switching away from (and back to) a printing machine leaves its print running; the tablet just changes which printer it watches.
- **result:** ⚪ **NOT RUN — owner-deferred (NOT blocking).** Matthew declined to start a live print this session to avoid burning filament. The switch teardown+rebind path exercised here is the SAME one proven repeatedly in items 1/2 (switching only changes which printer the tablet watches; it sends no print-affecting gcode), so this carries low residual risk. Re-run opportunistically the next time a print is genuinely running on one printer.

---

### Item 5 — Delete active + delete last (D-12 / D-14 / D-11)

Delete the **ACTIVE** profile via the Settings ConfirmGuard → confirm the app auto-selects the remaining
printer and rebinds (no dead no-active state). Then delete the **last** profile → falls to the Connect
prompt (D-11).

- **Expected:** deleting the active profile auto-picks another remaining profile + rebinds (Splash → its Status); deleting the last profile → Connect prompt. Both deletes go through the full-screen Confirm guard.
- **result:** ✅ **PASS** — deleting the ACTIVE profile auto-selects the remaining printer and rebinds (no dead no-active state); deleting the LAST profile falls to the Connect prompt (D-12/D-14/D-11 confirmed). _(The delete write also runs on the durable `writeScope` now, so the auto-pick persists reliably — see Gaps.)_

---

### Item 6 — Survival across process death (SC-3)

Force-stop the app after selecting a printer; relaunch → it reconnects to the **SAME** printer.

- **Expected:** a cold relaunch (after force-stop) lands back on the printer that was active before the kill (the persisted `active_id` survives). Instrumented happy-path is already GREEN; this confirms it end-to-end on the real shell.
- **result:** ✅ **PASS** — force-stop after selecting a printer → relaunch reconnects to the SAME printer (SC-3 confirmed end-to-end on the real shell, in addition to the instrumented `ProfileSurvivesRestartTest` happy-path GREEN at `7bb52dc`).

---

## Result summary

| Item | Behavior | Requirement | Result |
|------|----------|-------------|--------|
| 1 | Fresh start + add both printers | D-07 / D-11 / D-10 | ✅ PASS |
| 2 | Switch + drive each, no re-entry | SC-4 / D-03 / D-08 | ✅ PASS (post-fix `781277f`) |
| 3 | No-churn on theme edit | Pitfall 1 | ✅ PASS |
| 4 | Mid-print switch | D-04 | ⚪ NOT RUN — owner-deferred (not blocking) |
| 5 | Delete active + delete last | D-12 / D-14 / D-11 | ✅ PASS |
| 6 | Survival across process death | SC-3 | ✅ PASS (also instrumented `7bb52dc`) |

**Counts:** 5 PASS · 1 owner-deferred (item 4) · 0 FAIL. Gate **PASSED** (2026-06-04). The one FAIL caught on
the first run (item 2 intermittent revert) was root-caused and fixed in `781277f`, then re-verified PASS.

---

## Gaps

### G-1 — Headline switch INTERMITTENTLY reverts to the old printer; active (⚡) marker doesn't update (BLOCKING, SC-4) — RESOLVED

- **Symptom (first run, on-device):** tapping a printer in the Devices switcher intermittently reverted to
  the previously-active printer and the active-marker did not move. Roughly **4 switch attempts produced only
  ONE new `sessionInstanceId` rebind** (confirmed live via logcat) — most taps silently dropped.
- **Root cause (code-confirmed):** every profile-persistence write ran on a `rememberCoroutineScope()`
  (composition-scoped). The Devices switch (and Settings save/delete) **navigate away in the SAME frame**, so
  the composition tears down and its scope is cancelled mid-write. On the Nexus 7's slow flash, the DataStore
  `.tmp → rename` lost the race and the active-id write was silently dropped → `activeConfig` never emitted →
  no spine rebind. (A fast device would mostly win the race and hide this — the project's recurring
  **mock-vs-reality** class: green unit tests + fast hardware mask a write-cancellation bug that only the
  real slow-flash device reliably surfaces.)
- **Fix (commit `781277f`, production code):** `AppContainer` now owns a **process-lifetime `writeScope`**
  (`SupervisorJob` + `Dispatchers.IO`) and exposes durable `setActiveProfile(id)` / `saveProfile(profile)` /
  `deleteProfile(id)`. All UI write call-sites were converted to these methods: DevicesScreen switch;
  SettingsScreen save, delete, clear-key, and the active-profile theme-persist branch. The mDNS scan and the
  idle/global-theme-prefs branch legitimately stay composition-scoped (they don't race a navigation). Compile
  + full `:app:testReleaseUnitTest` GREEN; debug APK rebuilt and reinstalled on flox.
- **Re-verification:** item 2 (and 1/5/6) re-run PASS on flox + both live printers — the active marker now
  moves on every tap and the switch lands every time ("switch works as fast as I can navigate").

---

**Resume signal:** all six items reported — 5 PASS + item 4 owner-deferred (not blocking). Matthew approved
the gate after the `781277f` re-run ("switch works as fast as I can navigate the screens to do it").

---

_Hands-on run by Matthew on flox + live Ender 5 Plus and Ender 3 Pro, 2026-06-04. Gate PASSED after the
`781277f` durable-writeScope gap-closure._
