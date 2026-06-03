package works.mees.dinghy.calibration

import works.mees.dinghy.state.Capabilities

/**
 * The five calibration routines the hub renders (CALIB-01 / D-14). Each carries its gating object
 * name — the live `printer.objects.list` entry that proves the printer supports it. Per the UI-SPEC
 * §1 OWNER OVERRIDE, ALL five RENDER (unsupported greyed-but-listed, not omitted); `isSupported`
 * (via [calibrationSupported]) drives accent-vs-grey + Run-enabled.
 *
 * QUAD_GANTRY_LEVEL is built blind (D-02) and gates on its own `quad_gantry_level` object — absent on
 * both test printers (E5 bed-slinger, E3), so it renders greyed there. Z_TILT is the on-device proxy
 * for the same convergence code path.
 */
enum class CalibrationRoutine(val gatingObject: String) {
    SCREWS_TILT("screws_tilt_adjust"),
    Z_TILT("z_tilt"),
    QUAD_GANTRY_LEVEL("quad_gantry_level"),
    BED_MESH("bed_mesh"),
    PROBE_CALIBRATE("probe"),
}

/**
 * Pure hub gating predicate (CALIB-01). A routine is SUPPORTED iff the printer reports its gating
 * object via [Capabilities.hasObject]. No I/O, no Compose — host-tested by [CalibrationGateTest].
 */
fun calibrationSupported(caps: Capabilities, routine: CalibrationRoutine): Boolean =
    caps.hasObject(routine.gatingObject)

/** One hub row: a routine + its live supported flag. Supported routines sort first (D-14). */
data class RoutineEntry(
    val routine: CalibrationRoutine,
    val isSupported: Boolean,
)

/**
 * Pure hub data source: ALL five routines (UI-SPEC §1 — none omitted), each marked supported via
 * [calibrationSupported], supported-first ordering (stable within each group by enum order).
 */
fun calibrationSupport(caps: Capabilities): List<RoutineEntry> =
    CalibrationRoutine.entries
        .map { RoutineEntry(it, calibrationSupported(caps, it)) }
        .sortedByDescending { it.isSupported }
