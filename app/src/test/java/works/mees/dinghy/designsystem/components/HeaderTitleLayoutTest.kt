package works.mees.dinghy.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HeaderTitleLayoutTest {

    // avail=1000, slot=100 → symmetric budget = 1000 - 2*100 = 800
    private val avail = 1000f
    private val slot = 100f

    @Test
    fun `fitting title keeps symmetric padding and no marquee`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 500f, slotPx = slot, endSlotOccupied = false)
        assertEquals(1, r.startSlots)
        assertEquals("a fitting title stays truly centered (both slots reserved)", 1, r.endSlots)
        assertFalse(r.marquee)
    }

    @Test
    fun `fitting title stays symmetric even when an end glyph is present`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 500f, slotPx = slot, endSlotOccupied = true)
        assertEquals(1, r.endSlots)
        assertFalse(r.marquee)
    }

    @Test
    fun `overflowing title with no end glyph reclaims the trailing slot and marquees`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 900f, slotPx = slot, endSlotOccupied = false)
        assertEquals(1, r.startSlots)
        assertEquals("no end glyph → reclaim the trailing slot", 0, r.endSlots)
        assertTrue(r.marquee)
    }

    @Test
    fun `overflowing title with an end glyph keeps the trailing slot for the glyph`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 900f, slotPx = slot, endSlotOccupied = true)
        assertEquals("end glyph occupies the slot → cannot reclaim", 1, r.endSlots)
        assertTrue(r.marquee)
    }

    @Test
    fun `boundary — title exactly at the symmetric budget still fits`() {
        val r = resolveHeaderTitleLayout(avail, titleWidthPx = 800f, slotPx = slot, endSlotOccupied = false)
        assertFalse("== budget counts as fitting", r.marquee)
    }

    @Test
    fun `tiny width coerces a negative budget to zero — anything overflows`() {
        // avail=120, slot=100 → budget would be -80 → coerced to 0 → any positive title overflows
        val r = resolveHeaderTitleLayout(availableWidthPx = 120f, titleWidthPx = 10f, slotPx = 100f, endSlotOccupied = false)
        assertTrue(r.marquee)
    }
}
