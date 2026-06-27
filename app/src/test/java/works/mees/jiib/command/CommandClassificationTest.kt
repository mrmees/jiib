package works.mees.jiib.command

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandClassificationTest {
    @Test fun motion_softBusy_fenced() {
        listOf(CommandRegistry.jog, CommandRegistry.overrideJog, CommandRegistry.extrude).forEach {
            assertEquals("${it.catalogId} SoftBusy", GatingMode.SoftBusy, it.gating)
            assertTrue("${it.catalogId} fenced", it.fence)
        }
        assertEquals(GatingMode.SoftBusy, CommandRegistry.moveTo.gating)
        assertTrue(CommandRegistry.moveTo.fence)
        assertTrue(CommandRegistry.forceMove.fence)
    }

    @Test fun homing_and_autoCalibration_hardLock_fenced() {
        listOf(
            CommandRegistry.homeAll, CommandRegistry.homeXY, CommandRegistry.homeAxis,
            CommandRegistry.bedMeshCalibrate, CommandRegistry.screwsTiltCalculate,
            CommandRegistry.zTiltAdjust, CommandRegistry.quadGantryLevel,
        ).forEach {
            assertEquals("${it.catalogId} HardLock", GatingMode.HardLock, it.gating)
            assertTrue("${it.catalogId} fenced", it.fence)
        }
    }

    @Test fun longRunners_haveExtendedTimeout() {
        listOf(
            CommandRegistry.bedMeshCalibrate, CommandRegistry.quadGantryLevel,
            CommandRegistry.zTiltAdjust, CommandRegistry.screwsTiltCalculate,
        ).forEach { assertEquals("${it.catalogId} 600s", 600_000L, it.gatingTimeoutMs) }
    }

    @Test fun duringPrint_and_interactive_areNeverFenced() {
        // babystep + flow/speed/PA/retraction run during a print; manual-probe is interactive.
        listOf(
            CommandRegistry.babystepZ, CommandRegistry.speedFactor, CommandRegistry.flowFactor,
            CommandRegistry.setPressureAdvance, CommandRegistry.testZ, CommandRegistry.accept,
            CommandRegistry.abort, CommandRegistry.probeCalibrate, CommandRegistry.zEndstopCalibrate,
        ).forEach { assertFalse("${it.catalogId} must not fence", it.fence) }
    }
}
