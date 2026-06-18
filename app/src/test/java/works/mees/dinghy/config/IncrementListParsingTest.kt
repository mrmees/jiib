package works.mees.dinghy.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IncrementListParsingTest {

    @Test fun `filter strips disallowed chars, keeps digits dot comma space`() {
        assertEquals("1,5.5, 10", filterIncrementInput("1a,5.5x, 10!"))
    }

    @Test fun `canonical strips spaces, preserves token text and order`() {
        val r = parseIncrementInput("10, 5 , 1", maxCount = null) as IncrementParse.Ok
        assertEquals("10,5,1", r.canonical)
        assertEquals(listOf(10.0, 5.0, 1.0), r.values)
    }

    @Test fun `decimal precision preserved verbatim`() {
        val r = parseIncrementInput("0.001,0.005,0.01", maxCount = 3) as IncrementParse.Ok
        assertEquals("0.001,0.005,0.01", r.canonical)
        assertEquals(0.001, r.values[0], 0.0)
    }

    @Test fun `empty token rejected`() {
        assertTrue(parseIncrementInput("1,,5", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `non-positive rejected`() {
        assertTrue(parseIncrementInput("0,5", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("-1,5", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `multiple decimal points rejected`() {
        assertTrue(parseIncrementInput("1.2.3", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `at least one value required`() {
        assertTrue(parseIncrementInput("", maxCount = null) is IncrementParse.Error)
        assertTrue(parseIncrementInput("   ", maxCount = null) is IncrementParse.Error)
    }

    @Test fun `fixed count enforced`() {
        assertTrue(parseIncrementInput("1,5", maxCount = 3) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,10,25", maxCount = 3) is IncrementParse.Error)
        assertTrue(parseIncrementInput("1,5,10", maxCount = 3) is IncrementParse.Ok)
    }

    @Test fun `formatList renders canonical comma string with trimmed zeros`() {
        assertEquals("0.001,0.05,1,10", formatIncrementList(listOf(0.001, 0.05, 1.0, 10.0)))
    }
}
