# Spool Color Filter — Hue-Family Classification

**Date:** 2026-06-18
**Status:** Approved (design)
**Area:** Spoolman screen — color filter (`SpoolHolder.applyColorSwatch`)

## Problem

The Spoolman color filter cannot find muted / dark colors. Concrete case: the
"Olive Green Sunlu" filament (`color_hex = #64794b`) is found by **no** palette
swatch.

Root cause (verified, not assumed):

- The filter hands the tapped swatch's exact hex to Spoolman's
  `GET /v1/filament?color_hex=…&color_similarity_threshold=…` endpoint, which
  measures **CIE76 Delta-E in Lab space** and returns filaments within the
  threshold.
- We use `color_similarity_threshold = 20` — **tighter than Spoolman's own
  default of 25**.
- More fundamentally, perceptual distance to a *saturated* swatch does **not**
  model "color family." For `#64794b` the nearest swatch is **Gray (ΔE 28.6)**,
  then Brown (36.4); **Green is ΔE 72.1** — 5th-closest. No threshold makes
  "tap Green → find olive" work: low thresholds match nothing, high thresholds
  make olive surface under Gray/Brown (and make every swatch grab unrelated
  hues).

A human calls `#64794b` "green." The fix is to classify by **hue family**, the
way a person categorizes color, instead of perceptual nearness to a saturated
reference swatch.

## Approach

Keep the existing data-flow spine; swap only the *selection* mechanism.

The spool list endpoint has no direct color filter, so today's two-step is:
resolve a color selection → a set of filament ids → fold into the spool query as
`filament.id=<csv>` (`buildSpoolQuery`). **That plumbing is unchanged.**

| | Today | New |
|---|---|---|
| Fetch | `GET /v1/filament?color_hex=…&color_similarity_threshold=20&limit=50` | `GET /v1/filament?limit=1000` (see Fetch cap) |
| Select | Spoolman server-side CIE76 distance | Client-side hue-family classification |
| Output | `colorFilamentIds` → `filament.id=<csv>` | identical |

**Fetch cap.** Today's similarity fetch is already capped at `FILAMENT_LIMIT = 50`,
so client-side filtering over the same 50 would silently miss matches in libraries
larger than one page. We raise the classification fetch to `limit=1000` — Spoolman
filament tables are small (a heavy user has dozens to low-hundreds), so one
generous page is simpler than pagination and removes the cap as a practical
concern. Documented, not silent. (Pagination remains a possible follow-up if a
real library ever exceeds 1000 filaments.)

## Components

### `colorFamily(hex: String?): String?` — new pure function

Host-testable (no Android, no I/O). Returns one of the **12** palette family
names, or `null` if the hex is absent/unparseable.

It works on the integer RGB channels and derives: `H` (hue 0–360°),
`L` (HSL lightness), `S` (HSL saturation), and `C` (chroma = `max − min`, 0–255)
and `Shsv` (`C / max`). The Natural test uses **chroma / HSV saturation**, NOT
HSL saturation — HSL saturation is numerically unstable near white (cream and
pastel pink both report `S ≈ 1.0`), so it cannot distinguish a pale tint.

Rules, in order:

1. **Natural** — `L > 0.80` AND `12 ≤ C ≤ 70` AND `20° ≤ H ≤ 95°`: a pale,
   lightly-tinted warm near-white (cream / ivory / beige / "natural" filament).
   Tested first, ahead of White and the hue bands, so cream resolves to Natural
   rather than White or Yellow.
2. **Neutral** — `S < 0.15`:
   - `L < 0.22` → `Black`
   - `L > 0.85` → `White`
   - else → `Gray`
3. **Brown** — `20° ≤ H < 50°` AND (`L < 0.45` OR `S < 0.45`). Brown is a dark /
   muted warm color, not a hue band, so it is tested before the hue families.
4. **Hue families** (chromatic, not caught above):
   - `Red`: `H < 15` or `H ≥ 345`
   - `Orange`: `15 ≤ H < 45`
   - `Yellow`: `45 ≤ H < 70`
   - `Green`: `70 ≤ H < 165`
   - `Blue`: `165 ≤ H < 255`
   - `Purple`: `255 ≤ H < 290`
   - `Pink`: `290 ≤ H < 345`

Output domain is exactly the **12** swatch names (the 11 prior + **Natural**). All
12 palette swatches must self-classify to their own name — the 11 prior do
(verified); the new Natural swatch's chosen hex (a solid cream, see Palette
changes) must classify as Natural (verify at build). The tapped swatch's target
family is derived by running the swatch's own hex through `colorFamily`; the
picker's `onTapSwatch(hex)` signature is unchanged.

