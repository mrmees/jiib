package works.mees.jiib.state

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Extended `FilePreviewMetadata` filament-array lift (SPOOL-07).
 *
 * A multi-material slicer metadata reply carrying `filament_type[]` / `filament_name[]` /
 * `filament_colors[]` (`#hex`) / `filament_weights[]`:
 *  - each array lifts via the existing `runCatching{...}.getOrNull().orEmpty()` walk
 *  - a missing array degrades to an empty list (never throws), mirroring `largestThumbRelPath`
 *  - the existing filament_total / filament_weight_total parsing is unchanged
 */
class FilePreviewMetadataTest {

    @Test
    fun liftsFilamentTypeNameColorsWeightsArrays() {
        val result: JsonObject = buildJsonObject {
            put("filament_total", 1234.5)
            put("filament_weight_total", 67.8)
            put("filament_type", buildJsonArray { add("PLA"); add("PETG") })
            put("filament_name", buildJsonArray { add("Olive Green"); add("Black") })
            put("filament_colors", buildJsonArray { add("#64794B"); add("#000000") })
            put("filament_weights", buildJsonArray { add(40.0); add(27.8) })
        }

        val meta = parseFilePreviewMetadata("multi.gcode", result)

        assertEquals(listOf("PLA", "PETG"), meta.filamentType)
        assertEquals(listOf("Olive Green", "Black"), meta.filamentName)
        assertEquals(listOf("#64794B", "#000000"), meta.filamentColors)
        assertEquals(listOf(40.0, 27.8), meta.filamentWeights)

        // Existing fields unchanged.
        assertEquals(1234.5, meta.filamentTotal!!, 0.0001)
        assertEquals(67.8, meta.filamentWeightTotal!!, 0.0001)
    }

    @Test
    fun missingFilamentArraysDegradeToEmptyLists() {
        // No filament arrays at all → all four empty, never throws.
        val empty = parseFilePreviewMetadata("plain.gcode", buildJsonObject { })
        assertTrue(empty.filamentType.isEmpty())
        assertTrue(empty.filamentName.isEmpty())
        assertTrue(empty.filamentColors.isEmpty())
        assertTrue(empty.filamentWeights.isEmpty())

        // A non-array / garbage value for the keys also degrades to empty (no throw).
        val garbage = buildJsonObject {
            put("filament_type", "not-an-array")
            put("filament_weights", buildJsonObject { put("x", 1) })
        }
        val meta = parseFilePreviewMetadata("garbage.gcode", garbage)
        assertTrue(meta.filamentType.isEmpty())
        assertTrue(meta.filamentWeights.isEmpty())
    }
}
