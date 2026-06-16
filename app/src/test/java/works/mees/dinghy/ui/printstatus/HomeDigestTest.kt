package works.mees.dinghy.ui.printstatus

import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.state.HeaterState
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

class HomeDigestTest {

    @Test
    fun prettyHeaterLabelStripsPrefixAndCapitalizes() {
        assertEquals("Extruder", prettyHeaterLabel("extruder"))
        assertEquals("Bed", prettyHeaterLabel("heater_bed"))
        assertEquals("Chamber", prettyHeaterLabel("heater_generic chamber"))
        assertEquals("Extruder1", prettyHeaterLabel("extruder1"))
    }

    @Test
    fun orderedHeaterKeysPutsExtruderThenBedThenSorted() {
        val heaters = persistentMapOf(
            "heater_generic chamber" to HeaterState(),
            "heater_bed" to HeaterState(),
            "extruder1" to HeaterState(),
            "extruder" to HeaterState(),
        )
        assertEquals(
            listOf("extruder", "heater_bed", "extruder1", "heater_generic chamber"),
            orderedHeaterKeys(heaters),
        )
    }

    @Test
    fun orderedHeaterKeysOmitsAbsentHeadHeaters() {
        val heaters = persistentMapOf("heater_generic chamber" to HeaterState())
        assertEquals(listOf("heater_generic chamber"), orderedHeaterKeys(heaters))
    }

    @Test
    fun heaterValueTextRoundsBothToIntegers() {
        assertEquals("151/220", heaterValueText(HeaterState(temperature = 150.6, target = 220.0)))
        assertEquals("25/0", heaterValueText(HeaterState(temperature = 25.2, target = 0.0)))
    }

    @Test
    fun homedTextCanonicalizesXyzOrderOrNone() {
        assertEquals("XYZ", homedText("xyz"))
        assertEquals("XYZ", homedText("yzx"))   // R-CDX-4 NIT: force X/Y/Z order regardless of input order
        assertEquals("XY", homedText("yx"))
        assertEquals("NONE", homedText(""))
    }

    @Test
    fun spoolDigestValueHiddenWhenSpoolmanAbsent() {
        assertNull(spoolDigestValue(spoolmanPresent = false, state = ActiveSpoolCardState.NoActive))
    }

    @Test
    fun spoolDigestValueWeightWhenLoaded() {
        val spool = SpoolmanSpool(id = 1, remainingWeight = 848.4)
        assertEquals("848g", spoolDigestValue(spoolmanPresent = true, state = ActiveSpoolCardState.Loaded(spool)))
    }

    @Test
    fun spoolDigestValueNaForEveryNonLoadedVariant() {
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.NoActive))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Disconnected))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Loading(5)))
        assertEquals("N/A", spoolDigestValue(true, ActiveSpoolCardState.Loaded(SpoolmanSpool(id = 1, remainingWeight = null))))
    }
}
