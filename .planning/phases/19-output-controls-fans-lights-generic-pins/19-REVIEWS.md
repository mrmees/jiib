---
phase: 19
reviewers: [codex]
reviewed_at: 2026-06-07
plans_reviewed: [19-01-PLAN.md, 19-02-PLAN.md, 19-03-PLAN.md, 19-04-PLAN.md, 19-05-PLAN.md, 19-06-PLAN.md, 19-07-PLAN.md, 19-08-PLAN.md]
note: "Single external reviewer (codex). claude skipped for independence (running inside Claude Code). gemini/opencode/qwen/cursor/coderabbit not installed. Findings cross-checked against the live codebase by the orchestrator — see Consensus Summary."
---

# Cross-AI Plan Review — Phase 19

## Codex Review

**Summary**

The plans are strong and unusually well-grounded: they use real printer research, fixture-driven tests, explicit icon decisions, a staged wave structure, and a live UAT gate. I would not execute them unchanged, though. The main risks are not product-scope risks; they are integration correctness risks: command names vs status object keys, missing `CommandRegistry` specs, an unsupported "dispatch on scrubber settle" assumption, and wave/test dependency conflicts.

**Cross-Cutting Concerns**

- **HIGH: Command name model is ambiguous and likely wrong in places.** Live state keys are full object names like `fan_generic FILTER_fan`, but Klipper G-code commands usually expect the bare section name, e.g. `FAN=FILTER_fan`, `LED=chamber_light`, `PIN=mosfet2`. Several plans say to call builders with `rawName`. Add separate fields: `objectKey = "fan_generic FILTER_fan"` for state, `commandName = "FILTER_fan"` for G-code.
- **HIGH: `CommandRegistry` is missing from the command plans.** Existing architecture registers gcode specs in `CommandRegistry.all` with args, availability predicates, dispatch keys, and drift tests. 19-03 only adds `PrinterCommands` builders, and 19-06 dispatches raw `GCODE_SCRIPT`. Add output args/specs/tests.
- **HIGH: `ScrubberPage` does not support settle-dispatch.** Current API has continuous `onValueChange` plus visible Cancel/Apply. 19-06 treats hiding Apply / pointer-up dispatch as a wiring detail, but it likely requires modifying `ScrubberPage` or creating an output-specific scrubber variant.
- **HIGH: Wave-0 RED tests conflict with later "full suite green" gates.** 19-02 creates failing `OutputsHolderTest` and `PrinterCommandsOutputsTest`. 19-04 runs the full suite but does not depend on 19-03 or 19-05, so it can fail from unrelated still-red scaffolds.
- **MEDIUM: configfile failure/staleness needs explicit clearing.** Output descriptors are hardware-control capability state. On configfile read failure or reconnect to a different printer, call `setOutputDescriptors(emptyList())`; do not leave stale output controls visible.
- **MEDIUM: LED behavior needs tighter definition.** Specify how live `color_data[0]` maps to row brightness/swatch and initial hue. Consider `rgbToHsv`, and decide whether color dispatch always sends `WHITE=0` to avoid stale white-channel state.

**Per-Plan**