**Known boundary nit (tune, not blocking):** a very pale *pink* near 346° (e.g.
`#FFD1DC`) currently falls into Red (the Pink band ends at 345°). Out of scope
here; flagged for boundary tuning with real samples.

HSL is intentionally cheap (min/max/divide) — lighter than the Lab conversion
the current server path performs.

**Pure, no Android.** `colorFamily` and its HSL math must NOT touch
`android.graphics.Color` (used elsewhere only for display, e.g.
`SpoolScreen.kt:1008`). Reuse the existing integer-RGB parse (`parseRgb` /
`normalizeColorHex`, `SpoolHolder.kt:670`, `SpoolmanModels.kt:147`) and compute
HSL on the integer channels directly, ignoring alpha — keeping it host-testable.

### `applyColorSwatch(swatchHex)` — reworked selection

1. Re-tap of the active swatch still clears (unchanged).
2. `targetFamily = colorFamily(swatchHex)`.
3. Fetch all filaments: `client.listFilaments("limit=$FILAMENT_LIMIT")`.
4. A filament **matches** `targetFamily` if **any** entry in its `colorSwatches`
   list (the existing model field: the split `multi_color_hexes`, or the single
   `color_hex`) classifies to `targetFamily`. So a multicolor spool containing
   green is still found by the Green swatch — this is what makes dropping the
   dedicated Multi-color tile (see Palette changes) acceptable.
5. Keep matching ids → `colorFilamentIds`; set `colorSwatchHex = swatchHex` for
   the active-filter chip (unchanged).

### Palette & grid changes

- **Add `Natural`** as the 12th color family/swatch, rendered as a **solid
  cream/ivory** (a warm off-white, ~`#EDE6D6` — exact hex proposed and approved
  at build; it must self-classify as Natural).
- **Remove the Multi-color tile** from the color-swatch grid and the
  `applyMultiColor` filter feature entirely. Multi-color is a *property*, not a
  color, and never fit a color grid; multicolor filaments stay discoverable via
  their constituent colors (step 4 above).
- Resulting grid: **12 pure-color swatches in the proven 4×3 fill layout** — no
  ragged row, no layout rework. `PALETTE_SWATCHES` (`SpoolPicker.kt`) and
  `PREFILTER_PALETTE` (`SpoolHolder.kt`) both gain Natural and drop the
  Multi-color entry; they must stay mirror-consistent.

### `seedPrefilter` — make the gcode color hint consistent

The Files "Pick spool" gcode-aware seed (`seedPrefilter`, `SpoolHolder.kt:495`)
currently maps the file's color to a pre-highlighted swatch via
`nearestPaletteSwatch` — **RGB euclidean** distance. That is the same
saturated-swatch-nearness trap: an olive file color would pre-highlight **Gray**,
contradicting the new filter that classifies olive as **Green**. Fix: derive the
hint swatch from `colorFamily` instead (file color → family → that family's
swatch hex), so the hint and the filter agree.

`nearestPaletteSwatch` (and its RGB `parseRgb` helper) has no other caller after
this; fold its intent into the family path and remove it, or leave it only if
still referenced.

**Re-tap guard.** The seed sets `colorSwatchHex` (highlight) with
`colorFilamentIds = null` ("hint, not a hard filter"). Today `applyColorSwatch`
treats a tap whose hex equals `colorSwatchHex` as a re-tap and **clears** — so
tapping the seed-highlighted swatch wrongly clears instead of applying the hard
filter. Fix: the re-tap-clears branch must require an **active hard filter**
(`colorFilamentIds != null`); a tap on a hint-only highlight applies the filter.

### Removed

- `COLOR_SIMILARITY_THRESHOLD` constant.
- The `color_hex` / `color_similarity_threshold` query string.
- `nearestPaletteSwatch` + its RGB-distance helper (superseded by `colorFamily`),
  pending no remaining callers.
- The **Multi-color tile**, `applyMultiColor`, the `SpoolFilters.MULTICOLOR`
  sentinel, and the multi-color `ColorChoice.Multi` rendering — and their tests
  (e.g. any `SpoolPickerStateTest` multi-color cases). Multicolor matching now
  happens implicitly via per-sub-color classification.

## Decisions

- **Near-black boundary:** `Black` cutoff stays at `L < 0.22`. The real-world
  `#3A3C3B` "Black Sunlu" (S 0.02, L 0.23) classifies as **Gray** — honest by
  the numbers; found under the Gray swatch. No special-casing.
