package works.mees.dinghy.bench

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Benchmark harness entry point — EXPORTED so :macrobenchmark / UiAutomator can
 * launch it with a scene-select intent extra.
 *
 * ============================== STUB (plan 01-01) ==============================
 * This is a SKELETON only. It reads the scene-select extra and renders a marker
 * so the launch path + intent contract are wired and registered. The actual
 * worst-case scenes (Compose vs. hybrid-Views), the in-process synthetic 2–4 Hz
 * feed (D-06), the real Coil PNG decode + Canvas temp graph + console spew
 * (D-07), and the gfxinfo measurement are all implemented in plan 01-03.
 *
 * Launch contract (consumed by 01-03 + the macrobenchmark UiAutomator script):
 *   adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene compose
 *   adb shell am start -n works.mees.dinghy/.bench.BenchActivity --es scene views
 *
 * SCOPE GUARD: no Moonraker, no connection layer, no state machine here.
 * ==============================================================================
 */
class BenchActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Scene-select contract is defined now so 01-03 only fills in the bodies.
        val scene = intent?.getStringExtra(EXTRA_SCENE) ?: SCENE_COMPOSE

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        // STUB body — replaced by the real scenes in plan 01-03.
                        Text(text = "BenchActivity stub — scene=$scene (scenes land in 01-03)")
                    }
                }
            }
        }
    }

    companion object {
        /** Intent extra key selecting which worst-case scene to render. */
        const val EXTRA_SCENE = "scene"

        /** Render the Compose worst-case scene (default). */
        const val SCENE_COMPOSE = "compose"

        /** Render the hybrid-Views worst-case scene. */
        const val SCENE_VIEWS = "views"
    }
}
