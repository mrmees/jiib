package works.mees.jiib.calibration

import works.mees.jiib.state.Capabilities
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TDD RED→GREEN for [ProbeTool] gate predicate and [probeToolSupport] list builder.
 *
 * Task 12 — probe-section branch.
 */
class ProbeToolGateTest {

    // ── Caps fixtures ──────────────────────────────────────────────────────────

    /** Standard klicky/BLTouch printer: manual_probe + probe + gcode_move; no eddy. */
    private val klickyCaps = Capabilities(
        objects = setOf("manual_probe", "probe", "gcode_move"),
    )

    /** Same printer but gcode_move absent (APPLY_BABYSTEP must gate off). */
    private val noGcodeMoveCAPS = Capabilities(
        objects = setOf("manual_probe", "probe"),
    )

    /** Printer with an eddy-current probe. */
    private val eddyCaps = Capabilities(
        objects = setOf("manual_probe", "probe", "gcode_move", "probe_eddy_current my_eddy"),
    )

    // ── probeToolSupported (atomic gate) ──────────────────────────────────────

    @Test
    fun zOffset_gates_on_manual_probe() {
        assertTrue(probeToolSupported(klickyCaps, ProbeTool.Z_OFFSET))
        assertFalse(probeToolSupported(Capabilities(), ProbeTool.Z_OFFSET))
    }

    @Test
    fun probeTest_gates_on_probe() {
        assertTrue(probeToolSupported(klickyCaps, ProbeTool.PROBE_TEST))
        assertFalse(probeToolSupported(Capabilities(), ProbeTool.PROBE_TEST))
    }

    @Test
    fun applyBabystep_gates_on_gcode_move() {
        assertTrue(probeToolSupported(klickyCaps, ProbeTool.APPLY_BABYSTEP))
        assertFalse(probeToolSupported(noGcodeMoveCAPS, ProbeTool.APPLY_BABYSTEP))
    }

    @Test
    fun eddyTools_gate_on_eddy_descriptor() {
        // All three eddy tools supported when eddy object present.
        assertTrue(probeToolSupported(eddyCaps, ProbeTool.EDDY_CALIBRATE))
        assertTrue(probeToolSupported(eddyCaps, ProbeTool.EDDY_TAP))
        assertTrue(probeToolSupported(eddyCaps, ProbeTool.EDDY_DRIVE_CURRENT))

        // Not supported on a plain klicky printer.
        assertFalse(probeToolSupported(klickyCaps, ProbeTool.EDDY_CALIBRATE))
        assertFalse(probeToolSupported(klickyCaps, ProbeTool.EDDY_TAP))
        assertFalse(probeToolSupported(klickyCaps, ProbeTool.EDDY_DRIVE_CURRENT))
    }

    // ── probeToolSupport (list builder) ───────────────────────────────────────

    @Test
    fun klicky_showUnsupportedFalse_returns_exactly_three_supported_tools() {
        val tools = probeToolSupport(klickyCaps, showUnsupported = false)
        // Exactly the three supported tools; no eddy tools.
        assertEquals(
            listOf(ProbeTool.Z_OFFSET, ProbeTool.PROBE_TEST, ProbeTool.APPLY_BABYSTEP),
            tools.map { it.tool },
        )
        assertTrue(tools.all { it.isSupported })
    }

    @Test
    fun withoutGcodeMove_applyBabystep_absent_when_unsupportedHidden() {
        val tools = probeToolSupport(noGcodeMoveCAPS, showUnsupported = false)
        assertFalse(tools.any { it.tool == ProbeTool.APPLY_BABYSTEP })
    }

    @Test
    fun eddyCaps_showUnsupportedFalse_includes_eddy_tools() {
        val tools = probeToolSupport(eddyCaps, showUnsupported = false)
        val toolSet = tools.map { it.tool }.toSet()
        assertTrue(ProbeTool.EDDY_CALIBRATE in toolSet)
        assertTrue(ProbeTool.EDDY_TAP in toolSet)
        assertTrue(ProbeTool.EDDY_DRIVE_CURRENT in toolSet)
        assertTrue(tools.all { it.isSupported })
    }

    @Test
    fun showUnsupportedTrue_all_six_tools_present() {
        val tools = probeToolSupport(klickyCaps, showUnsupported = true)
        assertEquals(ProbeTool.entries.size, tools.size)
        assertEquals(6, tools.size)
    }

    @Test
    fun supported_tools_sort_before_unsupported() {
        val tools = probeToolSupport(klickyCaps, showUnsupported = true)
        val supported = tools.takeWhile { it.isSupported }
        val unsupported = tools.dropWhile { it.isSupported }
        assertTrue(supported.isNotEmpty())
        assertTrue(unsupported.isNotEmpty())
        assertTrue(supported.all { it.isSupported })
        assertTrue(unsupported.none { it.isSupported })
    }
}
