package works.mees.jiib.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.jiib.command.PrinterCommands
import works.mees.jiib.state.HeaterLimits

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

    // Pre-merge review fix: a garbled configfile max_temp must not produce an inverted/empty range
    // (which would make Scrubber.value.coerceIn(start, end) THROW). Fall back to the global clamp,
    // and the range is always well-formed (start <= endInclusive).

    @Test fun `negative max falls back to global and stays well-formed`() {
        val r = heaterScrubberRange(HeaterLimits(maxTemp = -5.0))
        assertEquals(0f, r.start, 0f)
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
        assertTrue("range not inverted", r.start <= r.endInclusive)
    }

    @Test fun `zero max falls back to global`() {
        val r = heaterScrubberRange(HeaterLimits(maxTemp = 0.0))
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
        assertTrue("range not inverted", r.start <= r.endInclusive)
    }

    @Test fun `nan max falls back to global`() {
        val r = heaterScrubberRange(HeaterLimits(maxTemp = Double.NaN))
        assertEquals(PrinterCommands.MAX_TEMP_C.toFloat(), r.endInclusive, 0f)
        assertTrue("range not inverted", r.start <= r.endInclusive)
    }
}
