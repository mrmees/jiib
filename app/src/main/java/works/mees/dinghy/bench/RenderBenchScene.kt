package works.mees.dinghy.bench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.StateFlow
import works.mees.dinghy.render.GraphViewHost
import works.mees.dinghy.render.ProgressRing
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * SCENE RENDER — the D-10 perf scene that exercises BOTH shared render primitives (03-05) at the
 * throttled ~3 Hz SyntheticFeed cadence, so the on-device `dumpsys gfxinfo framestats` capture
 * measures the ring + line-graph draw cost on the Adreno-320 floor (criterion #5).
 *
 * Unlike [ComposeBenchScene] / [ViewsBenchScene] (which stress a worst-case printer SCREEN — Files
 * list + console + graph — to settle the Compose-vs-Views TOOLKIT verdict, D-02), this scene draws
 * exactly the two primitives every later live surface inherits:
 *  - the Compose [ProgressRing] (low-churn, one value → one redraw), driven by the feed's `progress`;
 *  - the classic-Views [works.mees.dinghy.render.GraphView] (high-churn) hosted via [GraphViewHost],
 *    driven by the bounded [works.mees.dinghy.render.RingBuffer] snapshot (D-12).
 *
 * The SAME [SyntheticFeed] fills the ring buffer in [BenchActivity] (reused verbatim — no second
 * feed, D-06/fairness), and [BenchActivity] re-publishes (progress, snapshot) into [state] each tick.
 * Both primitives recolor for free on a theme flip because the ring reads [LocalTokens] and the host
 * pushes the same [ThemeTokens] to the Views Canvas (D-06). Motion is static glow only — neither
 * primitive animates per Choreographer frame (D-13); the redraw is value-driven at the feed cadence.
 *
 * @param state the latest (progress, ring-buffer snapshot) pushed by [BenchActivity]'s feed loop.
 */
@Composable
fun RenderBenchScene(
    state: StateFlow<RenderSceneState>,
    tokens: ThemeTokens,
    modifier: Modifier = Modifier,
) {
    val s by state.collectAsState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Compose half: the progress ring (sacred square wraps itself via aspectRatio(1f)).
        ProgressRing(
            progress = s.progress,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
        // Views half: the live line graph hosted in Compose, recolored from the same tokens (D-06).
        GraphViewHost(
            tokens = tokens,
            snapshot = s.snapshot,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}

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
