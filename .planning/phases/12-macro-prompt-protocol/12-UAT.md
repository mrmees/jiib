# Phase 12-05 — On-Device UAT (Macro Prompt Protocol)

**Device:** flox (Nexus 7 2013, LineageOS 18.1 / Android 11, Adreno 320 / 2GB / armeabi-v7a).
**Build:** `app-armeabi-v7a-debug.apk` (debug-signed), installed via `adb install -r` from
commit `6a6a072` (12-05 Task 1 wiring).
**Printer:** **Ender 3 Pro** Moonraker at **192.168.1.121:7125**. (The Ender 5 Plus at
192.168.1.120 was unusable — Klipper in MCU-error state: `mcu 'EBBCan': Unable to connect`.)
**Run date:** 2026-06-04.
**Status:** ☑ **PASSED** — all four gates PASS on flox + the live E3.

---

## UAT method (novel — recorded for the mock-vs-reality ledger)

Rather than installing `macro-examples.cfg` + `FIRMWARE_RESTART`, the orchestrator **streamed the
protocol command sequences directly to Moonraker's REST `/printer/gcode/script`** as
`RESPOND TYPE=command MSG="action:prompt_*"` lines. Moonraker broadcasts the resulting `// action:*`
responses via `notify_gcode_response` to **all** websocket clients (including Dinghy), which parsed +
rendered them exactly as it would a real macro's output. Matthew viewed/tapped on the real flox tablet.

- **Safe button gcodes:** button gcodes that referenced the uninstalled `_MOVE_*` helper macros were
  substituted with **non-motion `M118` echoes** (e.g. `M118 DINGHY_BTN move +10`) so taps were SAFE
  (no toolhead motion) AND server-verifiable.
- **Objective server-side evidence:** every button dispatch was cross-checked against Moonraker's
  `/server/gcode_store` for which gcode actually fired — directly answering the project's recurring
  mock-vs-reality concern (a tap → a confirmed server-side echo, not just a UI animation).
- **Image gate:** two test images (`nozzle.png`, `spool.svg`) were uploaded to E3
  `config/prompt-assets/` and **removed afterward** (cleanup done).

---

## Prereqs

- [x] AppShell wiring + carve-out docs committed (`6a6a072`); full `:app:testDebugUnitTest` GREEN.
- [x] Debug APK built and installed on flox (`61ed7eb` scaffolded this script).
- [x] Protocol command sequences streamed to E3 via `/printer/gcode/script` (method above);
      `notify_gcode_response` broadcast drove Dinghy's overlay.
- [x] Dinghy launched on flox, connected to the E3.

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

**Record:** ☑ **PASS** — _detail:_
- `MPP_BUTTON_GROUP` "Jog Distance" rendered as an equal-width row of `+10 / +1 / -1 / -10`. Tapping
  all four dispatched their `M118` echoes in **EXACT order** (server `gcode_store` confirmed) — the
  flatten-index contract holds: right button → its OWN gcode, no neighbor misfire, no collision for a
  button nested in a `button_group`.
- **Content-vs-footer independence:** content[0] "Content Hello" → `M118 DINGHY_CONTENT hello`,
  content[1] "Unknown Style" (`neon` → fallback) → `M118 DINGHY_CONTENT unknown`, footer[0] "Done" →
  `M118 DINGHY_FOOTER done`. Distinct dispatches from the **same index 0** prove separate
  `buttonKey`/`footerKey` namespaces (the same-index-collision guard). The unknown style rendered with a
  sane default + still fired.
- **Live-append:** "Live Status" showed "Heating nozzle…", then `prompt_text`/`footer_button` after
  `prompt_show` updated the SAME overlay in place (added "Nozzle ready." + Continue) — no reopen/flicker.
- **Row layout:** "Spool Row" rendered image(alt) + markup (**Blue PLA** bold / 215 C smaller) + Select
  on ONE row as equal cells.

---

### Gate 2 — SC-3: Close control → `prompt_end` echo round-trip

