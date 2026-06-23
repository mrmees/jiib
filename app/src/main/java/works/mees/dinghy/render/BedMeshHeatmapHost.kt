package works.mees.dinghy.render

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.viewinterop.AndroidView
import works.mees.dinghy.calibration.BedMeshModel
import works.mees.dinghy.preview.PreviewPlaceholderBox
import works.mees.dinghy.theme.ThemeTokens

/**
 * The Compose↔Views interop seam (D-06) that hosts the classic-Views [BedMeshHeatmapView] inside a
 * Compose tree via `AndroidView` — the bed-mesh sibling of [GraphViewHost]. The CALLER collects the
 * active [ThemeTokens] (via `collectAsStateWithLifecycle()` at its level) and passes them in along with
 * the current [BedMeshModel] + [scaleMode]; on every recomposition the `update` block PUSHES the tokens
 * (recolor + invalidate) and the latest mesh/scale (repaint) into the SAME [BedMeshHeatmapView]
 * instance — `factory` runs once, so a dark→light/custom flip OR a scale-mode cycle recolors the
 * heatmap without recreating the View (D-06, no jank).
 *
 * Both pushes happen in `update`:
 *  - `view.applyTokens(tokens)` recolors the ramp endpoints + dot color and `invalidate()`s (theme swap);
 *  - `view.setRampColors(lowColorArgb, highColorArgb)` overrides the ramp from per-printer selectors;
 *  - `view.setViewMode(viewMode)` switches heatmap fill / colored probe dots / 3D wireframe;
 *  - `view.setMesh(model, scaleMode)` hands the new mesh + active scale and `invalidate()`s (D-13 —
 *    repaint on a new sample / a scale cycle, never per frame; there is no animation loop).
 *
 * @param tokens        the active resolved tokens (the caller collects the flow; THEME-01 — never raw).
 * @param model         the pure [BedMeshModel] (interpolated grid + probe dots + extents).
 * @param scaleMode     the active color-scale mode (D-09 — cycled by the screen's overlay toggle).
 * @param viewMode      HEATMAP (interpolated fill), PROBE_POINTS (ramp-colored dots), or ISO_WIREFRAME (3D grid).
 * @param lowColorArgb  packed ARGB for the ramp's low endpoint (resolved from per-printer selector).
 * @param highColorArgb packed ARGB for the ramp's high endpoint (resolved from per-printer selector).
 * @param midColorArgb  optional packed ARGB for the ramp's midpoint (e.g. tokens.outline); when set
 *                      the gradient reads low → mid → high instead of a direct 2-endpoint interpolation.
 * @param modifier      caller layout for the hosted View (the screen wraps it in `aspectRatio(1f)`).
 */
@Composable
fun BedMeshHeatmapHost(
    tokens: ThemeTokens,
    model: BedMeshModel,
    scaleMode: BedMeshHeatmapView.ScaleMode,
    viewMode: BedMeshHeatmapView.ViewMode,
    lowColorArgb: Int,
    highColorArgb: Int,
    midColorArgb: Int? = null,
    modifier: Modifier = Modifier,
) {
    // D-05/D-04: the heatmap AndroidView renders blank under @Preview — short-circuit to a labeled
    // stand-in so a screen embedding it previews cleanly. The real heatmap is the on-device gate.
    if (LocalInspectionMode.current) {
        PreviewPlaceholderBox(label = "Bed mesh (live on device)", modifier = modifier)
        return
    }
    AndroidView(
        factory = { ctx -> BedMeshHeatmapView(ctx) }, // created once; never recreated on a theme/data change
        update = { view ->
            view.applyTokens(tokens)                                       // D-06 push-tokens + invalidate (recolor, no recreation)
            view.setRampColors(lowColorArgb, highColorArgb, midColorArgb)  // override ramp from per-printer selectors
            view.setViewMode(viewMode)                      // switch render mode
            view.setMesh(model, scaleMode)                  // new mesh / scale cycle → invalidate (D-13, no loop)
        },
        modifier = modifier,
    )
}
