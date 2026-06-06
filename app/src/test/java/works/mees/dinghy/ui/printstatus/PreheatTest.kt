package works.mees.dinghy.ui.printstatus

import org.junit.Assert.fail
import org.junit.Test

/**
 * Wave-0 RED scaffold (16-01) — turned GREEN by 16-02 (`selectPreheatPath` / `PreheatPath`).
 *
 * Encodes the PURE spool-aware Preheat-selection CONTRACT that 16-02 will build:
 *
 *   selectPreheatPath(spoolmanPresent: Boolean, nozzleTemp: Int?, bedTemp: Int?): PreheatPath
 *
 *   - spool present + BOTH temps present -> DirectTemps(nozzle, bed)
 *   - spool present + ONLY one temp      -> DirectTemps carrying ONLY the present temp; the missing one
 *                                           is `null` (NEVER coerced to 0 — a 0 would silently command a
 *                                           cooldown of the un-set heater via applyPreset-with-0).
 *   - spool ABSENT, OR both temps null   -> OpenSelector (fall back to the Phase-5 PresetSelector flow)
 *
 * RED discipline ([[dinghy-wave0-red-scaffold-compile]]): these bodies do NOT reference the not-yet-built
 * `selectPreheatPath` / `PreheatPath` / `DirectTemps` / `OpenSelector` (they land in 16-02). The expected
 * values are spelled out in comments + held as locals so the contract is unambiguous; each case
 * `fail(...)`s until 16-02 turns it GREEN. The file compiles day-one (no unbuilt symbol referenced).
 */
class PreheatTest {

    @Test
    fun spoolPresent_bothTemps_directTempsBoth() {
        val nozzle = 215
        val bed = 60
        // EXPECT (16-02): selectPreheatPath(true, nozzle, bed) == DirectTemps(nozzle = 215, bed = 60)
        require(nozzle == 215 && bed == 60)
        fail("not yet implemented — Wave 2 16-02 selectPreheatPath (DirectTemps both)")
    }

    @Test
    fun spoolPresent_onlyNozzle_directTempsCarriesOnlyNozzle_bedNullNotZero() {
        val nozzle = 215
        val bed: Int? = null
        // EXPECT (16-02): selectPreheatPath(true, nozzle, null) == DirectTemps(nozzle = 215, bed = null)
        // The absent bed MUST be null, NEVER 0 (a 0 target = unintended cooldown of the bed).
        require(nozzle == 215 && bed == null)
        fail("not yet implemented — Wave 2 16-02 selectPreheatPath (per-temp guard, bed null)")
    }

    @Test
    fun spoolPresent_onlyBed_directTempsCarriesOnlyBed_nozzleNullNotZero() {
        val nozzle: Int? = null
        val bed = 60
        // EXPECT (16-02): selectPreheatPath(true, null, bed) == DirectTemps(nozzle = null, bed = 60)
        require(nozzle == null && bed == 60)
        fail("not yet implemented — Wave 2 16-02 selectPreheatPath (per-temp guard, nozzle null)")
    }

    @Test
    fun spoolAbsent_openSelector() {
        // EXPECT (16-02): selectPreheatPath(false, 215, 60) == OpenSelector
        fail("not yet implemented — Wave 2 16-02 selectPreheatPath (spool absent -> OpenSelector)")
    }

    @Test
    fun spoolPresent_bothTempsNull_openSelector() {
        // EXPECT (16-02): selectPreheatPath(true, null, null) == OpenSelector
        fail("not yet implemented — Wave 2 16-02 selectPreheatPath (no temps -> OpenSelector)")
    }
}
