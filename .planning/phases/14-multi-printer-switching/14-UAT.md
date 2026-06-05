# Phase 14-06 — On-Device UAT (Multi-Printer Switching)

**Device:** flox (Nexus 7 2013, LineageOS 18.1 / Android 11, Adreno 320 / 2GB / armeabi-v7a).
**Build:** _(orchestrator records the installed APK + commit here at UAT time.)_
**Printers:** **Ender 5 Plus** Moonraker @ **192.168.1.120:7125** and **Ender 3 Pro** @ **192.168.1.121:7125** (both klicky probes).
**Run date:** _(pending)_
**Status:** ⬜ **PENDING** — blocking live two-printer hands-on UAT (success criterion 4).

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
- [x] Full `:app:testDebugUnitTest` GREEN (final phase regression).
- [ ] Debug (or signed release) APK built via `E:\Android\gw.bat` and installed on flox (orchestrator).
- [ ] Both printers powered + reachable (E5+ 192.168.1.120:7125, E3 192.168.1.121:7125).

---

## The six UAT items

### Item 1 — Fresh start + add both printers (D-07 / D-11 / D-10)

First launch with 0 profiles → the Connect prompt routes to Settings. Add the **E5+** (host/port,
optional API key, give it a name + a distinct theme/accent). Add the **E3** (name + a DIFFERENT
theme/accent). Confirm each appears as a profile row.

- **Expected:** empty store → Connect prompt → Settings; both printers added; each shows as a profile row with its own name/theme.
- **result:** [pending]

---

### Item 2 — Switch + drive each, no re-entry (SC-4 / D-03 / D-08)

Open the **Devices** tile (drawer subtitle shows the active printer's name, D-03). Tap the **E5+** →
recovery Splash → lands on the E5+ Status; drive a control (a jog or a temp set). Tap the **E3** → Splash
→ E3 Status; drive a control. Neither switch re-asks for host/port/key. The whole app look changes per
printer (D-08).

- **Expected:** each tap → Splash → that printer's Status; a control action works on each; NO re-entry of host/port/key; the full theme flips per printer.
- **result:** [pending]

---

### Item 3 — No-churn on theme edit (Pitfall 1 / `distinctUntilChanged`)

With one printer active, change its accent in Settings → confirm **NO** recovery Splash fires (the
connection must not bounce).

- **Expected:** an accent/theme edit on the active profile does NOT trigger a recovery Splash / reconnect — `distinctUntilChanged` suppresses the config churn.
- **result:** [pending]

---

### Item 4 — Mid-print switch (D-04)

With a print running on one printer, switch to the other and back — the print continues untouched.

- **Expected:** switching away from (and back to) a printing machine leaves its print running; the tablet just changes which printer it watches.
- **result:** [pending]

---

### Item 5 — Delete active + delete last (D-12 / D-14 / D-11)

Delete the **ACTIVE** profile via the Settings ConfirmGuard → confirm the app auto-selects the remaining
printer and rebinds (no dead no-active state). Then delete the **last** profile → falls to the Connect
prompt (D-11).

- **Expected:** deleting the active profile auto-picks another remaining profile + rebinds (Splash → its Status); deleting the last profile → Connect prompt. Both deletes go through the full-screen Confirm guard.
- **result:** [pending]

---

### Item 6 — Survival across process death (SC-3)

Force-stop the app after selecting a printer; relaunch → it reconnects to the **SAME** printer.

- **Expected:** a cold relaunch (after force-stop) lands back on the printer that was active before the kill (the persisted `active_id` survives). Instrumented happy-path is already GREEN; this confirms it end-to-end on the real shell.
- **result:** [pending]

---

## Result summary

| Item | Behavior | Requirement | Result |
|------|----------|-------------|--------|
| 1 | Fresh start + add both printers | D-07 / D-11 / D-10 | [pending] |
| 2 | Switch + drive each, no re-entry | SC-4 / D-03 / D-08 | [pending] |
| 3 | No-churn on theme edit | Pitfall 1 | [pending] |
| 4 | Mid-print switch | D-04 | [pending] |
| 5 | Delete active + delete last | D-12 / D-14 / D-11 | [pending] |
| 6 | Survival across process death | SC-3 | [pending] |

**Resume signal:** report PASS/FAIL per item. Type **"approved"** if all six pass, otherwise describe the
failures (each FAIL spawns a gap-closure plan; the phase is NOT marked complete on a FAIL).

---

_Pending hands-on run by Matthew (flox + live E5+ and E3). 14-06-SUMMARY is written ONLY after this UAT records PASS._
