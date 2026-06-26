package works.mees.jiib.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pattern 4 traceability / drift guard (D-03): assert the most saturated baked sRGB literals equal
 * the values the committed bake script (tools/oklch-bake/bake_tokens.py) emits and that were
 * cross-checked against an external oklch->hex converter. A careless hand-edit of BakedTokens.kt
 * (or a regression in the bake) is caught here. These hex values are the ones recorded in the
 * BakedTokens.kt header comment.
 */
class BakedTokenTableTest {

    private fun assertHex(expectedRgb: Int, actual: Color) {
        // Compare the RGB (ignore alpha — these are opaque tokens) bit-exactly.
        assertEquals(expectedRgb and 0xFFFFFF, actual.toArgb() and 0xFFFFFF)
    }

    @Test
    fun darkSaturatedTokens_matchCommittedConverterHex() {
        assertHex(0x4C94EC, TokensDark.accent) // oklch(0.66 0.15 255)
        assertHex(0xF3A958, TokensDark.heat)   // oklch(0.79 0.13 66)
        assertHex(0x5AC576, TokensDark.go)     // oklch(0.74 0.15 150)
        assertHex(0xF4514F, TokensDark.stop)   // oklch(0.66 0.2 25)
        assertHex(0x0C1015, TokensDark.bg)     // oklch(0.17 0.012 255)
    }

    @Test
    fun lightSaturatedTokens_matchCommittedConverterHex() {
        assertHex(0x106ED7, TokensLight.accent) // oklch(0.55 0.18 256)
        assertHex(0xCE6400, TokensLight.heat)   // oklch(0.62 0.16 52)
        assertHex(0x008C3F, TokensLight.go)     // oklch(0.56 0.16 150)
        assertHex(0xD01C29, TokensLight.stop)   // oklch(0.55 0.21 25)
    }

    @Test
    fun alphaBearingTokens_carryExpectedAlpha() {
        // --accent-soft dark is .16 alpha → 0x29 (round(0.16*255)=41=0x29).
        assertEquals(0x29, TokensDark.accentSoft.toArgb() ushr 24 and 0xFF)
        // --hair dark is rgba(255,255,255,.08) → 0x14 alpha (round(0.08*255)=20=0x14).
        assertEquals(0x14, TokensDark.hair.toArgb() ushr 24 and 0xFF)
        // Opaque role token has full alpha.
        assertEquals(0xFF, TokensDark.accent.toArgb() ushr 24 and 0xFF)
    }
}
