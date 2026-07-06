package works.mees.jiib.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.net.GoldenFixtures
import works.mees.jiib.net.MoonrakerJson

/**
 * STATE-01: prove the pure reducer SEEDS from an `objects.query` snapshot then DEEP-MERGES each
 * `notify_status_update` partial diff without wiping un-diffed fields (Pitfall 1, merge-not-replace).
 * Runs over the golden corpus, falling back to the synthetic `fallback_*` siblings automatically.
 */
class PrinterStateReducerTest {

    /** The `result.status` object of an objects.query/subscribe snapshot fixture. */
    private fun snapshotStatus(liveName: String): JsonObject =
        GoldenFixtures.resolve(liveName).jsonObject["result"]!!.jsonObject["status"]!!.jsonObject

    /** The `[0]` "changed objects" object of a notify_status_update frame string. */
    private fun diffObjects(frame: String): JsonObject =
        MoonrakerJson.parseToJsonElement(frame).jsonObject["params"]!!.jsonArray[0].jsonObject

    @Test
    fun seedsFromSnapshot() {
        val state = reduceSnapshot(snapshotStatus("objects_query_snapshot.json"))

        assertEquals(KlippyState.Ready, state.klippyState)
        assertEquals(PrintState.Standby, state.printState)
        assertNotNull("extruder heater seeded", state.heaters["extruder"])
        assertNotNull("bed heater seeded", state.heaters["heater_bed"])
        assertEquals(24.5, state.heaters["extruder"]!!.temperature, 0.0001)
        assertEquals(23.8, state.heaters["heater_bed"]!!.temperature, 0.0001)
        assertEquals(1.0, state.speedFactor, 0.0001)
    }

    @Test
    fun tempOnlyDiffDoesNotWipeTarget() {
        // Seed, then SET a bed target, then apply a temp-only bed diff — the target must survive (Pitfall 1).
        val seeded = reduceSnapshot(snapshotStatus("objects_query_snapshot.json"))
        val withTarget = reduceDiff(
            seeded,
            MoonrakerJson.parseToJsonElement("""{ "heater_bed": { "target": 60.0 } }""").jsonObject,
        )
        assertEquals(60.0, withTarget.heaters["heater_bed"]!!.target, 0.0001)

        val afterTempOnly = reduceDiff(
            withTarget,
            MoonrakerJson.parseToJsonElement("""{ "heater_bed": { "temperature": 41.2, "power": 0.95 } }""").jsonObject,
        )

        // Temperature updated...
        assertEquals(41.2, afterTempOnly.heaters["heater_bed"]!!.temperature, 0.0001)
        // ...but the retained target was NOT wiped to 0.
        assertEquals(60.0, afterTempOnly.heaters["heater_bed"]!!.target, 0.0001)
    }

    @Test
    fun replayingDiffStreamYieldsExpectedFinalStateWithNoFieldReset() {
        var state = reduceSnapshot(snapshotStatus("objects_query_snapshot.json"))
        for (frame in GoldenFixtures.frames(streamName())) {
            state = reduceDiff(state, diffObjects(frame))
        }

        // Final temps/targets from the captured stream (bed heated to 60, extruder to 210).
        assertEquals(60.1, state.heaters["heater_bed"]!!.temperature, 0.0001)
        assertEquals(60.0, state.heaters["heater_bed"]!!.target, 0.0001)
        assertEquals(209.8, state.heaters["extruder"]!!.temperature, 0.0001)
        assertEquals(210.0, state.heaters["extruder"]!!.target, 0.0001)

        // Position/homed set by a later frame did NOT reset earlier-merged heater fields.
        assertEquals("xyz", state.homedAxes)
        assertNotNull(state.toolheadPosition)
        assertEquals(110.0, state.toolheadPosition!![0], 0.0001)

        // print_stats + progress flowed through as first-class fields.
        assertEquals(PrintState.Printing, state.printState)
        assertEquals("calibration_cube.gcode", state.printFilename)
        assertEquals(0.25, state.progress, 0.0001)

        // Nothing flickered to empty: the extruder seeded at start is still present.
        assertTrue("extruder retained", state.heaters.containsKey("extruder"))
    }

    @Test
    fun reduceSnapshotIsPureAndDeterministic() {
        val status = snapshotStatus("objects_query_snapshot.json")
        assertEquals(reduceSnapshot(status), reduceSnapshot(status))
    }

