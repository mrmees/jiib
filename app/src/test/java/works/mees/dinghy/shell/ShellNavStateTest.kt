package works.mees.dinghy.shell

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.ui.route.Dest
import works.mees.dinghy.ui.shell.ShellNavState

/**
 * Host-pure proof of the four-tile Settings IA (15.2-04, D-01..D-05): the App-Drawer destinations
 * Printers([Dest.Devices]) · [Dest.Theme] · [Dest.Settings] · [Dest.About] each route to a DISTINCT
 * [Dest] through the lean [ShellNavState] router (NOT Navigation-Compose), and the Devices→Printers
 * rename leaves no orphaned enum value (the rename is lexical; [Dest.Devices] is the kept constant).
 *
 * [ShellNavState] is plain (Compose `mutableStateOf` is a thin wrapper) so it is directly host-testable
 * with no Robolectric — `navigateTo`/`goBack` are pure state transitions over [Dest].
 */
class ShellNavStateTest {

    /** The four IA destinations each push a distinct dest and back-pop correctly (normal pushes). */
    @Test
    fun four_ia_destinations_route_distinctly() {
        val nav = ShellNavState()

        // The four IA tiles, in drawer order. Printers IS Dest.Devices (the rename is lexical, D-02).
        val ia = listOf(Dest.Devices, Dest.Theme, Dest.Settings, Dest.About)

        // They are four DISTINCT enum values (no two tiles collide on one dest).
        assertEquals("the four IA dests must be distinct", 4, ia.toSet().size)

        // Navigating to each from Home pushes it as the visible dest and stacks the caller (Home).
        for (dest in ia) {
            nav.navigateTo(Dest.PrintStatus) // back to home (clears the stack)
            assertEquals(Dest.PrintStatus, nav.dest)
            nav.navigateTo(dest)
            assertEquals("navigateTo($dest) must make it the visible dest", dest, nav.dest)
            assertTrue("a non-home dest must stack its caller", nav.backStack.isNotEmpty())
            nav.goBack()
            assertEquals("Back from $dest must pop to the caller (Home)", Dest.PrintStatus, nav.dest)
        }
    }

    /** Chaining the four IA dests keeps each visible distinctly and back-pops in LIFO order. */
    @Test
    fun four_ia_destinations_chain_and_pop_in_order() {
        val nav = ShellNavState()
        // Home → Devices → Theme → Settings → About (each a normal push; only PrintStatus clears).
        nav.navigateTo(Dest.Devices)
        nav.navigateTo(Dest.Theme)
        nav.navigateTo(Dest.Settings)
        nav.navigateTo(Dest.About)
        assertEquals(Dest.About, nav.dest)
        // Pop back through the chain in reverse.
        nav.goBack(); assertEquals(Dest.Settings, nav.dest)
        nav.goBack(); assertEquals(Dest.Theme, nav.dest)
        nav.goBack(); assertEquals(Dest.Devices, nav.dest)
        nav.goBack(); assertEquals(Dest.PrintStatus, nav.dest)
    }

    /** The Devices→Printers rename is lexical: Dest.Devices is the single kept constant (no dangling ref). */
    @Test
    fun devices_rename_no_dangling_ref() {
        // Dest.Devices remains a valid enum value (the drawer tile relabel "Printers" does not orphan it).
        assertTrue(
            "Dest.Devices must remain in the enum (the rename is the drawer LABEL only)",
            Dest.entries.contains(Dest.Devices),
        )
        // Navigating to it routes distinctly (it is the Printers tile's target).
        val nav = ShellNavState()
        nav.navigateTo(Dest.Devices)
        assertEquals(Dest.Devices, nav.dest)
    }
}
