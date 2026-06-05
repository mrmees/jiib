---
phase: 15-theme-system-settings-redesign
plan: 06
subsystem: ui
tags: [settings, theme-editor, color-wheel, compose-canvas, d-04-retirement, palette-modes, on-device-uat, flox, adreno-320]
requires:
  - phase: 15-05
    provides: "Per-profile theme tuple (seedHex/dark/paletteMode/poolShift/maxItems/poolOverrides) + AppContainer durable editor intents (setActiveSeed/Mode/Shift/Dark/Override + resetActiveTheme via writeScope) + the @Deprecated TokenDelta/setBase/setDeltas/resolve + Profile.themeBase/themeDeltaArgb shims that survived only to keep this wave compiling"
  - phase: 15-04
    provides: "ThemeResolver generate-and-cache (no-arg ctor + seed/mode/shift/override API + single re-emit)"
  - phase: 15-03
    provides: "TokenBridge.build() + extended ThemeTokens (pool[]/Directional)"
provides:
  - "Hybrid Settings hub: Printers · Connection · Appearance · Feature toggles · System (D-10/D-11); old accent picker REMOVED; palette-mode chip row (Colorful default / Simple / High-contrast, D-15) + an 'Edit theme…' forward-entry row; greyed Coming-soon placeholders for Output/WebRTC/Fine-tune; System = version+build only (D-12)"
  - "Pushed theme-editor sub-page (ThemeEditorScreen): color wheel + preset swatches + generated-swatch-strip preview + per-slot pool-override grid + Randomize(Warn)/Reset(Danger,ConfirmGuard)/Done(Go), all writes through the AppContainer durable surface (D-06/D-08/D-09)"
  - "ColorWheel.kt: Compose Canvas hue-ring (cached sweepGradient, ONE awaitEachGesture, onHandleMove per move = no regen + onSettle on pointer-up only, D-07) — settle-regen protects the Adreno-320 budget"
  - "Editor open-state OWNED inside SettingsScreen (rememberSaveable + BackHandler) so BOTH entry paths reach it — the AppShell Dest.Settings route AND the RootController first-run/Connect/settings-escape render (no AppShell/RootController wiring divergence)"
  - "D-04 two-step retirement COMPLETE: TokenDelta/Role/setBase/setDeltas + the top-level resolve() shim DELETED from ThemeResolver; Profile.themeBase/themeDeltaArgb + toThemeResolved DELETED; ThemePrefs.Resolved/sanitize legacy keys DELETED; GalleryScreen re-pointed to setDark/setSeed; zero LIVE references to the retired symbols remain across app/src"
affects:
  - "16 (Home redesign) — inherits the final Settings hub + the seed-generated theme as the visual foundation"
  - "17/18/19/20 (Fine-tune/Output/SysInfo/WebRTC) — the greyed forward-entry placeholders in Feature toggles are their landing seams"
  - "the follow-on theming/conformance phase — picks up the two deferred items (Settings-vs-Devices boundary; pool-color semantic assignment) tracked in .planning/todos/pending/"
tech-stack:
  added: []
  patterns:
    - "Settle-regen color control: ONE awaitEachGesture, onHandleMove repaints only the handle per drag frame (no theme regen), onSettle on pointer-up fires the single regen+persist (D-07) — the ScrubberPage WR-01 gesture analog applied to a 2D wheel"
    - "Cached hue-ring: the sweepGradient brush is built once (remember) and drawn per frame rather than arc-by-arc — protects the Adreno-320 fill-rate floor while keeping the sacred circle (aspect-ratio 1f)"
    - "In-screen-owned editor open-state: the screen (not AppShell/ShellNavState) owns editorOpen via rememberSaveable + BackHandler, so any caller that renders SettingsScreen — AppShell Dest.Settings AND RootController first-run/escape — gets the editor for free with no per-path wiring divergence (T-15-06-04)"
    - "Two-step API retirement COMPLETED: the @Deprecated chrome-override shims that were only kept to compile prior waves are now hard-deleted once the sole consumer (SettingsScreen) is rebuilt; old persisted blobs still decode via ignoreUnknownKeys (fresh-start, no migration)"
    - "Data-color carve-out reused for swatches: the wheel ring/handle, preset swatches, preview strip, and pool-override grid render their LITERAL generated colors (the AccentSwatch precedent) while ALL chrome routes through LocalTokens"
