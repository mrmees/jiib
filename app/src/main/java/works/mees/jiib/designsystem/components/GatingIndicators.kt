package works.mees.jiib.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import works.mees.jiib.R
import works.mees.jiib.designsystem.control.Intent
import works.mees.jiib.designsystem.control.OutlinedControl
import works.mees.jiib.designsystem.layout.LocalUnitDp
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * Focus content card shown while a HardLock operation is running (e.g. homing).
 *
 * Fills the full Focus body with a single centered [label] in the [JiibType.focusHero] role —
 * the same large Mono display used for coordinate readouts — so the status is unmissable.
 *
 * **Static only** — no looping animation (Adreno 320 fill-rate budget; motion law: no continuous
 * animation). Short-circuited into the FocusFrame content lambda via `return@FocusFrame` so the
 * normal mode content is never composed while the lock is active.
 *
 * @param label human-readable status string (e.g. [works.mees.jiib.R.string.gating_homing]
 *              resolved by the caller via [androidx.compose.ui.res.stringResource]).
 * @param uDp   one unit U from the screen's unit grid (reserved for future sizing/layout use).
 */
@Composable
fun HardLockStatusCard(
    label: String,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = tokens.text,
            style = JiibType.focusHero.toTextStyle(tokens),
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Focus content card shown when a HardLock op exits abnormally (timeout / connection drop) —
 * the printer may still be running the operation. Forces an explicit user acknowledgement before
 * the gating latch clears, preventing the screen from silently unlocking.
 *
 * Shows a prominent title, a short explanatory message, and a single Dismiss button ([Intent.Accent])
 * that calls [onDismiss] (→ [works.mees.jiib.command.CommandDispatcher.acknowledgeUnresolved]).
 *
 * **Static only** — no looping animation (Adreno 320 fill-rate budget).
 * E-stop stays live in the [FocusFrame] header above; this card owns only the Focus body.
 *
 * @param uDp      one unit U from the screen's unit grid (controls Dismiss button height via [LocalUnitDp]).
 * @param onDismiss called when the user taps Dismiss; the caller wires this to [CommandDispatcher.acknowledgeUnresolved].
 */
@Composable
fun UnknownStatusCard(
    uDp: Dp,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTokens.current
    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.gating_unknown_title),
            color = tokens.text,
            style = JiibType.focusHeroLabel.toTextStyle(tokens),
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.gating_unknown_message),
            color = tokens.text2,
            style = JiibType.body.toTextStyle(tokens),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 24.dp),
        )
        CompositionLocalProvider(LocalUnitDp provides uDp) {
            OutlinedControl(
                label = stringResource(R.string.gating_unknown_dismiss),
                onClick = onDismiss,
                intent = Intent.Accent,
                modifier = Modifier.fillMaxWidth(0.7f),
            )
        }
    }
}
