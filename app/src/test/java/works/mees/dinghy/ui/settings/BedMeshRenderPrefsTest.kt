package works.mees.dinghy.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import works.mees.dinghy.calibration.BedMeshViewType

/**
 * Host-side proof of the [BedMeshRenderPrefs] persistence contract (the 14th DataStore).
 *
 * HOST CONSTRAINT (see MacroPrefsTest / ConnectionStoreTest): back-to-back DataStore writes to the
 * same file are NOT reliably testable on the Windows build host (atomic .tmp → final rename races on
 * Windows when the target already exists, since File.renameTo fails over existing files unlike POSIX).
 * Each test therefore uses either a single write path or a fresh store per write so each atomic rename
 * is to a non-existent target. Multi-write atomicity is verified on-device.
 */
class BedMeshRenderPrefsTest {
    @get:Rule val tmp = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()

    @After fun tearDown() { scopes.forEach { it.cancel() } }

    /** Fresh store per call — uses File(root, name) not tmp.newFile() which pre-creates the file.
     *  DataStore's atomic rename (.tmp → target) needs a non-existent target on Windows. */
    private fun store(): DataStore<Preferences> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO).also { scopes += it }
        val file = java.io.File(tmp.root, "bm_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope) { file }
    }

    @Test fun defaultsWhenUnset() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerA").first())
        assertEquals(0, p.highColorSel("printerA").first())  // accent (index 0 in bedMeshPoolColors)
        assertEquals(4, p.lowColorSel("printerA").first())   // pool[0] (index 4 in bedMeshPoolColors)
    }

    @Test fun viewType_roundTrips_scopedByProfileId() = runBlocking {
        // First store: write viewType for printerA, verify; printerB still reads default.
        val p = BedMeshRenderPrefs(store())
        p.setViewType("printerA", BedMeshViewType.PROBE_POINTS)
        assertEquals(BedMeshViewType.PROBE_POINTS, p.viewType("printerA").first())
        assertEquals(BedMeshViewType.HEATMAP, p.viewType("printerB").first())
    }

    @Test fun highColorSel_roundTrips() = runBlocking {
        val p = BedMeshRenderPrefs(store())
        p.setHighColorSel("printerA", 2)
        assertEquals(2, p.highColorSel("printerA").first())
    }

    @Test fun viewType_roundTrips_allThreeValues() = runBlocking {
        // Each value gets its own store so DataStore's atomic rename (.tmp → file) never hits a
        // pre-existing target (Windows renameTo fails over existing files; first write is always safe).
        for (v in BedMeshViewType.entries) {
            val p = BedMeshRenderPrefs(store())
            p.setViewType("printerA", v)
            assertEquals(v, p.viewType("printerA").first())
        }
    }
}
