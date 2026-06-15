package works.mees.dinghy.ui.outputs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.components.FootButtonBar
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.control.OutlinedControl
import works.mees.dinghy.designsystem.layout.ListFrameInset
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.designsystem.layout.rememberUnitGrid
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/**
 * The digital On/Off toggle page (SC-2) — the ONE new small control this plan adds, for a non-PWM
 * `output_pin`. Keyboard-free, immediate-dispatch: tapping On or Off dispatches SET_PIN VALUE=1/0 through the
 * catalog ([works.mees.dinghy.command.CommandRegistry.setOutputPin]) ONCE, with NO Apply flow and NO
 * confirm-guard step. A dispatch failure → [SeverityToast] and the user STAYS on the page. The currently
 * active state's button wears the accent ([Intent.Accent]); the other is neutral. A read-only (static_value)
 * pin renders value-only with the toggle disabled (SC-3).
 *
 * Stateless content seam (no `remember`/dispatcher) — the live wiring lives in [OutputFocusControl], which feeds
 * [onOn]/[onOff] the catalog dispatch.
 *
 * @param prettyName the output's display name.
 * @param isOn       the live On/Off state (null = absent value — both buttons available, SC-3).
 * @param readOnly   value-only when true (static pin).
 * @param enabled    false while a dispatch to this pin is in-flight (per-objectKey busy).
 * @param failureText a dispatch failure message to surface, or null.
 * @param onOn        dispatch SET_PIN VALUE=1 (immediate).
 * @param onOff       dispatch SET_PIN VALUE=0 (immediate — the page's Off action).
 * @param onBack      the Back exit (accent, FIRST in the foot bar — R5/R8).
 */
@Composable
fun OutputToggleControl(
    prettyName: String,
    isOn: Boolean?,
    readOnly: Boolean,
    enabled: Boolean,
    failureText: String?,
    onOn: () -> Unit,
    onOff: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val grid = rememberUnitGrid(minOf(maxWidth, maxHeight))
        ScreenScaffold(
            fieldFramed = false,
            field = {
                Column(
                    Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = prettyName,
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(20f, t.fs).sp,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    if (readOnly) {
                        // SC-3: value-only, no control.
                        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (isOn == true) {
                                    stringResource(R.string.output_on)
                                } else {
                                    stringResource(R.string.output_off)
                                },
                                color = t.text3,
                                fontFamily = GeistMono,
                                fontWeight = FontWeight.Bold,
                                fontSize = fsSp(48f, t.fs).sp,
                            )
                        }
                        Text(
                            text = stringResource(R.string.output_read_only),
                            color = t.text3,
                            fontFamily = GeistMono,
                            fontSize = fsSp(15f, t.fs).sp,
                        )
                    } else {
                        Row(
                            Modifier.fillMaxWidth().weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            OutlinedControl(
                                label = stringResource(R.string.output_on),
                                onClick = { if (enabled) onOn() },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                intent = if (isOn == true) Intent.Accent else Intent.Neutral,
                            )
                            OutlinedControl(
                                label = stringResource(R.string.output_off),
                                onClick = { if (enabled) onOff() },
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                intent = if (isOn == false) Intent.Accent else Intent.Neutral,
                            )
                        }
                    }
                    failureText?.let { msg -> SeverityToast(Severity.Error, msg, Modifier.fillMaxWidth()) }
                    // Foot-of-list Back (R1 gutter retirement): FIRST + accent per R5/R8.
                    FootButtonBar(uDp = grid.uDp, modifier = Modifier.padding(horizontal = ListFrameInset, vertical = 8.dp)) {
                        OutlinedControl(
                            label = stringResource(R.string.common_back),
                            onClick = onBack,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                        )
                    }
                }
            },
        )
    }
}
