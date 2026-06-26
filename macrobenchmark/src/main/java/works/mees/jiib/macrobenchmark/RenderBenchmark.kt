package works.mees.jiib.macrobenchmark

import android.content.Intent
import android.os.SystemClock
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Render-primitive perf benchmark (D-10 / criterion #5) — drives the `render` scene of
 * [works.mees.jiib.bench.BenchActivity] (the Compose [works.mees.jiib.render.ProgressRing] + the
 * classic-Views [works.mees.jiib.render.GraphView], both fed by the deterministic ~3 Hz
 * SyntheticFeed filling a RingBuffer) against :app's RELEASE variant on the real `flox` tablet. This
 * REUSES the Phase-1 macrobenchmark harness (mirrors [ToolkitBenchmark]) — no new perf rig.
 *
 * ============================ SYSTEM OF RECORD (read this) ============================
 * `FrameTimingMetric` here is CORROBORATION ONLY. On the Android 6 / API-23 Nexus 7 floor it surfaces
 * `frameDurationCpuMs` but NOT `frameOverrunMs` (`frameOverrunMs` requires API 31+, absent on the
 * target), and Macrobenchmark's framestats parsing is OS-version-fragile on old builds. Therefore the
 * AUTHORITATIVE on-device metric is the raw `dumpsys gfxinfo works.mees.jiib framestats` CSV,
 * captured during the on-device run and parsed by `tools/gfxinfo-parser/parse_framestats.py` into
 * p50/p90/p95 + count(frames>700 ms) — recorded in 03-PERF-RESULTS.md. Do NOT treat the numbers this
 * rule prints as the verdict; they corroborate the gfxinfo CSV (per ADR 0001).
 *
 * NO Baseline Profile generator is included or gated here — profile-guided compilation is an API 24+
 * feature and a NO-OP on the API-23 target (D-03). We measure the plain release/R8 build as installed.
 * =====================================================================================
 *
 * Unlike [ToolkitBenchmark], the ring+graph are NOT scrolled — they redraw value-driven at the feed
 * cadence (D-13, no animation loop). So [renderScene] cold-launches the scene and HOLDS for a fixed
 * dwell while the feed pushes frames; the gfxinfo capture (Task 2) brackets that same dwell.
 */
@RunWith(AndroidJUnit4::class)
class RenderBenchmark {

    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun renderScene() = benchmarkRule.measureRepeated(
        packageName = TARGET_PACKAGE,
        // FrameTimingMetric = corroboration only (see class header). gfxinfo CSV is SoR.
        metrics = listOf(FrameTimingMetric()),
        iterations = ITERATIONS,
        // Each iteration cold-launches BenchActivity with the render scene so the in-process
        // synthetic feed restarts identically.
        startupMode = StartupMode.COLD,
        setupBlock = {
            killProcess()
            val intent = Intent().apply {
                setClassName(TARGET_PACKAGE, BENCH_ACTIVITY)
                putExtra(EXTRA_SCENE, SCENE_RENDER)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivityAndWait(intent)
        },
    ) {
        // The ring/graph are not scrolled — just hold while the throttled feed pushes samples so the
        // measured window captures steady-state ring+graph draws (the fill-rate cost we gate on).
        //
        // BUG-03: `waitForIdle(DWELL_MS)` RETURNS as soon as the UI goes idle, and this scene goes
        // idle between its ~3 Hz redraws — so it would end the measure window far earlier than the
        // intended dwell, under-sampling frames. Let startup settle once, then hold for the FULL
        // fixed window with an explicit timed sleep so the steady state is observed end-to-end.
        device.waitForIdle()
        SystemClock.sleep(DWELL_MS)
    }

    companion object {
        // The measured artifact is :app's RELEASE variant (D-03/D-07).
        private const val TARGET_PACKAGE = "works.mees.jiib"
        private const val BENCH_ACTIVITY = "works.mees.jiib.bench.BenchActivity"
        private const val EXTRA_SCENE = "scene"
        private const val SCENE_RENDER = "render"

        private const val ITERATIONS = 5
        private const val DWELL_MS = 12_000L
    }
}
