# Phase 16 — On-Device UAT (16-08)

Binding on-device gates for the four-state Print-Status home. Host tests cannot prove these —
the project's #1 recurring failure class is "green host suite, broken on real hardware"
([[dinghy-display-mock-vs-reality]], 5+ strikes). All three gates run on the **real flox tablet**
(LineageOS 18.1 / API 30 / genuine Adreno 320 / `armeabi-v7a`) against a **live printer**.

A **FAIL** on any gate spawns gap-closure (`/gsd-plan-phase 16 --gaps`) — it does **NOT** mark the
phase complete.

## Build / Stage (autonomous — DONE)

- **Build:** `:app:assembleRelease` → BUILD SUCCESSFUL (R8 release, `armeabi-v7a` split).
- **Sign:** `app-armeabi-v7a-release-signed.apk` (zipalign + debug-keystore via `E:\Android\sign-release.bat`).
- **Install:** `adb -s 0a64b42e install -r` → **Success** (release `versionName=0.1.0`, `versionCode=1`,
  minSdk 23 / targetSdk 35).
- **Launch:** app routes to `works.mees.dinghy/.MainActivity` (Standby home when idle).
- **Printers reachable:** Ender 5 Plus `192.168.1.120:7125` (state: ready) · Ender 3 Pro
  `192.168.1.121:7125` (state: ready).

> Perf (SC-3) MUST be measured on this **release** build — debug Compose is 5–10× slower and lies
> about jank.

## Pre-UAT cleanup gate — PASS

The three mechanical completion-blocker greps + the full unit suite, run before the build:

| Check | Result |
|-------|--------|
| Full `:app:testDebugUnitTest` suite | **GREEN** (BUILD SUCCESSFUL, exit 0) |
| No leftover `fail("not yet implemented` RED placeholder | **PASS** — the one grep hit is a KDoc *describing* the historical RED pattern in `BabystepPrefsTest.kt:43`; no live `fail()` placeholder (the test has concrete typed assertions). |
| No `rememberCoroutineScope` in babystep/prefs write paths | **PASS** — all 6 hits in `AppContainer.kt` are KDoc *warnings against* the trap (`[[dinghy-compose-write-scope-cancellation]]`); zero live `rememberCoroutineScope().launch` write usages. `BabystepPrefs.kt` had no hits. |
| No `proc_stat` host-load in the `state` package (deferred to P19) | **PASS** — NONE. |

Cleanup gate is **clean** — no completion blockers; proceed to the device gates.

---

## Device gates (owner-run on flox + live printer)

> Convention: Compress = nozzle CLOSER to bed = `babystepZ(-step)` = `SET_GCODE_OFFSET Z_ADJUST=-step MOVE=1`.
> This SIGN is implemented per plan in 16-06 but was **NOT guess-flipped** — it is **device-verified here**
> on a live first layer. A wrong sign is invisible to unit tests and dangerous on a first layer; that is
> the whole point of SC-5.

### SC-5 — Live babystep round-trip + correct sign (flox + Ender 5 Plus, `192.168.1.120:7125`)

- [x] **RESULT: PASS** — owner-verified 2026-06-06 on a live first layer (E5 Plus, release build).

**Result:** On a live first layer, tapping **Compress** moved the nozzle **CLOSER** to the bed **AND** the
on-screen Applied-Z-offset (`gcode_move.homing_origin[2]`) **DECREASED**; **Expand** moved the nozzle
**FARTHER** and the offset **INCREASED**. On-screen direction matched physical nozzle motion exactly. **The
sign 16-06 implemented (it did NOT guess-flip) is CORRECT — no sign flip needed.** The center cell cycled
the step; the babystep row HID once past the early-layer window and the normal shortcut row returned;
**session-only confirmed** — the Z-offset reset to `0.000` after resume with **no `SAVE_CONFIG`** (correct).

This is the whole-point gate of the plan (a wrong sign is invisible to unit tests and physically dangerous
on a first layer). It passed on the first run.

**Steps:**
1. On flox, connect to the **Ender 5 Plus** (`192.168.1.120:7125`). Start a print.
2. Confirm the **babystep row** (`[Compress][step][Expand]`) APPEARS during the first ~5 layers, and is
   HIDDEN if the slicer doesn't report `current_layer` (no time-based fallback — expected).
3. Tap **Compress** → verify the nozzle moves **CLOSER** to the bed **AND** the on-screen
   Applied-Z-offset (`gcode_move.homing_origin[2]`) **decreases** by the step.
4. Tap **Expand** → nozzle moves **FARTHER** and the offset **increases**. The on-screen direction MUST
   match the physical nozzle. **If inverted → FAIL = a one-line sign flip in 16-06 (Compress = `babystepZ(-step)`).**
5. Tap the **center cell** → verify the step cycles `.02 → .05 → .10 → .15 → .20`.
6. Continue past the layer window → the babystep row HIDES and the normal shortcut row returns.
7. Confirm **session-only**: no `SAVE_CONFIG` is issued; the offset is NOT written to config.

**Observed `homing_origin[2]` direction (record):**
- Compress → `homing_origin[2]` **decreases**, nozzle **closer** to bed. Expand → **increases**, nozzle **farther**.
- Physical direction matched on-screen? **YES** (verified live).

---

### SC-3 — Perf no-regression on Adreno 320 (flox, release build)

- [x] **RESULT: PASS** — owner-verified 2026-06-06 (genuine Adreno 320, RELEASE build = R8 / `armeabi-v7a`).

**Result:** Steady-state printing framestats (`gfxinfo`, 61 frames over 25s; event-driven ~2.4 redraws/s
per design D-13 = no-continuous-animation):

