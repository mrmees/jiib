package works.mees.jiib.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import works.mees.jiib.render.GraphViewHost
import works.mees.jiib.render.ProgressRing
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens

/**
 * SCENE RENDER — the D-10 perf scene that exercises BOTH shared render primitives (03-05) at the
 * throttled ~3 Hz SyntheticFeed cadence, so the on-device `dumpsys gfxinfo framestats` capture
 * measures the ring + line-graph draw cost on the Adreno-320 floor (criterion #5).
 *
 * Unlike [ComposeBenchScene] / [ViewsBenchScene] (which stress a worst-case printer SCREEN — Files
 * list + console + graph — to settle the Compose-vs-Views TOOLKIT verdict, D-02), this scene draws
 * exactly the two primitives every later live surface inherits:
 *  - the Compose [ProgressRing] (low-churn, one value → one redraw), driven by the feed's `progress`;
 *  - the classic-Views [works.mees.jiib.render.GraphView] (high-churn) hosted via [GraphViewHost],
 *    driven by the bounded [works.mees.jiib.render.RingBuffer] snapshot (D-12).
 *
 * The SAME [SyntheticFeed] fills the ring buffer in [BenchActivity] (reused verbatim — no second
 * feed, D-06/fairness), and [BenchActivity] re-publishes (progress, snapshot) into [state] each tick.
 * Both primitives recolor for free on a theme flip because the ring reads [LocalTokens] and the host
 * pushes the same [ThemeTokens] to the Views Canvas (D-06). Motion is static glow only — neither
 * primitive animates per Choreographer frame (D-13); the redraw is value-driven at the feed cadence.
 *
 * LAYOUT FIDELITY (criterion #5 must measure the REAL graph, not an over-sized one): the first
 * capture laid out the ring and graph each at `weight(1f)` of a full portrait column, so the
 * `GraphView` covered ≈ half the screen (~1200×900 px) and the translucent area-fill bled across
 * it — over-sizing the fill region vs the canonical Print Status mockup. Per `LAYOUT.md` the portrait
 * rhythm is **Focus / Field / Gutter ≈ 40 / 40 / 20**, and the graph is a **Field panel** (a `.graph`
 * card with y-labels / x-axis / legend chrome *around* the `.plot`), NOT half the screen. This scene
 * now reproduces that rhythm: ring in the Focus band, the graph centered in the Field band as a panel
 * (not edge-to-edge), and a Gutter spacer — so the gfxinfo capture measures the graph at its real size.
 *
 * @param state    the latest (progress, ring-buffer snapshot) pushed by [BenchActivity]'s feed loop.
 * @param drawArea paint the canonical translucent area-fill (default `true`). The fill-rate ISOLATION
 *                 lever (T-03-08): the on-device A-B capture launches the scene with it off to attribute
 *                 the fill cost, then on for the design-true number. Product surfaces always pass `true`.
 */
@Composable
fun RenderBenchScene(
    state: StateFlow<RenderSceneState>,
    tokens: ThemeTokens,
    modifier: Modifier = Modifier,
    drawArea: Boolean = true,
) {
    val s by state.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // FOCUS band (~40%): the progress ring (sacred square wraps itself via aspectRatio(1f)).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(FOCUS_WEIGHT),
            contentAlignment = Alignment.Center,
        ) {
            ProgressRing(progress = s.progress, modifier = Modifier.fillMaxWidth())
        }
        // FIELD band (~40%): the live line graph as a centered PANEL (not edge-to-edge). The graph
        // card in the mockup carries chrome around the plot, so the real fill region is a band inside
        // the Field — we approximate that by insetting the hosted GraphView, recolored from the same
        // tokens (D-06). `drawArea` toggles the fill-rate isolation A-B (T-03-08).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(FIELD_WEIGHT)
                .padding(horizontal = 8.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            GraphViewHost(
                tokens = tokens,
                snapshot = s.snapshot,
                modifier = Modifier.fillMaxSize(),
                drawArea = drawArea,
            )
        }
        // GUTTER band (~20%): in the real screen this holds the Back/action buttons; here it is a
        // spacer so the Focus/Field bands sit at their true portrait proportion (40/40/20) — the
        // graph is NOT given the gutter's height, which is what over-sized it in the first capture.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(GUTTER_WEIGHT),
        )
    }
}

// Portrait Focus/Field/Gutter rhythm from LAYOUT.md (≈ 40/40/20). The graph (Field) therefore gets
// ~40% of the column height, not the ~50% the first naive weight(1f)/weight(1f) split handed it.
private const val FOCUS_WEIGHT = 40f
private const val FIELD_WEIGHT = 40f
private const val GUTTER_WEIGHT = 20f

/**
 * Immutable per-tick snapshot the render scene draws. [BenchActivity] rebuilds this from the SAME
 * [SyntheticFeed] each event: `progress` drives the ring, `snapshot` (the bounded RingBuffer copy)
 * drives the graph. Holding the array by reference is fine — RingBuffer.snapshot() already returns a
 * fresh defensive copy per push (it never hands out its backing store), so the scene never mutates it.
 */
data class RenderSceneState(
    val progress: Float = 0f,
    val snapshot: FloatArray = FloatArray(0),
) {
    // data class over a FloatArray: identity-compare the array so a new snapshot is a new state.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RenderSceneState) return false
        return progress == other.progress && snapshot === other.snapshot
    }

    override fun hashCode(): Int = 31 * progress.hashCode() + System.identityHashCode(snapshot)
}
