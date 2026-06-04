---
phase: 12-macro-prompt-protocol
plan: 05
subsystem: prompt-protocol
tags: [kotlin, compose, prompt-protocol, appshell, dispatch, moonraker, gcode, ui, on-device-uat]

# Dependency graph
requires:
  - phase: 12-macro-prompt-protocol
    provides: "12-03 PromptEngine (per-session ctor + view StateFlow + buttonKey/footerKey/closeKey/closeGcode); 12-04 PromptDialog overlay + flattenContentButtons() button-index contract"
provides:
  - "PromptEngine constructed per-session in AppShell (remember(store), events = spine?.dispatcher?.events) — idle empty-store fallback when no spine"
  - "PromptDialog hoisted OUTSIDE when(dest) (beside MacroExecutionPopup/ScanSurface) — floats over any screen when promptView.visible"
  - "Button/footer/close dispatch wiring: content -> flattenContentButtons()[i].gcode @ buttonKey(i); footer -> footer_buttons[i].gcode @ footerKey(i); close -> action:prompt_end @ closeKey; all via CommandDispatcher GCODE_SCRIPT"
  - "Swipe-up drawer suppressed + BackHandler(prompt_end) while the prompt is visible"
  - "The D-03 author-hex carve-out documented in docs/ui_design/CLAUDE.md + THEMING.md (markup text runs only; chrome stays token-routed) so the Phase-21 conformance auditor does not flag it"
  - "On-device proof (flox + live E3): the mock-vs-reality gates (prompt_end echo round-trip, disconnect-closes-locally-with-NO-prompt_end) verified with objective server-side gcode_store evidence"
affects: [macro-prompt-protocol, 21-final-theme-ui-conformance, 22-release-hardening-ship]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Per-session prompt engine via remember(store) re-keyed on spine rebuild (the calibration/ExtrudeHolder template) — the empty fallback store backs it while idle"
    - "Prompt overlay hoisted OUTSIDE when(dest) as an AppShell overlay (D-05), NOT a Dest — same shape as MacroExecutionPopup/ScanSurface"
    - "Content vs footer index spaces are SEPARATE dispatcher-key namespaces (buttonKey(i) vs footerKey(i)) so a same-index content/footer pair never debounce-collides"
    - "Disconnect/Back semantics: user Close + system Back DISPATCH prompt_end (explicit dismissal); disconnect/process-death do a LOCAL reducer reset with NO dispatch (D-10 cross-client safety)"
    - "Live-stream UAT method: stream RESPOND TYPE=command MSG=action:prompt_* to /printer/gcode/script -> Moonraker notify_gcode_response broadcast drives the overlay; cross-check every tap against /server/gcode_store for objective server-side evidence"

key-files:
  created:
    - ".planning/phases/12-macro-prompt-protocol/12-05-SUMMARY.md"
  modified:
    - "app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt"
    - "docs/ui_design/CLAUDE.md"
    - "docs/ui_design/THEMING.md"
    - ".planning/phases/12-macro-prompt-protocol/12-UAT.md"

key-decisions:
  - "Content and footer buttons dispatch on DISTINCT key namespaces (buttonKey vs footerKey) — both index spaces start at 0, so reusing buttonKey for footers would debounce-suppress the wrong control (Codex pre-execute finding)."
  - "Buttons do NOT auto-close the overlay (D-11) — only the inbound echoed prompt_end line closes it via the reducer."
  - "The D-03 author-hex carve-out (macro-authored PromptMarkup <color:#hex>/<bgcolor:#hex>) is content DATA, not chrome — an explicit, bounded carve-out from the semantic-tokens-only LAW (markup text runs ONLY), with the Spoolman spool-color border as precedent."
  - "UAT method: stream protocol command sequences directly to Moonraker /printer/gcode/script (no macro install / FIRMWARE_RESTART) and verify every dispatch against /server/gcode_store — objective server-side evidence directly answering the mock-vs-reality concern."

patterns-established:
  - "Prompt engine wiring mirrors the calibration-holder remember(store) seam; the overlay-hoist mirrors MacroExecutionPopup/ScanSurface; the dispatch lookup uses the ONE shared flattenContentButtons() helper from 12-04."
  - "On-device verification of cross-client side effects (no prompt_end on disconnect) proven by a SECOND client (Mainsail) keeping the prompt open + server gcode_store showing no emission."

requirements-completed: [PROMPT-01, PROMPT-02, PROMPT-03, PROMPT-04]

# Metrics
duration: ~25min (Task 1 wiring) + on-device UAT gate
completed: 2026-06-04
---

# Phase 12 Plan 05: Shell Wiring + On-Device UAT Summary

