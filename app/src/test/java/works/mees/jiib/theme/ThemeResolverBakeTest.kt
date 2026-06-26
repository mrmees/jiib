package works.mees.jiib.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * 15.2-01 Task 2 — proves `ThemeResolver.bake(tuple)` is PURE (HIGH-2): it returns a snapshot
 * [ThemeTokens] for the given tuple while leaving the resolver's own `tokens.value` instance untouched
 * (no spurious `_tokens` emit, no shared-field mutation), and it produces the SAME tokens a resolver
 * yields after `apply()` of the equivalent tuple (it reuses the same generate path).
 */
class ThemeResolverBakeTest {

    private fun tuple(
        seedHex: String = DEFAULT_SEED_HEX,
        dark: Boolean = true,
        paletteMode: String = ThemeResolver.MODE_COLORFUL,
        poolShift: Int = 0,
        poolOverrides: Map<Int, Long> = emptyMap(),
        statusOverrides: Map<String, Long> = emptyMap(),
        fs: Float = FontScale.M.multiplier,
    ) = ThemePrefs.ThemeTuple(
        seedHex = seedHex,
        dark = dark,
        paletteMode = paletteMode,
        poolShift = poolShift,
        poolOverrides = poolOverrides,
        statusOverrides = statusOverrides,
        fs = fs,
    )

    @Test
    fun bake_is_pure_leaves_resolver_tokens_untouched() {
        // A resolver seeded DARK; capture its live tokens BEFORE baking a DIFFERING (light) tuple.
        val resolver = ThemeResolver(dark = true)
        val before = resolver.tokens.value

        val baked = resolver.bake(tuple(dark = false))

        // bake mutated no field and emitted nothing on _tokens → the live value is the SAME instance.
        assertSame(
            "bake must not touch the resolver's own tokens StateFlow",
            before,
            resolver.tokens.value,
        )
        // The returned snapshot reflects the baked tuple (light), distinct from the live (dark) bg.
        assertNotEquals(
            "baked light tokens must differ from the live dark tokens",
            before.bg,
            baked.bg,
        )
    }

    @Test
    fun bake_matches_apply_for_same_tuple() {
        val t = tuple(seedHex = "#c8b400", dark = false, fs = FontScale.S.multiplier)

        // A fresh resolver after apply() of the tuple's fields.
        val applied = ThemeResolver()
        applied.apply(
            seedHex = t.seedHex,
            dark = t.dark,
            paletteMode = t.paletteMode,
            poolShift = t.poolShift,
            overrides = t.poolOverrides.mapValues { it.value.toComposeColor() },
            statusOverrides = t.statusOverrides.mapValues { it.value.toComposeColor() },
            fs = t.fs,
        )

        // bake of the same tuple equals the applied tokens — same compute() path, no shared state touched.
        assertEquals(applied.tokens.value, ThemeResolver().bake(t))
    }
}
