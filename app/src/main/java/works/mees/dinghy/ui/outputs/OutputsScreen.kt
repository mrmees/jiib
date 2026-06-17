package works.mees.dinghy.ui.outputs

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.components.FootAction
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.components.ListRow
import works.mees.dinghy.designsystem.components.ListRowIcon
import works.mees.dinghy.designsystem.components.ListRowLabel
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.icons.DinghyIcon
import works.mees.dinghy.designsystem.icons.DinghyIconView
import works.mees.dinghy.designsystem.icons.DinghyIcons
import androidx.compose.foundation.layout.BoxWithConstraints
import works.mees.dinghy.designsystem.layout.FocusInset
import works.mees.dinghy.designsystem.layout.ListBlock
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.di.AppContainer
import works.mees.dinghy.outputs.OutputRowVm
import works.mees.dinghy.outputs.OutputsHolder
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle

/**
 * The Outputs screen (D-18/D-19) — a list+detail-in-Focus layout collapsing the three legacy detail
 * pages (OutputScrubberDetail, OutputPinDetail, OutputLedDetail) into a single screen that uses the
 * [ScreenScaffold] Focus/Field grammar: the Field holds the flat output list; the Focus holds the
 * per-output inline control surface ([OutputFocusControl]) when a row is selected, or an empty prompt
 * when nothing is selected. No extra back-stack entries — selection state lives here.
 *
 * Mirrors `ui/spool/SpoolScreen.kt`'s Detail-in-Focus / two-overload stateless seam:
 * - LIVE overload: consumes [holder] + [container], collects [OutputsHolder.rows], holds [selectedKey].
 * - STATELESS overload: pure params for @Preview matrix — no Moonraker, no holder.
 *
 * FootButtonBar lives in the field lambda (MANDATORY per 26-PATTERNS redesign rule).
 * Static styling only — no looping animation (Adreno-320 budget).
 *
 * @param holder    the headless [OutputsHolder] (rows + dispatch wiring).
 * @param container service-locator for dispatcher + write scope.
 * @param onBack    exits the screen (neutral Back — never a safety color, D-10).
 */
@Composable
fun OutputsScreen(
    holder: OutputsHolder,
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows by holder.rows.collectAsStateWithLifecycle()
    val dispatcher by container.dispatcher.collectAsStateWithLifecycle(initialValue = null)
    val printerState by container.printerState.collectAsStateWithLifecycle(initialValue = PrinterState())
    val isPrinting = printerState.printState == PrintState.Printing ||
        printerState.printState == PrintState.Paused
    var selectedKey by remember { mutableStateOf<String?>(null) }
    Box(modifier.fillMaxSize()) {
        OutputsContent(
            rows = rows,
            selectedKey = selectedKey,
            onSelect = { selectedKey = it },
            holder = holder,
            container = container,
            isPrinting = isPrinting,
            onEmergencyStop = { dispatcher?.dispatch(CommandRegistry.emergencyStop, Unit) },
            onBack = onBack,
        )
    }
}

/**
 * STATELESS preview seam — drives the [ScreenScaffold] from pure fixture data.
 * The live [holder] + [container] are omitted; [OutputFocusControl] is skipped when a row is
 * selected (focus shows a plain label placeholder in this overload, as there is no dispatcher).
 *
 * This overload exists exclusively for the @Preview matrix — do NOT call from production code.
 *
 * @param rows        fixture row list.
 * @param selectedKey the currently selected [works.mees.dinghy.outputs.OutputDescriptor.objectKey], or null.
 * @param onSelect    callback when a row or Back-in-Focus is tapped.
 * @param onBack      exits the screen.
 */
@Composable
fun OutputsScreen(
    rows: List<OutputRowVm>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutputsContent(
        rows = rows,
        selectedKey = selectedKey,
        onSelect = onSelect,
        holder = null,
        container = null,
        isPrinting = false,
        onEmergencyStop = {},
        onBack = onBack,
        modifier = modifier,
    )
}

/**
 * Shared layout core (Focus/Field grammar). Called by both overloads.
 *
 * @param holder    null in the stateless/preview overload → Focus shows a label placeholder.
 * @param container null in the stateless/preview overload (paired with holder).
 */
