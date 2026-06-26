package works.mees.jiib.ui.increments

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.jiib.R
import works.mees.jiib.command.CommandRegistry
import works.mees.jiib.command.dispatch
import works.mees.jiib.config.IncrementParse
import works.mees.jiib.config.filterIncrementInput
import works.mees.jiib.config.formatIncrementList
import works.mees.jiib.config.parseIncrementInput
import works.mees.jiib.designsystem.components.FocusFrame
import works.mees.jiib.designsystem.components.FootAction
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.ListRow
import works.mees.jiib.designsystem.components.ListRowIcon
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.icons.DinghyIcons
import works.mees.jiib.designsystem.layout.ListBlock
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.designsystem.layout.rememberUnitGrid
import works.mees.jiib.di.AppContainer
import works.mees.jiib.state.PrintState
import works.mees.jiib.state.PrinterState
import works.mees.jiib.theme.DinghyType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle
import works.mees.jiib.ui.screen.TokenTextField

/**
 * The **Increment Values** screen — per-printer step-selector value lists.
 *
 * Reached from Printer Settings → "Increment Values". A SINGLE flat Field list of every
 * configurable control (the 13 Fine-Tune params + Microstep + Babystep + Probe Z-Test); no
 * sub-pages. Tapping a row HIGHLIGHTS it and swaps the Focus to that control's editor — the list
 * stays fully visible. The Field foot bar carries only Back; the per-item **Save** is docked at the
 * bottom of the Focus.
 */
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

    var selectedKey by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedSpec = selectedKey?.let { IncrementControls.specFor(it) }

    /** The current canonical string for [spec]: stored, else its jiib default. */
    fun currentString(spec: IncrementControlSpec): String =
        stored[spec.key] ?: formatIncrementList(spec.defaultValues)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            focus = {
                FocusFrame(
                    title = selectedSpec?.let { rowLabel(it) }
                        ?: stringResource(R.string.increment_values_title),
                    icon = selectedSpec?.icon ?: DinghyIcons.FineTune,
                    uDp = grid.uDp,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    isPrinting = isPrinting,
                    onEmergencyStop = estop,
                    onPanic = estop,
                ) {
                    if (selectedSpec == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.increment_values_select_hint),
                                color = LocalTokens.current.text2,
                                style = DinghyType.body.toTextStyle(LocalTokens.current),
                            )
                        }
                    } else {
                        IncrementEditor(
                            spec = selectedSpec,
                            initial = currentString(selectedSpec),
                            onSave = { canonical -> container.saveIncrementList(selectedSpec.key, canonical) },
                        )
                    }
                }
            },
            field = {
                ListBlock(modifier = Modifier.weight(1f)) {
                    items(IncrementControls.ALL, key = { it.key }) { spec ->
                        val t = LocalTokens.current
                        // Rows show only icon + name; the values + Save live in the Focus editor.
                        ListRow(
                            selected = spec.key == selectedKey,
                            onClick = { selectedKey = spec.key },
                            uDp = grid.uDp,
                            leadingContent = { ListRowIcon(spec.icon, grid.uDp, t.text) },
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

/**
 * The Focus body for a selected control: the filtered text field + live validation, with a docked
 * **Save** at the bottom. [initial] seeds the field; the entry resets when the SELECTED control
 * changes (keyed on [spec].key) so switching rows discards any unsaved edits.
 */
@Composable
private fun IncrementEditor(
    spec: IncrementControlSpec,
    initial: String,
    onSave: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable(spec.key) { mutableStateOf(initial) }
    val parse = parseIncrementInput(text, spec.maxCount)
    val valid = parse is IncrementParse.Ok
    val errorMsg = (parse as? IncrementParse.Error)?.reason
    val t = LocalTokens.current

    Column(modifier.fillMaxSize()) {
        TokenTextField(
            value = text,
            onValueChange = { text = filterIncrementInput(it) },
            label = stringResource(R.string.increment_values_input_label),
            // Text IME: the list needs ',' '.' and digits — numeric keyboards can't reliably enter
            // commas/decimals. filterIncrementInput is the enforcement layer (only digits/./,/space
            // survive); this is the sanctioned Settings keyboard exception.
            keyboardType = KeyboardType.Text,
            isError = !valid,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = errorMsg ?: stringResource(R.string.increment_values_input_hint),
            color = if (errorMsg != null) t.stop else t.text2,
            style = DinghyType.caption.toTextStyle(t),
        )
        Spacer(Modifier.weight(1f))
        OutlinedControl(
            label = stringResource(R.string.common_save),
            onClick = { (parse as? IncrementParse.Ok)?.let { onSave(it.canonical) } },
            icon = DinghyIcons.CheckCircle,
            intent = Intent.Go,
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Row/Focus label: the control title when present, else a per-key name. */
private fun rowLabel(spec: IncrementControlSpec): String = spec.controlTitle ?: when (spec.key) {
    "move_microstep" -> "Microstep"
    "babystep" -> "Babystep"
    "probe_testz" -> "Probe Z Test"
    else -> spec.group
}
