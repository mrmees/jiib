---
phase: 12-macro-prompt-protocol
plan: 04
subsystem: prompt-protocol
tags: [kotlin, compose, prompt-protocol, ui, render, markup, annotatedstring, coil, theme-tokens]

# Dependency graph
requires:
  - phase: 12-macro-prompt-protocol
    provides: "12-02 PromptView (6-key) + PromptItem/FooterButton model + PromptStyle/PromptAlign/PromptTextSize enums; 12-01 parseMarkup AST (MarkupNode) the markup builder consumes"
provides:
  - "PromptDialog full-screen overlay (header / scrollable Field / footer action bar + always-present close) — parameter-driven, composes standalone (AppShell hoist + gcode dispatch is 12-05)"
  - "flattenContentButtons(): the ONE shared depth-first buttons-only walk = the cross-plan button-index contract (renderer click-wiring AND 12-05 dispatch resolve the SAME gcode for nested buttons)"
  - "PromptMarkupText: MarkupNode AST -> AnnotatedString with the D-03 author-hex carve-out (<color>/<bgcolor>) + the 15/18/22/28 fsSp size ladder"
  - "promptStyleColor: the 6-protocol-style -> Dinghy-token resolver (info -> accent2; Intent stays 5)"
  - "PromptImageItem: Coil 3 AsyncImage, ContentScale.Fit, hard-bounded square (width/3 x clamped scale), alt-text fallback; config/... served at httpBase/server/files/"
affects: [12-05, macro-prompt-protocol]

# Tech tracking
tech-stack:
  added: []
  patterns:
    - "Full-screen opaque overlay assembled from MacroExecutionPopup primitives (Box(t.bg) + Column header/weighted-scrollable Field/footer Row) — an AppShell overlay, not a Dest (D-05)"
    - "Cross-plan button-index contract via ONE pure-Kotlin depth-first flatten helper: renderer threads a shared ButtonCounter so a nested-container button gets the SAME index as flattenContentButtons()[i]"
    - "AnnotatedString builder folding a renderer-neutral AST with withStyle scopes; the D-03 author-hex carve-out is the ONLY raw Color(...) constructor in the prompt UI (chrome is token-only)"
    - "Coil hard-bounding (ContentScale.Fit + request size tied to the cell in px) reusing the Files/Phase-10 downscale discipline for the Adreno-320 fill-rate floor"

key-files:
  created:
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptStyleColors.kt"
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptMarkupText.kt"
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptButtonFlatten.kt"
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptContentItems.kt"
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptImageItem.kt"
    - "app/src/main/java/works/mees/dinghy/ui/prompt/PromptDialog.kt"
    - "app/src/test/java/works/mees/dinghy/prompt/PromptStyleColorsTest.kt"
  modified: []

key-decisions:
  - "SVG prompt_image falls through to the alt-text fallback (coil-svg is NOT in the catalog — only coil-compose + coil-network-okhttp); PNG/JPEG render via Coil. A failed/unsupported decode -> alt text, which the UI-SPEC explicitly sanctions. SVG support = add a verified minSdk-23 coil3 coil-svg artifact later; alt-text is the floor and ships either way (plan decision A1)."
  - "Moonraker image URL = httpBase/server/files/<path> — config/... files are served from the same /server/files/ mount as gcodes/... (confirmed against PrintMetadata.thumbnailUrl which builds /server/files/gcodes/...). Blank httpBase -> null -> alt-text fallback (no malformed request)."
  - "promptStyleColor is a SEPARATE String/PromptStyle -> Color map, NOT a 6th Intent member (Intent stays the 5-value safety vocabulary); info -> accent2 is the one style with no Intent equivalent."
  - "The dialog keys content-button indices off a shared ButtonCounter walked depth-first in the SAME order as flattenContentButtons() — a button nested in a row/button_group dispatches the right gcode (the silent-runtime-defect BLOCKER guard for 12-05)."

patterns-established:
  - "PromptButtonControl: an outline-led >=64dp control tinted by promptStyleColor(style) (the protocol-button analog of OutlinedControl/Intent), filling its cell, maxLines 2"
  - "Always-present close control in the footer (the 'always an exit' guarantee) — accent OutlinedControl + close symbol, in NEITHER the content nor footer index list"

requirements-completed: []

