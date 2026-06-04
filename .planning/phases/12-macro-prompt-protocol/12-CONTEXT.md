# Phase 12: Macro Prompt Protocol - Context

**Gathered:** 2026-06-04
**Status:** Ready for planning

<domain>
## Phase Boundary

Make Dinghy a **conformant native renderer of the Macro Prompt Protocol v1** — the line-oriented
`action:prompt_*` protocol authored by the project owner (`mrmees/klipper-macro-prompt-protocol`).
Klipper macros emit `RESPOND TYPE=command MSG="action:prompt_*"` lines through the gcode-response
stream; this phase parses that protocol into a normalized prompt model and renders an interactive
dialog (title, text/markup/image content, buttons that fire gcode, footer buttons) over any screen.

**Scope decision (locked): FULL v1** — core + ALL optional extensions: live-append after `prompt_show`,
`prompt_target`, `prompt_size`, rows, `prompt_image`, PromptMarkup, `prompt_align`. Dinghy becomes the
first native (non-web) full-v1 renderer. The protocol is designed so each extension degrades gracefully,
and the repo ships a renderer-neutral test oracle (`fixtures/fixtures.json`, 26 fixtures) + a clean-room
JS reference engine — Dinghy's Kotlin parser/reducer is validated against the SAME fixtures every other
frontend uses.

**In scope:** the parser/reducer (line protocol → normalized state), the full-screen prompt dialog
overlay, button gcode dispatch, all v1 extensions, disconnect-closes-prompt, graceful tolerance of
unknown commands / malformed input, fixture-driven conformance tests.

**Out of scope:** `prompt_input` (explicitly RESERVED, not v1); `prompt_text_scale` / `prompt_image_scale`
(KlipperScreen local aliases, not v1); any change to Klipper/Moonraker; per-client routing/ownership/
"already answered" state (not in v1).
</domain>

<decisions>
## Implementation Decisions

### Conformance scope
- **D-01:** **Full v1.** Implement core + every optional extension (live-append, targeting, prompt_size,
  rows, images, PromptMarkup, prompt_align). Conformance target = all 26 fixtures in
  `fixtures/fixtures.json` (8 core + 18 optional).
