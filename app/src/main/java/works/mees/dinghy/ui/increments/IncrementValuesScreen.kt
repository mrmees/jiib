package works.mees.dinghy.ui.increments

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.R
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.config.IncrementParse
import works.mees.dinghy.config.filterIncrementInput
import works.mees.dinghy.config.formatIncrementList
import works.mees.dinghy.config.parseIncrementInput
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.ui.screen.TokenTextField

/** Top-level rows (Fine-Tune opens a submenu; the rest open the editor directly). */
private val TOP_ROWS = listOf(
    IncrementControls.specFor("move_microstep")!!,
    IncrementControls.specFor("babystep")!!,
    IncrementControls.specFor("probe_testz")!!,
)
private val FINE_TUNE_ROWS = IncrementControls.ALL.filter { it.group == "Fine-Tune" }

/** The Increment Values settings screen. Reached from Printer Settings → "Increment Values". */
@Composable
fun IncrementValuesScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stored by container.activeIncrementStrings.collectAsStateWithLifecycle(emptyMap())
    val printerState by container.printerState.collectAsStateWithLifecycle(PrinterState())
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(null)
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    val estop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit); Unit }

    var view by rememberSaveable(stateSaver = IncViewSaver) { mutableStateOf<IncView>(IncView.TopList) }

    /** The current canonical string for [spec]: stored, else its default. */
    fun currentString(spec: IncrementControlSpec): String =
        stored[spec.key] ?: formatIncrementList(spec.defaultValues)

    when (val v = view) {
        is IncView.Editing -> {
            val spec = IncrementControls.specFor(v.key)
            if (spec == null) { view = IncView.TopList; return }
            IncrementEditFocus(
                spec = spec,
                initial = currentString(spec),
                isPrinting = isPrinting,
                onEmergencyStop = estop,
                onCancel = { view = if (spec.group == "Fine-Tune") IncView.FineTuneSubmenu else IncView.TopList },
                onSave = { canonical ->
                    container.saveIncrementList(spec.key, canonical)
                    view = if (spec.group == "Fine-Tune") IncView.FineTuneSubmenu else IncView.TopList
                },
                modifier = modifier,
            )
        }
        IncView.FineTuneSubmenu -> IncrementListView(
            title = stringResource(R.string.increment_values_finetune),
            icon = DinghyIcons.FineTune,
            rows = FINE_TUNE_ROWS,
            summaryFor = { currentString(it) },
            isPrinting = isPrinting,
            onEmergencyStop = estop,
            onRowClick = { view = IncView.Editing(it.key) },
            onBack = { view = IncView.TopList },
            modifier = modifier,
        )
        IncView.TopList -> IncrementListView(
            title = stringResource(R.string.increment_values_title),
            icon = DinghyIcons.FineTune,
            rows = TOP_ROWS,
            // The Fine-Tune entry is a NAV row (opens the submenu), not an editable control.
            extraNavRow = FineTuneNavRow,
            summaryFor = { currentString(it) },
            isPrinting = isPrinting,
            onEmergencyStop = estop,
            onRowClick = { spec ->
                view = if (spec === FineTuneNavRow) IncView.FineTuneSubmenu else IncView.Editing(spec.key)
            },
            onBack = onBack,
            modifier = modifier,
        )
    }
}

/** A synthetic "row" for the Fine-Tune submenu entry (not a real control; has no editable list). */
private val FineTuneNavRow = IncrementControlSpec(
    key = "__finetune_nav", group = "Fine-Tune", controlTitle = null,
    icon = DinghyIcons.FineTune, maxCount = null, defaultValues = emptyList(),
)

private sealed interface IncView {
    data object TopList : IncView
    data object FineTuneSubmenu : IncView
    data class Editing(val key: String) : IncView
}

/** Saver so the view state (incl. the edited key) survives rotation / process death. */
private val IncViewSaver = androidx.compose.runtime.saveable.Saver<IncView, String>(
    save = { v ->
        when (v) {
            IncView.TopList -> "T"
            IncView.FineTuneSubmenu -> "F"
            is IncView.Editing -> "E:${v.key}"
        }
    },
    restore = { raw ->
        when {
            raw == "F" -> IncView.FineTuneSubmenu
            raw.startsWith("E:") -> IncView.Editing(raw.removePrefix("E:"))
            else -> IncView.TopList
        }
    },
)

