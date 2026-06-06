package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Wave-2 (16-02) — the GREEN `selectPreheatPath` / `PreheatPath` contract turned over from the
 * 16-01 RED scaffold.
 *
 *   selectPreheatPath(spoolmanPresent: Boolean, nozzleTemp: Int?, bedTemp: Int?): PreheatPath
 *
 *   - spool present + BOTH temps present -> DirectTemps(nozzle, bed)
 *   - spool present + ONLY one temp      -> DirectTemps carrying ONLY the present temp; the missing one
 *                                           is `null` (NEVER coerced to 0 — a 0 would silently command a
 *                                           cooldown of the un-set heater via applyPreset-with-0).
 *   - spool ABSENT, OR both temps null   -> OpenSelector (fall back to the Phase-5 PresetSelector flow)
 */
class PreheatTest {

    @Test
    fun spoolPresent_bothTemps_directTempsBoth() {
        assertEquals(
            PreheatPath.DirectTemps(nozzle = 215, bed = 60),
            selectPreheatPath(spoolmanPresent = true, nozzleTemp = 215, bedTemp = 60),
        )
    }

    @Test
    fun spoolPresent_onlyNozzle_directTempsCarriesOnlyNozzle_bedNullNotZero() {
        // The absent bed MUST be null, NEVER 0 (a 0 target = unintended cooldown of the bed).
        assertEquals(
            PreheatPath.DirectTemps(nozzle = 215, bed = null),
            selectPreheatPath(spoolmanPresent = true, nozzleTemp = 215, bedTemp = null),
        )
    }

    @Test
    fun spoolPresent_onlyBed_directTempsCarriesOnlyBed_nozzleNullNotZero() {
        assertEquals(
            PreheatPath.DirectTemps(nozzle = null, bed = 60),
            selectPreheatPath(spoolmanPresent = true, nozzleTemp = null, bedTemp = 60),
        )
    }

    @Test
    fun spoolAbsent_openSelector() {
        assertEquals(
            PreheatPath.OpenSelector,
            selectPreheatPath(spoolmanPresent = false, nozzleTemp = 215, bedTemp = 60),
        )
    }

    @Test
    fun spoolPresent_bothTempsNull_openSelector() {
        assertEquals(
            PreheatPath.OpenSelector,
            selectPreheatPath(spoolmanPresent = true, nozzleTemp = null, bedTemp = null),
        )
    }
}
