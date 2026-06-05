package works.mees.dinghy.di

import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.config.ConnectionConfig
import works.mees.dinghy.config.Profile

/**
 * Wave-0 scaffold for the AppContainer active-config derivation (MULTI-01, RESEARCH Pattern 2).
 *
 * The real `AppContainer.activeConfig` does NOT exist yet (it lands in plan 02). So this test builds the
 * SAME pipeline LOCALLY from primitives that exist NOW — `combine(profiles, activeId)` → pick-by-id →
 * `.map { it?.toConnectionConfig() }` → `.distinctUntilChanged()` — and asserts the load-bearing
 * contract (Pitfall 1): two [Profile]s differing ONLY in name/theme produce a SINGLE distinct
 * [ConnectionConfig], so editing the active profile's name/theme must NOT churn the spine.
 *
 * Keep the pipeline shape identical to RESEARCH Pattern 2 so plan 02 can lift it into the container.
 */
class ActiveConfigDerivationTest {

    /** The exact RESEARCH-Pattern-2 derivation, expressed over local flows (no container dependency). */
    private fun activeConfig(
        profiles: List<List<Profile>>,
        activeId: String?,
    ) = combine(flowOf(*profiles.toTypedArray()), flowOf(activeId)) { list, id ->
        list.firstOrNull { it.id == id }
    }
        .map { it?.toConnectionConfig() }
        .distinctUntilChanged()

    @Test
    fun nameOrThemeEditDoesNotChurnConfig() = runTest {
        // Two emissions of the active profile that differ ONLY in name + theme — same host/port/key.
        val v1 = Profile(id = "a", name = "Ender 5 Plus", host = "192.168.1.120", port = 7125, seedHex = "#3f78ff")
        val v2 = v1.copy(name = "Big Printer", seedHex = "#8b5cf6")

        val emitted = activeConfig(profiles = listOf(listOf(v1), listOf(v2)), activeId = "a").toList()

        // distinctUntilChanged collapses the name/theme-only edit to ONE ConnectionConfig (no rebind).
        assertEquals(1, emitted.size)
        assertEquals(ConnectionConfig("192.168.1.120", 7125, null), emitted.single())
    }

    @Test
    fun hostOrPortEditEmitsADistinctConfig() = runTest {
        val v1 = Profile(id = "a", host = "192.168.1.120", port = 7125)
        val v2 = v1.copy(host = "192.168.1.121") // a real connection change → must re-emit

        val emitted = activeConfig(profiles = listOf(listOf(v1), listOf(v2)), activeId = "a").toList()

        assertEquals(2, emitted.size)
        assertEquals(ConnectionConfig("192.168.1.120", 7125, null), emitted[0])
        assertEquals(ConnectionConfig("192.168.1.121", 7125, null), emitted[1])
    }

    @Test
    fun danglingActiveIdResolvesToNull() = runTest {
        val p = Profile(id = "a", host = "192.168.1.120")
        // active id points at a profile that isn't in the set → null (→ Connect prompt, D-11/D-12).
        val emitted = activeConfig(profiles = listOf(listOf(p)), activeId = "gone").toList()
        assertEquals(listOf<ConnectionConfig?>(null), emitted)
    }
}
