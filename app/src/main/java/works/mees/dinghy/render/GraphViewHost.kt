package works.mees.dinghy.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.dinghy.theme.ThemeTokens

/**
 * The Compose↔Views interop seam (D-06) that hosts the classic-Views [GraphView] inside a Compose
 * tree via `AndroidView`. This is the "theme is a token remap across BOTH toolkits" proof in one
 * place: the CALLER collects the active [ThemeTokens] (via `collectAsStateWithLifecycle()` at its
 * level) and passes them in; on every recomposition the `update` block PUSHES those tokens and the
 * latest ring-buffer [snapshot] into the SAME [GraphView] instance — `factory` runs once, so a
 * dark→light/custom flip recolors the Canvas graph without recreating the View (D-06, no jank).
 *
 * Both pushes happen in `update`:
 *  - `view.applyTokens(tokens)` recolors the pre-allocated paints and `invalidate()`s (theme swap);
 *  - `view.setData(snapshot)` hands the new throttled (~2-4 Hz) sample, sanitized + capped, and
 *    `invalidate()`s (D-13 — repaint on a new sample, not per frame).
 *
 * @param tokens   the active resolved tokens (the caller collects the flow; THEME-01 — never raw).
 * @param snapshot the bounded [RingBuffer.snapshot] in oldest→newest order (D-12).
 * @param modifier caller layout for the hosted View.
 */
@Composable
fun GraphViewHost(
    tokens: ThemeTokens,
    snapshot: FloatArray,
    modifier: Modifier = Modifier,
) {
    AndroidView(
        factory = { ctx -> GraphView(ctx) }, // created once; never recreated on a theme/data change
        update = { view ->
            view.applyTokens(tokens) // D-06 push-tokens + invalidate (recolor, no recreation)
            view.setData(snapshot)   // new throttled sample → sanitize/cap + invalidate (D-13)
        },
        modifier = modifier,
    )
}
