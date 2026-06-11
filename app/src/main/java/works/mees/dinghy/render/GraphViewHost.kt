package works.mees.dinghy.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.dinghy.preview.PreviewPlaceholderBox
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
 * @param drawArea paint the translucent `.g-area` fill (default `true` = design contract). Exposed
 *                 ONLY for the criterion-#5 fill-rate A-B capture (T-03-08); leave `true` in product.
 * @param modifier caller layout for the hosted View.
 */
@Composable
fun GraphViewHost(
    tokens: ThemeTokens,
    snapshot: FloatArray,
    modifier: Modifier = Modifier,
    drawArea: Boolean = true,
) {
    // D-05/D-04: a classic-Views AndroidView renders as a blank/broken region under @Preview (the
    // View's onDraw never runs in inspection mode), so a screen EMBEDDING this host would preview an
    // empty hole. Short-circuit to a labeled stand-in instead — the real graph is the on-device gate.
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "GraphView (live on device)", modifier = modifier)
        return
    }
    AndroidView(
        factory = { ctx -> GraphView(ctx) }, // created once; never recreated on a theme/data change
        update = { view ->
            view.applyTokens(tokens) // D-06 push-tokens + invalidate (recolor, no recreation)
            view.drawArea = drawArea // fill-rate isolation lever (default true = design aesthetic)
            view.setData(snapshot)   // new throttled sample → sanitize/cap + invalidate (D-13)
        },
        modifier = modifier,
    )
}

/**
 * Multi-trace host overload (05-04 / D-05) — hosts the SAME classic-Views [GraphView] for the N-sensor
 * Temperature history graph (nozzle/bed/chamber). `factory` runs once; the `update` block PUSHES the
 * tokens, the fixed [yRange], the N per-sensor [series], the per-sensor [setpoints], and the per-trace
 * [traceColors] color overrides into that one instance, so a dark→light/custom flip recolors all traces
 * with NO recreation (D-06, no jank).
 *
 * The single-snapshot overload above is retained unchanged for the Phase-4 Print Status sparkline.
 *
 * @param tokens      the active resolved tokens (THEME-01 — the View derives each trace color from them).
 * @param series      one bounded [RingBuffer.snapshot] per trace, index 0 = primary (oldest→newest, D-12).
 * @param setpoints   per-trace current target for the dashed setpoint line (D-04); `null`/absent = none.
 * @param traceColors per-trace chosen-color ARGB Int overrides (D-14); `null` entry = token default;
 *                    index-aligned with [series] (the screen's ONE visible-trace model keeps alignment).
 *                    Pushed into [GraphView.setTraceColorOverrides] which is equality-guarded — no
 *                    invalidate/churn when the list is unchanged (Phase-22 D-12 discipline preserved).
 * @param yRange      the shared Y-range. Default 0..350 (the setHeater ceiling); the Temperature panel
 *                    passes a DYNAMIC range computed by [works.mees.dinghy.ui.temperature.TemperatureHolder]
 *                    (fits live data + active setpoints, padded + rounded — 05 UI tweak).
 * @param drawArea    paint the translucent `.g-area` fill under the PRIMARY trace (default `true`).
 * @param showAxisLabels draw the min/max Y-value labels at the right edge (default `false` — the Temperature
 *                    panel turns it on; the small Print Status sparkline leaves it off).
 * @param modifier    caller layout for the hosted View.
 */
@Composable
fun GraphViewHost(
    tokens: ThemeTokens,
    series: List<FloatArray>,
    modifier: Modifier = Modifier,
    setpoints: List<Float?> = emptyList(),
    traceColors: List<Int?> = emptyList(),
    yRange: ClosedFloatingPointRange<Float> = GraphView.DEFAULT_Y_MIN..GraphView.DEFAULT_Y_MAX,
    drawArea: Boolean = true,
    showAxisLabels: Boolean = false,
) {
    // D-05: same inspection-mode stand-in as the single-snapshot overload (this multi-trace host
    // backs the Temperature panel's embedded graph). See that overload's comment for the rationale.
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "GraphView (live on device)", modifier = modifier)
        return
    }
    AndroidView(
        factory = { ctx -> GraphView(ctx) }, // created once; never recreated on a theme/data change
        update = { view ->
            view.applyTokens(tokens)          // D-06 push-tokens + invalidate (recolor all traces, no recreation)
            view.drawArea = drawArea          // fill-rate isolation lever (primary-trace fill only)
            view.showAxisLabels = showAxisLabels // min/max Y labels (Temperature on, sparkline off)
            view.yRange = yRange              // shared Y-range (dynamic for Temperature; G-1 fix)
            view.setTraceColorOverrides(traceColors) // D-14: equality-guarded per-trace color push (override-wins)
            view.setData(series)              // N throttled samples → per-series sanitize/cap + invalidate (D-13)
            view.setSetpoints(setpoints)      // per-trace dashed current-setpoint line (D-04)
        },
        modifier = modifier,
    )
}
