package works.mees.jiib.bench

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.random.Random

/**
 * Deterministic in-process synthetic feed (D-06) — the SINGLE source of truth both
 * benchmark scenes consume so the Compose-vs-Views head-to-head is FAIR (D-02).
 *
 * Fairness contract:
 *  - Fixed [seed] + fixed [periodMs] schedule ⇒ the emitted [FeedEvent] sequence is
 *    REPLAYABLE BYTE-IDENTICALLY across runs and across BOTH scenes. [replay] returns
 *    the canonical event list; two `SyntheticFeed` built with the same seed/length
 *    produce `equals`-identical lists (asserted in [assertDeterministic]).
 *  - Events mimic Moonraker `notify_status_update` shape (extruder/bed temps, print
 *    progress, toolhead position, file-list mutations, temperature graph samples, and
 *    console appends) — enough to drive a representative worst-case printer screen.
 *
 * This is NOT a connection layer and NOT a panel. It is a measurement fixture only:
 * no networking, no Moonraker, no PrinterState. The shape merely RESEMBLES Moonraker
 * so the recomposition / data-shaping cost the benchmark measures is realistic.
 *
 * @param seed         fixed RNG seed — same seed ⇒ same byte-identical event stream.
 * @param eventCount   number of events to emit (the full deterministic schedule).
 * @param periodMs     inter-event delay; 333 ms ≈ 3 Hz, inside the planned 2–4 Hz band.
 */
class SyntheticFeed(
    private val seed: Long = DEFAULT_SEED,
    private val eventCount: Int = DEFAULT_EVENT_COUNT,
    private val periodMs: Long = DEFAULT_PERIOD_MS,
) {

    /**
     * Build the full deterministic event schedule eagerly. Pure function of
     * (seed, eventCount): no clock, no shared mutable state, no I/O. Called by
     * [events] for live driving and by [replay] for the fairness assertion.
     */
    fun replay(): List<FeedEvent> {
        val rng = Random(seed)
        val out = ArrayList<FeedEvent>(eventCount)

        // Running synthetic printer state, mutated deterministically per tick.
        var extruderTemp = 200.0
        var bedTemp = 60.0
        val extruderTarget = 215.0
        val bedTarget = 60.0
        var progress = 0.0
        var posX = 110.0
        var posY = 110.0
        var posZ = 0.2
        var fileCounter = 0

        // The visible file list mutates over the run (Files-panel churn). Seeded once.
        val files = ArrayList<GcodeFile>(INITIAL_FILE_COUNT)
        repeat(INITIAL_FILE_COUNT) { i ->
            files += GcodeFile(
                id = i,
                name = "bench_part_%03d.gcode".format(i),
                sizeKb = 50 + rng.nextInt(9_950),
                // Thumbnail seed drives REAL Coil PNG decode (D-07) — see [SyntheticThumbnail].
                thumbSeed = rng.nextInt(),
            )
        }
        fileCounter = INITIAL_FILE_COUNT

        for (i in 0 until eventCount) {
            // Temps wander deterministically toward their targets with seeded noise.
            extruderTemp += (extruderTarget - extruderTemp) * 0.15 + (rng.nextDouble() - 0.5) * 1.5
            bedTemp += (bedTarget - bedTemp) * 0.15 + (rng.nextDouble() - 0.5) * 0.6
            progress = (progress + 0.004).coerceAtMost(1.0)
            posX += (rng.nextDouble() - 0.5) * 4.0
            posY += (rng.nextDouble() - 0.5) * 4.0
            posZ += 0.0008

            // Periodically mutate the file list (insert near the top) — list churn.
            if (i % FILE_MUTATE_EVERY == 0) {
                files.add(
                    0,
                    GcodeFile(
                        id = fileCounter,
                        name = "bench_part_%03d.gcode".format(fileCounter),
                        sizeKb = 50 + rng.nextInt(9_950),
                        thumbSeed = rng.nextInt(),
                    ),
                )
                fileCounter++
                if (files.size > MAX_FILE_COUNT) files.removeAt(files.size - 1)
            }

            // Bounded console spew — append a couple of lines per tick.
            val console = buildList {
                add("recv: temps E:%.1f/%.0f B:%.1f/%.0f".format(extruderTemp, extruderTarget, bedTemp, bedTarget))
                if (i % 5 == 0) add("send: M105")
            }

            out += FeedEvent(
                index = i,
                extruderTemp = round1(extruderTemp),
                extruderTarget = extruderTarget,
                bedTemp = round1(bedTemp),
                bedTarget = bedTarget,
                progress = round3(progress),
                posX = round2(posX),
                posY = round2(posY),
                posZ = round3(posZ),
                files = files.toList(),
                // Graph sample pair (extruder, bed) appended to the rolling history.
                graphSample = GraphSample(round1(extruderTemp), round1(bedTemp)),
                consoleLines = console,
            )
        }
        return out
    }

    /**
     * Live driver: emit the deterministic [replay] schedule at [periodMs] cadence
     * (2–4 Hz). The ORDER and CONTENT are identical to [replay] — only the timing is
     * added here — so both scenes still see byte-identical events.
     */
    fun events(): Flow<FeedEvent> = flow {
        val schedule = replay()
        for (event in schedule) {
            emit(event)
            kotlinx.coroutines.delay(periodMs)
        }
    }

    companion object {
        const val DEFAULT_SEED: Long = 0xD1_46_47_59L          // fixed deterministic seed
        const val DEFAULT_EVENT_COUNT: Int = 600                // ≈ 200 s @ 3 Hz
        const val DEFAULT_PERIOD_MS: Long = 333                 // ≈ 3 Hz (inside 2–4 Hz)

        const val INITIAL_FILE_COUNT: Int = 60
        const val MAX_FILE_COUNT: Int = 80
        const val FILE_MUTATE_EVERY: Int = 7

        /**
         * Fairness self-check (D-02): two feeds with the same seed/length MUST
         * replay byte-identically. Throws [IllegalStateException] on divergence so a
         * harness regression that breaks fairness fails loudly rather than silently
         * invalidating the toolkit verdict. Cheap enough to call at scene entry.
         */
        fun assertDeterministic(
            seed: Long = DEFAULT_SEED,
            eventCount: Int = DEFAULT_EVENT_COUNT,
        ) {
            val a = SyntheticFeed(seed, eventCount).replay()
            val b = SyntheticFeed(seed, eventCount).replay()
            check(a == b) {
                "SyntheticFeed is NOT deterministic (seed=$seed) — fairness (D-02) broken"
            }
        }

        private fun round1(v: Double): Double = Math.round(v * 10.0) / 10.0
        private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0
        private fun round3(v: Double): Double = Math.round(v * 1000.0) / 1000.0
    }
}

/** A single Moonraker-shaped status update (immutable ⇒ Compose-stable). */
data class FeedEvent(
    val index: Int,
    val extruderTemp: Double,
    val extruderTarget: Double,
    val bedTemp: Double,
    val bedTarget: Double,
    val progress: Double,
    val posX: Double,
    val posY: Double,
    val posZ: Double,
    val files: List<GcodeFile>,
    val graphSample: GraphSample,
    val consoleLines: List<String>,
)

/** One file-list row; [thumbSeed] drives a REAL deterministic PNG decode (D-07). */
data class GcodeFile(
    val id: Int,
    val name: String,
    val sizeKb: Int,
    val thumbSeed: Int,
)

/** One temperature-graph sample pair (extruder, bed) in °C. */
data class GraphSample(
    val extruder: Double,
    val bed: Double,
)
