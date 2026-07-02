package works.mees.jiib.theme.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import works.mees.jiib.theme.FontScale
import works.mees.jiib.theme.TokensLight

class FocusHeroValueTextTest {
    @get:Rule val rule = createComposeRule()
    private val t = TokensLight.copy(fs = FontScale.L.multiplier)

    /**
     * Verifies the shrink-to-fit contract: a wide value+unit pair stays on ONE line with no width
     * overflow inside a fixed box that is narrower than the text at maxSp.
     *
     * DEVIATION from brief's original test (width 90dp → 140dp), documented:
     * The brief assumed `TextAutoSize.StepBased` would shrink single-line AnnotatedString on
     * width overflow. On-device (2026-06-29) it does NOT — the fallback uses BoxWithConstraints +
     * TextMeasurer stepping from maxSp down to minSp. The minimum font is minSp = fsSp(15, 1.32)
     * = 19.8sp. On-device measurement shows "1200.0mm/s²" at 19.8sp has a natural width of:
     *   - Nexus 7 (density 2.0): 258px = 129dp
     *   - Moto G Play (density 1.75): 185px = 106dp
     * The brief's 90dp box (≤157px) is narrower than the text at ANY allowed font size and causes
     * unavoidable overflow regardless of implementation. 140dp (= 280px on Nexus 7, 245px on Moto)
     * is wide enough for the text at minSp (258px < 280px, 185px < 245px) while narrow enough to
     * force shrinking from maxSp (583px and 510px respectively). The load-bearing assertions
     * (lineCount == 1, !didOverflowWidth, !didOverflowHeight) are UNCHANGED.
     */
    @Test fun wideValueUnit_staysOneLine_noOverflow_inNarrowFixedBox() {
        rule.setContent {
            Box(Modifier.size(width = 140.dp, height = 56.dp)) {
                FocusHeroValueText(
                    value = "1200.0", unit = "mm/s²", t = t,
                    valueColor = Color.Black, unitColor = Color.Gray,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        val out = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText("1200.0", substring = true).fetchSemanticsNode()
            .config[SemanticsActions.GetTextLayoutResult].action!!.invoke(out)
        val l = out.first()
        assertEquals("hero stays one line", 1, l.lineCount)   // proves it shrank, not wrapped
        assertFalse("no width overflow", l.didOverflowWidth)
        assertFalse("no height overflow", l.didOverflowHeight)
    }
}
