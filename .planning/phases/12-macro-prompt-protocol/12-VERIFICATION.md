---
phase: 12-macro-prompt-protocol
verified: 2026-06-04T00:00:00Z
status: passed
score: 4/4 must-haves verified
overrides_applied: 0
---

# Phase 12: Macro Prompt Protocol Verification Report

**Phase Goal:** Make user-authored macros that drive interactive dialogs work on Dinghy. Klipper macros emit `// action:prompt_*` lines through the gcode-response stream; this phase parses that mini-protocol and renders the corresponding interactive dialog — title, text, and buttons that fire their gcode — reusing the Console's `notify_gcode_response` stream and the design system's dialog primitive.
**Verified:** 2026-06-04
**Status:** PASSED
**Re-verification:** No — initial verification

---

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | The `// action:prompt_*` sequence is parsed from the live gcode-response stream into a structured prompt model (title, text, buttons with label/gcode/style), tolerant of malformed/partial sequences | VERIFIED | `ParseAction.kt` is a total parser (returns `null` not throw on unknown/malformed lines); `PromptReducer.kt` is a pure state machine; `PromptEngine.kt` subscribes `store.gcodeResponses` as an INDEPENDENT collector (`scope.launch { store.gcodeResponses.collect { ... } }`). 26-fixture conformance corpus passes GREEN (`PromptFixtureTest.allFixturesConform`). |
| 2 | A prompt renders as an interactive dialog using the existing design-system dialog primitive; tapping a button sends its gcode and the dialog dismisses/updates per the protocol | VERIFIED | `PromptDialog.kt` is a complete, non-stub Composable (header + scrollable Field + footer row + always-present close). AppShell wires `PromptDialog` outside `when(dest)` so it floats over any screen. Content button dispatch: `promptView.flattenContentButtons()[buttonIndex].gcode` via `dispatcher?.dispatch(promptEngine.buttonKey(i), ...)`. Footer dispatch: `promptView.footerButtons[footerIndex].gcode` via `promptEngine.footerKey(i)`. Separate key namespaces prevent same-index collision. UAT Gate 1 PASS (server-side `gcode_store` confirms correct gcode fired per button, including nested button_group). |
| 3 | `prompt_end`/footer semantics and re-entrancy (a macro re-prompting) are handled, and an unsupported/garbage action line never crashes or wedges the Console | VERIFIED | Reducer handles `PromptEvent.End` and `PromptEvent.Disconnect` identically (no-op when already idle, else reset without bumping epoch). Re-entrancy: a new `prompt_begin` bumps epoch and starts fresh regardless of prior state. Unknown `prompt_*` commands return `null` from `parseAction` — silently ignored, loop cannot wedge. `allFixturesConform` covers 8 hardening cases. UAT Gate 2 PASS: close dispatches `action:prompt_end`; overlay closes on the echoed line, NOT a local teardown (server-confirmed). |
| 4 | Provable with a real prompt-protocol macro on the printer (e.g. a load-filament wizard) driven start-to-finish from the tablet | VERIFIED | On-device UAT 2026-06-04 on flox (Nexus 7 2013, Adreno 320 / 2GB) against the live Ender 3 Pro. All four gates PASS. `MPP_BUTTON_GROUP` / `MPP_LIVE_APPEND_AFTER_SHOW` / `MPP_ROW_LAYOUT` / `MPP_BUTTON_FIELD_DEFAULTS` driven via `notify_gcode_response` stream; every button dispatch cross-checked against `/server/gcode_store` for objective server-side evidence. |

**Score:** 4/4 truths verified

