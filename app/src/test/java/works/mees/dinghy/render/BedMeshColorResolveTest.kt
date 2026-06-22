package works.mees.dinghy.render

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.theme.ThemeTokens

class BedMeshColorResolveTest {
    // Build ThemeTokens from the real dark token set with a known accent + 4-slot pool (the pattern
    // existing tests use, e.g. SeriesColorTest.kt:22-34 -> `TokensDark.copy(...)`).
    private val accent = Color(0xFF112233)
    private val pool = listOf(Color(0xFF0A0A0A), Color(0xFF0B0B0B), Color(0xFF0C0C0C), Color(0xFF0D0D0D))
    private val t = works.mees.dinghy.theme.TokensDark.copy(accent = accent, pool = pool)

    @Test fun negativeSelIsAccent() = assertEquals(accent, resolveMeshColor(t, -1))
    @Test fun slotSelIsPool() = assertEquals(pool[2], resolveMeshColor(t, 2))
    @Test fun outOfRangeSelFallsBackToAccent() = assertEquals(accent, resolveMeshColor(t, 9))
}
