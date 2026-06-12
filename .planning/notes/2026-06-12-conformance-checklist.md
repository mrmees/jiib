# Conformance Checklist — Visual Normalization Sweep, Step 2

**Derived from** the 2026-06-12 reconciled law (`docs/ui_design/` @ `54259f3`, rulings R1–R16).
Every screen is scored per criterion: **PASS** / **FLAG** (fixable in place) / **FAIL**
(structural). Evidence = file:line. This doc is also the script for the final flox walk.

| # | Criterion | Law |
|---|---|---|
| C-U1 | Unit grid: `rememberUnitGrid`/`uDp` drives vertical sizing; rows/controls `heightIn(min = uDp)`; integer-U heights. **C-U1b (pilot finding): the grid MUST be derived at the SCREEN root (`BoxWithConstraints(fillMaxSize)` before `ScreenScaffold`), never inside a Focus/Field slot** — a slot-derived U is smaller than the law's screen-short-edge U and varies with rotation (the home-standby bug, fixed `dde62a1`) | LAYOUT §The unit U; C6 All-1U |
| C-U2 | Controls cap at 1U; >1U exceptions explicitly named (ColorWheel only) | LAYOUT UAT-5 |
| C-R1 | Two-region scaffold; `gutter = null` (live gutter content = migration item per R1) | LAYOUT §regions; R1 |
| C-R2 | `FootButtonBar` inside field slot, last element; **Back FIRST** | LAYOUT §foot-of-list; R8 |
| C-F1 | Fill convention: list rows transparent+outline (ListRow or conformant); buttons FILLED | COMPONENTS §2; R4 |
| C-F2 | No local re-implementation of a catalog class (local row anatomy = consolidation flag) | COMPONENTS §1 |
| C-I1 | Button intents match the R5 four-class scheme (expected=go, nav=accent, hazard-in-process=caution, destructive=stop); discard-Back stays stop | THEMING §intent; C7 |
| C-T1 | All text via `fsSp`; list/button labels at base 20 (17–18 = repaint flag); sub-15 = FAIL flag | THEMING §type ramp; R11 |
| C-T2 | Geist Mono + tabular for live data/filenames | THEMING §shape & type |
| C-G1 | Icons registry-only (`DinghyIconView`/`DinghyIcons`; raw `MaterialSymbol`/glyph `painterResource` = FLAG) | CLAUDE icon law |
| C-G2 | Icon sizes match R15/R16 tiers (inline fsSp(label+2) · dense fsSp(22) · control ~0.5U · hero 0.7–0.8U; fixed-dp = FLAG) | COMPONENTS §7c |
| C-S1 | Strokes from the §7b table (1.5/2/3/2); radii via `t.rCard`/`t.rCtrl` | COMPONENTS §7b; R12 |
| C-S2 | Spacing from named tokens (gapS 8 / gapM 12 / padFloat 14); other structural dp = FLAG | COMPONENTS §7b; R13 |
| C-K1 | Token routing: no raw `Color(` outside sanctioned carve-outs (PromptMarkup runs, spool spiral, DetailCard.ringColor, FillMeter.fillColor) | THEMING carve-outs |
| C-B1 | No deprecated fill-bar scrubber (migration item; UAT-3 1U cap binds interim) | COMPONENTS §7; R9 |
| C-A1 | UAT-2 row anatomy: icon → name start-aligned → `Spacer(weight(1f))` → value end-aligned | LAYOUT UAT-2 |
| C-A2 | UAT-4: Focus top-left clear for FloatingEStop | LAYOUT UAT-4 |
| C-X1 | Strings: user-facing literals via `stringResource` (count raw); icon call-sites via registry (R14 scope) | PREVIEW §5/§6 |
| FOCUS | (inventory, not scored) describe the Focus treatment → candidate archetype | sweep design note |

**Verdicts** (owner rules per screen off the matrix): **REBUILD** (pre-redesign structure) ·
**POLISH** (conformant structure; fix FLAGs in place) · **LEAVE** (conformant / sanctioned).

**Sweep-wide work items independent of screens:** delete `ScrubberPage.kt` (R9) · oklch
caution-reads-red generator clamp (R10) · `ScreenScaffold.gutter` slot deletion once last user
migrates (R1) · stale KDoc `PrintStatusScreen.kt:54` (Drawer flexible-tile).
