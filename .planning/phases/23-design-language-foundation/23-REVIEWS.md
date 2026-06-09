---
phase: 23
reviewers: [gemini, codex]
reviewed_at: 2026-06-09T21:16:58Z
plans_reviewed: [23-01-PLAN.md, 23-02-PLAN.md, 23-03-PLAN.md, 23-04-PLAN.md, 23-05-PLAN.md, 23-06-PLAN.md]
note: claude reviewer skipped (running inside Claude Code — excluded for independence); coderabbit/opencode/qwen/cursor not installed
---

# Cross-AI Plan Review — Phase 23 (Design-Language Foundation)

## Gemini Review

This is a comprehensive and high-quality set of plans for Phase 23 of the Dinghy Display project. The strategy of building the design-language foundation bottom-up, starting with layout primitives and documented law, and concluding with a real-world pilot on the `SpoolScreen`, is architecturally sound and effectively mitigates the "abstract kit" trap.

### 1. Summary
The plans faithfully execute the "jiib" redesign's lists-first and conditional-waterfall philosophy. The sequencing is excellent: Wave 0 establishes a non-bricking TDD baseline; Wave 1 locks the design law and icon registry; Waves 2–3 build the Compose kit; and Wave 4 validates the entire system against a complex, live-data screen. The strict adherence to the unit-grid U and the content-vs-control fill convention ensures visual consistency while respecting the performance floor of the Adreno 320.

### 2. Strengths
- **TDD discipline:** Wave 0 compiling RED scaffolds gradually turned GREEN ensures continuous integration and non-bricking builds.
- **Performance awareness:** Box gradients instead of ComposeShader for edge fades, avoiding blur filters on fill meters, mandatory `remember` keys for U derivation.
- **Design-law integration:** Rewriting LAYOUT.md and authoring COMPONENTS.md before implementation ensures shared precise vocabulary.
- **Orientation consistency:** Deriving U from the landscape dimension and holding it constant through rotation ensures physical tap-target parity.
- **Icon-registry integrity:** Strict registration of new Material Symbol ligatures prevents AI-chosen/hand-traced glyphs.

### 3. Concerns

**MEDIUM: `UnitGrid` formula fitting logic** — If N is calculated on a 41dp target then uDp floored at 64dp, total height (N×uDp) exceeds screen height on small screens. For a 360dp screen N=7, 7×64dp=448dp > 360dp. Could cause un-scrollable overflow / content pushed off-screen on phone-sized displays.

**MEDIUM: `ListBlock` edge-fade touch blocking** — An overlay Box above a LazyColumn swallows touch events unless explicitly made transparent to hits. The list will appear un-scrollable if fade boxes cover the hit area — critical UX failure at the on-device gate.

**LOW: `DetailCard` internal padding vs U alignment** — Fixed 16.dp padding may break tabular alignment if card content needs to align with rows outside the card. Cosmetic alignment drift on high-density displays.

### 4. Suggestions
- Refine UnitGrid: compute max N that fits given the 64dp floor before applying the 41dp target:
  ```
  val N_target = (contentMinDim / 41.dp).roundToInt().coerceIn(5, 7)
  val N_max_fit = (contentMinDim / 64.dp).toInt().coerceAtLeast(5)
  val N = minOf(N_target, N_max_fit)
  val uDp = contentMinDim / N
  ```
- Harden ListBlock overlays: use `Modifier.drawWithContent` for fades or ensure the overlay Box doesn't consume pointer input.
- Add a "Small Phone" 320/360dp preview panel to verify U derivation on the phone floor.
- Clarify SortRow indicator: ensure the arrow_upward/arrow_downward doesn't offset the primary icon (Box + Alignment.TopEnd).

### 5. Risk Assessment: LOW
Plans are exceptionally detailed, grounded in existing-analog research, with multiple validation gates (host unit tests, preview matrices, on-device UAT). The pilot-first approach is the strongest mitigation. Concerns are primarily mechanical implementation details addressable during execution.

---

## Codex Review