**Wired the prompt feature into the live AppShell — PromptEngine constructed per-session, the PromptDialog hoisted over any screen, content/footer/close presses dispatching their gcode through the shared CommandDispatcher (separate buttonKey/footerKey namespaces, close → `action:prompt_end`), the swipe-up drawer suppressed + a prompt_end BackHandler while visible — documented the D-03 author-hex carve-out in the UI LAW, and proved all four mandatory on-device gates PASS on flox + a live Ender 3 Pro with objective server-side `gcode_store` evidence (the mock-vs-reality echo-round-trip and disconnect-no-prompt_end behaviors are now real-hardware-proven).**

## Performance

- **Duration:** ~25 min (Task 1 wiring) + the blocking on-device UAT gate
- **Completed:** 2026-06-04
- **Tasks:** 2 (Task 1 auto wiring + Task 2 blocking human-verify UAT)
- **Files modified:** 4 (AppShell.kt, docs/ui_design/CLAUDE.md, docs/ui_design/THEMING.md, 12-UAT.md) + this SUMMARY

## Accomplishments
- **AppShell wiring (Task 1, commit `6a6a072`):** `PromptEngine` constructed via `remember(store)` with `events = spine?.dispatcher?.events` (per-session, re-keyed on spine rebuild; idle empty-store fallback). `PromptDialog` hoisted OUTSIDE `when(dest)` (beside `MacroExecutionPopup`/`ScanSurface`), shown when `promptView.visible`, floating over any screen.
- **Dispatch wiring:** a CONTENT button dispatches `promptView.flattenContentButtons()[buttonIndex].gcode` (the shared 12-04 depth-first buttons-only helper — a button nested in a row/button_group fires the RIGHT gcode) keyed on `promptEngine.buttonKey(i)`; a FOOTER button dispatches `promptView.footer_buttons[footerIndex].gcode` keyed on the SEPARATE `promptEngine.footerKey(i)` namespace; the close control dispatches `RESPOND TYPE=command MSG=action:prompt_end` keyed on `closeKey` — all via `JsonRpcMethods.GCODE_SCRIPT` + `PrinterCommands.scriptParams`. Buttons do NOT auto-close (D-11).
- **Drawer + Back:** the swipe-up App Drawer is suppressed while the prompt is visible (a content scroll never triggers nav), and a `BackHandler(enabled = promptView.visible)` routes system Back through the same `onClose` → `prompt_end` (explicit user dismissal), mirroring the scan/popup handlers.
- **D-03 carve-out docs:** `docs/ui_design/THEMING.md` + `docs/ui_design/CLAUDE.md` record that macro-authored `PromptMarkup` text runs (`<color:#hex>`/`<bgcolor:#hex>`) render the author's exact hex as an EXPLICIT carve-out from the semantic-tokens-only LAW — content DATA, not chrome — bounded to markup text runs ONLY (all dialog chrome stays token-routed), with the Spoolman spool-color border as precedent. Keeps the Phase-21 conformance auditor from flagging it.
- **On-device UAT (Task 2): ALL FOUR GATES PASS** on flox (Adreno-320 / live E3 @ 192.168.1.121) — see `12-UAT.md`. Verified with a novel live-stream method + objective server-side `gcode_store` cross-checks.

## Task Commits

1. **Task 1: AppShell wiring (engine construct + overlay hoist + dispatch + drawer/Back suppress) + D-03 carve-out docs** — `6a6a072` (feat)
2. **Task 2 (UAT prep): build + install on flox + 12-UAT.md script** — `61ed7eb` (docs)

**Plan metadata + UAT results + tracking:** _this docs commit_ (docs(12-05): record on-device UAT pass + complete shell-wiring plan)

_Task 2 is a blocking `checkpoint:human-verify` gate — no code commit; the human ran the four gates and reported PASS, recorded into `12-UAT.md`._

## Files Created/Modified
- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` — PromptEngine construction + PromptDialog overlay hoist + button/footer/close dispatch wiring + drawer suppression + prompt_end BackHandler.
- `docs/ui_design/CLAUDE.md` — D-03 author-hex carve-out section.
- `docs/ui_design/THEMING.md` — D-03 author-hex carve-out (markup text runs only).
- `.planning/phases/12-macro-prompt-protocol/12-UAT.md` — the four on-device gate results (PASSED) + UAT method + device/printer + caveat.
- `.planning/phases/12-macro-prompt-protocol/12-05-SUMMARY.md` — this file.

## On-Device UAT Evidence (the mock-vs-reality gates, proven on real hardware)

**Method (novel — recorded for the ledger):** rather than installing `macro-examples.cfg` + `FIRMWARE_RESTART`, the orchestrator STREAMED the protocol command sequences directly to Moonraker's REST `/printer/gcode/script` as `RESPOND TYPE=command MSG="action:prompt_*"` lines; Moonraker broadcast the `// action:*` responses via `notify_gcode_response` to all clients (including Dinghy), which rendered them. Button gcodes referencing uninstalled `_MOVE_*` helpers were substituted with non-motion `M118` echoes so taps were SAFE and server-verifiable; every dispatch was cross-checked against `/server/gcode_store`. Two test images were uploaded to E3 `config/prompt-assets/` and removed afterward.

