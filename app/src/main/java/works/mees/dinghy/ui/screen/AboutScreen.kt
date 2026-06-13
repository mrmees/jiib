package works.mees.dinghy.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.brandTint
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The **About** drawer destination (15.2-04, D-05) — dense C6-exempt restyle (28-07, D-11).
 * Restyled to a single `verticalScroll` Column that fits one page at M text size (D-11). Keeps
 * the jiib wordmark (brandTint accent-tinted, WCAG-3:1 floor), version string, tagline, jib-note,
 * and the dev-enable toggle (persistence unchanged — writeScope via `container.setDevCyclerEnabled`).
 *
 * ## Layout (D-11)
 * Field-only `ScreenScaffold`. Back foot is the last control inside the scrolling
 * Column — always visible at the foot of the one-page layout. All persistence routes through the
 * container writeScope intent helpers — never a composition scope (T-28-07-02). The dev-enable
 * toggle keeps its existing writeScope route.
 *
 * @param container the process-scoped service-locator.
 * @param onBack the explicit neutral Back exit.
 */
@Composable
fun AboutScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val devEnabled by container.devCyclerEnabled.collectAsStateWithLifecycle(initialValue = false)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    AboutContent(
        devEnabled = devEnabled,
        onDevToggle = { container.setDevCyclerEnabled(it) },
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * The STATELESS content seam (PREVIEW_AND_TOKENS preview-first LAW) — pure inputs, no
 * AppContainer/Moonraker, so the `@Preview` matrix in [works.mees.dinghy.preview.AboutPreviews]
 * drives every theme + dev-enable state without a live session.
 */
@Composable
fun AboutContent(
    devEnabled: Boolean,
    onDevToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = stringResource(R.string.system_row_about),
                    icon = DinghyIcons.SystemRowAbout,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {}
            },
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── jiib wordmark ─────────────────────────────────────────────────────────
                    // D-05/D-06: accent-tinted via brandTint WCAG-3:1 contrast-floor helper.
                    AboutWordmark()

                    // ── App info ──────────────────────────────────────────────────────────────
                    InfoRow(
                        label = stringResource(R.string.about_version),
                        value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    )
                    Text(
                        text = stringResource(R.string.about_tagline),
                        color = LocalTokens.current.text3,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, LocalTokens.current.fs).sp,
                    )
                    // D-11: the jib-explainer note, directly below the kept tagline (D-10).
                    Text(
                        text = stringResource(R.string.about_jib_note),
                        color = LocalTokens.current.text3,
                        fontFamily = Geist,
                        fontSize = fsSp(15f, LocalTokens.current.fs).sp,
                    )

                    // ── Developer section ─────────────────────────────────────────────────────
                    // The hidden dev-widget enable toggle (D-08) — discoverable but out of the way.
                    // 1U height floor applied to the clickable row (owner UAT ruling, Phase 28, 2026-06-12).
                    DevEnableRow(
                        checked = devEnabled,
                        onToggle = onDevToggle,
                        uDp = grid.uDp,
                    )

                    // ── Back foot ────────────────────────────────────────────────────────────
                    // Neutral — plain nav spends no safety color (D-10). Inside the Column so it
                    // is always visible at the foot of the one-page scroll.
                    OutlinedControl(
                        label = stringResource(R.string.common_back),
                        onClick = onBack,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        intent = Intent.Accent, // R5: Back = accent
                    )
                }
            },
            modifier = modifier,
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
 * The dev-enable toggle row (D-08) — drives [AppContainer.setDevCyclerEnabled]. Persistence stays
 * on the container writeScope intent helper — never a composition scope (T-28-07-02). The sub-label
 * tells the user what it does AND that OFF restores the real theme (HIGH-5 made visible).
 */
@Composable
private fun DevEnableRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCard)
    val outline = if (checked) t.accentLine else t.outline
    // 1U height floor — owner UAT ruling, Phase 28, 2026-06-12.
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = uDp)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .clickable { onToggle(!checked) }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.about_dev_widgets),
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
            )
            Text(
                text = if (checked) {
                    stringResource(R.string.about_dev_widgets_on)
                } else {
                    stringResource(R.string.about_dev_widgets_off)
                },
                color = t.text3,
                fontFamily = Geist,
                fontSize = fsSp(15f, t.fs).sp,
            )
        }
        val pillShape = RoundedCornerShape(t.rPill)
        androidx.compose.foundation.layout.Box(
            Modifier
                .clip(pillShape)
                .border(BorderStroke(2.dp, if (checked) t.accentLine else t.outline), pillShape)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = if (checked) stringResource(R.string.common_on) else stringResource(R.string.common_off),
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
