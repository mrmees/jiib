package works.mees.jiib.designsystem.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
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
