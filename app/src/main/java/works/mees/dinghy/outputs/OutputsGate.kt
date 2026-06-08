package works.mees.dinghy.outputs

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * PURE output-discovery gate (SC-1 / SC-3). [parseOutputs] turns `configfile.settings` + `objects.list`
 * into the controllable-output list — whitelist-filtered, case-recovered, prettified, alpha-sorted. No
 * I/O, no Compose; host-tested by `OutputsGateTest` against the real E5P/E3P fixtures, exactly like
 * [works.mees.dinghy.calibration.calibrationSupport].
 *
 * The discovery algorithm (19-RESEARCH § "Discovery Algorithm"):
 *  1. iterate `configfile.settings` entries
 *  2. keep only those whose first space-delimited token ∈ [WHITELIST] (excludes
 *     extruder/heater_bed/fan/temperature_sensor/stepper_*)
 *  3. case-recover the FULL object name from `objects.list` (settings is lowercased; objects.list is
 *     case-preserved). An entry with NO matching live object is DROPPED (configured-but-not-loaded —
 *     the `hasMacroIgnoreCase` lesson)
 *  4. build the descriptor: `commandName` = the bare name after the space (HIGH-1 — the wire name)
 *  5. read pwm (settings boolean, always present for output_pin), maximum_servo_angle (default 180),
 *     readOnly (a `static_value` key → static pin)
 *  6. prettify the bare name (underscores/dashes → spaces, title-case, collapse) — NO camelCase split
 *  7. sort alphabetically by prettyName
 */
object OutputsGate {

    /**
     * The ten controllable output families (CONTEXT / 19-RESEARCH). A settings section is an output iff
     * its first token is one of these — everything else (extruder, heater_bed, fan, temperature_sensor,
     * stepper_N, tmc drivers, mcu, gcode_macro, ...) is excluded by construction.
     */
    val WHITELIST: Set<String> = setOf(
        "heater_generic",
        "fan_generic",
        "led",
        "neopixel",
        "dotstar",
        "pca9533",
        "pca9632",
        "servo",
        "output_pin",
        "pwm_tool",
    )

    /**
     * Discover controllable outputs (SC-1). [settings] is `configfile.settings` (LOWERCASED keys);
     * [liveObjects] is the `objects.list` set (case-PRESERVED). Returns whitelist-filtered, case-recovered,
     * prettified descriptors sorted by prettyName. Empty when the printer defines no whitelisted output
     * (drives the D-10 tile gate). Pure — no I/O.
     */
    fun parseOutputs(settings: JsonObject, liveObjects: Set<String>): List<OutputDescriptor> =
        settings.entries
            .mapNotNull { (lcKey, value) ->
                val family = lcKey.substringBefore(' ')
                if (family !in WHITELIST) return@mapNotNull null

                // Case-recover the FULL object name from objects.list. Settings is lowercased; the live
                // object preserves case and is the wire-truth. No live match → configured-but-not-loaded
                // → DROP (never fabricate a command name from the lowercased settings key — HIGH-1 / the
                // hasMacroIgnoreCase lesson).
                val objectKey = liveObjects.firstOrNull { it.equals(lcKey, ignoreCase = true) }
                    ?: return@mapNotNull null

                val section = value as? JsonObject ?: return@mapNotNull null
                val commandName = objectKey.substringAfter(' ')

                // GAP-B: derive per-LED channel capability from the settings section (only for LED families).
                val (ledHasRgb, ledHasWhite) =
                    if (family in OutputsHolder.LED_FAMILIES) ledCapability(family, section)
                    else false to false

                OutputDescriptor(
                    objectKey = objectKey,
                    family = family,
                    commandName = commandName,
                    prettyName = prettify(commandName),
                    pwm = section.booleanOrNull("pwm") ?: false,
                    servoAngleMax = section.floatOrNull("maximum_servo_angle")
                        ?: OutputDescriptor.DEFAULT_SERVO_ANGLE_MAX,
                    readOnly = section.containsKey("static_value"),
                    ledHasRgb = ledHasRgb,
                    ledHasWhite = ledHasWhite,
                )
            }
            .sortedBy { it.prettyName }

