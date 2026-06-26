package works.mees.jiib.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Host-side proof for [computeGraphYRange] — the dynamic Temperature graph Y-range (05 UI tweak):
 * fits all finite sensor history + active setpoints, ±5° absolute pad, bounds rounded OUTWARD to 5°,
 * clamped to the 0..350 heater envelope, no hard floor (the absolute pad is the anti-noise mechanism).
 */
class GraphYRangeTest {

    private fun assertRange(loExp: Float, hiExp: Float, r: ClosedFloatingPointRange<Float>) {
        assertEquals("lo", loExp, r.start, 0.001f)
        assertEquals("hi", hiExp, r.endInclusive, 0.001f)
    }

    @Test
    fun emptyInputReturnsTheCalmDefaultBand() {
        assertRange(0f, 40f, computeGraphYRange(emptyList(), emptyList()))
        // all-empty series + no setpoints is still "no finite data" → default.
        assertRange(0f, 40f, computeGraphYRange(listOf(FloatArray(0)), listOf(null)))
    }

    @Test
    fun userExample_10to100_becomes5to105() {
        // The canonical case Matthew described: data 10..100 → padded+rounded to 5..105.
        assertRange(5f, 105f, computeGraphYRange(listOf(floatArrayOf(10f, 55f, 100f)), emptyList()))
    }

    @Test
    fun steadyIdleGetsABreathingBand_noNoiseZoom() {
        // A single dead-steady ~26° reading: absolute pad + round-to-5 → a calm 20..35, never collapsed.
        assertRange(20f, 35f, computeGraphYRange(listOf(floatArrayOf(26f, 26f, 26f)), emptyList()))
    }

    @Test
    fun activeSetpointExpandsTheRange_offSetpointIgnored() {
        // Current 25 with a 200° target → range pre-zooms to include the setpoint (20..205).
        assertRange(20f, 205f, computeGraphYRange(listOf(floatArrayOf(25f)), listOf(200f)))
        // A null (off) setpoint must NOT drag the range.
        assertRange(20f, 35f, computeGraphYRange(listOf(floatArrayOf(26f)), listOf(null)))
    }

    @Test
    fun boundsClampIntoTheHeaterEnvelope() {
        // Near-ceiling: 348 → hi clamps to 350 (not 355); lo floors below.
        assertRange(340f, 350f, computeGraphYRange(listOf(floatArrayOf(348f)), emptyList()))
        // Near-floor: 2 → lo clamps to 0 (not −5).
        assertRange(0f, 10f, computeGraphYRange(listOf(floatArrayOf(2f)), emptyList()))
    }

    @Test
    fun multipleTracesSpanLowestToHighest() {
        // nozzle history climbing to 100, bed steady ~33 → range spans both.
        val range = computeGraphYRange(
            listOf(floatArrayOf(25f, 60f, 100f), floatArrayOf(33f, 33f, 33f)),
            listOf(null, null),
        )
        // overall min 25 → 25-5=20; overall max 100 → 100+5=105.
        assertRange(20f, 105f, range)
    }

    @Test
    fun nonFiniteSamplesAreSkipped() {
        // NaN / Infinity must not poison the min/max.
        val range = computeGraphYRange(
            listOf(floatArrayOf(Float.NaN, 50f, Float.POSITIVE_INFINITY, 80f)),
            emptyList(),
        )
        assertRange(45f, 85f, range) // from finite {50,80}: 50-5=45, 80+5=85
    }
}
