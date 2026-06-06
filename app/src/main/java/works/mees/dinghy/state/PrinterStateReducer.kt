package works.mees.dinghy.state

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import works.mees.dinghy.net.JsonRpcMethods

/**
 * The PURE state layer (STATE-01 / STATE-04). Two free functions — [reduceSnapshot] and [reduceDiff] —
 * turn Moonraker's loose status JSON into the immutable [PrinterState]. Plus [applyKlippyMethod] folds a
 * `notify_klippy_*` method name into the first-class [PrinterState.klippyState].
 *
 * House rule (T-02-03 mitigation, ASVS V5): inbound wire JSON is untrusted-ish — every walk is null-safe
 * (`?.`/`orNull`), NEVER `!!`. A missing/garbage field is SKIPPED (the retained value is kept), never fatal.
 *
 * Purity (the whole point — RESEARCH "the risk is getting the diff-merge semantics exactly right"):
 *  - no `CoroutineScope`, socket, `Context`, or any I/O — same input always yields the same output;
 *  - so Wave-3 (02-04) re-runs these deterministically on every reconnect and the golden corpus proves
 *    merge/lifecycle semantics with no live hardware.
 *
 * Merge-not-replace (Pitfall 1, STATE-01): [reduceDiff] deep-merges a `notify_status_update` partial diff
 * field-by-field onto the RETAINED state. A temp-only `extruder` diff updates the temperature and KEEPS the
 * previously-set target; it does not blank un-diffed fields.
 */

/**
 * Seed a full [PrinterState] from an `objects.query` / `objects.subscribe` `status` object
 * (the `result.status` of a query reply, or the first element of a subscribe reply). Reads each
 * v1-subscribed object defensively; absent objects leave their defaults.
 */
fun reduceSnapshot(status: JsonObject): PrinterState =
    applyStatus(PrinterState(), status)

/**
 * Deep-merge a `notify_status_update` partial diff (the `[0]` "changed objects" object of the params
 * array) onto [current], producing the next [PrinterState]. Field-by-field merge — un-mentioned fields
 * are retained (Pitfall 1). Identical mechanics to the seed, only the base differs (retained vs default).
 */
fun reduceDiff(current: PrinterState, diff: JsonObject): PrinterState =
    applyStatus(current, diff)

/**
 * Fold a `notify_klippy_*` method name into the next [KlippyState] (STATE-04). These notifications carry
 * NO params, so the method name alone drives the transition. Last-known values are RETAINED (D-03 retain,
 * distinct from blanking) — only [PrinterState.klippyState] changes. Unknown methods leave state untouched.
 */
fun applyKlippyMethod(current: PrinterState, method: String): PrinterState {
    val next = when (method) {
        JsonRpcMethods.NOTIFY_KLIPPY_READY -> KlippyState.Ready
        JsonRpcMethods.NOTIFY_KLIPPY_SHUTDOWN -> KlippyState.Shutdown
        JsonRpcMethods.NOTIFY_KLIPPY_DISCONNECTED -> KlippyState.Disconnected
        else -> return current
    }
    return current.copy(klippyState = next)
}

// ---------------------------------------------------------------------------------------------------------
// Internals — a single status-walker shared by seed and diff so merge semantics are identical (only the
// base state differs). Every accessor below is null-safe: a missing object/field falls through to `current`.
// ---------------------------------------------------------------------------------------------------------

