package works.mees.dinghy.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * D-02 delta persistence shape: the sparse custom theme serializes to (a set of overridden role
 * names) + (one packed ARGB long per role), and the PURE read-path sanitizer round-trips it back to
 * the exact same [TokenDelta]. This locks the persisted format the DataStore wrapper writes/reads so
 * a refactor can't silently change it. (Host-pure: it drives [ThemePrefs.sanitize] directly, no I/O.)
 */
class TokenDeltaSerializationTest {

    /** Simulate the persisted store as a plain map, mirroring exactly what ThemePrefs writes. */
    private fun roundTrip(delta: TokenDelta): TokenDelta {
        val roleNames = delta.overrides.keys.map { it.name }.toSet()
        val argbByName = delta.overrides.mapKeys { it.key.name }
        val resolved = ThemePrefs.sanitize(
            rawBase = ThemeBase.Dark.name,
            rawFs = FontScale.M.name,
            rawRoleKeys = roleNames,
            readArgb = { argbByName[it] },
        )
        return resolved.deltas
    }

    @Test
    fun emptyDelta_roundTrips() {
        assertEquals(TokenDelta.EMPTY, roundTrip(TokenDelta.EMPTY))
    }

    @Test
    fun fullCustomScope_roundTripsExactly() {
        val delta = TokenDelta.of(
            TokenDelta.Role.Accent to Color(0xFF112233).toArgb(),
            TokenDelta.Role.Heat to Color(0xFF445566).toArgb(),
            TokenDelta.Role.Go to Color(0xFF778899).toArgb(),
            TokenDelta.Role.Stop to Color(0xFFAABBCC).toArgb(),
            TokenDelta.Role.Bg to Color(0xFFDDEEFF).toArgb(),
        )
        assertEquals(delta, roundTrip(delta))
    }

    @Test
    fun partialCustom_roundTripsOnlyTheOverriddenRoles() {
        val delta = TokenDelta.of(TokenDelta.Role.Accent to Color(0xFF010203).toArgb())
        val back = roundTrip(delta)
        assertEquals(1, back.overrides.size)
        assertEquals(delta, back)
    }

    @Test
    fun argbValue_isPreservedBitExact_includingAlpha() {
        // A fully-opaque ARGB (0xFF......) packs into 0xFFRRGGBB which exceeds Int range when read
        // as a signed Long unless masked — assert the value survives the long round-trip exactly.
        val argb = Color(0xFF4C94EC).toArgb().toLong() and 0xFFFFFFFFL
        val delta = TokenDelta.of(TokenDelta.Role.Accent to Color(0xFF4C94EC).toArgb())
        val back = roundTrip(delta)
        assertEquals(argb, back.overrides[TokenDelta.Role.Accent])
        // And resolving it reproduces the original Color.
        val resolved = resolve(ThemeBase.Dark, back, FontScale.M.multiplier)
        assertEquals(Color(0xFF4C94EC), resolved.accent)
    }
}
