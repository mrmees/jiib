package works.mees.jiib.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.state.FilePreviewMetadata
import works.mees.jiib.ui.spool.SpoolWarning
import works.mees.jiib.ui.spool.evaluatePrintStartGate

/**
 * SPOOL-07 — warn-only print-start gate decision table (D-01).
 *
 * The gate NEVER blocks; each condition yields an ordered amber warning the confirm flow can tap past:
 *  - no active spool                  → SpoolWarning.NoActiveSpool
 *  - material family mismatch (D-05)  → SpoolWarning.MaterialMismatch
 *  - remaining < needed + margin      → SpoolWarning.LowFilament
 *  - active spool archived            → SpoolWarning.ArchivedSpool
 *  - pending_reports stale            → SpoolWarning.PendingReports
 *  - spool fetch failed               → SpoolWarning.FetchFailed
 *  - clean pass                       → empty list (never blocks)
 *  - filamentWeightTotal == null      → low-filament check SKIPPED
 */
class PrintStartGateTest {

    private fun file(
        filamentType: List<String> = listOf("PLA"),
        filamentWeightTotal: Double? = 50.0,
    ) = FilePreviewMetadata(
        filename = "miata/airbox.gcode",
        sizeBytes = 1024L,
        modifiedEpochSeconds = 1.0,
        estimatedTime = 3600.0,
        filamentTotal = 20000.0,
        filamentWeightTotal = filamentWeightTotal,
        layerCount = 200,
        objectHeight = 50.0,
        largestThumbRelPath = null,
        filamentType = filamentType,
    )

    private fun spool(
        material: String? = "PLA",
        remainingWeight: Double? = 800.0,
        archived: Boolean = false,
    ) = SpoolmanSpool(
        id = 3,
        filament = SpoolmanFilament(id = 1, material = material),
        remainingWeight = remainingWeight,
        archived = archived,
    )

    private fun status(
        activeSpoolId: Int? = 3,
        pending: List<PendingSpoolmanReport> = emptyList(),
    ) = SpoolmanStatus(
        spoolmanConnected = true,
        activeSpoolId = activeSpoolId,
        pendingReports = pending,
    )

    @Test
    fun warnsWhenNoActiveSpool() {
        val warnings = evaluatePrintStartGate(
            activeSpool = null,
            status = status(activeSpoolId = null),
            fetchFailed = false,
            file = file(),
        )
        assertEquals(listOf<SpoolWarning>(SpoolWarning.NoActiveSpool), warnings)
    }

    @Test
    fun warnsOnMaterialFamilyMismatch() {
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(material = "PETG"),
            status = status(),
            fetchFailed = false,
            file = file(filamentType = listOf("PLA")),
        )
        val mismatch = warnings.filterIsInstance<SpoolWarning.MaterialMismatch>().single()
        assertEquals("PETG", mismatch.spoolMaterial)
        assertEquals(listOf("PLA"), mismatch.fileMaterials)
    }

    @Test
    fun materialFamilyMatchIsPartialAndCaseInsensitive() {
        // file PLA vs spool PLA+ → MATCH (no warning). Multi-family matches if ANY family matches.
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(material = "PLA+"),
            status = status(),
            fetchFailed = false,
            file = file(filamentType = listOf("pla")),
        )
        assertTrue(warnings.none { it is SpoolWarning.MaterialMismatch })

        val multi = evaluatePrintStartGate(
            activeSpool = spool(material = "ASA"),
            status = status(),
            fetchFailed = false,
            file = file(filamentType = listOf("PLA", "ASA")),
        )
        assertTrue(multi.none { it is SpoolWarning.MaterialMismatch })
    }

    @Test
    fun warnsOnLowRemainingFilament() {
        // file needs 50g, margin → ~60g; spool reports 55g remaining → LOW.
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(remainingWeight = 55.0),
            status = status(),
            fetchFailed = false,
            file = file(filamentWeightTotal = 50.0),
        )
        val low = warnings.filterIsInstance<SpoolWarning.LowFilament>().single()
        assertEquals(50.0, low.neededGrams, 0.001)
        assertEquals(55.0, low.remainingGrams, 0.001)
    }

    @Test
    fun warnsOnArchivedSpool() {
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(archived = true),
            status = status(),
            fetchFailed = false,
            file = file(),
        )
        assertTrue(warnings.contains(SpoolWarning.ArchivedSpool))
    }

    @Test
    fun warnsOnStalePendingReports() {
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(),
            status = status(pending = listOf(PendingSpoolmanReport(spoolId = 3, filamentUsedMm = 5.0))),
            fetchFailed = false,
            file = file(),
        )
        assertTrue(warnings.contains(SpoolWarning.PendingReports))
    }

    @Test
    fun warnsWhenSpoolFetchFailed() {
        val warnings = evaluatePrintStartGate(
            activeSpool = null,
            status = status(activeSpoolId = 3),
            fetchFailed = true,
            file = file(),
        )
        assertEquals(listOf<SpoolWarning>(SpoolWarning.FetchFailed), warnings)
    }

    @Test
    fun passesCleanWithNoWarningsAndNeverBlocks() {
        // active PLA spool with plenty remaining, matching family, no pending, not archived → clean.
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(material = "PLA", remainingWeight = 800.0, archived = false),
            status = status(),
            fetchFailed = false,
            file = file(filamentType = listOf("PLA"), filamentWeightTotal = 50.0),
        )
        assertTrue("clean pass must produce zero warnings", warnings.isEmpty())
    }

    @Test
    fun skipsLowFilamentCheckWhenWeightTotalNull() {
        // Even with a nearly-empty spool, a null filament_weight_total skips the low-filament check.
        val warnings = evaluatePrintStartGate(
            activeSpool = spool(remainingWeight = 1.0),
            status = status(),
            fetchFailed = false,
            file = file(filamentType = listOf("PLA"), filamentWeightTotal = null),
        )
        assertTrue(warnings.none { it is SpoolWarning.LowFilament })
    }
}