---

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `app/src/main/java/works/mees/dinghy/prompt/ParseAction.kt` | Total line parser | VERIFIED | 129 lines, substantive; `parseAction()` handles all 12 prompt_* commands + `null` fallback for unknown |
| `app/src/main/java/works/mees/dinghy/prompt/PromptEvent.kt` | Sealed event hierarchy | VERIFIED | 112 lines; full sealed class hierarchy (Begin, Text, Show, End, Markup, Button, FooterButton, Image, Target, Size, Align, RowStart, RowEnd, ButtonGroupStart, ButtonGroupEnd, Disconnect) |
| `app/src/main/java/works/mees/dinghy/prompt/PromptModel.kt` | Internal state + PromptView | VERIFIED | 118 lines; `PromptStateData` (internal) + `PromptView` (6-key external conformance shape), all `@Immutable` |
| `app/src/main/java/works/mees/dinghy/prompt/PromptReducer.kt` | Pure state machine | VERIFIED | 187 lines; `reduce()` is exhaustive over all `PromptEvent` types; handles container nesting, suppressed lifecycle, liveAppend mode |
| `app/src/main/java/works/mees/dinghy/prompt/PromptEngine.kt` | Spine-level engine holder | VERIFIED | 146 lines; constructs 3 coroutine collectors: (1) gcode stream subscribe+reduce, (2) disconnect-local-close edge, (3) dispatcher Failure fold for toast |
| `app/src/main/java/works/mees/dinghy/prompt/Markup.kt` | Markup AST parser | VERIFIED | 152 lines; total `parseMarkup()` + `markupToPlainText()` with entity decoding, 6-tag grammar |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptDialog.kt` | Full-screen overlay Composable | VERIFIED | 173 lines; header + scrollable field + footer row + always-present close; parameter-driven (no AppShell coupling); errorText + inFlight slots wired |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptButtonFlatten.kt` | Shared depth-first flatten helper | VERIFIED | 53 lines; `flattenContentButtons()` extension on `PromptView`; pure Kotlin (no Compose) so usable off UI path |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptContentItems.kt` | Per-item renderers (text/markup/image/button/row/button_group) | VERIFIED | 211 lines; `ButtonCounter` shared across tree; nested-container buttons get correct depth-first index |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptImageItem.kt` | Coil-bounded image with alt fallback | VERIFIED | 130 lines; `BoxWithConstraints` cell sizing, Coil request size tied to `targetPx`, `onState` error → `failed = true` → alt text fallback |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptMarkupText.kt` | Markup AST → AnnotatedString | VERIFIED | 127 lines; 6-node type map (Text, Emphasis, Color, Size); D-03 author-hex carve-out documented and implemented correctly |
| `app/src/main/java/works/mees/dinghy/ui/prompt/PromptStyleColors.kt` | 6-style → token color resolver | VERIFIED | 44 lines; maps PRIMARY/SECONDARY/INFO/WARNING/ERROR/SUCCESS to role tokens; no raw color literals except the carve-out in PromptMarkupText |
| `app/src/test/resources/prompt/fixtures.json` | 26-fixture conformance corpus | VERIFIED | 873 lines; schema_version 1; 26 fixtures (8 core + 18 optional) per `corpusGuard` test |
| `app/src/test/java/works/mees/dinghy/prompt/PromptFixtureTest.kt` | Conformance gate | VERIFIED | 210 lines; replays all 26 fixtures through `parseAction`/`reduce`/`promptView`; toMatchObject semantics; strict 6-key shape guard (T-12-07) |

---

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| `AppShell.kt` | `PromptEngine` | `remember(store) { PromptEngine(scope, store, events = calibEvents) }` | WIRED | Lines 350-352 of AppShell.kt; re-keyed on spine rebuild |
| `AppShell.kt` | `PromptDialog` | `if (promptView.visible) { PromptDialog(...) }` | WIRED | Lines 625-671 of AppShell.kt; hoisted outside `when(dest)` |
| `PromptDialog.onButton` | gcode dispatch | `promptView.flattenContentButtons()[buttonIndex].gcode` via `dispatcher?.dispatch(promptEngine.buttonKey(i), ...)` | WIRED | Lines 638-645; uses shared flatten helper |
| `PromptDialog.onFooterButton` | gcode dispatch | `promptView.footerButtons[footerIndex].gcode` via `dispatcher?.dispatch(promptEngine.footerKey(i), ...)` | WIRED | Lines 650-658; separate `footerKey` namespace |
| `PromptDialog.onClose` | gcode dispatch | `promptEngine.closeGcode` via `dispatcher?.dispatch(promptEngine.closeKey, ...)` | WIRED | Lines 662-667; dispatches `RESPOND TYPE=command MSG="action:prompt_end"` |
| `PromptEngine` | `store.gcodeResponses` | `scope.launch { store.gcodeResponses.collect { line -> parseAction(line)?.let { ... } } }` | WIRED | Lines 83-89 of PromptEngine.kt; independent collector, not throttled |
| `PromptEngine` disconnect | local-close-no-dispatch | `store.printerState.map { it.connection }.distinctUntilChanged().collect { isDown && wasConnected -> reduce(state, disconnectEvent()) }` | WIRED | Lines 97-111 of PromptEngine.kt; explicitly NO `CommandDispatcher.dispatch` on the disconnect path |
| `PromptContentItems.ButtonCounter` | `flattenContentButtons()` | Both walk items depth-first, visiting BUTTON type only | WIRED | Counter increments in `PromptContainerRow` match the flatten helper's walk order; same-index contract held |
| `AppShell.BackHandler(promptView.visible)` | `prompt_end` dispatch | `dispatcher?.dispatch(promptEngine.closeKey, ..., promptEngine.scriptParamsFor(promptEngine.closeGcode))` | WIRED | Lines 388-394 of AppShell.kt |
| `AppShell` swipe-up | drawer suppressed while prompt visible | `.pointerInput(dest, promptView.visible)` guard: `if (!promptView.visible && ...)` | WIRED | Lines 405-425 of AppShell.kt |

---

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| `AppShell.kt` → `PromptDialog` | `promptView` | `promptEngine.view.collectAsStateWithLifecycle()` | YES — StateFlow populated by live `store.gcodeResponses` coroutine | FLOWING |
| `PromptEngine._view` | `PromptView` | `store.gcodeResponses.collect { line -> parseAction(line)?.let { state = reduce(...); _view.value = promptView(state) } }` | YES — real gcode stream from Moonraker WebSocket | FLOWING |
| `PromptImageItem` | `url` | `moonrakerImageUrl(httpBase, path)` where `httpBase` comes from `container.httpBase.collectAsStateWithLifecycle()` | YES — live session host/port | FLOWING |

---

### Behavioral Spot-Checks

Step 7b: SKIPPED for server-dependent behaviors (requires a live Moonraker connection). On-device UAT in the phase already covers all four critical behaviors with objective server-side evidence — more authoritative than local spot-checks.

---

### Probe Execution

No probe scripts declared or found for this phase. The phase used the on-device UAT pattern (human-in-the-loop with server-side `gcode_store` cross-checks) rather than automated probes.

---

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|---------|
| PROMPT-01 | 12-01, 12-02, 12-03 | Parse + reduce `// action:prompt_*` into PromptView; 26-fixture conformance corpus GREEN | SATISFIED | Parser total, reducer pure, engine wired, all 26 fixtures pass |
| PROMPT-02 | 12-04, 12-05 | Render dialog + buttons fire gcode | SATISFIED | PromptDialog substantive + AppShell dispatch wiring + Gate 1 UAT PASS |
| PROMPT-03 | 12-02, 12-03, 12-05 | prompt_end/footer/re-entrancy + never-wedge Console | SATISFIED | Reducer handles all edge cases; Gates 2 + 3 UAT PASS |
| PROMPT-04 | 12-05 | Provable with a real macro on the printer | SATISFIED | On-device UAT PASS on flox + live E3, server-confirmed |

