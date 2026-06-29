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

    @Test fun missingWeightFallsToNearestLowerThenAny() {
        val f = font(mapOf(FontWeight.Normal to 1, FontWeight.SemiBold to 3))
        // Bold (700) has no exact entry → nearest ≤ is SemiBold (600)
        assertEquals(3, f.fontRes(FontWeight.Bold))
        // Medium (500) has no exact entry → nearest ≤ is Normal (400)
        assertEquals(1, f.fontRes(FontWeight.Medium))
    }

    @Test fun belowAllFallsToLightest() {
        val f = font(mapOf(FontWeight.SemiBold to 3, FontWeight.Bold to 4))
        // Normal (400) is below every entry → take the lightest present (SemiBold)
        assertEquals(3, f.fontRes(FontWeight.Normal))
    }
}