# Metrics
duration: ~12min
completed: 2026-06-04
---

# Phase 12 Plan 04: Prompt Render Layer (PromptDialog + sub-components) Summary

**Built the full-screen `PromptDialog` overlay and its render sub-components — the markup AST -> `AnnotatedString` builder (the one genuinely-new Compose piece, with the D-03 author-hex carve-out), the 6-style -> token resolver, the depth-first content-button flatten contract shared with 12-05's dispatch, the equal-width row/group cell layout, the Coil-bounded image item with alt-text fallback, and the always-present close control — all parameter-driven and token-pure chrome, compiling clean with the style resolver unit-GREEN.**

## Performance

- **Duration:** ~12 min
- **Completed:** 2026-06-04
- **Tasks:** 2
- **Files modified:** 7 created

## Accomplishments
- `promptStyleColor` — the 6-protocol-style -> Dinghy-token map (primary->accentLine, secondary/unknown->outline, info->accent2, warning->heat, error->stop, success->go), a SEPARATE map keeping `Intent` at its original 5 members. `PromptStyleColorsTest` GREEN (9 cases: exact mapping, unknown->outline, case-insensitive, info-distinct-from-primary).
- `PromptMarkupText` — folds the 12-01 `MarkupNode` AST into an `AnnotatedString`: `<b>`->SemiBold(600), `<i>`->Italic, `<u>`->Underline, `<color>`/`<bgcolor>`->the author-hex carve-out (`Color(0xFF + #rrggbb)` — the ONLY raw color constructor in the prompt UI), `<size>`->the 15/18/22/28 `fsSp` ladder; default run = `--text` 18sp Geist Regular. Nested tags nest spans.
- `flattenContentButtons()` in `PromptButtonFlatten.kt` — the pure-Kotlin (no Compose import) depth-first buttons-only walk that IS the cross-plan button-index contract: the renderer indexes content buttons by the SAME walk (via a threaded `ButtonCounter`) and 12-05 resolves gcode via `flattenContentButtons()[buttonIndex]`, so a button nested in a `row`/`button_group` dispatches the right gcode (BLOCKER guard).
- `PromptContentItems.kt` — per-item renderers (text/markup/button/image) + equal-width `row`/`button_group` cells (`Row` of `weight(1f)`, centered); a content button fills its cell; alignment (left/right; center default) honored.
- `PromptImageItem.kt` — Coil 3 `AsyncImage`, `ContentScale.Fit`, a hard-bounded square box (`contentWidth/3 × clampedScale`, scale invalid/<=0/non-finite -> 1.0) with the request size tied to the cell in px (never intrinsic — the Adreno-320 OOM guard); `error`/unsupported -> alt text as plain centered 15sp `--text-2`; no alt -> silent collapse; a failed image never blocks the rest.
- `PromptDialog.kt` — the opaque full-screen `Box(t.bg)` overlay: header band (28sp Geist SemiBold, maxLines 3, empty-but-reserved), weighted scrollable Field (16dp gap, "This prompt has no content." placeholder), `SeverityToast` slots (button-rejection error + "Sending…" in-flight), and a footer action bar = the weighted `footer_buttons` Row + an ALWAYS-present accent close control. Parameter-driven (`PromptView` + `onButton`/`onFooterButton`/`onClose` + `httpBase` + `errorText`/`inFlight`); buttons do NOT auto-close; all dimensions ratio/weight.

## Task Commits

1. **Task 1: Markup AnnotatedString builder + 6-style token resolver** - `bd2c98c` (feat)
2. **Task 2: PromptDialog overlay + content/image/footer/close render** - `f8797e4` (feat)

_Task 1 was authored `tdd="true"`; the project-level MVP+TDD gate is inactive (`tdd_mode: false`), so it is a single feat commit (matching the 12-01/12-02 precedent) — the style resolver is unit-GREEN. The markup builder + the Compose render layer are Compose-coupled and proven in the 12-05 UI test / on-device gate per the plan._

