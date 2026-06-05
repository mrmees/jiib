package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import works.mees.dinghy.config.Profile

/**
 * Wave-0 scaffold for the per-printer theme triple derivation (MULTI-01, D-08, RESEARCH Pattern 3).
 *
 * Exercises [Profile.toThemeResolved] (real, Task 1) — a profile's persisted theme PRIMITIVES
 * (`themeBase`/`fsChoice`/`themeDeltaArgb`) resolve to the correct [ThemePrefs.Resolved], reusing
 * [ThemePrefs.sanitize] parity so corrupt primitives fail safe to the defaults (NEVER throw). Fully
 * exercisable now (no AppContainer dependency — the container re-seed wiring lands in plan 02).
 */
class ProfileThemeSeedTest {

    private fun profile(
        base: String = "Dark",
        fs: String = "M",
        deltaArgb: Map<String, Long> = emptyMap(),
    ) = Profile(id = "a", host = "192.168.1.120", themeBase = base, fsChoice = fs, themeDeltaArgb = deltaArgb)

    @Test
    fun mapsBaseAndFsToResolved() {
        val r = profile(base = "Light", fs = "L").toThemeResolved()
        assertEquals(ThemeBase.Light, r.base)
        assertEquals(FontScale.L.multiplier, r.fs)
    }

    @Test
    fun appliesValidDeltaOverride() {
        val accent = Color(0xFF112233).toArgb().toLong() and 0xFFFFFFFFL
        val r = profile(deltaArgb = mapOf("Accent" to accent)).toThemeResolved()
        assertTrue(r.deltas.overrides.containsKey(TokenDelta.Role.Accent))
        assertEquals(accent, r.deltas.overrides[TokenDelta.Role.Accent])
        // And it resolves into a complete token set (fail-safe, fully usable).
        assertEquals(Color(0xFF112233), resolve(r.base, r.deltas, r.fs).accent)
    }

    @Test
    fun corruptBasePrimitive_failsSafeToDefaultBase() {
        val r = profile(base = "Banana").toThemeResolved()
        assertEquals(ThemePrefs.DEFAULT.base, r.base) // unknown base → Dark default, never a throw
    }

    @Test
    fun corruptFsPrimitive_failsSafeToDefaultFs() {
        val r = profile(fs = "XXL").toThemeResolved()
        assertEquals(ThemePrefs.DEFAULT.fs, r.fs)
    }

    @Test
    fun junkDeltaRole_isDropped() {
        val r = profile(deltaArgb = mapOf("Banana" to 0x123L)).toThemeResolved()
        assertTrue("an unknown role must be dropped, never thrown", r.deltas.isEmpty)
    }

    @Test
    fun fullyDefaultPrimitives_resolveToThemePrefsDefault() {
        assertEquals(ThemePrefs.DEFAULT, profile().toThemeResolved())
    }
}