key-files:
  created:
    - app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt
    - app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/screen/SettingsScreen.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemeResolver.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemePrefs.kt
    - app/src/main/java/works/mees/dinghy/theme/ThemeTokens.kt
    - app/src/main/java/works/mees/dinghy/config/Profile.kt
    - app/src/main/java/works/mees/dinghy/di/AppContainer.kt
    - app/src/main/java/works/mees/dinghy/gallery/GalleryScreen.kt
    - app/src/main/java/works/mees/dinghy/gallery/GalleryActivity.kt
    - app/src/test/java/works/mees/dinghy/theme/ThemeResolverTest.kt
    - app/src/test/java/works/mees/dinghy/theme/FontScaleTest.kt
    - app/src/test/java/works/mees/dinghy/prompt/PromptStyleColorsTest.kt
    - app/src/test/java/works/mees/dinghy/config/ProfileStoreTest.kt
    - app/src/test/java/works/mees/dinghy/di/ActiveConfigDerivationTest.kt
    - app/src/test/java/works/mees/dinghy/theme/TokenDeltaSerializationTest.kt
key-decisions:
  - "F1: the 'Printers' section was REMOVED from Settings on-device (the Devices screen already owns profile CRUD) — left the broader Settings-vs-Devices reframe DEFERRED to the follow-on theming phase (tracked todo), not re-litigated here"
  - "F2: the color wheel was hue-rotated 90° to match the live generated app color — the ring sweepGradient origin and the hueAt() readback were aligned so a tapped/dragged hue matches what the engine actually produces (Matthew: 'wheel color matches now')"
  - "F3: the wheel was given more layout breathing room + a scroll buffer so the handle can be dragged around its full circumference without the page consuming the gesture (Matthew: 'enough space to scroll around it now')"
  - "F4: the Appearance section was made palette-mode-REACTIVE so flipping Colorful/Simple/High-contrast produces a visible retheme on the spot (mode switches confirmed live)"
  - "Editor open-state hosted INSIDE SettingsScreen (rememberSaveable + BackHandler), NOT hoisted to AppShell/ShellNavState — keeps the RootController first-run/escape path reaching the editor (T-15-06-04 / Step-8 PASS)"
patterns-established:
  - "Settle-regen + cached-ring color wheel (D-07 / Adreno-320 budget)"
  - "In-screen-owned sub-page open-state for screens rendered from multiple shells (AppShell + RootController)"
  - "Hard-delete completion of a two-step deprecation once the last live consumer is rebuilt"
requirements-completed: [D-04, D-06, D-07, D-08, D-09, D-10, D-11, D-12, D-15]
duration: live-uat-session
completed: 2026-06-05
---

# Phase 15 Plan 06: Settings Hybrid Hub + Theme-Editor Sub-Page Summary

**Rebuilt Settings into the hybrid hub (Printers · Connection · Appearance · Feature toggles · System) with a pushed theme-editor sub-page — a settle-regen Compose-Canvas color wheel (cached sweep-gradient ring), preset swatches, a generated-swatch-strip preview, a per-slot pool-override grid, and Randomize/Reset — reachable from BOTH the in-shell Settings route and the first-run/escape path; the legacy TokenDelta chrome-override API was fully retired (shims + Profile.themeBase/themeDeltaArgb deleted, both sourcesets compile clean); on-device flox UAT APPROVED after four fixes.**

## Performance

- **Duration:** live-UAT session (build → flox install → eyeball → fix loop)
- **Completed:** 2026-06-05
- **Tasks:** 3 (2 auto + 1 blocking on-device UAT)
- **Files modified:** 15 (2 created, 13 modified)

## Accomplishments

