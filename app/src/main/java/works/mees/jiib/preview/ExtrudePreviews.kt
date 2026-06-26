package works.mees.jiib.preview

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import works.mees.jiib.spool.SpoolmanFilament
import works.mees.jiib.spool.SpoolmanSpool
import works.mees.jiib.ui.extrude.ExtrudeMacroRowVm
import works.mees.jiib.ui.extrude.ExtrudeScreen
import works.mees.jiib.ui.extrude.ExtrudeVm
import works.mees.jiib.ui.extrude.SensorRowVm

/**
 * @Preview matrix for the Extrude screen — refreshed for the 2026-06-16 Focus/Field rebuild.
 *
 * ExtrudeScreen is the filament-handling hub on the two-region grammar:
 *  - Focus: Extrude/Retract accent commands + distance ±[StepperRow] (1/5/25/50 mm) + speed
 *    [Scrubber] capped to `max_extrude_only_velocity` + a live nozzle current/target readout.
 *  - Field: live runout-sensor [ToggleRow]s (hidden when none discovered) + presence-gated
 *    Load/Unload macro rows + the user's pinned macro rows + inline nozzle-only thermal chips +
 *    a spool link row.
 *  - FootButtonBar: Back / Cooldown / Macros.
 *
 * The STATELESS overload `ExtrudeScreen(vm, activeSpoolDetail, onBack, onOpenSpool)` drives ALL
 * previews — no live Moonraker, no [works.mees.jiib.ui.extrude.ExtrudeHolder].
 *
 * Interesting axes:
 *  - [ExtrudeVm] capability state: cold-extruder (`canExtrude = false`), populated vs no-sensor.
 *  - Runout-sensor section present (with enabled + disabled rows) vs absent (empty `sensors`).
 *  - Loaded-spool row (`activeSpoolDetail`) driving the inline thermal preset.
 *  - Theme: the 6 palette/luminance combos.
 *  - `fs = L` overflow check (text/tile clipping).
 *  - Landscape 5U phone budget (800×480dp).
 *
 * The MacroSettings field-takeover is INTERNAL screen state (no public param). It is not directly
 * previewable without adding plumbing just for a preview, so it is intentionally skipped here; the
 * populated fixture still seeds [ExtrudeVm.allMacros]/[ExtrudeVm.pinnedNames] so that data path
 * compiles and is exercised by the screen's unit tests.
 *
 * A `@Preview` annotation cannot select the Colorful/Simple/High-Contrast palette MODE; the themes
 * MUST be explicit [PreviewBox] seed wrappers. `fs = L` is injected via [fsLargeSeed] (NOT
 * `@Preview(fontScale = …)`, which is a NO-OP — DinghyTheme pins fontScale to 1f).
 */

// ─────────────────────────────────────────────────────────────────────────────
// Sample fixtures for Extrude
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Fully-populated, extrudable state: warm nozzle, two runout sensors (one enabled / one disabled),
 * Load+Unload present, two pinned macros, and the macro-settings library seeded.
 */
private val extrudeReady = ExtrudeVm(
    canExtrude = true,
    maxExtrudeVelocity = 15,
    maxExtrudeDistance = 50f,
    nozzleTemp = 210.0,
    nozzleTarget = 210.0,
    activeHeater = "extruder",
    hasLoadMacro = true,
    hasUnloadMacro = true,
    sensors = listOf(
        SensorRowVm(
            objectKey = "filament_switch_sensor Runout",
            sensorName = "Runout",
            prettyName = "Runout",
            enabled = true,
            filamentDetected = true,
        ),
        SensorRowVm(
            objectKey = "filament_motion_sensor Encoder",
            sensorName = "Encoder",
            prettyName = "Encoder",
            enabled = false,
            filamentDetected = null,
        ),
    ),
    pinnedMacros = listOf(
        ExtrudeMacroRowVm(name = "PURGE", description = null),
        ExtrudeMacroRowVm(name = "TIP_SHAPING", description = null),
    ),
    allMacros = listOf(
        ExtrudeMacroRowVm(name = "PURGE", description = null),
        ExtrudeMacroRowVm(name = "TIP_SHAPING", description = null),
        ExtrudeMacroRowVm(name = "CLEAN_NOZZLE", description = null),
        ExtrudeMacroRowVm(name = "M600", description = null),
    ),
    pinnedNames = setOf("PURGE", "TIP_SHAPING"),
)

/**
 * Cold state — nozzle not at temp, no sensors discovered. Extrude/Retract should be disabled +
 * show the cold glyph, and the runout section should vanish.
 */
private val extrudeCold = ExtrudeVm(
    canExtrude = false,
    nozzleTemp = 24.0,
    nozzleTarget = 0.0,
    activeHeater = "extruder",
    hasLoadMacro = true,
    hasUnloadMacro = true,
    sensors = emptyList(),
)

/**
 * Warm + extrudable but NO runout sensors — confirms the runout ToggleRow section disappears while
 * the macro/thermal rows remain.
 */
private val extrudeNoSensors = extrudeReady.copy(sensors = emptyList())

/** A loaded spool with a known extruder temperature for the inline thermal preset row. */
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
// 6-theme matrix on the populated ready state
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeThemeColorfulDark() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeColorfulLight() =
    PreviewBox(colorfulLight) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeSimpleDark() =
    PreviewBox(simpleDark) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeSimpleLight() =
    PreviewBox(simpleLight) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeHighContrastDark() =
    PreviewBox(highContrastDark) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

@Nexus7Previews
@Composable
private fun ExtrudeThemeHighContrastLight() =
    PreviewBox(highContrastLight) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

// ─────────────────────────────────────────────────────────────────────────────
// Cold-extrude gating (EXTR-04) — Extrude/Retract disabled + cold glyph, no runout section
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeColdGated() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeCold) }

// ─────────────────────────────────────────────────────────────────────────────
// No-sensor case — confirms the runout ToggleRow section vanishes (warm + extrudable)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeNoSensors() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeNoSensors) }

// ─────────────────────────────────────────────────────────────────────────────
// Inline thermal preset WITHOUT a loaded spool (activeSpoolDetail = null)
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeNoSpool() =
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = null) }

// ─────────────────────────────────────────────────────────────────────────────
// fs = L overflow check — catches tile/text clipping at the LARGEST in-app text size
// ─────────────────────────────────────────────────────────────────────────────

@Nexus7Previews
@Composable
private fun ExtrudeFsLargeOverflow() =
    PreviewBox(fsLargeSeed) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }

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
    PreviewBox(colorfulDark) { ExtrudeScreen(vm = extrudeReady, activeSpoolDetail = loadedSpool) }
