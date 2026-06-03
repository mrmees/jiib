package works.mees.dinghy.ui.calibration

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import works.mees.dinghy.R
import works.mees.dinghy.calibration.TiltState
import works.mees.dinghy.calibration.TiltVm
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.MaterialSymbol
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** The two routines this ONE screen serves (D-02 shared automatic-flow code path). */
enum class TiltVariant { ZTilt, Qgl }

/**
 * The Z-tilt / QGL shared automatic-flow screen (CALIB-03 / UI-SPEC §3). ONE code path serves both
 * routines — [variant] selects the title + which [CommandRegistry] command Run dispatches
 * ([CommandRegistry.zTiltAdjust] vs [CommandRegistry.quadGantryLevel], D-02).
 *
 * This is a "run it and watch it converge" page with NO mid-run user action — there is **no Abort** and
 * no e-stop framing (D-11: Klipper has no clean cancel; the routine is short and hands-off). Done is
 * `applied == true`; failure is the dispatcher's `Failure` toast — NEVER `applied == false` alone
 * (Pitfall 2, handled in [works.mees.dinghy.calibration.TiltHolder] / `tiltState`).
 *
 * Layout (ScreenScaffold):
 *  - Focus = `img/bed_tilt.svg` square ~90%H, theme-tinted. Overlay `question_exchange` (accent) when
 *    not-yet-run; swap to `data_table` once a run has results (Done/Running with applied data).
 *  - Field = the convergence readout once a run exists (an "applied" / per-stepper summary line).
 *  - Gutter = Run (blue/accent, dispatches per [variant]) + Back (green/go). Run is disabled while
 *    Running and gated on homed state — when unhomed it shows an inline blue Home offer instead (D-13).
 *
 * Ratio-only sizing; token-only color. The screen NEVER infers state — it renders [TiltVm] verbatim.
 *
 * @param vm         the resolved [TiltVm] (state machine + homed gate + error text).
 * @param variant    Z-tilt or QGL — selects the title + the dispatched command (D-02).
 * @param tokens     the active resolved tokens (THEME-01 — never raw color).
 * @param dispatcher the live session dispatcher; Run goes through it (null until a session exists).
 * @param onRunDispatched called after Run dispatches so the holder can mark the routine dispatched.
 * @param onHome     dispatch the homed pre-flight (G28) — the inline Home offer (D-13).
 * @param onBack     leave the page (green Back).
 */
@Composable
fun TiltScreen(
    vm: TiltVm,
    variant: TiltVariant,
    tokens: ThemeTokens,
    dispatcher: CommandDispatcher?,
    onRunDispatched: () -> Unit,
    onHome: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = when (variant) {
        TiltVariant.ZTilt -> "Z-Tilt Adjust"
        TiltVariant.Qgl -> "Quad Gantry Level"
    }
    val running = vm.state == TiltState.Running

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                TiltFocus(
                    title = title,
                    state = vm.state,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            field = {
                TiltField(
                    state = vm.state,
                    errorText = vm.errorText,
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!vm.homedGate) {
                        // Inline Home pre-flight offer (D-13) — replaces Run until the printer is homed.
                        TiltGutterButton(
                            label = "Home first",
                            onClick = onHome,
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null,
                        )
                    } else {
                        TiltGutterButton(
                            label = if (running) "Running…" else "Run",
                            onClick = {
                                val d = dispatcher ?: return@TiltGutterButton
                                when (variant) {
                                    TiltVariant.ZTilt -> d.dispatch(CommandRegistry.zTiltAdjust, Unit)
                                    TiltVariant.Qgl -> d.dispatch(CommandRegistry.quadGantryLevel, Unit)
                                }
                                onRunDispatched()
                            },
                            modifier = Modifier.weight(1f),
                            intent = Intent.Accent,
                            enabled = dispatcher != null && !running,
                        )
                    }
                    TiltGutterButton(
                        label = "Back",
                        onClick = onBack,
                        modifier = Modifier.weight(1f),
                        intent = Intent.Go,
                    )
                }
            },
        )

        // Failure → SeverityToast(Error) with the printer's REDACTED RpcError text (never e.message,
        // T-05-11-01) — overlaid at the bottom so the user sees WHY the routine was refused.
        if (vm.state == TiltState.Failed && vm.errorText != null) {
            Box(
                Modifier.fillMaxSize().padding(16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) {
                SeverityToast(Severity.Error, vm.errorText, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TiltFocus(title: String, state: TiltState, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            color = t.text,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(22f, t.fs).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 12.dp),
        )
        Box(
            Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
            contentAlignment = Alignment.Center,
        ) {
            // bed_tilt.svg → token-tinted (THEME-01). The Focus asset for both routines.
            Icon(
                painter = painterResource(R.drawable.bed_tilt),
                contentDescription = title,
                tint = t.text2,
                modifier = Modifier.fillMaxSize(0.9f),
            )
            // State overlay: not-yet-run → question_exchange (accent); run → data_table.
            val overlayGlyph = if (state == TiltState.Idle) "question_exchange" else "data_table"
            val overlayTint = when (state) {
                TiltState.Idle -> t.accent
                TiltState.Done -> t.go
                TiltState.Failed -> t.stop
                TiltState.Running -> t.accent2
            }
            MaterialSymbol(overlayGlyph, tint = overlayTint, sizeSp = fsSp(40f, t.fs))
        }
    }
}

@Composable
private fun TiltField(
    state: TiltState,
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    val t = LocalTokens.current
    val (headline, body) = when (state) {
        TiltState.Idle -> "Not yet run" to "Run to level the gantry automatically."
        TiltState.Running -> "Running…" to "Probing and adjusting. This is hands-off — wait for it to converge."
        TiltState.Done -> "Applied" to "The gantry is level. Re-run any time to re-check."
        TiltState.Failed -> "Failed" to (errorText ?: "The printer rejected the routine.")
    }
    Column(
        modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = headline,
            color = when (state) {
                TiltState.Done -> t.go
                TiltState.Failed -> t.stop
                else -> t.text
            },
            fontFamily = GeistMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(26f, t.fs).sp,
        )
        Text(
            text = body,
            color = t.text2,
            fontFamily = Geist,
            fontWeight = FontWeight.Normal,
            fontSize = fsSp(16f, t.fs).sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun TiltGutterButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    intent: Intent = Intent.Neutral,
    enabled: Boolean = true,
) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    val outline = intentColor(intent, t)
    val base = modifier
        .heightIn(min = 64.dp)
        .clip(shape)
        .border(BorderStroke(2.dp, if (enabled) outline else t.hair), shape)
        .background(if (enabled) Color.Transparent else t.surface)
        .padding(horizontal = 12.dp, vertical = 18.dp)
    val box = if (enabled) base.clickable(onClick = onClick) else base
    Box(box, contentAlignment = Alignment.Center) {
        Text(
            text = label,
            color = if (enabled) t.text else t.text3,
            fontFamily = Geist,
            fontWeight = FontWeight.SemiBold,
            fontSize = fsSp(18f, t.fs).sp,
            maxLines = 1,
        )
    }
}
