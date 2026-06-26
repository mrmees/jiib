package works.mees.jiib.ui.calibration

import org.junit.Assert.assertEquals
import org.junit.Test

class BedMeshEditStateTest {
    private fun c(active: String, empty: Boolean, sel: String?, saved: Set<String>) =
        classifyMeshEdit(active, empty, sel, saved)

    @Test fun freshCalibrateDefaultIsActiveUnsaved() =
        assertEquals(MeshEditKind.ACTIVE_UNSAVED, c("default", false, null, setOf("default")))

    @Test fun noMeshLoadedIsActiveUnsaved() =
        assertEquals(MeshEditKind.ACTIVE_UNSAVED, c("", true, null, emptySet()))

    @Test fun viewingActiveSavedProfile() =
        assertEquals(MeshEditKind.ACTIVE_SAVED, c("cold", false, "cold", setOf("cold")))

    @Test fun viewingActiveSavedWithNoSelectionDefaultsToActive() =
        assertEquals(MeshEditKind.ACTIVE_SAVED, c("cold", false, null, setOf("cold")))

    @Test fun previewingNonActiveProfile() =
        assertEquals(MeshEditKind.PREVIEW_NONACTIVE, c("cold", false, "hot", setOf("cold", "hot")))
}
