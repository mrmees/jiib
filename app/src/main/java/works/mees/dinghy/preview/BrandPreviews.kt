package works.mees.dinghy.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import works.mees.dinghy.ui.screen.AboutWordmark
import works.mees.dinghy.ui.screen.SplashBrandLockup

/**
 * Brand-surface previews (18.2-04 / D-04, D-05, D-06) — the theme matrix for the two rebranded in-app
 * marks: the splash jiib **lockup** and the About jiib **wordmark**.
 *
 * ## Preview the STATELESS seams, NOT the screens (PREVIEW_AND_TOKENS / D-03)
 * `SplashScreen` and `AboutScreen` both take a required `container: AppContainer`, and there is NO fake
 * AppContainer in main source — instantiating one in a `@Preview` pulls the live service-locator graph
 * and violates preview-safety. So this file drives the small AppContainer-FREE stateless seams that
 * Task 2/Task 3 factored out — [SplashBrandLockup] and [AboutWordmark] — which read only `LocalTokens`.
 *
 * ## Why the six-combo matrix (D-06 visual gate)
 * Both marks tint via `brandTint(t.accent, t.bg, t.text)`, which falls back to `t.text` when the accent
 * would wash out against the bg. Rendering both marks across all six {Colorful, Simple, HighContrast} ×
 * {dark, light} combos is exactly how the D-06 accent-vs-bg contrast risk gets eyeballed BEFORE device.
 *
 * No live Moonraker, no AppContainer (SC-1) — the seams are pure token consumers.
 */
@Nexus7Previews
@Composable
private fun BrandColorfulDark() = BrandMatrixCell(colorfulDark)

@Nexus7Previews
@Composable
private fun BrandColorfulLight() = BrandMatrixCell(colorfulLight)

@Nexus7Previews
@Composable
private fun BrandSimpleDark() = BrandMatrixCell(simpleDark)

@Nexus7Previews
@Composable
private fun BrandSimpleLight() = BrandMatrixCell(simpleLight)

@Nexus7Previews
@Composable
private fun BrandHighContrastDark() = BrandMatrixCell(highContrastDark)

@Nexus7Previews
@Composable
private fun BrandHighContrastLight() = BrandMatrixCell(highContrastLight)

/**
 * The fs = L overflow shot ([fsLargeSeed]) — catches any clipping of the marks at the LARGEST text size.
 * fs is injected via the seed's `fs` field; `@Preview(fontScale = …)` is a verified NO-OP here.
 */
@Nexus7Previews
@Composable
private fun BrandFsLargeOverflow() = BrandMatrixCell(fsLargeSeed)

/** Both brand marks stacked in one PreviewBox seed — the per-combo matrix cell. */
@Composable
private fun BrandMatrixCell(tuple: works.mees.dinghy.theme.ThemePrefs.ThemeTuple) {
    PreviewBox(tuple) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Splash front-door lockup (D-04) and About wordmark (D-05), both brandTint-guarded (D-06).
            SplashBrandLockup()
            AboutWordmark()
        }
    }
}
