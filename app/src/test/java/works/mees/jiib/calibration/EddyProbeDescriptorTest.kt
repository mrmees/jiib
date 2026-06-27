package works.mees.jiib.calibration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import works.mees.jiib.state.Capabilities

class EddyProbeDescriptorTest {

    @Test
    fun derivesCasePreservedChip() {
        val caps = Capabilities(objects = setOf("probe_eddy_current My_Eddy", "toolhead"))
        assertEquals("My_Eddy", eddyProbeDescriptor(caps)!!.chip)
    }

    @Test
    fun nullWhenAbsent() {
        assertNull(eddyProbeDescriptor(Capabilities(objects = setOf("probe"))))
    }

    @Test
    fun unnamedSectionYieldsEmptyChip() {
        val caps = Capabilities(objects = setOf("probe_eddy_current"))
        assertEquals("", eddyProbeDescriptor(caps)!!.chip)
    }

    @Test
    fun ignoresOtherObjects() {
        val caps = Capabilities(objects = setOf("toolhead", "extruder", "heater_bed"))
        assertNull(eddyProbeDescriptor(caps))
    }

    @Test
    fun picksFirstMatchAmongMultiple() {
        // Sets are unordered, but any valid eddy object must produce a non-null result
        val caps = Capabilities(objects = setOf("probe_eddy_current Alpha", "probe_eddy_current Beta"))
        val result = eddyProbeDescriptor(caps)!!
        assert(result.chip == "Alpha" || result.chip == "Beta")
    }

    @Test
    fun doesNotMatchPrefixSubstring() {
        // "probe_eddy_currentX" should NOT match — must be exact or followed by space
        val caps = Capabilities(objects = setOf("probe_eddy_currentX"))
        assertNull(eddyProbeDescriptor(caps))
    }
}
