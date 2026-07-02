package works.mees.jiib.ui.systeminfo

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import works.mees.jiib.designsystem.components.DigestRow
import works.mees.jiib.designsystem.focus.FocusDigest
import works.mees.jiib.systeminfo.HostDevice
import works.mees.jiib.systeminfo.McuDevice
import works.mees.jiib.systeminfo.hostDetailLines
import works.mees.jiib.systeminfo.mcuDetailLines

/**
 * Focus detail body for the selected host SBC device.
 *
 * Renders a plain-text digest via [FocusDigest]/[DigestRow.Note]. The FocusFrame title
 * carries the device name — it is NOT repeated in the body.
 *
 * Lines are built by [hostDetailLines] (pure, unit-tested). Each null/missing value segment is
 * gracefully dropped; if a whole conceptual line has no data it is omitted or rendered as "—".
 * LAW 5 shrink→scroll is owned by DigestColumn.
 *
 * @param host     The host device model (identity + live fields + version strings).
 * @param throttle Decoded throttle condition labels from [decodeThrottleConditions]; empty = off-Pi.
 */
@Composable
fun ColumnScope.HostDetail(
    host: HostDevice,
    throttle: List<String>,
) {
    FocusDigest(
        rows = hostDetailLines(host, throttle).map { DigestRow.Note(it, marquee = true) },
        horizontalAlignment = Alignment.Start,
    )
}

/**
 * Focus detail body for the selected MCU device.
 *
 * Renders a plain-text digest via [FocusDigest]/[DigestRow.Note]. The FocusFrame title
 * carries the device name — not repeated.
 *
 * Lines are built by [mcuDetailLines] (pure, unit-tested). Sparse MCU data (CAN boards report a
 * different subset than the mainboard — SYS-04 degrade) gracefully drops null segments.
 * LAW 5 shrink→scroll is owned by DigestColumn.
 *
 * ⚠ Long MCU lists shrink toward 15sp before scrolling (was scroll-at-full-size).
 *
 * @param mcu  The MCU device model from the device browser state.
 */
@Composable
fun ColumnScope.McuDetail(
    mcu: McuDevice,
) {
    FocusDigest(
        rows = mcuDetailLines(mcu).map { DigestRow.Note(it, marquee = true) },
        horizontalAlignment = Alignment.Start,
    )
}
