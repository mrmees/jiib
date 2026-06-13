package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.calibration.CalibrationHubHolder
import works.mees.dinghy.calibration.CalibrationRoutine
import works.mees.dinghy.calibration.RoutineEntry
import works.mees.dinghy.designsystem.components.DetailCard
import works.mees.dinghy.designsystem.components.FloatingEStop
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.UnitGrid
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
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
    onOpen: (CalibrationRoutine) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val routines by holder.routines.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<CalibrationRoutine?>(null) }
    // D-05: pre-select the first entry so Focus is never empty on initial render.
    LaunchedEffect(routines) {
        if (selected == null) selected = routines.firstOrNull()?.routine
    }

    CalibrationHubContent(
        routines = routines,
        selected = selected,
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
 * Focus = [DetailCard] with the selected routine's icon (UAT-1: ~75% of U), title, author-written
 * description, and an accent Open button.
 *
 * D-05: the thin [CalibrationHubScreen] wrapper ensures [selected] is never null on first render
 * (pre-select logic via LaunchedEffect). This composable handles null gracefully with an empty Focus.
 */
@Composable
fun CalibrationHubContent(
    routines: List<RoutineEntry>,
    selected: CalibrationRoutine?,
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
                    // Focus: DetailCard with routine icon + title + description + Open button.
                    // UAT-4: FloatingEStop reserves the top-left corner; keep content clear of it.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        DetailCard(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                            if (selected != null) {
                                HubRoutineFocus(
                                    routine = selected,
                                    onOpen = { onOpen(selected) },
                                    grid = grid,
                                    t = t,
                                )
                            }
                        }
                        // UAT-4: FloatingEStop top-left corner reservation (printing-only overlay).
                        // The hub is a pre-print/calibration screen so isPrinting is always false here;
                        // the FloatingEStop correctly shows nothing. We still include it so the structural
                        // contract is honoured if the app state ever allows this screen during printing.
                        FloatingEStop(
                            visible = false, // hub is a pop-to-root foot-gun — not shown during print
                            onClick = {},
                            uDp = grid.uDp,
                            modifier = Modifier.align(Alignment.TopStart).padding(14.dp),
                        )
                    }
                },
                field = {
                    // Field: ListBlock suppresses swipe-up App Drawer automatically (scrollable Field).
                    ListBlock(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
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
                    ) {
                        OutlinedControl(
                            label = "",
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent, // R5: Back = accent
                            icon = DinghyIcons.Back,
                            contentDescription = stringResource(R.string.common_back),
                        )
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Hub Focus content (the DetailCard interior)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HubRoutineFocus(
    routine: CalibrationRoutine,
    onOpen: () -> Unit,
    grid: UnitGrid,
    t: ThemeTokens,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // UAT-1: prominent icon ~70-80% of U (75% = midpoint of range).
        DinghyIconView(
            icon = routineIconToken(routine),
            contentDescription = null, // title below provides the label
            tint = t.accent2,
            sizeDp = grid.uDp * 0.75f,
        )
        Text(
            text = stringResource(routineTitleRes(routine)),
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(20f, t.fs).sp,
        )
        Text(
            text = stringResource(routineDescRes(routine)),
            color = t.text2,
            fontFamily = Geist,
            fontSize = fsSp(15f, t.fs).sp,
        )
        OutlinedControl(
            label = stringResource(R.string.calibration_open_routine),
            onClick = onOpen,
            modifier = Modifier.fillMaxWidth(),
            intent = Intent.Go, // R5: Open = the expected action
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

/** Maps a [CalibrationRoutine] to its title string resource ID. */
private fun routineTitleRes(routine: CalibrationRoutine): Int = when (routine) {
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