    // --- klippyStateMessage (review #8): the splash needs the real Klippy/Moonraker reason ---

    @Test
    fun shutdownDiffWithStateMessageSetsKlippyStateMessage() {
        val msg = "Klipper reports: SHUTDOWN — MCU error"
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "webhooks": { "state": "shutdown", "state_message": ${quote(msg)} } }""",
            ).jsonObject,
        )

        assertEquals(KlippyState.Shutdown, state.klippyState)
        assertEquals(msg, state.klippyStateMessage)
    }

    @Test
    fun readyTransitionClearsKlippyStateMessage() {
        // Stuck in shutdown with a reason, then Klipper comes back ready (no state_message).
        val shutdown = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "webhooks": { "state": "shutdown", "state_message": "MCU 'mcu' shutdown" } }""",
            ).jsonObject,
        )
        assertEquals("MCU 'mcu' shutdown", shutdown.klippyStateMessage)

        val ready = reduceDiff(
            shutdown,
            MoonrakerJson.parseToJsonElement("""{ "webhooks": { "state": "ready" } }""").jsonObject,
        )

        assertEquals(KlippyState.Ready, ready.klippyState)
        assertEquals(null, ready.klippyStateMessage)
    }

    @Test
    fun webhooksAbsentDiffDoesNotWipeKlippyStateMessage() {
        val shutdown = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "webhooks": { "state": "shutdown", "state_message": "Lost communication with MCU" } }""",
            ).jsonObject,
        )

        // A heater-only diff carries NO webhooks block — the reason must survive (STATE-01 merge).
        val afterHeaterDiff = reduceDiff(
            shutdown,
            MoonrakerJson.parseToJsonElement("""{ "heater_bed": { "temperature": 41.2 } }""").jsonObject,
        )

        assertEquals(KlippyState.Shutdown, afterHeaterDiff.klippyState)
        assertEquals("Lost communication with MCU", afterHeaterDiff.klippyStateMessage)
    }

    // --- Phase-5 additions: gcode_position (MOVE-04) + can_extrude (EXTR-04) ---

    @Test
    fun gcodeMoveDiffCapturesGcodePosition() {
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "gcode_move": { "gcode_position": [10.0, 20.0, 5.0, 0.0] } }""",
            ).jsonObject,
        )

        assertEquals(listOf(10.0, 20.0, 5.0, 0.0), state.gcodePosition)
    }

    /**
     * Wave-0 RED scaffold (16-01) — turned GREEN by 16-04.
     *
     * A `gcode_move.homing_origin = [x, y, z, e]` diff must parse the Z component (index 2) into a new
     * `PrinterState.gcodeZOffset` field, so the Print-Status Z-babystep readout reflects the live
     * applied offset (`gcode_move.homing_origin[2]`). A malformed/short array must yield null / retain
     * the prior value (the existing `double*ListOrNull` null-safe walk discipline).
     *
     * RED discipline ([[dinghy-wave0-red-scaffold-compile]]): `gcodeZOffset` does NOT exist yet (it
     * lands in 16-04 — a 1-line reducer parse off `gcode_move.homing_origin[2]` + the new field on
     * PrinterState). The frame is parsed with the existing helpers (compile proof) but the field
     * assertion is held as a `fail()` so the case is RED until 16-04 adds the field + reducer line.
     */
    @Test
    fun gcodeMoveDiffCapturesHomingOriginZAsGcodeZOffset() {
        val diff = MoonrakerJson.parseToJsonElement(
            """{ "gcode_move": { "homing_origin": [0.0, 0.0, 0.125, 0.0] } }""",
        ).jsonObject
        val state = reduceDiff(PrinterState(), diff)
        assertNotNull(state)
        // homing_origin index 2 is the applied Z offset (SC-5).
        assertNotNull("gcodeZOffset parsed", state.gcodeZOffset)
        assertEquals(0.125, state.gcodeZOffset!!, 0.0001)
    }

    @Test
    fun gcodeMoveShortHomingOriginYieldsNullOrRetainsPrior() {
        val diff = MoonrakerJson.parseToJsonElement(
            """{ "gcode_move": { "homing_origin": [0.0, 0.0] } }""",
        ).jsonObject
        val state = reduceDiff(PrinterState(), diff)
        assertNotNull(state)
        // A short (<3) homing_origin array -> getOrNull(2) == null; no index-out-of-bounds crash.
        assertNull("short homing_origin yields null gcodeZOffset", state.gcodeZOffset)
    }

    /**
     * Phase 16 Standby glance: temperature_sensor readings accumulate into a RETAINED map. The
     * partial-diff fix — a later diff that OMITS a sensor object must KEEP that sensor's prior value,
     * not drop it (the map is never rebuilt from the current diff alone).
     */
    @Test
    fun temperatureSensorRetainsPriorValueWhenAbsentFromLaterDiff() {
        val first = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "temperature_sensor mcu": { "temperature": 45.0 } }""",
            ).jsonObject,
        )
        assertEquals(45.0, first.temperatureSensors["temperature_sensor mcu"]!!, 0.0001)

        // A subsequent diff that omits the sensor entirely must NOT drop its prior value.
        val second = reduceDiff(
            first,
            MoonrakerJson.parseToJsonElement(
                """{ "extruder": { "temperature": 200.0 } }""",
            ).jsonObject,
        )
        assertEquals(
            "absent sensor retains prior value",
            45.0,
            second.temperatureSensors["temperature_sensor mcu"]!!,
            0.0001,
        )
    }

    @Test
    fun temperatureSensorUpdatesOnPresent() {
        val first = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "temperature_sensor mcu": { "temperature": 45.0 } }""",
            ).jsonObject,
        )
        val second = reduceDiff(
            first,
            MoonrakerJson.parseToJsonElement(
                """{ "temperature_sensor mcu": { "temperature": 46.5 } }""",
            ).jsonObject,
        )
        assertEquals(46.5, second.temperatureSensors["temperature_sensor mcu"]!!, 0.0001)
    }

    @Test
    fun extruderDiffCapturesCanExtrudeTrue() {
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "extruder": { "can_extrude": true } }""",
            ).jsonObject,
        )

        assertEquals(true, state.heaters["extruder"]!!.canExtrude)
    }

    @Test
    fun extruderDiffCapturesCanExtrudeFalse() {
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "extruder": { "can_extrude": false } }""",
            ).jsonObject,
        )

        assertEquals(false, state.heaters["extruder"]!!.canExtrude)
    }

    @Test
    fun tempOnlyExtruderDiffRetainsCanExtrude() {
        // EXTR-04 / STATE-01: a temp-only extruder diff (no can_extrude key) keeps the prior flag.
        val canExtrude = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "extruder": { "can_extrude": true } }""").jsonObject,
        )
        assertEquals(true, canExtrude.heaters["extruder"]!!.canExtrude)

        val afterTempOnly = reduceDiff(
            canExtrude,
            MoonrakerJson.parseToJsonElement("""{ "extruder": { "temperature": 205.0 } }""").jsonObject,
        )

        assertEquals(205.0, afterTempOnly.heaters["extruder"]!!.temperature, 0.0001)
        // can_extrude was NOT wiped to its default false.
        assertEquals(true, afterTempOnly.heaters["extruder"]!!.canExtrude)
    }

    // --- Phase-7: print-control state confirmation for pause/resume/cancel ---

    @Test
    fun printControlDiffsTrackPauseResumeAndCancelWithoutWipingFilename() {
        var state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{
                  "print_stats": { "state": "printing", "filename": "folder/cube.gcode" },
                  "virtual_sdcard": { "progress": 0.25 },
                  "pause_resume": { "is_paused": false }
                }""",
            ).jsonObject,
        )
        assertEquals(PrintState.Printing, state.printState)
        assertEquals(false, state.pauseResumePaused)
        assertEquals("folder/cube.gcode", state.printFilename)

        state = reduceDiff(
            state,
            MoonrakerJson.parseToJsonElement(
                """{
                  "print_stats": { "state": "paused" },
                  "pause_resume": { "is_paused": true }
                }""",
            ).jsonObject,
        )
        assertEquals(PrintState.Paused, state.printState)
        assertEquals(true, state.pauseResumePaused)
        assertEquals("folder/cube.gcode", state.printFilename)
        assertEquals(0.25, state.progress, 0.0001)

        state = reduceDiff(
            state,
            MoonrakerJson.parseToJsonElement(
                """{
                  "print_stats": { "state": "printing" },
                  "pause_resume": { "is_paused": false }
                }""",
            ).jsonObject,
        )
        assertEquals(PrintState.Printing, state.printState)
        assertEquals(false, state.pauseResumePaused)

        state = reduceDiff(
            state,
            MoonrakerJson.parseToJsonElement("""{ "print_stats": { "state": "cancelled" } }""").jsonObject,
        )
        assertEquals(PrintState.Cancelled, state.printState)
        assertEquals("folder/cube.gcode", state.printFilename)
    }

    @Test
    fun pauseResumeAbsentDiffRetainsPriorPausedTruth() {
        val paused = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "pause_resume": { "is_paused": true } }""").jsonObject,
        )
        val afterProgressOnly = reduceDiff(
            paused,
            MoonrakerJson.parseToJsonElement("""{ "virtual_sdcard": { "progress": 0.5 } }""").jsonObject,
        )

        assertEquals(true, afterProgressOnly.pauseResumePaused)
        assertEquals(0.5, afterProgressOnly.progress, 0.0001)
    }

    // --- Progress source: prefer slicer M73 (display_status), fall back to file (virtual_sdcard),
    //     persisted separately + monotonic clamp so the ring doesn't flip/jitter (2026-07-06). ---

    private fun status(json: String) = MoonrakerJson.parseToJsonElement(json).jsonObject

    @Test
    fun progressPrefersSlicerM73AndDoesNotFlipOnSdcardOnlyDiff() {
        var s = reduceDiff(
            PrinterState(),
            status("""{"print_stats":{"state":"printing","filename":"a.gcode"},"display_status":{"progress":0.40}}"""),
        )
        assertEquals(0.40, s.progress, 1e-6)
        // A file-position-only diff (43%) must NOT flip the ring off the slicer estimate.
        s = reduceDiff(s, status("""{"virtual_sdcard":{"progress":0.43}}"""))
        assertEquals("sdcard-only diff must not override the slicer M73 progress", 0.40, s.progress, 1e-6)
    }

    @Test
    fun progressNeverTicksBackwardDuringPrint() {
        var s = reduceDiff(
            PrinterState(),
            status("""{"print_stats":{"state":"printing","filename":"a.gcode"},"display_status":{"progress":0.50}}"""),
        )
        assertEquals(0.50, s.progress, 1e-6)
        // M73 revises the estimate DOWN — the ring holds, never ticks backward.
        s = reduceDiff(s, status("""{"display_status":{"progress":0.42}}"""))
        assertEquals(0.50, s.progress, 1e-6)
        // Forward again advances normally.
        s = reduceDiff(s, status("""{"display_status":{"progress":0.55}}"""))
        assertEquals(0.55, s.progress, 1e-6)
    }

    @Test
    fun progressResetsWhenANewPrintStarts() {
        var s = reduceDiff(
            PrinterState(),
            status("""{"print_stats":{"state":"printing","filename":"a.gcode"},"display_status":{"progress":0.99}}"""),
        )
        s = reduceDiff(s, status("""{"print_stats":{"state":"complete"}}"""))
        // New job (printing + new filename): the ring resets, never shows the prior job's 99%.
        s = reduceDiff(
            s,
            status("""{"print_stats":{"state":"printing","filename":"b.gcode"},"virtual_sdcard":{"progress":0.02}}"""),
        )
        assertEquals(0.02, s.progress, 1e-6)
    }

    @Test
    fun progressIgnoresStaleSlicerM73AtNewPrintStart() {
        // Job A finishes near 100% (M73 0.99). Job B then starts via the queue (new filename) while
        // Klipper still momentarily holds A's stale M73 0.99, but B's file position is a fresh 2%. The
        // ring must follow the fresh file position, not the stale 99% (reconnect/back-to-back guard).
        var s = reduceDiff(
            PrinterState(),
            status("""{"print_stats":{"state":"printing","filename":"a.gcode"},"display_status":{"progress":0.99}}"""),
        )
        s = reduceDiff(
            s,
            status(
                """{"print_stats":{"state":"printing","filename":"b.gcode"},""" +
                    """"virtual_sdcard":{"progress":0.02},"display_status":{"progress":0.99}}""",
            ),
        )
        assertEquals(0.02, s.progress, 1e-6)
    }

    @Test
    fun progressFallsBackToFilePositionWhenNoSlicerM73() {
        // No display_status ever → file position (virtual_sdcard) drives the ring.
        val s = reduceDiff(
            PrinterState(),
            status("""{"print_stats":{"state":"printing","filename":"a.gcode"},"virtual_sdcard":{"progress":0.30}}"""),
        )
        assertEquals(0.30, s.progress, 1e-6)
    }

    // --- Phase-9: the five calibration live objects, fed the REAL Ender-5-Plus fixtures (09-01) ---

    /**
     * Load a captured `*_e5.json` calibration fixture as a status diff. The fixtures are wrapped
     * `{ "<object_name>": <value> }` — exactly the "changed objects" shape `applyStatus` consumes — so a
     * fixture IS a one-object `notify_status_update` diff.
     */
    private fun calibrationDiff(fixture: String): JsonObject {
        val res = javaClass.getResource("/fixtures/$fixture")
            ?: error("fixture /fixtures/$fixture missing from test classpath")
        return MoonrakerJson.parseToJsonElement(res.readText()).jsonObject
    }

    @Test
    fun bedMeshFixturePopulatesBedMeshWithArrayMinMaxAndMatrices() {
        val state = reduceDiff(PrinterState(), calibrationDiff("bed_mesh_e5.json"))
        assertNotNull("bed_mesh populated", state.bedMesh)
        val bm = state.bedMesh!!

        // mesh_min / mesh_max read as [x, y] arrays (Python tuple → JSON array; 09-01 surprise #4).
        assertEquals(2, bm.meshMin!!.size)
        assertEquals(2, bm.meshMax!!.size)
        assertEquals(85.52119999999996, bm.meshMin!![0], 0.0001)
        assertEquals(237.48999999999998, bm.meshMax!![1], 0.0001)

        // mesh_matrix (interpolated) is the wider grid; probed_matrix is the raw dots — both non-empty.
        assertTrue("meshMatrix non-empty", bm.meshMatrix!!.isNotEmpty() && bm.meshMatrix!![0].isNotEmpty())
        assertTrue("probedMatrix non-empty", bm.probedMatrix!!.isNotEmpty() && bm.probedMatrix!![0].isNotEmpty())
        assertEquals(3, bm.probedMatrix!!.size)
        assertEquals(4, bm.probedMatrix!![0].size)

        // profile_name is the active loaded mesh; empty-state is SEPARATE from saved profiles (Pitfall 4).
        assertEquals("adaptive-7FA0AB1C50", bm.profileName)
    }

    @Test
    fun screwsTiltFixturePopulatesResultsAndError() {
        val state = reduceDiff(PrinterState(), calibrationDiff("screws_tilt_adjust_e5.json"))
        val st = state.screwsTilt!!

        assertFalse("error false when in tolerance", st.error)
        // max_deviation is null even after a run (09-01 surprise #2) — done-detection uses error+results.
        assertEquals(null, st.maxDeviation)
        assertTrue("≥1 screw result", st.results.size >= 1)
        assertEquals(4, st.results.size)

        // adjust is carried VERBATIM as a clock STRING (09-01 surprise #1 — not a float).
        val base = st.results["screw1"]!!
        assertTrue("screw1 is the base reference", base.isBase)
        assertEquals("00:00", base.adjust)
        assertEquals("CCW", st.results["screw2"]!!.sign)
        assertEquals("00:07", st.results["screw2"]!!.adjust)
    }

    @Test
    fun manualProbeFixturePopulatesIsActiveAndZPosition() {
        val state = reduceDiff(PrinterState(), calibrationDiff("manual_probe_e5.json"))
        val mp = state.manualProbe!!

        assertTrue("is_active true mid-session", mp.isActive)
        assertNotNull("z_position present", mp.zPosition)
        assertEquals(27.52437499995758, mp.zPosition!!, 0.0001)
        assertEquals(7.624374999975678, mp.zPositionLower!!, 0.0001)
        assertEquals(27.62437499995749, mp.zPositionUpper!!, 0.0001)
    }

    @Test
    fun manualProbePartialDiffWithoutIsActiveRetainsActiveSession() {
        // The real Ender-3 klicky flow: PROBE_CALIBRATE starts a session (is_active true, z≈9.825), then
        // the macro's `TESTZ Z=20` lift sends a PARTIAL diff carrying only the changed z_position(+lower)
        // and OMITTING the unchanged is_active. Rebuilding from the delta alone reset is_active→false,
        // collapsing the live session to "Accepted" mid-probe (froze the Z hero at 9.825, killed the jog).
        // A partial diff MUST retain is_active and update z_position.
        val opened = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "manual_probe": { "is_active": true, "z_position": 9.825 } }""",
            ).jsonObject,
        )
        assertTrue("session active after probe", opened.manualProbe!!.isActive)
        assertEquals(9.825, opened.manualProbe!!.zPosition!!, 0.0001)

        val afterTestZLift = reduceDiff(
            opened,
            MoonrakerJson.parseToJsonElement(
                """{ "manual_probe": { "z_position": 35.990, "z_position_lower": 15.990 } }""",
            ).jsonObject,
        )

        // is_active was OMITTED from the diff → must RETAIN true (not collapse to false)...
        assertTrue("is_active retained through partial diff", afterTestZLift.manualProbe!!.isActive)
        // ...and z_position must follow the lift to 35.990 (the hero was frozen at 9.825 before the fix).
        assertEquals(35.990, afterTestZLift.manualProbe!!.zPosition!!, 0.0001)
        assertEquals(15.990, afterTestZLift.manualProbe!!.zPositionLower!!, 0.0001)

        // Session end DOES send an explicit is_active:false — honored.
        val ended = reduceDiff(
            afterTestZLift,
            MoonrakerJson.parseToJsonElement("""{ "manual_probe": { "is_active": false } }""").jsonObject,
        )
        assertEquals(false, ended.manualProbe!!.isActive)
    }

    @Test
    fun zTiltFixtureCarriesAppliedFalseWithoutImplyingFailure() {
        // applied:false post-run is BOTH "running" AND "failed" — the reducer just carries the flag (Pitfall 2).
        val state = reduceDiff(PrinterState(), calibrationDiff("z_tilt_e5.json"))
        assertEquals(false, state.zTiltApplied)
    }

    @Test
    fun zTiltAppliedTrueIsCarried() {
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "z_tilt": { "applied": true } }""").jsonObject,
        )
        assertEquals(true, state.zTiltApplied)
    }

    @Test
    fun quadGantryLevelAppliedIsCarried() {
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "quad_gantry_level": { "applied": true } }""").jsonObject,
        )
        assertEquals(true, state.qglApplied)
    }

    // --- Defensive: a malformed calibration field is SKIPPED, retaining the prior value (Pitfall 6) ---

    @Test
    fun malformedBedMeshDiffRetainsPriorMesh() {
        // Seed a good mesh, then feed a GARBAGE bed_mesh diff (matrix is a string, min/max wrong type).
        val good = reduceDiff(PrinterState(), calibrationDiff("bed_mesh_e5.json"))
        assertEquals("adaptive-7FA0AB1C50", good.bedMesh!!.profileName)

        val afterGarbage = reduceDiff(
            good,
            MoonrakerJson.parseToJsonElement(
                """{ "bed_mesh": { "mesh_matrix": "not-an-array", "mesh_min": 5, "profile_name": 99 } }""",
            ).jsonObject,
        )

        // The walk never throws; field-merge SKIPS the unparseable matrix/min fields (they read null) and
        // RETAINS the prior good values — strictly better than the old rebuild-from-delta that nulled them.
        // A garbage diff can no longer wipe a good mesh.
        assertNotNull("reducer did not crash on garbage bed_mesh", afterGarbage.bedMesh)
        assertEquals(good.bedMesh!!.meshMatrix, afterGarbage.bedMesh!!.meshMatrix)
        assertEquals(good.bedMesh!!.meshMin, afterGarbage.bedMesh!!.meshMin)
    }

    @Test
    fun partialBedMeshDeltaMergesAndRetainsOmittedFields() {
        // The real Moonraker behavior the old rebuild-from-delta missed: PARTIAL bed_mesh deltas.
        val seeded = reduceDiff(PrinterState(), calibrationDiff("bed_mesh_e5.json"))
        assertTrue("seed has saved profiles", seeded.bedMesh!!.profileNames.isNotEmpty())
        assertTrue("seed has a mesh", seeded.bedMesh!!.meshMatrix!!.isNotEmpty())

        // A profile LOAD: matrices + name change, the unchanged `profiles` dict is NOT echoed → the saved
        // list must be RETAINED (else the Load button blanks out after one use — UAT bug #1).
        val afterLoad = reduceDiff(
            seeded,
            MoonrakerJson.parseToJsonElement(
                """{ "bed_mesh": { "profile_name": "default", "mesh_matrix": [[0.1,0.2],[0.3,0.4]] } }""",
            ).jsonObject,
        )
        assertEquals("default", afterLoad.bedMesh!!.profileName)
        assertEquals("profiles list retained across a load", seeded.bedMesh!!.profileNames, afterLoad.bedMesh!!.profileNames)
        assertEquals(2, afterLoad.bedMesh!!.meshMatrix!!.size)

        // A profile SAVE: only `profiles` changes (new name added), the matrices are NOT echoed → the
        // displayed mesh must be RETAINED (else the heatmap clears after a save — UAT bug #2).
        val afterSave = reduceDiff(
            afterLoad,
            MoonrakerJson.parseToJsonElement(
                """{ "bed_mesh": { "profiles": { "default": {}, "post": {}, "pre": {}, "new": {} } } }""",
            ).jsonObject,
        )
        assertEquals("displayed mesh retained across a save", afterLoad.bedMesh!!.meshMatrix, afterSave.bedMesh!!.meshMatrix)
        assertEquals("active profile retained across a save", "default", afterSave.bedMesh!!.profileName)
        assertTrue("the newly-saved profile appears in the list", "new" in afterSave.bedMesh!!.profileNames)
    }

    @Test
    fun bedMeshClearIsHonoredViaExplicitEmptyMatrix() {
        // A genuine BED_MESH_CLEAR sends mesh_matrix [] + profile_name "" — PRESENT-but-empty, which the
        // merge must honor as a real clear (distinct from "field absent → retain"). double2dListOrNull
        // returns emptyList for [] vs null for an absent key, so the distinction holds.
        val seeded = reduceDiff(PrinterState(), calibrationDiff("bed_mesh_e5.json"))
        val afterClear = reduceDiff(
            seeded,
            MoonrakerJson.parseToJsonElement("""{ "bed_mesh": { "profile_name": "", "mesh_matrix": [] } }""").jsonObject,
        )
        assertEquals("", afterClear.bedMesh!!.profileName)
        assertTrue("an explicit empty matrix clears the mesh", afterClear.bedMesh!!.meshMatrix!!.isEmpty())
    }

    @Test
    fun calibrationObjectsDefaultNullUntilSeen() {
        // A pristine state (no calibration diff) leaves every calibration field at its null default.
        val s = PrinterState()
        assertEquals(null, s.bedMesh)
        assertEquals(null, s.screwsTilt)
        assertEquals(null, s.zTiltApplied)
        assertEquals(null, s.qglApplied)
        assertEquals(null, s.manualProbe)
    }

    @Test
    fun heaterOnlyDiffRetainsPriorCalibrationObjects() {
        // STATE-01 merge: a heater diff must not wipe a previously-seen bed_mesh.
        val seeded = reduceDiff(PrinterState(), calibrationDiff("bed_mesh_e5.json"))
        val afterHeater = reduceDiff(
            seeded,
            MoonrakerJson.parseToJsonElement("""{ "extruder": { "temperature": 205.0 } }""").jsonObject,
        )
        assertEquals("adaptive-7FA0AB1C50", afterHeater.bedMesh!!.profileName)
    }

    // --- Phase-17 Fine-Tune reducer additions (GREEN — implemented in 17-03) ----------------------
    //
    // The new PrinterState fields (maxVelocity / maxAccel / minimumCruiseRatio / squareCornerVelocity /
    // pressureAdvance / smoothTime / partFanSpeed / firmwareRetraction) + the FirmwareRetractionObject
    // model are reduced RAW (no scaling); diff-merge retains omitted fields (Pitfall 1).

    @Test
    fun reduces_toolhead_motion_limits() {
        // A synthetic `toolhead` diff with the four motion limits reduces RAW (no scaling in the reducer).
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "toolhead": { "max_velocity": 300.0, "max_accel": 3000.0,
                       "minimum_cruise_ratio": 0.5, "square_corner_velocity": 5.0 } }""",
            ).jsonObject,
        )

        assertEquals(300.0, state.maxVelocity!!, 0.0001)
        assertEquals(3000.0, state.maxAccel!!, 0.0001)
        // minimum_cruise_ratio stays the RAW ratio (0.5), NOT a percent — the holder converts for display.
        assertEquals(0.5, state.minimumCruiseRatio!!, 0.0001)
        assertEquals(5.0, state.squareCornerVelocity!!, 0.0001)
    }

    @Test
    fun reduces_extruder_pa_and_smoothtime() {
        // extruder.pressure_advance + extruder.smooth_time read from a SEPARATE extruder walk (not the
        // per-heater merge loop). A temp-only extruder diff must not crash and PA/smooth stay null there.
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "extruder": { "pressure_advance": 0.045, "smooth_time": 0.04 } }""",
            ).jsonObject,
        )

        assertEquals(0.045, state.pressureAdvance!!, 0.0001)
        assertEquals(0.04, state.smoothTime!!, 0.0001)
    }

    @Test
    fun reduces_fan_speed() {
        // fan.speed 0.6 -> partFanSpeed=0.6 (RAW 0..1 ratio, NO scaling in the reducer).
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "fan": { "speed": 0.6 } }""").jsonObject,
        )

        assertEquals(0.6, state.partFanSpeed!!, 0.0001)
    }

    @Test
    fun reduces_firmware_retraction_synthetic() {
        // A SYNTHETIC firmware_retraction object (build-blind coverage — neither dev printer has it)
        // reduces into a populated FirmwareRetractionObject.
        val state = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "firmware_retraction": { "retract_length": 0.5, "retract_speed": 35.0,
                       "unretract_extra_length": 0.0, "unretract_speed": 35.0 } }""",
            ).jsonObject,
        )

        val fr = state.firmwareRetraction!!
        assertEquals(0.5, fr.retractLength!!, 0.0001)
        assertEquals(35.0, fr.retractSpeed!!, 0.0001)
        assertEquals(0.0, fr.unretractExtraLength!!, 0.0001)
        assertEquals(35.0, fr.unretractSpeed!!, 0.0001)
    }

    @Test
    fun diffMerge_retains_omitted_finetune_fields() {
        // Seed maxVelocity, then a toolhead diff carrying only max_accel must KEEP maxVelocity (merge-
        // not-replace). Same retention proven for a partial firmware_retraction delta.
        val seeded = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement("""{ "toolhead": { "max_velocity": 300.0 } }""").jsonObject,
        )
        assertEquals(300.0, seeded.maxVelocity!!, 0.0001)

        val afterAccelOnly = reduceDiff(
            seeded,
            MoonrakerJson.parseToJsonElement("""{ "toolhead": { "max_accel": 3000.0 } }""").jsonObject,
        )
        // max_velocity OMITTED from the second diff -> retained (not wiped to null).
        assertEquals("maxVelocity retained across a partial toolhead diff", 300.0, afterAccelOnly.maxVelocity!!, 0.0001)
        assertEquals(3000.0, afterAccelOnly.maxAccel!!, 0.0001)

        // firmware_retraction field-by-field merge: seed all four, then a partial delta retains omitted.
        val frSeed = reduceDiff(
            PrinterState(),
            MoonrakerJson.parseToJsonElement(
                """{ "firmware_retraction": { "retract_length": 0.5, "retract_speed": 35.0,
                       "unretract_extra_length": 0.0, "unretract_speed": 35.0 } }""",
            ).jsonObject,
        )
        val frPartial = reduceDiff(
            frSeed,
            MoonrakerJson.parseToJsonElement(
                """{ "firmware_retraction": { "retract_length": 0.8 } }""",
            ).jsonObject,
        )
        assertEquals(0.8, frPartial.firmwareRetraction!!.retractLength!!, 0.0001)
        // retract_speed OMITTED -> retained.
        assertEquals(35.0, frPartial.firmwareRetraction!!.retractSpeed!!, 0.0001)
    }

    /** JSON-quote a string (escapes embedded quotes/backslashes) for inline fixture building. */
    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun streamName(): String = "notify_status_update_stream.json"
}
