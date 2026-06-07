---
phase: 18-preview-harness-tokenization-foundation
plan: 05
subsystem: ui + preview-harness
tags: [preview, multipreview, tokenization, strings, icons, accessibility, local-inspection-mode, anchor-exemplar]

# Dependency graph
requires:
  - phase: 18-02
    provides: PreviewBox + 6 theme seeds + fsLargeSeed + SampleFixtures.forMode + PreviewPlaceholderBox + Nexus7Previews
  - phase: 18-03
    provides: DinghyIcons registry + DinghyIconView (sizeDp/a11y) + strings.xml master vocabulary + key convention
provides:
  - "PrintStatusPreviews.kt — PrintStatusModeProvider (4/6 states) + the MINIMIZED @Preview matrix (state matrix on one theme, 6-theme matrix on one state, fs=L overflow, RTL spot-check) — the D-01/D-02/D-05 ANCHOR exemplar 18-06/18-07 copy"
  - "Stateless PrintStatusScreen(state=…) overload + shared private PrintStatusContent — the container-free preview seam (no Moonraker), state-hoisted from the live PrintStatusScreen(container=…)"
  - "Tokenized PrintStatusScreen: 16 stringResource(R.string.printstatus_*/cd_*) sites + 13 DinghyIconView(DinghyIcons.*) sites + both Coil AsyncImage sites guarded by LocalInspectionMode"
  - "New DinghyIcons entries: Altitude, PauseCircle + 9 launcher/shortcut glyphs (icon-never-twice, unique alternates)"
  - "New strings.xml printstatus_* labels/guards/status + cd_* a11y keys"
affects: [18-06, 18-07, phase-22-tokenization-backfill]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "ANCHOR preview pattern (D-01/D-02): mode = @PreviewParameter (the interesting state axis); theme = explicit PreviewBox(seed) wrappers (an annotation cannot select the palette MODE); fs=L via fsLargeSeed (NOT @Preview(fontScale=), a verified NO-OP); ONE RTL spot-check via CompositionLocalProvider(LocalLayoutDirection=Rtl)"
    - "State-hoist for preview: extract a container-free PrintStatusContent shared by the live (AppContainer) overload and a stateless PrintStatusScreen(state=) entry — previews render byte-identical layout to runtime, no Moonraker"
    - "D-05 Coil branch: if (LocalInspectionMode.current) PreviewPlaceholderBox(label) else AsyncImage(...) — a Coil load never resolves under @Preview"
    - "Icon token routing: MaterialSymbol/painter(icon) call sites -> DinghyIconView(DinghyIcons.X, sizeDp = <sp>.dp, contentDescription = stringResource(cd_*)); DinghyIconView owns the ligature a11y so TalkBack never speaks the raw glyph name"

key-files:
  created:
    - app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
  modified:
    - app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt
    - app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt
    - app/src/main/res/values/strings.xml

key-decisions:
  - "State-hoist (not a fake AppContainer): PrintStatusScreen took a heavyweight AppContainer (6 DataStores + mDNS scanner + dispatcher flows + a getSpool LaunchedEffect) — un-previewable. The plan's own key_links + RESEARCH Q8 call the preview entry `PrintStatusScreen(state = SampleFixtures.forMode(mode))`, so the correct move is the textbook Compose state-hoist: extract a pure PrintStatusContent the live overload AND a new stateless overload both call. Behavior-neutral (the live entry's flow/dispatch/guards/LaunchedEffects are unchanged; it now just passes resolved values + lambdas into PrintStatusContent)."
  - "Minimized matrix per RESEARCH Q8: did NOT render 4 modes x 6 themes x fs (28+ panels). Rendered the full STATE matrix on Colorful/dark (mode @PreviewParameter), the full 6-THEME matrix on Printing (six PreviewBox seed wrappers), ONE fs=L Standby overflow shot, ONE RTL Printing spot-check — all under @Nexus7Previews (portrait+landscape)."
  - "Progress and LauncherSpool both back onto the `donut_large` ligature but carry UNIQUE DinghyIcon.alternate handles (progress / launcher_spool), so DinghyIconsTest's alternate-uniqueness assertion stays green — ligature reuse with distinct semantic tokens is allowed (the alternate is the uniqueness key, not the glyph)."
  - "Task 3 (instrumented matcher migration) = verified NO-OP: there is no PrintStatusScreenTest.kt and NO androidTest references PrintStatusScreen.kt's own user-facing literals. The onNodeWithText matchers in ShellPresenceTest/FineTuneNavTest/Webcam tests target App-Drawer tiles + shell text + the FineTune screen — none owned by PrintStatus. The LauncherTile/TuneShortcutTile a11y cd VALUES are unchanged (same strings, now via stringResource); they only moved from the Box .semantics to DinghyIconView. compileDebugAndroidTestKotlin stays green."

