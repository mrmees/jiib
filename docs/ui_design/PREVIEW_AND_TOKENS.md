# Preview-First & Tokenized-First — Dinghy Display (the screen-build convention)

> **This is UI LAW (Phase 18).** Every screen built or touched from Phase 19 onward ships, from
> day one: (a) a `@Preview` matrix that renders in Android Studio across the **6 theme combos +
> `fs = L`** with **no live Moonraker**, (b) user-facing strings routed through `stringResource`,
> and (c) glyphs routed through the `DinghyIcon` token registry. This doc is the mechanical
> copy-template — the 3 Phase-18 exemplars (`PrintStatusPreviews.kt`, `FineTunePreviews.kt`,
> `SpoolPreviews.kt`) are the worked reference; copy them.
>
> Sibling LAW: `docs/ui_design/LAYOUT.md` (Focus/Field/Gutter grammar) and `THEMING.md` (the role
> tokens these previews exercise). Where this doc and the harness code (`app/.../preview/`,
> `designsystem/icons/`) ever drift, **the code is authoritative and this doc is the bug.**

The whole point: a screen's correctness across themes, text sizes, RTL, and capability/state
variants is provable in Studio in seconds — *before* it ever reaches the flox device — and its
vocabulary is translatable + its glyphs are remappable from one place. The harness exists so a new
screen is a mechanical fill-in, not a research project.

---

## 1. The preview wrapper idiom — `PreviewBox(tuple) { Screen(...) }`

Every preview wraps the screen in **`PreviewBox(seed)`** (`preview/PreviewTheming.kt`). `PreviewBox`
is the ONE preview theme boundary — it drives the real production seam
`DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple)))`, so a preview resolves a token set
**byte-identically to runtime**. There is NO preview-only palette.

```kotlin
@Nexus7Previews
@Composable
private fun MyScreenThemeColorfulDark() =
    PreviewBox(colorfulDark) { MyScreen(state = SampleFixtures.myThing) }
```

The six named seeds live in `PreviewTheming.kt`: `colorfulDark`, `colorfulLight`, `simpleDark`,
`simpleLight`, `highContrastDark`, `highContrastLight`, plus `fsLargeSeed` (Colorful/dark at
`FontScale.L`) and a `themeCombos` list.

---

## 2. The multipreview annotation sets device/orientation ONLY — themes are wrappers

`preview/DinghyPreviews.kt` provides one multipreview annotation:

- **`@Nexus7Previews`** — the floor-device geometry (`dpi=320`, 1920×1200), portrait + landscape.

The pseudolocale (`en-XA`) check is NOT a multipreview annotation — it is a dedicated per-screen
`@Preview(device = NEXUS7, locale = "en-XA")` (`*PseudolocaleSpotCheck`, see §3/§7). (A former
`@DeviceAndLocalePreviews` annotation was removed: it was defined-but-unused, and its night-uiMode
panel was a verified no-op — uiMode does not select the palette, theme is `PreviewBox`-driven.)

> ⚠ **A `@Preview` annotation can set device / uiMode / locale, but it CANNOT select the
> Colorful / Simple / High-Contrast palette MODE.** The six theme combos come from explicit
> **`PreviewBox(seed)` wrapper functions**, never from the annotation. This is the single most
> common copy-paste mistake. The annotation gives you orientations + locale; the wrappers give you
> palettes. You need both.

---

## 3. The minimized matrix shape (do NOT render every state × theme × fs)

Rendering every state × 6 themes × fs is 28+ slow, noisy panels per screen. The convention
(RESEARCH Q8) is a **minimized** matrix — pick the interesting axis as a `@PreviewParameter`, vary
theme/fs/RTL in single representative shots:

| Preview fn | What it proves | How |
|---|---|---|
| `*Matrix` | the FULL interesting-axis matrix on ONE theme | axis = `@PreviewParameter`, theme = `PreviewBox(colorfulDark)` |
| `*Theme{ColorfulDark…HighContrastLight}` | the FULL 6-theme matrix on ONE representative state | six sibling `PreviewBox(seed)` wrappers |
| `*FsLargeOverflow` | text/row clipping at the LARGEST text size | `PreviewBox(fsLargeSeed)` |
| `*RtlSpotCheck` | `start`/`end` modifiers mirror correctly | `CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl)` |
| `*PseudolocaleSpotCheck` | every APP-vocabulary literal is tokenized (no plain English shows through) | `@Preview(device = NEXUS7, locale = "en-XA")` (a single dedicated panel — NOT a multipreview) |

