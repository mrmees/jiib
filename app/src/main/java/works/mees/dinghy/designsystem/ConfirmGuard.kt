package works.mees.dinghy.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The single full-screen Confirm guard (PRIM-03) — the one safety gate every destructive printer
 * action routes through (emergency stop / cancel print / disable motors / restart Klipper, all
 * wired in later phases). Per docs/ui_design/LAYOUT.md the guard **omits the gutter**: its Field
 * buttons ARE the actions, so there is no separate action row — the whole screen is the decision.
 *
 * Intent contract (docs/ui_design/THEMING.md "Button intent = color"):
 *  - the confirm action is [Intent.Danger] (red `--stop`) when [destructive] (the default), else
 *    [Intent.Go] (green `--go`) for a positive commit;
 *  - the cancel/dismiss action is always [Intent.Neutral] — backing out of a guard is a
 *    non-destructive safe dismiss, never itself a red act.
 *
 * The full-bleed background carries a faint tint of the relevant token (`--stop-soft` /
 * `--go-soft`) so the decision's gravity reads at a glance before any label is parsed. This guard
 * DISPATCHES NOTHING itself (threat T-03-04) — it only invokes [onConfirm]/[onCancel]; the actual
 * destructive command (PRIM-05) lands in Phase 4.
 *
 * Static styling only (D-13): no looping/breathing animation — the Adreno-320 floor cannot spare
 * the fill rate, and the color + tint already carry the affordance.
 *
 * @param title       short headline ("Stop print?").
 * @param message     the consequence sub-line.
 * @param confirmLabel label for the confirm action ("Stop", "Cancel print", "Restart").
 * @param onConfirm   invoked when the user commits the action.
 * @param onCancel    invoked when the user backs out (safe dismiss).
 * @param destructive when true (default) confirm is red ([Intent.Danger]); when false it is green.
 */
@Composable
fun ConfirmGuard(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = true,
) {
    val t = LocalTokens.current
    val tint = if (destructive) t.stopSoft else t.goSoft
    val confirmIntent = if (destructive) Intent.Danger else Intent.Go

    Box(modifier.fillMaxSize().background(tint)) {
        // Gutter omitted (LAYOUT.md): the Field's CONFIRM/CANCEL buttons are the navigation.
        ScreenScaffold(
            field = {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = title,
                        color = t.text,
                        fontFamily = Geist,
                        fontWeight = FontWeight.Bold,
                        fontSize = fsSp(28f, t.fs).sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message,
                        color = t.text2,
                        fontFamily = Geist,
                        fontWeight = FontWeight.Normal,
                        fontSize = fsSp(17f, t.fs).sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedControl(
                            label = "Cancel",
                            onClick = onCancel,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Neutral,
                        )
                        OutlinedControl(
                            label = confirmLabel,
                            onClick = onConfirm,
                            modifier = Modifier.weight(1f),
                            intent = confirmIntent,
                        )
                    }
                }
            },
        )
    }
}
