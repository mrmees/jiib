package works.mees.dinghy.prompt

import org.junit.Assert.assertEquals
import org.junit.Test
import works.mees.dinghy.theme.ThemeBase
import works.mees.dinghy.theme.TokenDelta
import works.mees.dinghy.theme.resolve
import works.mees.dinghy.ui.prompt.promptStyleColor

/**
 * The 6-protocol-style → Dinghy-token resolver (12-04 Task 1; UI-SPEC button-mapping table). This is a
 * SEPARATE map from `designsystem/control/Intent` — Intent stays the 5-value safety vocabulary; the
 * protocol's 6th style (`info`) resolves to the brighter accent text tint `accent2`, which is NOT an
 * Intent member. Host-testable because `promptStyleColor` is a pure (style, tokens) → Color function;
 * the Compose-coupled markup builder is proven in the 12-05 UI test / on-device gate instead.
 *
 * The mapping (UI-SPEC):
 *  - primary   → accentLine
 *  - secondary → outline   (and ANY unknown/unnormalized style → outline, the protocol default)
 *  - info      → accent2
 *  - warning   → heat
 *  - error     → stop
 *  - success   → go
 */
class PromptStyleColorsTest {

    // A complete, resolved Dark theme at fs=1.0 — the source of the expected token colors.
    private val t = resolve(ThemeBase.Dark, TokenDelta.EMPTY, 1.0f)

    @Test
    fun primary_resolves_to_accentLine() {
        assertEquals(t.accentLine, promptStyleColor("primary", t))
    }

    @Test
    fun secondary_resolves_to_outline() {
        assertEquals(t.outline, promptStyleColor("secondary", t))
    }

    @Test
    fun info_resolves_to_accent2() {
        assertEquals(t.accent2, promptStyleColor("info", t))
    }

    @Test
    fun warning_resolves_to_heat() {
        assertEquals(t.heat, promptStyleColor("warning", t))
    }

    @Test
    fun error_resolves_to_stop() {
        assertEquals(t.stop, promptStyleColor("error", t))
    }

    @Test
    fun success_resolves_to_go() {
        assertEquals(t.go, promptStyleColor("success", t))
    }

    @Test
    fun unknown_style_falls_back_to_outline() {
        assertEquals(t.outline, promptStyleColor("not-a-style", t))
        assertEquals(t.outline, promptStyleColor("", t))
    }

    @Test
    fun mapping_is_case_insensitive_on_the_string_token() {
        assertEquals(t.accentLine, promptStyleColor("PRIMARY", t))
        assertEquals(t.stop, promptStyleColor("Error", t))
    }

    @Test
    fun the_six_styles_use_three_distinct_intent_free_colors_where_expected() {
        // info must NOT collapse onto primary's accentLine (it is the distinct accent2 tint).
        org.junit.Assert.assertNotEquals(
            promptStyleColor("primary", t),
            promptStyleColor("info", t),
        )
    }
}
