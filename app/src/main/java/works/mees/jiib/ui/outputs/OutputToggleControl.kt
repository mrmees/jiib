package works.mees.jiib.ui.outputs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import works.mees.jiib.R
import works.mees.jiib.control.ControlSpecs
import works.mees.jiib.designsystem.Severity
import works.mees.jiib.designsystem.SeverityToast
import works.mees.jiib.designsystem.components.FootButtonBar
import works.mees.jiib.designsystem.components.footAction
import works.mees.jiib.designsystem.focus.FocusStage
import works.mees.jiib.theme.JiibType
import works.mees.jiib.theme.compose.FocusHeroText
import works.mees.jiib.theme.compose.LocalTokens
import works.mees.jiib.theme.compose.toTextStyle

/**
 * The digital On/Off toggle surface for a non-PWM `output_pin`, hosted inline in the Outputs
 * `FocusFrame` body. Keyboard-free, immediate-dispatch: tapping On or Off dispatches SET_PIN
 * VALUE=1/0 ONCE (no Apply flow, no confirm-guard). A dispatch failure → [SeverityToast] and the
 * user stays on the surface.
 *
 * Layout (Outputs Focus cleanup, 2026-06-21): the body is the BIG current-state readout (On = `go`,
 * else dim) filling the space; the actions live in a foot [FootButtonBar] `[On][Off]` (power/power_off,
 * On = Go / Off = Warn). There is NO in-Focus Back (the Field list + its Back own navigation). A
 * read-only (static_value) pin shows the state readout + "Read-only" caption and NO foot bar.
 *
 * Stateless content seam (no `remember`/dispatcher) — the live wiring lives in [OutputFocusControl].
 * Identity is carried by the FocusFrame header, so this surface takes no name.
 *
 * @param isOn       the live On/Off state (null = absent — rendered as Off, both actions available).
 * @param readOnly   value-only when true (static pin): state readout + caption, no foot bar.
 * @param enabled    false while a dispatch to this pin is in-flight (per-objectKey busy).
 * @param failureText a dispatch failure message to surface, or null.
 * @param onOn        dispatch SET_PIN VALUE=1 (immediate).
 * @param onOff       dispatch SET_PIN VALUE=0 (immediate).
 * @param uDp         one unit U, for the foot bar height.
 */
@Composable
fun OutputToggleControl(
    isOn: Boolean?,
    readOnly: Boolean,
    enabled: Boolean,
    failureText: String?,
    onOn: () -> Unit,
    onOff: () -> Unit,
    uDp: Dp,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    FocusStage(
        modifier = modifier,
        body = {
            // Body: the current On/Off state, color-coded, centered by the Stage's body zone (no
            // duplicate name — the FocusFrame header carries identity). No wrapper Box: the body
            // slot IS BoxScope with Center alignment.
            FocusHeroText(
                text = if (isOn == true) stringResource(R.string.output_on) else stringResource(R.string.output_off),
                role = JiibType.focusHero,
                t = t,
                color = if (isOn == true) t.go else t.text3,
            )
        },
        dock = {
            if (readOnly) {
                Text(
                    text = stringResource(R.string.output_read_only),
                    color = t.text3,
                    style = JiibType.caption.toTextStyle(t),
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
            failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
            if (!readOnly) {
                FootButtonBar(
                    uDp = uDp,
                    actions = listOf(
                        footAction(ControlSpecs.outputOn, onClick = onOn, enabled = enabled),
                        footAction(ControlSpecs.outputOff, onClick = onOff, enabled = enabled),
                    ),
                )
            }
        },
    )
}
