package works.mees.jiib.ui.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Host-side proof of the [TraceStylePrefs] PROFILE-SCOPED selection/persistence contract (per-printer
 * trace config — pre-merge review fix 2026-06-14).
 *
 * Mirrors the [BabystepPrefsTest] / [DisplayPrefsTest] harness:
 *   - Single-write tests use the real temp-file DataStore (proves the flat-key encoding).
 *   - Multi-write tests use an in-memory DataStore to sidestep the Windows-host back-to-back
 *     rename race (`File.renameTo` fails if destination exists; see [DisplayPrefsTest] comment).
 *
 * Contract covered:
 *   - absent key == not selected (sparse store), per profile
 *   - `setSensorSelected(profileId, name, true/false)` add/remove round-trips
 *   - selection is ISOLATED per profile id (no cross-printer bleed)
 *   - `migrateUnscopedTo` copies legacy unscoped keys into a profile, removes them, and is idempotent
 */
class TraceStylePrefsSelectionTest {

    private val tmpFiles = mutableListOf<File>()
    private val scopes = mutableListOf<CoroutineScope>()

    private val pidA = "printer-A"
    private val pidB = "printer-B"

    @After
    fun tearDown() {
        scopes.forEach { it.cancel() }
        tmpFiles.forEach { it.delete() }
    }

    /** Verbatim BabystepPrefsTest harness — fresh temp `.preferences_pb` on a process-lifetime IO scope. */
    private fun newDataStore(): DataStore<Preferences> {
        val file = File.createTempFile("tracesel_test_", ".preferences_pb").also {
            it.delete()
            tmpFiles += it
        }
        val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        scopes += ioScope
        return PreferenceDataStoreFactory.create(scope = ioScope) { file }
    }

    /** In-memory DataStore (verbatim DisplayPrefsTest MemDataStore shape) for multi-write tests. */
    private fun memDataStore(): DataStore<Preferences> {
        val state = MutableStateFlow<Preferences>(emptyPreferences())
        return object : DataStore<Preferences> {
            override val data: Flow<Preferences> = state
            override suspend fun updateData(
                transform: suspend (t: Preferences) -> Preferences,
            ): Preferences {
                val next = transform(state.value)
                state.value = next
                return next
            }
        }
    }

    @Test
    fun emptyStore_selectedSensorsIsEmpty() = runBlocking {
        val prefs = TraceStylePrefs(newDataStore())
        assertTrue("empty by default", prefs.selectedSensors(pidA).first().isEmpty())
    }

