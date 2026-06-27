package works.mees.jiib.ui.calibration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.calibration.ProbeHubHolder
import works.mees.jiib.calibration.ProbeTool
import works.mees.jiib.calibration.ProbeToolEntry
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.components.ListRowLabel
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.icons.JiibIcons
import works.mees.jiib.designsystem.layout.FocusInset
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.ThemeTokens
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.theme.fsSp

/**
 * Thin VM-reading wrapper for the ProbeHub. Collects `holder.tools` + owns `selected`
 * state, then delegates ALL layout to the stateless [ProbeHubContent] overload (WARNING-5
 * preview seam — the @Preview matrix targets [ProbeHubContent], not this screen).
 *
 * @param holder  the headless hub holder (supported-first tool list, live off capabilities).
 * @param onOpen  invoked with the tapped [ProbeTool] — navigates to the tool's NavDest
 *                sub-route via navController.navigate(tool.toNavDest()).
 * @param onBack  the neutral Back footer exit.
 */
@Composable
fun ProbeHubScreen(
    holder: ProbeHubHolder,
    container: AppContainer,
    onOpen: (ProbeTool) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tools by holder.tools.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var selected by remember { mutableStateOf<ProbeTool?>(null) }
    // D-05: pre-select the first entry so Focus is never empty on initial render.
    LaunchedEffect(tools) {
        if (selected == null) selected = tools.firstOrNull()?.tool
    }

    ProbeHubContent(
        tools = tools,
        selected = selected,
        isPrinting = isPrinting,
        onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
        onSelect = { selected = it },
        onOpen = onOpen,
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Stateless hub layout — the @Preview matrix targets this composable (no VM, no live Moonraker).
 *
 * Field = [ListBlock] of [ListRow]s (supported-first, greyed-if-unsupported per D-06 — all tools
 * always render when showUnsupportedTools is true, unsupported dimmed in [ThemeTokens.text3] but
 * still selectable and openable).
 * Focus = [FocusFrame] with the selected tool's icon (UAT-1: ~75% of U), title, and the
 * author-written description (fills the content area). The Open action lives in the Field foot bar
 * after Back, not in the Focus.
 *
 * D-05: the thin [ProbeHubScreen] wrapper ensures [selected] is never null on first render
 * (pre-select logic via LaunchedEffect). This composable handles null gracefully with an empty Focus.
 */
@Composable
fun ProbeHubContent(
    tools: List<ProbeToolEntry>,
    selected: ProbeTool?,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onSelect: (ProbeTool) -> Unit,
    onOpen: (ProbeTool) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    // Hub title/icon: the Focus header tracks the SELECTED tool, not a static label.
                    // Falls back to the hub identity only on the (defensive) null-selection frame
                    // before D-05 pre-select resolves.
                    val headerTitle = stringResource(
                        selected?.let { probeToolTitleRes(it) } ?: R.string.probe_hub_title,
                    )
                    val headerIcon = selected?.let { probeToolIconToken(it) }
                        ?: JiibIcons.RoutineProbeCalibrate
                    FocusFrame(
                        title = headerTitle,
                        icon = headerIcon,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                        contentInset = FocusInset / 2,
                    ) {
                        if (selected != null) {
                            HubToolFocus(
                                tool = selected,
                                t = t,
                            )
                        }
                    }
                },
                field = {
                    // Field: ListBlock suppresses swipe-up App Drawer automatically (scrollable Field).
                    ListBlock(modifier = Modifier.weight(1f)) {
                        items(tools, key = { it.tool.name }) { entry ->
                            ListRow(
                                selected = entry.tool == selected,
                                onClick = { onSelect(entry.tool) },
                                uDp = grid.uDp,
                                leadingContent = {
                                    // R23: canonical 0.6U list-row icon (registry-routed, D-16).
                                    ListRowIcon(
                                        icon = probeToolIconToken(entry.tool),
                                        uDp = grid.uDp,
                                        tint = if (entry.isSupported) t.accent else t.text3,
                                    )
                                },
                            ) {
                                // Canonical list label (R22); D-06 dim survives via the color override.
                                ListRowLabel(
                                    text = stringResource(probeToolTitleRes(entry.tool)),
                                    color = if (entry.isSupported) t.text else t.text3,
                                )
                            }
                        }
                    }
                    FootButtonBar(
                        uDp = grid.uDp,
                        actions = buildList {
                            add(FootAction(
                                label = stringResource(R.string.common_back),
                                icon = JiibIcons.Back,
                                onClick = onBack,
                                intent = Intent.Accent, // R5: Back = accent, first
                                contentDescription = stringResource(R.string.common_back),
                            ))
                            // Open the selected tool — docked after Back. Go intent (R5: the expected
                            // action). 2 actions → the bar renders icon+text.
                            selected?.let { sel ->
                                add(FootAction(
                                    label = stringResource(R.string.calibration_open_routine),
                                    icon = JiibIcons.RoutineOpen,
                                    onClick = { onOpen(sel) },
                                    intent = Intent.Go,
                                    contentDescription = stringResource(R.string.calibration_open_routine),
                                ))
                            }
                        },
                    )
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub Focus content (the FocusFrame interior)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HubToolFocus(
    tool: ProbeTool,
    t: ThemeTokens,
) {
    // The description owns the WHOLE Focus content area. The icon + title live in the FocusFrame
    // header. Description is VERTICALLY CENTERED (owner UAT 2026-06-15: top-aligned floats high on
    // big screens — centering reads better across device sizes). TextAutoSize caps it at 22sp (one
    // notch above the 20sp list standard) and SHRINKS to fit on tight screens.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = stringResource(probeToolDescRes(tool)),
            style = JiibType.body.toTextStyle(t).copy(
                color = t.text2,
            ),
            autoSize = TextAutoSize.StepBased(
                minFontSize = fsSp(15f, t.fs).sp, // type-ramp floor — shrink stops here
                maxFontSize = fsSp(22f, t.fs).sp, // one notch above the 20sp list standard
                stepSize = 1.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Icon token mapping (owner-confirmed tokens from Task 0)
// ─────────────────────────────────────────────────────────────────────────────

/** Maps a [ProbeTool] to its owner-confirmed [JiibIcons] token (Task 0). */
internal fun probeToolIconToken(tool: ProbeTool) = when (tool) {
    ProbeTool.Z_OFFSET           -> JiibIcons.RoutineProbeCalibrate
    ProbeTool.PROBE_TEST         -> JiibIcons.ProbeTestTool
    ProbeTool.APPLY_BABYSTEP     -> JiibIcons.Babystep
    ProbeTool.EDDY_CALIBRATE     -> JiibIcons.EddyCalibrate
    ProbeTool.EDDY_TAP           -> JiibIcons.EddyTap
    ProbeTool.EDDY_DRIVE_CURRENT -> JiibIcons.EddyDriveCurrent
}

// ─────────────────────────────────────────────────────────────────────────────
// String resource helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Maps a [ProbeTool] to its title string resource ID. */
internal fun probeToolTitleRes(tool: ProbeTool): Int = when (tool) {
    ProbeTool.Z_OFFSET           -> R.string.probe_tool_z_offset_calibrate_title
    ProbeTool.PROBE_TEST         -> R.string.probe_tool_test_title
    ProbeTool.APPLY_BABYSTEP     -> R.string.probe_tool_babystep_title
    ProbeTool.EDDY_CALIBRATE     -> R.string.probe_tool_eddy_calibrate_title
    ProbeTool.EDDY_TAP           -> R.string.probe_tool_eddy_tap_title
    ProbeTool.EDDY_DRIVE_CURRENT -> R.string.probe_tool_eddy_drive_current_title
}

/** Maps a [ProbeTool] to its description string resource ID. */
private fun probeToolDescRes(tool: ProbeTool): Int = when (tool) {
    ProbeTool.Z_OFFSET           -> R.string.probe_tool_z_offset_calibrate_desc
    ProbeTool.PROBE_TEST         -> R.string.probe_tool_test_desc
    ProbeTool.APPLY_BABYSTEP     -> R.string.probe_tool_babystep_desc
    ProbeTool.EDDY_CALIBRATE     -> R.string.probe_tool_eddy_calibrate_desc
    ProbeTool.EDDY_TAP           -> R.string.probe_tool_eddy_tap_desc
    ProbeTool.EDDY_DRIVE_CURRENT -> R.string.probe_tool_eddy_drive_current_desc
}
