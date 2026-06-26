package works.mees.jiib.macrobenchmark

import android.content.Intent
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Head-to-head toolkit benchmark (D-02/D-05) — drives BOTH the Compose scene and the
 * classic-Views scene of [works.mees.jiib.bench.BenchActivity] with an IDENTICAL
 * UiAutomator sequence against :app's RELEASE variant, so the only difference between the
 * two runs is the toolkit.
 *
 * ============================ SYSTEM OF RECORD (read this) ============================
 * `FrameTimingMetric` here is CORROBORATION ONLY. On the Android 6 / API-23 Nexus 7 it
 * surfaces `frameDurationCpuMs` (CPU-side frame duration) but NOT `frameOverrunMs` —
 * `frameOverrunMs` requires API 31+ (absent on the target). Macrobenchmark's framestats
 * parsing is also OS-version-fragile on old builds. Therefore the AUTHORITATIVE on-device
 * metric is the raw `dumpsys gfxinfo <pkg> framestats` CSV, captured during the on-device
 * run (plan 01-04) and parsed by `tools/gfxinfo-parser/parse_framestats.py` into
 * p50/p90/p95 + count(frames>700 ms). Do NOT treat the numbers this rule prints as the
 * verdict; they corroborate the gfxinfo CSV.
 *
 * NO Baseline Profile generator is included or gated here — profile-guided compilation is
 * an API 24+ feature and a NO-OP on the API-23 target (D-03). We measure the plain
 * release/R8 build as installed.
 * =====================================================================================
 *
 * Both @Test methods call the SAME [driveScene] with the SAME dwell/scroll/iterations, so
 * the workload is byte-for-byte identical apart from the scene-select intent extra. Any
 * divergence here would invalidate the fairness of the toolkit decision.
 */
@RunWith(AndroidJUnit4::class)
class ToolkitBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun composeScene() = measureScene(scene = "compose")

    @Test
    fun viewsScene() = measureScene(scene = "views")

    private fun measureScene(scene: String) = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        // FrameTimingMetric = corroboration only (see class header). gfxinfo CSV is SoR.
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        // Each iteration cold-launches BenchActivity with the scene-select extra so the
        // in-process synthetic feed restarts identically for both scenes.
        startupMode = StartupMode.COLD,
        setupBlock = {
            killProcess()
            val intent = Intent().apply {
                setClassName(TARGET_PACKAGE, BENCH_ACTIVITY)
                putExtra(EXTRA_SCENE, scene)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivityAndWait(intent)
        },
    ) {
        driveScene()
    }

    /**
     * The IDENTICAL drive sequence applied to both scenes: let the feed render for a fixed
     * dwell, then scroll the Files list a fixed number of times by a fixed distance, with a
     * fixed settle between scrolls. Same numbers ⇒ same workload ⇒ fair comparison (D-02).
     */
    private fun androidx.benchmark.macro.MacrobenchmarkScope.driveScene() {
        // Initial dwell so the synthetic feed pushes several frames before scrolling.
        device.waitForIdle(INITIAL_DWELL_MS)

        // The Files list is the largest scrollable surface in both scenes. Find any
        // scrollable container and apply identical flings.
        val scrollable = device.wait(Until.findObject(By.scrollable(true)), FIND_TIMEOUT_MS)
        repeat(SCROLL_ITERATIONS) {
            scrollable?.fling(Direction.DOWN)
            device.waitForIdle(SCROLL_SETTLE_MS)
        }
        repeat(SCROLL_ITERATIONS) {
            scrollable?.fling(Direction.UP)
            device.waitForIdle(SCROLL_SETTLE_MS)
        }
    }

    companion object {
        // The measured artifact is :app's RELEASE variant (D-03/D-07).
        private const val TARGET_PACKAGE = "works.mees.jiib"
        private const val BENCH_ACTIVITY = "works.mees.jiib.bench.BenchActivity"
        private const val EXTRA_SCENE = "scene"

        private const val ITERATIONS = 5
        private const val INITIAL_DWELL_MS = 3_000L
        private const val FIND_TIMEOUT_MS = 5_000L
        private const val SCROLL_ITERATIONS = 6
        private const val SCROLL_SETTLE_MS = 500L
    }
}
