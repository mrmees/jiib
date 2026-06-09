package works.mees.dinghy.ui.spool

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonElement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.spool.SpoolmanClient
import works.mees.dinghy.spool.SpoolmanStatus

/**
 * Holder-level tests for vendor multi-select toggle through [SpoolHolder.toggleVendor].
 *
 * These tests exercise the ACTUAL toggle state machine end-to-end — not just the [SpoolFilters]
 * data class in isolation. The mock-vs-reality gap that hid the on-device bug was that
 * [SpoolPickerStateTest] only tested the data layer; [SpoolHolder.toggleVendor] was never
 * exercised through a real holder instance.
 *
 * ## Bug the tests are written to catch (pre-fix regression guard):
 * Old code: `SpoolFilters.vendor: String?` — a scalar. Toggling vendor A then vendor B overwrote A
 * with B; only one vendor could be selected at a time.
 * New code: `SpoolFilters.vendors: List<String>` — list. Both A and B accumulate. These tests
 * assert the multi-select (accumulation) contract and will FAIL if the scalar is reintroduced.
 *
 * ## No-op client
 * The fake client returns null for every call (the [SpoolmanClient] default), which means
 * [SpoolHolder.refresh] degrades gracefully to an empty spool list. These tests only care about
 * the FILTER STATE after toggling — they do not inspect the spool list contents.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SpoolHolderVendorTest {

    // A minimal no-op SpoolmanClient (all methods return null per the interface defaults).
    private val noopClient: SpoolmanClient = object : SpoolmanClient {}

    private val noActiveSpool = MutableStateFlow<SpoolmanStatus?>(null)

    private fun holder() = SpoolHolder(
        scope = kotlinx.coroutines.test.TestScope(UnconfinedTestDispatcher()),
        client = noopClient,
        activeSpool = noActiveSpool,
    )

    // ─────────────────────────────────────────────────────────────────────────
    // Single-toggle ON
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor ON — vendor appears in filters dot vendors`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        assertTrue(h.state.value.filters.vendors.contains("Polymaker"))
    }

    @Test
    fun `toggleVendor ON — vendors list has exactly one entry`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        assertEquals(1, h.state.value.filters.vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-select (the core regression guard)
    //
    // These are the tests that FAIL against the old scalar `vendor: String?` field.
    // Pre-fix, toggling a second vendor would set vendor="Sunlu" (scalar overwrite),
    // so vendors.size would be 1 and "Polymaker" would not be present.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor MULTI — two vendors accumulate independently`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        val vendors = h.state.value.filters.vendors
        // Both must be present — pre-fix, only "Sunlu" was present (scalar overwrite).
        assertTrue("'Polymaker' must remain selected after 'Sunlu' is added", vendors.contains("Polymaker"))
        assertTrue("'Sunlu' must be selected", vendors.contains("Sunlu"))
        assertEquals("Both vendors must be in the list", 2, vendors.size)
    }

    @Test
    fun `toggleVendor MULTI — three vendors all accumulate`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")
        h.toggleVendor("Jayo")

        val vendors = h.state.value.filters.vendors
        assertTrue(vendors.containsAll(listOf("Polymaker", "Sunlu", "Jayo")))
        assertEquals(3, vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Toggle OFF (deselect)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor OFF — re-tapping same vendor removes it`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Polymaker")  // toggle off

        assertTrue("vendors must be empty after toggle-off", h.state.value.filters.vendors.isEmpty())
    }

    @Test
    fun `toggleVendor OFF — deselects only the tapped vendor, leaves others`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()

        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")
        h.toggleVendor("Polymaker")  // deselect Polymaker only

        val vendors = h.state.value.filters.vendors
        assertFalse("'Polymaker' must be removed", vendors.contains("Polymaker"))
        assertTrue("'Sunlu' must remain", vendors.contains("Sunlu"))
        assertEquals("Only Sunlu remains", 1, vendors.size)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Picker stays open (no auto-close on toggle)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `toggleVendor does NOT close the picker — fieldMode stays FilterPicker MFG`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.openFilterPicker(SpoolFilterCategory.MFG)

        h.toggleVendor("Polymaker")

        // Pre-fix: closeFilterPicker() was called inline → fieldMode became Spools.
        val mode = h.state.value.fieldMode
        assertTrue(
            "Field mode must remain FilterPicker(MFG) after toggle — picker must NOT auto-close",
            mode is FieldMode.FilterPicker && (mode as FieldMode.FilterPicker).category == SpoolFilterCategory.MFG,
        )
    }

    @Test
    fun `toggleMaterialFamily does NOT close the picker — fieldMode stays FilterPicker TYPE`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.openFilterPicker(SpoolFilterCategory.TYPE)

        h.toggleMaterialFamily("PLA")

        val mode = h.state.value.fieldMode
        assertTrue(
            "Field mode must remain FilterPicker(TYPE) after toggle — picker must NOT auto-close",
            mode is FieldMode.FilterPicker && (mode as FieldMode.FilterPicker).category == SpoolFilterCategory.TYPE,
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Clear
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `clearVendor resets vendors to empty list`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        h.clearVendor()

        assertTrue("clearVendor must empty the vendors list", h.state.value.filters.vendors.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────────────────
    // buildSpoolQuery parity — the query issued after a vendor toggle must carry
    // repeated filament.vendor.name= params (Spoolman OR semantics), not a scalar.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `after multi-toggle, buildSpoolQuery carries one param per vendor`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.toggleVendor("Sunlu")

        val query = buildSpoolQuery(h.state.value.filters, h.state.value.sortKey, h.state.value.sortAscending)
        assertTrue("filament.vendor.name=Polymaker must be in query", query.contains("filament.vendor.name=Polymaker"))
        assertTrue("filament.vendor.name=Sunlu must be in query", query.contains("filament.vendor.name=Sunlu"))
    }

    @Test
    fun `after clearVendor, buildSpoolQuery carries no vendor param`() = runTest(UnconfinedTestDispatcher()) {
        val h = holder()
        h.toggleVendor("Polymaker")
        h.clearVendor()

        val query = buildSpoolQuery(h.state.value.filters, h.state.value.sortKey, h.state.value.sortAscending)
        assertFalse("No filament.vendor.name= param expected after clear", query.contains("filament.vendor.name="))
    }
}