**Device/printer:** flox (Nexus 7 2013, LineageOS 18.1 / Android 11, Adreno 320 / 2GB / armeabi-v7a) against the **Ender 3 Pro** @ 192.168.1.121:7125. (The E5+ @ 192.168.1.120 was unusable — Klipper MCU error `mcu 'EBBCan': Unable to connect`.)

| Gate | Behavior | Requirement | Result | Key evidence |
|------|----------|-------------|--------|--------------|
| 1 | Prompt driven start-to-finish from the tablet (buttons fire gcode) | SC-4 / PROMPT-04 | **PASS** | `MPP_BUTTON_GROUP` +10/+1/-1/-10 fired in exact order (flatten-index contract); content[0]/content[1]/footer[0] dispatched distinctly (separate buttonKey/footerKey from the same index 0); live-append updated the SAME overlay in place; row layout rendered image+markup+Select as equal cells. |
| 2 | Close → `prompt_end` echo round-trip closes overlay | SC-3 / PROMPT-03 | **PASS** | Close dispatched `action:prompt_end`; the `// action:prompt_end` echoed back and the reducer closed on the echo (server-confirmed dispatch+echo pair) — not a local teardown. |
| 3 | Disconnect closes locally, NO `prompt_end` (cross-client) | SC-3 / D-10 | **PASS** | Prompt open on BOTH flox + Mainsail; dropped flox Wi-Fi → Dinghy closed locally to the Syncing splash while Mainsail KEPT the prompt; `gcode_store` confirmed `prompt_end emitted after disconnect: False`. |
| 4 | `prompt_image` bounded, no jank/OOM | SC-2 | **PASS** | `nozzle.png` decoded as a bounded ~half-cell square (scale 0.5), no jank/OOM on Adreno-320; SVGs → alt text by design; `MPP_INVALID_IMAGE_PATHS` (absolute / home / parent-traversal) all rejected → alt text, prompt survived. |

**Caveat (Gate 4):** `nozzle.png` was only ~2KB, so the hard OOM guards (request-size-tied-to-cell, inSampleSize downscale) were exercised for correctness/bounding but NOT brute-forced with a large image. The guards remain code-reasoned for the large-image case (opportunistic re-check if a heavy `prompt_image` appears in the wild).

## Decisions Made
- **Separate dispatcher-key namespaces for content vs footer** (`buttonKey(i)` vs `footerKey(i)`) — both index spaces start at 0, so reusing `buttonKey` for footers would debounce-collide content-button N with footer-button N (Codex pre-execute finding). Resolved at planning, implemented verbatim.
- **Buttons do not auto-close (D-11)** — only the echoed `prompt_end` closes the overlay via the reducer.
- **D-03 author-hex carve-out is content DATA, not chrome** — bounded to markup text runs, documented in the UI LAW with the Spoolman spool-color precedent.
- **Live-stream UAT over a macro install** — streaming `RESPOND TYPE=command MSG=action:prompt_*` to `/printer/gcode/script` + `gcode_store` cross-checks gave objective server-side evidence without touching the printer's config, and the E5 was down anyway.

## Deviations from Plan

None — plan executed exactly as written. The E5+ being unavailable (MCU error) was handled by running the UAT on the E3, which the plan explicitly sanctioned (either printer). The live-stream UAT method is a procedural choice within the plan's "drive the four gates on a live printer" envelope, not a deviation from the wiring or success criteria.

## Issues Encountered
- **E5+ unusable for the UAT** — Klipper in MCU-error state (`mcu 'EBBCan': Unable to connect`). Resolved by running the full UAT against the live E3 (192.168.1.121), which the plan permits.

## Known Stubs
None. The feature is fully wired end-to-end and on-device-proven.

## Next Phase Readiness
- Phase 12 (Macro Prompt Protocol) is **COMPLETE** — all 5 plans done, all four on-device gates PASS, PROMPT-01/02/03/04 closed.
- The prompt overlay is live over any screen; buttons + footer + close fire gcode through the shared dispatcher; drawer suppressed + Back routes prompt_end while visible; the D-03 carve-out is in the UI LAW for the Phase-21 conformance pass.
- The image-gate large-PNG OOM brute-force is the only open caveat — code-reasoned, opportunistic re-check later. No blockers.

## Self-Check: PASSED

- `app/src/main/java/works/mees/dinghy/ui/shell/AppShell.kt` exists; `docs/ui_design/CLAUDE.md` + `docs/ui_design/THEMING.md` exist; `12-UAT.md` updated to PASSED; this SUMMARY written.
- Commits `6a6a072` (feat: wiring + carve-out) and `61ed7eb` (docs: UAT script) present in git history.
- Full `:app:testDebugUnitTest` was GREEN and `:app:assembleDebug` succeeded at Task-1 commit (per the plan's automated verify); debug APK installed on flox; all four on-device gates PASS.

---
*Phase: 12-macro-prompt-protocol*
*Completed: 2026-06-04*
