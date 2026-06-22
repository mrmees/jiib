package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.calibration.BedMeshViewType

class BedMeshRenderPrefsTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun store(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = TestScope()) { tmp.newFile("bm_${System.nanoTime()}.preferences_pb") }

    @Test fun defaultsWhenUnset() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerA").first())
        assertEquals(-1, p.highColorSel("printerA").first()) // accent sentinel
        assertEquals(0, p.lowColorSel("printerA").first())   // pool slot 0
    }

    @Test fun roundTripsScopedByProfileId() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        p.setViewType("printerA", BedMeshViewType.PROBE_POINTS)
        p.setHighColorSel("printerA", 2)
        assertEquals(BedMeshViewType.PROBE_POINTS, p.viewType("printerA").first())
        assertEquals(2, p.highColorSel("printerA").first())
        // Different printer is unaffected (independent scope).
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerB").first())
    }
}