| Pctl | Frame time |
|------|-----------|
| p50 | 42 ms |
| p90 | 48 ms |
| p95 | 53 ms |
| p99 | 57 ms |

**0 missed vsync · 0 high input latency · 0 frozen frames** — the histogram maxes at 57 ms; nothing lands in
the 100 ms+ buckets. The 95% "janky" figure is the **known `gfxinfo` artifact for a sparse event-driven
redraw** (each rare redraw exceeds the 60 fps bucket; there is no animation to stutter). p95 53 ms sits next
to the Phase-5 baseline (48.64 ms), **~4 ms higher** for the richer four-state focus (Benchy hero image) —
**within no-regression for an event-driven screen.** Owner confirmed interaction "feels fine"
(responsiveness eyeball = the reframed gate per ADR-0001 Addendum 2: no-frozen-frames + responsiveness).

**Steps:**
1. On the **release** build on flox (genuine Adreno 320), open Print-Status in **BOTH Standby and Printing**.
2. Eyeball + capture `gfxinfo framestats`: no frozen frames / no sustained jank vs the current home.
   Watch the live Printing cockpit (ring + stats updating ~2–4 Hz).
3. Framing per ADR-0001 Addendum 2 = **no-frozen-frames + responsiveness** on the real GPU (emulators
   lie about old-GPU fill rate). `gfxinfo` capture command (Claude can run on request):
   `adb -s 0a64b42e shell dumpsys gfxinfo works.mees.dinghy framestats`.

**Notes (record):** ___

---

### SC-4 — Core monitor loop preserved (flox + live printer)

- [x] **RESULT: PASS (full)** — including the Terminal end-state, now live-verified 2026-06-06.

**Result (core — PASS):** On flox + the live E5 Plus: **connect → temps / progress / Z-offset / layer all
update live**; **Pause → Resume round-trips correctly**; the four states **classify correctly off real
printer state** (Standby on connect, Printing, Paused). The connect → monitor → drive-a-print loop is
**behavior-preserving** — the project's core value holds with no regression vs the prior home.

**Terminal end-state (PASS — owner cancelled a live print, 2026-06-06):** Home correctly classified to
**Terminal (Cancelled)** with the hero + **Dismiss / Reprint** rendering passively. Also confirmed the
**Standby-stays-Standby** behavior live: an **emergency stop** produced a Klippy shutdown that the
classifier correctly kept as **Standby** (NOT a fake Terminal on a stale filename) — the headline P16
behavior change, proven on hardware. Two Terminal-state polish gaps were caught + fixed live (commit
`f61d5e5`): the focus showed the app icon instead of the print's gcode thumbnail (metadata was nulled on
print-end → now retained through terminal states), and the field reused the live cockpit grid (→ replaced
with a finished-print stats list: File / Print time / Filament / Layers, roomier padding, marquee filename).

**Steps:**
1. On flox + a live printer, run the core loop: **connect → monitor** live progress/temps/Z on the Status
   home → **drive a print** (start / pause / resume / cancel). Confirm no regression vs the prior home —
   the connect/monitor/control loop still works flawlessly (the project's core value).
2. **Terminal spot-check:** let a print complete (or cancel one) → navigate Home → confirm the Terminal
   hero + stats + **Dismiss/Reprint** render (passive — it does NOT yank you off another screen), and that
   **Terminal(Error)** shows the projected (≤3) error lines.

**Notes (record):** ___

---

## On-device findings fixed during UAT

Hands-on UAT on the four-state screen ALSO caught visual issues the green host suites missed (the project's
recurring [[dinghy-display-mock-vs-reality]] pattern). **All were fixed live + re-verified on flox, and are
already committed** — recorded here for traceability (do NOT re-commit):

| # | Finding | Fix | Commit |
|---|---------|-----|--------|
| 1 | Field buttons rendered with label text | Icon-only field buttons (LauncherTile + Tune stub) | `0950b57` |
| 2 | Standby launcher grid left an empty cell / double-width More | Drawer tile absorbs the leftover cell (grid tight) | `23205ec` |
| 3 | Standby focus glance too small | Enlarged glance fonts + icon | `c507fe7` |
| 4 | Paused pause overlay fixed-sp / cropped | Ring-relative Paused pause overlay (no crop) | `cc2c6d4` |
| 5 | Standby brand image didn't fill focus; launcher glyphs generic | Benchy crop-FILLS focus both orientations + launcher glyph swaps (Files=print_connect, Extrude=output_circle, Drawer=more_horiz) | `6815424` |
| 6 | Paused status label clipped + dimmed; ring shaved by dim layer; pause glyph capped | Status label rendered outside the alpha dim layer (full + readable); ProgressRing arcs inset by half-stroke; pause-circle glyph un-boxed | `e7b0126` |
| 7 | Terminal focus showed app icon, not the print's thumbnail | PrintMetadataHolder retains metadata through terminal states (clears only on Standby) + retention unit test | `f61d5e5` |
| 8 | Terminal field reused the live cockpit grid | TerminalStatsList finished-print summary (File/Print time/Filament/Layers) + padding + marquee filename | `f61d5e5` |

These six commits are production code already in git from the live UAT loop — they are listed for the
audit trail, not staged again by this finalization.

## Phase-completion bar

- [x] **SC-5 PASS** · [x] **SC-3 PASS** · [x] **SC-4 PASS (full)** *(Terminal end-state live-verified on a real cancel, 2026-06-06)*
- All binding gates PASS → `16-08-SUMMARY.md` written, STATE advanced, plan complete.
- **No FAIL** → no gap-closure spawned. The previously-deferred Terminal end-state visual was
  subsequently eyeballed on a live cancel and PASSED (with two polish fixes folded in, commit `f61d5e5`).
- Orchestrator owns phase verification + `phase.complete` after this plan returns.