- **Settings is now the hybrid hub** — five ordered sections (Printers · Connection · Appearance · Feature toggles · System), the old accent picker removed, a palette-mode chip row (Colorful default / Simple / High-contrast) + an "Edit theme…" forward-entry row added, greyed "Coming soon" placeholders for Output/WebRTC/Fine-tune, and System reduced to version+build only.
- **A pushed theme-editor sub-page** (`ThemeEditorScreen`) hosting the color wheel, preset seed swatches, a generated-swatch-strip preview, a per-slot pool-override grid, and Randomize(amber)/Reset(red, ConfirmGuard)/Done(green) — every theme write routed through the AppContainer durable write surface (never a composition `rememberCoroutineScope()`).
- **`ColorWheel.kt`** — a Compose Canvas hue-ring with ONE `awaitEachGesture`: `onHandleMove` repaints only the handle per drag frame (no regen), `onSettle` fires the single regen+persist on pointer-up only (D-07). The ring is a cached `sweepGradient` brush (not arc-by-arc per frame) to protect the Adreno-320 fill-rate floor.
- **Editor open-state owned INSIDE SettingsScreen** (`rememberSaveable` + `BackHandler`) so both the AppShell `Dest.Settings` route AND the `RootController` first-run/Connect/settings-escape render reach the editor with no per-path wiring divergence (verified live on the escape path — Step 8).
- **D-04 two-step retirement COMPLETE** — the `@Deprecated` `TokenDelta`/`Role`/`setBase`/`setDeltas` + top-level `resolve()` shims deleted from `ThemeResolver`; `Profile.themeBase`/`themeDeltaArgb` + `toThemeResolved` deleted; `ThemePrefs.Resolved`/`sanitize` legacy keys deleted; `GalleryScreen` re-pointed to `setDark`/`setSeed`; tests reworked onto the new model. Full `:app:testDebugUnitTest` GREEN + `:app:assembleDebug` SUCCEEDS; zero LIVE references to the retired symbols remain.

## Task Commits

1. **Task 1: ColorWheel + theme-editor sub-page; editor open-state in SettingsScreen** — `2c410b9` (feat)
2. **Task 2: rebuild SettingsScreen hybrid hub + COMPLETE the D-04 retirement** — `3e86a88` (refactor)
3. **Task 3: on-device flox UAT + remediation** — `e490103` (fix) — the four device-only fixes F1–F4 (wheel hue +90° drop, wheel size/scroll gate, Printers-section removal, palette-reactive Appearance), re-verified APPROVED on flox. `:app:assembleDebug` SUCCESSFUL with the fixes in place.

