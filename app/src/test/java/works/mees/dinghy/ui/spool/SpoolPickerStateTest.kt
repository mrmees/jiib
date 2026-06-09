package works.mees.dinghy.ui.spool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Host-side proof for the [SpoolPickerState] value type and [FieldMode] state machine (23-06).
 *
 * Tests the FieldMode sealed class transitions and the picker state defaults — pure value-type
 * logic, no Compose, no coroutines, no Moonraker. The companion [SpoolHolderTest] covers the
 * holder's suspend mutators and live StateFlow behavior.
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
}