    @Test
    fun setSensorSelected_true_roundTrips() = runBlocking {
        // Single write — real file DataStore proves key encoding on disk.
        val prefs = TraceStylePrefs(newDataStore())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())
    }

    @Test
    fun setSensorSelected_false_removesKey() = runBlocking {
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", false)
        assertTrue("deselect removes the key", prefs.selectedSensors(pidA).first().isEmpty())
    }

    @Test
    fun multipleSelectionsAccumulate() = runBlocking {
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", true)
        prefs.setSensorSelected(pidA, "temperature_sensor mcu", true)
        assertEquals(
            setOf("temperature_sensor chamber", "temperature_sensor mcu"),
            prefs.selectedSensors(pidA).first(),
        )
    }

    @Test
    fun selection_isIsolatedPerProfile() = runBlocking {
        // The core cross-printer fix: a selection under profile A must NOT appear under profile B,
        // even though both share the raw sensor object name.
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", true)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())
        assertTrue("profile B is unaffected", prefs.selectedSensors(pidB).first().isEmpty())
    }

    @Test
    fun colorAndVisibility_areIsolatedPerProfile() = runBlocking {
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setTraceColor(pidA, "extruder", 0xFFFF0000.toInt())
        prefs.setTraceVisibility(pidA, "heater_bed", false)
        assertEquals(mapOf("extruder" to 0xFFFF0000.toInt()), prefs.traceColors(pidA).first())
        assertEquals(mapOf("heater_bed" to false), prefs.traceVisibility(pidA).first())
        assertTrue("profile B has no color", prefs.traceColors(pidB).first().isEmpty())
        assertTrue("profile B has no visibility override", prefs.traceVisibility(pidB).first().isEmpty())
    }

    @Test
    fun applyDefaultSensorSelection_brandNewProfile_selectsAllAvailable() = runBlocking {
        // Design A (2026-07-05): a printer's first visit defaults EVERY available temperature_sensor to
        // selected so the monitoring page shows all thermometers out of the box.
        val prefs = TraceStylePrefs(memDataStore())
        val available = listOf("temperature_sensor chamber", "temperature_sensor mcu")
        prefs.applyDefaultSensorSelection(pidA, available)
        assertEquals(available.toSet(), prefs.selectedSensors(pidA).first())
    }

    @Test
    fun applyDefaultSensorSelection_isIdempotent_respectsLaterDeselect() = runBlocking {
        // Once defaulted, the per-profile sentinel stops re-defaulting — a later deselect (and even a
        // deselect-to-none) is honored across reconnects; the default must never claw a sensor back.
        val prefs = TraceStylePrefs(memDataStore())
        val available = listOf("temperature_sensor chamber", "temperature_sensor mcu")
        prefs.applyDefaultSensorSelection(pidA, available)
        prefs.setSensorSelected(pidA, "temperature_sensor mcu", false)
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())

        prefs.applyDefaultSensorSelection(pidA, available) // e.g. a reconnect re-runs the default pass
        assertEquals(
            "sentinel stops re-defaulting; the deselected sensor stays off",
            setOf("temperature_sensor chamber"),
            prefs.selectedSensors(pidA).first(),
        )
    }

    @Test
    fun applyDefaultSensorSelection_preservesExistingOptInSelection() = runBlocking {
        // An install configured under the OLD opt-in default (one sensor explicitly selected) must be
        // PRESERVED — the default pass sees existing selection keys and neither clears nor force-adds.
        val prefs = TraceStylePrefs(memDataStore())
        prefs.setSensorSelected(pidA, "temperature_sensor chamber", true)
        val available =
            listOf("temperature_sensor chamber", "temperature_sensor mcu", "temperature_sensor exhaust")
        prefs.applyDefaultSensorSelection(pidA, available)
        assertEquals(
            "existing selection preserved, the other available sensors not force-added",
            setOf("temperature_sensor chamber"),
            prefs.selectedSensors(pidA).first(),
        )
    }

    @Test
    fun migrateUnscopedTo_copiesLegacyKeysToProfile_removesLegacy_idempotent() = runBlocking {
        val ds = memDataStore()
        // Seed LEGACY unscoped keys directly (the pre-scoping on-disk shape).
        ds.edit { p ->
            p[booleanPreferencesKey("trace_selected_temperature_sensor chamber")] = true
            p[intPreferencesKey("trace_color_extruder")] = 0xFF00FF00.toInt()
            p[booleanPreferencesKey("trace_visible_heater_bed")] = false
        }
        val prefs = TraceStylePrefs(ds)

        prefs.migrateUnscopedTo(pidA)

        // Legacy data now reads back under the profile scope.
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())
        assertEquals(mapOf("extruder" to 0xFF00FF00.toInt()), prefs.traceColors(pidA).first())
        assertEquals(mapOf("heater_bed" to false), prefs.traceVisibility(pidA).first())

        // Legacy raw keys are gone.
        val rawKeys = ds.data.first().asMap().keys.map { it.name }
        assertFalse("legacy selected key removed", rawKeys.contains("trace_selected_temperature_sensor chamber"))
        assertFalse("legacy color key removed", rawKeys.contains("trace_color_extruder"))
        assertFalse("legacy visible key removed", rawKeys.contains("trace_visible_heater_bed"))

        // Idempotent: a second call (even for a DIFFERENT profile) no-ops — profile B stays empty.
        prefs.migrateUnscopedTo(pidB)
        assertTrue("second migrate is a no-op", prefs.selectedSensors(pidB).first().isEmpty())
        // ...and profile A's migrated data is untouched.
        assertEquals(setOf("temperature_sensor chamber"), prefs.selectedSensors(pidA).first())
    }

    @Test
    fun migrate_leavesAlreadyScopedKeysIntact_noDoubleScope() = runBlocking {
        // Codex re-review race: a SCOPED write for pidA landed before the sentinel was set, alongside a
        // legacy unscoped key. Migration must migrate the legacy one but leave the scoped one untouched
        // (NOT re-scope it to trace_color_<pid>_<pid>_extruder and drop the correct key).
        val ds = memDataStore()
        ds.edit { p ->
            p[intPreferencesKey(TraceStylePrefs.colorKey(pidA, "extruder"))] = 0xFF112233.toInt() // scoped
            p[intPreferencesKey("trace_color_heater_bed")] = 0xFF445566.toInt()                   // legacy
        }
        val prefs = TraceStylePrefs(ds)
        prefs.migrateUnscopedTo(pidA)

        assertEquals(
            mapOf("extruder" to 0xFF112233.toInt(), "heater_bed" to 0xFF445566.toInt()),
            prefs.traceColors(pidA).first(),
        )
        val rawKeys = ds.data.first().asMap().keys.map { it.name }
        assertFalse(
            "no double-scoped key produced",
            rawKeys.any { it.startsWith("trace_color_" + pidA + "_" + pidA + "_") },
        )
    }
}
