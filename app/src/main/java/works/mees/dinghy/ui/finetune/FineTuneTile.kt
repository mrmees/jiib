package works.mees.dinghy.ui.finetune

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The shared Fine-Tune value tile (D-13/D-14/D-16/D-19) — a single live-adjust control:
 * `icon · name · live-value · − · +`.
 *
 * ## Tap-inert value, long-press = reset (D-14 / D-16, REVIEW #3)
 * The value cell is NEVER `.clickable` for a tap-to-set — tapping it is INERT (D-14). [onReset] is
 * NULLABLE: a long-press handler is installed ONLY when `onReset != null` (a tuner with a real config
 * baseline); when null the value cell carries no reset affordance at all (firmware-retraction / part-fan
 * with no baseline — REVIEW #3, so a missing baseline can never emit a bare/invalid command).
 *
 * ## ± are the screen's expected physical action → accent (THEMING C5)
 * [onDecrement] / [onIncrement] are plain `clickable`; the ± arrows render in the accent intent.
 *
 * ## State-flip busy dim (D-15)
 * When `!enabled` the WHOLE tile dims (alpha 0.4) and ALL handlers (± and long-press) are stripped, so a
 * busy whole-group lock serializes taps — there is no optimistic local state, the value is the live vm
 * reduced value the caller passes in.
 *
 * Glyphs come from the 17-04 project-local vector drawables via [painterResource] + [Icon] tinting — NOT
 * the Material Symbols font (D-17, minSdk-23 / Adreno-320, no font dependency).
 *
 * @param iconRes    the 17-04 R.drawable glyph for this tuner.
 * @param name       the tuner label.
 * @param valueText  the live, display-scaled value ("—" when unreported, never a fabricated 0).
 * @param onDecrement − nudge (one command per tap; confirmed by the reduced state flip).
 * @param onIncrement + nudge.
 * @param onReset    NULLABLE long-press reset; null → no reset affordance (REVIEW #3).
 * @param enabled    false while the whole-group busy lock is held (D-15) — dims + strips handlers.
 */
@Composable
fun FineTuneTile(
    @DrawableRes iconRes: Int,
    name: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
    onReset: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = if (enabled) t.outline else t.hair

    Box(
        modifier
            .alpha(if (enabled) 1f else 0.4f)
            .clip(shape)
            .border(BorderStroke(2.dp, outline), shape)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Identity: glyph + name (left).
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (enabled) t.text2 else t.text3,
                modifier = Modifier.size(fsSp(34f, t.fs).dp),
            )
            Column(
                Modifier.weight(1f).padding(horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = name,
                    color = if (enabled) t.text2 else t.text3,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = fsSp(18f, t.fs).sp,
                    textAlign = TextAlign.Center,
                )
                // Tap-INERT value cell. Long-press resets ONLY when onReset != null (REVIEW #3).
                val valueMod = if (enabled && onReset != null) {
                    Modifier.pointerInput(onReset) { detectTapGestures(onLongPress = { onReset() }) }
                } else {
                    Modifier
                }
                Text(
                    text = valueText,
                    color = if (enabled) t.text else t.text3,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,
                    textAlign = TextAlign.Center,
                    modifier = valueMod,
                )
            }
            // ± nudge cells — the screen's expected physical action (accent, THEMING C5).
            NudgeCell(onDecrement, enabled, decrement = true)
            NudgeCell(onIncrement, enabled, decrement = false)
        }
    }
}

/** One ±-arrow cell: accent outline, plain clickable, stripped + dimmed while disabled (D-15). */
@Composable
private fun NudgeCell(
    onClick: () -> Unit,
    enabled: Boolean,
    decrement: Boolean,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    var cell = Modifier
        .size(fsSp(64f, t.fs).dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) t.accentLine else t.hair), shape)
    if (enabled) cell = cell.clickable(onClick = onClick)
    Box(cell, contentAlignment = Alignment.Center) {
        Icon(
            painter = painterResource(
                if (decrement) {
                    works.mees.dinghy.R.drawable.remove
                } else {
                    works.mees.dinghy.R.drawable.add
                },
            ),
            contentDescription = if (decrement) "decrease" else "increase",
            tint = if (enabled) t.accent2 else t.text3,
            modifier = Modifier.size(fsSp(34f, t.fs).dp),
        )
    }
}
