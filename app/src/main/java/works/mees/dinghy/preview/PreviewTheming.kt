package works.mees.dinghy.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.flowOf
import works.mees.dinghy.theme.FontScale
import works.mees.dinghy.theme.ThemePrefs
import works.mees.dinghy.theme.ThemeResolver
import works.mees.dinghy.theme.compose.DinghyTheme
import works.mees.dinghy.theme.compose.LocalTokens

/**
 * The ONE preview theme boundary (SC-1). This is the reusable wrapper every later UI exemplar
 * (18-05/06/07 and the Phase 19-21 backfills) composes inside to render a `@Preview` through the
 * SAME live theme path the running app uses — there is deliberately NO preview-only token map
 * (RESEARCH "Don't-Hand-Roll": a hand-rolled preview palette would silently drift from production
 * and defeat the point of the harness).
 *
 * ## The bake/theme seam (verified this session)
 * The real Compose boundary is `DinghyTheme(tokensFlow = …, content)` (DinghyTheme.kt:37-51). The
 * pure per-tuple bake is `ThemeResolver().bake(tuple): ThemeTokens` (ThemeResolver.kt:162) — a
 * no-arg `ThemeResolver()` constructs with all-default fields (every ctor param defaults;
 * AppContainer.kt:454 + BenchActivity.kt:80) and `bake` reads NOTHING mutable. So the compiling,
 * production-faithful seam is:
 *
 * ```
 * DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple))) { content() }
 * ```
 *
 * This reuses the ONE token boundary (the `tokensFlow` overload + `bake`) rather than the
 * resolver-based overload, so a preview and the running app resolve a tuple identically.
 *
 * ## ⚠ `--fs` is injected via [ThemePrefs.ThemeTuple.fs], NOT `@Preview(fontScale = …)`
 * `@Preview(fontScale = …)` is a NO-OP in this app: `DinghyTheme` pins the OS `fontScale` to `1f`
 * at DinghyTheme.kt:48 so the in-app S/M/L `--fs` is the SOLE text-size authority (THEME-02/D-04).
 * To preview a larger text size you MUST seed the tuple's `fs` field — see [fsLargeSeed]. Setting
 * `@Preview(fontScale = 1.5f)` will render IDENTICALLY to `1f` and mislead you. Always use the
 * `fs`-seeded tuple.
 */
@Composable
fun PreviewBox(
    tuple: ThemePrefs.ThemeTuple,
    content: @Composable () -> Unit,
) {
    DinghyTheme(tokensFlow = flowOf(ThemeResolver().bake(tuple))) {
        // Paint the themed shell background so previews are theme-faithful. The screens
        // themselves do NOT fill a root background — the app shell does (MainActivity.kt:79:
        // `Box(Modifier.fillMaxSize().background(LocalTokens.current.bg))`). Without this,
        // every @Preview shows Studio's default-white backdrop and dark/light look identical.
        Box(Modifier.fillMaxSize().background(LocalTokens.current.bg)) {
            content()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The SIX canonical theme combo seeds: {Colorful, Simple, HighContrast} × {dark, light}.
//
// The combos are explicit named ThemeTuples (NOT a `@Preview` multipreview annotation — an
// annotation can set device/uiMode/locale but CANNOT select the palette MODE; see DinghyPreviews.kt).
// Each is built from the validated default tuple so a future field addition fails to compile here
// (the single place that must know the tuple shape) rather than silently mis-seeding every preview.
// ---------------------------------------------------------------------------------------------

private fun seed(
    paletteMode: String,
    dark: Boolean,
    fs: Float = FontScale.M.multiplier,
): ThemePrefs.ThemeTuple =
    ThemePrefs.TUPLE_DEFAULT.copy(
        paletteMode = paletteMode,
        dark = dark,
        fs = fs,
    )

/** Colorful palette, dark polarity — the out-of-box default look. */
val colorfulDark: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_COLORFUL, dark = true)

/** Colorful palette, light polarity. */
val colorfulLight: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_COLORFUL, dark = false)

/** Simple palette, dark polarity (status collapses to text — D-04). */
val simpleDark: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_SIMPLE, dark = true)

/** Simple palette, light polarity. */
val simpleLight: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_SIMPLE, dark = false)

/** High-Contrast palette, dark polarity (status forced to RYG — D-04). */
val highContrastDark: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_HIGH_CONTRAST, dark = true)

/** High-Contrast palette, light polarity. */
val highContrastLight: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_HIGH_CONTRAST, dark = false)

/**
 * The fs = L overflow seed (THEME-02): Colorful/dark at the LARGEST text size. Use this in a
 * dedicated `@Preview` to catch text-overflow/clipping at `--fs = L` — because `@Preview(fontScale=)`
 * is a no-op here (see [PreviewBox] KDoc), this seeded tuple is the ONLY way to preview large text.
 */
val fsLargeSeed: ThemePrefs.ThemeTuple = seed(ThemeResolver.MODE_COLORFUL, dark = true, fs = FontScale.L.multiplier)

/**
 * All SIX theme combos in a stable order — the reusable matrix the exemplars iterate to render every
 * combo, plus the smoke test's bake-seam coverage list.
 */
val themeCombos: List<ThemePrefs.ThemeTuple> = listOf(
    colorfulDark,
    colorfulLight,
    simpleDark,
    simpleLight,
    highContrastDark,
    highContrastLight,
)
