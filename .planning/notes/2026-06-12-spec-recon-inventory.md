# Spec Reconciliation Inventory — Step 1 of the Visual Normalization Sweep

**Date:** 2026-06-12 · **Feeds:** owner ruling rounds → `docs/ui_design/` rewrite
**Source review:** all six docs read in full + code spot-checks (DinghyIcons, PrintStatusField,
ScrubberPage usage, sketch skill sources).

Legend: **[A]** mechanical doc bug (no ruling — fix to match reality) · **[B]** already-ruled,
needs propagation · **[Q]** policy question for Matthew · **[G]** law gap (de-facto value to
ratify) · **[V]** verify in code during audit.

---

## A. Mechanical doc bugs (fix without asking)

### README.md (pre-redesign throughout — fate itself is Q2)
- §"layout grammar in one paragraph": three-region Focus/Field/Gutter, 40/40/20 — dead grammar.
- §2 App Drawer as "the app's primary navigation" — drawer deleted (Phase 28).
- §§3–9 per-screen specs: gutter button assignments everywhere; Move "Back (green)" and
  Temperature "Back (red)" contradict D-10 Back=neutral; §7 full-height fill-bar scrubber
  contradicts UAT-3 (≤1U) and the Phase-26 step-based adjusters.
- §Interactions: "swipe-up opens the App Drawer" — dead. "No alphanumeric keyboard, anywhere" —
  superseded by the Save-name carve-out + Settings-screen keyboard rule.
- §Motion: "status dot breathes" / "logo pulses" — violates the no-continuous-animation law.
- §Assets: "icons: inline SVG, hand-drawn, no icon-library dependency" — superseded by Material
  Symbols font + DinghyIcons registry law.
- Brand: "Dinghy Display" wordmark — app is jiib (18.2).
- `images/02-app-drawer.png` listed as a current screen.

### docs/ui_design/CLAUDE.md
- L38–44 intent rule "(esp. the gutter)"; L45–49 Back "consistent **gutter** POSITION" +
  "DEFERRED to Phase 15.2" deferral note (see Q6); L74–77 dense-cells "(the Gutter is exempt)";
  L78–81 "Scrollable Fields disable the swipe-up drawer" — drawer dead, delete/rewrite.
- L148–156 hi-fi section still points at `hifi.css` / `Print Status Hi-Fi.html` as canonical
  (see Q3). Title says "Dinghy Display — project notes" (brand).

### LAYOUT.md (current law, three orphans)
- L213 flexible-tile table: "Drawer (always last; the swipe-up App Drawer's tap alternative)" —
  Drawer tile retired in 28-05 (System rehomed). Also stale KDoc at `PrintStatusScreen.kt:54`.
- L288–290 "Scroll Field ⇒ no swipe-up drawer" — suppression of a deleted affordance.
- L129 "Settings/config surfaces (C6) are exempt" — ambiguous against its own rewritten C6
  (All-1U); tighten to "exempt from density expectations, NOT from the 1U floor."
- §Floating e-stop / Phase-16 rule: future-tense phase notes ("Phase 23 builds…", "Phase 16 D-?").

### THEMING.md
- L112 "especially governs the **gutter**"; worked examples (L191–201) describe pre-redesign
  Move/Extrude gutters — rewrite against the rebuilt screens' FootButtonBars.
- Status Shape Vocabulary: "octagon-✕ (`ic_status_octagon`)" — actual law since 18.1 is
  `StatusStop` = `disabled_by_default` (square-✕, shape-distinct, NOT octagon; drawable deleted).
  Update table + console-severity references. **[B]** (owner-curated bucket already ruled this).
- C2 note "(component deferred — see AUDIT R1 / Phase 17)" — stepper/IncrementPicker built in 26.

### PREVIEW_AND_TOKENS.md
- L10 "Sibling LAW: LAYOUT.md (Focus/Field/Gutter grammar)" — stale phrase.
- §10 backfill pointers reference the OLD Phase-22 (now the perf refactor) — the backfill home
  no longer exists (see Q12).

---

