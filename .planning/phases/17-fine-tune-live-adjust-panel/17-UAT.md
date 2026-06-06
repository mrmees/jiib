# Phase 17 — On-Device UAT (Fine-Tune / Live-Adjust)

**Plan:** 17-06 (Task 2 — `checkpoint:human-verify`, blocking)
**Status:** PENDING — awaiting Matthew on flox + a live printer
**Build:** debug `app-armeabi-v7a-debug.apk` installed on flox (`0a64b42e`) from commit `2c4cbc4`
**Device:** flox (LineageOS 18.1 / API 30, genuine Adreno 320 / 2GB / 1920×1200, `armeabi-v7a`)
**Printers (Moonraker):** Ender 5 Plus = `192.168.1.120:7125` · Ender 3 Pro = `192.168.1.121:7125`

## Scope & ground rules

- **Motion + Extrusion ONLY.** FW-Retraction is **build-blind** on both dev printers (neither exposes a
  `[firmware_retraction]` object) — it is EXPLICITLY EXCLUDED from on-device UAT (covered by the 17-03
  synthetic-fixture reducer test + the 17-05 gate + the compile-checked `onFwRetraction` wire). Expect NO
  FW-retraction entry on the Extrusion screen on these printers (that ABSENCE is itself check 3 / SC-2).
- **State-flip, not optimistic.** "Tile flips" means the tile's REPORTED value updates to the commanded
  value after the printer object actually changes — not an instant local echo. The whole group stays
  busy/dimmed until the value flips (D-15).
- **Perf reframe (ADR-0001 Add.2):** judge on **no frozen frames + responsiveness**, NOT the 95%-janky
  gfxinfo artifact (these are sparse event-driven static value-tile screens — LOW perf risk).
- **On-device iteration model:** Claude built + installed; **Matthew navigates + eyeballs** the result and
  records PASS/FAIL below. A FAIL spawns a gap-closure plan — the phase is NOT marked complete on a FAIL.

## Pre-flight (Matthew)

1. Open Dinghy Display on flox; connect to a live printer (E5 `192.168.1.120:7125` for the mid-print checks,
   or E3 `192.168.1.121:7125`).
2. For the state-flip-during-print checks (2 & 3), **start a real print on E5** (ROADMAP SC-4) so speed/flow
   changes have a visible physical effect. The Standby/idle path still proves the tiles flip; the live print
   proves the physical effect + mid-print availability.

## Checks

> Record `PASS` / `FAIL` + notes per check. Leave PENDING until run.

### 1 — Entry + reset-to-Hub (TUNE-01 / D-21 / REVIEW #6) — PENDING
- Tap the **Print-Status Tune button** (the first/flexible tile in the active-print shortcut row, sliders
  glyph) → the **Fine-Tune Hub** opens. Hub shows **Motion + Extrusion** entries, **no summary values** (D-20).
- Navigate into **Motion**, **Back** to the Hub, leave Fine-Tune (Back again), then **re-enter via the Tune
  button** → it opens the **HUB** again, NOT the Motion group page (REVIEW #6 — reset-to-Hub on entry).
- Repeat the re-entry check via the **drawer "Fine-Tune" tile** (swipe up → Fine-Tune): also opens the Hub.
- **Expected:** Tune (and the drawer tile) always land on the Hub; group sub-nav never shows a stale page.

### 2 — Motion state-flip + perf (SC-1 / SC-4 / Open-Q2) — PENDING
- On **Motion**, nudge **Speed % ±5** → the tile's reported value **flips** to the commanded value (not
  instant-optimistic) and the print **visibly speeds/slows**.
- Nudge **Max accel ±100** → the tile value **flips**.
- Confirm **5 tiles** show (Speed, Max velocity, Max accel, Min cruise, Square-corner velocity), each with
  **≥64px** targets, and the screen is usable in **BOTH portrait and landscape** (Open-Q2).
