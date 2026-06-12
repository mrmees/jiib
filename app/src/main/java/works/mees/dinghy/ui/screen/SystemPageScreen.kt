package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.brandTint
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp
import works.mees.dinghy.ui.route.NavDest

/**
 * Thin VM-reading wrapper for the System page (Phase 28, D-01). Collects live [AppContainer] state
 * (active printer name) and delegates ALL layout to the stateless [SystemPageContent] seam so the
 * @Preview matrix (WARNING-5) can drive it without a Moonraker connection.
 *
 * NavDest.System is mid-print reachable (D-06) — it is intentionally absent from FOOT_GUN_DESTS
 * and does NOT render its own FloatingEStop. The shell-level FloatingEStop fires on this screen.
 *
 * @param container  the process-scoped service-locator (activeName + session state).
 * @param onNavigate called with a [NavDest] when a dense row is tapped (direct-tap nav, no picker).
 * @param onBack     the neutral Back footer exit.
 */
@Composable
fun SystemPageScreen(
    container: AppContainer,
    onNavigate: (NavDest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val activeName by container.activeName.collectAsStateWithLifecycle(initialValue = null)

    SystemPageContent(
        activePrinterName = activeName ?: "",
        versionName = BuildConfig.VERSION_NAME,
        onNavigate = onNavigate,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless System page layout — the @Preview matrix targets this composable (WARNING-5 preview seam).
 *
 * Layout (D-02):
 *  - Portrait:  Focus strip at 20% of screen HEIGHT (focusGrow=0.2f, fieldGrow=0.8f).
 *  - Landscape: Focus column at 40% of content WIDTH (focusGrow=0.4f, fieldGrow=0.6f).
 *
 * Focus is STATIC brand identity (jiib lockup + version + active printer name) — no connection
 * state, no live telemetry.
 *
 * Field rows in D-03 order (direct-tap nav; no selection state — tap = navigate immediately):
 *   Printers → Settings → Theme → System Info → About → Power (inert stub, D-08).
 *
 * Shell-level FloatingEStop applies (this screen does NOT render its own e-stop; D-06 / Pitfall 6).
 *
 * @param activePrinterName  display name of the active printer profile (user-set label).
 * @param versionName        [BuildConfig.VERSION_NAME] — static at build time.
 * @param onNavigate         direct-tap navigation lambda.
 * @param onBack             Back foot button exit.
 */
@Composable
fun SystemPageContent(
    activePrinterName: String,
    versionName: String,
    onNavigate: (NavDest) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        val landscape = maxWidth > maxHeight

        // D-02 Focus ratio cap: 40% of WIDTH in landscape, 20% of HEIGHT in portrait.
        val focusGrow = if (landscape) 0.4f else 0.2f
        val fieldGrow = if (landscape) 0.6f else 0.8f

        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focusGrow = focusGrow,
                fieldGrow = fieldGrow,
                focus = {
                    // STATIC brand strip — jiib lockup + version + active printer name.
                    // No connection state. No live telemetry. (D-02 Focus content rule)
                    SystemFocusContent(
                        activePrinterName = activePrinterName,
                        versionName = versionName,
                    )
                },
                field = {
                    // Field: dense ListBlock of direct-tap nav rows in D-03 order.
                    ListBlock(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        // Rows 1-5: direct-tap (onClick navigates immediately; no selection state).
                        items(systemNavRows()) { row ->
                            ListRow(
                                dense = true,
                                selected = false,   // nav, not a picker — never selected
                                onClick = { onNavigate(row.dest) },
                                uDp = grid.uDp,
                                leadingContent = {
                                    DinghyIconView(
                                        icon = row.icon,
                                        contentDescription = null,  // row label describes the row
                                        tint = t.text,
                                        sizeDp = 22.dp,
                                        modifier = Modifier.padding(end = 8.dp),
                                    )
                                },
                            ) {
                                Text(
                                    text = stringResource(row.labelRes),
                                    color = t.text,
                                    fontFamily = Geist,
                                    fontSize = fsSp(17f, t.fs).sp,
                                )
                            }
                        }

                        // Row 6: Power stub — D-08. No onClick. stop-tinted at alpha 0.38; inert.
                        item {
                            PowerStubRow()
                        }
                    }

                    FootButtonBar(
                        uDp = grid.uDp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        OutlinedControl(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                        )
                    }
                },
                gutter = null,  // LAW: rebuilt screens always null the gutter.
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Focus content (static brand strip — stateless, no Moonraker)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SystemFocusContent(
    activePrinterName: String,
    versionName: String,
) {
    val t = LocalTokens.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // jiib wordmark — accent-tinted via brandTint (same pattern as AboutWordmark in AboutScreen).
        Icon(
            painter = painterResource(R.drawable.jiib_wordmark),
            contentDescription = stringResource(R.string.cd_jiib_logo),
            tint = brandTint(t.accent, t.bg, t.text),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .padding(bottom = 8.dp),
        )

        // App version string (Geist Mono 15sp — static BuildConfig.VERSION_NAME, not live data).
        Text(
            text = versionName,
            color = t.text2,
            fontFamily = GeistMono,
            fontSize = fsSp(15f, t.fs).sp,
        )

        // Active printer name (Geist 17sp — user-set display label; no host/IP shown).
        if (activePrinterName.isNotBlank()) {
            Text(
                text = activePrinterName,
                color = t.text,
                fontFamily = Geist,
                fontWeight = FontWeight.SemiBold,
                fontSize = fsSp(17f, t.fs).sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Power stub row (D-08 — greyed/inert, no onClick, stop-tinted at 0.38f)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun PowerStubRow() {
    val t = LocalTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DinghyIconView(
            icon = DinghyIcons.SystemRowPower,
            contentDescription = null,
            tint = t.stop.copy(alpha = 0.38f),
            sizeDp = 22.dp,
            modifier = Modifier.padding(end = 8.dp),
        )
        Text(
            text = stringResource(R.string.system_row_power),
            color = t.stop.copy(alpha = 0.38f),
            fontFamily = Geist,
            fontSize = fsSp(17f, t.fs).sp,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.system_row_power_sub),
            color = t.text2.copy(alpha = 0.38f),
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nav row model (D-03 order: Printers → Settings → Theme → System Info → About)
// ─────────────────────────────────────────────────────────────────────────────

private data class SystemNavRow(
    val dest: NavDest,
    val icon: DinghyIcon,
    val labelRes: Int,
)

private fun systemNavRows(): List<SystemNavRow> = listOf(
    SystemNavRow(NavDest.Devices,    DinghyIcons.SystemRowPrinters, R.string.system_row_printers),
    SystemNavRow(NavDest.Settings,   DinghyIcons.SystemRowSettings, R.string.system_row_settings),
    SystemNavRow(NavDest.Theme,      DinghyIcons.SystemRowTheme,    R.string.system_row_theme),
    SystemNavRow(NavDest.SystemInfo, DinghyIcons.SysInfoTile,       R.string.system_row_sysinfo),
    SystemNavRow(NavDest.About,      DinghyIcons.SystemRowAbout,    R.string.system_row_about),
)
