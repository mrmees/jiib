package works.mees.jiib.ui.temperature

import org.junit.Assert.assertEquals
import org.junit.Test

class TitleCaseTest {
    @Test fun titleCases_single_word() {
        assertEquals("Chamber", titleCase("chamber"))
    }

    @Test fun titleCases_underscored_token() {
        assertEquals("Mcu Temp", titleCase("mcu_temp"))
    }

    @Test fun titleCases_spaced_token() {
        assertEquals("Pi Cpu", titleCase("pi cpu"))
    }

    @Test fun collapses_empty_segments() {
        assertEquals("A B", titleCase("a__b"))
    }

    @Test fun collapses_mixed_whitespace_and_underscores() {
        assertEquals("A B C", titleCase("a \t b__c"))
    }
}
