# Font Conformance — design

**Date:** 2026-06-15
**Status:** approved (brainstorm), pending implementation plan
**Owner:** Matthew

## Problem

The app's *substrate* is already correct — two font families exist and are correctly conceived
(`Geist` for all UI text, `GeistMono` for live printer data, tabular by construction), and every
size already flows through one helper, `fsSp(baseSp, fs)` (base × the S/M/L `--fs` multiplier),
at ~278 call sites with only ~7 stray raw `.sp` literals left.

The gap is that there is **no semantic text-role layer**. Every `Text()` re-specifies
`fontFamily` + base size + weight inline — **~224 hand-written family assignments** (113 Mono,
111 Geist) and base sizes scattered across **22 distinct values** (11, 12, 13, 16, 17, 18 …
alongside the sanctioned 15/20/22/24/26). THEMING.md §R11 already documents the ramp and flags
11/13/14/16/17/18 as non-conformant — yet they are all over the code (PrintStatusFocus alone
mixes 18/22/23/30/34). Nothing *prevents* a misassignment (system text rendered in Mono, or a
printer value rendered in Geist) and nothing *pins* a size to a ramp tier.

This is the **component-class workflow** applied to type: distill the recurring `(family, size,
weight)` combos into named classes, route every call site through them, make the class own its
styling, and make drift hard.

## Goals

- A small set of **named semantic text roles**; a call site says *what the text is*, not *what
  font/size it uses*. Misassignment becomes impossible (the role *is* the family decision).
- Every rendered base size snaps to a sanctioned ramp tier: **15 / 20 / 22 / 24 / 26 / 28+**.
  The stray 16/17/18/23/30/34 bases are eliminated.
- **One source of truth across both toolkits** — Compose *and* the four classic-Views surfaces
  derive from the same role definitions; a tier edit reaches both.
- **Drift is enforced by a build-failing test**, not convention.

Out of scope (explicitly deferred): the ramp *numbers* do not change (THEMING.md §R11 stands);
whether the four Views surfaces should be rewritten into Compose is a separate later discussion.

## Non-goals / what is already correct

- The two families and the `fsSp` helper stay as-is.
- The S/M/L `--fs` mechanism stays as-is (roles multiply through `fsSp`).
- Console (`ConsoleRowsAdapter`) is already conformant (Mono, `fsSp(15)`, `--fs`-aware) and just
  gets re-pointed at its role's `baseSp`.

## Design

### 1. The source of truth — `TextRole` (toolkit-neutral)

A plain data type with **no Compose import**, so the four Views surfaces can use it too. Lives in
`theme/DinghyType.kt`, beside `Geist.kt` and `fsSp`.

```kotlin
/** Which font family a role draws in. Ui → Geist, Data → GeistMono (live printer values). */
enum class TypeRole { Ui, Data }

/**
 * One named text class. FontFamily (Compose) and Typeface (Views) are DERIVED from [role] at the
 * toolkit boundary — never stored here — so this type stays free of any toolkit dependency.
 */
data class TextRole(
    val role:   TypeRole,
    val baseSp: Float,            // a sanctioned ramp tier; scaled via fsSp(baseSp, fs) at use
    val weight: FontWeight,       // androidx.compose.ui.text.font.FontWeight (data-only, no UI dep)
    val maxSp:  Float? = null,    // shrink-to-fit roles only (focusHero)
    val minSp:  Float? = null,
)
```

`FontWeight` is a Compose type but is a plain value class (no rendering dependency); if even that
import is undesirable in the neutral layer, fall back to an `Int` weight (400/500/600/700) and map
it at each boundary. Decided at implementation time; default is to use `FontWeight`.

### 2. The catalog — `DinghyType`

The eleven roles, as named `TextRole` constants. (`SB` = SemiBold, `Med` = Medium.)

**`Geist` — system / UI text (`TypeRole.Ui`):**

| Role | Base sp | Weight | Use |
|---|---|---|---|
| `screenTitle` | 22 | SB | screen / section titles |
| `focusHeader` | 20 | SB | the FocusFrame 1U header title (marquees on overflow) |
| `listLabel` | 20 | SB | list-row primary label |
| `buttonLabel` | 20 | SB | button + foot-bar labels |
| `body` | 20 | Med | reading / paragraph text |
| `caption` | 15 | Med | timestamps, fine print, unit suffixes, secondary |

**`GeistMono` — live printer data (`TypeRole.Data`):**

| Role | Base sp | Weight | Use |
|---|---|---|---|
| `focusHero` | max 40 → shrink to min 15 | Bold | the Focus region's primary value, read across the room |
| `statValue` | 26 | SB | tabular stat readouts |
| `dataInline` | 20 | Med | a data value inline in a row/line — filename, inline reading (matches `listLabel` size, but Mono) |
| `dataMeta` | 15 | Med | a small data value in a metadata slot |
| `consoleLine` | 15 | Med | console scrollback (the Views surface) |

