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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import works.mees.dinghy.R
import works.mees.dinghy.calibration.ProbeCalibrateVm
import works.mees.dinghy.calibration.ProbePageState
import works.mees.dinghy.command.CommandDispatcher
import works.mees.dinghy.command.CommandRegistry
import works.mees.dinghy.command.TestZArgs
import works.mees.dinghy.command.dispatch
import works.mees.dinghy.designsystem.ConfirmGuard
import works.mees.dinghy.designsystem.Severity
import works.mees.dinghy.designsystem.SeverityToast
import works.mees.dinghy.designsystem.control.Intent
import works.mees.dinghy.designsystem.layout.ScreenScaffold
import works.mees.dinghy.theme.Geist
import works.mees.dinghy.theme.GeistMono
import works.mees.dinghy.theme.ThemeTokens
import works.mees.dinghy.theme.compose.LocalTokens
import works.mees.dinghy.theme.fsSp

/** Fine TESTZ jog steps (mm) — white "setting" pills; selecting a step is a setting, not a command. */
private val TESTZ_STEPS = listOf(1.0, 0.1, 0.05, 0.025)

/**
 * The interactive manual-probe Z-calibrate page (CALIB-05 / D-01 / UI-SPEC §5). This is the ONE
 * stateful calibration page: Start opens a manual-probe session, the jog pad nudges Z via TESTZ,
 * Accept/Abort closes it, and an amber SAVE_CONFIG persists the captured offset.
 *
 * The three Focus/gutter states are driven purely by [ProbeCalibrateVm.state]
 * ([ProbePageState.Idle]/[ProbePageState.Active]/[ProbePageState.Accepted] — derived from
 * `manual_probe.is_active`). The screen NEVER infers state; it renders the vm verbatim.
 *
 * Layout (ScreenScaffold):
 *  - Focus idle: `img/nozzle.svg` square + "Start, then lower the nozzle to the bed."
 *  - Focus active: the live `manual_probe.z_position` as a big GeistMono hero (updates as you nudge);
 *    if the `// Z position: <lower> --> <current> <-- <upper>` line parsed, render the bracket under it
 *    (the bounds are `??????` → "—" on the real wire; the page tolerates null bounds, 09-01).
 *  - Focus accepted: the captured offset + "Offset set. Save to keep it (restarts printer)."
 *  - Field = the jog pad (disabled until Active): white step-preset pills + blue Z▲/Z▼ (TESTZ ±step).
 *  - Gutter state-adaptive: Idle → Start (blue, the gated PROBE_CALIBRATE/Z_ENDSTOP_CALIBRATE) + Back
 *    (green); Active → Accept (green) + Abort (red) — BACK SUPPRESSED while a session is live
 *    (T-09-06-02 — leaving mid-probe leaves a dangling session); Accepted → Save (amber → ConfirmGuard
 *    restart gate → SAVE_CONFIG) + Back (green, discards the in-memory offset).
 *
 * Ratio-only sizing; token-only color.
 *
 * @param vm         the resolved [ProbeCalibrateVm] (state machine + live Z + bracket + start cmd + error).
 * @param tokens     the active resolved tokens (THEME-01 — never raw color).
 * @param dispatcher the live session dispatcher; all actions go through it (null until a session exists).
 * @param onStartDispatched called after Start dispatches (parity with the other calibration screens).
 * @param onBack     leave the page (green Back; only offered when NOT Active).
 */
