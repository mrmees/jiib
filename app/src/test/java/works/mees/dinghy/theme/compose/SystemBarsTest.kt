package works.mees.dinghy.theme.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.theme.TokensDark
import works.mees.dinghy.theme.TokensLight

/**
 * Host unit tests for the [isDarkBackdrop] system-bar contrast gate (quick 260611-cj1). Like
 * [works.mees.dinghy.theme.BrandTintTest], `androidx.compose.ui.graphics.Color` is a value class and
 * its `.luminance()` extension is pure math (no Android runtime), so these run on the JVM with no
 * Robolectric/instrumentation.
 *
 * The gate is the SOLE dark/light decision authority for system-bar styling: dark backdrop → light
 * bar icons ([androidx.activity.SystemBarStyle.dark]); light backdrop → dark icons.
 */
class SystemBarsTest {

    @Test
    fun bakedDarkThemeBg_isDarkBackdrop() {
        // TokensDark.bg (#0C1015) is the app's fail-safe default — MUST select light icons.
        assertTrue(isDarkBackdrop(TokensDark.bg))
    }

    @Test
    fun bakedLightThemeBg_isNotDarkBackdrop() {
        // TokensLight.bg (#F2F4F6) — MUST select dark icons.
        assertFalse(isDarkBackdrop(TokensLight.bg))
    }

    @Test
    fun pureExtremes_blackIsDark_whiteIsLight() {
        assertTrue(isDarkBackdrop(Color.Black))
        assertFalse(isDarkBackdrop(Color.White))
    }

    @Test
    fun midGreyNearThreshold_resolvesDeterministicallyDark() {
        // Boundary documentation: #767676 is a PERCEPTUAL mid-grey, but its sRGB-linearized relative
        // luminance is ~0.184 — well below the 0.5 gate — so it deterministically lands DARK (light
        // icons). Pinned so a future gate change (different threshold or perceptual-lightness math)
        // fails loudly here instead of silently flipping bar contrast on mid-tone custom themes.
        val midGrey = Color(0xFF767676)
        assertTrue(
            "test premise: #767676 relative luminance (${midGrey.luminance()}) must sit below 0.5",
            midGrey.luminance() < 0.5f,
        )
        assertTrue(isDarkBackdrop(midGrey))
    }
}
