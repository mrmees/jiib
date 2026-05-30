package works.mees.dinghy.bench

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

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
            else -> mountComposeScene()
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

        /** Rolling temperature-graph window (samples kept on screen). */
        private const val GRAPH_MAX = 120

        /** Bounded console scrollback (lines kept on screen). */
        private const val CONSOLE_MAX = 40
    }
}