## B. Already-ruled contradictions to propagate (cite the ruling, rewrite the losers)

1. **C6 "All 1U" (owner UAT ruling, Phase 28, 2026-06-12).** LAYOUT.md C6 is correct.
   **THEMING.md C7-section C6** still says "EXEMPT from the ≥64px touch minimum… tighter rows";
   **COMPONENTS.md §4** still says the unit grid "does NOT apply to Settings-class screens…
   ignore the touch-floor minimum." Both lose; rewrite to All-1U densification.
2. **Status glyphs** (18.1 owner glyph map): square-✕ `StatusStop` / triangle `warning` — fix
   THEMING.md vocabulary table.
3. **No-breathing motion law** vs README's breathing dot / pulsing logo — no-breathing wins.
4. **Keyboard carve-outs** (Save-name fields; Settings screen) vs README's "anywhere" — carve-outs win.
5. **Brand = jiib** (18.2) — retitle docs.

---

## C. Policy questions for ruling rounds [Q]

### Round 1 — structural law
- **Q1 — `ScreenScaffold.gutter` slot fate.** Law says backward-compat only; in code ~20 screens
  still carry the slot and `PrintStatusGutter` is LIVE on Printing/Paused. Options: (a) retire the
  slot entirely this sweep (forces PrintStatus gutter→FootButtonBar/e-stop migration regardless of
  its Step-2 verdict); (b) keep slot until per-screen verdicts execute, law adds "no NEW gutter
  use"; (c) keep indefinitely as sanctioned legacy. Interacts with the PrintStatus rebuild verdict.
- **Q2 — README.md fate.** (a) Rewrite as the new front door (doc map + redesign summary);
  (b) demote to `docs/ui_design/archive/` as the historical handoff + write a thin new README.
- **Q3 — `hifi.css` / hi-fi HTML canonicity.** README calls it "canonical, pixel-for-pixel";
  THEMING says values are seed-generated now; the jiib sketches (001–004) + `sources/themes/`
  superseded the look. Ruling: demote hi-fi bundle to historical reference? What IS the visual
  north star of record (Spoolman screen as built + sketch sources)?
- **Q4 — Control fill language.** THEMING §"The control language (the outline rule)" still says
  interactive = 2px outline + **transparent fill** + glow. COMPONENTS/LAYOUT fill convention says
  controls are **FILLED** (`t.surface`) — content is the transparent one. Confirm filled-wins and
  rewrite; and rule on glow's current role (where does `edgeGlow`/static glow still apply?).

### Round 2 — component/behavior law
- **Q5 — Flexible-tile rule.** Drawer (its standby instance) is gone; PrintStatusField grid now
  has all-equal weights. Does the rule retain any live instance (Tune tile on the shortcut grid)?
  Keep as law, or demote to historical note? [V: confirm Tune tile behavior in code.]
- **Q6 — Back rule, restated.** Back=neutral stands, but "consistent gutter location app-wide" is
  meaningless post-gutter. Restate as a FootButtonBar position rule — which position (end-aligned?)
  — and the old "app-wide Back sweep" (deferred since 15.2, still marked HONEST DEFERRAL) gets
  folded into this sweep's audit as a checklist column.
- **Q7 — Scrubber style.** Sketch 004 (scrubber-style) was processed, but the skill still lists
  "scrubber style TBD" as open and UAT-3 says "while the current fill-bar style is in use." Did
  004 produce a verdict? If yes: write it into law + plan the migration; if no: rule now or
  explicitly mark OPEN with the fill-bar as interim law. Also: `ScrubberPage.kt` is referenced
  only by the debug GalleryScreen — delete as dead code?
- **Q8 — oklch caution-reads-red fix.** Open sketch follow-up. In this sweep's scope or parked?

### Round 3 — fine-grained ratifications [G]
- **Q9 — fsSp size scale into law.** The working scale (15sp metadata floor · 17–18sp body ·
  20–22sp titles · 26sp tabular stats · 30sp+ focus) lives only in memory/convention. Ratify and
  write into THEMING.md (or PREVIEW_AND_TOKENS.md §typography)?
