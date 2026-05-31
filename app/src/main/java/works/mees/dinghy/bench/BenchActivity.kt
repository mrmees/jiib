package works.mees.dinghy.bench

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import works.mees.dinghy.render.RingBuffer
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme

/**
 * Benchmark harness entry point — EXPORTED (registered by plan 01-01's manifest, which
 * this plan does NOT edit) so :macrobenchmark / UiAutomator can launch it with a
 * scene-select intent extra.
 *
 * Filled in by plan 01-03: reads the [EXTRA_SCENE] extra, launches full-screen at the
 * device's real resolution (1920×1200 on the Nexus 7), mounts the chosen worst-case
 * scene ([ComposeBenchScene] or [ViewsBenchScene]), and starts the SAME deterministic
 * [SyntheticFeed] (D-06) driving whichever scene is mounted — so the Compose-vs-Views
 * comparison is fair (D-02).
 *
 * Launch contract (consumed by the macrobenchmark UiAutomator script):
 *   adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene compose
 *   adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene views
 *   adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene render
 *
 * The `render` scene (added in plan 03-07) is the D-10 perf scene: it mounts ONLY the two shared
 * render primitives (Compose [works.mees.dinghy.render.ProgressRing] + classic-Views
 * [works.mees.dinghy.render.GraphView] via [works.mees.dinghy.render.GraphViewHost]) driven by the
 * SAME [SyntheticFeed] filling a [RingBuffer], so the on-device gfxinfo framestats capture measures
 * the ring+graph draw cost on the Adreno-320 floor (criterion #5). It is an intent-extra route — the
 * exported activity registration in the shared manifest is unchanged.
 *
 * SCOPE GUARD: no Moonraker, no connection layer, no state machine — measurement only.
 */
class BenchActivity : ComponentActivity() {

    private var feedJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep the screen on for the full measured run; render full-screen so the scene
        // pushes the device's real panel resolution (1920×1200) — the fill-rate wall the
        // benchmark exists to measure (D-01a/D-07).
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Fairness self-check: prove the feed replays byte-identically before driving a
        // scene (D-02). Cheap; fails loudly if a harness change breaks determinism.
        SyntheticFeed.assertDeterministic()

        val scene = intent?.getStringExtra(EXTRA_SCENE) ?: SCENE_COMPOSE
        when (scene) {
            SCENE_VIEWS -> mountViewsScene()
            SCENE_RENDER -> mountRenderScene()
            else -> mountComposeScene()
        }
    }

    /**
     * SCENE RENDER (D-10, plan 03-07): the ring + line-graph perf scene. Drives the SAME
     * [SyntheticFeed] verbatim — each feed event's progress drives the Compose [ProgressRing] and the
     * graph-sample fills a bounded [RingBuffer] (GRAPH_MAX window) whose snapshot drives the Views
     * [GraphView] through [GraphViewHost]. Both primitives recolor from one [ThemeResolver]'s tokens
     * (Compose via LocalTokens at the [DinghyTheme] boundary, the Views graph via push-tokens, D-06).
     * Repaint is value-driven at the ~3 Hz cadence — no per-frame animation (D-13).
     */
    private fun mountRenderScene() {
        val feed = SyntheticFeed()
        val resolver = ThemeResolver()
        val state = MutableStateFlow(RenderSceneState())

        setContent {
            DinghyTheme(resolver = resolver) {
                val tokens by resolver.tokens.collectAsStateWithLifecycle()
                RenderBenchScene(
                    state = state,
                    tokens = tokens,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        feedJob = lifecycleScope.launch {
            val ring = RingBuffer(GRAPH_MAX)
            feed.events().collect { event ->
                // Fill the bounded ring buffer from the deterministic feed (extruder series).
                ring.push(event.graphSample.extruder.toFloat())
                state.value = RenderSceneState(
                    progress = event.progress.toFloat(),
                    snapshot = ring.snapshot(),
                )
            }
        }
    }

    /** SCENE A: Compose-everywhere. Drive an immutable [ComposeSceneState] StateFlow. */
    private fun mountComposeScene() {
        val feed = SyntheticFeed()
        val initial = feed.replay().first()
        val state = MutableStateFlow(
            ComposeSceneState(
                event = initial,
                graphHistory = listOf(initial.graphSample),
                console = initial.consoleLines.takeLast(CONSOLE_MAX),
            ),
        )

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ComposeBenchScene(state = state, modifier = Modifier.fillMaxSize())
                }
            }
        }

        feedJob = lifecycleScope.launch {
            val graph = ArrayDeque<GraphSample>()
            val console = ArrayDeque<String>()
            feed.events().collect { event ->
                graph.addLast(event.graphSample)
                while (graph.size > GRAPH_MAX) graph.removeFirst()
                event.consoleLines.forEach { console.addLast(it) }
                while (console.size > CONSOLE_MAX) console.removeFirst()
                state.value = ComposeSceneState(
                    event = event,
                    graphHistory = graph.toList(),
                    console = console.toList(),
                )
            }
        }
    }

    /** SCENE B: classic/hybrid Views. Push each shared-feed event into the scene. */
    private fun mountViewsScene() {
        val feed = SyntheticFeed()
        val view = ViewsBenchScene(this)
        setContentView(view)

        feedJob = lifecycleScope.launch {
            val graph = ArrayDeque<GraphSample>()
            val console = ArrayDeque<String>()
            feed.events().collect { event ->
                graph.addLast(event.graphSample)
                while (graph.size > GRAPH_MAX) graph.removeFirst()
                event.consoleLines.forEach { console.addLast(it) }
                while (console.size > CONSOLE_MAX) console.removeFirst()
                view.render(event, graph.toList(), console.toList())
            }
        }
    }

    override fun onDestroy() {
        feedJob?.cancel()
        super.onDestroy()
    }

    companion object {
        /** Intent extra key selecting which worst-case scene to render. */
        const val EXTRA_SCENE = "scene"

        /** Render the Compose worst-case scene (default). */
        const val SCENE_COMPOSE = "compose"

        /** Render the hybrid-Views worst-case scene. */
        const val SCENE_VIEWS = "views"

        /** Render the ring+graph perf scene (D-10 — the shared render primitives only). */
        const val SCENE_RENDER = "render"

        /** Rolling temperature-graph window (samples kept on screen). */
        private const val GRAPH_MAX = 120

        /** Bounded console scrollback (lines kept on screen). */
        private const val CONSOLE_MAX = 40
    }
}