    /** D-10 input: does the printer expose ANY controllable output? */
    fun hasAnyOutput(descriptors: List<OutputDescriptor>): Boolean = descriptors.isNotEmpty()

    /** Pin-based "dumb" PWM LED — capability comes from its red/green/blue/white pins. */
    private const val FAMILY_LED = "led"
    /** Fixed 4-channel RGBW driver (Codex SS-1: no `*_pin`, no `color_order`). */
    private const val FAMILY_PCA9533 = "pca9533"

    /**
     * GAP-B (T-19-10-01 / T-19-10-04): derive `(ledHasRgb, ledHasWhite)` for an LED [family] from its
     * settings [section]. Three shapes:
     *  - pin-based `led`: red/green/blue pin present ⇒ RGB; white_pin present ⇒ white (an RGBW `[led]` is both).
     *  - color_order families (`neopixel`/`dotstar`/`pca9632`): a token with R/G/B ⇒ RGB; a token with "W" ⇒
     *    white. Handles array AND scalar `color_order` shapes. Absent `color_order` defaults RGBW-capable —
     *    `pca9632`'s Klipper default order is RGBW; neopixel/dotstar are RGB by definition.
     *  - fixed driver `pca9533`: a 4-channel RGBW driver with no pins/order ⇒ both flags directly (Codex SS-1).
     *  - FALLBACK: an LED that matches none of the above defaults RGB-capable so we never hide ALL controls
     *    (RGB is the safe superset; a no-op RGB write is the pre-fix behavior, not a regression).
     */
    private fun ledCapability(family: String, section: JsonObject): Pair<Boolean, Boolean> = when (family) {
        FAMILY_LED -> {
            val rgb = section.containsKey("red_pin") ||
                section.containsKey("green_pin") ||
                section.containsKey("blue_pin")
            val white = section.containsKey("white_pin")
            // FALLBACK: a bare [led] with neither set defaults RGB-capable (never hide all controls).
            if (!rgb && !white) true to false else rgb to white
        }
        FAMILY_PCA9533 -> true to true // fixed RGBW driver — no pins, no color_order (Codex SS-1).
        else -> {
            // color_order families (neopixel / dotstar / pca9632).
            val tokens = section.colorOrderTokens()
            if (tokens.isEmpty()) {
                // Absent color_order: pca9632 defaults RGBW; neopixel/dotstar are RGB by definition.
                true to (family == "pca9632")
            } else {
                val rgb = tokens.any { tok -> tok.any { it == 'R' || it == 'G' || it == 'B' } }
                val white = tokens.any { tok -> tok.contains('W') }
                // FALLBACK guard: a recognized-but-unparseable order still shows RGB.
                if (!rgb && !white) true to false else rgb to white
            }
        }
    }

    /**
     * The upper-cased `color_order` tokens from either a JSON array (`["GRB"]`, the Klipper shape) or a
     * scalar string (`"GRB"`), or empty when absent/unrecognized. Defensive (T-19-10-04).
     */
    private fun JsonObject.colorOrderTokens(): List<String> {
        return when (val co = this["color_order"]) {
            is JsonArray -> co.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.uppercase() }
            is JsonPrimitive -> co.contentOrNull?.uppercase()?.let { listOf(it) } ?: emptyList()
            else -> emptyList()
        }
    }

    /**
     * Prettify a bare section name: split on `_`/`-`/whitespace only, title-case each token, collapse
     * spaces. Deliberately does NOT camelCase-split (`expanderPixel` → "Expanderpixel", RESEARCH Open Q3).
     */
    private fun prettify(bare: String): String =
        bare.split('_', '-', ' ')
            .filter { it.isNotBlank() }
            .joinToString(" ") { token ->
                token.lowercase().replaceFirstChar { it.uppercaseChar() }
            }

    private fun JsonObject.booleanOrNull(key: String): Boolean? =
        runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

    private fun JsonObject.floatOrNull(key: String): Float? =
        runCatching { this[key]?.jsonPrimitive?.floatOrNull }.getOrNull()
}