@Composable
private fun OutputsContent(
    rows: List<OutputRowVm>,
    selectedKey: String?,
    onSelect: (String?) -> Unit,
    holder: OutputsHolder?,
    container: AppContainer?,
    isPrinting: Boolean,
    onEmergencyStop: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val selectedRow = rows.firstOrNull { it.descriptor.objectKey == selectedKey }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))

        ScreenScaffold(
            focus = {
                // Detail-in-Focus: the selected output's inline control surface (D-18)
                if (selectedRow != null && holder != null && container != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        FocusFrame(
                            title = selectedRow.descriptor.prettyName,
                            icon = glyphFor(selectedRow.descriptor.family),
                            uDp = grid.uDp,
                            modifier = Modifier.fillMaxSize(),
                            isPrinting = isPrinting,
                            onEmergencyStop = onEmergencyStop,
                            onPanic = onEmergencyStop,
                            contentInset = FocusInset / 2, // shared calibration-focus rhythm
                        ) {
                            // CR-04 (26-rev): key on the selected output's IDENTITY so switching between two
                            // same-family outputs (identical range/step) tears down the previous control's
                            // pointer-input node + dispatch closures — without it, a live gesture handler kept
                            // the previous output's captured dispatch and could send the wire command to the
                            // WRONG device. This is NOT the forbidden key(value) per-frame rebuild (P19 SC-3 /
                            // fa97efb): objectKey cannot change mid-drag, and the 004 Scrubber's internal
                            // working state is still seeded via remember(value, range), never per recompose.
                            key(selectedRow.descriptor.objectKey) {
                                OutputFocusControl(
                                    output = selectedRow,
                                    holder = holder,
                                    container = container,
                                    onBack = { onSelect(null) },
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                } else {
                    // No selection: framed empty state — the Outputs section identity (icon law:
                    // existing owner-locked OutputSection glyph) with the prompt centered inside.
                    FocusFrame(
                        title = stringResource(R.string.outputs_title),
                        icon = DinghyIcons.OutputSection,
                        uDp = grid.uDp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        isPrinting = isPrinting,
                        onEmergencyStop = onEmergencyStop,
                        onPanic = onEmergencyStop,
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.outputs_select_prompt),
                                color = t.text3,
                                style = DinghyType.body.toTextStyle(t),
                            )
                        }
                    }
                }
            },
            field = {
                ListBlock(
                    modifier = Modifier
                        .weight(1f),
                ) {
                    items(rows, key = { it.descriptor.objectKey }) { row ->
                        ListRow(
                            selected = row.descriptor.objectKey == selectedKey,
                            onClick = { onSelect(row.descriptor.objectKey) },
                            uDp = grid.uDp,
                            leadingContent = {
                                // R23: canonical 0.6U list-row icon.
                                ListRowIcon(
                                    icon = glyphFor(row.descriptor.family),
                                    uDp = grid.uDp,
                                    tint = t.accent2,
                                    contentDescription = stringResource(R.string.cd_output_glyph),
                                )
                            },
                            trailingContent = if (row.displayValue != null) {
                                {
                                    Text(
                                        text = row.displayValue,
                                        color = t.text2,
                                        style = DinghyType.dataInline.toTextStyle(t),
                                    )
                                }
                            } else null,
                        ) {
                            // Canonical list label (R22/R11); ListRow owns the weighting now.
                            ListRowLabel(row.descriptor.prettyName)
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
                            intent = Intent.Accent, // R5: Back = accent
                            contentDescription = stringResource(R.string.cd_back),
                        ),
                    ),
                )
            },
        )
    }
}

/**
 * The owner-locked per-family row glyph (icon law D-01..D-06 — NEVER invented, NEVER a raw ligature):
 * heater_generic → [DinghyIcons.OutputHeater] (D-01), fan_generic → [DinghyIcons.OutputFan] (D-02),
 * every LED family → [DinghyIcons.OutputLed] (D-03), servo → [DinghyIcons.OutputServo] (D-04),
 * output_pin → [DinghyIcons.OutputPin] (D-05), pwm_tool → [DinghyIcons.OutputPwmTool] (D-06).
 * Unknown family falls back to the Outputs section glyph (defensive; discovery whitelist precludes it).
 */
private fun glyphFor(family: String): DinghyIcon = when (family) {
    OutputsHolder.FAMILY_HEATER -> DinghyIcons.OutputHeater
    OutputsHolder.FAMILY_FAN -> DinghyIcons.OutputFan
    OutputsHolder.FAMILY_SERVO -> DinghyIcons.OutputServo
    OutputsHolder.FAMILY_OUTPUT_PIN -> DinghyIcons.OutputPin
    OutputsHolder.FAMILY_PWM_TOOL -> DinghyIcons.OutputPwmTool
    in OutputsHolder.LED_FAMILIES -> DinghyIcons.OutputLed
    else -> DinghyIcons.OutputSection
}
