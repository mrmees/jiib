package works.mees.jiib.config

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class IncrementListPrefsTest {

    /** In-memory DataStore for multi-write tests (sidesteps the Windows .tmp→rename race). */
    private fun newMemPrefs(): IncrementListPrefs {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        val mem = object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
                val next = transform(state.value); state.value = next; return next
            }
        }
        return IncrementListPrefs(mem)
    }

    @Test fun `absent key reads empty`() = runTest {
        assertEquals(emptyMap<String, String>(), newMemPrefs().lists("p1").first())
    }

    @Test fun `setList round-trips`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "move_microstep", "0.1,1,10")
        assertEquals(mapOf("move_microstep" to "0.1,1,10"), prefs.lists("p1").first())
    }

    @Test fun `setList overwrites only its key`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "SPEED", "1,5,10")
        prefs.setList("p1", "SPEED", "2,4,6")
        prefs.setList("p1", "FLOW", "1,5,10")
        assertEquals(mapOf("SPEED" to "2,4,6", "FLOW" to "1,5,10"), prefs.lists("p1").first())
    }

    @Test fun `profiles are isolated`() = runTest {
        val prefs = newMemPrefs()
        prefs.setList("p1", "SPEED", "1,5,10")
        prefs.setList("p2", "SPEED", "9,9,9")
        assertEquals(mapOf("SPEED" to "1,5,10"), prefs.lists("p1").first())
        assertEquals(mapOf("SPEED" to "9,9,9"), prefs.lists("p2").first())
    }

    @Test fun `seedIfEmpty only seeds when empty`() = runTest {
        val prefs = newMemPrefs()
        prefs.seedIfEmpty("p1", mapOf("SPEED" to "1,5,10"))
        prefs.setList("p1", "SPEED", "2,4,6")
        prefs.seedIfEmpty("p1", mapOf("SPEED" to "1,5,10")) // must NOT overwrite
        assertEquals("2,4,6", prefs.lists("p1").first()["SPEED"])
    }
}