- **Q10 — Stroke/floor units table.** Codify one table: ListRow 1.5dp/2dp selected, DetailCard
  3dp ring, control outline 2dp (THEMING says "2px"), hairlines; touch floor wording (64dp
  OutlinedControl vs ≥48dp PREVIEW §8 vs "64px" prose) — pick dp everywhere, one authoritative
  table in COMPONENTS.md.
- **Q11 — Spacing rhythm.** De-facto values: 8dp grid gaps (PrintStatusField), 12dp inter-row
  rhythm (SortFilter tile = uDp−12), FloatingEStop `padding(14.dp)`. Ratify as named values
  (e.g. gapS/gapM) or re-derive from U? Currently violates "no hardcoded sizes" as written.
- **Q12 — PREVIEW §10 backfill scope.** The exhaustive backfill (full string extraction, icon
  call-site migration, @Preview matrices everywhere, golden screenshots) pointed at a phase that
  was re-purposed. In this sweep, in Ship, or explicitly dropped for v1?

---

## RULINGS — Round 1 (owner, 2026-06-12)

- **R1 (Q1) — Gutter:** retired as a full-width REGION; it lives on as the primary button area
  under field sections (`FootButtonBar` is the gutter's successor) and is NOT required on every
  page. Code slot disposition (folded, consistent with ruling): no new `gutter`-slot use; existing
  users migrate as their Step-2 verdicts execute; delete the slot when the last user migrates.
- **R2 (Q2) — README:** rewrite as the new front door (doc map + two-region summary + pointers).
- **R3 (Q3) — North star:** hi-fi bundle (`hifi.css`, `Print Status Hi-Fi.html`, images/) demoted
  to historical reference, marked as such. North star of record = the as-built Spoolman screen +
  sketch skill sources (incl. `themes/default.css`) + THEMING.md tokens.
- **R4 (Q4) — Buttons are FILLED** (list rows stay translucent/outline) — fill denotes button-ness.
- **R5 (Q4 follow-up, supersedes C1/C5/D-10) — NEW button intent scheme:**
  - **stop** = could be destructive
  - **warning/caution** = could be destructive but part of the process (jog, load filament, resets)
  - **go** = the EXPECTED action (Files' Print, Spoolman's Load, Save, accept)
  - **accent** = neutral items / plain navigation (Back, Home-screen nav)
  - **neutral/outline RETIRES as a button intent.** Audit gets a repaint column; C1/C5/D-10
    rewritten to this scheme. (Confirmed explicitly against old law before adoption.)
- **R6 (Q4) — "Glow":** owner unsure of term; real blur isn't on the API floor. Keep the glow
  tokens defined as a cheap static alpha treatment; achieving a true glow via clever caching is
  ASPIRATIONAL polish, not law. Audit flags current glow renders for review, doesn't enforce.

## RULINGS — Round 2 (owner, 2026-06-12)

- **R7 (Q5) — Flexible-tile rule KEPT as law;** instance table corrected to real instances only
  (verify Tune tile + any others during audit).
- **R8 (Q6) — Back = FIRST (start-aligned) button in a FootButtonBar, app-wide.** Law + a Step-2
  audit column (position + the new accent intent per R5). Closes the 15.2 HONEST DEFERRAL.
- **R9 (Q7) — Sketch-004 SeekBar-style ringed-thumb scrubber is LAW** (6px track, accent fill /
  surface-3 remainder; surface knob + 5px accent ring ~34px visible, ~74px invisible touch target,
  accent-soft press halo; labels under, value above, snap-to-step; build-once/update-in-place drag
  rule per fa97efb). Fill-bar style DEPRECATED → migration items in the audit (Outputs LED
  brightness, inline scrubbers); UAT-3's "while in use" hedge resolved (the 1U height cap
  carries over to the new style); dead `ScrubberPage.kt` deleted this sweep.
- **R10 (Q8) — oklch caution-reads-red fix IN SCOPE** (it undermines R5's warning-vs-stop
  distinguishability). Generator/bridge clamp fix rides the sweep.

