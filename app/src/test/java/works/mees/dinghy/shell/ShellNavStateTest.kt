package works.mees.dinghy.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.route.NavDest
import works.mees.dinghy.ui.route.knownNavDests
import works.mees.dinghy.ui.shell.ShellNavState

/**
 * Host-pure proof of the four-tile Settings IA (15.2-04, D-01..D-05): the App-Drawer destinations
 * Printers([NavDest.Devices]) · [NavDest.Theme] · [NavDest.Settings] · [NavDest.About] each route to
 * a DISTINCT [NavDest] through the lean [ShellNavState] router (NOT Navigation-Compose), and the
 * Devices→Printers rename leaves no orphaned value (the rename is lexical; [NavDest.Devices] is the
 * kept constant).
 *
 * [ShellNavState] is plain (Compose `mutableStateOf` is a thin wrapper) so it is directly host-testable
 * with no Robolectric — `navigateTo`/`goBack` are pure state transitions over [NavDest].
 */
class ShellNavStateTest {

    /** The four IA destinations each push a distinct dest and back-pop correctly (normal pushes). */
    @Test
    fun four_ia_destinations_route_distinctly() {
        val nav = ShellNavState()

        // The four IA tiles, in drawer order. Printers IS NavDest.Devices (the rename is lexical, D-02).
        val ia = listOf(NavDest.Devices, NavDest.Theme, NavDest.Settings, NavDest.About)

        // They are four DISTINCT values (no two tiles collide on one dest).
        assertEquals("the four IA dests must be distinct", 4, ia.toSet().size)

        // Navigating to each from Home pushes it as the visible dest and stacks the caller (Home).
        for (dest in ia) {
            nav.navigateTo(NavDest.WaterfallHome) // back to home (clears the stack)
            assertEquals(NavDest.WaterfallHome, nav.dest)
            nav.navigateTo(dest)
            assertEquals("navigateTo($dest) must make it the visible dest", dest, nav.dest)
            assertTrue("a non-home dest must stack its caller", nav.backStack.isNotEmpty())
            nav.goBack()
            assertEquals("Back from $dest must pop to the caller (Home)", NavDest.WaterfallHome, nav.dest)
        }
    }

    /** Chaining the four IA dests keeps each visible distinctly and back-pops in LIFO order. */
    @Test
    fun four_ia_destinations_chain_and_pop_in_order() {
        val nav = ShellNavState()
        // Home → Devices → Theme → Settings → About (each a normal push; only WaterfallHome clears).
        nav.navigateTo(NavDest.Devices)
        nav.navigateTo(NavDest.Theme)
        nav.navigateTo(NavDest.Settings)
        nav.navigateTo(NavDest.About)
        assertEquals(NavDest.About, nav.dest)
        // Pop back through the chain in reverse.
        nav.goBack(); assertEquals(NavDest.Settings, nav.dest)
        nav.goBack(); assertEquals(NavDest.Theme, nav.dest)
        nav.goBack(); assertEquals(NavDest.Devices, nav.dest)
        nav.goBack(); assertEquals(NavDest.WaterfallHome, nav.dest)
    }

    /** The Devices→Printers rename is lexical: NavDest.Devices is the single kept constant (no dangling ref). */
    @Test
    fun devices_rename_no_dangling_ref() {
        // NavDest.Devices remains a known dest (the drawer tile relabel "Printers" does not orphan it).
        assertTrue(
            "NavDest.Devices must remain in knownNavDests (the rename is the drawer LABEL only)",
            NavDest.Devices in knownNavDests,
        )
        // Navigating to it routes distinctly (it is the Printers tile's target).
        val nav = ShellNavState()
        nav.navigateTo(NavDest.Devices)
        assertEquals(NavDest.Devices, nav.dest)
    }
}