**Deferred-items capture (separate, pre-existing):** `17d90f1` (docs: the two deferred UAT design items — NOT part of this plan's code, do not re-touch)

**Plan metadata:** _this commit_ (docs: complete 15-06 plan — SUMMARY + STATE + ROADMAP)

## Files Created/Modified

- `designsystem/ColorWheel.kt` — **created.** Compose Canvas hue-ring; cached `sweepGradient`; ONE `awaitEachGesture`; `onHandleMove` (no regen) + `onSettle` (D-07); ≥64dp handle + 2dp outline + static glow.
- `ui/screen/ThemeEditorScreen.kt` — **created.** Pushed sub-page: Seed (wheel) / Presets / Preview swatch strip / per-slot Pool override grid / Randomize(Warn) + Reset(Danger, ConfirmGuard) + Done(Go); all writes via AppContainer durable intents; `fsSp` floors.
- `ui/screen/SettingsScreen.kt` — rebuilt into the five-section hybrid hub; accent picker + AccentSwatch/ACCENT_PALETTE/persistDeltas removed; palette-mode chip row + "Edit theme…" seam added; owns `editorOpen` (rememberSaveable) + BackHandler; **F1: Printers section removed on-device; F4: Appearance made palette-mode-reactive.**
- `theme/ThemeResolver.kt` — D-04: deleted `TokenDelta`/`Role`/`setBase`/`setDeltas` + the top-level `resolve()` shim; the public ctor is now the seed-tuple generate-and-cache ctor.
- `theme/ThemePrefs.kt` — D-04: deleted `Resolved`/`sanitize` + legacy delta keys; the tuple path is the sole persistence.
- `theme/ThemeTokens.kt` — KDoc cleanup tied to the retirement.
- `config/Profile.kt` — D-04: deleted `themeBase`/`themeDeltaArgb` + `toThemeResolved`; old blobs still decode via `ignoreUnknownKeys` (fresh-start, no migration).
- `di/AppContainer.kt` — minor wiring to the retired-symbol-free resolver.
- `gallery/GalleryScreen.kt` + `gallery/GalleryActivity.kt` — re-pointed to the new seed API (`setDark`/`setSeed`); the custom-delta toggle became a sample-seed re-theme demo.
- tests (`ThemeResolverTest`, `FontScaleTest`, `PromptStyleColorsTest`, `ProfileStoreTest`, `ActiveConfigDerivationTest`, `TokenDeltaSerializationTest`) — reworked onto the new model; `TokenDeltaSerializationTest` deliberately keeps the retired key NAMES in a JSON fixture string to prove an old blob still decodes cleanly (fresh-start, no-migration regression guard).

## Decisions Made

The four on-device fixes (F1–F4) and the in-screen editor-state hosting decision are captured verbatim in the frontmatter `key-decisions`. In short: the wheel was hue-aligned (F2) and given scroll room (F3); Appearance was made palette-reactive (F4); the redundant Printers section was removed (F1) with the broader Settings-vs-Devices reframe deferred; and the editor open-state stays inside SettingsScreen so the RootController first-run path reaches it.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 — Bug] Color wheel hue was 90° off from the engine's generated color**
- **Found during:** Task 3 (on-device flox UAT, item F2)
- **Issue:** A hue picked on the wheel did not match the color the generative engine actually produced — the ring's `sweepGradient` origin / `hueAt()` readback were offset 90° from the palette engine's hue convention.
- **Fix:** Rotated the wheel's hue mapping so the ring and the readback align with the engine; a tapped/dragged hue now matches the live retheme.
- **Files modified:** `designsystem/ColorWheel.kt` (committed in `e490103`)
- **Verification:** flox re-verification — Matthew: "wheel color matches now, great job."

**2. [Rule 1 — Bug] Wheel too cramped — the page swallowed the drag gesture**
- **Found during:** Task 3 (UAT item F3)
- **Issue:** Insufficient layout space around the wheel meant the surrounding scroll consumed the circular drag, so the handle couldn't be moved around the full circumference.
- **Fix:** Gave the wheel more breathing room + a scroll buffer so the gesture stays with the wheel.
- **Files modified:** `designsystem/ColorWheel.kt` (the wheel is bounded to 200dp + centered, and its gesture is ring-annulus-gated so the parent scroll keeps a hole/outside touch) (committed in `e490103`)
- **Verification:** flox re-verification — Matthew: "enough space to scroll around it now."

**3. [Rule 2 — Missing Critical] Appearance palette-mode row produced no visible change**
- **Found during:** Task 3 (UAT item F4)
- **Issue:** Switching Colorful/Simple/High-contrast did not visibly retheme — the Appearance surface wasn't observing the mode change reactively, defeating the point of the mode selector.
- **Fix:** Made the Appearance section palette-mode-reactive — a live accent+pool preview swatch row and pool-filled S/M/L segments that re-paint instantly off `LocalTokens` when the mode flips.
- **Files modified:** `ui/screen/SettingsScreen.kt` (committed in `e490103`)
- **Verification:** flox re-verification — mode switches confirmed live (visible difference per mode).

**4. [Rule 4-adjacent — owner decision] Removed the redundant Printers section from Settings**
- **Found during:** Task 3 (UAT item F1)
- **Issue:** The "Printers" section duplicated profile CRUD that the Devices screen already owns.
- **Fix (this plan):** Removed the Printers section from Settings on-device. The broader "should Connection become a printer-settings page / Settings-vs-Devices boundary" reframe was an owner decision to DEFER — captured as a tracked todo, NOT implemented here.
- **Files modified:** `ui/screen/SettingsScreen.kt` (committed in `e490103`)
- **Verification:** flox re-verification — Settings reads cleanly without the duplicate section.

---

