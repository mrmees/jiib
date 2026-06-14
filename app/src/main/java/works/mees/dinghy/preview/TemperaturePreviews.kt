package works.mees.dinghy.preview

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.Composable
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.ui.temperature.DEFAULT_GRAPH_RANGE
import works.mees.dinghy.ui.temperature.SensorReadout
import works.mees.dinghy.ui.temperature.TemperatureScreen

// ─────────────────────────────────────────────────────────────────────────────
// Fixture data — pure values, no Moonraker
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Four-sensor legend: two adjustable heaters (extruder + bed) and two read-only sensors
 * (chamber + MCU). Exercises the [SensorReadout.isAdjustable] path and the sensor-toggle rows.
 */
private val fakeLegend: List<SensorReadout> = listOf(
    SensorReadout(
        name = "extruder",
        label = "NOZZLE",
        current = 210.4,
        target = 200.0,
        isAdjustable = true,
    ),
    SensorReadout(
        name = "heater_bed",
        label = "BED",
        current = 60.1,
        target = 60.0,
        isAdjustable = true,
    ),
    SensorReadout(
        name = "temperature_sensor chamber",
        label = "CHAMBER",
        current = 34.8,
        target = null,
        isAdjustable = false,
    ),
    SensorReadout(
        name = "temperature_sensor mcu",
        label = "MCU",
        current = 47.0,
        target = null,
        isAdjustable = false,
    ),
)

/** Fake ring-buffer snapshots per sensor (index-aligned with [fakeLegend]). */
private val fakeNozzleSeries: FloatArray = SampleFixtures.tempSeries
private val fakeBedSeries: FloatArray = FloatArray(60) { i ->
    when {
        i < 30 -> 22f + (60f - 22f) * (i / 29f)
        else -> 60f + (((i % 2) - 0.5f) * 0.4f)
    }
}
private val fakeChamberSeries: FloatArray = FloatArray(60) { 35f + (((it % 4) - 1.5f) * 0.5f) }
private val fakeMcuSeries: FloatArray = FloatArray(60) { 46f + (((it % 3) - 1f) * 0.6f) }

private val fakeSeries: List<FloatArray> = listOf(fakeNozzleSeries, fakeBedSeries, fakeChamberSeries, fakeMcuSeries)
private val fakeSetpoints: List<Float?> = listOf(200f, 60f, null, null)

/** Read-only sensors exposed in the sensor-toggle panel. */
private val fakeAvailableSensors: List<String> = listOf(
    "temperature_sensor chamber",
    "temperature_sensor mcu",
)

/** Both read-only sensors selected (visible in the monitoring legend). */
private val fakeSelectedSensors: Set<String> = setOf(
    "temperature_sensor chamber",
    "temperature_sensor mcu",
)
private val fakeGraphRange: ClosedFloatingPointRange<Float> = 0f..250f

/** D-14 chosen-color fixture: nozzle=orange, bed=cyan; chamber+MCU use default (absent). */
private val fakeTraceColors: Map<String, Color> = mapOf(
    "extruder" to Color(0xFFFF8C00.toInt()),         // amber-orange
    "heater_bed" to Color(0xFF00BCD4.toInt()),        // cyan
)

/** Hidden-trace fixture: MCU trace hidden; chamber still visible. */
private val fakeTraceVisibilityWithHidden: Map<String, Boolean> = mapOf(
    "temperature_sensor mcu" to false,
)

/** Default — all traces visible. */
private val fakeTraceVisibilityAll: Map<String, Boolean> = emptyMap()

/** Fake 8-swatch Colorful pool for the color-picker row (previews can't call Palette.generate). */
private val fakeColorfulSwatches: List<Color> = listOf(
    Color(0xFFFF5252.toInt()),
    Color(0xFFFF8C00.toInt()),
    Color(0xFFFFEB3B.toInt()),
    Color(0xFF4CAF50.toInt()),
    Color(0xFF00BCD4.toInt()),
    Color(0xFF2196F3.toInt()),
    Color(0xFF9C27B0.toInt()),
    Color(0xFFFFFFFF.toInt()),
)

// ─────────────────────────────────────────────────────────────────────────────
// Demo composables
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Graph-default state (no sensor selected) — the typical resting view. Four active traces (two
 * heaters + two read-only sensors), chosen colors applied to nozzle (orange) and bed (cyan), MCU
 * trace hidden. [availableSensors]/[selectedSensors] supplied to exercise the sensor-toggle rows.
 */
