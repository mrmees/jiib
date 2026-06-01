package works.mees.dinghy.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Backspace ligature shown on the delete key. */
private const val BACKSPACE = "⌫"

/** The numpad key grid (digits + decimal + backspace), row-major. */
private val KEY_ROWS: List<List<String>> = listOf(
    listOf("7", "8", "9"),
    listOf("4", "5", "6"),
    listOf("1", "2", "3"),
    listOf(".", "0", BACKSPACE),
)

/** Cap typed length so a fat-finger can't build an absurd number. */
private const val MAX_ENTRY_LEN = 6

/**
 * The full-screen **numeric keypad** page — exact manual entry of a single bounded number, the
 * touch-first companion to [ScrubberPage]'s drag-to-set. A digit-only pad (NOT an alphanumeric
 * keyboard, so it honors the "no keyboard in printer controls" LAW): tap digits to build a value,
 * `.` for a fraction (gated by [allowDecimal]), [BACKSPACE] to delete. On Set the entry is parsed and
 * CLAMPED to [range]; an empty entry commits the [initial] value unchanged.
 *
 * Like [ScrubberPage] it paints an opaque [androidx.compose.ui.graphics.Color] `t.bg` root because it
 * is shown OVER another screen, and routes every color through [LocalTokens] (THEME-01). The live
 * entry is [GeistMono] (tabular numerals) so digits don't jitter. Cancel is [Intent.Neutral] (a
 * non-destructive back-out), Set is [Intent.Go] (commit) — the same fixed intent contract as the
 * scrubber.
 *
 * @param label        the setting's name shown above the entry ("Distance", "Speed").
 * @param initial      the current value; shown dimmed as a placeholder until the user types.
 * @param range        the allowed closed range; the committed value is clamped into it.
 * @param unit         optional unit suffix shown after the value ("mm", "mm/s").
 * @param allowDecimal when false the `.` key is disabled (integer-only settings like feedrate).
 * @param onCancel     called when the user backs out without committing.
 * @param onSet        called with the parsed+clamped value when the user commits.
 */
@Composable
fun NumpadPage(
    label: String,
    initial: Double,
    range: ClosedFloatingPointRange<Double>,
    unit: String = "",
    allowDecimal: Boolean = true,
    onCancel: () -> Unit,
    onSet: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    // The typed string (empty = "show the initial placeholder"). Re-seeded if [initial] changes.
    var entry by remember(initial) { mutableStateOf("") }

    fun press(key: String) {
        entry = when (key) {
            BACKSPACE -> entry.dropLast(1)
            "." -> when {
                !allowDecimal || entry.contains(".") -> entry
                entry.isEmpty() -> "0."
                else -> "$entry."
            }
            else -> when { // a digit
                entry.length >= MAX_ENTRY_LEN -> entry
                entry == "0" -> key // replace a lone leading zero
                else -> entry + key
            }
        }
    }

    fun committed(): Double =
        (entry.toDoubleOrNull() ?: initial).coerceIn(range.start, range.endInclusive)

    val display = entry.ifEmpty { fmtNum(initial) }
    val rangeHint = "${fmtNum(range.start)}–${fmtNum(range.endInclusive)}${unitSuffix(unit)}"

    Column(
        modifier
            .fillMaxSize()
            .background(t.bg)
            .padding(16.dp),
    ) {
        // Header: label · live entry · allowed-range hint.
        Column(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                color = t.text2,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(20f, t.fs).sp,
            )
            Text(
                text = "$display${unitSuffix(unit)}",
                color = if (entry.isEmpty()) t.text3 else t.text,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Bold,
                fontSize = fsSp(52f, t.fs).sp,
            )
            Text(
                text = rangeHint,
                color = t.text3,
                fontFamily = GeistMono,
                fontWeight = FontWeight.Medium,
                fontSize = fsSp(13f, t.fs).sp,
            )
        }

        // The key grid — each row weighted so the keys fill the available height as big targets.
        Column(
            Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (row in KEY_ROWS) {
                Row(
                    Modifier.fillMaxWidth().weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (key in row) {
                        val disabled = key == "." && !allowDecimal
                        KeyCell(
                            key = key,
                            disabled = disabled,
                            onClick = { press(key) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
        }

        // Cancel (neutral back-out) · Set (green commit) — same intent contract as ScrubberPage.
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedControl("Cancel", onCancel, Modifier.weight(1f), Intent.Neutral)
            OutlinedControl("Set", { onSet(committed()) }, Modifier.weight(1f), Intent.Go)
        }
    }
}

/** One numpad key: a big outlined cell with a GeistMono glyph; disabled keys dim and ignore taps. */
@Composable
private fun KeyCell(key: String, disabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var box = modifier
        .alpha(if (disabled) 0.4f else 1f)
        .clip(shape)
        .border(BorderStroke(2.dp, t.outline), shape)
    if (!disabled) box = box.clickable(onClick = onClick)
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = key,
            color = t.text,
            fontFamily = GeistMono,
            fontWeight = FontWeight.Bold,
            fontSize = fsSp(28f, t.fs).sp,
        )
    }
}

/** Drop a trailing ".0" on whole numbers; keep one decimal otherwise. */
private fun fmtNum(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

/** A space-prefixed unit suffix, or empty when no unit. */
private fun unitSuffix(unit: String): String = if (unit.isEmpty()) "" else " $unit"