## RULINGS — Round 3 (owner, 2026-06-12)

- **R11 (Q9) — Type ramp ratified WITH ONE STRUCTURAL TWEAK:** the default list-item/button text
  size becomes what the fs=L setting renders TODAY for field/list items (Move/Extrude/Calibration
  rows etc.). Derivation: current base 17–18sp × L(1.32) ≈ 22–24sp rendered → **new base ≈ 20sp**
  (default-M renders ~23sp; L grows to ~26sp). Secondary text / measurement units may sit smaller
  per-case. Rest of ramp re-anchors around it: 15sp floor (metadata) · **20sp list/button default**
  · 22–24 titles · 26 tabular stats · 28+ focus heroes. Sub-15 stragglers (11/13/14sp) = audit
  flags. [Interpretation derived from owner wording — confirm before THEMING.md rewrite.]
- **R12 (Q10) — Stroke/floor table APPROVED AS-BUILT** (ListRow 1.5/2 · DetailCard 3/1 ·
  OutlinedControl 2, 64dp floor · FillMeter 6 · scrubber 6/34/5/74 · SortFilter U−12, 8dp gaps ·
  FloatingEStop 0.7U≥64, 14dp pad · floors: controls ≥64dp, absolute ≥48dp), **with a "minimal
  style" consolidation mandate** — audit proposes value consolidations (e.g. fewer distinct
  stroke weights) as owner-call polish items rather than enshrining every variant forever.
- **R13 (Q11) — Spacing ratified as NAMED TOKENS at as-built values** (e.g. gapS=8dp, gapM=12dp,
  padFloat=14dp), documented beside the radii; "no hardcoded sizes" amended to "spacing from the
  named set." No visual change; audit enforces the names.
- **R14 (Q12) — Backfill SPLIT:** string extraction + icon call-site migration ride THIS sweep
  (they gate vocabulary/registry conformance); exhaustive @Preview matrices + golden screenshots
  move to Ship as release-hardening.

## RULINGS — Round 4 (owner, 2026-06-12, icon sizing)

- **R15 — Icon-size HYBRID idiom by tier** (law: COMPONENTS.md §7c): text-companion icons
  (inline beside labels, status glyphs riding values) track TEXT via `fsSp`; cell-filling icons
  (control ~0.5U, prominent/hero 0.7–0.8U per UAT-1) track U. Fixed-dp icon sizes RETIRED except
  where a tier names one. Audit converts stragglers.
- **R16 — Dense list leading icons now TRACK THE LABEL at `fsSp(22).dp`** (amends UAT-1's frozen
  22dp — with the R11 20sp label default, a frozen icon reads undersized at fs=L). Still must
  not be grown to the prominent tier.

## RULINGS — Pilot session (owner, 2026-06-12)

- **R21 — FootButtonBar container padding = gapS (8dp) both axes** (was per-site 8h/4v); owned
  by the primitive, call sites pass no padding. No gapXS token.
- **R22 (from pilot feedback) — ListRow OWNS its anatomy:** built-in 12dp leading gap +
  content `weight(1f)` (trailing always end-aligned, UAT-2 by construction) + canonical
  `ListRowLabel` (Geist SemiBold, 20sp) as THE list-label style. Recorded in COMPONENTS.md.

## D. Verify during audit [V]
- Tune-tile flexible behavior (Q5); Back intent/position on every rebuilt screen (Q6);
  which screens pass `gutter = null` vs real gutter content (Q1); image-backed info card grammar
  survival on rebuilt Files (CLAUDE.md L87–93); whether any production surface still routes
  through `ScrubberPage` (greps say no — confirm before deleting).

## E. Doc-structure suggestion (for the rewrite, after rulings)
Single reading order: README (front door) → CLAUDE.md (philosophy) → LAYOUT.md → COMPONENTS.md →
THEMING.md → PREVIEW_AND_TOKENS.md, each opening with a "supersedes/superseded-by" header so the
next drift is at least labeled. The conformance checklist (Step 2) gets derived from the rewritten
set, one criterion per testable rule.
