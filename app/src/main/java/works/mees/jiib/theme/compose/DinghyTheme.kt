package works.mees.jiib.theme.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.Flow
import works.mees.jiib.theme.ThemeResolver
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.TokensDark

/**
 * The single Compose theme boundary (D-05): the one place that collects the headless
 * [ThemeResolver.tokens] flow and republishes it to the whole subtree through [LocalTokens]. Every
 * later panel composes INSIDE this — they read `LocalTokens.current`, never a raw color, so "a theme
 * is a token remap" holds on the Compose side exactly as it does for the Views graph.
 *
 * `collectAsStateWithLifecycle()` (androidx.lifecycle-runtime-compose) is used rather than plain
 * `collectAsState()` so collection follows the host lifecycle — the appliance screen does not burn
 * cycles re-collecting a rarely-changing theme while stopped.
 *
 * ## `--fs` as the SOLE text-size authority (D-04 / THEME-02)
 * This is the ONE place the OS accessibility `fontScale` is neutralized. We override [LocalDensity]
 * to a [Density] whose `fontScale = 1f` while preserving the real pixel `density`. Consequence: an
 * `sp` size no longer absorbs the system font-scale multiplier, so the app's `--fs` (S≈1.0 / M≈1.15 /
 * L≈1.32, applied via [works.mees.jiib.theme.fsSp] to base sizes) is the ONLY text-size multiplier
 * — it can never double-apply on top of the OS setting.
 *
 * This is the standard kiosk/appliance text-authority trade (RESEARCH Pattern 5): a printer-side
 * screen owns its own legible-at-arm's-length type scale via the in-app S/M/L setting rather than
 * deferring to a device-wide accessibility slider that the wall-mounted tablet's operator may not
 * control. Keep this override at EXACTLY this one boundary — no second density path downstream may
 * re-introduce `fontScale`, or `--fs` stops being sole authority.
 */
@Composable
fun DinghyTheme(
    tokensFlow: Flow<ThemeTokens>,
    content: @Composable () -> Unit,
) {
    // The override-aware effective-tokens flow (15.2-01 HIGH-1). The initial value is the baked default
    // (TokensDark) until the first emission lands — the same Phase-3 fail-safe default the resolver uses.
    val tokens by tokensFlow.collectAsStateWithLifecycle(initialValue = TokensDark)
    val baseDensity = LocalDensity.current
    CompositionLocalProvider(
        LocalTokens provides tokens,
        LocalDensity provides Density(density = baseDensity.density, fontScale = 1f),
        content = content,
    )
}

/**
 * Resolver-based overload (the original Phase-3 boundary): collects [ThemeResolver.tokens] directly. The
 * app's production host (MainActivity) uses the [Flow]-based overload above with the override-aware
 * `AppContainer.effectiveTokens`; this overload remains for benchmarks/instrumented tests that drive a
 * bare [ThemeResolver] with no override layer. Both share the SAME `--fs` density authority.
 */
@Composable
fun DinghyTheme(
    resolver: ThemeResolver,
    content: @Composable () -> Unit,
) = DinghyTheme(tokensFlow = resolver.tokens, content = content)
