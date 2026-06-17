package works.mees.dinghy.state

import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.net.MoonrakerJson

/**
 * Host-side proof for [parsePrintMetadata] + [thumbnailUrl] — pure, no Android, no I/O.
 *
 * Fixtures are FAITHFUL catalog-shaped raw JSON parsed via [MoonrakerJson] (the mock-vs-reality
 * lesson: build inputs from the real `server.files.metadata` reply shape, never a hand-built lenient
 * object). The E5 sample (capabilities doc § "File metadata") OMITS `object_height`; the E3 sample
 * carries `object_height 9.96` — both are exercised so the null-degrade path is real, not assumed.
 */
class PrintMetadataParseTest {

    private fun parse(raw: String): PrintMetadata =
        parsePrintMetadata(MoonrakerJson.parseToJsonElement(raw).jsonObject)

    /** A faithful Ender-5 metadata reply (estimated_time 2191, layer_count 50, NO object_height, 32/48/300 thumbs). */
    private val e5Metadata = """
        {
          "size": 1234567,
          "modified": 1700000000.0,
          "uuid": "abc-123",
          "slicer": "OrcaSlicer",
          "slicer_version": "2.3",
          "estimated_time": 2191,
          "filament_total": 5749.86,
          "filament_weight_total": 17.15,
          "layer_count": 50,
          "layer_height": 0.2,
          "first_layer_height": 0.24,
          "nozzle_diameter": 0.4,
          "printer_model": "Creality Ender-5 Plus",
          "thumbnails": [
            { "width": 32,  "height": 32,  "size": 1024,  "relative_path": ".thumbs/benchy-32x32.png" },
            { "width": 48,  "height": 48,  "size": 2048,  "relative_path": ".thumbs/benchy-48x48.png" },
            { "width": 300, "height": 300, "size": 40960, "relative_path": ".thumbs/benchy-300x300.png" }
          ]
        }
    """.trimIndent()