patterns-established:
  - "This screen IS the convention 18-06 (FineTune) + 18-07 (Spool) copy verbatim (D-02 core three + mechanical riders)."

requirements-completed: [SC-1, SC-3, D-01, D-02, D-05]

# Metrics
duration: ~40min
completed: 2026-06-06
---

# Phase 18 Plan 05: PrintStatus Anchor Exemplar Summary

**Applied the full D-02 "core three + mechanical riders" template to PrintStatusScreen — the multi-state `@PreviewParameter` anchor (`PrintStatusModeProvider` over the 4/6 `PrintStatusMode` states), a minimized `@Preview` matrix (state×one-theme + 6-theme×one-state + fs=L + RTL) rendering through `PreviewBox`/`SampleFixtures` with NO live Moonraker, string tokenization (16 `R.string.printstatus_*`/`cd_*` sites), icon routing (13 `DinghyIconView(DinghyIcons.*)` sites), and `LocalInspectionMode`-guarded Coil thumbnails — the reference exemplar 18-06/18-07 copy.**

## Performance
- **Duration:** ~40 min
- **Tasks:** 3 (Task 3 a verified no-op)
- **Files:** 4 (1 created, 3 modified)

## Task Commits
1. **Task 1: PrintStatusModeProvider + minimized @Preview matrix + stateless hoist** — `922f7ce` (feat)
2. **Task 2: Tokenize PrintStatusScreen (strings + icons + Coil branch + a11y)** — `d30c741` (feat)
3. **Task 3: Instrumented matcher migration** — NO-OP (no commit; see decisions)

## What Was Built

### Task 1 — Provider + matrix + the state-hoist (`922f7ce`)
- `preview/PrintStatusPreviews.kt`: `class PrintStatusModeProvider : PreviewParameterProvider<PrintStatusMode>` emitting Standby/Printing/Paused/Terminal(Complete|Cancelled|Error). Matrix authored to MINIMIZE proliferation (RESEARCH Q8): `PrintStatusStateMatrix` (full state matrix on Colorful/dark, mode = `@PreviewParameter`, Terminal(Error) fed a 3-line error projection), six `PrintStatusTheme*` sibling functions (full 6-theme matrix on Printing via `PreviewBox(seed)`), `PrintStatusFsLargeOverflow` (fs=L Standby via `fsLargeSeed`), `PrintStatusRtlSpotCheck` (RTL Printing via `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)`). All `@Nexus7Previews` (portrait+landscape).
- `PrintStatusScreen.kt` state-hoist: extracted a container-free `private @Composable PrintStatusContent(...)` holding the gutter renderer + active-print field + the per-mode `when(mode)` ScreenScaffold. The live `PrintStatusScreen(container=…)` resolves its flows + builds its dispatch lambdas exactly as before, then delegates to `PrintStatusContent`; the dispatcher-driven guards/PresetSelector stay in the live entry (live-only modals). Added a stateless `PrintStatusScreen(state: PrinterState, …)` overload (no-op callbacks, default values) that classifies the fixture and calls the same `PrintStatusContent` — the preview seam.
- Gate: `:app:compileDebugKotlin` exit 0. Grep: `PreviewParameterProvider<PrintStatusMode>` ×1, `PreviewBox(` ×9, `SampleFixtures` ×11.

