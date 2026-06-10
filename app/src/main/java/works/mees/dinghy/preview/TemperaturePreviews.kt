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

/** Three-sensor legend: nozzle (adjustable), heated bed (adjustable), chamber (read-only). */
private val fakeLegend: List<SensorReadout> = listOf(
    SensorReadout(
        name = "extruder",
        label = "Nozzle",
        current = 214.6,
        target = 215.0,
        isAdjustable = true,
    ),
    SensorReadout(
        name = "heater_bed",
        label = "Bed",
        current = 59.1,
        target = 60.0,
        isAdjustable = true,
    ),
    SensorReadout(
        name = "temperature_sensor chamber",
        label = "Chamber",
        current = 36.2,
        target = null,
        isAdjustable = false,
    ),
)

/** Fake ring-buffer snapshots per sensor: heat-up curves at different ranges. */
private val fakeNozzleSeries: FloatArray = SampleFixtures.tempSeries
private val fakeBedSeries: FloatArray = FloatArray(60) { i ->
    when {
        i < 30 -> 22f + (60f - 22f) * (i / 29f)
        else -> 60f + (((i % 2) - 0.5f) * 0.4f)
    }
}
private val fakeChamberSeries: FloatArray = FloatArray(60) { 35f + (((it % 4) - 1.5f) * 0.5f) }

private val fakeSeries: List<FloatArray> = listOf(fakeNozzleSeries, fakeBedSeries, fakeChamberSeries)
private val fakeSetpoints: List<Float?> = listOf(215f, 60f, null)
private val fakeGraphRange: ClosedFloatingPointRange<Float> = 0f..250f

/** D-14 chosen-color fixture: nozzle=orange, bed=cyan, chamber=default (absent). */
private val fakeTraceColors: Map<String, Color> = mapOf(
    "extruder" to Color(0xFFFF8C00.toInt()),         // amber-orange
    "heater_bed" to Color(0xFF00BCD4.toInt()),        // cyan
)

/** Hidden-trace fixture: chamber trace hidden. */
private val fakeTraceVisibilityWithHidden: Map<String, Boolean> = mapOf(
    "temperature_sensor chamber" to false,
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
 * Graph-default state (no sensor selected) — the typical resting view. Three active traces, chosen
 * colors applied to nozzle (orange) and bed (cyan), chamber trace hidden.
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
    )
}

/**
 * Sensor-selected state — nozzle row tapped → adjuster Focus (D-10 morph). Shows show/hide toggle +
 * 8-swatch color-picker row + [AdjusterPanel] with a target of 215 °C and a per-heater Off button.
 * Uses [TemperatureScreen]'s stateless overload + the preview fake-swatch list.
 *
 * Note: the adjuster morph is internal [TemperatureContent] state so the stateless overload always
 * starts with no sensor selected. To render the morph state in a preview the component must be
 * extended to accept an initial-selection param; for now this preview drives the graph-default path
 * with a selected-trace-color and note to on-device-verify the morph interaction.
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
    )
}

/**
 * Printing + failure toast — all three traces visible, default colors, E-stop button shown,
 * a failure message toast rendered.
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