@Composable
private fun TemperatureGraphDefault() {
    TemperatureScreen(
        series = fakeSeries,
        setpoints = fakeSetpoints,
        legend = fakeLegend,
        graphRange = fakeGraphRange,
        traceColors = fakeTraceColors,
        traceVisibility = fakeTraceVisibilityWithHidden,
        isPrinting = false,
        failureText = null,
        seedHex = ThemePrefs.DEFAULT_SEED,
        dark = true,
        availableSensors = fakeAvailableSensors,
        selectedSensors = fakeSelectedSensors,
    )
}

/**
 * Sensor-selected state — stateless overload with [isPrinting] = true so the FloatingEStop renders.
 * All four traces visible; both read-only sensors in [selectedSensors].
 *
 * Note: the adjuster morph is internal [TemperatureContent] state so the stateless overload always
 * starts with no sensor selected. To render the morph state in a preview the component must be
 * extended to accept an initial-selection param; for now this preview drives the graph-default path
 * with selected trace colors and a note to on-device-verify the morph interaction.
 */
@Composable
private fun TemperatureAdjusterSelected() {
    TemperatureScreen(
        series = fakeSeries,
        setpoints = fakeSetpoints,
        legend = fakeLegend,
        graphRange = fakeGraphRange,
        traceColors = fakeTraceColors,
        traceVisibility = fakeTraceVisibilityAll,
        isPrinting = true,          // FloatingEStop visible
        failureText = null,
        seedHex = ThemePrefs.DEFAULT_SEED,
        dark = true,
        availableSensors = fakeAvailableSensors,
        selectedSensors = fakeSelectedSensors,
    )
}

/**
 * Printing + failure toast — all four traces visible, default colors, E-stop button shown,
 * a failure message toast rendered. Read-only sensors still present in the legend.
 */
@Composable
private fun TemperaturePrintingWithError() {
    TemperatureScreen(
        series = fakeSeries,
        setpoints = fakeSetpoints,
        legend = fakeLegend,
        graphRange = fakeGraphRange,
        traceColors = emptyMap(),
        traceVisibility = fakeTraceVisibilityAll,
        isPrinting = true,
        failureText = "Heater timeout — check wiring",
        seedHex = ThemePrefs.DEFAULT_SEED,
        dark = true,
        availableSensors = fakeAvailableSensors,
        selectedSensors = fakeSelectedSensors,
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme combo matrix (docs/ui_design/PREVIEW_AND_TOKENS.md)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun TemperatureColorfulDark() =
    PreviewBox(colorfulDark) { TemperatureGraphDefault() }

@Nexus7Previews
@Composable
private fun TemperatureColorfulLight() =
    PreviewBox(colorfulLight) { TemperatureGraphDefault() }

@Nexus7Previews
@Composable
private fun TemperatureSimpleDark() =
    PreviewBox(simpleDark) { TemperatureGraphDefault() }

@Nexus7Previews
@Composable
private fun TemperatureSimpleLight() =
    PreviewBox(simpleLight) { TemperatureGraphDefault() }

@Nexus7Previews
@Composable
private fun TemperatureHighContrastDark() =
    PreviewBox(highContrastDark) { TemperatureGraphDefault() }

@Nexus7Previews
@Composable
private fun TemperatureHighContrastLight() =
    PreviewBox(highContrastLight) { TemperatureGraphDefault() }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun TemperatureFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { TemperatureGraphDefault() }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape-specific check (800×480dp — 5U phone landscape Focus budget)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Temperature landscape 5U",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun TemperatureLandscape() =
    PreviewBox(colorfulDark) { TemperatureGraphDefault() }

// ─────────────────────────────────────────────────────────────────────────────
// Sensor-selected state (adjuster morph + FloatingEStop visible, isPrinting)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun TemperatureAdjusterMorphState() =
    PreviewBox(colorfulDark) { TemperatureAdjusterSelected() }

// ─────────────────────────────────────────────────────────────────────────────
// Printing with failure toast
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun TemperaturePrintingError() =
    PreviewBox(colorfulDark) { TemperaturePrintingWithError() }
