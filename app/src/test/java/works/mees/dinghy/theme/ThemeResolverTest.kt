package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * D-05 + Pitfall 7: the resolver is the single StateFlow source of truth, and the delta model never
 * drifts from its base — an empty delta set resolves IDENTICALLY to the base; a single override
 * changes EXACTLY one token and leaves the rest equal to the base.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ThemeResolverTest {

    @Test
    fun emptyDelta_resolvesIdenticalToBase_dark() {
        val resolved = resolve(ThemeBase.Dark, TokenDelta.EMPTY, FontScale.M.multiplier)
        // empty delta === base (Pitfall 7). M is the baked default fs, so equality is total.
        assertEquals(TokensDark, resolved)
    }

    @Test
    fun emptyDelta_resolvesIdenticalToBase_light() {
        val resolved = resolve(ThemeBase.Light, TokenDelta.EMPTY, FontScale.M.multiplier)
        assertEquals(TokensLight, resolved)
    }

    @Test
    fun singleAccentDelta_overridesExactlyOneToken() {
        val custom = Color(0xFF112233)
        val delta = TokenDelta.of(TokenDelta.Role.Accent to custom.toArgb())
        val resolved = resolve(ThemeBase.Dark, delta, FontScale.M.multiplier)

        // The one overridden token changed...
        assertEquals(custom, resolved.accent)
        assertNotEquals(TokensDark.accent, resolved.accent)
        // ...and EVERY other token still equals the base (no drift).
        assertEquals(TokensDark.copy(accent = custom), resolved)
    }

    @Test
    fun resolver_exposesStateFlow_andReEmitsOnEachMutator() = runTest {
        val resolver = ThemeResolver(base = ThemeBase.Dark)
        val emissions = mutableListOf<ThemeTokens>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            resolver.tokens.toList(emissions)
        }
        runCurrent()
        assertEquals(TokensDark, resolver.tokens.value) // seeded

        resolver.setBase(ThemeBase.Light)
        runCurrent()
        assertEquals(TokensLight, resolver.tokens.value)

        val custom = Color(0xFFABCDEF)
        resolver.setDeltas(TokenDelta.of(TokenDelta.Role.Stop to custom.toArgb()))
        runCurrent()
        assertEquals(custom, resolver.tokens.value.stop)

        resolver.setFs(FontScale.L.multiplier)
        runCurrent()
        assertEquals(1.32f, resolver.tokens.value.fs)

        // seed + 3 mutators = 4 distinct emissions.
        assertEquals(4, emissions.size)
        job.cancel()
    }

    @Test
    fun apply_setsAllThreeInputsInOneEmission() = runTest {
        val resolver = ThemeResolver()
        val custom = Color(0xFF00FF00)
        resolver.apply(
            base = ThemeBase.Light,
            deltas = TokenDelta.of(TokenDelta.Role.Go to custom.toArgb()),
            fs = FontScale.S.multiplier,
        )
        val t = resolver.tokens.value
        assertEquals(TokensLight.bg, t.bg)       // base applied
        assertEquals(custom, t.go)               // delta applied
        assertEquals(1.0f, t.fs)                 // fs applied
    }

    @Test
    fun emptyDelta_isSingleton() {
        assertSame(TokenDelta.EMPTY, TokenDelta.EMPTY)
        assertEquals(true, TokenDelta.EMPTY.isEmpty)
    }
}