The "interesting axis" differs per archetype:
- **PrintStatus** (anchor): the 4/6 print **STATE**s (`PrintStatusModeProvider`).
- **FineTune**: the present / absent / busy **CAPABILITY** variants (`FineTuneVariantProvider`).
- **Spool**: the no-selection / selected **SELECTION** state over a dense fixture
  (`SpoolSelectionProvider`).

Phase 19 (Output Controls, capability-gated) copies the **FineTune** provider shape.

---

## 4. No live Moonraker — the stateless preview seam (SC-1)

A preview MUST render with no `AppContainer`, no holder, no dispatcher, no socket. Screens that
take a heavyweight container get a textbook Compose **state-hoist**: extract a container-free
`*Content(...)` the live overload AND a stateless preview overload both call, and add a stateless
entry the previews drive:

```kotlin
// live entry — resolves flows, builds dispatch lambdas, then delegates
@Composable fun MyScreen(container: AppContainer, …) { … MyContent(state = resolved, onX = { … }) }
// preview seam — pure fixture in, no-op callbacks
@Composable fun MyScreen(state: MyState, onX: () -> Unit = {}) { MyContent(state = state, onX = onX) }
@Composable private fun MyContent(state: MyState, onX: () -> Unit) { /* the previewed body */ }
```

The live overload stays **behavior-neutral** — it resolves exactly as before, then delegates.
Live-only modals/overlays (filter selectors, measured-weight pages, confirm guards driven by the
dispatcher) stay in the live entry and are NOT part of the stateless seam — the preview proves the
base scaffold, not the live-only surfaces.

Fixtures live in **`preview/SampleFixtures.kt`** — pure, immutable, reusable `object` values (the
SAME seed the Phase-22 backfill reuses; NOT per-preview one-offs). To add a fixture: add a `val`
(or a builder, e.g. `forMode(...)`) to `SampleFixtures`, keep it plain data, no network/coroutines.

### ⚠ The `fs = L` gotcha — `@Preview(fontScale = …)` is a NO-OP

`DinghyTheme` pins OS `fontScale` to `1f` (`--fs` is the sole text-size authority). So
`@Preview(fontScale = 1.3f)` does **nothing**. The ONLY way to preview the large text size is the
seed's `fs` field — use `PreviewBox(fsLargeSeed)`. Never reach for the annotation parameter.

---

## 5. String tokenization — `<area>_<element>` / `cd_*` (SC-3)

User-facing literals route through `stringResource(R.string.…)`; keys live in
`app/src/main/res/values/strings.xml`.

- **Visible labels / titles / body:** `<area>_<element>` — e.g. `spool_badge_loaded`,
  `printstatus_title`, `finetune_motion_label`.
- **contentDescription (a11y, TalkBack-only, never shown):** `cd_<thing>` — e.g. `cd_spool_weight`.
- **`<area>`** = the screen/feature owning the string (`printstatus`, `finetune`, `spool`,
  `common`, …).

**Format-arg example:** `<string name="printstatus_layer_progress">Layer %1$d of %2$d</string>` →
`stringResource(R.string.printstatus_layer_progress, current, total)`.

**Plurals example:**
```xml
<plurals name="spool_count">
    <item quantity="one">%1$d spool</item>
    <item quantity="other">%1$d spools</item>
</plurals>
```
→ `pluralStringResource(R.plurals.spool_count, n, n)`.

### IN / OUT boundary (what gets a key vs. what stays raw)

- **IN** = APP vocabulary we author: labels, titles, actions, status words, a11y descriptions →
  tokenized.
- **OUT** = pass-through DATA rendered verbatim: filenames, console output, macro params, raw
  Moonraker fields, sensor names → **NEVER** given keys.

---

## 6. Icon tokenization — `DinghyIcon` / `DinghyIcons` / `DinghyIconView`

Glyphs route through the registry, not raw `MaterialSymbol(...)` / `painterResource(...)`:

- **`DinghyIcon`** (`designsystem/icons/DinghyIcon.kt`) — `data class DinghyIcon(primary: IconRef,
  alternate: String)`. `IconRef` is a sealed one-of: `Ligature(name)` (Material Symbols) or
  `Drawable(resId)` (bundled vector). The **`alternate`** is the canonical one-place remap handle
  (D-07), required **unique** across the registry; the icon carries **no fused label** (D-08 — the
  visible/spoken label is a separate `stringResource` at the call site).
- **`DinghyIcons`** (`designsystem/icons/DinghyIcons.kt`) — the registry `object`. Add a `val` per
  icon a screen uses, AND add it to `DinghyIcons.all` (the hand-rolled list the uniqueness test +
  `tools/subset-symbols` iterate — no reflection).
