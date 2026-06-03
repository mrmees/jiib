package works.mees.dinghy.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.GoldenFixtures
import works.mees.dinghy.net.MoonrakerJson

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

        // The walk never throws; the matrices/min-max degrade to null and the prior mesh is NOT lost as a
        // whole (the reducer still produced a BedMeshObject — no crash). The critical property: no throw.
        assertNotNull("reducer did not crash on garbage bed_mesh", afterGarbage.bedMesh)
        assertEquals(null, afterGarbage.bedMesh!!.meshMatrix)
        assertEquals(null, afterGarbage.bedMesh!!.meshMin)
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

    /** JSON-quote a string (escapes embedded quotes/backslashes) for inline fixture building. */
    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""

    private fun streamName(): String = "notify_status_update_stream.json"
}
