# Phase 25: Browse Screens - Context

**Gathered:** 2026-06-10
**Status:** Ready for planning

<domain>
## Phase Boundary

Migrate the four list/collection "browse" screens onto the **Phase-23 component-class kit** —
**Files**, **Macros** (Bookmarked launcher + System manage-visibility), **Console**, and **Webcam** —
reusing `ListRow`, `ListBlock`, `SortFilterControlRow`, `FootButtonBar`, `DetailCard`, `FillMeter`,
and the Field-takeover picker proven on the Phase-23 Spoolman pilot. Per-screen **conformance**
(≥64px touch targets or documented exception, `fsSp` S/M/L without clipping, portrait+landscape
rotation), `@Preview` matrices, and tokenized strings/registry icons fold into **each** migration —
there is no separate sweep.

**This is a UX migration phase, not a feature phase.** No new printer capability is added. The
navigation spine (Phase 24), the idle-list entry points, capability gating, and the holders
(`FileBrowserHolder` / `MacroHolder` / `ConsoleHolder` / `WebcamHolder`) already exist and are
NOT rebuilt — only the screen surfaces are. SpoolScreen is the reference implementation; do not
touch it. The adjustment screens (Temperature/Extrude/Outputs/Fine-Tune), motion/calibration, and
the System/Settings cluster are Phases 26–28, NOT here.

**Out of scope:** the stepper/scrubber restyle (Phase 26 — COMPONENTS.md §7 forbids touching them
earlier); any change to the SurfaceView/Bitmap webcam render path (Phase-21 work); the Macro Prompt
Protocol dialog primitive (Phase 12 — those are macro-emitted prompts, distinct from param entry);
console↔macros nav-flow refinements (deferred to Phase 29).

</domain>

<decisions>
## Implementation Decisions

### Files & Console toolkit — measure first (the consequential fork)
- **D-01: Wave-0 flox gfxinfo spike decides Views-vs-Compose PER SURFACE.** Files and Console are
  currently classic **RecyclerView-in-`AndroidView`** (ADR-0001: measured ~2× lower p95 frame time
  on the Adreno-320 floor; Phase 22 *retained* them as Views and added the D-12 `applyTokens`
  equality guards). The kit's `ListRow`/`ListBlock` are Compose-only, so "consume the classes" is
  either literal (Compose) or visual-equivalent (Views). **Resolution:** before migrating, build a
  throwaway Compose `LazyColumn` + literal `ListRow` version of *each* of the two surfaces and
  capture **release-mode gfxinfo on the real flox device**, comparing against the current Views
  implementation. Decide each surface independently on the data.
- **D-02: Pass/fail bar = the ADR-0001 Addendum-2 gate.** If the Compose version shows **0 frozen
  frames** and p90 frame time within the established floor budget (parity with, or better than, the
  current Views), **migrate that surface to literal Compose `ListRow`** (single-definition class
  consumption, simpler code). If it shows frozen frames or a meaningful p90 regression, **keep that
  surface as Views** and **conform it visually** — restyle the RecyclerView item views to MATCH the
  `ListRow` appearance (transparent/`outline` content fill, `fsSp`, token colors) and wrap them with
  Compose `SortFilterControlRow` / `FootButtonBar` siblings. A Views-conformed surface is documented
  in `COMPONENTS.md` as an explicit **"class-equivalent (Views)" exception** to the define-once rule,
  not an accidental divergence.
- **D-03: The spike is a hard gate, not advisory.** The measured result drives the choice; do not
  pre-decide the toolkit in planning. If results are ambiguous/borderline, surface to the owner with
  the captured numbers rather than guessing.

### Files screen anatomy
- **D-04: Spoolman two-pane shape.** Focus = an **image-backed `DetailCard`** of the selected file
  (dimmed thumbnail background + left-aligned icon-led stat lines: est time · filament · layers ·
  height · size · modified — the established "Files Focus = future-print fields only" grammar; never
  elapsed/finished/status). Field = `ListBlock` of file rows + `FootButtonBar`. Portrait stacks
  (detail → controls → list), landscape is Focus|Field side-by-side. This matches both the pilot and
  today's Files structure.
