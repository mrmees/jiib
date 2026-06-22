package works.mees.dinghy.render

import androidx.compose.ui.graphics.Color
import works.mees.dinghy.theme.ThemeTokens

/**
 * Resolve a stored bed-mesh ramp color selector to a live Color. Selector: -1 = Accent sentinel
 * (== seriesColor(0)); 0..3 = data-pool slot. Out-of-range/empty-pool falls back to accent. Resolving
 * at render time (not storing ARGB) is what lets mesh colors track theme changes.
 */
fun resolveMeshColor(t: ThemeTokens, sel: Int): Color =
    if (sel < 0) t.accent else t.pool.getOrElse(sel) { t.accent }
