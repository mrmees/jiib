package works.mees.dinghy.ui.heatpresets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.HeatPreset
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.Capabilities
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.state.enumerateSettableHeaters
import works.mees.dinghy.state.heaterDisplayName
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The **Heat Presets** screen — per-printer preheat preset list/detail + create/edit wizard (Task 10).
 *
 * Reached from Printer Settings → "Heat Presets". Two view states (held in [rememberSaveable]):
 *  - **Browsing**: a Field [ListBlock] of saved presets; selecting one renders its setpoints in the
 *    Focus with a docked Edit control. Foot bar = Back / Delete (on selection) / Add.
 *  - **Editing**: the [HeatPresetWizard] (create when `presetId == null`, edit otherwise).
 *
 * Modeled on [works.mees.dinghy.ui.calibration.BedMeshScreen] (Focus/Field + FootButtonBar +
 * ConfirmGuard). All persistence routes through [AppContainer] write helpers (write-scope law).
 *
 * @param container the service-locator (presets flow, dispatcher, write helpers).
 * @param onBack    leave the screen.
 */
@Composable
fun HeatPresetsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets by container.activeHeatPresets.collectAsStateWithLifecycle(emptyList())
    val heaterLimits by container.heaterLimits.collectAsStateWithLifecycle(emptyMap())
    val capabilities by container.capabilities.collectAsStateWithLifecycle(Capabilities())
    val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused

    // The live, ordered settable heaters for the wizard (falls back to extruder+bed when the printer
    // hasn't reported config — Task 6 — so this is never empty).
    val liveHeaters = remember(heaterLimits, capabilities) {
        enumerateSettableHeaters(heaterLimits, capabilities.temperatureFans)
    }

    // View state. `editing` carries the preset-id being edited (null = create); when non-null the
    // wizard takes over. `selectedId` is the browsing selection; `showDeleteGuard` the delete overlay.
    var editing by rememberSaveable(stateSaver = HeatPresetEditStateSaver) {
        mutableStateOf<HeatPresetEditState>(HeatPresetEditState.Browsing)
    }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showDeleteGuard by remember { mutableStateOf(false) }

    when (val edit = editing) {
        is HeatPresetEditState.Editing -> {
            val saved = edit.presetId?.let { id -> presets.firstOrNull { it.id == id } }
            BoxWithConstraints(modifier.fillMaxSize()) {
                val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
                HeatPresetWizard(
                    saved = saved,
                    heaters = liveHeaters,
                    isPrinting = isPrinting,
                    onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                    onCancel = { editing = HeatPresetEditState.Browsing },
                    onSave = { preset ->
                        container.saveHeatPreset(preset)
                        selectedId = preset.id
                        editing = HeatPresetEditState.Browsing
                    },
                    uDp = grid.uDp,
                )
            }
        }

        HeatPresetEditState.Browsing -> {
            val selected = selectedId?.let { id -> presets.firstOrNull { it.id == id } }
            BoxWithConstraints(modifier.fillMaxSize()) {
                val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

                Box(Modifier.fillMaxSize()) {
                    ScreenScaffold(
                        focus = {
                            FocusFrame(
                                title = stringResource(R.string.heat_presets_title),
                                icon = DinghyIcons.TempPresets,
                                uDp = grid.uDp,
                                modifier = Modifier.fillMaxWidth().weight(1f),
                                isPrinting = isPrinting,
                                onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                                onPanic = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
                            ) {
                                HeatPresetDetail(
                                    preset = selected,
                                    onEdit = { selected?.let { editing = HeatPresetEditState.Editing(it.id) } },
                                    uDp = grid.uDp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        },
                        field = {
                            if (presets.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = stringResource(R.string.heat_presets_empty_hint),
                                        color = LocalTokens.current.text2,
                                        style = DinghyType.body.toTextStyle(LocalTokens.current),
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                ListBlock(modifier = Modifier.weight(1f)) {
                                    items(presets, key = { it.id }) { p ->
                                        val t = LocalTokens.current
                                        ListRow(
                                            selected = p.id == selectedId,
                                            onClick = { selectedId = p.id },
                                            uDp = grid.uDp,
                                            leadingContent = {
                                                ListRowIcon(DinghyIcons.TempPresets, grid.uDp, t.text)
                                            },
                                            trailingContent = {
                                                Text(
                                                    text = presetSummary(p),
                                                    style = DinghyType.dataMeta.toTextStyle(t),
                                                    color = t.text2,
                                                )
                                            },
                                        ) {
                                            ListRowLabel(p.name)
                                        }
                                    }
                                }
                            }

                            FootButtonBar(
                                uDp = grid.uDp,
                                actions = listOf(
                                    FootAction(
                                        label = stringResource(R.string.common_back),
                                        icon = DinghyIcons.Back,
                                        onClick = onBack,
                                        intent = Intent.Accent,
                                        contentDescription = stringResource(R.string.cd_back),
                                    ),
                                    FootAction(
                                        label = stringResource(R.string.common_delete),
                                        icon = DinghyIcons.HeatPresetDelete,
                                        onClick = { showDeleteGuard = true },
                                        intent = Intent.Danger,
                                        enabled = selectedId != null,
                                    ),
                                    FootAction(
                                        label = stringResource(R.string.common_add),
                                        icon = DinghyIcons.HeatPresetAdd,
                                        onClick = { editing = HeatPresetEditState.Editing(null) },
                                        intent = Intent.Go,
                                    ),
                                ),
                            )
                        },
                    )

                    if (showDeleteGuard && selected != null) {
                        ConfirmGuard(
                            title = stringResource(R.string.heat_presets_delete_title),
                            message = stringResource(R.string.heat_presets_delete_message, selected.name),
                            confirmLabel = stringResource(R.string.common_delete),
                            destructive = true,
                            onConfirm = {
                                container.deleteHeatPreset(selected.id)
                                selectedId = null
                                showDeleteGuard = false
                            },
                            onCancel = { showDeleteGuard = false },
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// View state
// ---------------------------------------------------------------------------

/** The two Heat Presets view states. [Editing] carries the edited preset id (null = create). */
internal sealed class HeatPresetEditState {
    data object Browsing : HeatPresetEditState()
    data class Editing(val presetId: String?) : HeatPresetEditState()
}

/** Saver so the view state (incl. the edited-preset id) survives rotation / process death. */
internal val HeatPresetEditStateSaver =
    androidx.compose.runtime.saveable.Saver<HeatPresetEditState, String>(
        save = { state ->
            when (state) {
                HeatPresetEditState.Browsing -> "B"
                is HeatPresetEditState.Editing -> "E:${state.presetId ?: ""}"
            }
        },
        restore = { raw ->
            when {
                raw == "B" -> HeatPresetEditState.Browsing
                raw.startsWith("E:") -> {
                    val id = raw.removePrefix("E:")
                    HeatPresetEditState.Editing(id.ifEmpty { null })
                }
                else -> HeatPresetEditState.Browsing
            }
        },
    )

// ---------------------------------------------------------------------------
// Sub-composables + helpers
// ---------------------------------------------------------------------------

/**
 * The Focus body for the browsing state: when a preset is selected, a fit-to-display line-item list
 * of its setpoints (heater label left, value right; 0 = "Off") with a docked Edit control; otherwise
 * a short hint.
 */
@Composable
private fun HeatPresetDetail(
    preset: HeatPreset?,
    onEdit: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    if (preset == null) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.heat_presets_select_hint),
                color = t.text2,
                style = DinghyType.body.toTextStyle(t),
                textAlign = TextAlign.Center,
            )
        }
        return
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            preset.setpoints.entries.sortedBy { it.key }.forEach { (obj, v) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = heaterDisplayName(obj),
                        color = t.text,
                        style = DinghyType.listLabel.toTextStyle(t),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (v == 0) stringResource(R.string.heat_presets_off) else "$v°C",
                        color = t.text2,
                        style = DinghyType.dataInline.toTextStyle(t),
                    )
                }
            }
        }
        OutlinedControl(
            label = stringResource(R.string.common_edit),
            onClick = onEdit,
            icon = DinghyIcons.HeatPresetEdit,
            intent = Intent.Accent,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Trailing one-line summary of a preset's setpoints ("Nozzle 200° · Bed 60°"); empty when none. */
internal fun presetSummary(p: HeatPreset): String =
    p.setpoints.entries
        .sortedBy { it.key }
        .joinToString(" · ") { "${heaterDisplayName(it.key)} ${it.value}°" }