## Files Created
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptStyleColors.kt` — `promptStyleColor(PromptStyle/String, ThemeTokens)`; the 6-style->token map (info->accent2).
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptMarkupText.kt` — `PromptMarkupText` + `buildPromptAnnotatedString`; the AST->AnnotatedString fold with the D-03 carve-out + the fsSp ladder.
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptButtonFlatten.kt` — `PromptView.flattenContentButtons(): List<PromptButton>` (pure Kotlin) + `PromptButton`.
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptContentItems.kt` — `PromptContentItem` / `PromptButtonControl` / container-row / per-leaf renderers + `ButtonCounter`.
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptImageItem.kt` — `PromptImageItem` + `moonrakerImageUrl`; Coil-bounded image with alt-text fallback.
- `app/src/main/java/works/mees/dinghy/ui/prompt/PromptDialog.kt` — `PromptDialog` overlay + the always-present `PromptCloseControl`.
- `app/src/test/java/works/mees/dinghy/prompt/PromptStyleColorsTest.kt` — 9-case host test of the style resolver.

## Decisions Made
- **SVG -> alt-text fallback (plan A1).** The catalog has `coil-compose` + `coil-network-okhttp` but NOT `coil-svg`. PNG/JPEG render via Coil; an SVG (unsupported decode) falls through to the alt-text path the UI-SPEC sanctions — never a crash. Adding a verified minSdk-23 `io.coil-kt.coil3:coil-svg@3.1.0` to the catalog + ImageLoader would enable SVG later, but alt-text is the floor and ships either way. _Documented deferral._
- **Image URL = `httpBase/server/files/<path>`.** Confirmed against `PrintMetadata.thumbnailUrl` (Files builds `/server/files/gcodes/...`); `config/...` files are served from the same `/server/files/` mount. Blank `httpBase` -> `null` -> alt-text (no malformed request fired).
- **`promptStyleColor` is a separate map, NOT a 6th `Intent`.** `Intent` stays the 5-value safety vocabulary; `info` (the only protocol style with no Intent equivalent) resolves to `accent2`.
- **Depth-first content-button indexing via a shared `ButtonCounter`** matches `flattenContentButtons()` exactly so 12-05's `flattenContentButtons()[buttonIndex]` dispatch resolves the right gcode for nested buttons (the BLOCKER guard).

## Deviations from Plan

None - plan executed exactly as written. (Task 1 is a single feat commit rather than a TDD test->feat split because `tdd_mode` is disabled project-wide; this matches the 12-01/12-02 precedent and the plan's intent — the style resolver is pure and unit-GREEN.)

## Issues Encountered
- A first-compile delegate error in `PromptImageItem.kt`: a `var failed by remember { mutableStateOf(false) }` written with fully-qualified `androidx.compose.runtime.*` lacked the `getValue`/`setValue` delegate operator imports. Fixed by importing `getValue`/`setValue`/`remember`/`mutableStateOf` and using the plain delegate form (Rule 3 blocking-issue fix, folded into the Task 2 commit `f8797e4`).

## Known Stubs
None that block the plan goal. The render layer is parameter-driven by design — `onButton`/`onFooterButton`/`onClose` are callbacks the standalone overlay exposes; the actual AppShell hoist + gcode dispatch wiring is the explicit scope of 12-05 (the plan keeps this plan render-only so it composes in a preview/UI test). This is not a stub — it is the documented plan boundary.

## Next Phase Readiness
- The render contract is complete and compiles: `PromptDialog` renders the full `PromptView` (header/Field/footer + always-present close) with markup spans (author-hex carve-out), Coil-bounded images, equal-width rows, and all degenerate states. The 6 styles map to tokens (Intent stays 5).
- 12-05 hoists `PromptDialog` into the AppShell, feeds it the live `promptView(state)`, and wires `onButton(i)` -> `flattenContentButtons()[i].gcode` dispatch / `onFooterButton(i)` -> `footerButtons[i].gcode` / `onClose()` -> `prompt_end`. The button-index contract is the shared `flattenContentButtons()` helper — implement the dispatch lookup against it verbatim.
- PROMPT-02 render half complete (the gcode-firing wiring is 12-05).
- No blockers.

## Self-Check: PASSED

All 7 created files exist on disk; both task commits (`bd2c98c`, `f8797e4`) are present in git history. `:app:compileDebugKotlin` exits 0; `PromptStyleColorsTest` GREEN (9 tests, 0 failures); the full `works.mees.dinghy.prompt.*` unit suite builds + passes.

---
*Phase: 12-macro-prompt-protocol*
*Completed: 2026-06-04*