The "is this text a printer value?" test decides Ui vs Data: filenames, sensor/temperature
values, console output, coordinate readouts = Data; everything else (labels, titles, buttons,
captions) = Ui.

### 3. Toolkit consumption

**Compose** (`theme/compose/`): an extension that bakes the role into a `TextStyle`.

```kotlin
fun TextRole.toTextStyle(t: ThemeTokens): TextStyle = TextStyle(
    fontFamily = if (role == TypeRole.Ui) Geist else GeistMono,
    fontSize   = fsSp(baseSp, t.fs).sp,
    fontWeight = weight,
)
// call site:  Text(name, style = DinghyType.listLabel.toTextStyle(t))
```

**`focusHero` is special** — it is not a plain `TextStyle`; it carries `maxSp`/`minSp` for
shrink-to-fit. Provide a `FocusHeroText(text, t, color, modifier)` composable wrapping `BasicText`
+ `TextAutoSize.StepBased(minFontSize = 15.sp * fs, maxFontSize = 40.sp * fs)` — the sanctioned
shrink-to-fit pattern already documented in THEMING.md §"Shrink-to-fit text". It renders at the
max when there's room and steps DOWN to fit, never up.

**Views** (the four text-bearing classic-Views surfaces): replace each private `const … _SP` with
the role's `baseSp`, and resolve the `Typeface` from `role` (Ui → `geist_*`, Data → `geist_mono_*`,
by weight). They already call `fsSp` and already load `res/font` typefaces — they only change
*where the number and family come from* (the role, not a local literal).

| Views surface | Renders | Role(s) |
|---|---|---|
| **Console** (`ConsoleRowsAdapter`) | scrollback lines | `consoleLine` |
| **Files list** (`FileRowsAdapter`) | filename / metadata | `dataInline` / `dataMeta` |
| **GraphView** | temp axis labels (`${v}°`) | small Data tier (`dataMeta`, or `statValue` if larger is wanted) |
| **WebcamView** | overlay chrome ("Reconnecting…", badges) + card title/body | chrome → `caption`/`body`; card filename → `dataInline` |

(The other `AndroidView`s — the webcam SurfaceView, BedMesh heatmap, ScanSurface — render
graphics/video, no text, and are untouched.)

### 4. The migration sweep

Mechanical migration of the ~224 inline `fontFamily=` + base-size sites onto roles, **screen by
screen** so each is independently reviewable and UAT-able on flox + moto.

**Net effect to verify per screen:** family assignments become provably correct (a `Data` role
cannot render in Geist), and every base size lands on a ramp tier — eliminating the stray
16/17/18/23/30/34 bases. Where a current size is between tiers, snap to the nearest sanctioned
tier (the role's `baseSp`); call out any case where snapping noticeably changes the rendered size
so the owner can eyeball it on-device.

### 5. Enforcement — `FontConformanceTest`

A JVM unit test (the existing `CommandCatalogDriftTest` pattern) that scans `app/src/main`:

- **No `fontFamily =`** outside the allowlist → fail.
- **No bare `fsSp(<number>`** with a non-ramp base outside the allowlist → fail.
- **Allowlist:** `theme/DinghyType.kt`, `theme/compose/` role plumbing, the four Views surfaces,
  and `preview/` + `bench/` (non-shipping). Plus the sanctioned token carve-outs that already
  exist (PromptMarkup author-hex etc. are color, not font — unaffected).

Result: the next screen *must* use a role or the build breaks.

### 6. Docs

- THEMING.md §R11 ramp table gains a **role-name column** (the numbers are unchanged — roles just
  name and enforce them).
- COMPONENTS.md gains a `DinghyType` entry (it is a component class).
- A short note that the four Views surfaces derive their type from `DinghyType` (one source of
  truth), and that rewriting them into Compose is a separate deferred question.

## Implementation phases (for the plan)

1. **Role layer** — `TextRole`/`TypeRole`/`DinghyType` (neutral), the Compose `toTextStyle`
   extension, and `FocusHeroText`. No call-site changes yet; builds green.
2. **Enforcement test** — `FontConformanceTest` with the allowlist, initially **reporting** (not
   failing) so the sweep can burn down the list; flip to failing once the sweep completes.
3. **Compose sweep** — screen by screen onto roles; per-screen UAT on flox + moto.
4. **Views wiring** — Console, Files, GraphView, WebcamView read `baseSp`/family from roles.
5. **Flip the test to failing** + docs (THEMING.md, COMPONENTS.md).

## Open questions

None blocking. Two implementation-time micro-decisions, both with stated defaults:
- `TextRole.weight` as Compose `FontWeight` (default) vs neutral `Int`.
- GraphView axis-label role: `dataMeta` (15) vs `statValue` (26) — pick by on-device legibility.