---

### Anti-Patterns Found

Scanned all files under `app/src/main/java/works/mees/dinghy/prompt/` and `app/src/main/java/works/mees/dinghy/ui/prompt/`.

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `PromptDialog.kt` (line 37) | — | Word "placeholder" in KDoc comment | INFO | Describes the empty-state UX ("quiet placeholder") — NOT a stub; the actual empty-state rendering at line 102-108 is real code producing "This prompt has no content." |
| Various `ParseAction.kt` | Various | `return null` | INFO | All are legitimate — parser returns null on non-matching or malformed input (the tolerant-total-parser contract). No null is rendered to the user from these paths. |

No TBD/FIXME/XXX/HACK markers. No empty implementations. No hardcoded empty data returned to the UI. No console.log-only handlers.

---

### Human Verification Required

None — all observable truths were verified either structurally (code inspection at all 4 levels) or through the on-device UAT with objective server-side evidence. The one caveat noted in the UAT (large-image OOM brute-force not exercised, only code-reasoned) does not constitute a blocking human check — it is a deferred opportunistic re-check if a heavy `prompt_image` appears in the wild.

---

### Gaps Summary

No gaps. All four roadmap success criteria are VERIFIED through code inspection (Levels 1-4) plus on-device UAT with server-side cross-checks.

**Notable design properties confirmed by code inspection (not just SUMMARY claims):**

1. **D-10 disconnect-no-dispatch is structurally present** in `PromptEngine.kt` lines 97-111: the disconnect path calls `reduce(state, disconnectEvent())` and updates `_view.value` — it does NOT call `CommandDispatcher.dispatch` or `store` methods. The comment is explicit. This was also proven live (Gate 3: Mainsail kept prompt open after flox Wi-Fi dropped).

2. **Flatten-index contract is correct**: `PromptButtonFlatten.kt` and `PromptContentItems.kt` (`ButtonCounter`) use identical depth-first BUTTON-only traversal. Both are pure Kotlin with no Compose coupling, so they can be compared mechanically. The AppShell dispatch lookup (`promptView.flattenContentButtons()[buttonIndex]`) uses the same helper.

3. **Separate buttonKey/footerKey namespaces**: `PromptEngine.buttonKey(i)` → `"prompt:<epoch>:<i>"` vs `PromptEngine.footerKey(i)` → `"prompt:<epoch>:footer:<i>"`. The `footer:` infix ensures a content button at index 0 and a footer button at index 0 never share a CommandDispatcher key. UAT Gate 1 confirmed content[0] and footer[0] dispatched independently.

4. **Engine is not dead code**: imported and used in AppShell at lines 42, 44, 350-353, 388-394, 405, 419, 625-671.

---

_Verified: 2026-06-04_
_Verifier: Claude (gsd-verifier)_
