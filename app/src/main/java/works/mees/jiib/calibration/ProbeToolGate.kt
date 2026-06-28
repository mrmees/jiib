package works.mees.jiib.calibration

import works.mees.jiib.state.Capabilities

/**
 * The six probe-tool actions the Probe sub-hub renders. Each has a distinct gating predicate
 * checked against [Capabilities.objects] (and, for eddy tools, [eddyProbeDescriptor]).
 *
 * Unlike [CalibrationRoutine], the gate is NOT a single gatingObject string because
 * APPLY_BABYSTEP needs `gcode_move` (distinct from the command itself) and the three EDDY_*
 * tools share a descriptor lookup. The predicate lives in [probeToolSupported].
 */
enum class ProbeTool {
    Z_OFFSET,
    PROBE_TEST,
    APPLY_BABYSTEP,
    EDDY_CALIBRATE,
    EDDY_TAP,
    EDDY_DRIVE_CURRENT,
}

/** One hub row: a probe tool + its live supported flag. Supported tools sort first. */
data class ProbeToolEntry(
    val tool: ProbeTool,
    val isSupported: Boolean,
)

/**
 * Pure gating predicate for a single [ProbeTool] against live [caps].
 *
 * - [ProbeTool.Z_OFFSET] — `hasObject("manual_probe")`: Klipper loads `[manual_probe]` when
 *   any probe calibration flow is available; it is the canonical gate for the Z-Offset tool.
 * - [ProbeTool.PROBE_TEST] — `hasObject("probe")`: the PROBE_ACCURACY command's backing object.
 * - [ProbeTool.APPLY_BABYSTEP] — `hasObject("gcode_move")`: the babystep readback requires
 *   `SET_GCODE_OFFSET` via `gcode_move`; [AvailabilityPredicate] has no AllOf so this gate lives
 *   here, not in the command registry.
 * - [ProbeTool.EDDY_CALIBRATE], [ProbeTool.EDDY_TAP], [ProbeTool.EDDY_DRIVE_CURRENT] —
 *   `eddyProbeDescriptor(caps) != null`: all three eddy actions require the chip descriptor.
 */
fun probeToolSupported(caps: Capabilities, tool: ProbeTool): Boolean = when (tool) {
    ProbeTool.Z_OFFSET -> caps.hasObject("manual_probe")
    ProbeTool.PROBE_TEST -> caps.hasObject("probe")
    ProbeTool.APPLY_BABYSTEP -> caps.hasObject("gcode_move")
    ProbeTool.EDDY_CALIBRATE,
    ProbeTool.EDDY_TAP,
    ProbeTool.EDDY_DRIVE_CURRENT -> eddyProbeDescriptor(caps) != null
}

/**
 * Pure hub data source: all [ProbeTool]s mapped to [ProbeToolEntry], supported-first ordered
 * (stable within each group by enum order). When [showUnsupported] is `false` (the default),
 * unsupported tools are omitted; when `true`, all six render (greyed if unsupported).
 *
 * Mirrors [calibrationSupport] exactly.
 */
fun probeToolSupport(caps: Capabilities, showUnsupported: Boolean): List<ProbeToolEntry> =
    ProbeTool.entries
        .map { ProbeToolEntry(it, probeToolSupported(caps, it)) }
        .filter { showUnsupported || it.isSupported }
        .sortedByDescending { it.isSupported }
