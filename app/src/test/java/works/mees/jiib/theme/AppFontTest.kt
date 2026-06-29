package works.mees.jiib.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFontTest {
    private fun font(weights: Map<FontWeight, Int>) =
        AppFont("x", "X", FontKind.Ui, FontFamily.Default, weights)

    @Test fun exactWeightWins() {
        val f = font(mapOf(FontWeight.Normal to 1, FontWeight.Medium to 2, FontWeight.SemiBold to 3))
        assertEquals(2, f.fontRes(FontWeight.Medium))
    }

    @Test fun missingWeightFallsToNearestWithNothingSuitableAbove() {
        val f = font(mapOf(FontWeight.Normal to 1, FontWeight.SemiBold to 3))
        // Bold (700, >500) has nothing heavier available → nearest lighter is SemiBold (600)
        assertEquals(3, f.fontRes(FontWeight.Bold))
        // Medium (500) has nothing in (500..500] above → nearest lighter is Normal (400)
        assertEquals(1, f.fontRes(FontWeight.Medium))
    }

    @Test fun missingWeightAbove500PrefersHeavier_matchesComposeMatcher() {
        // Carlito-like: Regular + Bold, no SemiBold. A SemiBold (600, >500) request must jump UP to
        // Bold (700) — mirroring Compose's FontFamily matcher — NOT down to Regular (400). This is the
        // alignment that keeps the Compose and classic-Views seams on the same static weight.
        val f = font(mapOf(FontWeight.Normal to 1, FontWeight.Bold to 4))
        assertEquals(4, f.fontRes(FontWeight.SemiBold))
    }

    @Test fun belowAllFallsToLightest() {
        val f = font(mapOf(FontWeight.SemiBold to 3, FontWeight.Bold to 4))
        // Normal (400) is below every entry → take the lightest present (SemiBold)
        assertEquals(3, f.fontRes(FontWeight.Normal))
    }
}