private fun applyStatus(current: PrinterState, status: JsonObject): PrinterState {
    var s = current

    status.objectOrNull("webhooks")?.let { wh ->
        wh.stringOrNull("state")?.let { s = s.copy(klippyState = klippyFromWebhook(it)) }
        // klippyStateMessage (review #8): prefer a freshly-provided reason; clear stale reason on
        // recovery to Ready; otherwise RETAIN (a webhooks block without a state_message must not wipe
        // it — STATE-01 merge). A webhooks-ABSENT diff retains by construction (this block is skipped).
        val newMessage = wh.stringOrNull("state_message")
        s = s.copy(
            klippyStateMessage = when {
                newMessage != null -> newMessage
                s.klippyState == KlippyState.Ready -> null
                else -> s.klippyStateMessage
            },
        )
    }

    status.objectOrNull("print_stats")?.let { ps ->
        ps.stringOrNull("state")?.let { s = s.copy(printState = printStateFrom(it)) }
        ps.stringOrNull("filename")?.let { s = s.copy(printFilename = it) }
        ps.doubleOrNullAt("print_duration")?.let { s = s.copy(printDuration = it) }
        ps.doubleOrNullAt("total_duration")?.let { s = s.copy(totalDuration = it) }
        ps.doubleOrNullAt("filament_used")?.let { s = s.copy(filamentUsed = it) }
        // info.{current,total}_layer are present-but-NULLABLE (null when idle / slicer didn't set them —
        // catalog). When the `info` block is present we SET both (a JSON null → null, resetting on idle);
        // an absent `info` block retains the prior values (merge semantics).
        ps.objectOrNull("info")?.let { info ->
            s = s.copy(
                currentLayer = info.intOrNullAt("current_layer"),
                totalLayer = info.intOrNullAt("total_layer"),
            )
        }
    }

    status.objectOrNull("toolhead")?.let { th ->
        th.stringOrNull("homed_axes")?.let { s = s.copy(homedAxes = it) }
        th.doubleListOrNull("position")?.let { s = s.copy(toolheadPosition = it) }
        // Phase-17 Fine-Tune motion limits (TUNE-02 / D-04..D-07) — RAW units/ratio, no scaling here
        // (Pitfall 1: minimum_cruise_ratio stays the raw 0..1 ratio; the holder converts to percent for
        // display). Each is null-safe + null-only-skips, so a partial toolhead diff retains omitted fields.
        th.doubleOrNullAt("max_velocity")?.let { s = s.copy(maxVelocity = it) }
        th.doubleOrNullAt("max_accel")?.let { s = s.copy(maxAccel = it) }
        th.doubleOrNullAt("minimum_cruise_ratio")?.let { s = s.copy(minimumCruiseRatio = it) }
        th.doubleOrNullAt("square_corner_velocity")?.let { s = s.copy(squareCornerVelocity = it) }
    }

    status.objectOrNull("gcode_move")?.let { gm ->
        gm.doubleOrNullAt("speed_factor")?.let { s = s.copy(speedFactor = it) }
        gm.doubleOrNullAt("extrude_factor")?.let { s = s.copy(extrudeFactor = it) }
        // MOVE-04 / Pitfall 1: gcode_position is the offsets-stripped user-facing X/Y/Z source — NOT toolhead.position.
        gm.doubleListOrNull("gcode_position")?.let { s = s.copy(gcodePosition = it) }
        // Phase 16 / SC-5: applied Z offset (live babystep) = homing_origin[2]. getOrNull(2) is null-safe
        // on a short/garbage array (the helper already null-guards non-numeric cells), so we never crash.
        gm.doubleListOrNull("homing_origin")?.let { s = s.copy(gcodeZOffset = it.getOrNull(2)) }
    }

    // Phase-17 Fine-Tune extruder tunables (TUNE-02 / D-09/D-10). Read in a SEPARATE extruder-keyed
    // walk — NOT the per-heater merge loop below — because pressure_advance / smooth_time are NOT
    // per-heater concepts (they belong to the PRIMARY extruder, single-extruder v1). Null-safe + null-
    // only-skips so a temp-only extruder diff (handled by the heater loop) leaves these retained.
    status.objectOrNull("extruder")?.let { ex ->
        ex.doubleOrNullAt("pressure_advance")?.let { s = s.copy(pressureAdvance = it) }
        ex.doubleOrNullAt("smooth_time")?.let { s = s.copy(smoothTime = it) }
    }

    // Phase-17 part-cooling fan (TUNE-02 / D-11). RAW 0.0..1.0 ratio — NO scaling in the reducer
    // (Pitfall 1); the holder scales to a percentage at the display boundary. Part-cooling `fan` only.
    status.objectOrNull("fan")?.doubleOrNullAt("speed")?.let { s = s.copy(partFanSpeed = it) }

    // Phase-17 firmware_retraction (TUNE-04 / D-12). Field-by-field merge onto the RETAINED object
    // (mirrors bed_mesh/manual_probe) so a partial diff retains omitted fields — never rebuild-from-
    // delta. Build-blind on both dev printers (object absent → block skipped); proven via a synthetic
    // fixture. NO Z-hop field (D-12).
    status.objectOrNull("firmware_retraction")?.let { fr ->
        val prev = s.firmwareRetraction ?: FirmwareRetractionObject()
        s = s.copy(
            firmwareRetraction = FirmwareRetractionObject(
                retractLength = fr.doubleOrNullAt("retract_length") ?: prev.retractLength,
                retractSpeed = fr.doubleOrNullAt("retract_speed") ?: prev.retractSpeed,
                unretractExtraLength = fr.doubleOrNullAt("unretract_extra_length") ?: prev.unretractExtraLength,
                unretractSpeed = fr.doubleOrNullAt("unretract_speed") ?: prev.unretractSpeed,
            ),
        )
    }

    // Progress can arrive on either virtual_sdcard or display_status; last writer wins per frame.
    status.objectOrNull("virtual_sdcard")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }
    status.objectOrNull("display_status")?.doubleOrNullAt("progress")?.let { s = s.copy(progress = it) }

    status.objectOrNull("pause_resume")?.booleanOrNull("is_paused")?.let {
        s = s.copy(pauseResumePaused = it)
    }

    // --- Phase-9 calibration live objects (CALIB-02..05) -----------------------------------------
    // Each walk is null-safe (the house rule): a missing object is skipped (retained); a present object
    // REPLACES the prior value (these are whole structured objects, not field-merged like heaters). The
    // structured object is the result source-of-truth (RESEARCH Pattern 1) — never console parsing.

    status.objectOrNull("screws_tilt_adjust")?.let { st ->
        val results = LinkedHashMap<String, ScrewResult>()
        st.objectOrNull("results")?.let { res ->
            for ((screw, value) in res) {
                val r = (value as? JsonObject) ?: continue
                results[screw] = ScrewResult(
                    z = r.doubleOrNullAt("z"),
                    sign = r.stringOrNull("sign"),
                    adjust = r.stringOrNull("adjust"),
                    isBase = r.booleanOrNull("is_base") ?: false,
                )
            }
        }
        s = s.copy(
            screwsTilt = ScrewsTiltObject(
                error = st.booleanOrNull("error") ?: false,
                maxDeviation = st.doubleOrNullAt("max_deviation"),
                results = results,
            ),
        )
    }

    status.objectOrNull("z_tilt")?.booleanOrNull("applied")?.let { s = s.copy(zTiltApplied = it) }
    status.objectOrNull("quad_gantry_level")?.booleanOrNull("applied")?.let { s = s.copy(qglApplied = it) }

    status.objectOrNull("bed_mesh")?.let { bm ->
        // Merge field-by-field onto the retained mesh (like the heater block below) — Moonraker pushes
        // PARTIAL bed_mesh deltas: a profile LOAD carries the matrices but not the unchanged `profiles`
        // dict; a profile SAVE carries the updated `profiles` but not the matrices. Rebuilding from the
        // delta alone wiped the omitted fields (blanked the Load list after one load; cleared the
        // displayed mesh after a save). An ABSENT field reads null → retain prior; a PRESENT-but-empty
        // field (BED_MESH_CLEAR sends mesh_matrix []) reads empty → honored as a real clear, because
        // double2dListOrNull distinguishes absent (null) from empty ([] -> emptyList).
        val prev = s.bedMesh ?: BedMeshObject()
        s = s.copy(
            bedMesh = BedMeshObject(
                profileName = bm.stringOrNull("profile_name") ?: prev.profileName,
                meshMin = bm.doubleListOrNull("mesh_min") ?: prev.meshMin,
                meshMax = bm.doubleListOrNull("mesh_max") ?: prev.meshMax,
                probedMatrix = bm.double2dListOrNull("probed_matrix") ?: prev.probedMatrix,
                meshMatrix = bm.double2dListOrNull("mesh_matrix") ?: prev.meshMatrix,
                profileNames = bm.objectOrNull("profiles")?.keys?.toList() ?: prev.profileNames,
            ),
        )
    }

    status.objectOrNull("manual_probe")?.let { mp ->
        // Merge field-by-field onto the retained object (like bed_mesh above / heaters below) — Moonraker
        // pushes PARTIAL manual_probe deltas. A TESTZ move during a live session sends only the changed
        // `z_position`(+`z_position_lower`) and OMITS the unchanged `is_active`. Rebuilding from the delta
        // alone reset isActive to false (absent → false), which collapsed the live Probe-Calibrate session
        // to "Accepted" mid-probe (froze the Z hero, disabled the jog) — proven on the real Ender 3 klicky
        // flow (probe → TESTZ Z=20 lift to remove the detachable probe). An ABSENT field retains prior;
        // session-end sends an explicit `is_active:false`, which is honored.
        val prev = s.manualProbe ?: ManualProbeObject()
        s = s.copy(
            manualProbe = ManualProbeObject(
                isActive = mp.booleanOrNull("is_active") ?: prev.isActive,
                zPosition = mp.doubleOrNullAt("z_position") ?: prev.zPosition,
                zPositionLower = mp.doubleOrNullAt("z_position_lower") ?: prev.zPositionLower,
                zPositionUpper = mp.doubleOrNullAt("z_position_upper") ?: prev.zPositionUpper,
            ),
        )
    }

    // Heaters: merge each present heater object field-by-field onto the retained HeaterState.
    val heaterUpdates = mutableMapOf<String, HeaterState>()
    for ((key, value) in status) {
        if (!isHeaterObject(key)) continue
        val obj = (value as? JsonObject) ?: continue
        val prev = s.heaters[key] ?: HeaterState()
        heaterUpdates[key] = prev.copy(
            temperature = obj.doubleOrNullAt("temperature") ?: prev.temperature,
            target = obj.doubleOrNullAt("target") ?: prev.target,
            power = obj.doubleOrNullAt("power") ?: prev.power,
            // EXTR-04: extruder objects carry can_extrude; a temp-only diff lacks it and RETAINS the prior flag.
            canExtrude = obj.booleanOrNull("can_extrude") ?: prev.canExtrude,
        )
    }
    if (heaterUpdates.isNotEmpty()) {
        s = s.copy(heaters = s.heaters + heaterUpdates)
    }

    // temperature_sensor objects (Phase 16 Standby glance). SEPARATE from the heater loop —
    // isHeaterObject() excludes temperature_sensor. UPDATE-ON-PRESENT merge onto the retained map:
    // only sensors PRESENT in this diff with a numeric `temperature` are touched, so an absent
    // sensor RETAINS its prior value (never rebuilt from the current diff alone — the flip fix).
    val sensorUpdates = mutableMapOf<String, Double>()
    for ((key, value) in status) {
        if (!key.startsWith("temperature_sensor ")) continue
        val obj = (value as? JsonObject) ?: continue
        obj.doubleOrNullAt("temperature")?.let { sensorUpdates[key] = it }
    }
    if (sensorUpdates.isNotEmpty()) {
        s = s.copy(temperatureSensors = s.temperatureSensors + sensorUpdates)
    }

    return s
}

