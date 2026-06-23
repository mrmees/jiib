package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.R
import works.mees.dinghy.calibration.CalibrationHubHolder
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.RoutineEntry
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.FocusInset
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.fsSp

/**
 * Thin VM-reading wrapper for the CalibrationHub. Collects `holder.routines` + owns `selected`
 * state, then delegates ALL layout to the stateless [CalibrationHubContent] overload (WARNING-5
 * preview seam — the @Preview matrix targets [CalibrationHubContent], not this screen).
 *
 * @param holder  the headless hub holder (supported-first routine list, live off capabilities).
 * @param onOpen  invoked with the tapped [CalibrationRoutine] — navigates to the routine's NavDest
 *                sub-route via navController.navigate(routine.toNavDest()) (D-07, Phase 27).
 * @param onBack  the neutral Back footer exit.
 */
@Composable
fun CalibrationHubScreen(
    holder: CalibrationHubHolder,
    container: AppContainer,
    onOpen: (CalibrationRoutine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var selected by remember { mutableStateOf<CalibrationRoutine?>(null) }
    // D-05: pre-select the first entry so Focus is never empty on initial render.
    LaunchedEffect(routines) {
        if (selected == null) selected = routines.firstOrNull()?.routine
    }

    CalibrationHubContent(
        routines = routines,
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
 * Field = [ListBlock] of [ListRow]s (supported-first, greyed-if-unsupported per D-06 — all 5 always
 * render, unsupported dimmed in [ThemeTokens.text3] but still selectable and openable).
 * Focus = [FocusFrame] with the selected routine's icon (UAT-1: ~75% of U), title, and the
 * author-written description (fills the content area). The Open action lives in the Field foot bar
 * after Back (owner 2026-06-23), not in the Focus.
 *
 * D-05: the thin [CalibrationHubScreen] wrapper ensures [selected] is never null on first render
 * (pre-select logic via LaunchedEffect). This composable handles null gracefully with an empty Focus.
 */
@Composable
fun CalibrationHubContent(
    routines: List<RoutineEntry>,
    selected: CalibrationRoutine?,
    isPrinting: Boolean = false,
    onEmergencyStop: () -> Unit = {},
    onSelect: (CalibrationRoutine) -> Unit,
    onOpen: (CalibrationRoutine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        Box(Modifier.fillMaxSize()) {
            ScreenScaffold(
                focus = {
                    // Hub title/icon law (2026-06-13): the Focus header tracks the SELECTED routine,
                    // not a static "Calibration" label. Falls back to the launcher identity only on
                    // the (defensive) null-selection frame before D-05 pre-select resolves.
                    val headerTitle = stringResource(
                        selected?.let { routineTitleRes(it) } ?: R.string.cd_launcher_calibration,
                    )
                    val headerIcon = selected?.let { routineIconToken(it) }
                        ?: DinghyIcons.LauncherCalibration
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
                        contentInset = FocusInset / 2, // tighter than the 16dp default (owner UAT 2026-06-13)
                    ) {
                        if (selected != null) {
                            HubRoutineFocus(
                                routine = selected,
                                t = t,
                            )
                        }
                    }
                },
                field = {
                    // Field: ListBlock suppresses swipe-up App Drawer automatically (scrollable Field).
                    ListBlock(modifier = Modifier.weight(1f)) {
                        items(routines, key = { it.routine.name }) { entry ->
                            ListRow(
                                selected = entry.routine == selected,
                                onClick = { onSelect(entry.routine) },
                                uDp = grid.uDp,
                                leadingContent = {
                                    // R23: canonical 0.6U list-row icon (registry-routed, D-16).
                                    ListRowIcon(
                                        icon = routineIconToken(entry.routine),
                                        uDp = grid.uDp,
                                        tint = if (entry.isSupported) t.accent2 else t.text3,
                                    )
                                },
                            ) {
                                // Canonical list label (R22); D-06 dim survives via the color override.
                                ListRowLabel(
                                    text = stringResource(routineTitleRes(entry.routine)),
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
                                icon = DinghyIcons.Back,
                                onClick = onBack,
                                intent = Intent.Accent, // R5: Back = accent
                                contentDescription = stringResource(R.string.common_back),
                            ))
                            // Open the selected routine — docked after Back (owner 2026-06-23, moved
                            // out of the Focus). Go intent (R5: the expected action). 2 actions → the
                            // bar renders icon+text, so the play_arrow glyph + "Open" label both show.
                            selected?.let { sel ->
                                add(FootAction(
                                    label = stringResource(R.string.calibration_open_routine),
                                    icon = DinghyIcons.RoutineOpen,
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
private fun HubRoutineFocus(
    routine: CalibrationRoutine,
    t: ThemeTokens,
) {
    // The description now owns the WHOLE Focus content area — the Open button moved to the Field
    // foot bar (owner 2026-06-23). The icon + title live in the FocusFrame header. Description is
    // VERTICALLY CENTERED (owner UAT 2026-06-15: a top-aligned text body floats high on big screens —
    // centering reads better across device sizes). TextAutoSize caps it at 22sp (one notch above the
    // 20sp list standard — owner 2026-06-23, using the space the Open button vacated) and SHRINKS to
    // fit on tight screens — never grows past 22sp.
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        BasicText(
            text = stringResource(routineDescRes(routine)),
            style = DinghyType.body.toTextStyle(t).copy(
                color = t.text2,
            ),
            autoSize = TextAutoSize.StepBased(
                minFontSize = fsSp(15f, t.fs).sp, // metadata floor (THEMING type ramp) — shrink stops here
                maxFontSize = fsSp(22f, t.fs).sp, // one notch above the 20sp list standard — never grows past it
                stepSize = 1.sp,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Icon token mapping (owner-confirmed tokens from 27-01)
// ─────────────────────────────────────────────────────────────────────────────

/** Maps a [CalibrationRoutine] to its owner-confirmed [DinghyIcons] token (27-01). */
internal fun routineIconToken(routine: CalibrationRoutine) = when (routine) {
    CalibrationRoutine.PROBE_CALIBRATE -> DinghyIcons.RoutineProbeCalibrate
    CalibrationRoutine.BED_MESH -> DinghyIcons.RoutineBedMesh
    CalibrationRoutine.SCREWS_TILT -> DinghyIcons.RoutineScrewsTilt
    CalibrationRoutine.Z_TILT -> DinghyIcons.RoutineZTilt
    CalibrationRoutine.QUAD_GANTRY_LEVEL -> DinghyIcons.RoutineQgl
}

// ─────────────────────────────────────────────────────────────────────────────
// String resource helpers
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Shared calibration utilities (used by BedMeshScreen, ScrewsTiltScreen, TiltScreen,
// ProbeCalibrateScreen — kept internal to the ui.calibration package)
// ─────────────────────────────────────────────────────────────────────────────

/** Intent → outline color mapping. Shared by pre-redesign calibration screen helper buttons. */
internal fun intentColor(intent: Intent, t: ThemeTokens) = when (intent) {
    Intent.Neutral -> t.outline
    Intent.Accent -> t.accentLine
    Intent.Warn -> t.heat
    Intent.Danger -> t.stop
    Intent.Go -> t.go
}

/** Maps a [CalibrationRoutine] to its title string resource ID. `internal` so the calibration
 *  sub-screens can reuse it for their FocusFrame header title (Focus-header law). */
internal fun routineTitleRes(routine: CalibrationRoutine): Int = when (routine) {
    CalibrationRoutine.PROBE_CALIBRATE -> R.string.calibration_routine_probe_title
    CalibrationRoutine.BED_MESH -> R.string.calibration_routine_mesh_title
    CalibrationRoutine.SCREWS_TILT -> R.string.calibration_routine_screws_title
    CalibrationRoutine.Z_TILT -> R.string.calibration_routine_ztilt_title
    CalibrationRoutine.QUAD_GANTRY_LEVEL -> R.string.calibration_routine_qgl_title
}

/** Maps a [CalibrationRoutine] to its description string resource ID. */
private fun routineDescRes(routine: CalibrationRoutine): Int = when (routine) {
    CalibrationRoutine.PROBE_CALIBRATE -> R.string.calibration_routine_probe_desc
    CalibrationRoutine.BED_MESH -> R.string.calibration_routine_mesh_desc
    CalibrationRoutine.SCREWS_TILT -> R.string.calibration_routine_screws_desc
    CalibrationRoutine.Z_TILT -> R.string.calibration_routine_ztilt_desc
    CalibrationRoutine.QUAD_GANTRY_LEVEL -> R.string.calibration_routine_qgl_desc
}