- **19-01 — LOW.** Good isolation of icon work; verifies font before wiring; D-08 `FanMode` reassignment is symbolic so the call site need not change. Minor: `verify_ligatures.py` is manually curated (already has drift); ExtrusionScreen fan-glyph is grep-verified only. Suggest deriving the ligature NEEDED set from `DinghyIcons.all`; keep `mode_fan` in the gate for regression knowledge.
- **19-02 — MEDIUM.** Real fixtures + compiling RED scaffolds are the right discipline. But: "byte-for-byte server output" conflicts with saving only `result.status.configfile.settings`/`result.objects` (JSON can't carry a header comment); full config fixtures may disclose unrelated macro/config detail vs the threat model's "output-section JSON only"; **intentionally failing tests in the shared test source make later full-suite gates fail until every scaffold is replaced.** Suggest a passing fixture-shape smoke test + keep future tests `@Ignore`/scoped until implemented; commit sanitized/minimized real-shape fixtures or explicitly accept full captures.
- **19-03 — MEDIUM-HIGH.** Clamp-before-format is exactly right; tests cover scaling/clamp/LED-Off/`pwm_tool→SET_PIN`. But: **missing `CommandRegistry` args/specs/all-list/tests**; **builders must take `commandName`/bare section name, not full object key**; public "wire precision helper" needs exact pending-state semantics (display percent / wire double / formatted precision must not drift). Add `SetGenericFanArgs`/`SetLedArgs`/`SetServoArgs`/`SetOutputPinArgs` (or one typed output arg) + `CommandRegistryGcodeTest`.
- **19-04 — HIGH.** Discovery algorithm well chosen; pure `parseOutputs` with real fixtures is good. But: **update `deriveSubscribeSet`, not ad-hoc session code** (subscribe happens before the configfile read, so output live objects must be derived from `objects.list`); descriptor needs both full `objectKey` and bare `commandName`; `heater_generic` is already in `PrinterState.heaters` — duplicating in `outputs` risks divergence unless tested; **configfile read failure must clear descriptors.** Add `DeriveCapabilities.kt` to `files_modified`; define `OutputLiveValue` merge tests for partial diffs / null-absent.
- **19-05 — MEDIUM.** Keeps display scaling out of the reducer; good row-degrade rule; preview-first appropriate. But `markPending` is under-specified per type — **servo angle cannot be confirmed as angle from live status.** Define `reached()` per family (fan/pin/heater/LED/pwm; skip or timeout-only for servo); rename VM fields to distinguish `displayValue`/`swatchColor`/`isSettable`/`busy`.
- **19-06 — HIGH.** Preserves no-ConfirmGuard/immediate-dispatch/toast grammar; reuses ColorWheel. But: **`ScrubberPage` can't dispatch on pointer-up without API changes**; **detail pages must not pass full `rawName` to G-code builders**; **missing `CommandRegistry` usage bypasses the catalog/availability**; servo "Disable" is required by staging but the plan says omit-unless-trivial, contradicting the must-have "Every detail page has Off/zero"; LED initial-state/white-channel handling imprecise. Add a `ScrubberPage` variant/param (`actions = None`/`onSettle`) or `ImmediateScrubberPage`; add a servo disable builder if live-verified (likely `SET_SERVO SERVO=<name> WIDTH=0`); add LED command-gen tests + a no-per-frame-dispatch test.
- **19-07 — MEDIUM.** Correctly treats D-10 as hidden (not greyed); local list/detail stack mirrors shell patterns. But the drawer uses raw symbol strings today while icon work creates `DinghyIcons.OutputSection` — decide raw-string vs registry token; `outputsPresent` must be scoped to the current `SpineHandle`/store, not stale process state. Extract a pure `visibleDrawerTiles(...)` helper + test; reset selected output when its descriptor disappears.
- **19-08 — LOW-MEDIUM.** Treats host tests as necessary-but-insufficient; UAT covers rich + sparse printers + state-flip evidence. But **no live `heater_generic`** means one whitelisted family isn't UAT-proven; "trigger a failure" needs a safe deterministic method. Record `heater_generic` as automated-only/accepted; make `/server/gcode_store` command-evidence required (not optional) for at least fan/LED/pin/servo.

**Overall Risk Assessment: MEDIUM-HIGH.** The phase design is sound and likely achieves the four success criteria after correction. As written, the plans have several execution blockers and one likely live-command failure mode. Fix the descriptor naming split, add `CommandRegistry` specs, make immediate scrubber dispatch a real API/design task, and repair the wave dependency/test gates before execution.

---

## Consensus Summary

Single external reviewer (Codex). The orchestrator cross-checked each HIGH finding against the live codebase; results below.

### Confirmed against the codebase (all 4 HIGH findings hold)

1. **Command name vs object key (HIGH) — REAL, partly mitigated.** 19-04's descriptor already carries `bare = rawName.substringAfter(' ')` and 19-03's builders are tested with the bare name (`setGenericFan("FILTER_fan", …)`). BUT 19-06 dispatches `buildCommand(value)` while threading `rawName`, without stating that commands are built from `descriptor.bare`. Fix: make every detail page build the G-code from `descriptor.bare` and use `rawName`/objectKey only for the state map + dispatch/busy key. Add an explicit builder test that the family prefix never reaches the wire.

2. **CommandRegistry bypass (HIGH) — REAL, NOT borderline.** Confirmed: the claimed analog `ExtrusionScreen.kt` dispatches via `CommandRegistry.setFan` / `CommandRegistry.flowFactor` / `CommandRegistry.setPressureAdvance` (lines 103-105), i.e. registered `CommandSpec`s in `CommandRegistry.all` — NOT raw GCODE_SCRIPT strings. 19-03/19-06 plan to build raw strings and dispatch GCODE_SCRIPT directly, bypassing the catalog the analog uses. Fix: add output `CommandSpec`s (args + availability + dispatch key) to `CommandRegistry.all` with drift tests, and dispatch through them like Fine-Tune does.

3. **ScrubberPage settle-dispatch (HIGH) — REAL, acknowledged-but-under-specified.** 19-06 names the Apply/Cancel-vs-immediate conflict and offers an "OR" (wire `onApply`+hide Apply OR debounce `onValueChange`). Fix: make this a concrete API/design task — either a `ScrubberPage` parameter (`actions = None` / `onSettle`) or an `ImmediateScrubberPage` variant — not a wiring footnote. ScrubberPage signature is at lines 87-99.

4. **Wave-0 RED vs full-suite-green gate (HIGH) — REAL blocker.** 19-02 commits intentionally-failing scaffolds; VALIDATION.md's per-wave sampling says "full suite green," and 19-04 (Wave 1) runs the full suite without depending on 19-03/19-05. The red scaffolds make any intermediate full-suite gate fail. Fix: scope every intermediate gate to `--tests <specific class>` for the classes that wave actually turns green, OR mark not-yet-implemented scaffolds `@Ignore` until their owning plan fills them; reserve the true full-suite gate for the final pre-UAT step (19-08).

### Agreed Strengths
- Live-printer-grounded research + real Moonraker fixtures (closes the mock-vs-reality trap).
- Clamp-before-format / single-source clamp authority (the Phase-17 lesson) is correctly applied.
- Owner-locked icon isolation (19-01) + symbolic D-08 reassignment.
- D-10 handled as hidden-not-greyed; immediate-dispatch / no-ConfirmGuard grammar preserved.
- Live UAT gate on rich (E5P) + sparse (E3P) printers with state-flip evidence.

### Highest-priority concerns (fix before execute)
- Build G-code from the **bare** section name, never the full object key (likely live-command failure otherwise).
- Route output commands through **CommandRegistry specs** (match the Fine-Tune analog) instead of raw GCODE_SCRIPT.
- Promote **settle-dispatch** to a real ScrubberPage API/variant task.
- Fix the **RED-scaffold vs full-suite-gate** sequencing (scoped `--tests` or `@Ignore`).

### Secondary (MEDIUM — address in replan)
- Clear output descriptors on configfile read-failure / printer switch (no stale controls).
- Update `deriveSubscribeSet` for output live objects (derived from `objects.list`, pre-configfile).
- Resolve `heater_generic` duplication vs `PrinterState.heaters` (single source or tested divergence).
- Define `markPending`/`reached()` per family; servo can't confirm angle (timeout-only).
- Tighten LED `color_data`→swatch/brightness mapping + white-channel policy (`WHITE=0`).
- Servo Off/Disable vs the "every page has Off/zero" must-have — reconcile (likely `SET_SERVO … WIDTH=0` if live-verified).
- Drawer tile: decide raw-symbol-string vs `DinghyIcons` token; scope `outputsPresent` to the active store.

### Divergent Views
None — single reviewer.
