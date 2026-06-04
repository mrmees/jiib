package works.mees.dinghy.state

import org.junit.Assert.fail
import org.junit.Test

/**
 * RED scaffold (SPOOL-07) — extended `FilePreviewMetadata` filament-array lift.
 *
 * Wave-0 compile-safety: pure `fail()` bodies, no reference to the not-yet-added
 * `FilePreviewMetadata.filamentType/Name/Colors/Weights` fields (added in Wave 1 by extending
 * `state/PrintMetadata.kt`'s null-safe `parseFilePreviewMetadata` walk). A sibling green suite
 * (`PrintMetadataParseTest`) already covers the existing fields and must stay green.
 *
 * Target assertions (Wave 1 turns these green) — a multi-material slicer metadata reply carrying
 * `filament_type[]` / `filament_name[]` / `filament_colors[]` (`#hex`) / `filament_weights[]`:
 *  - each array lifts via the existing `runCatching{...}.getOrNull().orEmpty()` walk
 *  - a missing array degrades to an empty list (never throws), mirroring `largestThumbRelPath`
 */
class FilePreviewMetadataTest {

    @Test
    fun liftsFilamentTypeNameColorsWeightsArrays() {
        fail("RED: FilePreviewMetadata filament_type[]/name[]/colors[]/weights[] lift not yet implemented")
    }

    @Test
    fun missingFilamentArraysDegradeToEmptyLists() {
        fail("RED: missing filament arrays -> empty lists (null-safe) not yet implemented")
    }
}