1. With any prompt open (e.g. `MPP_CORE_BASIC`), tap the **Close** control (always-present, accent, bottom).
2. CONFIRM the overlay closes **because the dispatched `action:prompt_end` ECHOED back** through the
   gcode stream and the reducer closed it — **NOT** an instant local teardown.
   - Watch Dinghy's **Console** (or a second client): the `// action:prompt_end` line should appear, and
     the overlay closes when it arrives. A perceptible (network-latency) close, not a zero-latency one,
     is the tell. System **Back** does the same (it dispatches the same `prompt_end`).

**Record:** ☑ **PASS** — _detail:_ Close control dispatched
`RESPOND TYPE=command MSG="action:prompt_end"`; the `// action:prompt_end` echoed back through the
stream and the reducer closed the overlay **on the echo** (server-confirmed dispatch+echo pair) — NOT
an instant local teardown.

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

**Record:** ☑ **PASS** — _detail:_ Prompt opened on **BOTH** flox and Mainsail
(`http://192.168.1.121/`). Dropped flox Wi-Fi → Dinghy closed the overlay **locally** and dropped to
the standby/Syncing splash, while **Mainsail kept the prompt open**. Server `gcode_store` confirmed
`prompt_end emitted after disconnect: False` — Dinghy broadcast **nothing** on the local-disconnect edge.

---

### Gate 4 — SC-2: `prompt_image` bounding / fill-rate on Adreno-320

1. Trigger **`MPP_IMAGE_ALT_SCALE`** ("Image Prompt") — and/or **`MPP_INVALID_IMAGE_PATHS`**.
2. CONFIRM on the real flox (Adreno-320, 2GB):
   - The image renders **bounded/downscaled** (a square ≈ 1/3 width × scale) with **no jank/OOM**; the
     rest of the prompt still renders.
   - An SVG (`spool.svg`) or a bad/rejected path shows **alt text**, never a crash (SVG → alt-text is
     by design — coil-svg isn't bundled). A PNG (`nozzle.png`) decodes if present.

**Record:** ☑ **PASS** — _detail:_
- `nozzle.png` decoded and rendered as a **bounded ~half-cell square** (scale 0.5), **no jank/OOM** on
  the real Adreno-320 flox.
- SVGs (`spool.svg` / `valid.svg`) → **alt text** by design (coil-svg not bundled).
- `MPP_INVALID_IMAGE_PATHS`: absolute `/tmp/…`, home `~/…`, and `config/../secret.svg` parent-traversal
  **all rejected** → alt text (the V5 path allow-list, T-12-02). "Still renders." confirmed the prompt
  survived the bad images.
- **Caveat:** `nozzle.png` is only ~2KB, so the hard OOM guards (request-size-tied-to-cell, inSampleSize
  downscale) were exercised for correctness/bounding but **NOT brute-forced with a large image**. The
  guards remain code-reasoned for the large-image case.

---

## Result summary

| Gate | Behavior | Requirement | Result |
|------|----------|-------------|--------|
| 1 | Prompt driven start-to-finish from the tablet (buttons fire gcode) | SC-4 / PROMPT-04 | ☑ **PASS** |
| 2 | Close → `prompt_end` echo round-trip closes overlay | SC-3 / PROMPT-03 | ☑ **PASS** |
| 3 | Disconnect closes locally, NO `prompt_end` (cross-client) | SC-3 / D-10 | ☑ **PASS** |
| 4 | `prompt_image` bounded, no jank/OOM | SC-2 | ☑ **PASS** |

**All four gates PASS** on flox + the live E3 (2026-06-04). The mock-vs-reality behaviors (the
`prompt_end` echo round-trip and disconnect-closes-locally-with-NO-`prompt_end`) are **proven on real
hardware with objective server-side `gcode_store` evidence**, not merely host-side unit assertions.

**Caveat carried forward (image gate):** the large-image OOM brute-force was not exercised (the test PNG
was ~2KB); the bounding/downscale guards are code-reasoned for the large-image case and stand for a later
opportunistic re-check if a heavy `prompt_image` shows up in the wild.

---

_Approved by Matthew (hands-on flox + live E3, 2026-06-04). 12-05-SUMMARY records these results._
