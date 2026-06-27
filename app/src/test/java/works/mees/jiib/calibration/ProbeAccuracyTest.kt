package works.mees.jiib.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProbeAccuracyTest {

    @Test
    fun parsesRealLine() {
        val r = parseProbeAccuracy(
            "// probe accuracy results: maximum 2.012500, minimum 2.000000, range 0.012500, " +
            "average 2.005000, median 2.005000, standard deviation 0.003536"
        )!!
        assertEquals(0.012500, r.range, 1e-6)
        assertEquals(0.003536, r.stdDev, 1e-6)
    }

    @Test
    fun ignoresProgressLines() =
        assertNull(parseProbeAccuracy("// probe at 2.0,2.0 is z=2.005"))

    @Test
    fun toleratesNoPrefixAndSign() {
        assertNotNull(
            parseProbeAccuracy(
                "probe accuracy results: maximum -0.1, minimum -0.2, range 0.1, " +
                "average -0.15, median -0.15, standard deviation 0.02"
            )
        )
    }

    @Test
    fun malformedLineReturnsNull() =
        assertNull(parseProbeAccuracy("probe accuracy results: maximum NOPE"))
}