- **D-05: Flat list — NO folder navigation.** Show gcode files flat; do **not** build hierarchical
  folder drill-in / breadcrumb. (Owner steer, 2026-06-10.)
- **D-06: Sort by file AGE only, with direction toggle. NO filter facet, no name/size sort.** The
  sort control reduces to a single date/age metric (direction toggleable per the SortFilter
  direction-arrow convention). The owner explicitly cut the filter facet ("never mind for now, stick
  to file age as only sorting metric") — do **not** build folder/file-type/has-thumbnail filters this
  phase. Planner's discretion whether a one-metric sort warrants the full `SortFilterControlRow`
  (type-tile + single date tile) or a leaner age-direction toggle — keep it conformant either way.
- **D-07: FootButtonBar actions = the existing Files action set, restyled.** Start-print (through the
  full-screen Confirm guard showing thumbnail/details), Delete (red, destructive Confirm), Back. The
  existing **spool-warning-on-start** gate (`SpoolWarningGuard`) is preserved. Start is confirmed via
  the resulting `print_stats` state flip, not the command ack (unchanged behavior).
- **D-08: Delete-scoping is already shipped — fold as a regression CHECK, not new work.** The
  "Delete blocks ALL files during any print" defect was fixed in 09-06 (D-15): delete is scoped to
  the active print file via the shared `deleteAllowed(selectedPath, activePrintFilename, printState)`
  predicate, applied at both the screen gate and the holder dispatch gate. The migration must NOT
  regress this — re-verify the predicate survives the rebuild (and the on-device UAT check from
  `files-delete-gating-too-broad.md` runs in this phase's UAT).

### Macros
- **D-09: One screen — Bookmarked launcher PRIMARY + System "Manage" as an in-screen MODE.** The
  idle-list "Macros" entry (shown only if bookmarks exist, Phase 24 D-08) opens the Bookmarked
  launcher. The System manage-visibility list (pin/unpin, reveal-hidden) is reached as an in-screen
  mode via a **foot button**, not a separate nav route — they share `MacroHolder`. This **converts**
  the in-screen Bookmarked↔System sub-nav that Phase 24 D-01 explicitly left for this phase.
- **D-10: Bookmarked launcher = a `ListRow` LIST (content), not a tile grid.** Each bookmark is a
  translucent `ListRow`; tap to execute. (Owner chose lists-first uniformity over the
  control-tile-grid reading.) Today's `LazyVerticalGrid` of `MacroTile`s is replaced.
- **D-11: Density fix folded in.** Size the Bookmarked list so ~9-12 entries fill the Field
  responsively (portrait + landscape) and scroll only beyond that threshold — the list must not waste
  vertical space for the typical handful of bookmarks (folds `2026-06-05-bookmarked-macros-density`).
- **D-12: Macro Execution param entry → Field-takeover PER PARAM.** Replace the full-screen
  `MacroExecutionPopup` form with the Field-takeover pattern: the Field lists the macro's params
  (each a row), tapping one swaps the Field to that param's entry surface (NumpadPage for numeric,
  text entry for string), back returns, an Execute foot button fires. (Owner chose the
  grammar-consistent path over restyling the popup; lists-and-detail.md names macro-params as a
  canonical Field-takeover use.) **Keep the existing string-param sanitizer / injection-rejection**
  (08-03 `MacroInvocation`) on the dispatch path.
- **D-13: Macro PROMPT-protocol dialogs stay overlays (untouched).** The Phase-12 `// action:prompt_*`
  dialogs are macro-emitted runtime prompts, NOT pre-execute param entry — they remain floating
  overlays per Phase 24 and are out of scope for D-12. Do not conflate the two.

### Console
- **D-14: Field-only live log.** Console has no detail item and no sort — it is a single full-width
  Field (the scrollback), no Focus region (status quo). "Fill the usable space" is satisfied by the
  log filling the Field.
- **D-15: The 3 filters render as restyled `FootButtonBar` toggles.** Hide-temperatures /
  hide-timelapse / hide-prompts stay always-visible toggle buttons pinned at the foot of the log
  (NOT a Field-takeover picker, NOT a separate route — owner chose the simple always-visible
  toggles). Restyle to the component classes + tokens. **D-04 from Phase 8 is preserved:**
  `ConsoleHolder` keeps the RAW unfiltered scrollback; `ConsoleFilters` are applied at RENDER time
  only, so toggling re-reveals hidden lines without starving the source.

### Webcam
- **D-16: Token-conform only — NO restructure, render path UNTOUCHED.** Keep the current
  Column-picker structure; re-token the chrome and add a `FootButtonBar` (Back + any existing
  actions). The SurfaceView (H.264) / Bitmap (MJPEG) render path and the rung-select / cam-cycle
  logic stay exactly as-is — the Phase-21 native-streaming work is not reopened. (Owner chose the
  light touch over restructuring the picker into a `ListRow` list.)
- **D-17: Webcam-screen crash fix is MANDATORY (not optional).** The screen currently crashes on
  flox (`2026-06-05-webcam-screen-crash`, never diagnosed). A crashing screen cannot be
  owner-approved on flox (SC-1) and would be a functional regression (SC-5), so the fix is required
  regardless of the light-touch styling scope: capture the logcat stack trace first
  (`adb logcat` while opening the Webcam tile), then fix. Also re-verify the latent WR-02 webcam-URL
  resolver bug from `2026-06-05-webcam-tile-gating-verification` (resolver was reading the write-dead
  `connectionStore` instead of `container.activeConfig`) — Phase 21 may already have repointed it
  (the nested ravens-perch schema read, commit `a254469`); confirm it follows the active profile, fix
  if not.
- **D-18: Verify idle-list HIDE gating on-device.** Phase 24 D-08 changed webcam from a greyed drawer
  tile to **HIDE-from-idle-list when the per-profile toggle is off**. Run the deferred on-device
  verification (`2026-06-05-webcam-tile-gating-verification`) against that current behavior: toggle
  webcam per-profile, switch printers → independent state; idle-list Webcam row appears/disappears
  correctly.

### Cross-cutting (apply to every screen)
- **D-19: Conformance folds into each migration** — ≥64px targets (or a documented exception),
  `fsSp` S/M/L without clipping, correct portrait↔landscape rotation. Not a separate sweep (SC-3).
- **D-20: Preview-first + tokenized-first** — every migrated screen ships an `@Preview` matrix (the
  Phase-18 6-combo + `fs=L` shape, no live Moonraker), `stringResource` strings, and `DinghyIcons`
  registry glyphs (SC-4).
- **D-21: Icon law — never auto-pick.** If a migrated screen needs a glyph not already selected (in
  `DinghyIcons`, an existing screen, the hi-fi mockups, or an `img/` source asset), **STOP and ASK
  the owner.** Do not invent a drawable or choose a Material Symbol independently.

### Claude's Discretion
- The exact spike harness shape for D-01 (throwaway scene vs. a toggle in the real screen), and how
  the per-surface decision is recorded.
- Whether Files' single-metric sort uses the full `SortFilterControlRow` or a leaner date-direction
  control (D-06) — keep it conformant.
- The Macros "Manage mode" toggle affordance (a foot button mode-swap vs. a Field-takeover mode list)
  and how the Execute foot button coexists with the param Field-takeover (D-12).
- `NavHost` route shape for the rebuilt screens (they already have nav entry points from Phase 24);
  holder hoisting/lifetime is already settled by Phase 24 — preserve it.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### jiib redesign LAW (the design contract — read FIRST)
- `.claude/skills/sketch-findings-dinghy-display/SKILL.md` — redesign design-direction index
  (auto-load before building any redesigned UI).
- `.claude/skills/sketch-findings-dinghy-display/references/lists-and-detail.md` — **THE primary spec
  for this phase**: the Spoolman collection/detail archetype, sort-vs-filter (never conflated),
  Field-takeover picker, `DetailCard` + `FillMeter`, `ListRow` anatomy, "what to avoid."
- `.claude/skills/sketch-findings-dinghy-display/references/foundations.md` — unit grid `U`,
  content/control fill convention, intent colors, icon registry, Focus/Field-no-gutter.
- `docs/ui_design/COMPONENTS.md` — **the component-class catalog (UI LAW)**: `ListRow`, `ListBlock`,
  `SortFilterControlRow` (LOCKED compound anatomy), `FootButtonBar`, `DetailCard`, `FillMeter`,
  Field-takeover picker §5, fill convention §2, unit `U` §4. §7 = stepper/scrubber restyle FORBIDDEN
  until Phase 26.
- `docs/ui_design/LAYOUT.md` — two-region Focus/Field grammar (gutter removed), unit `U` formula,
  foot-of-list, NON-NEGOTIABLES (one shared grid, sacred aspect ratios, ratio-only sizing).
- `docs/ui_design/THEMING.md` — semantic tokens, button-intent-by-safety colors, `--fs` S/M/L,
  THEME-01 data carve-outs.
- `docs/ui_design/CLAUDE.md` — design non-negotiables; **icon never-auto-pick law (D-21)**; the
  "image-backed info card" grammar (Files Focus, D-04); the delete-scoping rule (D-08); the
  scrollable-Field-suppresses-swipe-drawer rule; "content images fit, don't crop."
- `docs/ui_design/PREVIEW_AND_TOKENS.md` — `@Preview` matrix shape, `fsSp` usage (D-20).

### The reference implementation (the pilot — DO NOT modify; copy the patterns)
- `app/src/main/java/works/mees/dinghy/ui/spool/SpoolScreen.kt` — Phase-23 pilot: pure Compose
  two-pane (`DetailCard` + `FillMeter` Focus / `ListBlock` of `ListRow` + `FootButtonBar` Field),
  Field-takeover filter picker, `FloatingEStop` wired to live state. The template for Files (D-04).
- `app/src/main/java/works/mees/dinghy/designsystem/components/` — `ListRow.kt`, `DetailCard.kt`,
  `FillMeter.kt`, `FootButtonBar.kt`, `FloatingEStop.kt`, `SortFilterControlRow.kt`.
- `app/src/main/java/works/mees/dinghy/designsystem/layout/` — `ListBlock.kt` (edge-faded LazyColumn),
  `UnitGrid.kt` (`rememberUnitGrid`).
- `app/src/main/java/works/mees/dinghy/designsystem/control/OutlinedControl.kt` — control tile + Intent.

### Screens being migrated (current implementations)
- `app/src/main/java/works/mees/dinghy/ui/files/FilesScreen.kt` (680 lines) +
  `FileListView.kt` (RecyclerView-in-AndroidView, the D-01 spike target) + `FileBrowserHolder.kt`.
- `app/src/main/java/works/mees/dinghy/ui/macros/BookmarkedMacrosScreen.kt` (LazyVerticalGrid → list,
  D-10) + `SystemMacrosScreen.kt` + `MacroExecutionPopup.kt` (→ Field-takeover, D-12) + `MacroHolder.kt`
  + `MacroParamParser` / `MacroInvocation` sanitizer (08-03).
- `app/src/main/java/works/mees/dinghy/ui/console/ConsoleScreen.kt` (257 lines) +
  `ConsoleListView.kt` (RecyclerView-in-AndroidView, the D-01 spike target) + `ConsoleHolder.kt`
  (raw lines) + `ConsoleFilters` (render-time, D-15).
- `app/src/main/java/works/mees/dinghy/ui/webcam/WebcamScreen.kt` (267 lines) + `WebcamHolder.kt`
  (render path NOT to be touched, D-16).

### Nav + perf context
- `.planning/phases/24-navigation-spine/24-CONTEXT.md` — D-01 (convert Macros sub-nav HERE), D-04
  (Files/Console/Webcam stay valid mid-print; no pop-to-root), D-08 (idle-list HIDE gating), holder
  hoisting above the NavHost.
- ADR-0001 (`docs/adr/0001-ui-toolkit-decision.md`) + Addendum-2 — the Views-for-Files/Console
  rationale + the **no-frozen-frames / p90 floor budget** that is the D-02 spike pass/fail bar.
- `docs/moonraker-capabilities.md` — `print_stats.filename` path form for the delete-scoping match (D-08).

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- **Phase-23 kit** (`ListRow`/`ListBlock`/`SortFilterControlRow`/`FootButtonBar`/`DetailCard`/
  `FillMeter`/`rememberUnitGrid`/`OutlinedControl`) — the entire visual vocabulary for this phase;
  SpoolScreen exercises every class.
- **Holders are done** — `FileBrowserHolder`, `MacroHolder`, `ConsoleHolder`, `WebcamHolder` expose
  StateFlow and are NOT rebuilt; only the screen composables that consume them change.
- **`MacroInvocation` sanitizer + `MacroParamParser`** (08-03, verbatim Mainsail regex) — reused under
  the new Field-takeover param entry (D-12).
- **`deleteAllowed(...)` shared predicate** (09-06) — the delete-scoping gate to preserve (D-08).
- **`ConsoleFilters` render-time view** (08-05, D-04) — reused; only the toggle UI restyles (D-15).

### Established Patterns
- **RecyclerView-in-AndroidView with pinned height + `clipToBounds()`** (Files `FileListView.kt:49`,
  Console `ConsoleListView.kt:54`) — the load-bearing over-measure workaround. If D-02 keeps a surface
  as Views, this pattern (and the D-12 `applyTokens` equality guard from Phase 22) stays.
- **Overlays float outside the destination** (MacroExecutionPopup today, prompt dialogs, ScanSurface) —
  prompt dialogs (D-13) keep floating; param entry moves INTO the Field (D-12).
- **Scrollable Fields suppress the swipe-up drawer** — preserve for Files/Console/Macros/Webcam.

### Integration Points
- Idle-list + drawer entry points (Phase 24) already route to all four screens — the rebuilds slot in
  behind the existing nav entries; don't re-wire navigation, just preserve the entry contracts.
- `PrinterState.printState` → e-stop visibility + (for Files) the start/delete confirm flow; these
  screens are NOT pop-to-root foot-guns (Phase 24 D-04).
- Capability flows gate webcam idle-list membership (D-18) and Spoolman presence (Files spool-warning).

</code_context>

<specifics>
## Specific Ideas

- **Files Focus = Spoolman's image-backed DetailCard, future-print fields only** (est time · filament ·
  layers · height · size · modified). Filenames in Geist Mono.
