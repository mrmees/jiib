package works.mees.dinghy.ui.heatpresets

import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.state.SettableHeater
import org.junit.Assert.assertEquals
import org.junit.Test

class HeatPresetWizardLogicTest {
    private val heaters = listOf(
        SettableHeater("extruder", "Nozzle", 10, 300),                  // min 10 — guards the 0=off escape
        SettableHeater("heater_bed", "Bed", 0, 130),
        SettableHeater("heater_generic chamber", "Chamber", 0, 120),    // NEW since the saved preset
    )

    @Test fun reconcile_prefillsSaved_newHeaterBlank() {
        val saved = HeatPreset("id", "Med", mapOf("extruder" to 200, "heater_bed" to 60))
        val fields = reconcileWizardFields(saved, heaters)
        assertEquals(listOf("extruder", "heater_bed", "heater_generic chamber"), fields.map { it.objectName })
        assertEquals(listOf("200", "60", ""), fields.map { it.initialValue }) // new chamber blank
    }

    @Test fun reconcile_create_allBlank() {
        val fields = reconcileWizardFields(saved = null, heaters = heaters)
        assertEquals(listOf("", "", ""), fields.map { it.initialValue })
    }

    @Test fun build_blankOmitted_zeroIsOffEvenBelowMin_positivesClamped() {
        val raw = mapOf(
            "extruder" to "0",                 // 0 = OFF — must stay 0 despite extruder minTemp=10
            "heater_bed" to "",                // blank = omit
            "heater_generic chamber" to "9999", // positive → clamp to chamber max 120
        )
        val preset = buildPresetFromInput(id = "id", name = "  My Preset ", rawValues = raw, heaters = heaters)
        assertEquals("My Preset", preset.name)                 // trimmed
        assertEquals(mapOf("extruder" to 0, "heater_generic chamber" to 120), preset.setpoints)
    }

    @Test fun build_positiveBelowMin_clampsUpToMin() {
        val preset = buildPresetFromInput("id", "P", mapOf("extruder" to "5"), heaters) // min 10
        assertEquals(mapOf("extruder" to 10), preset.setpoints)
    }

    @Test fun build_preservesSavedSetpointsForHeatersNotCurrentlyPresent() {
        // saved has a temperature_fan the CURRENT heaters list lacks (printer temporarily absent) →
        // its setpoint must be PRESERVED, not dropped, alongside the edited shown heaters.
        val saved = HeatPreset(
            "id",
            "Med",
            mapOf("extruder" to 200, "temperature_fan exhaust" to 40),
        )
        val raw = mapOf("extruder" to "210", "heater_bed" to "60")
        val preset = buildPresetFromInput("id", "Med", raw, heaters, saved = saved)
        assertEquals(
            mapOf("extruder" to 210, "heater_bed" to 60, "temperature_fan exhaust" to 40),
            preset.setpoints,
        )
    }

    @Test fun build_hugeOverflowingInput_clampsToMaxNotOmitted() {
        // A digit string that overflows Int must clamp to the heater max, not silently omit (toLong).
        val preset = buildPresetFromInput("id", "P", mapOf("heater_generic chamber" to "999999999999"), heaters)
        assertEquals(mapOf("heater_generic chamber" to 120), preset.setpoints) // chamber max 120
    }
}
