# Phase 12-05 — On-Device UAT (Macro Prompt Protocol)

**Device:** flox (Nexus 7 2013, LineageOS 18.1 / Android 11, Adreno 320 / armeabi-v7a).
**Build:** `app-armeabi-v7a-debug.apk` (debug-signed), installed via `adb install -r` from
commit `6a6a072` (12-05 Task 1 wiring).
**Status:** ☐ AWAITING — Matthew runs the four gates against a live printer.

---

## Prereqs (orchestrator — DONE except the printer-side cfg)

- [x] AppShell wiring + carve-out docs committed (`6a6a072`); full `:app:testDebugUnitTest` GREEN.
- [x] Debug APK built and installed on flox.
- [ ] **Printer side (Matthew or orchestrator):** copy
      `klipper-macro-prompt-protocol/fixtures/macro-examples.cfg` into the printer's config (e.g.
      `[include macro-examples.cfg]` in `printer.cfg`) on **E5+ (192.168.1.120:7125)** or
      **E3 (192.168.1.121:7125)**, then `FIRMWARE_RESTART` so the `MPP_*` / `_MOVE_*` / `_SELECT_SPOOL`
      macros register. (Optional, for the image gate: drop `spool.svg`/`nozzle.png` under
      `config/prompt-assets/` — SVG falls to alt-text by design; a PNG renders.)
- [ ] Launch Dinghy on flox, connect to that printer.

> The macros are triggered from a second client's console (Mainsail/Fluidd/KlipperScreen) OR from
> Dinghy's own **Macros** drawer tile (the `MPP_*` macros appear there once registered). The prompt
> overlay then floats over whatever Dinghy screen is showing.

---

## The four gates

### Gate 1 — SC-4: full prompt driven start-to-finish from the tablet (PROMPT-04)

The fixture has no single monolithic "load-filament wizard"; it is a catalog of protocol exercisers.
Drive the **button/group/live-append/row** path that proves end-to-end button-gcode firing:

1. Trigger **`MPP_BUTTON_GROUP`** (the "Jog Distance" prompt: a button_group of `+10 / +1 / -1 / -10`,
   each firing a `_MOVE_*` macro).
   - CONFIRM the PromptDialog appears **over the current Dinghy screen** with the title "Jog Distance"
     and the four buttons laid out as **equal-width cells** in a row (the button_group).
   - Tap each button (`+10`, `+1`, `-1`, `-10`). CONFIRM the printer **reacts** (the toolhead jogs / a
     `_MOVE_*` macro runs — watch the printer or the Console). A button nested in the group must fire
     its OWN gcode, not the wrong neighbor (the flatten-index contract).
2. Trigger **`MPP_LIVE_APPEND_AFTER_SHOW`** ("Live Status"): CONFIRM the prompt shows, then **live-appends**
   content after `prompt_show` (the overlay updates in place without re-opening).
3. Trigger **`MPP_ROW_LAYOUT`** ("Spool Row"): CONFIRM the image + markup + **Select** button render on
   ONE row as equal cells; tap **Select** → `_SELECT_SPOOL` runs.
4. Trigger **`MPP_BUTTON_FIELD_DEFAULTS`** ("Button Defaults"): CONFIRM the three content buttons + the
   **Done** footer button render; a **footer** Done tap and a **content** button tap fire INDEPENDENTLY
   (no same-index collision — content `buttonKey` vs footer `footerKey`).

**Record:** ☐ PASS / ☐ FAIL — _detail:_

---

### Gate 2 — SC-3: Close control → `prompt_end` echo round-trip

1. With any prompt open (e.g. `MPP_CORE_BASIC`), tap the **Close** control (always-present, accent, bottom).
2. CONFIRM the overlay closes **because the dispatched `action:prompt_end` ECHOED back** through the
   gcode stream and the reducer closed it — **NOT** an instant local teardown.
   - Watch Dinghy's **Console** (or a second client): the `// action:prompt_end` line should appear, and
     the overlay closes when it arrives. A perceptible (network-latency) close, not a zero-latency one,
     is the tell. System **Back** does the same (it dispatches the same `prompt_end`).

**Record:** ☐ PASS / ☐ FAIL — _detail:_

---

### Gate 3 — D-10: disconnect closes locally with NO `prompt_end` (cross-client safety)

1. Open a prompt on Dinghy (e.g. `MPP_CORE_BASIC`). **Also** open the SAME (or any) prompt on a SECOND
   client — Mainsail/Fluidd/KlipperScreen, or a second Dinghy — if available.
2. **Drop the Dinghy connection:** yank flox's Wi-Fi (or kill the printer link).
3. CONFIRM:
   - The Dinghy overlay **closes locally** (Dinghy goes to the Syncing/Unreachable Splash), AND
   - the prompt **stays OPEN on the other client** — i.e. Dinghy emitted **NO** `prompt_end`.
   - If no second client is available: confirm via the printer **Console** that **no** `action:prompt_end`
     line was emitted by Dinghy on the disconnect.

**Record:** ☐ PASS / ☐ FAIL — _detail:_

---

### Gate 4 — SC-2: `prompt_image` bounding / fill-rate on Adreno-320

1. Trigger **`MPP_IMAGE_ALT_SCALE`** ("Image Prompt") — and/or **`MPP_INVALID_IMAGE_PATHS`**.
2. CONFIRM on the real flox (Adreno-320, 2GB):
   - The image renders **bounded/downscaled** (a square ≈ 1/3 width × scale) with **no jank/OOM**; the
     rest of the prompt still renders.
   - An SVG (`spool.svg`) or a bad/rejected path shows **alt text**, never a crash (SVG → alt-text is
     by design — coil-svg isn't bundled). A PNG (`nozzle.png`) decodes if present.

**Record:** ☐ PASS / ☐ FAIL — _detail:_

---

## Result summary

| Gate | Behavior | Requirement | Result |
|------|----------|-------------|--------|
| 1 | Prompt driven start-to-finish from the tablet (buttons fire gcode) | SC-4 / PROMPT-04 | ☐ |
| 2 | Close → `prompt_end` echo round-trip closes overlay | SC-3 / PROMPT-03 | ☐ |
| 3 | Disconnect closes locally, NO `prompt_end` (cross-client) | SC-3 / D-10 | ☐ |
| 4 | `prompt_image` bounded, no jank/OOM | SC-2 | ☐ |

**A FAIL on any gate** captures the runtime evidence (logcat / the actual stream behavior) and spawns a
gap-closure plan — do **not** mark the phase complete on a fail. The 12-05-SUMMARY records these results
once reported.

---

_Awaiting Matthew's hands-on results. Resume signal: type "approved" with the 4 results, or describe the
failing gate(s) for gap-closure._