@Composable
fun ProbeCalibrateScreen(
    vm: ProbeCalibrateVm,
    tokens: ThemeTokens,
    dispatcher: CommandDispatcher?,
    onStartDispatched: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(0.05) } // default a sensible fine step.
    var saveGuard by remember { mutableStateOf(false) }
    val active = vm.state == ProbePageState.Active

    Box(modifier.fillMaxSize()) {
        ScreenScaffold(
            focus = {
                ProbeFocus(vm = vm, modifier = Modifier.fillMaxSize().padding(8.dp))
            },
            field = {
                ProbeJogPad(
                    enabled = active && dispatcher != null,
                    step = step,
                    onSelectStep = { step = it },
                    onTestZ = { delta -> dispatcher?.dispatch(CommandRegistry.testZ, TestZArgs(delta)) },
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                )
            },
            gutter = {
                Row(
                    Modifier.fillMaxWidth().padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    when (vm.state) {
                        ProbePageState.Idle -> {
                            ProbeGutterButton(
                                label = "Start",
                                onClick = {
                                    val d = dispatcher ?: return@ProbeGutterButton
                                    // The gated Z-calibrate command (A3): PROBE_CALIBRATE vs Z_ENDSTOP_CALIBRATE.
                                    if (vm.startCommand == "Z_ENDSTOP_CALIBRATE") {
                                        d.dispatch(CommandRegistry.zEndstopCalibrate, Unit)
                                    } else {
                                        d.dispatch(CommandRegistry.probeCalibrate, Unit)
                                    }
                                    onStartDispatched()
                                },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Accent,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Back",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Go,
                            )
                        }
                        ProbePageState.Active -> {
                            // BACK SUPPRESSED while a session is live (T-09-06-02) — Accept or Abort to leave.
                            ProbeGutterButton(
                                label = "Accept",
                                onClick = { dispatcher?.dispatch(CommandRegistry.accept, Unit) },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Go,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Abort",
                                onClick = { dispatcher?.dispatch(CommandRegistry.abort, Unit) },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Danger,
                                enabled = dispatcher != null,
                            )
                        }
                        ProbePageState.Accepted -> {
                            ProbeGutterButton(
                                label = "Save",
                                onClick = { saveGuard = true },
                                modifier = Modifier.weight(1f),
                                intent = Intent.Warn,
                                enabled = dispatcher != null,
                            )
                            ProbeGutterButton(
                                label = "Back",
                                onClick = onBack,
                                modifier = Modifier.weight(1f),
                                intent = Intent.Go,
                            )
                        }
                    }
                }
            },
        )

        // Amber SAVE_CONFIG restart gate (D-12) — persists the captured probe offset.
        if (saveGuard) {
            ConfirmGuard(
                title = "Save & restart?",
                message = "This saves the new Z offset and restarts the printer — the expected result of " +
                    "calibration. The connection will briefly drop and reconnect.",
                confirmLabel = "Save & restart",
                warn = true,
                onConfirm = {
                    dispatcher?.dispatch(CommandRegistry.saveConfig, Unit)
                    saveGuard = false
                },
                onCancel = { saveGuard = false },
            )
        }

        // Failure → SeverityToast(Error) with the printer's REDACTED RpcError text (never e.message,
        // T-09-06-05) — overlaid at the bottom so the user sees WHY a TESTZ/Accept/Start was refused.
        if (vm.errorText != null) {
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
private fun ProbeFocus(vm: ProbeCalibrateVm, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when (vm.state) {
            ProbePageState.Idle -> {
                Box(
                    Modifier.fillMaxWidth(0.9f).aspectRatio(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.nozzle),
                        contentDescription = "Probe calibrate",
                        tint = t.text2,
                        modifier = Modifier.fillMaxSize(0.9f),
                    )
                }
                Text(
                    text = "Start, then lower the nozzle to the bed.",
                    color = t.text2,
                    fontFamily = Geist,
                    fontWeight = FontWeight.Normal,
                    fontSize = fsSp(16f, t.fs).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            ProbePageState.Active -> {
                Text(
                    text = vm.zPosition?.let { fmtZ(it) } ?: "—",
                    color = t.text,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(48f, t.fs).sp,
                )
                vm.bracket?.let { b ->
                    Text(
                        text = "${fmtBound(b.lower)} → ${fmtZ(b.current)} ← ${fmtBound(b.upper)}",
                        color = t.text2,
                        fontFamily = GeistMono,
                        fontWeight = FontWeight.Medium,
                        fontSize = fsSp(18f, t.fs).sp,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    text = "Nudge Z until the nozzle just grabs paper, then Accept.",
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(16f, t.fs).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            ProbePageState.Accepted -> {
                Text(
                    text = vm.capturedOffset?.let { fmtZ(it) } ?: "—",
                    color = t.go,
                    fontFamily = GeistMono,
                    fontWeight = FontWeight.Bold,
                    fontSize = fsSp(40f, t.fs).sp,
                )
                Text(
                    text = "Offset set. Save to keep it (restarts printer).",
                    color = t.text2,
                    fontFamily = Geist,
                    fontSize = fsSp(16f, t.fs).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
    }
}

/**
 * The jog pad: a white step-preset pill row + blue Z▲/Z▼ that fire TESTZ(±step). Disabled until the
 * session is Active (is_active true). Selecting a step is a setting (white/neutral); the Z nudges are
 * physical commands (blue/accent).
 */
@Composable
private fun ProbeJogPad(
    enabled: Boolean,
    step: Double,
    onSelectStep: (Double) -> Unit,
    onTestZ: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // White step-preset pills (setting intent) — always selectable so the step is set before Start.
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in TESTZ_STEPS) {
                StepPill(value = s, selected = s == step, onSelect = { onSelectStep(s) }, modifier = Modifier.weight(1f))
            }
        }
        // Z▲ / Z▼ (blue/accent — physical commands) fire TESTZ(+step)/TESTZ(-step).
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProbeGutterButton(
                label = "Z ▲",
                onClick = { onTestZ(step) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                intent = Intent.Accent,
                enabled = enabled,
            )
            ProbeGutterButton(
                label = "Z ▼",
                onClick = { onTestZ(-step) },
                modifier = Modifier.weight(1f).fillMaxHeight(),
                intent = Intent.Accent,
                enabled = enabled,
            )
        }
    }
}

/** One white "setting" step pill — accent-outlined when active, neutral otherwise. */
@Composable
private fun StepPill(value: Double, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalTokens.current
    val shape = RoundedCornerShape(t.rCtrl)
    Box(
        modifier.fillMaxHeight()
            .clip(shape)
            .border(BorderStroke(2.dp, if (selected) t.accentLine else t.outline), shape)
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fmtStep(value),
            color = if (selected) t.accent2 else t.text2,
            fontFamily = GeistMono,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            fontSize = fsSp(15f, t.fs).sp,
        )
    }
}

@Composable
private fun ProbeGutterButton(
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

/** Three-decimal mm — manual-probe nudges are fine (down to 0.025 mm). */
private fun fmtZ(v: Double): String = String.format(Locale.US, "%.3f", v)

/** A bracket bound, or "—" when the wire carried `??????` (null) — the 09-01 unknown-bound contract. */
private fun fmtBound(v: Double?): String = v?.let { fmtZ(it) } ?: "—"

/** Step label: drop the trailing ".0" on the 1 mm step, keep 0.1/0.05/0.025. */
private fun fmtStep(d: Double): String =
    if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()