- **D-02:** **Live-append after `prompt_show` is SUPPORTED** (like KlipperScreen + the Fluidd reference
  branch, NOT Mainsail's snapshot-on-show). The visible prompt updates as later supported content
  commands arrive.

### PromptMarkup color rendering (the token-LAW tension)
- **D-03:** **Honor author hex colors as an explicit carve-out.** Prompt content is macro-AUTHORED DATA,
  not app chrome — so `<color:#rrggbb>` / `<bgcolor:#rrggbb>` render the author's exact colors, exempt
  from the "semantic tokens only, no raw colors" LAW. Precedent: Spoolman spool colors already render
  real hex (the spool-colored detail border). The carve-out MUST be documented in `docs/ui_design/`
  (CLAUDE.md + THEMING.md) so it doesn't read as a token-purity violation.
- **D-04:** Full PromptMarkup grammar: `<b>/<i>/<u>`, `<color:#hex>`, `<bgcolor:#hex>`,
  `<size:small|normal|large|x-large>`, entity decode (`&lt; &gt; &amp; \n`), proper nesting, unknown
  tags stripped (inner text preserved), invalid color/size ignored (inner text preserved). Render to a
  Compose `AnnotatedString`. The dialog chrome itself (background, outline, footer bar) still uses
  semantic tokens — only the markup text runs honor author colors.

### Dialog rendering on Dinghy's grammar
- **D-05:** **Always full-screen overlay.** Every prompt renders as a full-screen overlay (the
  `MacroExecutionPopup` precedent), shown over whatever screen is active via the AppShell overlay pattern
  (NOT a `Dest`). This is the kiosk/touch render policy the spec explicitly blesses ("frontends may
  clamp... based on kiosk mode, touchscreen geometry").
- **D-06:** **`prompt_size` is PARSED + tracked in normalized state (the size fixtures assert it) but the
  RENDERER CLAMPS to full-screen.** This is the key reconciliation: parser stays fully conformant (passes
  all size fixtures) while the render ignores the envelope hint per kiosk policy. Do NOT skip parsing
  size just because the renderer clamps it.
- **D-07:** Responsive: the full-screen overlay honors Focus/Field/Gutter + portrait/landscape. Images
  and rows are bounded HARD for the Adreno-320 fill-rate floor (reuse the Coil downscale discipline from
  Phases 10/Files; rows = equal-width cells).

### Identity & targeting
- **D-08:** **Implement `prompt_target` filtering.** Dinghy matches `all`, `touch` (category), and a new
  concrete frontend ID **`dinghy`**. Empty/whitespace target list → no match (per spec). Last
  `prompt_target` before `prompt_begin` wins; consumed at begin; cleared by `prompt_end`.
- **D-09:** Add `dinghy` to the spec's target-name list (a one-line doc change in the owner's own
  `klipper-macro-prompt-protocol` repo — see Deferred). Until merged, `dinghy` is still a valid concrete
  ID Dinghy claims locally.

### Reconnect / disconnect
- **D-10:** **Close + wait for next prompt** (spec-required minimum). On Klipper/Moonraker disconnect,
  close the active prompt and clear pending `prompt_target`/`prompt_size` + any open row/button_group
  container (the spec's disconnect-reset). Do NOT implement reset-then-replay from gcode-store history
  this phase — mid-workflow recovery is left to the macro-side `[delayed_gcode]` resume pattern the spec
  documents. (Reset-then-replay noted as a future enhancement — see Deferred.)

### Button behavior (from spec, not re-litigated)
- **D-11:** Buttons = `label|gcode|style`. `label` required (empty → button ignored); `gcode` defaults
  to `label`; `style` defaults to `secondary` (unknown style → `secondary`). 6 semantic styles
  (primary/secondary/info/warning/error/success) map to Dinghy intent tokens. Pressing a button sends
  its gcode via the shared `CommandDispatcher` (`printer.gcode.script`) and does NOT auto-close — only
  `action:prompt_end` (emitted by the button's gcode or the close control) closes the prompt. The dialog
  has a close control that emits `action:prompt_end`.
- **D-12:** `|` is reserved in v1 fields (no escaping in v1). Footer buttons render in a separate action
  bar; content buttons fill their cell width; button groups + rows = equal-width cells.

### Console interaction (already wired in Phase 8)
- **D-13:** Reuse the existing un-throttled `store.gcodeResponses` SharedFlow. The prompt engine
  subscribes independently — it is NEVER starved by console filters. `ConsoleSeverity.ACTION` already
  special-cases `// action:` and `ConsoleFilters.HIDE_PROMPT_COMMANDS` already hides prompt lines from
  the console view (Phase 8 anticipated this phase). Confirm the prompt engine reads the SAME raw stream;
  no new transport.

### Conformance testing
- **D-14:** **Port `fixtures/fixtures.json` as the Kotlin conformance gate.** Each fixture replays its
  `events` and asserts the resulting normalized state matches `expected` (or `expected_by_frontend` for
  the `dinghy`/`touch` identity where present, e.g. `target-touch-only`). Mirror the JS reference engine's
  reducer semantics. This is the project's "tolerant parser + golden fixtures" pattern (QrPayloadParser,
  calibration parsers) at its strongest.

### Claude's Discretion
- Exact Kotlin model shape for the normalized prompt state (the spec's normalized JSON is a conformance
  contract, not a required internal shape — D-14 only requires producing equivalent asserted fields).
- The PromptMarkup→AnnotatedString rendering internals.
- Overlay animation (must obey the no-continuous-animation Adreno rule; one-shot show/hide OK).
</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### The protocol (PRIMARY — this phase implements it)
- `/mnt/e/claude/personal/github/klipper-macro-prompt-protocol/SPEC.md` — Macro Prompt Protocol v1, the
  authoritative spec. Core commands, optional extensions, lifecycle, button parsing, targeting, sizes,
  images, PromptMarkup grammar, alignment, security model, disconnect semantics. READ IN FULL.
- `/mnt/e/claude/personal/github/klipper-macro-prompt-protocol/fixtures/fixtures.json` — the conformance
  test oracle: 26 renderer-neutral fixtures (`schema_version: 1`), tagged `[core]`/`[optional]`, each with
  `events` + `expected` normalized state. Port these as Kotlin tests (D-14).
- `/mnt/e/claude/personal/github/klipper-macro-prompt-protocol/fixtures/README.md` — how the fixture pack
  is structured + `expected_by_frontend` usage.
- `/mnt/e/claude/personal/github/klipper-macro-prompt-protocol/fixtures/macro-examples.cfg` — real Klipper
  macros for on-device manual testing (load-filament wizard, jog cluster, etc.).
- `/mnt/e/claude/personal/github/klipper-macro-prompt-protocol/packages/js/` — clean-room JS reference
  engine (the parser/reducer Dinghy's Kotlin should behaviorally match). Mirror its reducer semantics.

### Dinghy design + reuse
- `docs/ui_design/CLAUDE.md`, `docs/ui_design/LAYOUT.md`, `docs/ui_design/THEMING.md` — UI LAW. The
  markup-hex carve-out (D-03) must be documented here.
- `.planning/phases/08-macros-console-functional-core-complete/08-CONTEXT.md` + `08-RESEARCH.md` — the
  Console/gcode-response-stream design this phase consumes (D-04 raw stream, severity classifier).

### Note
This phase has NO gsd `*-SPEC.md`; the protocol `SPEC.md` above IS the requirements contract.
</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `state/PrinterStateStore.kt` → `store.gcodeResponses: SharedFlow<String>` — un-throttled raw gcode line
  stream (extraBufferCapacity 10000); the prompt engine subscribes here (D-13). Fed by
  `net/MoonrakerSession.kt` (`rpc.gcodeResponses → store.onGcodeLine()`), source `net/JsonRpcClient.kt`.
- `ui/console/ConsoleSeverity.kt` — already classifies `// action:` as `ACTION` BEFORE generic `//`.
  `ui/console/ConsoleFilters.kt` — already has `HIDE_PROMPT_COMMANDS` regex (`^(?:// )?action:prompt`), so
  prompt lines are hidden from the console view while the raw stream still carries them.
- `ui/macros/MacroExecutionPopup.kt` — the full-screen overlay popup precedent (param fields +
  Execute/Cancel); the prompt dialog follows this pattern.
- `designsystem/ConfirmGuard.kt`, `designsystem/SeverityToast.kt`, `designsystem/control/OutlinedControl.kt`
  — dialog/button/toast primitives to reuse.
- `command/CommandDispatcher.kt` → `dispatch(key, method, params)` — routes button gcode via
  `printer.gcode.script`; has in-flight debounce, timeout, failure→SeverityToast. Reuse for button presses.
- `theme/ThemeTokens.kt` (+ `theme/compose/LocalTokens.kt`, `fsSp(baseSp, fs)`) — token system + font
  scale. Dialog chrome uses tokens; markup text runs honor author hex (D-03). Mind the recurring
  too-small-font lesson — use the fsSp scale.
- Coil 3 (Phase 10 / Files) — downscale discipline for `prompt_image` rendering on the Adreno floor.
- Tolerant-parser exemplars: `ui/spool/scan/QrPayloadParser.kt`, `ui/console/ConsoleSeverity.kt`
  (total function, never throws) — the pattern for the prompt line parser.

### Established Patterns
- **AppShell overlay pattern (LOAD-BEARING):** overlays float OUTSIDE the `when(dest)` block in
  `ui/shell/AppShell.kt`, shown by hoisted state (e.g. `if (promptActive) { PromptDialog(...) }` after the
  routing block). The prompt dialog is an overlay, NOT a `Dest` in `ui/route/TopRoute.kt`.
- Headless state holder + pure parser, host-testable, no I/O — the project's universal shape.

### Integration Points
- New prompt-state holder subscribes to `store.gcodeResponses`; lives at the AppShell/spine level so the
  dialog can appear over any screen. Wires button presses to `CommandDispatcher`. Observes connection
  state to close on disconnect (D-10) — coordinate with the Phase-13 reconnect/connection-state plumbing.
</code_context>

<specifics>
## Specific Ideas

- This is the project owner's OWN protocol; Dinghy is intended to be a showcase / first native full-v1
  renderer. Fidelity to the spec matters — when in doubt, match the JS reference engine + fixtures.
- The spec is STABLE for v1 (repo paused at clean `main`, `prompt_align` merged, `prompt_input` reserved).
  Remaining upstream work is cross-frontend comparison, NOT v1 command-set changes — safe to build against.
- `expected_by_frontend` fixtures (e.g. `target-touch-only`) should be evaluated under Dinghy's identity
  (`dinghy` + `touch` + `all`).
</specifics>

<deferred>
## Deferred Ideas

- **Reset-then-replay reconnect recovery** — rebuilding an active prompt from gcode-store backfill on
  reconnect (the spec's "may" recovery). Deferred from this phase (D-10 = close + wait). Candidate future
  enhancement once value is proven; would lean on the Phase-8 backfill + Phase-13 reconnect.
- **Add `dinghy` to the spec target-name list** (D-09) — a one-line doc change in the owner's
  `klipper-macro-prompt-protocol` repo. Owner's repo, so allowed, but it's a separate cross-repo action,
  not Dinghy code. Do when convenient.
- **Feed Dinghy implementation learnings back to the spec** — being the first native renderer may surface
  ambiguities; capture them for the protocol's cross-frontend standardization (the paused effort in that
  repo). Not Phase-12 Dinghy scope.
- **`prompt_input`** — reserved in the spec, explicitly NOT v1. If/when the spec promotes it, a future
  Dinghy phase. Out of scope.

### Reviewed Todos (not folded)
- `2026-06-04-phase-11-spool-feature-robustness-hardening` — matched only on generic keywords; it's spool
  resilience work bound to Phase 22 (ship). Not Phase-12 scope.
</deferred>

---

*Phase: 12-macro-prompt-protocol*
*Context gathered: 2026-06-04*
