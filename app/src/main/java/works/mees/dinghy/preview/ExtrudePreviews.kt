package works.mees.dinghy.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.dinghy.spool.SpoolmanFilament
import works.mees.dinghy.spool.SpoolmanSpool
import works.mees.dinghy.ui.extrude.ExtrudeScreen
import works.mees.dinghy.ui.extrude.ExtrudeVm

/**
 * @Preview matrix for the Extrude screen (26-06 / D-23 preview-first convention).
 *
 * ExtrudeScreen uses a SPECIALIZED command-centric layout (D-15 exemption — like Move's jog grid,
 * the function requires its shape). The conformance changes are:
 *  - FootButtonBar in the field (gutter = null)
 *  - Numeric IME for Distance/Speed (BasicTextField, KeyboardType.Decimal)
 *  - FilamentPresets Field-takeover for the nozzle-temp button (D-17)
 *
 * The STATELESS overload `ExtrudeScreen(vm, activeSpoolDetail, onBack)` drives ALL previews —
 * no live Moonraker, no [works.mees.dinghy.ui.extrude.ExtrudeHolder].
 *
 * Interesting axes:
 *  - [ExtrudeVm] capability state: cold-extruder (can_extrude = false), tool-selector visible
 *  - FilamentPresets mode with + without a loaded spool row
 *  - Theme: 6 combos × dark/light × palette mode
 *  - fs = L overflow check (text/tile clipping)
 *  - Landscape 5U phone budget (800×480dp)
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes
 * MUST be explicit [PreviewBox] seed wrappers. `fs = L` is injected via [fsLargeSeed] (NOT
 * `@Preview(fontScale = …)`, which is a NO-OP — DinghyTheme pins fontScale to 1f).
 */

// ─────────────────────────────────────────────────────────────────────────────
// Sample fixtures for Extrude
// ─────────────────────────────────────────────────────────────────────────────

/** Standard Extrude state: extrudable (can_extrude = true), single extruder, nozzle warm. */
private val extrudeReady = ExtrudeVm(
    canExtrude = true,
    nozzleTemp = 210.0,
    nozzleTarget = 210.0,
    activeHeater = "extruder",
    hasLoadMacro = true,
    hasUnloadMacro = true,
)

/** Cold state — nozzle not at temp, Extrude/Retract buttons should be disabled + show cold glyph. */
private val extrudeCold = ExtrudeVm(
    canExtrude = false,
    nozzleTemp = 25.0,
    nozzleTarget = 0.0,
    activeHeater = "extruder",
    hasLoadMacro = true,
    hasUnloadMacro = true,
)

/** Multi-extruder state — tool selector row visible (EXTR-03 / D-09). */
private val extrudeMultiTool = extrudeReady.copy(
    showToolSelector = true,
    tools = listOf("T0", "T1"),
)

/** A loaded spool with a known extruder temperature for the preset takeover. */
private val loadedSpool = SpoolmanSpool(
    id = 1,
    filament = SpoolmanFilament(
        name = "Galaxy Black",
        material = "PLA",
        colorHex = "#1A1A1A",
        settingsExtruderTemp = 210,
        settingsBedTemp = 60,
    ),
    remainingWeight = 740.0,
)

// ─────────────────────────────────────────────────────────────────────────────
// 6-theme matrix on the standard ready state (cold-extruder dimming exercise)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeThemeColorfulDark() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeReady) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeColorfulLight() =
    PreviewBox(colorfulLight) { ExtrudeScreen(vm = extrudeReady) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeSimpleDark() =
    PreviewBox(simpleDark) { ExtrudeScreen(vm = extrudeReady) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeSimpleLight() =
    PreviewBox(simpleLight) { ExtrudeScreen(vm = extrudeReady) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeHighContrastDark() =
    PreviewBox(highContrastDark) { ExtrudeScreen(vm = extrudeReady) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeHighContrastLight() =
    PreviewBox(highContrastLight) { ExtrudeScreen(vm = extrudeReady) }

// ─────────────────────────────────────────────────────────────────────────────
// Cold-extrude gating (EXTR-04) — Extrude/Retract should be disabled + cold glyph
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeColdGated() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeCold) }

// ─────────────────────────────────────────────────────────────────────────────
// Multi-tool selector visible (EXTR-03 / D-09)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeMultiTool() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeMultiTool) }

// ─────────────────────────────────────────────────────────────────────────────
// FilamentPresets Field-takeover (D-17) — WITHOUT a loaded-spool row
// ─────────────────────────────────────────────────────────────────────────────
// NOTE: We can't force fieldMode=FilamentPresets from the outside (it is internal state).
// This preview uses the standard ready state; tapping the nozzle-temp button in a real
// render would show the preset list. The stateless overload covers compilation + layout.
// The loaded-spool variant below exercises the activeSpoolDetail parameter.

@Nexus7Previews
@Composable
private fun ExtrudePresetTakeoverNoSpool() =
    PreviewBox(colorfulDark) {
        ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = null)
    }

// ─────────────────────────────────────────────────────────────────────────────
// FilamentPresets Field-takeover WITH a loaded spool row (activeSpoolDetail present)
// Exercises the conditional row that reads activeSpoolDetail.filament.settingsExtruderTemp
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudePresetTakeoverWithSpool() =
    PreviewBox(colorfulDark) {
        ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool)
    }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — catches tile/text clipping at the LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { ExtrudeScreen(vm = extrudeReady) }

// ─────────────────────────────────────────────────────────────────────────────
// Landscape — 5U phone-landscape Focus budget (800×480dp)
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "Extrude landscape 5U",
    widthDp = 800,
    heightDp = 480,
    showBackground = true,
)
@Composable
private fun ExtrudeLandscape() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeReady) }
