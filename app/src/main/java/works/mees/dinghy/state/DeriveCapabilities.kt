package works.mees.dinghy.state

/**
 * PURE capability derivation (STATE-02 / A3 / A4). Both functions take only a `List<String>` (the
 * `printer.objects.list` `result.objects` array) and return immutable models — no I/O, no coroutines,
 * no socket. Same input always yields the same output, so the Wave-3 handshake (02-04) re-runs them on
 * EVERY reconnect (STATE-02) and 02-02 tests prove gating deterministically off-hardware.
 */

private val EXTRUDER_N = Regex("""extruder\d+""")

private fun isExtruder(name: String): Boolean = name == "extruder" || name.matches(EXTRUDER_N)

/**
 * Turn `printer.objects.list` into an immutable [Capabilities] (STATE-02). Filters the object-name
 * strings into the v1 gating surface and also retains the exact raw object/component sets for generic
 * command predicates. [Capabilities.powerDevices] is ALWAYS EMPTY here (A4 — power devices come from
 * `machine.device_power.devices`, a different Moonraker API, NOT objects.list).
 */
fun deriveCapabilities(objects: List<String>, components: Set<String> = emptySet()): Capabilities {
    val ext = objects.filter(::isExtruder)
    val macros = objects.filter { it.startsWith("gcode_macro ") }.map { it.removePrefix("gcode_macro ") }
    val fans = objects.filter {
        it == "fan" ||
            it.startsWith("fan_generic ") ||
            it.startsWith("heater_fan ") ||
            it.startsWith("controller_fan ")
    }
    val heaters = objects.filter {
        it == "heater_bed" || isExtruder(it) || it.startsWith("heater_generic ")
    }
    return Capabilities(
        objects = objects.toSet(),
        components = components,
        hasBed = "heater_bed" in objects,
        extruderCount = ext.size,
        fans = fans,
        macros = macros,
        // A4: power devices are sourced from machine.device_power.devices later, NOT objects.list.
        powerDevices = emptyList(),
        heaters = heaters,
    )
}

/**
 * The v1 `objects.subscribe` superset — the objects the spine + future v1 panels need (02-RESEARCH
 * § "Code Examples"). Static core objects are always-requested-IF-present; dynamic objects (extra
 * extruders, fans, heater_generic.*) are added from the detected list by [deriveSubscribeSet].
 */
private val V1_SUBSCRIBE_CORE: Set<String> = setOf(
    "webhooks",        // klippy state/state_message (STATE-04)
    "print_stats",     // job lifecycle (STATE-04 / Phase 6)
    "pause_resume",    // pause/resume state confirmation (Phase 7)
    "virtual_sdcard",  // progress (Phase 6)
    "display_status",  // progress/message (Phase 6)
    "toolhead",        // position, homed_axes (Move/Phase 4)
    "stepper_enable",  // motion-stepper enable map → PrinterState.motorsEnabled (home digest)
    "gcode_move",      // speed/extrude factor, gcode_position (Phase 4/6)
    "heater_bed",      // temp/target/power (Temp/Phase 4)
    "extruder",        // temp/target/power/can_extrude (Temp/Extrude/Phase 4)
    // Phase-9 calibration objects (CALIB-02..05) — always-requested-IF-present; the intersect loop
    // in deriveSubscribeSet enforces A3 (never subscribe an object the printer does not define).
    "screws_tilt_adjust", // CALIB-02 guided-loop result source
    "z_tilt",             // CALIB-03 applied flag
    "quad_gantry_level",  // CALIB-03 applied flag (gated off on both test printers, D-02)
    "bed_mesh",           // CALIB-04 mesh matrices / profiles
    "manual_probe",       // CALIB-05 is_active / z_position session state
    "probe",              // CALIB-05 probe-present gate (PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE, A3)
    // Phase-17 Fine-Tune (TUNE-04): firmware_retraction live values. Build-blind on both dev printers
    // (object absent) — safe to request unconditionally; the intersect loop drops it when the printer
    // does not define the object. toolhead/gcode_move/extruder/fan are already subscribed (fan via the
    // dynamic `name == "fan"` clause), so only firmware_retraction is net-new here.
    "firmware_retraction", // CALIB/TUNE-04 SET_RETRACTION readback (gated OFF when absent)
)

/**
 * Derive the exact `objects.subscribe` set to request: the v1 superset INTERSECTED with the detected
 * `objects` (A3), plus the dynamic objects the printer actually defines (extra extruders, fans,
 * heater_generic.*). Pure — re-runnable on every reconnect. The client NEVER requests an object the
 * printer does not define (e.g. a minimal printer's set omits `heater_bed` / a second extruder).
 */
fun deriveSubscribeSet(objects: List<String>): Set<String> {
    val present = objects.toSet()
    val result = linkedSetOf<String>()

    // Core superset ∩ detected (A3 — never subscribe to a missing object).
    for (core in V1_SUBSCRIBE_CORE) {
        if (core in present) result += core
    }

    // Dynamic objects the printer defines that v1 cares about.
    for (name in objects) {
        when {
            EXTRUDER_N.matches(name) -> result += name
            name == "fan" ||
                name.startsWith("fan_generic ") ||
                name.startsWith("heater_fan ") ||
                name.startsWith("controller_fan ") -> result += name
            name.startsWith("heater_generic ") -> result += name
            // Phase 16: temperature_sensor objects (chamber/mcu/host probes) for the Standby glance metric.
            name.startsWith("temperature_sensor ") -> result += name
            // Phase 19 Output Controls (SC-1 / 19-04): every detected object whose first token ∈ the output
            // WHITELIST is subscribed so its live status diffs (speed/value/color_data/temperature) reach the
            // reducer. Built HERE from objects.list (before the configfile read) — the single owner of the
            // subscribe set — NOT in ad-hoc session code (the HIGH/MEDIUM review fix). Intersect-with-detected
            // is the function's contract (A3), so we only subscribe outputs the printer actually defines.
            // heater_generic is already added above (its own clause) and flows through the heaters map.
            name.substringBefore(' ') in works.mees.dinghy.outputs.OutputsGate.WHITELIST -> result += name
            // Extrude rework: filament-runout sensors so their enabled/filament_detected live diffs
            // reach the reducer (PrinterState.filamentSensors). Same intersect-with-detected contract (A3).
            name.substringBefore(' ') in setOf("filament_switch_sensor", "filament_motion_sensor") ->
                result += name
        }
    }

    return result
}
