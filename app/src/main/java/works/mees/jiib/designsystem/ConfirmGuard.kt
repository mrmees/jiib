package works.mees.jiib.designsystem

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.layout.ScreenScaffold
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The single full-screen Confirm guard (PRIM-03) — the one safety gate every destructive printer
 * action routes through (emergency stop / cancel print / disable motors / restart Klipper, all
 * wired in later phases). Per docs/ui_design/LAYOUT.md the guard **omits the gutter**: its Field
 * buttons ARE the actions, so there is no separate action row — the whole screen is the decision.
 *
 * Intent contract (docs/ui_design/THEMING.md "Button intent = color"):
 *  - the confirm action is [Intent.Warn] (amber `--heat`) when [warn] (the proceed-at-peril variant),
 *    else [Intent.Danger] (red `--stop`) when [destructive] (the default), else [Intent.Go] (green
 *    `--go`) for a positive commit;
 *  - the cancel/dismiss action is always [Intent.Neutral] — backing out of a guard is a
 *    non-destructive safe dismiss, never itself a red act.
 *
 * Amber proceed-at-peril variant (09-05 / D-12): the reusable **SAVE_CONFIG restart gate**. A
 * config-save+restart is the *intended, expected* result of calibration (bed-mesh Save, probe-calibrate
 * Save), not a destructive stop — so it is amber [Intent.Warn] + a `--heat-soft` tint, NOT red. [warn]
 * takes precedence over [destructive] (the SAVE_CONFIG gate is amber even though it mutates printer.cfg).
 *
 * The full-bleed background carries a faint tint of the relevant token (`--stop-soft` / `--heat-soft` /
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
 * @param cancelLabel label for the safe-dismiss action; defaults to the existing "Cancel".
 * @param destructive when true (default) confirm is red ([Intent.Danger]); when false it is green.
 * @param warn        when true, confirm is amber [Intent.Warn] (proceed-at-peril, the SAVE_CONFIG
 *                    restart gate, D-12) — takes PRECEDENCE over [destructive].
 */
@Composable
fun ConfirmGuard(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    cancelLabel: String = "Cancel",
    destructive: Boolean = true,
    warn: Boolean = false,
) {
    val t = LocalTokens.current
    // warn (amber proceed-at-peril) takes precedence; then destructive (red); else positive (green).
    val tint = when {
        warn -> t.heatSoft
        destructive -> t.stopSoft
        else -> t.goSoft
    }
    val confirmIntent = when {
        warn -> Intent.Warn
        destructive -> Intent.Danger
        else -> Intent.Go
    }

    // G-4: the stop-soft / go-soft tints are ALPHA-BEARING (…/ .15, …/ .16) — content behind bled
    // through, weakening the emergency-stop safety gate. Lay the OPAQUE token bg under the tint
    // (chained .background paints in order: opaque bg first, translucent intent tint on top) so the
    // backdrop firmly obscures while keeping the stop-soft/go-soft gravity. All color via tokens.
    Box(modifier.fillMaxSize().background(t.bg).background(tint)) {
        // Gutter omitted (LAYOUT.md): the Field's CONFIRM/CANCEL buttons are the navigation.
        ScreenScaffold(
            fieldFramed = false,
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
                        style = JiibType.screenTitle.toTextStyle(t),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = message,
                        color = t.text2,
                        style = JiibType.body.toTextStyle(t),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        OutlinedControl(
                            label = cancelLabel,
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
