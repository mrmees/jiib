package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

/**
 * Host-side proof for [parseLastJob] — pure, no Android, no I/O.
 *
 * Fixtures are FAITHFUL `server.history.list` reply shapes (capabilities doc § "Print history")
 * parsed via [MoonrakerJson] — the mock-vs-reality lesson: build inputs from the REAL reply shape,
 * never a hand-built lenient object. The metadata block reuses the exact same shape as
 * `server.files.metadata`, so the deleted-file / absent-metadata degrade path is exercised for real.
 */
class PrintHistoryParseTest {

    private fun parse(raw: String): LastJob? =
        parseLastJob(MoonrakerJson.parseToJsonElement(raw).jsonObject)

    /** A faithful full E5 completed job: all top-level fields + metadata (est/weight/300px thumb). */
    private val e5CompletedJob = """
        {
          "count": 137,
          "jobs": [
            {
              "job_id": "000089",
              "user": "No User",
              "filename": "miata/airbox-bracket.gcode",
              "status": "completed",
              "start_time": 1717200000.0,
              "end_time": 1717207191.0,
              "print_duration": 7012.4,
              "total_duration": 7191.0,
              "filament_used": 5749.86,
              "exists": true,
              "auxiliary_data": [],
              "metadata": {
                "estimated_time": 7000,
                "filament_weight_total": 17.15,
                "layer_count": 50,
                "object_height": 22.4,
                "thumbnails": [
                  { "width": 32,  "height": 32,  "size": 1024,  "relative_path": ".thumbs/b-32x32.png" },
                  { "width": 48,  "height": 48,  "size": 2048,  "relative_path": ".thumbs/b-48x48.png" },
                  { "width": 300, "height": 300, "size": 40960, "relative_path": ".thumbs/b-300x300.png" }
                ]
              }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesFaithfulE5CompletedJob_allFields() {
        val job = parse(e5CompletedJob)!!
        assertEquals("miata/airbox-bracket.gcode", job.filename)
        assertEquals("completed", job.status)
        assertEquals(7012.4, job.printDuration, 0.0)
        assertEquals(7191.0, job.totalDuration, 0.0)
        assertEquals(5749.86, job.filamentUsed, 0.0)
        assertTrue(job.exists)
        assertEquals(7000.0, job.estimatedTime!!, 0.0)
        assertEquals(17.15, job.filamentWeightTotal!!, 0.0)
        assertTrue(job.largestThumbRelPath!!.endsWith("-300x300.png"))
    }

    @Test
    fun countZero_isEmptyStateSignal_null() {
        val job = parse("""{ "count": 0, "jobs": [] }""")
        assertNull("count==0 → no last job → null empty-state signal", job)
    }

    @Test
    fun jobsAbsentOrEmpty_null() {
        assertNull(parse("""{ "count": 137 }"""))
        assertNull(parse("""{ "count": 137, "jobs": [] }"""))
    }

    @Test
    fun deletedFile_existsFalseAbsentMetadata_textFieldsPopulate_metadataFieldsNull_noCrash() {
        val raw = """
            {
              "count": 5,
              "jobs": [
                {
                  "filename": "old/gone.gcode",
                  "status": "completed",
                  "print_duration": 1200.0,
                  "total_duration": 1300.0,
                  "filament_used": 800.0,
                  "exists": false
                }
              ]
            }
        """.trimIndent()
        val job = parse(raw)!!
        // Text/card fields still populate from the top-level job.
        assertEquals("old/gone.gcode", job.filename)
        assertEquals("completed", job.status)
        assertEquals(1200.0, job.printDuration, 0.0)
        assertFalse(job.exists)
        // Metadata-derived fields degrade to null (no thumbnail / est / weight) — never crash.
        assertNull(job.estimatedTime)
        assertNull(job.filamentWeightTotal)
        assertNull(job.largestThumbRelPath)
    }

    @Test
    fun garbagePartialMetadata_perFieldNull_noThrow() {
        val raw = """
            {
              "count": 1,
              "jobs": [
                {
                  "filename": "x.gcode",
                  "status": "completed",
                  "exists": true,
                  "metadata": {
                    "estimated_time": "not-a-number",
                    "thumbnails": "also-garbage"
                  }
                }
              ]
            }
        """.trimIndent()
        val job = parse(raw)!!
        assertEquals("x.gcode", job.filename)
        assertNull(job.estimatedTime)
        assertNull(job.filamentWeightTotal)
        assertNull(job.largestThumbRelPath)
        // Absent numeric top-level fields fall back to 0.0, never throw.
        assertEquals(0.0, job.printDuration, 0.0)
    }

    @Test
    fun cancelledStatus_parsedVerbatim() {
        val raw = """
            {
              "count": 9,
              "jobs": [
                {
                  "filename": "test/aborted.gcode",
                  "status": "cancelled",
                  "print_duration": 42.0,
                  "total_duration": 55.0,
                  "filament_used": 10.0,
                  "exists": true
                }
              ]
            }
        """.trimIndent()
        val job = parse(raw)!!
        assertEquals("cancelled", job.status)
    }
}
