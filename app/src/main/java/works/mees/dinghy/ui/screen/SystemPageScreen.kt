package works.mees.dinghy.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.BuildConfig
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.brandTint
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
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
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    SystemPageContent(
        activePrinterName = activeName ?: "",
        versionName = BuildConfig.VERSION_NAME,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
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
 * Field rows (direct-tap nav; no selection state — tap = navigate immediately):
 *   App Settings → Printer Settings → About.
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
    isPrinting: Boolean = false,
    onEmergencyStop: (() -> Unit)? = null,
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
                    // STATIC brand strip wrapped in FocusFrame (Focus-header law, 2026-06-13).
                    // Brand identity content lives in the FocusFrame body; no live telemetry.
                    FocusFrame(
                        title = stringResource(R.string.home_foot_system),
                        icon = DinghyIcons.FootSystem,
                        uDp = grid.uDp,
                        modifier = Modifier.fillMaxSize(),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        SystemFocusContent(
                            activePrinterName = activePrinterName,
                            versionName = versionName,
                        )
                    }
                },
                field = {
                    // Field: dense ListBlock of direct-tap nav rows in D-03 order.
                    ListBlock(modifier = Modifier.weight(1f)) {
                        // 3 rows: direct-tap (onClick navigates immediately; no selection state).
                        items(systemNavRows()) { row ->
                            ListRow(
                                selected = false,   // nav, not a picker — never selected
                                onClick = { onNavigate(row.dest) },
                                uDp = grid.uDp,
                                leadingContent = {
                                    // R23: canonical 0.6U list-row icon.
                                    ListRowIcon(
                                        icon = row.icon,
                                        uDp = grid.uDp,
                                        tint = t.text,
                                    )
                                },
                            ) {
                                // Canonical list label (R22/R11).
                                ListRowLabel(stringResource(row.labelRes))
                            }
                        }

                    }

                    FootButtonBar(
                        uDp = grid.uDp,
                    ) {
                        OutlinedControl(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent, // R5: Back = accent
                        )
                    }
                },
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

        // App version string (Mono metadata — static BuildConfig.VERSION_NAME, not live data).
        Text(
            text = versionName,
            color = t.text2,
            style = DinghyType.dataMeta.toTextStyle(t),
        )

        // Active printer name (user-set display label; no host/IP shown).
        if (activePrinterName.isNotBlank()) {
            Text(
                text = activePrinterName,
                color = t.text,
                style = DinghyType.listLabel.toTextStyle(t),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nav row model (App Settings → Printer Settings → About)
// ─────────────────────────────────────────────────────────────────────────────

private data class SystemNavRow(
    val dest: NavDest,
    val icon: DinghyIcon,
    val labelRes: Int,
)

private fun systemNavRows(): List<SystemNavRow> = listOf(
    SystemNavRow(NavDest.AppSettings,     DinghyIcons.AppSettings,     R.string.system_row_app_settings),
    SystemNavRow(NavDest.PrinterSettings, DinghyIcons.PrinterSettings, R.string.system_row_printer_settings),
    SystemNavRow(NavDest.ManagePrinters,  DinghyIcons.ManagePrinters,  R.string.printer_settings_manage),
    SystemNavRow(NavDest.About,           DinghyIcons.SystemRowAbout,  R.string.system_row_about),
)
