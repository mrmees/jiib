package works.mees.dinghy.ui.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side proof for the [SpoolPickerState] value type and [FieldMode] state machine (23-06).
 *
 * Tests the FieldMode sealed class transitions, the picker state defaults, and the multi-select
 * filter facet behavior — pure value-type logic, no Compose, no coroutines, no Moonraker.
 */
class SpoolPickerStateTest {

    @Test
    fun `default state has Spools fieldMode`() {
        val state = SpoolPickerState()
        assertEquals(FieldMode.Spools, state.fieldMode)
    }

    @Test
    fun `default state has no selection`() {
        val state = SpoolPickerState()
        assertNull(state.selected)
    }

    @Test
    fun `default state is not loading`() {
        val state = SpoolPickerState()
        assertFalse(state.loading)
    }

    @Test
    fun `FilterPicker fieldMode carries the category`() {
        val state = SpoolPickerState(fieldMode = FieldMode.FilterPicker(SpoolFilterCategory.TYPE))
        val mode = state.fieldMode
        assertTrue(mode is FieldMode.FilterPicker)
        assertEquals(SpoolFilterCategory.TYPE, (mode as FieldMode.FilterPicker).category)
    }

    @Test
    fun `copy to FilterPicker and back to Spools`() {
        val initial = SpoolPickerState()
        val withPicker = initial.copy(fieldMode = FieldMode.FilterPicker(SpoolFilterCategory.COLOR))
        val backToSpools = withPicker.copy(fieldMode = FieldMode.Spools)

        assertEquals(FieldMode.Spools, initial.fieldMode)
        assertEquals(SpoolFilterCategory.COLOR, (withPicker.fieldMode as FieldMode.FilterPicker).category)
        assertEquals(FieldMode.Spools, backToSpools.fieldMode)
    }

    @Test
    fun `FilterPicker categories are distinct`() {
        val type = FieldMode.FilterPicker(SpoolFilterCategory.TYPE)
        val color = FieldMode.FilterPicker(SpoolFilterCategory.COLOR)
        val mfg = FieldMode.FilterPicker(SpoolFilterCategory.MFG)

        assertTrue(type != color)
        assertTrue(color != mfg)
        assertTrue(type != mfg)
    }

    @Test
    fun `Spools is a singleton data object`() {
        val a = FieldMode.Spools
        val b = FieldMode.Spools
        assertEquals(a, b)
        assertTrue(a === b)
    }

    @Test
    fun `default sortKey is NAME`() {
        assertEquals(SpoolSortKey.NAME, SpoolPickerState().sortKey)
    }

    @Test
    fun `default sortAscending matches NAME default`() {
        val state = SpoolPickerState()
        assertEquals(SpoolSortKey.NAME.defaultAscending, state.sortAscending)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Multi-select vendor filter (Issue 4 — owner feedback 23-06 checkpoint)
    // Filter semantics: OR within a facet, AND across facets.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    fun `default SpoolFilters has empty vendors list`() {
        val filters = SpoolFilters()
        assertTrue(filters.vendors.isEmpty())
    }

    @Test
    fun `vendors toggle ON — adding a vendor appears in the list`() {
        val initial = SpoolFilters()
        val afterToggle = initial.copy(vendors = initial.vendors + "Polymaker")
        assertTrue(afterToggle.vendors.contains("Polymaker"))
        assertEquals(1, afterToggle.vendors.size)
    }

    @Test
    fun `vendors toggle OFF — removing a vendor is gone from the list`() {
        val withTwo = SpoolFilters(vendors = listOf("Polymaker", "Prusament"))
        val afterRemove = withTwo.copy(vendors = withTwo.vendors.filterNot { it == "Polymaker" })
        assertFalse(afterRemove.vendors.contains("Polymaker"))
        assertEquals(listOf("Prusament"), afterRemove.vendors)
    }

    @Test
    fun `multiple vendors can be selected simultaneously (multi-select toggle on)`() {
        val f1 = SpoolFilters(vendors = listOf("Polymaker"))
        val f2 = f1.copy(vendors = f1.vendors + "Prusament")
        val f3 = f2.copy(vendors = f2.vendors + "eSun")
        assertEquals(3, f3.vendors.size)
        assertTrue(f3.vendors.containsAll(listOf("Polymaker", "Prusament", "eSun")))
    }

    @Test
    fun `clear vendors resets to empty list`() {
        val withVendors = SpoolFilters(vendors = listOf("Polymaker", "Prusament"))
        val cleared = withVendors.copy(vendors = emptyList())
        assertTrue(cleared.vendors.isEmpty())
    }

    @Test
    fun `SpoolFilters with vendors is not empty when vendors selected`() {
        val filters = SpoolFilters(vendors = listOf("Polymaker"))
        assertTrue(filters.vendors.isNotEmpty())
    }

    @Test
    fun `OR-match semantics — a spool matches if its vendor is in the selected set`() {
        // Simulates the OR-within-facet rule: Polymaker OR Prusament both match; eSun does not.
        val selectedVendors = listOf("Polymaker", "Prusament")
        assertTrue(selectedVendors.any { it.equals("Polymaker", ignoreCase = true) })
        assertTrue(selectedVendors.any { it.equals("Prusament", ignoreCase = true) })
        assertFalse(selectedVendors.any { it.equals("eSun", ignoreCase = true) })
    }

    @Test
    fun `buildSpoolQuery joins selected vendors into one comma param for OR semantics`() {
        val filters = SpoolFilters(vendors = listOf("Polymaker", "Prusament"))
        val query = buildSpoolQuery(filters, SpoolSortKey.NAME, true)
        // Spoolman declares filament.vendor.name as a SINGLE scalar str param and ORs comma-separated
        // terms WITHIN that one value (add_where_clause_str → value.split(",") → sqlalchemy.or_). Repeated
        // params do NOT OR — FastAPI keeps the last only (the MFG multi-select bug). So a multi-vendor
        // selection must be ONE comma-joined param.
        assertTrue(
            "Expected one comma-joined 'filament.vendor.name=Polymaker,Prusament', got: $query",
            query.contains("filament.vendor.name=Polymaker,Prusament"),
        )
    }

    @Test
    fun `buildSpoolQuery with empty vendors produces no vendor filter param`() {
        val filters = SpoolFilters(vendors = emptyList())
        val query = buildSpoolQuery(filters, SpoolSortKey.NAME, true)
        assertFalse("No vendor param expected when vendors is empty", query.contains("filament.vendor.name="))
    }

    @Test
    fun `multi-select vendor AND material families are independent facets`() {
        // AND across facets: both material and vendor filters apply simultaneously
        val filters = SpoolFilters(
            materialFamilies = listOf("PLA"),
            vendors = listOf("Polymaker"),
        )
        val query = buildSpoolQuery(filters, SpoolSortKey.NAME, true)
        assertTrue(query.contains("filament.material=PLA"))
        assertTrue(query.contains("filament.vendor.name=Polymaker"))
    }
}
