package works.mees.dinghy.calibration

import works.mees.dinghy.state.Capabilities

/**
 * Pure probe-present gate (CALIB-05 / A3). A printer with a `probe` object calibrates Z via
 * `PROBE_CALIBRATE`; a probe-less printer falls back to `Z_ENDSTOP_CALIBRATE`. Pure predicate over
 * [Capabilities.hasObject] (re-derived every reconnect from `printer.objects.list`). No I/O, no
 * Compose — host-tested by [ProbePresentGateTest].
 *
 * Returns the gcode COMMAND NAME the Z-calibrate page should dispatch.
 */
fun probeCalibrateGate(caps: Capabilities): String =
    if (caps.hasObject("probe")) "PROBE_CALIBRATE" else "Z_ENDSTOP_CALIBRATE"
