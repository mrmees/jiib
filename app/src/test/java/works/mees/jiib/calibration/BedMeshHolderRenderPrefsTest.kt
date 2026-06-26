package works.mees.jiib.calibration

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.state.PrinterStateStore
import works.mees.jiib.ui.settings.BedMeshRenderPrefs

class BedMeshHolderRenderPrefsTest {

    /** In-memory DataStore (sidesteps the Windows .tmp→rename race, works under runTest virtual time). */
    private fun memPrefs(): BedMeshRenderPrefs {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val mem = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
        return BedMeshRenderPrefs(mem)
    }

    @Test fun vmReflectsPersistedViewTypeForActiveProfile() = runTest {
        val prefs = memPrefs()
        prefs.setViewType("printerA", BedMeshViewType.PROBE_POINTS)
        prefs.setHighColorSel("printerA", 3)
        val pid = MutableStateFlow<String?>("printerA")
        val holder = BedMeshHolder(
            scope = backgroundScope,
            store = PrinterStateStore(backgroundScope), // ctor REQUIRES scope (see BedMeshHolderTest.kt)
            events = null,
            renderPrefs = prefs,
            activeProfileId = pid,
        )
        val vm = holder.vm.first { it.viewType == BedMeshViewType.PROBE_POINTS } // public flow is `vm`
        assertEquals(BedMeshViewType.PROBE_POINTS, vm.viewType)
        assertEquals(3, vm.highColorSel)
    }
}
