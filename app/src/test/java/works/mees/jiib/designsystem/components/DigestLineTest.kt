package works.mees.jiib.designsystem.components

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.jiib.theme.JiibType

class DigestLineTest {

    @Test
    fun standard_emphasis_pairs_body_label_with_inline_data() {
        val (label, value) = digestLineRoles(DigestEmphasis.Standard)
        assertEquals(JiibType.body, label)
        assertEquals(JiibType.dataInline, value)
    }

    @Test
    fun strong_emphasis_pairs_list_label_with_stat_value() {
        val (label, value) = digestLineRoles(DigestEmphasis.Strong)
        assertEquals(JiibType.listLabel, label)
        assertEquals(JiibType.statValue, value)
    }

    @Test
    fun meta_emphasis_pairs_caption_with_data_meta() {
        val (label, value) = digestLineRoles(DigestEmphasis.Meta)
        assertEquals(JiibType.caption, label)
        assertEquals(JiibType.dataMeta, value)
    }

    @Test
    fun scaled_sp_floors_at_the_15sp_ramp_floor() {
        assertEquals(20f, digestScaledSp(20f, 1f), 0.001f)
        assertEquals(16f, digestScaledSp(20f, 0.8f), 0.001f)
        assertEquals(15f, digestScaledSp(20f, 0.5f), 0.001f)   // 10 would breach the floor
        assertEquals(15f, digestScaledSp(15f, 0.9f), 0.001f)   // meta lines never shrink below base 15
    }
}
