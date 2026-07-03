package works.mees.jiib.ui.screen

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.BuildConfig
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.focus.FocusExplainer
import works.mees.jiib.designsystem.icons.JiibIcon
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.ui.route.NavDest

/**
 * Thin VM-reading wrapper for the System page (Phase 28, D-01). Collects live [AppContainer] state
 * (active printer name) and delegates ALL layout to the stateless [SystemPageContent] seam so the
 * @Preview matrix (WARNING-5) can drive it without a Moonraker connection.
 *
 * NavDest.System is mid-print reachable (D-06) — it is intentionally absent from FOOT_GUN_DESTS.
 * It is in the shell's `screenOwnsEstop` set, so the shell FloatingEStop overlay never shows here;
 * the FocusFrame header glyph morphs into the docked e-stop while printing (isPrinting/
 * onEmergencyStop threaded below).
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
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    SystemPageContent(
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
 * Layout: the canonical [ScreenScaffold] split (portrait stacks ~50/50; landscape Focus | Field 50/50).
 *
 * Focus is STATIC brand identity: the full jiib lockup as a bottom-right watermark (same treatment as
 * the standby Focus mark) over the app version + the jib/jiib brand explainer — no connection state,
 * no live telemetry.
 *
 * Field rows (direct-tap nav; no selection state — tap = navigate immediately):
 *   App Settings → Printer Settings → Manage Printers.
 *
 * E-stop: the FocusFrame header dock morphs to the e-stop while printing — the shell FloatingEStop
 * overlay never shows here (screenOwnsEstop; D-06 / Pitfall 6).
 *
 * @param versionName        [BuildConfig.VERSION_NAME] — static at build time.
 * @param onNavigate         direct-tap navigation lambda.
 * @param onBack             Back foot button exit.
 */
@Composable
fun SystemPageContent(
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

        ScreenScaffold(
            focus = {
                // STATIC brand strip wrapped in FocusFrame (Focus-header law, 2026-06-13).
                // Brand identity content lives in the FocusFrame body; no live telemetry.
                FocusFrame(
                    // Title carries the brand + app version (e.g. "jiib 0.1.0").
                    title = "${stringResource(R.string.app_name)} $versionName",
                    icon = JiibIcons.FootSystem,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxSize(),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    val jibNote = stringResource(R.string.system_focus_jib_note)
                    val jiibNote = stringResource(R.string.system_focus_jiib_note)
                    val block = remember(jibNote, jiibNote) {
                        buildAnnotatedString {
                            append(jibNote)
                            append("\n\n")
                            val jiibStart = length
                            append(jiibNote)
                            val idx = jiibNote.indexOf("jiib")
                            if (idx >= 0) {
                                addStyle(
                                    SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic),
                                    jiibStart + idx,
                                    jiibStart + idx + "jiib".length,
                                )
                            }
                        }
                    }
                    FocusExplainer(
                        text = block,
                        color = t.text,
                        maxSp = 34f,
                        watermark = {
                            BoxWithConstraints(Modifier.fillMaxSize()) {
                                val markSize = minOf(maxWidth, maxHeight) * 0.5f
                                Image(
                                    painter = painterResource(R.drawable.jiib_lockup),
                                    contentDescription = null,
                                    contentScale = ContentScale.Fit,
                                    colorFilter = ColorFilter.tint(t.accent2),
                                    alpha = 0.45f,
                                    modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
                                )
                            }
                        },
                    )
                }
            },
            field = {
                // Field: dense ListBlock of direct-tap nav rows in D-03 order.
                ListBlock(modifier = Modifier.weight(1f)) {
                    // direct-tap rows (onClick navigates immediately; no selection state).
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
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = JiibIcons.Back,
                            onClick = onBack,
                            intent = Intent.Accent, // R5: Back = accent
                        ),
                    ),
                )
            },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Nav row model (App Settings → Printer Settings → Manage Printers)
// ─────────────────────────────────────────────────────────────────────────────

private data class SystemNavRow(
    val dest: NavDest,
    val icon: JiibIcon,
    val labelRes: Int,
)

private fun systemNavRows(): List<SystemNavRow> = listOf(
    SystemNavRow(NavDest.AppSettings,     JiibIcons.AppSettings,     R.string.system_row_app_settings),
    SystemNavRow(NavDest.PrinterSettings, JiibIcons.PrinterSettings, R.string.system_row_printer_settings),
    SystemNavRow(NavDest.ManagePrinters,  JiibIcons.ManagePrinters,  R.string.printer_settings_manage),
)
