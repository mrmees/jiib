package works.mees.dinghy.render

import androidx.compose.ui.graphics.Color
import works.mees.dinghy.theme.ThemeTokens

/** The bed-mesh ramp color choices: intent colors first, then the data pool. Indexable 0-based. */
fun bedMeshPoolColors(t: ThemeTokens): List<Color> =
    listOf(t.accent, t.stop, t.heat, t.go) + t.pool.take(4)

/**
 * Resolve a stored bed-mesh ramp color selector to a live Color. Selector: 0..7 = index into
 * [bedMeshPoolColors] (intents 0..3 = accent/stop/heat/go; data pool 4..7). Out-of-range falls
 * back to accent. Resolving at render time (not storing ARGB) lets mesh colors track theme changes.
 */
fun resolveMeshColor(t: ThemeTokens, sel: Int): Color =
    bedMeshPoolColors(t).getOrElse(sel) { t.accent }