- **Expected:** every nudge flips the reported tile value; physical effect visible on Speed; layout works
  both orientations.

### 3 — Extrusion state-flip + cold-enable + capability-gating (D-02 / SC-2 / REVIEW #5) — PENDING
- On **Extrusion**, nudge **Flow % ±1** → tile **flips** + physical effect; nudge **Pressure advance ±0.001**
  → tile **flips**; nudge **Part-cooling fan ±5%** → the `fan.speed` readout **flips**.
- Confirm **Flow and PA tiles are ENABLED even when the extruder is COLD** (D-02 / REVIEW #5 — no cold guard).
- Confirm the **FW-retraction entry is ABSENT** (neither dev printer exposes it — capability-gated, SC-2).
- **Expected:** all three flip; Flow/PA usable cold; no FW-retraction entry.

### 4 — Long-press reset (TUNE-05 / D-16 / REVIEW #3) — PENDING
- **Long-press the Speed value** → resets to **100%**; **long-press the Max-accel value** → resets to the
  **config baseline**.
- Confirm the **part-fan tile has NO reset long-press** (`onReset = null`, REVIEW #3), and any tuner whose
  baseline is absent likewise has **no reset** (no-op, nothing happens).
- **Expected:** Speed→100%, Max accel→baseline; part-fan + baseline-absent tuners do not reset.

### 5 — Whole-group busy-lock holds until state-flip (D-15 / REVIEW #2) — PENDING
- **Rapid-nudge** a tile → the **whole group dims/disables** and **STAYS busy** until the tile's reported
  value actually **flips to the commanded target** (state-flip, not just the ack), then re-enables.
- **Expected:** group locks on dispatch; unlocks only when the value reaches target (or on failure).

### 6 — Reject path (G1 / T-17-06-01) — PENDING
- Nudge a value **Klipper rejects** (or push beyond range) → a **non-fatal error toast**, the readout does
  **NOT move**, the **busy-lock releases**, **no crash** (G1 lesson — RpcError must not be uncaught).
- **Expected:** toast + no readout move + lock releases + app stays up.

### 7 — Perf (SC-3 / ADR-0001 Add.2) — PENDING
- While nudging on Motion/Extrusion, observe responsiveness (and optionally capture `gfxinfo framestats`).
- Judge on **no frozen frames + responsive** (NOT the 95%-janky gfxinfo artifact). Static value-tile
  screens, LOW perf risk.
- **Expected:** no frozen frames; taps respond promptly.

### 8 — Instrumented nav test on-device (REVIEW #8, where feasible) — PENDING
- Run on flox:
  `:app:connectedDebugAndroidTest --tests 'works.mees.dinghy.ui.finetune.FineTuneNavTest'`
  (host-side via `E:\Android\gw.bat`).
- **Expected:** GREEN (Tune → Dest.FineTune Hub + reset-to-Hub on re-entry).

## Results

| Check | Result | Notes |
|-------|--------|-------|
| 1 Entry + reset-to-Hub | PENDING | |
| 2 Motion state-flip + perf | PENDING | |
| 3 Extrusion + cold + cap-gate | PENDING | |
| 4 Long-press reset | PENDING | |
| 5 Busy-lock holds to state-flip | PENDING | |
| 6 Reject path | PENDING | |
| 7 Perf no-frozen-frames | PENDING | |
| 8 Instrumented nav test on-device | PENDING | |

**Overall:** PENDING — A FAIL on any check spawns a gap-closure plan (NOT phase-complete).

## Known limitation (recorded per T-17-06-02)

FW-Retraction is shipped **untested on hardware that has it** — build-blind on both dev printers (E5/E3).
It is covered by the 17-03 synthetic-fixture reducer test, the 17-05 holder gate, and the explicit
compile-checked `onFwRetraction` wire (REVIEW #4). To be validated opportunistically on a printer that
reports a `firmware_retraction` object.