### Summary
The plans are thorough and mostly well sequenced, but they have several criteria-level gaps. The biggest risks are the `U` formula being internally inconsistent with the locked "5U phone-land → 7U tablet / constant through rotation / >=64dp" rule, the omission of the stepper/scrubber from a phase that still names them in SC-2, and unresolved icon-registry/API friction where components are required to use `DinghyIcon` but the planned control primitive appears to accept raw ligature strings. I would not execute as-is without tightening those.

### Strengths
- Clear wave ordering: docs/icons/layout/components/pilot mostly separated cleanly.
- Wave-0 RED scaffold plan correctly avoids references to not-yet-built symbols, preserving test-source compilation.
- The SpoolScreen pilot is the right proof surface: list, filter, detail, fill meter, actions, orientation, owner UAT.
- Plans repeatedly preserve key locked rules: no gutter, token-only chrome, `fsSp`, preview seed for large text, icon owner assignments.
- Human flox approval in both orientations is correctly blocking for the pilot.

### Concerns
- **HIGH: `UnitGrid` formula is wrong for the stated constraints.** `N = round(dim/41.dp).coerceIn(5,7)` then `uDp = dim/N` then `coerceAtLeast(64.dp)` can produce layouts that don't fit. dim=360dp → N=7, u=51dp → floors to 64dp → 7U=448dp exceeds content height. The formula must reduce N when the 64dp floor would overflow.
- **HIGH: "DPI-derived" is specified but the implementation is not DPI-derived.** The plan collapses U_target_from_dpi into a constant 41.dp, contradicting roadmap/research wording. If DPI matters, the pure helper needs a density/DPI parameter and host tests for it.
- **HIGH: Phase scope omits required kit pieces.** SC-2 and the phase goal name the stepper and scrubber, but the plans build neither. Research recommends deferring scrubber restyle, but SC-2 was not revised. This fails the phase definition unless SC-2 is amended or the plans include explicit stepper/scrubber work.
- **HIGH: Icon-registry discipline is underspecified at the control API boundary.** SortRow/FilterRow/FootButtonBar/FloatingEStop must use registered DinghyIcons, but the existing OutlinedControl pattern appears string-ligature based. Without a planned bridge, implementers will either pass raw strings or modify APIs outside the listed files.
- **HIGH: `SortFilterControlRow` API does not model the locked type-tile pattern.** Research says sort/filter rows have leading recessed type tiles (sort, filter_list) and no group-label words. The planned API only accepts option lists; this can silently ship a noncompliant control row.
- **MEDIUM: `ListBlock` is keyed but cannot enforce keys.** Accepts arbitrary `LazyListScope.() -> Unit`; callers can still use unkeyed `items`. The primitive doesn't guarantee the perf rule it claims.
- **MEDIUM: Preview plans may be non-buildable due to missing string resources.** 23-04/23-05 require stringResource/cd_* but their files_modified don't include strings.xml.
- **MEDIUM: Floating e-stop wiring is incomplete.** 23-06 adds isPrinting + e-stop overlay but doesn't identify the real print-state source, caller files, or emergency-stop callback path. If preview-only, it doesn't prove the printing-only rule.
- **MEDIUM: `minOf(maxWidth,maxHeight)` may not be constant through rotation once system bars/insets differ.** Sound only if measured at a stable root with equivalent content bounds. Add explicit U logging/assertion during flox rotation UAT.
- **LOW: CSS verification is narrow.** The grep catches one heat/outline mix pattern but may miss multiline or later overriding `.ctl.warn` rules. Fine as smoke check, not proof.

### Suggestions
- Replace the U helper with a fit-preserving algorithm: target count, then decrement while contentMinDim/count < 64.dp; keep count in [5,7]; explicitly handle impossible <320dp cases.
- Decide whether U is DPI-derived or dp-derived. If DPI-derived, make the helper take density/DPI and test phone/tablet examples directly.
- Add a plan task for stepper/scrubber OR formally edit SC-2 to defer them.
- Add a DinghyIcon-aware control/tile API, or extend OutlinedControl deliberately. Don't extract ligature strings ad hoc.
- Make SortFilterControlRow a real compound component with leading recessed DinghyIcons.Sort / DinghyIcons.FilterList type tiles.
- Add explicit FieldMode tests: filter tap opens field picker, option selection reverts, clear/show-all reverts.
- Include strings.xml in 23-04/23-05 if previews require new cd_* resources.
- Add an install/UAT command and a debug display/log for uDp in portrait vs landscape during owner verification.

