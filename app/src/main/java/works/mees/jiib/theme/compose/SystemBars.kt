package works.mees.jiib.theme.compose

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView

/**
 * System-bar theming (quick 260611-cj1): lock edge-to-edge bar styling (status/navigation icon
 * contrast + the pre-API-35 bar colors) to the ACTIVE [works.mees.jiib.theme.ThemeTokens], never
 * the system uiMode.
 *
 * Found at the Moto G Play 2024 (Android 14 / API 34) UAT of 26.5 R1: the no-arg
 * `enableEdgeToEdge()` baseline follows the SYSTEM theme, so the app's dark theme on a
 * system-light device wore a WHITE navigation bar. The app deliberately neutralizes system
 * theming in favor of its semantic-token system (dark + light + user custom) — the bars must
 * follow the active tokens too.
 */

/**
 * The SOLE dark/light decision authority for system-bar styling: `true` when [bg] is a dark
 * backdrop that needs LIGHT bar icons.
 *
 * Derived from the bg token's relative luminance rather than a declared theme polarity because:
 *  - [works.mees.jiib.theme.ThemeTokens] carries NO dark/light flag (the resolver's `dark`
 *    field is private and MainActivity consumes the override-aware `effectiveTokens` flow, not
 *    the resolver), and
 *  - icon contrast is a function of the ACTUAL backdrop, not the declared polarity — a user
 *    custom theme declared "dark" but seeded with a light bg still needs dark icons. This also
 *    covers dev overrides and every future seed for free.
 *
 * `Color.luminance()` is pure host-testable math (precedent: [works.mees.jiib.theme.brandTint]
 * + BrandTintTest); the 0.5 threshold is pinned by SystemBarsTest (note: perceptual mid-grey
 * #767676 linearizes to ~0.18 — luminance-dark, light icons).
 */
internal fun isDarkBackdrop(bg: Color): Boolean = bg.luminance() < 0.5f

/**
 * Theme-reactive system-bar sync for the ACTIVITY window — the official reactive edge-to-edge
 * pattern: re-call [enableEdgeToEdge] with explicit [SystemBarStyle]s whenever the active theme's
 * backdrop changes (settings, custom seeds, the dev cycler), refining the no-arg baseline call in
 * MainActivity.onCreate (which stays as the pre-first-frame default).
 *
 * Style semantics on both API generations:
 *  - **API < 35:** the scrims PAINT the bar colors — `t.bg` as both scrim and darkScrim fixes the
 *    white nav bar on the Moto (API 34) and forbids the system from picking its own contrast color.
 *  - **API 35+:** scrims are IGNORED (bars forced transparent); the root Box's token bg paints
 *    edge-to-edge behind the bars, and only the `isAppearanceLight*` icon contrast carried by the
 *    style applies. The SAME style selection is therefore correct on both generations.
 *
 * Resolves the host [ComponentActivity] from the view's context chain; silently no-ops in
 * previews ([android.view.View.isInEditMode]) and non-activity hosts (benchmark hosts
 * keep their own window untouched). Every color flows from [LocalTokens] — no raw literals.
 *
 * Call it from MainActivity's composition (the production host) as the first child inside the
 * [JiibTheme] boundary — deliberately NOT from [JiibTheme] itself, which is shared with
 * `@Preview` and benchmark hosts.
 */
@Composable
fun SyncSystemBarsToTheme() {
    val t = LocalTokens.current
    val view = LocalView.current
    if (view.isInEditMode) return
    val activity = view.context.findComponentActivity() ?: return
    val dark = isDarkBackdrop(t.bg)
    val scrim = t.bg.toArgb()
    // Keyed on (dark, scrim) so the window call re-fires ONLY on an actual style change — not on
    // every recomposition of the theme boundary.
    LaunchedEffect(dark, scrim) {
        val style = if (dark) {
            SystemBarStyle.dark(scrim)
        } else {
            // scrim AND darkScrim both the bg token — the app never wants a system-picked
            // contrast color.
            SystemBarStyle.light(scrim, scrim)
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
    }
}

// (SyncDialogWindowToTheme, the per-DIALOG-window variant of the sync above, was removed with its
// only consumer, the App Drawer — the remaining popups (PromptDialog, the BedMesh save/load dialogs)
// are in-app overlays that create no window. Recover it from git history if a real
// androidx.compose.ui.window.Dialog ever returns.)

/** Unwrap the [ContextWrapper] chain to the hosting [ComponentActivity] (null in previews/services). */
private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
