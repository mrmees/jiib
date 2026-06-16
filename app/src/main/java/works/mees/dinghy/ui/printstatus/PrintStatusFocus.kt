package works.mees.dinghy.ui.printstatus

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.components.FocusFrame
import works.mees.dinghy.designsystem.icons.DinghyIcons
import works.mees.dinghy.state.PrintState
import works.mees.dinghy.state.PrinterState
import works.mees.dinghy.theme.DinghyType
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.compose.toTextStyle
import works.mees.dinghy.theme.seriesColor
import works.mees.dinghy.ui.spool.ActiveSpoolCardState

/**
 * The universal home Focus (was StandbyFocus). Renders for EVERY printer state in the collapsed
 * skeleton: a 1U-header FocusFrame (two-axis state title + optional printer name), a faint brand
 * watermark at 30% of the focus's smaller edge pinned bottom-end, and a top-start glance block
 * (Nozzle · Bed · glance sensor · spool remaining) at uniform focusHero sizing.
 *
 * E-stop: PrintStatus owns its e-stop via the FocusFrame header (AppShell screenOwnsEstop); the
 * header shows it only when `isPrinting && onEmergencyStop != null`. We pass both so e-stop stays
 * reachable while Printing/Paused. During Klipper Error/Shutdown the firmware is already halted, so
 * the header e-stop is intentionally absent. Named top-level composable for an independently-
 * restartable scope (D-01/D-02).
 */
@Composable
internal fun HomeFocus(
    state: PrinterState,
    printerName: String?,
    isMultiPrinter: Boolean,
    spoolmanPresent: Boolean,
    activeSpoolCardState: ActiveSpoolCardState,
    onEmergencyStop: (() -> Unit)?,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val nozzle = primaryHeater(state)
    val bed = state.heaters["heater_bed"]
    val glance = selectGlanceSensor(state.temperatureSensors)
    val spoolRemaining = (activeSpoolCardState as? ActiveSpoolCardState.Loaded)?.spool?.remainingWeight
    val isPrinting = state.printState == PrintState.Printing || state.printState == PrintState.Paused
    val stateLabel = stringResource(homeStateLabelRes(state.printState, state.klippyState))
    val title = if (isMultiPrinter && !printerName.isNullOrBlank()) "$printerName · $stateLabel" else stateLabel
    FocusFrame(
        title = title,
        icon = DinghyIcons.PrintStatusStandby,
        uDp = uDp,
        modifier = Modifier.fillMaxSize(),
        isPrinting = isPrinting,
        onEmergencyStop = onEmergencyStop,
        onPanic = onEmergencyStop,
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            // Brand watermark: 30% of the smaller edge, bottom-end, faint accent2 tint.
            val markSize = minOf(maxWidth, maxHeight) * 0.30f
            Image(
                painter = painterResource(R.drawable.jiib_icon),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                colorFilter = ColorFilter.tint(t.accent2),
                alpha = 0.45f,
                modifier = Modifier.size(markSize).align(Alignment.BottomEnd),
            )
            // Glance block: top-start, tight, label + value both focusHero.
            Column(
                Modifier.align(Alignment.TopStart),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                GlanceRow(stringResource(R.string.printstatus_nozzle_label), tempActive(nozzle), t.seriesColor(0))
                GlanceRow(stringResource(R.string.printstatus_bed_label), tempActive(bed), t.seriesColor(1))
                glance?.let { GlanceRow(glanceLabel(it.name), fmt(it.temperature), t.text) }
                if (spoolmanPresent && spoolRemaining != null) {
                    GlanceRow(stringResource(R.string.printstatus_spool_label), "${spoolRemaining.roundToInt()} g", t.text)
                }
            }
        }
    }
}

/** One glance line: dim label + colored value, BOTH focusHero (normalized size), tight, one row. */
@Composable
internal fun GlanceRow(label: String, value: String, valueColor: Color) {
    val t = LocalTokens.current
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = t.text2, style = DinghyType.focusHero.toTextStyle(t))
        Text(value, color = valueColor, style = DinghyType.focusHero.toTextStyle(t))
    }
}

/** Friendly glance-sensor label: strip the `temperature_sensor ` prefix, fall back to the raw key. */
internal fun glanceLabel(name: String): String =
    name.removePrefix("temperature_sensor ").ifBlank { name }