- **Natural family added:** solid cream/ivory swatch, 12th family (owner choice).
- **Multi-color filter dropped:** the dedicated tile/feature is removed (owner
  choice); multicolor spools remain findable by their sub-colors.
- **Caching:** none. Per-tap fetch keeps today's network profile. A
  fetch-once + cache optimization is a possible later follow-up (adds state +
  invalidation), explicitly out of scope here.

## Edge cases

- Absent / unparseable hex → `colorFamily` returns `null` → not matched (matches
  today's behavior for colorless filaments).
- **Fetch succeeded, zero family matches** → `filament.id=-1` unmatchable
  sentinel (a deliberate "no matches in this family," not "show everything").
- **Fetch FAILED (network error / null envelope)** → leave the color filter
  unapplied (`colorFilamentIds = null`, no `filament.id` term) rather than
  collapsing the list to nothing. This corrects a latent flaw the spec earlier
  mis-described: today a failed read is indistinguishable from an empty result —
  both yield empty ids → `filament.id=-1` → an empty list while the swatch looks
  active (`SpoolHolder.kt:415`, `:611`). The plan must distinguish the two
  (`runCatching` failure / null envelope vs. a successful empty parse) and cover
  both with tests.

### Residual edge — saturated pastels & light browns

The new Natural family covers the big gap (cream/ivory/beige/"natural"). Two
intentional residuals remain, locked by acceptance tests rather than surprising:
a **saturated pale yellow** (`#FDFD96`, chroma 103 > 70) reads Yellow, not
Natural (it is a real pale yellow, not a natural tint); **tan/khaki**
(`#D2B48C` / `#C3B091`) read Brown (light browns). Both are defensible; tunable
later if real inventory says otherwise.

## Real-filament validation (owner's Spoolman, 2026-06-18)

| Name | hex | family |
|---|---|---|
| Olive Green | #64794b | **Green** ✓ (the bug) |
| Coffee Brown | #886543 | **Brown** ✓ |
| Sky Blue | #5dc0f0 | Blue |
| CMYK Yellow | #F6FA00 | Yellow |
| Cherry Red | #E63034 | Red |
| Black | #000000 | Black |
| White | #FFFFFF | White |
| "Black" dark gray | #3A3C3B | Gray (see decision) |

Natural rule validation (representative — owner has none yet, but a typical
library will): cream `#FFFDD0`, ivory `#FFFFF0`, beige `#F5F5DC`, eggshell
`#F0EAD6`, bone `#E3DAC9`, natural-PLA `#E8E0CE` → **Natural** ✓; pure/off-white
→ White; pastel blue `#AEC6CF` → Blue.

## Docs & fakes to update (old server-side contract)

The CIE76 / `color_similarity_threshold` contract is documented and faked in
several places; update or mark historical so the codebase doesn't describe a
mechanism that no longer exists:

- `docs/view_specific_notes/spoolman.md:265` — the "close-enough color filter"
  two-request example.
- `docs/view_specific_notes/spoolman_live_validation.md:384` — mark historical.
- `docs/ui_design/COMPONENTS.md:488` — color-filter component note.
- `app/src/test/java/works/mees/dinghy/spool/FakeSpoolmanClient.kt:52` — the fake
  must answer the new `listFilaments("limit=1000")` shape; any test asserting the
  old `color_hex=…&color_similarity_threshold=…` query string must be retired.

## Testing

Host-side unit tests:

- `colorFamily`: all **12** swatches self-classify (incl. the cream Natural
  swatch → Natural); the real filaments above (esp. `#64794b` → Green); the
  Natural set (cream/ivory/beige/eggshell/bone/natural-PLA → Natural) and that
  pure/off-white → White; neutral cutoffs (`#3A3C3B` → Gray); Brown boundary
  (`#886543` → Brown, but olive's `H 87` → Green); the residual edges
  (`#FDFD96` → Yellow, `#D2B48C` → Brown); null on absent/garbage hex.
- `applyColorSwatch`: against a fake filament list, a swatch tap selects exactly
  the ids whose `colorSwatches` classify to the swatch family, including a
  multicolor filament matched by a sub-color. A successful empty match → ids
  `[-1]` semantics (empty list). A **failed** fetch → `colorFilamentIds` stays
  `null` (filter unapplied), distinct from the empty-match case.
- Re-tap clears only when a hard filter is active; a tap on a seed-hint-only
  highlight (`colorSwatchHex` set, `colorFilamentIds == null`) applies the filter.
- `seedPrefilter`: an olive file color seeds the **Green** hint swatch (via
  `colorFamily`), not Gray.