    /** A faithful Ender-3 metadata reply (estimated_time 389, layer_count 82, object_height 9.96). */
    private val e3Metadata = """
        {
          "slicer": "OrcaSlicer",
          "slicer_version": "2.3",
          "estimated_time": 389,
          "filament_total": 241.26,
          "layer_count": 82,
          "layer_height": 0.12,
          "first_layer_height": 0.24,
          "object_height": 9.96,
          "thumbnails": [
            { "width": 32,  "height": 32,  "size": 900,   "relative_path": ".thumbs/plate-32x32.png" },
            { "width": 48,  "height": 48,  "size": 1800,  "relative_path": ".thumbs/plate-48x48.png" },
            { "width": 300, "height": 300, "size": 30000, "relative_path": ".thumbs/plate-300x300.png" }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesFaithfulE5Sample_objectHeightNull() {
        val md = parse(e5Metadata)
        assertEquals(50, md.layerCount)
        assertEquals(2191.0, md.estimatedTime!!, 0.0)
        assertEquals(5749.86, md.filamentTotal!!, 0.0001)
        assertNull("E5 sample omits object_height → null, never fabricated", md.objectHeight)
        assertTrue(md.largestThumbRelPath!!.endsWith("-300x300.png"))
    }

    @Test
    fun parsesFaithfulE3Sample_objectHeightPresent() {
        val md = parse(e3Metadata)
        assertEquals(82, md.layerCount)
        assertEquals(389.0, md.estimatedTime!!, 0.0)
        assertEquals(9.96, md.objectHeight!!, 0.0001)
        assertTrue(md.largestThumbRelPath!!.endsWith("-300x300.png"))
    }

    @Test
    fun emptyObject_allFieldsNull() {
        val md = parse("{}")
        assertNull(md.layerCount)
        assertNull(md.objectHeight)
        assertNull(md.estimatedTime)
        assertNull(md.largestThumbRelPath)
    }

    @Test
    fun thumbnailsOnly32And48_largestIs48_no300Fabricated() {
        val raw = """
            {
              "layer_count": 10,
              "thumbnails": [
                { "width": 32, "height": 32, "size": 900,  "relative_path": ".thumbs/x-32x32.png" },
                { "width": 48, "height": 48, "size": 1800, "relative_path": ".thumbs/x-48x48.png" }
              ]
            }
        """.trimIndent()
        val md = parse(raw)
        assertEquals(".thumbs/x-48x48.png", md.largestThumbRelPath)
    }

    @Test
    fun thumbnailUrl_subdirectory() {
        val url = thumbnailUrl("http://printer:7125", "miata/plate.gcode", ".thumbs/plate-300x300.png")
        assertEquals(
            "http://printer:7125/server/files/gcodes/miata/.thumbs/plate-300x300.png",
            url,
        )
    }

    @Test
    fun thumbnailUrl_rootFile_noLeadingSlashBeforeThumbs() {
        val url = thumbnailUrl("http://printer:7125", "plate.gcode", ".thumbs/plate-300x300.png")
        assertEquals(
            "http://printer:7125/server/files/gcodes/.thumbs/plate-300x300.png",
            url,
        )
    }

    @Test
    fun thumbnailUrl_spaceIsPercentEncoded() {
        val url = thumbnailUrl(
            "http://printer:7125",
            "my prints/cool benchy.gcode",
            ".thumbs/cool benchy-300x300.png",
        )
        assertEquals(
            "http://printer:7125/server/files/gcodes/my%20prints/.thumbs/cool%20benchy-300x300.png",
            url,
        )
    }

    @Test
    fun selectedFilePreviewParsesRichMetadata() {
        val preview = parseFilePreviewMetadata("folder/cube.gcode", MoonrakerJson.parseToJsonElement(e5Metadata).jsonObject)

        assertEquals("folder/cube.gcode", preview.filename)
        assertEquals(1234567L, preview.sizeBytes)
        assertEquals(1700000000.0, preview.modifiedEpochSeconds!!, 0.0001)
        assertEquals(2191.0, preview.estimatedTime!!, 0.0001)
        assertEquals(5749.86, preview.filamentTotal!!, 0.0001)
        assertEquals(17.15, preview.filamentWeightTotal!!, 0.0001)
        assertEquals(50, preview.layerCount)
        assertNull(preview.objectHeight)
        assertEquals(".thumbs/benchy-300x300.png", preview.largestThumbRelPath)
        assertEquals(
            "http://printer:7125/server/files/gcodes/folder/.thumbs/benchy-300x300.png",
            preview.thumbnailUrl("http://printer:7125"),
        )
    }

    @Test
    fun selectedFilePreviewDegradesForMissingThumbnailsAndBadNumbers() {
        val preview = parseFilePreviewMetadata(
            "bad data.gcode",
            MoonrakerJson.parseToJsonElement(
                """{
                  "estimated_time": "bad",
                  "filament_total": "bad",
                  "filament_weight_total": "bad",
                  "layer_count": "bad",
                  "object_height": "bad",
                  "size": "bad",
                  "modified": "bad",
                  "thumbnails": []
                }""",
            ).jsonObject,
        )

        assertEquals("bad data.gcode", preview.filename)
        assertNull(preview.sizeBytes)
        assertNull(preview.modifiedEpochSeconds)
        assertNull(preview.estimatedTime)
        assertNull(preview.filamentTotal)
        assertNull(preview.filamentWeightTotal)
        assertNull(preview.layerCount)
        assertNull(preview.objectHeight)
        assertNull(preview.largestThumbRelPath)
        assertNull(preview.thumbnailUrl("http://printer:7125"))
    }

    @Test
    fun selectedFilePreviewThumbnailUrlHandlesSpaces() {
        val preview = parseFilePreviewMetadata(
            "my prints/cool benchy.gcode",
            MoonrakerJson.parseToJsonElement(
                """{
                  "thumbnails": [
                    { "width": 300, "relative_path": ".thumbs/cool benchy-300x300.png" }
                  ]
                }""",
            ).jsonObject,
        )

        assertEquals(
            "http://printer:7125/server/files/gcodes/my%20prints/.thumbs/cool%20benchy-300x300.png",
            preview.thumbnailUrl("http://printer:7125"),
        )
    }
}