### Task 2 — Tokenization per D-02 (`d30c741`)
- **Strings:** routed every user-facing literal through `stringResource` — both `ConfirmGuard`s (estop + cancel titles/messages/labels), the ring-center/terminal `statusLabel*` (now `statusLabelRes(s): Int`), the Terminal summary row labels, the Standby glance labels (Nozzle/Bed/Spool), the Z-offset readout, the Spoolman line, and all `cd_*` a11y descriptions (pause overlay, e-stop octagon, babystep compress/expand/step-size, the 9 launcher tiles, the Tune shortcut, the GracefulCancel CustomAccessibilityAction). Added the keys to `strings.xml` (`printstatus_*` + `cd_*`). Filenames / sensor names / printer data left RAW (IN/OUT boundary).
- **Icons:** routed the listed sites + the StatGrid nozzle/bed cells through `DinghyIconView(DinghyIcons.*)` (Altitude, Layers, TimerUp, TimerDown, Height, Nozzle, HeatBed, PauseCircle, the 9 launcher/shortcut glyphs, FineTune, Progress, StatusOctagon). Added `Altitude`, `PauseCircle`, and 9 `Launcher*` entries to `DinghyIcons` + `DinghyIcons.all`. Deleted the now-dead private `DrawableIcon` helper + the unused `MaterialSymbol` import.
- **Coil/D-05:** branched BOTH `AsyncImage` sites (PrintStatusFocus ring thumbnail + TerminalFocus hero) on `LocalInspectionMode.current` → `PreviewPlaceholderBox(label = "Thumbnail")`. (The embedded GraphView region is already covered by 18-02's host branch.)
- **RTL rider:** verified the screen already uses direction-agnostic / end-relative modifiers only (`padding(horizontal=)`, `Arrangement.spacedBy`, `Alignment.Center*`, `TextAlign.End`) — no `padding(start/left)`, no `Alignment.*Start`, no hardcoded left/right to fix. The RTL preview spot-check exercises this.
- **D-03 respected:** zero `@Stable`/`@Immutable`/`ImmutableList` added (grep = 0).
- Gate: `:app:assembleDebug` + full `:app:testDebugUnitTest` exit 0 (DinghyIconsTest alternate-uniqueness green with the new entries). Grep: 16 `stringResource(R.string.printstatus_`, 13 `DinghyIconView(`, both `AsyncImage` guarded by `LocalInspectionMode`.

### Task 3 — Instrumented matcher migration (NO-OP)
No `PrintStatusScreenTest.kt` exists and no androidTest references PrintStatusScreen.kt's literals (the `onNodeWithText` matchers target App-Drawer tiles / shell text / the FineTune screen — not PrintStatus). The icon a11y cd values are unchanged. `:app:compileDebugAndroidTestKotlin` exit 0. Nothing to migrate; recorded per the plan's no-op clause.

## Deviations from Plan

### [Rule 3 — Blocking] State-hoist to satisfy the stateless preview contract
- **Found during:** Task 1 (the previews must call `PrintStatusScreen(state = …)` per key_links + RESEARCH Q8, but the only entry took a heavyweight `AppContainer`).
- **Fix:** Hoisted the rendering into a shared container-free `PrintStatusContent` + added a stateless `PrintStatusScreen(state=…)` overload (textbook Compose state-hoist; the documented anchor pattern). The live container overload is behavior-neutral.
- **Files modified:** `PrintStatusScreen.kt`. **Commit:** `922f7ce`.

Everything else executed as written.

## Known Stubs
None. `PreviewPlaceholderBox` (the Coil inspection branch) is the intended D-05 stand-in, not a stub — the live thumbnails load unchanged on-device.

## Threat Flags
None — behavior-neutral tokenization + host-rendered previews; no new trust-boundary surface (matches the plan's accepted T-18-05-01).

## Verification Summary
- `:app:compileDebugKotlin` — exit 0 (Task 1).
- `:app:assembleDebug` + full `:app:testDebugUnitTest` — exit 0 (Task 2; no regression).
- `:app:compileDebugAndroidTestKotlin` — exit 0 (Task 3; pre-existing createComposeRule deprecation warnings only, out of scope).
- Grep: `PreviewParameterProvider<PrintStatusMode>` ×1; `PreviewBox(`/`SampleFixtures` present; `stringResource(R.string.printstatus_` ×16 + `DinghyIconView(DinghyIcons.` ×13 (SC-3); both `AsyncImage` co-located with `LocalInspectionMode` (D-05); `@Stable`/`@Immutable`/`ImmutableList` = 0 (D-03).
- Studio render (4/6 states × 6 combos + fs=L + RTL; GraphView + Coil placeholders labeled) — deferred to the phase gate (human-eyeball), per `:app:lintDebug` being a known-crashing tool (neutralized; assembleDebug + unit tests are the authoritative gates).

## Self-Check: PASSED
- FOUND: app/src/main/java/works/mees/dinghy/preview/PrintStatusPreviews.kt
- FOUND: app/src/main/java/works/mees/dinghy/ui/printstatus/PrintStatusScreen.kt (tokenized)
- FOUND: app/src/main/java/works/mees/dinghy/designsystem/icons/DinghyIcons.kt (new entries)
- FOUND: app/src/main/res/values/strings.xml (new keys)
- FOUND commit: 922f7ce (Task 1)
- FOUND commit: d30c741 (Task 2)

---
*Phase: 18-preview-harness-tokenization-foundation*
*Completed: 2026-06-06*