**Total deviations:** 4 (2 bugs, 1 missing-critical, 1 owner-scoped removal) — all surfaced by the on-device UAT, none from green-suite work.
**Impact on plan:** All four were necessary to make the editor flow actually usable on the real device; no scope creep. The two larger reframes they touched (Settings-vs-Devices boundary; pool-color semantic assignment) were deliberately deferred (see Open / Deferred).

## Open / Deferred

Two design items were raised during the flox UAT and DEFERRED by owner choice to the follow-on theming/conformance phase. They are already captured as tracked todos (committed at `17d90f1`) and are intentionally NOT implemented in 15-06 — the follow-on theming phase should pick them up:

1. **Settings-vs-Devices boundary** — the Connection section still feels redundant given the Devices screen; a possible "printer settings page" reframe. Left AS-IS in 15-06.
   `.planning/todos/pending/2026-06-05-settings-vs-devices-boundary.md`
2. **Pool-color semantic assignment** — the broader "what generated color gets assigned to which semantic role" policy. Deferred to the follow-on theming phase.
   `.planning/todos/pending/2026-06-05-pool-color-semantic-assignment.md`

## Issues Encountered

The green unit suite passed but four real-device issues only surfaced on flox (the project's recurring mock-vs-reality pattern) — all four (F1–F4) were fixed in the live build→install→eyeball loop and re-verified APPROVED. See Deviations.

## Threat Surface

No new threat surface beyond the plan's `<threat_model>`. The mitigated threats were confirmed on-device:
- **T-15-06-01** (regen-storm / per-frame ring redraw): settle-regen + cached sweep gradient — no mid-drag retheme/jank observed.
- **T-15-06-02** (edit dropped by same-frame nav): all writes via the AppContainer writeScope — persistence survives relaunch.
- **T-15-06-03** (reseed churns the connection spine): theme excluded from ConnectionConfig — Step 8 reached Settings with Wi-Fi killed, connection surface intact.
- **T-15-06-04** (editor unreachable on the first-run/escape path): editor open-state inside SettingsScreen — **Step 8 PASS** ("killed wifi, got to connection settings, edit theme button is available").
- **T-15-06-05** (stale retired symbol left behind): D-04 retirement grep-asserted — zero LIVE references remain.

## Known Stubs

None. The greyed "Coming soon" entries (Output controls / Camera-WebRTC / Fine-tune) are intentional capability-gated forward seams (D-11), landing points for Phases 17–20 — not dead stubs. The retired-symbol KDoc mentions in `ThemeResolver`/`ThemePrefs`/`Profile` and the `TokenDeltaSerializationTest` JSON fixture are deliberate (documentation of the retirement + a fresh-start decode regression guard), not live references.

## Next Phase Readiness

- Phase 15 plan 6 of 7 complete; **15-07 remains** (the final phase-15 plan). NOT started here per instruction.
- The seed-generated theme + the final Settings hub are ready to serve as the visual foundation for Phase 16 (Home redesign) and the later feature phases.
- Two deferred theming items are tracked for the follow-on theming/conformance phase.

## Self-Check: PASSED

- FOUND: app/src/main/java/works/mees/dinghy/designsystem/ColorWheel.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/screen/ThemeEditorScreen.kt
- FOUND: commit 2c410b9 (Task 1: ColorWheel + theme-editor + in-screen open-state)
- FOUND: commit 3e86a88 (Task 2: hybrid hub + D-04 retirement complete)
- FOUND: commit e490103 (Task 3: flox-UAT remediation — F1/F2/F3/F4)
- VERIFIED: D-04 retirement — `.setBase(`/`.setDeltas(` = 0 across app/src; the remaining `TokenDelta`/`themeBase`/`themeDeltaArgb` hits are KDoc comments + a deliberate JSON-fixture regression guard, zero LIVE references
- VERIFIED: full `:app:testDebugUnitTest` GREEN + `:app:assembleDebug` SUCCEEDS (Task-2 verify)
- VERIFIED: on-device flox UAT APPROVED — F2 wheel color match PASS, F3 wheel size/scroll PASS, F4 palette-mode visible change PASS, Step-8 first-run/escape-path editor reachable PASS

---
*Phase: 15-theme-system-settings-redesign*
*Completed: 2026-06-05*