- **Files: flat, age-sorted, no filter** — explicit owner minimalism ("stick to file age as only
  sorting metric"). Don't add folder nav or filter chrome back in.
- **Macros: lists everywhere** — Bookmarked launcher is a `ListRow` list (not tiles), sized to fill
  ~9-12 then scroll.
- **Console: keep the simple always-visible filter toggles**, just restyled — owner rejected the
  Field-takeover/sub-page for filters.
- **Webcam: lightest touch** — owner rejected restructuring; re-token + FootButtonBar, render untouched,
  but the crash MUST be fixed.

</specifics>

<deferred>
## Deferred Ideas

- **Console↔Macros nav-flow refinements** (`console-macro-page-ux-flow`): auto-jump to Console after
  firing a macro, and a direct Console↔Macros nav button. Owner chose to **defer both to Phase 29
  (ship polish)** — this phase stays a pure visual migration.
- **Files filter facets** (folder/location, has-thumbnail, file-type) — cut from this phase (D-06);
  revisit only if real filtering need emerges. The Field-takeover folder-filter idea is recorded for a
  future milestone if folder organization becomes important.
- **Webcam picker as a true `ListRow` list / restructure** — deferred (D-16 chose token-conform only).
- **Console filters as a Field-takeover / dedicated sub-page** — deferred (D-15 kept foot toggles).

### Folded Todos
- `2026-06-05-bookmarked-macros-density.md` → **D-11** (size Bookmarked list ~9-12 fill→scroll).
- `files-delete-gating-too-broad.md` → **D-08** (already fixed in 09-06; folded as a regression check +
  on-device UAT item).
- `2026-06-05-webcam-screen-crash.md` → **D-17** (mandatory crash fix; capture logcat first).
- `2026-06-05-webcam-tile-gating-verification.md` → **D-17/D-18** (latent WR-02 URL-resolver check +
  on-device idle-list HIDE-gating verification).
- `console-macro-page-ux-flow.md` (Console filter sub-page portion) → **D-15** decided against a
  sub-page; the nav-flow portions deferred (see above).

### Reviewed Todos (not folded)
- The broader Phase-14/15.1 deferred code-review findings, Printers edit/delete-mode, Settings
  densify, Move Z-layout, increment-picker — all belong to other screens/phases (26–28), not the
  browse screens. Not folded.

</deferred>

---

*Phase: 25-browse-screens*
*Context gathered: 2026-06-10*
