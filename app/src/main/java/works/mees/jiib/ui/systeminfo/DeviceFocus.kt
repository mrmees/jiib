package works.mees.jiib.ui.systeminfo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.jiib.systeminfo.HostDevice
import works.mees.jiib.systeminfo.McuDevice
import works.mees.jiib.systeminfo.hostDetailLines
import works.mees.jiib.systeminfo.mcuDetailLines
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * Focus detail body for the selected host SBC device.
 *
 * Renders a **scrollable plain-text block** (no icons, no per-row labels). The FocusFrame title
 * carries the device name — it is NOT repeated in the body.
 *
 * Lines are built by [hostDetailLines] (pure, unit-tested). Each null/missing value segment is
 * gracefully dropped; if a whole conceptual line has no data it is omitted or rendered as "—".
 *
 * @param host     The host device model (identity + live fields + version strings).
 * @param throttle Decoded throttle condition labels from [decodeThrottleConditions]; empty = off-Pi.
 * @param uDp      The unit grid Dp from [rememberUnitGrid] / [LocalUnitDp].
 */
@Composable
fun ColumnScope.HostDetail(
    host: HostDevice,
    throttle: List<String>,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val lines = hostDetailLines(host, throttle)

    Column(
        modifier = androidx.compose.ui.Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = JiibType.dataInline.toTextStyle(t),
                color = t.text,
            )
        }
    }
}

/**
 * Focus detail body for the selected MCU device.
 *
 * Renders a **scrollable plain-text block** (no icons, no per-row labels). The FocusFrame title
 * carries the device name — not repeated.
 *
 * Lines are built by [mcuDetailLines] (pure, unit-tested). Sparse MCU data (CAN boards report a
 * different subset than the mainboard — SYS-04 degrade) gracefully drops null segments.
 *
 * @param mcu  The MCU device model from the device browser state.
 * @param uDp  The unit grid Dp.
 */
@Composable
fun ColumnScope.McuDetail(
    mcu: McuDevice,
    uDp: Dp,
) {
    val t = LocalTokens.current
    val lines = mcuDetailLines(mcu)

    Column(
        modifier = androidx.compose.ui.Modifier
            .weight(1f)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        lines.forEach { line ->
            Text(
                text = line,
                style = JiibType.dataInline.toTextStyle(t),
                color = t.text,
            )
        }
    }
}