- **`DinghyIconView`** — the ONE render primitive: `DinghyIconView(DinghyIcons.X, sizeDp = …,
  contentDescription = stringResource(R.string.cd_…))`. Call sites think in ONE unit (`sizeDp`); the
  ligature branch derives `sp` via `dpToSp` (pixel-correct only under the `fontScale = 1f` pin). The
  ligature branch OWNS its a11y semantics so TalkBack never speaks the raw ligature name.

To register a new icon: add the `val` + add it to `all` + give it a unique `alternate`. The
`DinghyIconsTest` enforces resolvable-source + unique-`alternate`.

---

## 7. RTL — `start`/`end`-relative modifiers (with hardware-spatial exceptions)

Use direction-agnostic / `start`/`end`-relative modifiers (`padding(horizontal=)`,
`Arrangement.spacedBy`, `Alignment.CenterStart`/`End`, `TextAlign.End`) so the screen mirrors in
RTL locales. NEVER hardcode `padding(start = …, left)`, `Alignment.*Start` meant as physical-left,
or left/right offsets. The `*RtlSpotCheck` preview is the proof.

**Exception:** genuinely hardware-spatial glyphs/controls that map to a PHYSICAL printer direction
(e.g. a +X jog that is physically rightward regardless of reading order) stay spatial — those are
about the machine, not the text. Document the exception inline when you keep one.

**i18n companion — the `*PseudolocaleSpotCheck`.** Alongside the RTL check, each screen ships one
dedicated `@Preview(device = NEXUS7, locale = "en-XA")` — its own single `@Preview`, NOT part of
`@Nexus7Previews`. The `en-XA` pseudolocale accordion-pads + brackets the app vocabulary, so it is the
proof that every app-vocabulary string is tokenized: plain-English text surviving a pseudolocalized
run is a still-hardcoded literal (the SC-3c completeness sweep).

---

## 8. Touch-target + a11y rider (≥48dp, tokenized `cd_*`)

Interactive elements honor the ≥48dp touch floor (the design system's `OutlinedControl` already
enforces ≥64dp). Every meaningful glyph/control carries a tokenized `cd_*` contentDescription;
purely decorative glyphs (where an adjacent text label already speaks) pass `null` to mark them
decorative.

---

## 9. D-04 / D-05 — embedded classic-View surfaces preview as labeled stand-ins

`AndroidView`-hosted classic Views and live hardware surfaces do NOT render under Layoutlib. Branch
each on `LocalInspectionMode` BEFORE the `AndroidView` and emit a token-aware
`PreviewPlaceholderBox(label)` (a 2px-outline labeled stand-in, NOT a blank/broken region):

```kotlin
if (LocalInspectionMode.current) {
    PreviewPlaceholderBox(label = stringResource(R.string.cd_…), modifier = modifier.fillMaxSize())
    return
}
// … the real AndroidView / live surface (renders on-device unchanged)
```

Covered surfaces: the three render hosts (`GraphViewHost`, `BedMeshHeatmapHost`, `WebcamViewHost`,
18-02) and the CameraX `PreviewView` in `ui/spool/scan/ScanSurface.kt` (18-07). The same idiom
covers any Coil `AsyncImage` (a network load never resolves under `@Preview`) — though note the
Spool surface has **no** Coil thumbnail (its color identity is token-colored `Box` swatches; its
QR is a static `painterResource`), so its real preview-unsafe surface is the camera host, not an
image loader. These embedded surfaces get **no** dedicated Compose preview (D-04 — their real
rendering is the on-device gate); the placeholder is the documented limitation.

---

## 10. EXCLUDED from this template — Phase-22 backfill (D-03)

The following are **NOT** part of the per-screen template and must NOT be added speculatively while
building a screen:

- **`@Stable` / `@Immutable` / `ImmutableList`** annotations (D-03) — the recomposition-skipping
  pass is its own co-sequenced Phase-22 sweep, not a preview/token rider.
- **Shared design-system components reused across unrelated screens** (e.g. `OutlinedControl`'s
  `symbol`/`label` API) — tokenize literals in the screen you're building + the children you
  directly edit for it ONLY. Shared components ride the Phase-22 backfill (the detekt baseline
  tolerates their literals). Pre-seed their `cd_*` keys if convenient, but do not reshape the shared
  component's signature for one screen.
- **The exhaustive every-screen backfill** — all remaining `@Previews`, the ~240-literal string
  extraction, full icon call-site migration, the app-wide a11y/RTL/`@Stable` riders, and the
  `compose-preview-screenshot` golden-image net — is **ONE co-sequenced per-screen pass in Phase
  22** (SC-5). Phase 18 proved the convention on 3 exemplars; Phase 22 applies it everywhere.

The lint gate (SC-3) tolerates not-yet-tokenized literals via a baseline — that deferral is
deliberate and is reconciled in Phase 22, not screen-by-screen now.