/** Shared Focus/Field list shell for the top list + the Fine-Tune submenu. */
@Composable
private fun IncrementListView(
    title: String,
    icon: works.mees.dinghy.designsystem.icons.DinghyIcon,
    rows: List<IncrementControlSpec>,
    summaryFor: (IncrementControlSpec) -> String,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onRowClick: (IncrementControlSpec) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    extraNavRow: IncrementControlSpec? = null,
) {
    val allRows = remember(rows, extraNavRow) { (listOfNotNull(extraNavRow) + rows) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = title,
                    icon = icon,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.increment_values_select_hint),
                            color = LocalTokens.current.text2,
                            style = DinghyType.body.toTextStyle(LocalTokens.current),
                        )
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(allRows, key = { it.key }) { spec ->
                        val t = LocalTokens.current
                        val isNav = spec === extraNavRow
                        ListRow(
                            selected = false,
                            onClick = { onRowClick(spec) },
                            uDp = grid.uDp,
                            leadingContent = { ListRowIcon(spec.icon, grid.uDp, t.text) },
                            trailingContent = {
                                if (!isNav) {
                                    Text(
                                        text = summaryFor(spec).replace(",", " / "),
                                        style = DinghyType.dataMeta.toTextStyle(t),
                                        color = t.text2,
                                    )
                                }
                            },
                        ) {
                            Text(
                                text = rowLabel(spec),
                                style = DinghyType.listLabel.toTextStyle(t),
                                color = t.text,
                                maxLines = 1,
                                modifier = Modifier.fillMaxWidth().basicMarquee(),
                            )
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
                    ),
                )
            },
        )
    }
}

/** The edit Focus for one control: a filtered text field + live validation + Save. */
@Composable
private fun IncrementEditFocus(
    spec: IncrementControlSpec,
    initial: String,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onCancel: () -> Unit,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(spec.key) { mutableStateOf(initial) }
    val parse = parseIncrementInput(text, spec.maxCount)
    val valid = parse is IncrementParse.Ok
    val errorMsg = (parse as? IncrementParse.Error)?.reason

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = rowLabel(spec),
                    icon = spec.icon,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = onEmergencyStop,
                    onPanic = onEmergencyStop,
                ) {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TokenTextField(
                            value = text,
                            onValueChange = { text = filterIncrementInput(it) },
                            label = stringResource(R.string.increment_values_input_label),
                            // Text IME: the list needs ',' '.' and digits — numeric keyboards can't
                            // reliably enter commas/decimals. filterIncrementInput is the enforcement layer
                            // (only digits/./,/space survive); this is the sanctioned Settings keyboard exception.
                            keyboardType = KeyboardType.Text,
                            isError = !valid,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            text = errorMsg ?: stringResource(R.string.increment_values_input_hint),
                            color = if (errorMsg != null) LocalTokens.current.stop else LocalTokens.current.text2,
                            style = DinghyType.caption.toTextStyle(LocalTokens.current),
                        )
                    }
                }
            },
            field = {
                FootButtonBar(
                    uDp = grid.uDp,
                    actions = listOf(
                        FootAction(
                            label = stringResource(R.string.common_back),
                            icon = DinghyIcons.Back,
                            onClick = onCancel,
                            intent = Intent.Accent,
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                        FootAction(
                            label = stringResource(R.string.common_save),
                            icon = DinghyIcons.CheckCircle,
                            onClick = { (parse as? IncrementParse.Ok)?.let { onSave(it.canonical) } },
                            intent = Intent.Go,
                            enabled = valid,
                        ),
                    ),
                )
            },
        )
    }
}

/** Row/Focus label: the control title when present, else its group name. */
private fun rowLabel(spec: IncrementControlSpec): String = spec.controlTitle ?: when (spec.key) {
    "move_microstep" -> "Microstep"
    "babystep" -> "Babystep"
    "probe_testz" -> "Probe Z Test"
    "__finetune_nav" -> "Fine-Tune"
    else -> spec.group
}
