package works.mees.jiib.theme.views

import works.mees.jiib.theme.ThemeTokens

/**
 * The Views-side push-tokens contract (D-06) — the classic-Views mirror of the Compose
 * [works.mees.jiib.theme.compose.LocalTokens] boundary. The hybrid (ADR 0001) hosts three
 * high-churn surfaces as custom `View`s (Files list, temperature graph, Console scrollback);
 * those can't read a CompositionLocal, so the AndroidView host PUSHES the active tokens to them
 * instead — exactly the setter+`invalidate()` idiom already used by `TempGraphView` in the
 * benchmark scene, formalized here into one seam every render `View` implements.
 *
 * The host calls [applyTokens] whenever the resolved [ThemeTokens] change (theme swap, `--fs`
 * step, light/dark, custom override) and the View repaints with the new role colors — NO view
 * recreation, no `findViewById` re-theming, no raw color literal inside the View. A theme is a
 * token remap on both sides of the toolkit boundary, not two divergent palettes.
 *
 * Implemented by the render `GraphView` (Wave 3, plan 03-05). Implementations should stash the
 * tokens (or the specific `.toArgb()` paints they need) and call `invalidate()` to redraw.
 */
interface ThemeableView {
    /** Push the current resolved tokens into this View; the implementation repaints. */
    fun applyTokens(t: ThemeTokens)
}
