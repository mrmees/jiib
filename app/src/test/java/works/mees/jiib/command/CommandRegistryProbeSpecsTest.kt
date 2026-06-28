package works.mees.jiib.command

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Task-5 guard: the 8 new probe/eddy CommandSpecs must be registered in [CommandRegistry.all]
 * with their exact catalogIds. Note: [CommandCatalogDriftTest] will be RED until Task 7
 * (printer-matrix.json rows missing) — that is expected and not run here.
 */
class CommandRegistryProbeSpecsTest {

    @Test
    fun probeSpecsRegistered() {
        val ids = CommandRegistry.all.map { it.catalogId }
        assertTrue(
            "Missing probe/eddy specs in CommandRegistry.all — got: $ids",
            ids.containsAll(
                listOf(
                    "KGC-QUERY_PROBE",
                    "KGC-PROBE",
                    "KGC-PROBE_ACCURACY",
                    "KGC-Z_OFFSET_APPLY_PROBE",
                    "KGC-Z_OFFSET_APPLY_ENDSTOP",
                    "KGC-PROBE_EDDY_CURRENT_CALIBRATE",
                    "KGC-PROBE_EDDY_CURRENT_TAP_CALIBRATE",
                    "KGC-LDC_CALIBRATE_DRIVE_CURRENT",
                )
            )
        )
    }
}
