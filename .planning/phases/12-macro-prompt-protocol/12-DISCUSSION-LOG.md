# Phase 12: Macro Prompt Protocol - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-06-04
**Phase:** 12-macro-prompt-protocol
**Areas discussed:** Conformance scope, Markup vs token LAW, Dialog on Dinghy grammar, Identity/targeting & reconnect

**Mid-discussion scope input (owner):** "We've been developing our own add-on spec to the klipper prompt
protocol and want to bake in support for it into Dinghy. Should already be compliant with the standard
prompt protocol." → The phase implements the owner's `mrmees/klipper-macro-prompt-protocol` **v1**, whose
"add-on" content (button groups promoted to core; rows, images, markup, targeting, size, prompt_align as
optional v1 extensions) is now part of the v1 spec. Spec confirmed STABLE (repo paused at clean `main`).

---

## Conformance scope

| Option | Description | Selected |
|--------|-------------|----------|
| Full v1 | Core + ALL optional extensions; all 26 fixtures | ✓ |
| Core + lightweight extensions | Core + targeting/size/align/live-append; defer images/markup/rows | |
| Core v1 only | Portable baseline only; all extensions degrade | |

**User's choice:** Full v1
**Notes:** Dinghy as the first native full-v1 renderer of the owner's own spec. Heavy bits (images, markup)
accepted; spec's graceful-degradation design + the 26-fixture oracle make it tractable.

---

## Markup vs token LAW

| Option | Description | Selected |
|--------|-------------|----------|
| Honor author hex (carve-out) | Prompt content = macro-authored data, exempt from no-raw-colors LAW | ✓ |
| Map hex → nearest token | Snap colors to semantic tokens; theme-aware but distorts intent | |
| b/i/u/size only, drop color | Render formatting, strip color | |

**User's choice:** Honor author hex (carve-out)
**Notes:** Same precedent as Spoolman spool colors (real hex already rendered). Carve-out to be documented
in docs/ui_design/. Dialog chrome still uses tokens; only markup text runs honor author color.

---

## Dialog on Dinghy grammar

| Option | Description | Selected |
|--------|-------------|----------|
| Honor prompt_size (mapped) | Map size ladder to envelope widths | |
| Always full-screen overlay | Render full-screen; clamp size per kiosk policy | ✓ |
| Two fixed sizes | Collapse ladder to compact/large | |

**User's choice:** Always full-screen overlay
**Notes:** Key reconciliation captured as D-06 — `prompt_size` is still PARSED + tracked in normalized
state (size fixtures assert it) but the RENDERER clamps to full-screen. Spec explicitly permits clamping
on kiosk/touch. Stays conformant against all 26 fixtures while rendering wall-tablet-appropriately.

---

## Identity, targeting & reconnect

| Option | Description | Selected |
|--------|-------------|----------|
| Implement targeting; claim dinghy + touch | Match all/touch/dinghy; add `dinghy` to spec | ✓ |
| Implement; claim touch only | Match all/touch, no new ID | |
| Skip targeting (match-all) | Don't implement prompt_target | |

**User's choice:** Implement; claim dinghy + touch (+ all). Add `dinghy` ID to the spec (owner's repo).

| Option | Description | Selected |
|--------|-------------|----------|
| Full reset-then-replay | Rebuild prompt from gcode-store backfill on reconnect | |
| Close + wait for next prompt | Spec-required minimum; macro [delayed_gcode] resume covers mid-workflow | ✓ |

**User's choice:** Close + wait for next prompt
**Notes:** Disconnect performs the spec's reset (clear active prompt + pending target/size + open
containers). Reset-then-replay deferred as a future enhancement.

## Claude's Discretion

- Internal Kotlin normalized-state model shape (fixtures assert fields, not the internal type).
- PromptMarkup → Compose AnnotatedString rendering internals.
- One-shot overlay show/hide animation (must obey the no-continuous-animation Adreno rule).

## Deferred Ideas

- Reset-then-replay reconnect recovery (future enhancement).
- Add `dinghy` to the spec target-name list (one-line doc change in the owner's protocol repo).
- Feed Dinghy implementation learnings back to the spec's cross-frontend standardization effort.
- `prompt_input` — reserved, explicitly not v1; future phase if/when promoted.
