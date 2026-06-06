package works.mees.dinghy.preview

import android.content.res.Configuration
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

/**
 * The canonical device + uiMode + locale review group for an exemplar: Nexus-7 landscape, the
 * pseudolocale (`en-XA`) for the i18n completeness sweep (SC-3c — plain-English text that shows
 * through a pseudolocalized run is a still-hardcoded literal), and the night uiMode flag.
 *
 * ⚠ Same contract as [Nexus7Previews]: device / uiMode / locale ONLY. The three palette MODES are
 * NOT selectable here — they come from [PreviewBox] wrappers. (Named `@DeviceAndLocalePreviews`, NOT
 * `@DinghyThemePreviews`, precisely so nobody believes the annotation selects the themes.)
 */
@Preview(name = "Device default", device = NEXUS7, showBackground = true)
@Preview(name = "Pseudolocale en-XA", device = NEXUS7, locale = "en-XA", showBackground = true)
@Preview(
    name = "Night uiMode",
    device = NEXUS7,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
annotation class DeviceAndLocalePreviews
