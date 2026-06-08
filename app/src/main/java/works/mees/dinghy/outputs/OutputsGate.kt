package works.mees.dinghy.outputs

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
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

                OutputDescriptor(
                    objectKey = objectKey,
                    family = family,
                    commandName = commandName,
                    prettyName = prettify(commandName),
                    pwm = section.booleanOrNull("pwm") ?: false,
                    servoAngleMax = section.floatOrNull("maximum_servo_angle")
                        ?: OutputDescriptor.DEFAULT_SERVO_ANGLE_MAX,
                    readOnly = section.containsKey("static_value"),
                )
            }
            .sortedBy { it.prettyName }

    /** D-10 input: does the printer expose ANY controllable output? */
    fun hasAnyOutput(descriptors: List<OutputDescriptor>): Boolean = descriptors.isNotEmpty()

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
