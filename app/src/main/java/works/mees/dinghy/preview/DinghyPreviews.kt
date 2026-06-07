package works.mees.dinghy.preview

import androidx.compose.ui.tooling.preview.Preview

/**
 * The Nexus 7 2013 (`flox`) device GEOMETRY as a Compose `@Preview(device = …)` spec string.
 *
 * ⚠ This is GEOMETRY ONLY — it pins the panel's px/dpi/orientation so a preview lays out at the
 * real floor-device resolution. It says NOTHING about the Adreno-320 fill-rate budget: a `@Preview`
 * is HOST-rendered on the dev machine, so it can never surface on-device jank. Perf stays an
 * on-device gate (ADR-0001 Addendum 2), never a preview claim.
 *
 * Hardware: 1920×1200, ~323 ppi (rounded to dpi=320 here for the spec). Landscape is the wider
 * dimension as width; [NEXUS7_PORTRAIT] swaps them.
 */
const val NEXUS7: String = "spec:width=1920px,height=1200px,dpi=320,orientation=landscape"

/** The Nexus 7 2013 in portrait (1200×1920) — same panel, swapped orientation. */
const val NEXUS7_PORTRAIT: String = "spec:width=1920px,height=1200px,dpi=320,orientation=portrait"

/**
 * Render an exemplar on the Nexus-7 floor device in BOTH orientations (portrait + landscape).
 *
 * ⚠ NAMING / CONTRACT (Codex MEDIUM-3): this annotation sets only the DEVICE + ORIENTATION. It does
 * NOT — and a `@Preview` multipreview CANNOT — select the Colorful/Simple/High-Contrast token set.
 * The six theme combos come ONLY from wrapping the previewed composable in an explicit
 * [PreviewBox] seed (e.g. `PreviewBox(colorfulDark) { … }`). Do not assume this annotation mints the
 * themes when you copy this template into Phases 19-21.
 */
@Preview(name = "Nexus7 landscape", device = NEXUS7, showBackground = true)
@Preview(name = "Nexus7 portrait", device = NEXUS7_PORTRAIT, showBackground = true)
annotation class Nexus7Previews

// NOTE: the pseudolocale (`en-XA`) i18n-completeness sweep (SC-3c) is NOT a multipreview annotation.
// It is a single dedicated `@Preview(device = NEXUS7, locale = "en-XA")` per screen — the
// `*PseudolocaleSpotCheck` companion to the `*RtlSpotCheck` (see the exemplar preview files +
// `docs/ui_design/PREVIEW_AND_TOKENS.md` §3/§7). A former `@DeviceAndLocalePreviews` annotation was
// removed: it was defined-but-unused, and its `uiMode = UI_MODE_NIGHT_YES` panel was a verified NO-OP
// (theme is tuple-driven through [PreviewBox] — uiMode does not select the palette), so it falsely
// implied the annotation selected theme.
