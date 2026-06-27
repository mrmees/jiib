package works.mees.jiib.command

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for probe/eddy gcode builders and constants in [PrinterCommands].
 * All host-side — no I/O, no device.
 */
class PrinterCommandsProbeTest {

    // --- plain constants -------------------------------------------------------------------------

    @Test fun queryProbe_constant() =
        assertEquals("QUERY_PROBE", PrinterCommands.QUERY_PROBE)

    @Test fun probe_constant() =
        assertEquals("PROBE", PrinterCommands.PROBE)

    @Test fun zOffsetApplyProbe_constant() =
        assertEquals("Z_OFFSET_APPLY_PROBE", PrinterCommands.Z_OFFSET_APPLY_PROBE)

    @Test fun zOffsetApplyEndstop_constant() =
        assertEquals("Z_OFFSET_APPLY_ENDSTOP", PrinterCommands.Z_OFFSET_APPLY_ENDSTOP)

    // --- probeAccuracy ---------------------------------------------------------------------------

    @Test fun probeAccuracy_buildsSamples() =
        assertEquals("PROBE_ACCURACY SAMPLES=10", PrinterCommands.probeAccuracy(10))

    @Test fun probeAccuracy_coercesZeroToOne() =
        assertEquals("PROBE_ACCURACY SAMPLES=1", PrinterCommands.probeAccuracy(0))

    @Test fun probeAccuracy_coercesNegativeToOne() =
        assertEquals("PROBE_ACCURACY SAMPLES=1", PrinterCommands.probeAccuracy(-5))

    @Test fun probeAccuracy_passesPositiveUnchanged() =
        assertEquals("PROBE_ACCURACY SAMPLES=50", PrinterCommands.probeAccuracy(50))

    // --- eddyCalibrate ---------------------------------------------------------------------------

    @Test fun eddyCalibrate_buildsChip() =
        assertEquals("PROBE_EDDY_CURRENT_CALIBRATE CHIP=my_eddy", PrinterCommands.eddyCalibrate("my_eddy"))

    @Test fun eddyCalibrate_blankChip_omitsArg() =
        assertEquals("PROBE_EDDY_CURRENT_CALIBRATE", PrinterCommands.eddyCalibrate(""))

    @Test fun eddyCalibrate_whitespaceChip_omitsArg() =
        assertEquals("PROBE_EDDY_CURRENT_CALIBRATE", PrinterCommands.eddyCalibrate("   "))

    // --- eddyTapCalibrate ------------------------------------------------------------------------

    @Test fun eddyTap_buildsStage() =
        assertEquals("PROBE_EDDY_CURRENT_TAP_CALIBRATE TAP=guess", PrinterCommands.eddyTapCalibrate("guess"))

    @Test fun eddyTap_buildsStageAutomatic() =
        assertEquals("PROBE_EDDY_CURRENT_TAP_CALIBRATE TAP=automatic", PrinterCommands.eddyTapCalibrate("automatic"))

    // --- ldcDriveCurrent -------------------------------------------------------------------------

    @Test fun ldc_buildsChip() =
        assertEquals("LDC_CALIBRATE_DRIVE_CURRENT CHIP=my_ldc", PrinterCommands.ldcDriveCurrent("my_ldc"))

    @Test fun ldc_blankChip_omitsArg() =
        assertEquals("LDC_CALIBRATE_DRIVE_CURRENT", PrinterCommands.ldcDriveCurrent("  "))

    @Test fun ldc_emptyChip_omitsArg() =
        assertEquals("LDC_CALIBRATE_DRIVE_CURRENT", PrinterCommands.ldcDriveCurrent(""))
}