private fun isHeaterObject(name: String): Boolean =
    name == "heater_bed" || name == "extruder" || name.matches(EXTRUDER_N) || name.startsWith("heater_generic ")

private val EXTRUDER_N = Regex("""extruder\d+""")

private fun klippyFromWebhook(state: String): KlippyState = when (state) {
    "ready" -> KlippyState.Ready
    "startup" -> KlippyState.Startup
    "shutdown" -> KlippyState.Shutdown
    "error" -> KlippyState.Error
    "disconnected" -> KlippyState.Disconnected
    else -> KlippyState.Disconnected
}

private fun printStateFrom(state: String): PrintState = when (state) {
    "standby" -> PrintState.Standby
    "printing" -> PrintState.Printing
    "paused" -> PrintState.Paused
    "complete" -> PrintState.Complete
    "error" -> PrintState.Error
    "cancelled" -> PrintState.Cancelled
    else -> PrintState.Standby
}

// --- null-safe JsonObject accessors (never `!!` on wire data; bad field -> null -> skipped) ---

private fun JsonObject.objectOrNull(key: String): JsonObject? = (this[key] as? JsonObject)

private fun JsonObject.stringOrNull(key: String): String? =
    runCatching { this[key]?.jsonPrimitive?.content }.getOrNull()

private fun JsonObject.doubleOrNullAt(key: String): Double? =
    runCatching { this[key]?.jsonPrimitive?.doubleOrNull }.getOrNull()

private fun JsonObject.intOrNullAt(key: String): Int? =
    runCatching { this[key]?.jsonPrimitive?.intOrNull }.getOrNull()

private fun JsonObject.booleanOrNull(key: String): Boolean? =
    runCatching { this[key]?.jsonPrimitive?.booleanOrNull }.getOrNull()

private fun JsonObject.doubleListOrNull(key: String): List<Double>? =
    runCatching { (this[key] as? JsonArray)?.map { it.jsonPrimitive.double } }.getOrNull()

/**
 * Array-of-arrays of Doubles (the `bed_mesh` `mesh_matrix`/`probed_matrix` grids — CALIB-04). Null-safe
 * like the 1-D sibling: a non-array key, or any non-numeric cell, makes the WHOLE read null (skip-and-retain).
 */
private fun JsonObject.double2dListOrNull(key: String): List<List<Double>>? =
    runCatching {
        (this[key] as? JsonArray)?.map { row ->
            (row as JsonArray).map { it.jsonPrimitive.double }
        }
    }.getOrNull()
