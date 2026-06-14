package works.mees.dinghy.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.command.PrinterCommands
import works.mees.dinghy.state.HeaterLimits

class ScrubberRangeTest {
    @Test fun `uses configured max when present`() {
        val r = heaterScrubberRange(HeaterLimits(minTemp = 0.0, maxTemp = 300.0))
        assertEquals(0f, r.start, 0f)
        assertEquals(300f, r.endInclusive, 0f)
    }

    @Test fun `falls back to global max when limits null`() {
        val r = heaterScrubberRange(null)
        assertEquals(0f, r.start, 0f)
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
    }

    @Test fun `falls back to global max when maxTemp missing`() {
        val r = heaterScrubberRange(HeaterLimits(minTemp = 10.0, maxTemp = null))
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
    }
}
