package works.mees.dinghy.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.brandTint
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **About** drawer destination (15.2-04, D-05) — the 4th IA tile and the APP-GLOBAL remainder of the
 * dissolved monolithic Settings (D-01). Where Printers/Theme/Settings are per-PRINTER, About carries the
 * items that belong to the whole app: the version/build, and a "Developer" subsection homing the hidden
 * dev-widget enable toggle (D-08).
 *
 * ## The dev-enable toggle (D-05/D-08/HIGH-5) — the durable control
 * This toggle is the DURABLE home for the dev theme cyclers (15.2-02). It drives
 * [AppContainer.devCyclerEnabled] via [AppContainer.setDevCyclerEnabled]:
 *  - ON  → the floating Style/Size cyclers appear over every screen (the conformance accelerant, D-09).
 *  - OFF → the cyclers hide AND any active transient override is CLEARED ([setDevCyclerEnabled] does this,
 *    HIGH-5) so the real saved per-profile theme returns — the app is never stranded in an overridden
 *    look with the dismiss control hidden.
 *
 * Per RESEARCH Open-Q1 it is a plain toggle in a discoverable-but-out-of-the-way "Developer" subsection —
 * NOT a tap-count easter egg, NOT a `BuildConfig.DEBUG` gate (the owner sideloads RELEASE APKs, so the
 * flag must be a RUNTIME control, not a build type). The default is OFF (the 15.2-02 TEMP default-true
 * flip was reverted in 15.2-04 Task 1, MEDIUM-3) so the cyclers never appear by default in release.
 *
 * ## Layout (the DevicesScreen/PrintersScreen template)
 * [ScreenScaffold] Field-only scrollable content + a green gutter Back (the swipe-up drawer is suppressed
 * for this FFG-exempt scrollable surface, like Settings/Theme). Every color via [LocalTokens]; fsSp font
 * scale ([[dinghy-font-sizes-too-small]] — 15sp metadata floor, 17–18 body, 20–22 titles).
 *
 * @param container the process-scoped service-locator (constructs nothing here).
 * @param onBack the explicit neutral gutter Back exit (D-10).
 */
@Composable
fun AboutScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val devEnabled by container.devCyclerEnabled.collectAsStateWithLifecycle(initialValue = false)

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    SectionHeader("About")

                    // ============================ APP ==========================================
                    AboutWordmark()
                    InfoRow(
                        label = "Version",
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    Text(
                        text = stringResource(R.string.about_tagline),
                        color = t.text3,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, t.fs).sp,
                    )
                    // D-11: the jib-explainer note, directly below the kept tagline (D-10).
                    Text(
                        text = stringResource(R.string.about_jib_note),
                        color = t.text3,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, t.fs).sp,
                    )

                    // ============================ DEVELOPER ====================================
                    // The hidden dev-widget enable toggle (D-08) — discoverable but out of the way.
                    SectionLabel("Developer")
                    DevEnableRow(
                        checked = devEnabled,
                        onToggle = { container.setDevCyclerEnabled(it) },
                    )

                    // Bottom breathing room so the last control clears the scroll edge.
                    Box(Modifier.height(24.dp))
                }
            },
            gutter = {
                OutlinedControl(
                    label = "Back",
                    onClick = onBack,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    intent = Intent.Neutral, // D-10: plain nav spends no safety color (matches Move).
                    symbol = "arrow_back",
                )
            },
        )
    }
}

/**
 * The jiib wordmark that fronts the About APP block (D-05) — wordmark only, NOT the stacked lockup.
 * Tinted from the theme accent with the D-06 contrast-floor guard ([brandTint] falls back to `t.text`
 * when the accent would wash out against `t.bg`).
 *
 * STATELESS and AppContainer-FREE on purpose — reads only [LocalTokens] — so it is the preview seam
 * [works.mees.dinghy.preview.BrandPreviews] drives across the six theme combos (PREVIEW_AND_TOKENS / D-03).
 *
 * Sized by RATIO (LAYOUT.md ratio-only rule) to carry roughly the visual weight of the 20sp
 * [SectionLabel] it replaces: it fills 55% of the width and wraps its (433×289) height un-stretched.
 */
@Composable
internal fun AboutWordmark() {
    val t = LocalTokens.current
    Icon(
        painter = painterResource(R.drawable.jiib_wordmark),
        contentDescription = stringResource(R.string.cd_jiib_logo),
        tint = brandTint(t.accent, t.bg, t.text),
        modifier = Modifier
            .fillMaxWidth(0.55f)
            .padding(top = 8.dp),
    )
}

/**
 * The dev-enable toggle row (D-08) — drives [AppContainer.setDevCyclerEnabled]. Reads the current state as
 * an intent-colored ON/OFF pill (color redundant to the word, THEMING). The sub-label tells the user what
 * it does AND that OFF restores the real theme (HIGH-5 made visible).
 */
@Composable
private fun DevEnableRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (checked) t.accentLine else t.outline
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Theme dev widgets",
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(18f, t.fs).sp,
            )
            Text(
                text = if (checked) {
                    "Floating Style/Size cyclers shown. Turn off to hide them and restore your theme."
                } else {
                    "Show the floating Style/Size theme cyclers over every screen."
                },
                color = t.text3,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }
        val pillShape = RoundedCornerShape(t.rPill)
        Box(
            Modifier
                .clip(pillShape)
                .border(BorderStroke(2.dp, if (checked) t.accentLine else t.outline), pillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (checked) "ON" else "OFF",
                color = if (checked) t.accent else t.text2,
                fontFamily = Geist,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}

/** A label : monospace-value info row (version/build). */
@Composable
private fun InfoRow(label: String, value: String) {
    val t = LocalTokens.current
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = t.text,
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
        )
        Text(
            text = value,
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text,
        fontFamily = Geist,
        fontWeight = FontWeight.Bold,
        fontSize = fsSp(22f, t.fs).sp,
    )
}

@Composable
private fun SectionLabel(text: String) {
    val t = LocalTokens.current
    Text(
        text = text,
        color = t.text2,
        fontFamily = Geist,
        fontWeight = FontWeight.SemiBold,
        fontSize = fsSp(20f, t.fs).sp,
        modifier = Modifier.padding(top = 8.dp),
    )
}
