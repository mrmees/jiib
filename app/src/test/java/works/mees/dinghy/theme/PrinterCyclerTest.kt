package works.mees.dinghy.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.dinghy.ui.shell.nextProfileId

/**
 * Host-pure proof of the printer-switcher dev-cycler stepping logic (15.2-04 finding 2). The stepper
 * advances the ACTIVE profile id to the next available id with wrap-around, no-ops on a single profile,
 * and lands the first id from an unknown/dangling current — mirroring [StyleCyclerTest]/[TextSizeCyclerTest].
 */
class PrinterCyclerTest {

    private val ids = listOf("a", "b", "c")

    @Test
    fun advances_to_next_with_wraparound() {
        // From each position, the next id; the last wraps back to the first.
        assertEquals("b", nextProfileId(ids, "a"))
        assertEquals("c", nextProfileId(ids, "b"))
        assertEquals("a", nextProfileId(ids, "c"))
    }

    @Test
    fun full_cycle_returns_to_start() {
        var current: String? = "a"
        val seen = mutableListOf<String?>()
        repeat(3) {
            current = nextProfileId(ids, current)
            seen += current
        }
        // a -> b -> c -> a (a clean 3-step wrap).
        assertEquals(listOf("b", "c", "a"), seen)
    }

    @Test
    fun single_profile_is_a_noop() {
        // One profile → the cycler keeps whatever is active (no crash, no spurious switch).
        assertEquals("only", nextProfileId(listOf("only"), "only"))
    }

    @Test
    fun empty_list_returns_null() {
        // No profiles → nothing to switch to.
        assertNull(nextProfileId(emptyList(), null))
        assertNull(nextProfileId(emptyList(), "ghost"))
    }

    @Test
    fun unknown_or_null_current_lands_first() {
        // A null/dangling active-id resolves to the FIRST id so the first tap lands a valid profile
        // rather than no-opping forever.
        assertEquals("a", nextProfileId(ids, null))
        assertEquals("a", nextProfileId(ids, "not-in-list"))
    }
}
