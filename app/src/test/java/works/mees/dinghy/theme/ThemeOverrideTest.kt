package works.mees.dinghy.theme

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import works.mees.dinghy.config.MoonrakerDiscovery
import works.mees.dinghy.di.AppContainer

/**
 * 15.2-01 Task 3 — host-pure proof of the transient theme-override LAYER on [AppContainer.effectiveTokens].
 * No active profile in these tests (the fake profile store is empty), so the canonical [activeThemeTuple]
 * resolves to the GLOBAL idle [ThemePrefs.tupleFlow] over a controllable in-memory DataStore. Covers
 * HIGH-1 (single canonical tuple + PURE effectiveTokens, no staleness), HIGH-3 (full-tuple passthrough),
 * HIGH-5 (disable clears/ignores), and MEDIUM atomic stepping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeOverrideTest {

    /** A controllable in-memory DataStore — `updateData` mutates the held snapshot and re-emits. */
    private class MemDataStore(seed: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(seed)
        override val data: Flow<Preferences> = state
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val next = transform(state.value)
            state.value = next
            return next
        }

        fun set(prefs: Preferences) {
            state.value = prefs
        }
    }

    private fun lazyDiscovery() = MoonrakerDiscovery(
        nsdProvider = { error("nsd must not be acquired in host tests") },
        multicastLockProvider = { error("multicast lock must not be acquired in host tests") },
    )

    /** Container over controllable theme prefs + empty everything-else; theme is the only store we drive. */
    private fun newContainer(themeStore: MemDataStore): AppContainer =
        AppContainer(
            themeStore,
            // connection, macro, webcam, profile, BABYSTEP (Phase 16, 6th store),
            // TRACESTYLE (Phase 26, 7th store), DISPLAY (Phase 26.5 §R2, 8th store),
            // SAVEDLOCATIONS (feat/move-hub-redesign, 9th store),
            // FONTSCALE (App/Printer Settings Split, 10th store),
            // EXTRUDE_MACROS (Extrude rework Task 4, 11th store), then discovery (last).
            MemDataStore(), MemDataStore(), MemDataStore(), MemDataStore(), MemDataStore(),
            MemDataStore(), MemDataStore(), MemDataStore(), MemDataStore(), MemDataStore(),
            lazyDiscovery(),
        )

    /** A theme-prefs snapshot carrying a specific seed (everything else default via sanitizeTuple). */
    private fun prefsWithSeed(seedHex: String): Preferences =
        mutablePreferencesOf(stringPreferencesKey("theme_seed") to seedHex)

    @Test
    fun override_null_passes_through_persisted() = runTest {
        val store = MemDataStore()
        val container = newContainer(store)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        // No override → effectiveTokens is a fresh bake of the canonical (idle/default) tuple.
        val expected = container.themeResolver.bake(container.activeThemeTuple.first())
        assertEquals(expected, emissions.last())
        job.cancel()
    }

    @Test
    fun override_nonnull_applies_baked_tokens() = runTest {
        val store = MemDataStore() // default dark tuple
        val container = newContainer(store)
        container.setDevCyclerEnabled(true)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        val darkBg = emissions.last().bg

        // Override flips to light (+ Simple) over the SAME real seed — the bg must flip.
        container.setThemeOverride(ThemeOverride(dark = false, paletteMode = "Simple"))
        runCurrent()
        assertNotEquals("override dark=false must flip bg from the dark baseline", darkBg, emissions.last().bg)
        job.cancel()
    }

    @Test
    fun override_idle_no_profile_bakes_from_themeprefs_tuple() = runTest {
        // A NON-default idle seed in the global ThemePrefs.
        val customSeed = "#c8b400"
        val store = MemDataStore(prefsWithSeed(customSeed))
        val container = newContainer(store)
        container.setDevCyclerEnabled(true)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()

        // A dark-only override must bake from the IDLE ThemePrefs tuple (the custom seed), not a default.
        container.setThemeOverride(ThemeOverride(dark = true))
        runCurrent()
        val expected = container.themeResolver.bake(
            ThemeOverride(dark = true).mergeOnto(container.activeThemeTuple.first()),
        )
        assertEquals("override must bake from the global idle tuple (custom seed)", expected, emissions.last())
        // And that tuple really carries the custom seed (not the default).
        assertEquals(customSeed, container.activeThemeTuple.first().seedHex)
        job.cancel()
    }

    @Test
    fun persisted_change_propagates_through_effectivetokens() = runTest {
        // HIGH-1: with NO override, changing the canonical tuple must RE-EMIT a freshly baked token set —
        // proving the non-override branch is a pure bake of the combine input, never a stale resolver read.
        val store = MemDataStore() // default seed
        val container = newContainer(store)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        val defaultBg = emissions.last().bg

        // Change the persisted idle theme to light → the canonical tuple changes → effectiveTokens re-emits.
        store.set(
            mutablePreferencesOf(
                androidx.datastore.preferences.core.booleanPreferencesKey("theme_dark") to false,
            ),
        )
        runCurrent()
        assertNotEquals("a persisted theme change must propagate (no staleness)", defaultBg, emissions.last().bg)
        assertEquals(
            "the re-emit is a fresh bake of the new canonical tuple",
            container.themeResolver.bake(container.activeThemeTuple.first()),
            emissions.last(),
        )
        job.cancel()
    }

    @Test
    fun override_carries_full_tuple_status_override_survives_round_trip() = runTest {
        // A base tuple with a non-empty statusOverride + poolOverride + non-default shift.
        val store = MemDataStore(
            mutablePreferencesOf(
                stringPreferencesKey("theme_seed") to "#3f78ff",
                androidx.datastore.preferences.core.intPreferencesKey("theme_shift") to 40,
                androidx.datastore.preferences.core.stringSetPreferencesKey("pool_override_keys") to setOf("1", "stop"),
                androidx.datastore.preferences.core.longPreferencesKey("pool_override_argb_1") to 0xFF00FFAAL,
                androidx.datastore.preferences.core.longPreferencesKey("pool_override_argb_stop") to 0xFF112233L,
            ),
        )
        val container = newContainer(store)
        container.setDevCyclerEnabled(true)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        val original = emissions.last()
        val baseTuple = container.activeThemeTuple.first()
        // sanity: the base really carries the status + pool overrides + shift.
        assertEquals(40, baseTuple.poolShift)
        assertEquals(setOf("stop"), baseTuple.statusOverrides.keys)
        assertEquals(setOf(1), baseTuple.poolOverrides.keys)

        // A style-only override (dark flip). mergeOnto must keep status/pool/shift verbatim.
        val merged = ThemeOverride(dark = !baseTuple.dark).mergeOnto(baseTuple)
        assertEquals("status override survives merge", baseTuple.statusOverrides, merged.statusOverrides)
        assertEquals("pool override survives merge", baseTuple.poolOverrides, merged.poolOverrides)
        assertEquals("poolShift survives merge", baseTuple.poolShift, merged.poolShift)
        assertEquals("seed survives merge", baseTuple.seedHex, merged.seedHex)

        // Clear → restored tokens are byte-identical to the original (no dropped field).
        container.setThemeOverride(ThemeOverride(dark = !baseTuple.dark))
        runCurrent()
        container.setThemeOverride(null)
        runCurrent()
        assertEquals("clearing restores the original baked tokens exactly", original, emissions.last())
        job.cancel()
    }

    @Test
    fun update_theme_override_steps_atomically() {
        val container = newContainer(MemDataStore())
        // Two successive transforms: the second must SEE the first's output (live state, not a snapshot).
        container.updateThemeOverride { (it ?: ThemeOverride()).copy(dark = false) }
        container.updateThemeOverride { (it ?: ThemeOverride()).copy(paletteMode = "Simple") }
        val o = container.themeOverride.value
        assertEquals(false, o?.dark)            // first step preserved
        assertEquals("Simple", o?.paletteMode)  // second step applied on top
    }

    @Test
    fun override_writes_no_persistence() = runTest {
        val store = MemDataStore()
        val container = newContainer(store)
        val before = store.data.first()

        container.setThemeOverride(ThemeOverride(dark = false))
        container.updateThemeOverride { (it ?: ThemeOverride()).copy(paletteMode = "Simple") }
        container.setThemeOverride(null)
        runCurrent()

        // The override path touched NOTHING in the persisted store.
        assertEquals("override apply/clear must write zero to DataStore", before, store.data.first())
    }

    @Test
    fun dismiss_restores_persisted_no_drift() = runTest {
        val store = MemDataStore(prefsWithSeed("#c8b400"))
        val container = newContainer(store)
        container.setDevCyclerEnabled(true)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        val persisted = emissions.last()

        container.setThemeOverride(ThemeOverride(dark = false, paletteMode = "Simple"))
        runCurrent()
        container.setThemeOverride(null)
        runCurrent()
        assertEquals("clear snaps back to the persisted bake, no drift", persisted, emissions.last())
        assertEquals(
            persisted,
            container.themeResolver.bake(container.activeThemeTuple.first()),
        )
        job.cancel()
    }

    @Test
    fun disable_dev_clears_or_ignores_override() = runTest {
        val store = MemDataStore()
        val container = newContainer(store)
        container.setDevCyclerEnabled(true)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            container.effectiveTokens.collect { emissions += it }
        }
        runCurrent()
        val persisted = container.themeResolver.bake(container.activeThemeTuple.first())

        // Apply an override (visible while dev on).
        container.setThemeOverride(ThemeOverride(dark = false, paletteMode = "Simple"))
        runCurrent()
        assertNotEquals(persisted.bg, emissions.last().bg)

        // Disable dev → effectiveTokens must show the persisted look again (override cleared/ignored).
        container.setDevCyclerEnabled(false)
        runCurrent()
        assertEquals("disabling dev must un-strand the override", persisted, emissions.last())
        job.cancel()
    }
}