### Risk Assessment
**Overall risk: HIGH until corrected.** Plan structure is strong, but the current U math can produce physically impossible layouts, the phase omits named success-criteria components, and the icon-registry/control API gap can lead to locked-rule violations or build churn. After fixing those, risk drops to **MEDIUM**: mostly normal Compose refactor risk plus real-device UX validation.

---

## Consensus Summary

Two independent reviewers (Gemini, Codex) agree the plan **structure and sequencing are strong** (bottom-up kit → pilot, Wave-0 RED scaffolds, perf-floor discipline, icon-registry integrity). They diverge on overall risk only because Codex weights the scope/API-spec gaps as execute-blockers while Gemini treats them as execution-time fixes.

### Agreed Strengths
- Wave-0 compiling RED scaffolds → non-bricking TDD baseline (both)
- Pilot-first (SpoolScreen) is the right proof surface and mitigates the abstract-kit trap (both)
- Locked-rule preservation: no gutter, token-only chrome, fsSp, preview seed for fs=L, icon owner assignments (both)
- Adreno-320 perf awareness — Box gradients over ComposeShader, no blur filters (Gemini)

### Agreed Concerns (highest priority — raised by BOTH)
1. **`UnitGrid` formula can produce non-fitting layouts** — Codex HIGH / Gemini MEDIUM. The 64dp floor applied AFTER choosing N from a 41dp target overflows on a 360dp phone (N=7 → 7×64=448dp > 360dp). **Both gave the same fix:** compute the max N that fits under the 64dp floor, then take `min(N_target, N_max_fit)`, so `N×uDp == contentMinDim` exactly. This is the clearest agreed show-stopper and is a one-function change in `UnitGrid.kt` + a new host test case.
2. **`ListBlock` edge-fade overlay** — both flag the primitive: Gemini (overlay Box swallows touch → list appears un-scrollable), Codex (can't enforce LazyColumn keys it claims). Harden the fade to not consume pointer input AND make the keying guarantee real (or document it as caller responsibility).

### Divergent Views (Codex-only HIGHs — worth a decision)
- **SC-2 names "stepper, scrubber" but the plans build neither** (deferred per research Open-Q §2). The plan-checker accepted the deferral, but SC-2 wording was never amended → the phase can't literally satisfy its own success criteria. **Decision needed:** amend SC-2 to formally defer stepper/scrubber to Phase 26, or add explicit (minimal) stepper kit work this phase.
- **Icon-registry at the control API boundary** — Codex warns OutlinedControl is ligature-string based with no planned bridge to registered DinghyIcons, risking raw-string passing or out-of-scope API edits. Gemini didn't flag it. Worth a planned DinghyIcon-aware control/tile API decision.
- **`SortFilterControlRow` may not model the locked leading-type-tile pattern** (Codex only) — API accepts option lists but not the recessed Sort/FilterList type tiles; could silently ship a noncompliant row.
- **Floating e-stop wiring** (Codex only) — needs a real print-state source + e-stop callback path, not just a preview-driven `isPrinting`, to actually prove the printing-only rule.

### Recommended disposition (per the codex-review-final-plans standing rule)
- **Fix now (agreed show-stopper):** the UnitGrid fit formula + host test (consensus, trivial, both gave the fix).
- **Decide before execute (borderline, ask owner):** the SC-2 stepper/scrubber reconciliation, the icon-at-control-API bridge, and the SortFilterControlRow type-tile spec — these are spec-tightening, cheap to fold into a `--reviews` replan.
- **Execution-time (note, don't necessarily replan):** ListBlock fade touch-transparency + key enforcement, strings.xml in 23-04/23-05 files_modified, e-stop wiring source, minOf-vs-insets U stability assertion at UAT, broader CSS grep.
