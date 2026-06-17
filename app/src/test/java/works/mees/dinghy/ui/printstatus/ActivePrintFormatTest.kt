package works.mees.dinghy.ui.printstatus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.state.HeaterState

class ActivePrintFormatTest {
    @Test fun percent_rounds_to_int() {
        assertEquals(0, progressPercent(0.0))
        assertEquals(42, progressPercent(0.4249))
        assertEquals(43, progressPercent(0.425))
        assertEquals(100, progressPercent(1.0))
        assertEquals(100, progressPercent(1.5))   // clamps
        assertEquals(0, progressPercent(-0.2))    // clamps
    }

    @Test fun layer_height_full() {
        assertEquals("1.2/55mm · 5/220 layers", formatLayerHeight(1.2, 55.0, 5, 220))
    }

    @Test fun layer_height_no_total_height() {
        assertEquals("1.2mm · 5/220 layers", formatLayerHeight(1.2, null, 5, 220))
    }

    @Test fun layer_height_no_layers_drops_clause() {
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, null, 220))
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, 5, null))
    }

    @Test fun layer_height_non_positive_layers_drops_clause() {
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, 0, 220))
        assertEquals("1.2/55mm", formatLayerHeight(1.2, 55.0, 5, 0))
    }

    @Test fun layer_height_no_z_falls_back_to_dash() {
        assertEquals("—", formatLayerHeight(null, 55.0, null, null))
        assertEquals("— · 5/220 layers", formatLayerHeight(null, 55.0, 5, 220))
    }

    @Test fun derive_current_layer_from_height() {
        // first=0.24, layer=0.2: z=0.24 -> layer 1; z=0.44 -> layer 2; z=23.8 -> 118 (clamped to 152)
        assertEquals(1, deriveCurrentLayer(0.24, 0.24, 0.2, 152))
        assertEquals(2, deriveCurrentLayer(0.44, 0.24, 0.2, 152))
        assertEquals(118, deriveCurrentLayer(23.8, 0.24, 0.2, 152))
    }

    @Test fun derive_current_layer_clamps_and_falls_back() {
        assertEquals(1, deriveCurrentLayer(0.0, 0.24, 0.2, 152))    // below first layer -> clamp to 1
        assertEquals(152, deriveCurrentLayer(99.0, 0.24, 0.2, 152)) // past the top -> clamp to total
        // no first-layer height (falls back to layerHeight 0.2) and no total: (1.0-0.2)/0.2=4, +1=5
        assertEquals(5, deriveCurrentLayer(1.0, null, 0.2, null))
        assertEquals(5, deriveCurrentLayer(1.0, null, 0.2, 0)) // non-positive total is invalid, not a clamp
    }

    @Test fun derive_current_layer_null_when_inputs_missing() {
        assertNull(deriveCurrentLayer(null, 0.24, 0.2, 152))   // no Z
        assertNull(deriveCurrentLayer(10.0, 0.24, null, 152))  // no layer height
        assertNull(deriveCurrentLayer(10.0, 0.24, 0.0, 152))   // zero layer height
    }

    @Test fun basename_strips_path_keeps_extension() {
        assertEquals("benchy.gcode", printFileBasename("prints/calib/benchy.gcode"))
        assertEquals("benchy.gcode", printFileBasename("benchy.gcode"))
        assertEquals("", printFileBasename(""))
    }

    @Test fun print_duration_brackets() {
        assertEquals("1h01m", formatPrintDuration(3661.0))
        assertEquals("1h00m", formatPrintDuration(3600.0))
        assertEquals("45m", formatPrintDuration(2700.0))
        assertEquals("1m", formatPrintDuration(60.0))
        assertEquals("59s", formatPrintDuration(59.0))
        assertEquals("30s", formatPrintDuration(30.0))
        assertEquals("0s", formatPrintDuration(0.0))
        assertEquals("0s", formatPrintDuration(-5.0))
    }

    @Test fun print_vs_estimate() {
        assertEquals("45m / 3h20m", formatPrintVsEstimate(2700.0, 12000.0))
        assertEquals("45m", formatPrintVsEstimate(2700.0, null))
        assertEquals("45m", formatPrintVsEstimate(2700.0, 0.0))
    }

    @Test fun heaters_line_all_current_only() {
        val h = mapOf(
            "extruder" to HeaterState(temperature = 229.6, target = 230.0),
            "heater_bed" to HeaterState(temperature = 75.2, target = 75.0),
        )
        assertEquals("Extruder 230 · Bed 75", formatHeatersLine(h))
    }

    @Test fun heaters_line_cold_still_shown() {
        val h = mapOf("extruder" to HeaterState(temperature = 24.0, target = 0.0))
        assertEquals("Extruder 24", formatHeatersLine(h))
    }

    @Test fun heaters_line_empty_is_blank() {
        assertEquals("", formatHeatersLine(emptyMap()))
    }

    @Test fun heaters_line_chamber_sorts_after_bed() {
        val h = mapOf(
            "heater_bed" to HeaterState(temperature = 60.0, target = 60.0),
            "heater_generic chamber" to HeaterState(temperature = 40.0, target = 45.0),
            "extruder" to HeaterState(temperature = 200.0, target = 200.0),
        )
        assertEquals("Extruder 200 · Bed 60 · Chamber 40", formatHeatersLine(h))
    }

    @Test fun z_height_line() {
        assertEquals("1.2 / 55 mm", formatZHeight(1.2, 55.0))
        assertEquals("1.2 mm", formatZHeight(1.2, null))
        assertEquals("—", formatZHeight(null, 55.0))
    }

    @Test fun layers_line() {
        assertEquals("5 / 220 layers", formatLayersLine(5, 220))
        assertNull(formatLayersLine(null, 220))
        assertNull(formatLayersLine(5, null))
        assertNull(formatLayersLine(0, 220))
    }
}
